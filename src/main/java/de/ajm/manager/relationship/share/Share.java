package de.ajm.manager.relationship.share;

import de.ajm.manager.coverletter.template.HtmlLetterTemplate;
import de.ajm.manager.document.Document;
import de.ajm.manager.relationship.Relationship;

import java.time.LocalDateTime;
import java.util.UUID;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "share")
@Getter
@Setter
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

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime grantedAt;

    private LocalDateTime revokedAt;

    public boolean isActive() {
        return revokedAt == null;
    }
}
