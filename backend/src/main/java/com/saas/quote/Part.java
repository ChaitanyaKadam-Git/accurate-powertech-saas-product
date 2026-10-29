package com.saas.quote;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Masters > Parts: every part or component once, with its price. Products list parts from here with a quantity, so a
 * price change applies to every product that uses the part. Statements copy the values (they never change afterwards).
 */
@Entity @Getter @Setter
public class Part {
    public static final String BOTH = "BOTH";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @JsonIgnore @Column(nullable = false) private Long tenantId;
    @Column(nullable = false) private String name;
    private String category;                // engine, alternator, module, inverter, cable ...
    @Column(nullable = false, length = 20) private String businessLine = BOTH;   // SOLAR | GENSET | BOTH
    @Column(length = 1000) private String specification;
    private String make;
    private String uom;
    private BigDecimal unitPrice = BigDecimal.ZERO;
    private String hsnCode;
    private String imageKey;
    private boolean active = true;
    @JsonIgnore private Long createdBy;
    private Instant createdAt, updatedAt;

    /** True when a product of this line may use the part. */
    public boolean fits(String line) { return BOTH.equals(businessLine) || businessLine.equals(line); }
}
