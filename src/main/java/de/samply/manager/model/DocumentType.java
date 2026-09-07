package de.samply.manager.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DocumentType {
    CV                   ("application/pdf",                                                                    "pdf"),
    COVER_LETTER_TEMPLATE("application/vnd.openxmlformats-officedocument.wordprocessingml.document",            "docx"),
    CERTIFICATE          ("application/pdf",                                                                    "pdf"),
    JOB_POSTING_SNAPSHOT ("application/pdf",                                                                    "pdf"),
    OTHER                ("application/pdf",                                                                    "pdf");

    private final String allowedMime;
    private final String extension;

    // The first bytes of the formats we accept. A PDF opens with "%PDF-"; a DOCX
    // is a ZIP container and opens with the local-file-header signature "PK\x03\x04".
    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};

    public boolean accepts(String contentType) {
        return allowedMime.equals(contentType);
    }

    /**
     * Whether the actual bytes are the format this type stores - the check the
     * client {@code Content-Type} header cannot be trusted for. Keyed on the
     * extension so a type added without a rule fails closed rather than being
     * waved through as a PDF.
     */
    public boolean matchesContent(byte[] content) {
        return switch (extension) {
            case "pdf"  -> startsWith(content, PDF_MAGIC);
            case "docx" -> startsWith(content, ZIP_MAGIC);
            default     -> false;
        };
    }

    private static boolean startsWith(byte[] content, byte[] magic) {
        if (content == null || content.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (content[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }
}
