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
    public List<FixedSection> all(@AuthenticationPrincipal AuthUser me, @RequestParam(defaultValue = "SOLAR") String line) { return service.ensure(me.tenantId(), BusinessLine.of(line)); }

    @PutMapping @Transactional @Requires(module = AppModule.TEMPLATE_DATA, right = Right.EDIT)
    public List<FixedSection> save(@AuthenticationPrincipal AuthUser me, @RequestParam(defaultValue = "SOLAR") String line, @RequestBody List<FixedSection> in) {
        BusinessLine bl = BusinessLine.of(line);
        Long uid = me.tenantId();
        for (FixedSection x : in) {
            if (x.getId() == null) continue;
            repo.findById(x.getId()).filter(s -> s.getOwnerId().equals(uid) && bl.name().equals(s.getBusinessLine())).ifPresent(s -> {
                s.setTitle(x.getTitle());
                s.setContent(x.getContent());
            });
        }
        audit.log(me, AppModule.TEMPLATE_DATA, "EDIT", "Standard text", null, bl.label + ": " + in.size() + " section(s) saved");
        return repo.findByOwnerIdAndBusinessLineOrderByPosition(uid, bl.name());
    }

    /** Adds a standard section that every new statement starts with. */
    @PostMapping @Requires(module = AppModule.TEMPLATE_DATA, right = Right.CREATE)
    public FixedSection add(@AuthenticationPrincipal AuthUser me, @RequestParam(defaultValue = "SOLAR") String line, @RequestBody FixedSection in) {
        FixedSection s = new FixedSection();
        s.setOwnerId(me.tenantId());
        s.setBusinessLine(BusinessLine.of(line).name());
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
    public List<FixedSection> reset(@AuthenticationPrincipal AuthUser me, @RequestParam(defaultValue = "SOLAR") String line) {
        BusinessLine bl = BusinessLine.of(line);
        audit.log(me, AppModule.TEMPLATE_DATA, "EDIT", "Standard text", null, bl.label + ": reset to built-in text");
        return service.reset(me.tenantId(), bl);
    }
}
