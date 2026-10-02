package de.ajm.manager.relationship.share;

import de.ajm.manager.coverletter.template.HtmlLetterTemplate;
import de.ajm.manager.document.Document;
import de.ajm.manager.relationship.Relationship;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "share")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Share {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "relationship_id", nullable = false)
    private Relationship relationship;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SharedSubject subjectType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "html_letter_id")
    private HtmlLetterTemplate htmlLetterTemplate;

    @CreationTimestamp
    private LocalDateTime grantedAt;

    private LocalDateTime revokedAt;

    public boolean isActive() {
        return revokedAt == null;
    }
}
