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

    @GetMapping
    public List<FixedSection> all(@AuthenticationPrincipal Long uid) { return service.ensure(uid); }

    @PutMapping @Transactional
    public List<FixedSection> save(@AuthenticationPrincipal Long uid, @RequestBody List<FixedSection> in) {
        for (FixedSection x : in) {
            repo.findById(x.getId()).filter(s -> s.getOwnerId().equals(uid)).ifPresent(s -> {
                s.setTitle(x.getTitle());
                s.setContent(x.getContent());
            });
        }
        return repo.findByOwnerIdOrderByPosition(uid);
    }

    /** Adds a standard section that every new statement starts with. */
    @PostMapping
    public FixedSection add(@AuthenticationPrincipal Long uid, @RequestBody FixedSection in) {
        FixedSection s = new FixedSection();
        s.setOwnerId(uid);
        s.setSectionKey("CUSTOM_" + java.util.UUID.randomUUID());
        s.setPosition(50);
        s.setTitle(in.getTitle() == null || in.getTitle().isBlank() ? "New section" : in.getTitle());
        s.setContent(in.getContent());
        return repo.save(s);
    }

    @DeleteMapping("/{id}")
    public void delete(@AuthenticationPrincipal Long uid, @PathVariable Long id) {
        repo.findById(id).filter(s -> s.getOwnerId().equals(uid) && s.getSectionKey().startsWith("CUSTOM_")).ifPresent(repo::delete);
    }

    @PostMapping("/reset")
    public List<FixedSection> reset(@AuthenticationPrincipal Long uid) { return service.reset(uid); }
}
