package com.saas.quote;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Masters > Products: a sellable setup of one business line (for example "125 kVA DG set" or "30 kWp rooftop solar")
 * made of parts. Picking it in a statement copies every part into the statement's items.
 */
@Entity @Getter @Setter
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @JsonIgnore @Column(nullable = false) private Long tenantId;
    @Column(nullable = false, length = 20) private String businessLine = BusinessLine.SOLAR.name();
    @Column(nullable = false) private String name;
    private String code;                    // model / product code, e.g. KG1-125WS
    private BigDecimal capacity;            // kVA for gensets, kWp for solar
    @Column(length = 1000) private String description;
    private boolean active = true;          // inactive products stay in the master but are not offered in statements
    @JsonIgnore private Long createdBy;
    private Instant createdAt, updatedAt;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sno ASC")
    private List<ProductPart> parts = new ArrayList<>();

    /** Sum of all parts (qty x unit price), shown in the list. */
    public BigDecimal getTotal() { return parts.stream().map(ProductPart::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add); }
}
