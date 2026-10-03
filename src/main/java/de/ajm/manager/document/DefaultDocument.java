package de.ajm.manager.document;

import de.ajm.manager.types.Language;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * The document a user has picked as their default of one type in one language -
 * "my German CV", "my English cover letter template". One row per slot, so a new
 * {@link Language} needs no schema change.
 *
 * <p>{@code type} and {@code language} repeat what the document itself carries,
 * deliberately: the unique constraint can only be declared over columns of this
 * table, and it is what makes "only one default per slot" hold under concurrent
 * requests. The price is that a document whose type or language changes no longer
 * fits its slot, which {@code DocumentService.update} handles by vacating it.
 *
 * <p>{@code @OnDelete(CASCADE)} puts {@code ON DELETE CASCADE} on the foreign key,
 * so deleting a document vacates its slot without {@code DocumentService} having to
 * know this table exists - unlike {@code share.document_id}, which has no cascade.
 *
 * <p>{@code @Getter}/{@code @Setter} rather than {@code @Data}: a generated
 * {@code equals}/{@code hashCode} over a mutable id and a lazy association is wrong
 * for an entity, and identity is all this one needs.
 */
@Entity
@Table(name = "default_documents",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "type", "language"}))
@Getter
@Setter
@NoArgsConstructor
public class DefaultDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Language language;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Document document;
}
