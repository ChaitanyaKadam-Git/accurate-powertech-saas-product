package com.saas.quote;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity @Table(name = "app_user") @Getter @Setter
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(unique = true, nullable = false) private String email;
    @Column(nullable = false) private String password;

    // ---- access control. The company owner has tenantId == id; employees point at the owner. ----
    private Long tenantId;
    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "role_id") private Role role;
    private String name, mobile, designation, department;
    private Boolean active;                 // null (accounts created before this feature) counts as active
    private Boolean mustChangePassword;     // true until the person replaces the temporary password
    private Instant createdAt, lastLoginAt;

    /** Per-user overrides on top of the role: extra rights granted, and role rights revoked. "MODULE:RIGHT" */
    @ElementCollection(fetch = FetchType.EAGER) @CollectionTable(name = "user_grant", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "right_key") private Set<String> grants = new HashSet<>();
    @ElementCollection(fetch = FetchType.EAGER) @CollectionTable(name = "user_revoke", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "right_key") private Set<String> revokes = new HashSet<>();

    public boolean enabled() { return active == null || active; }
    public boolean mustChange() { return mustChangePassword != null && mustChangePassword; }
    public boolean isAdminUser() { return role != null && role.isAdminRole(); }
    public String displayName() { return name != null && !name.isBlank() ? name : (companyName != null && !companyName.isBlank() ? companyName : email); }

    // company details shown on every PDF page and in the cover letter
    private String companyName;
    @Column(length = 600) private String address;
    private String logoKey;            // top-right of every page
    private String coverImageKey;      // default cover page image
    private String diagramImageKey;    // system description diagram
    private String signatoryName;      // e.g. the manager who signs the letter
    private String signatoryTitle;
    private String signatoryPhone;
    private String signatoryEmail;
    @Column(length = 15) private String gstin;   // company GSTIN, printed on invoices
}
