package com.saas.quote;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Masters > Templates: a named starting point for a document of one type (quotation, proforma, invoice) and one business
 * line: its PDF options and its sections. A new document copies them; later template changes do not touch it.
 */
@Entity @Getter @Setter @Table(name = "doc_template")
public class DocTemplate {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @JsonIgnore @Column(nullable = false) private Long tenantId;
    @Column(nullable = false, length = 20) private String docType = DocType.QUOTATION.name();
    @Column(nullable = false, length = 20) private String businessLine = BusinessLine.SOLAR.name();
    @Column(nullable = false) private String name;
    @com.fasterxml.jackson.annotation.JsonProperty("isDefault") private boolean isDefault;
    private boolean active = true;
    private String pdfTitle;              // big title on the cover / top of the document; empty = the built-in title
    private boolean showCover = true;     // quotations: cover page with image
    private boolean showLetter = true;    // quotations: cover letter page
    private boolean showPrices = true;    // item prices in the bill of material
    @Column(length = 600) private String description;
    @JsonIgnore private Long createdBy;
    private Instant createdAt, updatedAt;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<TemplateSection> sections = new ArrayList<>();
}
