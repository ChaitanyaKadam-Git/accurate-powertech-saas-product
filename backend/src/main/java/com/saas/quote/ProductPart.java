package com.saas.quote;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

/** One part of a Product: a Part from the master and how many the product needs. Everything else comes from the Part. */
@Entity @Getter @Setter
public class ProductPart {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private int sno;
    @ManyToOne(fetch = FetchType.EAGER, optional = false) @JoinColumn(name = "part_id") @JsonIgnore
    private Part part;
    private BigDecimal qty = BigDecimal.ONE;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "product_id") @JsonIgnore
    private Product product;

    // read-only view of the master part, for the product list and the statement form
    public Long getPartId() { return part.getId(); }
    public String getName() { return part.getName(); }
    public String getCategory() { return part.getCategory(); }
    public String getSpecification() { return part.getSpecification(); }
    public String getMake() { return part.getMake(); }
    public String getUom() { return part.getUom(); }
    public BigDecimal getUnitPrice() { return part.getUnitPrice(); }
    public String getImageKey() { return part.getImageKey(); }
    public boolean isPartActive() { return part.isActive(); }
    public BigDecimal getAmount() { return (qty == null || part.getUnitPrice() == null) ? BigDecimal.ZERO : qty.multiply(part.getUnitPrice()); }
}
