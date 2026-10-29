package com.saas.quote;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

/** A sub-product of a Product: one line of its bill of material. Same fields as a statement item. */
@Entity @Getter @Setter
public class ProductPart {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private int sno;
    @Column(nullable = false) private String name;
    @Column(length = 1000) private String specification;
    private String make;
    private String uom;
    private BigDecimal qty = BigDecimal.ONE;
    private BigDecimal unitPrice = BigDecimal.ZERO;
    private String imageKey;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "product_id") @JsonIgnore
    private Product product;

    public BigDecimal getAmount() { return (qty == null || unitPrice == null) ? BigDecimal.ZERO : qty.multiply(unitPrice); }
}
