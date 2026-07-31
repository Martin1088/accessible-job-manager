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

    public boolean accepts(String contentType) {
        return allowedMime.equals(contentType);
    }
}
