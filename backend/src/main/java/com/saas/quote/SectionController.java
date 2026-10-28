package com.saas.quote;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/sections") @RequiredArgsConstructor
public class SectionController {
    private final SectionRepo repo;
    private final SectionService service;
    private final AuditService audit;

    @GetMapping @Requires(module = AppModule.TEMPLATE_DATA, right = Right.VIEW)
    public List<FixedSection> all(@AuthenticationPrincipal AuthUser me) { return service.ensure(me.tenantId()); }

    @PutMapping @Transactional @Requires(module = AppModule.TEMPLATE_DATA, right = Right.EDIT)
    public List<FixedSection> save(@AuthenticationPrincipal AuthUser me, @RequestBody List<FixedSection> in) {
        Long uid = me.tenantId();
        for (FixedSection x : in) {
            repo.findById(x.getId()).filter(s -> s.getOwnerId().equals(uid)).ifPresent(s -> {
                s.setTitle(x.getTitle());
                s.setContent(x.getContent());
            });
        }
        audit.log(me, AppModule.TEMPLATE_DATA, "EDIT", "Standard text", null, in.size() + " section(s) saved");
        return repo.findByOwnerIdOrderByPosition(uid);
    }

    /** Adds a standard section that every new statement starts with. */
    @PostMapping @Requires(module = AppModule.TEMPLATE_DATA, right = Right.CREATE)
    public FixedSection add(@AuthenticationPrincipal AuthUser me, @RequestBody FixedSection in) {
        FixedSection s = new FixedSection();
        s.setOwnerId(me.tenantId());
        s.setSectionKey("CUSTOM_" + java.util.UUID.randomUUID());
        s.setPosition(50);
        s.setTitle(in.getTitle() == null || in.getTitle().isBlank() ? "New section" : in.getTitle());
        s.setContent(in.getContent());
        FixedSection saved = repo.save(s);
        audit.log(me, AppModule.TEMPLATE_DATA, "CREATE", "Standard text", saved.getId(), saved.getTitle());
        return saved;
    }

    @DeleteMapping("/{id}") @Requires(module = AppModule.TEMPLATE_DATA, right = Right.DELETE)
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        repo.findById(id).filter(s -> s.getOwnerId().equals(me.tenantId()) && s.getSectionKey().startsWith("CUSTOM_")).ifPresent(s -> {
            audit.log(me, AppModule.TEMPLATE_DATA, "DELETE", "Standard text", s.getId(), s.getTitle());
            repo.delete(s);
        });
    }

    @PostMapping("/reset") @Requires(module = AppModule.TEMPLATE_DATA, right = Right.EDIT)
    public List<FixedSection> reset(@AuthenticationPrincipal AuthUser me) {
        audit.log(me, AppModule.TEMPLATE_DATA, "EDIT", "Standard text", null, "Reset to built-in text");
        return service.reset(me.tenantId());
    }
}
