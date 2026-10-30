package com.saas.quote;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** One section of a template; copied into a document as a QuoteSection. */
@Entity @Getter @Setter
public class TemplateSection {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private int position;
    @Column(nullable = false, length = 20) private String kind = "TEXT";    // TEXT | BOM (item table) | OFFER (totals)
    private String title;
    @Column(columnDefinition = "TEXT") private String content;
    @Column(length = 20) private String style = "BULLETS";                 // BULLETS | PARAGRAPH | TABLE
    private boolean visible = true;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "template_id") @JsonIgnore
    private DocTemplate template;
}
