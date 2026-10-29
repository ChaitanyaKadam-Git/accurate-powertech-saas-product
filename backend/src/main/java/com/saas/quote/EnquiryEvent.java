package com.saas.quote;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.time.LocalDate;

/** Timeline of an enquiry: created, stage changes, calls, visits, notes and statements made for it. */
@Entity @Getter @Setter @Table(indexes = @Index(columnList = "enquiryId"))
public class EnquiryEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @JsonIgnore @Column(nullable = false) private Long enquiryId;
    @Column(nullable = false, length = 20) private String kind;           // CREATED | STAGE | CALL | VISIT | NOTE | QUOTATION
    private String stage;                                                 // for STAGE: the stage reached
    private LocalDate eventDate;                                          // the business date (may differ from when it was typed)
    @Column(length = 2000) private String text;
    private Long userId;
    private String userName;
    private Instant at;
}
