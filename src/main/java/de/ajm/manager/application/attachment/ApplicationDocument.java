package de.ajm.manager.application.attachment;

import de.ajm.manager.application.Application;
import de.ajm.manager.document.Document;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * One document enclosed with one application - "the CV and the two certificates
 * that went to Nordlicht".
 *
 * <p>Both foreign keys carry {@code ON DELETE CASCADE}, as {@code DefaultDocument}'s
 * does: deleting a document or an application drops the link in the database,
 * without {@code DocumentService} or {@code ApplicationService} having to know this
 * table exists.
 *
 * <p>No position column: the enclosures are read in a fixed order (CV, then
 * certificates, then the rest), which {@link ApplicationDocumentService} applies.
 */
@Entity
@Table(name = "application_documents",
        uniqueConstraints = @UniqueConstraint(columnNames = {"application_id", "document_id"}))
@Getter
@Setter
@NoArgsConstructor
public class ApplicationDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Document document;

    public ApplicationDocument(Application application, Document document) {
        this.application = application;
        this.document = document;
    }
}
