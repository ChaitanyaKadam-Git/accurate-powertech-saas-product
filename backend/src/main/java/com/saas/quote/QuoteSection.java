package com.saas.quote;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;

/** One chapter of a statement's PDF. Order, title, text, format and photos are all editable per statement. */
@Entity @Getter @Setter
public class QuoteSection {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private int position;
    private String kind = "TEXT";          // TEXT | BOM (item table) | OFFER (price summary)
    private String title;
    @Column(columnDefinition = "TEXT") private String content;
    private String style = "BULLETS";      // BULLETS | PARAGRAPH | TABLE ("Topic | Details" per line)
    private boolean visible = true;
    private int imageColumns = 2;          // photos per row, 1 to 3

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "quote_section_image", joinColumns = @JoinColumn(name = "section_id"))
    @OrderColumn(name = "idx") @Column(name = "image_key")
    private List<String> imageKeys = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "quotation_id") @JsonIgnore
    private Quotation quotation;
}
