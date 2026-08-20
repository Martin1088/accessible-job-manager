package de.samply.manager.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.samply.manager.model.Company;
import de.samply.manager.model.CompanyPosition;
import de.samply.manager.model.Document;
import de.samply.manager.model.DocumentType;
import de.samply.manager.types.Language;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * A Document linked to a CompanyPosition sits on a cycle:
 * Document -> companyPosition -> company -> positions -> the same position.
 * Serializing the entity ran round it until the stack overflowed, which is
 * what broke listing and uploading documents once any snapshot existed.
 */
class DocumentSerializationTest {

    private final ObjectMapper json = new ObjectMapper();

    /** A document of the kind /api/posting/snapshot creates: attached to a position. */
    private Document snapshotOfAPosition() {
        Company company = new Company();
        company.setId(1L);
        company.setName("Muster Technik GmbH");

        CompanyPosition position = new CompanyPosition();
        position.setId(2L);
        position.setTitle("Softwareentwickler");
        position.setCompany(company);
        company.getPositions().add(position);

        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setUserId("test-sub");
        document.setType(DocumentType.OTHER);
        document.setLanguage(Language.GERMAN);
        document.setLabel("Snapshot");
        document.setFilename("snapshot.pdf");
        document.setMimeType("application/pdf");
        document.setStorageKey("key");
        document.setCompanyPosition(position);
        return document;
    }

    @Test
    void theDtoSerializes_withoutWalkingTheEntityCycle() {
        assertThatCode(() -> json.writeValueAsString(DocumentDto.from(snapshotOfAPosition())))
                .doesNotThrowAnyException();
    }

    @Test
    void theDtoCarriesTheFieldsTheClientReads() throws Exception {
        String result = json.writeValueAsString(DocumentDto.from(snapshotOfAPosition()));

        // `type` is what the documents view filters on - a DTO without it would
        // serialize happily and still break the client.
        assertThat(result).contains("\"type\":\"OTHER\"")
                .contains("\"language\":\"GERMAN\"")
                .contains("\"label\":\"Snapshot\"")
                .contains("\"filename\":\"snapshot.pdf\"")
                .contains("\"mimeType\":\"application/pdf\"");
    }

    /**
     * The back-reference is cut on the entity too, so an endpoint that returns a
     * position (or anything holding one) cannot resurrect the overflow.
     */
    @Test
    void aPositionEntitySerializes_withoutRecursing() {
        CompanyPosition position = snapshotOfAPosition().getCompanyPosition();

        assertThatCode(() -> json.writeValueAsString(position)).doesNotThrowAnyException();
        assertThat(json.valueToTree(position).has("company")).isFalse();
    }
}
