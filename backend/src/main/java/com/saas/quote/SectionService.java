package com.saas.quote;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class SectionService {
    private final SectionRepo repo;

    /** Adds any standard section the tenant is missing (older accounts) and fixes the display order. */
    @Transactional
    public List<FixedSection> ensure(Long uid) {
        List<FixedSection> cur = repo.findByOwnerIdOrderByPosition(uid);
        Map<String, FixedSection> have = new HashMap<>();
        for (FixedSection s : cur) have.put(s.getSectionKey(), s);
        List<FixedSection> add = new ArrayList<>();
        for (FixedSection def : DefaultSections.forOwner(uid)) {
            FixedSection ex = have.get(def.getSectionKey());
            if (ex == null) add.add(def); else ex.setPosition(def.getPosition());
        }
        if (!add.isEmpty()) repo.saveAll(add);
        return repo.findByOwnerIdOrderByPosition(uid);
    }

    /** Overwrites every standard section with the built-in text. */
    @Transactional
    public List<FixedSection> reset(Long uid) {
        Map<String, FixedSection> have = new HashMap<>();
        for (FixedSection s : repo.findByOwnerIdOrderByPosition(uid)) have.put(s.getSectionKey(), s);
        for (FixedSection def : DefaultSections.forOwner(uid)) {
            FixedSection ex = have.get(def.getSectionKey());
            if (ex == null) repo.save(def);
            else { ex.setTitle(def.getTitle()); ex.setContent(def.getContent()); ex.setPosition(def.getPosition()); }
        }
        return repo.findByOwnerIdOrderByPosition(uid);
    }

    public Map<String, FixedSection> mapFor(Long uid) {
        Map<String, FixedSection> m = new LinkedHashMap<>();
        for (FixedSection s : ensure(uid)) m.put(s.getSectionKey(), s);
        return m;
    }
}
