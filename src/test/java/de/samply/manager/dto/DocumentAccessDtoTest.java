package de.samply.manager.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.samply.manager.model.Company;
import de.samply.manager.model.CompanyPosition;
import de.samply.manager.model.Document;
import de.samply.manager.model.DocumentAccess;
import de.samply.manager.model.DocumentType;
import de.samply.manager.types.Language;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Returning the DocumentAccess entity exposed everything reachable from it -
 * the document's storage key and owner, and through the linked position the
 * contact address and private notes. These assert the share reports only the
 * share.
 */
class DocumentAccessDtoTest {

    private final ObjectMapper json = new ObjectMapper();

    private DocumentAccess grantedShare() {
        Company company = new Company();
        company.setId(1L);
        company.setName("Muster GmbH");
        company.setUserId("owner-sub");

        CompanyPosition position = new CompanyPosition();
        position.setId(2L);
        position.setTitle("Softwareentwickler");
        position.setContactLastName("Schmidt");
        position.setEmail("bewerbung@muster.de");
        position.setNotes("internal note");
        position.setCompany(company);
        company.getPositions().add(position);

        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setUserId("owner-sub");
        document.setType(DocumentType.OTHER);
        document.setLanguage(Language.GERMAN);
        document.setLabel("Snapshot");
        document.setFilename("s.pdf");
        document.setMimeType("application/pdf");
        document.setStorageKey("bucket/secret/path/s.pdf");
        document.setCompanyPosition(position);

        DocumentAccess access = new DocumentAccess();
        access.setId(UUID.randomUUID());
        access.setDocument(document);
        access.setReviewerId("reviewer-sub");
        access.setGrantedByUserId("owner-sub");
        return access;
    }

    @Test
    void carriesTheShareItself() {
        DocumentAccess access = grantedShare();

        DocumentAccessDto dto = DocumentAccessDto.from(access);

        assertThat(dto.id()).isEqualTo(access.getId());
        assertThat(dto.documentId()).isEqualTo(access.getDocument().getId());
        assertThat(dto.reviewerId()).isEqualTo("reviewer-sub");
    }

    @Test
    void carriesNothingReachableThroughTheDocument() throws Exception {
        String result = json.writeValueAsString(DocumentAccessDto.from(grantedShare()));

        assertThat(result)
                .doesNotContain("bucket/secret/path/s.pdf")
                .doesNotContain("owner-sub")
                .doesNotContain("bewerbung@muster.de")
                .doesNotContain("internal note")
                .doesNotContain("Softwareentwickler")
                .doesNotContain("Muster GmbH");
    }

    /** A share whose document was already detached must not blow up the response. */
    @Test
    void survivesAMissingDocument() {
        DocumentAccess access = grantedShare();
        access.setDocument(null);

        assertThat(DocumentAccessDto.from(access).documentId()).isNull();
    }
}
