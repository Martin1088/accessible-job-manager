package de.samply.manager.services;

import org.docx4j.TextUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.HeaderPart;
import org.docx4j.wml.HdrFtrRef;
import org.docx4j.wml.HeaderReference;
import org.docx4j.wml.SectPr;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CoverLetterServiceTest {

    private final CoverLetterService service = new CoverLetterService(
            "http://localhost:1234", new org.springframework.context.support.StaticMessageSource());

    private static final Map<String, String> PERSONAL_DATA = Map.of(
            "senderName", "Jane Doe",
            "senderStreet", "Main Street 1",
            "senderPostalCode", "12345",
            "senderCity", "Springfield",
            "senderEmail", "jane@example.com"
    );

    @Test
    void createTemplateWithHeader_buildsHeaderAndMergeFieldSkeleton() throws Exception {
        byte[] result = service.createTemplateWithHeader(PERSONAL_DATA);

        WordprocessingMLPackage doc = WordprocessingMLPackage.load(new ByteArrayInputStream(result));

        SectPr sectPr = doc.getMainDocumentPart().getJaxbElement().getBody().getSectPr();
        assertThat(sectPr).isNotNull();
        HeaderReference headerRef = sectPr.getEGHdrFtrReferences().stream()
                .filter(HeaderReference.class::isInstance)
                .map(HeaderReference.class::cast)
                .filter(ref -> ref.getType() == HdrFtrRef.DEFAULT)
                .findFirst()
                .orElseThrow();

        HeaderPart headerPart = (HeaderPart) doc.getMainDocumentPart()
                .getRelationshipsPart().getPart(headerRef.getId());
        String headerText = TextUtils.getText(headerPart.getJaxbElement());

        assertThat(headerText).containsSubsequence(
                "Jane Doe", "Main Street 1", "12345", "Springfield", "jane@example.com");

        String bodyXml = doc.getMainDocumentPart().getXML();
        assertThat(bodyXml)
                .contains("MERGEFIELD company")
                .contains("MERGEFIELD street")
                .contains("MERGEFIELD city")
                .contains("MERGEFIELD position")
                .contains("MERGEFIELD contact")
                .contains("DATE \\@")
                .contains("w:ascii=\"Roboto\"")
                .contains("w:val=\"right\"") // date field is right-aligned
                .contains("w:before=\"0\"")  // no spacing between company/street/city lines
                .contains("w:after=\"0\"");

        String bodyText = TextUtils.getText(doc.getMainDocumentPart().getJaxbElement());
        assertThat(bodyText).contains("Bewerbung als", "Sehr");
        assertThat(bodyText).doesNotContain("Mit freundlichen Grüßen", "Anlage:");

        String headerXml = headerPart.getXML();
        assertThat(headerXml)
                .contains("w:ascii=\"Roboto\"")
                .contains("w:before=\"0\"")
                .contains("w:after=\"0\"");
    }

    @Test
    void createTemplateWithHeader_producesTemplateThatFillTemplateCanMerge() throws Exception {
        byte[] template = service.createTemplateWithHeader(PERSONAL_DATA);

        Map<String, String> replacements = Map.of(
                "company", "Acme Corp",
                "street", "Industrial Ave 5",
                "city", "12345 Metropolis",
                "position", "Backend Developer",
                "contact", "geehrter Herr Schmidt",
                "date", LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        );

        byte[] filled = service.fillTemplate(new ByteArrayInputStream(template), replacements);

        WordprocessingMLPackage doc = WordprocessingMLPackage.load(new ByteArrayInputStream(filled));
        String bodyText = TextUtils.getText(doc.getMainDocumentPart().getJaxbElement());

        assertThat(bodyText).contains("Acme Corp", "Industrial Ave 5", "12345 Metropolis",
                "Backend Developer", "geehrter Herr Schmidt");
    }

    @Test
    void extractPlainText_returnsBodyTextWithoutSenderHeaderBlock() throws Exception {
        byte[] template = service.createTemplateWithHeader(PERSONAL_DATA);

        Map<String, String> replacements = Map.of(
                "company", "Acme Corp",
                "street", "Industrial Ave 5",
                "city", "12345 Metropolis",
                "position", "Backend Developer",
                "contact", "geehrter Herr Schmidt",
                "date", LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        );
        byte[] filled = service.fillTemplate(new ByteArrayInputStream(template), replacements);

        String text = service.extractPlainText(filled);

        assertThat(text).contains("Acme Corp", "Industrial Ave 5", "12345 Metropolis",
                "Bewerbung als", "Backend Developer", "Sehr geehrter Herr Schmidt");
        assertThat(text).doesNotContain("Jane Doe", "jane@example.com");
        assertThat(text).doesNotContain("DATE", "\\@");
        assertThat(text).contains(LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
    }
}
