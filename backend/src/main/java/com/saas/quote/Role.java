package com.saas.quote;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.HashSet;
import java.util.Set;

@Entity @Getter @Setter
@Table(name = "app_role", uniqueConstraints = @UniqueConstraint(columnNames = {"tenantId", "name"}))
public class Role {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long tenantId;
    @Column(nullable = false) private String name;
    @Column(length = 300) private String description;
    private boolean systemRole;                 // seeded Admin role: cannot be renamed, edited or deleted
    private boolean adminRole;                  // signs in through the Admin login and holds every right
    private String quotationScope = "OWN";      // OWN = only own quotations, ALL = every quotation of the company

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_right", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "right_key")
    private Set<String> rights = new HashSet<>();   // "MODULE:RIGHT"
}
