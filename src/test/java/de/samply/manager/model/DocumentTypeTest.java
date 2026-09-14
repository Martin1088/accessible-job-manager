package de.samply.manager.model;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentTypeTest {

    private static final byte[] PDF = "%PDF-1.7\n...".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] DOCX = {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00};

    @Test
    void pdfTypesMatchAPdfHeaderAndRejectEverythingElse() {
        assertThat(DocumentType.CV.matchesContent(PDF)).isTrue();
        assertThat(DocumentType.CERTIFICATE.matchesContent(PDF)).isTrue();
        assertThat(DocumentType.JOB_POSTING_SNAPSHOT.matchesContent(PDF)).isTrue();

        assertThat(DocumentType.CV.matchesContent(DOCX)).isFalse();
        assertThat(DocumentType.CV.matchesContent("<html>gotcha".getBytes(StandardCharsets.US_ASCII))).isFalse();
    }

    @Test
    void coverLetterTemplateMatchesAZipHeaderAndRejectsAPdf() {
        assertThat(DocumentType.COVER_LETTER_TEMPLATE.matchesContent(DOCX)).isTrue();
        assertThat(DocumentType.COVER_LETTER_TEMPLATE.matchesContent(PDF)).isFalse();
    }

    @Test
    void contentShorterThanTheSignatureOrAbsentIsRejected() {
        assertThat(DocumentType.CV.matchesContent(new byte[] {'%', 'P'})).isFalse();
        assertThat(DocumentType.CV.matchesContent(new byte[0])).isFalse();
        assertThat(DocumentType.CV.matchesContent(null)).isFalse();
    }
}
