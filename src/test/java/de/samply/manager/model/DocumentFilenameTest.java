package de.samply.manager.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentFilenameTest {

    @Test
    void dropsAnyDirectoryPart() {
        assertThat(DocumentFilename.sanitize("../../etc/passwd")).isEqualTo("passwd");
        assertThat(DocumentFilename.sanitize("C:\\Users\\bob\\resume.pdf")).isEqualTo("resume.pdf");
        assertThat(DocumentFilename.sanitize("/tmp/cv.pdf")).isEqualTo("cv.pdf");
    }

    @Test
    void replacesCharactersThatCouldBreakAHeader() {
        assertThat(DocumentFilename.sanitize("my cv\".pdf")).isEqualTo("my_cv_.pdf");
        assertThat(DocumentFilename.sanitize("evil\r\nSet-Cookie: x.pdf")).isEqualTo("evil_Set-Cookie_x.pdf");
    }

    @Test
    void stripsLeadingPunctuationAndKeepsAReadableName() {
        assertThat(DocumentFilename.sanitize("...hidden.pdf")).isEqualTo("hidden.pdf");
        assertThat(DocumentFilename.sanitize("normal_file-1.docx")).isEqualTo("normal_file-1.docx");
    }

    @Test
    void truncatesToTwoHundredFiftyFiveCharacters() {
        String raw = "a".repeat(400) + ".pdf";

        assertThat(DocumentFilename.sanitize(raw)).hasSize(255);
    }

    @Test
    void fallsBackWhenNothingUsableIsLeft() {
        assertThat(DocumentFilename.sanitize(null)).isEqualTo("document");
        assertThat(DocumentFilename.sanitize("   ")).isEqualTo("document");
        assertThat(DocumentFilename.sanitize("///")).isEqualTo("document");
        assertThat(DocumentFilename.sanitize("....")).isEqualTo("document");
    }
}
