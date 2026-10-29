package com.saas.quote;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

/** Customer master: clients a company quotes for. Shared by everyone in the company; picked in the statement form. */
@Entity @Getter @Setter @Table(indexes = @Index(columnList = "tenantId,name"))
public class Customer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @JsonIgnore @Column(nullable = false) private Long tenantId;
    @Column(nullable = false) private String name;
    private String contactPerson;
    private String phone;              // digits only, up to 10 (same rule as the statement form)
    private String email;
    private String gstin;
    @Column(length = 600) private String address;
    private String city;               // used as the project location in a new statement
    @Column(length = 1000) private String notes;
    @JsonIgnore private Long createdBy;
    private Instant createdAt, updatedAt;
}
