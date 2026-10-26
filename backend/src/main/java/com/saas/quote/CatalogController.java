package com.saas.quote;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.*;

/** Items used in earlier statements (latest version of each equipment name), used to auto-fill new rows. */
@RestController @RequestMapping("/api/catalog") @RequiredArgsConstructor
public class CatalogController {
    private final QuoteItemRepo items;

    record Entry(String equipment, String specification, String make, String uom, BigDecimal unitPrice, String imageKey) {}

    @GetMapping @Transactional(readOnly = true)
    public List<Entry> catalog(@AuthenticationPrincipal Long uid) {
        Map<String, Entry> m = new LinkedHashMap<>();
        for (QuoteItem i : items.findByQuotationOwnerIdOrderByIdDesc(uid, PageRequest.of(0, 3000))) {
            if (i.getEquipment() == null || i.getEquipment().isBlank()) continue;
            m.putIfAbsent(i.getEquipment().trim().toLowerCase(),
                    new Entry(i.getEquipment().trim(), i.getSpecification(), i.getMake(), i.getUom(), i.getUnitPrice(), i.getImageKey()));
        }
        return new ArrayList<>(m.values());
    }
}
