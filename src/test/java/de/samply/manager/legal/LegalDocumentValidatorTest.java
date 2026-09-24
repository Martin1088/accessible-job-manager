package de.samply.manager.legal;

import de.samply.manager.legal.LegalDocument.Block;
import de.samply.manager.legal.LegalDocument.Definition;
import de.samply.manager.legal.LegalDocument.Section;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LegalDocumentValidatorTest {

    private static LegalDocument document(Section... sections) {
        return new LegalDocument("en", "Privacy policy", List.of("Intro."), List.of(sections));
    }

    private static Section section(Block... blocks) {
        return new Section("Heading", List.of(blocks));
    }

    @Test
    void acceptsAllThreeBlockTypes() {
        LegalDocument valid = document(section(
                new Block.Paragraph("A lead-in."),
                new Block.Bullets(List.of("One.", "Two.")),
                new Block.Definitions(List.of(new Definition("Email", "a@b.test", "mailto:a@b.test")))));

        assertThat(LegalDocumentValidator.validate(valid, "x.en.yaml", "en")).isEmpty();
    }

    @Test
    void rejectsAMissingTitle() {
        LegalDocument noTitle = new LegalDocument("en", "  ", List.of(), List.of(section(new Block.Paragraph("a"))));

        assertThat(LegalDocumentValidator.validate(noTitle, "x.en.yaml", "en"))
                .anySatisfy(defect -> assertThat(defect).contains("'title' is missing"));
    }

    @Test
    void rejectsADocumentWithNoSections() {
        LegalDocument empty = new LegalDocument("en", "Title", List.of(), List.of());

        assertThat(LegalDocumentValidator.validate(empty, "x.en.yaml", "en"))
                .anySatisfy(defect -> assertThat(defect).contains("'sections' is missing or empty"));
    }

    @Test
    void rejectsASectionWithNoBlocks() {
        LegalDocument bare = document(new Section("Your rights", List.of()));

        assertThat(LegalDocumentValidator.validate(bare, "x.en.yaml", "en"))
                .anySatisfy(defect -> assertThat(defect).contains("section 1").contains("has no 'blocks'"));
    }

    @Test
    void rejectsAnEmptyList() {
        assertThat(LegalDocumentValidator.validate(document(section(new Block.Bullets(List.of()))), "x.en.yaml", "en"))
                .anySatisfy(defect -> assertThat(defect).contains("is a list with no 'items'"));
    }

    @Test
    void rejectsADefinitionMissingItsValue() {
        LegalDocument termOnly = document(section(new Block.Definitions(List.of(new Definition("Name", null, null)))));

        assertThat(LegalDocumentValidator.validate(termOnly, "x.en.yaml", "en"))
                .anySatisfy(defect -> assertThat(defect).contains("has no 'value'"));
    }

    @Test
    void rejectsAHrefSchemeThatIsNotAllowed() {
        LegalDocument hostile = document(section(new Block.Definitions(
                List.of(new Definition("Name", "Click", "javascript:alert(1)")))));

        assertThat(LegalDocumentValidator.validate(hostile, "x.en.yaml", "en"))
                .anySatisfy(defect -> assertThat(defect).contains("javascript:alert(1)").contains("not allowed"));
    }

    @Test
    void rejectsALanguageThatDisagreesWithTheFilename() {
        LegalDocument mislabelled = new LegalDocument("en", "Impressum", List.of(),
                List.of(section(new Block.Paragraph("a"))));

        assertThat(LegalDocumentValidator.validate(mislabelled, "impressum.de.yaml", "de"))
                .anySatisfy(defect -> assertThat(defect)
                        .contains("declares language 'en'").contains("filename says 'de'"));
    }

    @Test
    void reportsEveryDefectAtOnce() {
        LegalDocument broken = new LegalDocument("en", null, List.of(),
                List.of(new Section(null, List.of(new Block.Bullets(List.of()),
                        new Block.Definitions(List.of(new Definition(null, null, "ftp://x")))))));

        // Three restarts to find three typos is the failure mode this avoids.
        assertThat(LegalDocumentValidator.validate(broken, "x.en.yaml", "en")).hasSizeGreaterThanOrEqualTo(5);
    }
}
