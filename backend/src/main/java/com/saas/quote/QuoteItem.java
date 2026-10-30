package com.saas.quote;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Entity @Getter @Setter
public class QuoteItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private int sno;
    private String equipment;
    @Column(length = 1000) private String specification;
    private String make;
    private String uom;
    private BigDecimal qty = BigDecimal.ONE;
    private BigDecimal unitPrice = BigDecimal.ZERO;
    @Column(length = 8) private String hsn;   // HSN / SAC code, printed on invoices
    private String imageKey;   // key in S3 / local storage, null when no photo

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "quotation_id") @JsonIgnore
    private Quotation quotation;

    public BigDecimal getAmount() {
        return (qty == null || unitPrice == null) ? BigDecimal.ZERO : qty.multiply(unitPrice);
    }
}
