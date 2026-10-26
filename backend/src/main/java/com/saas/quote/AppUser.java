package com.saas.quote;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "app_user") @Getter @Setter
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(unique = true, nullable = false) private String email;
    @Column(nullable = false) private String password;

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
}
