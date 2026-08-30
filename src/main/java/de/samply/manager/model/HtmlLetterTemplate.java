package de.samply.manager.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import de.samply.manager.coverletter.StyleSettings;
import de.samply.manager.types.LayoutLetterKey;
import de.samply.manager.types.Block;
import de.samply.manager.types.Language;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "html_letter_template")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HtmlLetterTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Language language;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LayoutLetterKey layoutLetter;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private StyleSettings style;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<Block> blocks;

    @Version
    @Column(nullable = false)
    private long version;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

}
