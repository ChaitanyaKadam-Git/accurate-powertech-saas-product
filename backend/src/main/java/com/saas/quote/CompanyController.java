package com.saas.quote;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/company") @RequiredArgsConstructor @Transactional
public class CompanyController {
    private final UserRepo users;
    private final StorageService storage;
    private final AuditService audit;

    record Company(String companyName, String address, String logoKey, String coverImageKey, String diagramImageKey,
                   String signatoryName, String signatoryTitle, String signatoryPhone, String signatoryEmail) {}

    /** Company name and logo are shown to every signed-in person; the details live on the company owner's record. */
    @GetMapping
    public Company get(@AuthenticationPrincipal AuthUser me) { return read(me.tenantId()); }

    private Company read(Long tid) {
        AppUser u = users.findById(tid).orElseThrow();
        return new Company(u.getCompanyName(), u.getAddress(), u.getLogoKey(), u.getCoverImageKey(), u.getDiagramImageKey(),
                u.getSignatoryName(), u.getSignatoryTitle(), u.getSignatoryPhone(), u.getSignatoryEmail());
    }

    @PutMapping @Requires(module = AppModule.TEMPLATES, right = Right.EDIT)
    public Company put(@AuthenticationPrincipal AuthUser me, @RequestBody Company c) {
        Long uid = me.tenantId();
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
        audit.log(me, AppModule.TEMPLATES, "EDIT", "Company", uid, "Company header, logo and signatory");
        return read(uid);
    }
}
