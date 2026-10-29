package com.saas.quote;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity @Getter @Setter
public class Quotation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @JsonIgnore private Long ownerId;       // the company (tenant) that owns the record
    @JsonIgnore private Long createdBy;     // the person who created it (null on records made before roles existed)

    @Column(nullable = false, length = 20) private String businessLine = BusinessLine.SOLAR.name();   // SOLAR | GENSET
    private Long enquiryId;                 // the enquiry (customer flow) this statement was made for, if any
    private String refNo;
    private LocalDate quoteDate = LocalDate.now();
    private String clientName;
    @Column(length = 600) private String clientAddress;
    private String contactPerson;
    private String phone;
    private String projectLocation;
    private BigDecimal capacityKw = new BigDecimal("30");
    private BigDecimal gstPercent = new BigDecimal("8.9");
    private boolean showItemPrices = true;

    @Column(columnDefinition = "TEXT") private String paymentTerms;   // when empty, the standard payment terms are used
    private String coverImageKey;                                      // overrides the company's default cover image
    private String signatoryName, signatoryTitle, signatoryPhone, signatoryEmail;   // override the company defaults

    @Column(columnDefinition = "TEXT") private String scopeText;    // variable scope of work, one bullet per line
    @Column(columnDefinition = "TEXT") private String inclusions;
    @Column(columnDefinition = "TEXT") private String exclusions;

    @OneToMany(mappedBy = "quotation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sno ASC")
    private List<QuoteItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "quotation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<QuoteSection> sections = new ArrayList<>();

    // computed, serialized to JSON
    public BigDecimal getSubtotal() {
        return items.stream().map(QuoteItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    public BigDecimal getGstAmount() {
        BigDecimal p = gstPercent == null ? BigDecimal.ZERO : gstPercent;
        return getSubtotal().multiply(p).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
    public BigDecimal getTotal() { return getSubtotal().add(getGstAmount()); }
}
