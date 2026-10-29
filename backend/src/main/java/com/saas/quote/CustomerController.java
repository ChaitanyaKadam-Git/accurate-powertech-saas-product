package com.saas.quote;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

/** Masters > Customers. Every company sees only its own customers; rights come from the CUSTOMERS module. */
@RestController @RequestMapping("/api/customers") @RequiredArgsConstructor @Transactional
public class CustomerController {
    private final CustomerRepo repo;
    private final AuditService audit;

    static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    static final Pattern GSTIN = Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$");

    record Save(String name, String contactPerson, String phone, String email, String gstin, String address, String city, String notes) {}

    /** The statement form uses this list too, so quotation creators can read it without the CUSTOMERS module. */
    @GetMapping
    public List<Customer> list(@AuthenticationPrincipal AuthUser me) {
        if (!me.can(AppModule.CUSTOMERS, Right.VIEW) && !me.canAny(AppModule.QUOTATIONS, Right.CREATE, Right.EDIT))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to view (" + AppModule.CUSTOMERS.label + ")");
        return repo.findByTenantIdOrderByNameAsc(me.tenantId());
    }

    @GetMapping("/{id}") @Requires(module = AppModule.CUSTOMERS, right = Right.VIEW)
    public Customer get(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) { return find(id, me); }

    @PostMapping @Requires(module = AppModule.CUSTOMERS, right = Right.CREATE)
    public Customer create(@AuthenticationPrincipal AuthUser me, @RequestBody Save in) {
        Customer c = new Customer();
        c.setTenantId(me.tenantId());
        c.setCreatedBy(me.userId());
        c.setCreatedAt(Instant.now());
        apply(c, in, me);
        Customer saved = repo.save(c);
        audit.log(me, AppModule.CUSTOMERS, "CREATE", "Customer", saved.getId(), saved.getName());
        return saved;
    }

    @PutMapping("/{id}") @Requires(module = AppModule.CUSTOMERS, right = Right.EDIT)
    public Customer update(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @RequestBody Save in) {
        Customer c = find(id, me);
        String before = c.getName();
        apply(c, in, me);
        audit.log(me, AppModule.CUSTOMERS, "EDIT", "Customer", c.getId(), before.equals(c.getName()) ? c.getName() : before + " -> " + c.getName());
        return repo.save(c);
    }

    /** Statements keep their own copy of the client details, so deleting a customer never changes an existing statement. */
    @DeleteMapping("/{id}") @Requires(module = AppModule.CUSTOMERS, right = Right.DELETE)
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        Customer c = find(id, me);
        audit.log(me, AppModule.CUSTOMERS, "DELETE", "Customer", c.getId(), c.getName());
        repo.delete(c);
    }

    private Customer find(Long id, AuthUser me) {
        return repo.findByIdAndTenantId(id, me.tenantId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
    }

    private void apply(Customer c, Save in, AuthUser me) {
        String name = trim(in.name());
        if (name == null) throw bad("Customer name is required");
        repo.findFirstByTenantIdAndNameIgnoreCase(me.tenantId(), name).filter(x -> !x.getId().equals(c.getId()))
                .ifPresent(x -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "A customer named \"" + name + "\" already exists"); });
        String phone = trim(in.phone());
        if (phone != null && !phone.matches("\\d{1,10}")) throw bad("Phone must be digits only, up to 10 digits");
        String email = trim(in.email());
        if (email != null && !EMAIL.matcher(email).matches()) throw bad("Enter a valid email address");
        String gstin = trim(in.gstin());
        if (gstin != null) { gstin = gstin.toUpperCase(); if (!GSTIN.matcher(gstin).matches()) throw bad("GSTIN must be 15 characters, for example 27ABCDE1234F1Z5"); }
        c.setName(name);
        c.setContactPerson(trim(in.contactPerson()));
        c.setPhone(phone);
        c.setEmail(email == null ? null : email.toLowerCase());
        c.setGstin(gstin);
        c.setAddress(trim(in.address()));
        c.setCity(trim(in.city()));
        c.setNotes(trim(in.notes()));
        c.setUpdatedAt(Instant.now());
    }

    private static String trim(String s) { return s == null || s.isBlank() ? null : s.trim(); }
    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
}
