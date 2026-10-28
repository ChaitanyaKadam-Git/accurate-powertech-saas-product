package com.saas.quote;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.*;

@RestController @RequestMapping("/api/dashboard") @RequiredArgsConstructor @Transactional(readOnly = true)
public class DashboardController {
    private final QuotationRepo repo;

    record Month(String label, BigDecimal total, long count) {}
    record Recent(Long id, String refNo, String clientName, String projectLocation, LocalDate quoteDate, BigDecimal total) {}
    record Stats(long thisMonthCount, BigDecimal thisMonthValue, long totalCount, BigDecimal totalValue, long clients,
                 List<Month> months, List<Recent> recent) {}

    @GetMapping @Requires(module = AppModule.QUOTATIONS, right = Right.VIEW)
    public Stats get(@AuthenticationPrincipal AuthUser me) {
        List<Quotation> all = me.seesAllQuotations() ? repo.findByOwnerIdOrderByIdDesc(me.tenantId())
                : repo.findByOwnerIdAndCreatedByOrderByIdDesc(me.tenantId(), me.userId());
        YearMonth now = YearMonth.now();
        Map<Long, BigDecimal> totals = new HashMap<>();
        Set<String> clients = new HashSet<>();
        BigDecimal totalValue = BigDecimal.ZERO;
        for (Quotation q : all) {
            BigDecimal t = q.getTotal();
            totals.put(q.getId(), t);
            totalValue = totalValue.add(t);
            if (q.getClientName() != null && !q.getClientName().isBlank()) clients.add(q.getClientName().trim().toLowerCase());
        }

        List<Month> months = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth ym = now.minusMonths(i);
            BigDecimal sum = BigDecimal.ZERO;
            long n = 0;
            for (Quotation q : all) {
                if (q.getQuoteDate() != null && YearMonth.from(q.getQuoteDate()).equals(ym)) { sum = sum.add(totals.get(q.getId())); n++; }
            }
            months.add(new Month(ym.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH), sum, n));
        }
        Month cur = months.get(months.size() - 1);

        List<Recent> recent = all.stream().limit(6)
                .map(q -> new Recent(q.getId(), q.getRefNo(), q.getClientName(), q.getProjectLocation(), q.getQuoteDate(), totals.get(q.getId())))
                .toList();
        return new Stats(cur.count(), cur.total(), all.size(), totalValue, clients.size(), months, recent);
    }
}
