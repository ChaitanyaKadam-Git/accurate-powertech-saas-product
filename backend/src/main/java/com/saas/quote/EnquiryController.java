package com.saas.quote;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Customer flow: enquiries from first contact to commissioning. Uses the ENQUIRIES rights. People whose role sees only
 * their own quotations also see only the enquiries they own or created.
 */
@RestController @RequestMapping("/api/enquiries") @RequiredArgsConstructor @Transactional
public class EnquiryController {
    private final EnquiryRepo repo;
    private final EnquiryEventRepo events;
    private final CustomerRepo customers;
    private final UserRepo users;
    private final QuotationRepo quotations;
    private final AuditService audit;

    record Save(Long customerId, String businessLine, String title, BigDecimal capacity, String source, Long ownerId,
                BigDecimal expectedValue, LocalDate nextFollowUp, String notes, String orderNo, LocalDate orderDate, BigDecimal orderValue,
                LocalDate deliveryDate, LocalDate installationDate, LocalDate commissioningDate, LocalDate warrantyUntil, LocalDate amcUntil,
                String lostReason) {}
    record StageChange(String stage, LocalDate date, String note, String lostReason, String orderNo, BigDecimal orderValue,
                       LocalDate warrantyUntil, LocalDate amcUntil) {}
    record Note(String kind, LocalDate date, String text, LocalDate nextFollowUp) {}
    record Row(Long id, String refNo, String businessLine, Long customerId, String customerCode, String customerName, String city, String title,
               BigDecimal capacity, String source, Long ownerId, String ownerName, String stage, String stageLabel, LocalDate stageDate,
               BigDecimal expectedValue, LocalDate nextFollowUp, BigDecimal orderValue, long quotationCount, Instant updatedAt) {}
    record QuoteRef(Long id, String docType, String refNo, LocalDate quoteDate, BigDecimal total) {}
    record Detail(Enquiry enquiry, Customer customer, String ownerName, String stageLabel, List<EnquiryEvent> events, List<QuoteRef> quotations) {}
    record Stats(Map<String, Long> byStage, BigDecimal openValue, BigDecimal wonValueThisYear, long wonThisYear, long lostThisYear, List<Row> followUps) {}

    // ------------------------------------------------------------------ read
    @GetMapping @Requires(module = AppModule.ENQUIRIES, right = Right.VIEW)
    public List<Row> list(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) String stage,
                          @RequestParam(required = false) String line, @RequestParam(required = false) Long ownerId,
                          @RequestParam(required = false) Long customerId) {
        return rows(me, visible(me).stream()
                .filter(e -> stage == null || stage.isBlank() || e.getStage().equals(stage.toUpperCase()))
                .filter(e -> line == null || line.isBlank() || e.getBusinessLine().equals(BusinessLine.of(line).name()))
                .filter(e -> ownerId == null || ownerId.equals(e.getOwnerId()))
                .filter(e -> customerId == null || customerId.equals(e.getCustomerId())).toList());
    }

    @GetMapping("/stats") @Requires(module = AppModule.ENQUIRIES, right = Right.VIEW)
    public Stats stats(@AuthenticationPrincipal AuthUser me) {
        List<Enquiry> all = visible(me);
        Map<String, Long> by = new LinkedHashMap<>();
        for (EnquiryStage s : EnquiryStage.values()) by.put(s.name(), 0L);
        all.forEach(e -> by.merge(e.getStage(), 1L, Long::sum));
        int year = LocalDate.now().getYear();
        BigDecimal open = sum(all.stream().filter(e -> stage(e).open()).map(Enquiry::getExpectedValue));
        List<Enquiry> wonYear = all.stream().filter(e -> stage(e).won() && e.getOrderDate() != null && e.getOrderDate().getYear() == year).toList();
        long lostYear = all.stream().filter(e -> stage(e) == EnquiryStage.LOST && e.getStageDate() != null && e.getStageDate().getYear() == year).count();
        LocalDate today = LocalDate.now();
        List<Enquiry> due = all.stream().filter(e -> stage(e).open() && e.getNextFollowUp() != null && !e.getNextFollowUp().isAfter(today))
                .sorted(Comparator.comparing(Enquiry::getNextFollowUp)).toList();
        return new Stats(by, open, sum(wonYear.stream().map(e -> e.getOrderValue() != null ? e.getOrderValue() : e.getExpectedValue())),
                wonYear.size(), lostYear, rows(me, due));
    }

    record Person(Long id, String name) {}

    /** People an enquiry can be assigned to (active users of the company). Others only ever assign to themselves. */
    @GetMapping("/owners") @Requires(module = AppModule.ENQUIRIES, right = Right.VIEW)
    public List<Person> owners(@AuthenticationPrincipal AuthUser me) {
        return users.findByTenantIdOrderByIdDesc(me.tenantId()).stream().filter(AppUser::enabled)
                .filter(u -> me.seesAllQuotations() || u.getId().equals(me.userId()))
                .map(u -> new Person(u.getId(), u.displayName())).sorted(Comparator.comparing(Person::name)).toList();
    }

    @GetMapping("/{id}") @Requires(module = AppModule.ENQUIRIES, right = Right.VIEW)
    public Detail get(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        Enquiry e = find(id, me);
        Customer c = customers.findByIdAndTenantId(e.getCustomerId(), me.tenantId()).orElse(null);
        List<QuoteRef> q = quotations.findByOwnerIdAndEnquiryIdOrderByIdDesc(me.tenantId(), e.getId()).stream()
                .map(x -> new QuoteRef(x.getId(), x.getDocType(), x.getRefNo(), x.getQuoteDate(), x.getTotal())).toList();
        return new Detail(e, c, names(me).get(e.getOwnerId()), stage(e).label, events.findByEnquiryIdOrderByIdDesc(e.getId()), q);
    }

    // ------------------------------------------------------------------ write
    @PostMapping @Requires(module = AppModule.ENQUIRIES, right = Right.CREATE)
    public Detail create(@AuthenticationPrincipal AuthUser me, @RequestBody Save in) {
        Enquiry e = new Enquiry();
        e.setTenantId(me.tenantId());
        e.setCreatedBy(me.userId());
        e.setCreatedAt(Instant.now());
        e.setStage(EnquiryStage.NEW.name());
        e.setStageDate(LocalDate.now());
        e.setRefNo(nextRef(me.tenantId()));
        apply(e, in, me);
        repo.save(e);
        event(e, me, "CREATED", EnquiryStage.NEW.name(), LocalDate.now(), "Enquiry created for " + customers.findById(e.getCustomerId()).map(Customer::getCode).orElse("") + ": " + e.getTitle() + (e.getSource() == null ? "" : " (source: " + e.getSource() + ")"));
        audit.log(me, AppModule.ENQUIRIES, "CREATE", "Enquiry", e.getId(), e.getRefNo() + " " + e.getTitle());
        return get(me, e.getId());
    }

    @PutMapping("/{id}") @Requires(module = AppModule.ENQUIRIES, right = Right.EDIT)
    public Detail update(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @RequestBody Save in) {
        Enquiry e = find(id, me);
        apply(e, in, me);
        audit.log(me, AppModule.ENQUIRIES, "EDIT", "Enquiry", e.getId(), e.getRefNo() + " " + e.getTitle());
        return get(me, e.getId());
    }

    /** Moves the enquiry to a stage (forward, back or to LOST) and records it on the timeline with its date and note. */
    @PostMapping("/{id}/stage") @Requires(module = AppModule.ENQUIRIES, right = Right.EDIT)
    public Detail moveStage(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @RequestBody StageChange in) {
        Enquiry e = find(id, me);
        EnquiryStage to = EnquiryStage.of(in.stage());
        if (to == null) throw bad("Unknown stage");
        LocalDate date = in.date() == null ? LocalDate.now() : in.date();
        if (date.isAfter(LocalDate.now())) throw bad("The date of a stage cannot be in the future");
        EnquiryStage from = stage(e);
        StringBuilder text = new StringBuilder(from.label + " -> " + to.label);
        switch (to) {
            case LOST -> {
                if (trim(in.lostReason()) == null) throw bad("Enter why the enquiry was lost");
                e.setLostReason(trim(in.lostReason()));
                text.append(". Reason: ").append(e.getLostReason());
            }
            case ORDER -> {
                if (trim(in.orderNo()) != null) e.setOrderNo(trim(in.orderNo()));
                if (in.orderValue() != null) { if (in.orderValue().signum() < 0) throw bad("Order value cannot be negative"); e.setOrderValue(in.orderValue()); }
                e.setOrderDate(date);
                if (e.getOrderNo() != null) text.append(". Order ").append(e.getOrderNo());
            }
            case DELIVERED -> e.setDeliveryDate(date);
            case INSTALLED -> e.setInstallationDate(date);
            case COMMISSIONED -> {
                e.setCommissioningDate(date);
                if (in.warrantyUntil() != null) e.setWarrantyUntil(in.warrantyUntil());
                if (in.amcUntil() != null) e.setAmcUntil(in.amcUntil());
                if (e.getWarrantyUntil() != null) text.append(". Warranty until ").append(e.getWarrantyUntil());
                if (e.getAmcUntil() != null) text.append(". AMC until ").append(e.getAmcUntil());
            }
            default -> { }
        }
        if (to != EnquiryStage.LOST) e.setLostReason(null);
        if (!to.open()) e.setNextFollowUp(null);   // won or lost: nothing left to chase
        e.setStage(to.name());
        e.setStageDate(date);
        e.setUpdatedAt(Instant.now());
        if (trim(in.note()) != null) text.append(". ").append(trim(in.note()));
        event(e, me, "STAGE", to.name(), date, text.toString());
        audit.log(me, AppModule.ENQUIRIES, "EDIT", "Enquiry", e.getId(), e.getRefNo() + ": " + text);
        return get(me, e.getId());
    }

    /** A call, visit or note on the timeline; can also set the next follow-up date. */
    @PostMapping("/{id}/events") @Requires(module = AppModule.ENQUIRIES, right = Right.EDIT)
    public Detail addNote(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @RequestBody Note in) {
        Enquiry e = find(id, me);
        String kind = in.kind() == null ? "NOTE" : in.kind().toUpperCase();
        if (!List.of("CALL", "VISIT", "NOTE").contains(kind)) throw bad("Choose call, visit or note");
        if (trim(in.text()) == null) throw bad("Write what happened");
        e.setNextFollowUp(in.nextFollowUp());
        e.setUpdatedAt(Instant.now());
        event(e, me, kind, null, in.date() == null ? LocalDate.now() : in.date(),
                trim(in.text()) + (in.nextFollowUp() == null ? "" : " (next follow-up " + in.nextFollowUp() + ")"));
        return get(me, e.getId());
    }

    /** Statements made for it stay; they are only unlinked. */
    @DeleteMapping("/{id}") @Requires(module = AppModule.ENQUIRIES, right = Right.DELETE)
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        Enquiry e = find(id, me);
        for (Quotation q : quotations.findByOwnerIdAndEnquiryIdOrderByIdDesc(me.tenantId(), e.getId())) q.setEnquiryId(null);
        events.deleteByEnquiryId(e.getId());
        audit.log(me, AppModule.ENQUIRIES, "DELETE", "Enquiry", e.getId(), e.getRefNo() + " " + e.getTitle());
        repo.delete(e);
    }

    // ------------------------------------------------------------------ used by the statement form
    /** Links a new statement to its enquiry: timeline entry, and an enquiry still before "Quotation sent" moves there. */
    public void statementCreated(AuthUser me, Long enquiryId, Quotation q) {
        Enquiry e = find(enquiryId, me);
        event(e, me, "QUOTATION", null, LocalDate.now(), "Statement " + q.getRefNo() + " created, total Rs. " + q.getTotal().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
        if (stage(e).ordinal() < EnquiryStage.QUOTED.ordinal()) {
            event(e, me, "STAGE", EnquiryStage.QUOTED.name(), LocalDate.now(), stage(e).label + " -> " + EnquiryStage.QUOTED.label + " (statement " + q.getRefNo() + ")");
            e.setStage(EnquiryStage.QUOTED.name());
            e.setStageDate(LocalDate.now());
        }
        e.setUpdatedAt(Instant.now());
    }

    /** Timeline entry for a proforma / invoice made for this enquiry. */
    public void documentCreated(AuthUser me, Long enquiryId, Quotation q, String text) {
        Enquiry e = find(enquiryId, me);
        event(e, me, "QUOTATION", null, LocalDate.now(), text + ", total Rs. " + q.getTotal().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
        e.setUpdatedAt(Instant.now());
    }

    /** The enquiry, if this person may see it; 404 otherwise. */
    public Enquiry find(Long id, AuthUser me) {
        Enquiry e = repo.findByIdAndTenantId(id, me.tenantId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Enquiry not found"));
        if (!canSee(e, me)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Enquiry not found");
        return e;
    }

    // ------------------------------------------------------------------ helpers
    private static boolean canSee(Enquiry e, AuthUser me) {
        return me.seesAllQuotations() || me.userId().equals(e.getOwnerId()) || me.userId().equals(e.getCreatedBy());
    }

    private List<Enquiry> visible(AuthUser me) {
        return repo.findByTenantIdOrderByIdDesc(me.tenantId()).stream().filter(e -> canSee(e, me)).toList();
    }

    private void apply(Enquiry e, Save in, AuthUser me) {
        if (in.customerId() == null) throw bad("Choose the customer");
        customers.findByIdAndTenantId(in.customerId(), me.tenantId()).orElseThrow(() -> bad("Choose a valid customer"));
        String title = trim(in.title());
        if (title == null) throw bad("Describe the requirement");
        Long owner = in.ownerId() == null ? (e.getOwnerId() == null ? me.userId() : e.getOwnerId()) : in.ownerId();
        if (!owner.equals(e.getOwnerId())) {
            users.findByIdAndTenantId(owner, me.tenantId()).orElseThrow(() -> bad("Choose a valid owner"));
            if (!me.seesAllQuotations() && !owner.equals(me.userId())) throw bad("You can only assign enquiries to yourself");
        }
        for (BigDecimal v : new BigDecimal[]{in.capacity(), in.expectedValue(), in.orderValue()})
            if (v != null && v.signum() < 0) throw bad("Values cannot be negative");
        e.setCustomerId(in.customerId());
        e.setBusinessLine(BusinessLine.of(in.businessLine()).name());
        e.setTitle(title);
        e.setCapacity(in.capacity());
        e.setSource(trim(in.source()));
        e.setOwnerId(owner);
        e.setExpectedValue(in.expectedValue());
        e.setNextFollowUp(in.nextFollowUp());
        e.setNotes(trim(in.notes()));
        e.setOrderNo(trim(in.orderNo()));
        e.setOrderDate(in.orderDate());
        e.setOrderValue(in.orderValue());
        e.setDeliveryDate(in.deliveryDate());
        e.setInstallationDate(in.installationDate());
        e.setCommissioningDate(in.commissioningDate());
        e.setWarrantyUntil(in.warrantyUntil());
        e.setAmcUntil(in.amcUntil());
        if (EnquiryStage.LOST.name().equals(e.getStage())) e.setLostReason(trim(in.lostReason()) == null ? e.getLostReason() : trim(in.lostReason()));
        e.setUpdatedAt(Instant.now());
    }

    private List<Row> rows(AuthUser me, List<Enquiry> list) {
        Map<Long, String> names = names(me);
        Map<Long, Customer> cust = customers.findByTenantIdOrderByNameAsc(me.tenantId()).stream().collect(Collectors.toMap(Customer::getId, c -> c));
        Map<Long, Long> qcount = new HashMap<>();
        for (Quotation q : quotations.findByOwnerIdOrderByIdDesc(me.tenantId()))
            if (q.getEnquiryId() != null) qcount.merge(q.getEnquiryId(), 1L, Long::sum);
        return list.stream().map(e -> {
            Customer c = cust.get(e.getCustomerId());
            return new Row(e.getId(), e.getRefNo(), e.getBusinessLine(), e.getCustomerId(), c == null ? null : c.getCode(), c == null ? "" : c.getName(), c == null ? null : c.getCity(),
                    e.getTitle(), e.getCapacity(), e.getSource(), e.getOwnerId(), names.get(e.getOwnerId()), e.getStage(), stage(e).label, e.getStageDate(),
                    e.getExpectedValue(), e.getNextFollowUp(), e.getOrderValue(), qcount.getOrDefault(e.getId(), 0L), e.getUpdatedAt());
        }).toList();
    }

    private Map<Long, String> names(AuthUser me) {
        Map<Long, String> m = new HashMap<>();
        for (AppUser u : users.findByTenantIdOrderByIdDesc(me.tenantId())) m.put(u.getId(), u.displayName());
        return m;
    }

    private void event(Enquiry e, AuthUser me, String kind, String stage, LocalDate date, String text) {
        EnquiryEvent ev = new EnquiryEvent();
        ev.setEnquiryId(e.getId()); ev.setKind(kind); ev.setStage(stage); ev.setEventDate(date);
        ev.setText(text.length() > 2000 ? text.substring(0, 2000) : text);
        ev.setUserId(me.userId()); ev.setUserName(me.name()); ev.setAt(Instant.now());
        events.save(ev);
    }

    private String nextRef(Long tenantId) {
        String prefix = "ENQ/" + LocalDate.now().getYear() + "/";
        int max = 0;
        for (String r : repo.findRefNos(tenantId, prefix + "%")) {
            try { max = Math.max(max, Integer.parseInt(r.substring(prefix.length()).trim())); } catch (NumberFormatException ignored) { }
        }
        return prefix + (max + 1);
    }

    private static EnquiryStage stage(Enquiry e) { EnquiryStage s = EnquiryStage.of(e.getStage()); return s == null ? EnquiryStage.NEW : s; }
    private static BigDecimal sum(java.util.stream.Stream<BigDecimal> s) { return s.filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add); }
    private static String trim(String s) { return s == null || s.isBlank() ? null : s.trim(); }
    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
}
