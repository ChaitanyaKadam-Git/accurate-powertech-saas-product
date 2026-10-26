package com.saas.quote;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Text that is identical in every statement (company profile, client scope, T&C ...). One row per tenant per section. */
@Entity @Getter @Setter
public class FixedSection {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @JsonIgnore private Long ownerId;
    private String sectionKey;   // PROFILE | CLIENT_SCOPE | PAYMENT | TERMS
    private int position;
    private String title;
    @Column(columnDefinition = "TEXT") private String content;   // one bullet per line
}
