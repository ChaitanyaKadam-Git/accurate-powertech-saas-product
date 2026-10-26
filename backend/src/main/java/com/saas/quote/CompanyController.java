package com.saas.quote;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/company") @RequiredArgsConstructor @Transactional
public class CompanyController {
    private final UserRepo users;
    private final StorageService storage;

    record Company(String companyName, String address, String logoKey, String coverImageKey, String diagramImageKey,
                   String signatoryName, String signatoryTitle, String signatoryPhone, String signatoryEmail) {}

    @GetMapping
    public Company get(@AuthenticationPrincipal Long uid) {
        AppUser u = users.findById(uid).orElseThrow();
        return new Company(u.getCompanyName(), u.getAddress(), u.getLogoKey(), u.getCoverImageKey(), u.getDiagramImageKey(),
                u.getSignatoryName(), u.getSignatoryTitle(), u.getSignatoryPhone(), u.getSignatoryEmail());
    }

    @PutMapping
    public Company put(@AuthenticationPrincipal Long uid, @RequestBody Company c) {
        AppUser u = users.findById(uid).orElseThrow();
        if (c.companyName() != null && !c.companyName().isBlank()) u.setCompanyName(c.companyName().trim());
        u.setAddress(c.address());
        u.setLogoKey(storage.own(uid, c.logoKey()));
        u.setCoverImageKey(storage.own(uid, c.coverImageKey()));
        u.setDiagramImageKey(storage.own(uid, c.diagramImageKey()));
        u.setSignatoryName(c.signatoryName());
        u.setSignatoryTitle(c.signatoryTitle());
        u.setSignatoryPhone(c.signatoryPhone());
        u.setSignatoryEmail(c.signatoryEmail());
        users.save(u);
        return get(uid);
    }
}
