package com.saas.quote;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One customer opportunity followed from the first enquiry to commissioning (the customer flow). Statements made for it
 * point at it (Quotation.enquiryId); every stage change and note is an EnquiryEvent.
 */
@Entity @Getter @Setter @Table(indexes = {@Index(columnList = "tenantId,stage"), @Index(columnList = "customerId")})
public class Enquiry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @JsonIgnore @Column(nullable = false) private Long tenantId;
    @Column(nullable = false) private String refNo;                       // ENQ/<year>/<n>
    @Column(nullable = false, length = 20) private String businessLine = BusinessLine.SOLAR.name();
    @Column(nullable = false) private Long customerId;
    @Column(nullable = false) private String title;                       // the requirement, e.g. "125 kVA DG set for hospital backup"
    private BigDecimal capacity;
    private String source;                                                // Website, Phone, Referral, Repeat customer, Tender, Walk-in ...
    private Long ownerId;                                                 // salesperson responsible
    @Column(nullable = false, length = 20) private String stage = EnquiryStage.NEW.name();
    private LocalDate stageDate;                                          // when the current stage was reached
    private BigDecimal expectedValue;
    private LocalDate nextFollowUp;
    @Column(length = 600) private String lostReason;
    private String orderNo;
    private LocalDate orderDate;
    private BigDecimal orderValue;
    private LocalDate deliveryDate, installationDate, commissioningDate, warrantyUntil, amcUntil;
    @Column(length = 2000) private String notes;
    @JsonIgnore private Long createdBy;
    private Instant createdAt, updatedAt;
}
