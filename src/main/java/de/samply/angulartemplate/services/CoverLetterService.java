package de.samply.angulartemplate.services;

import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.List;
import java.util.Map;

@Service
public class CoverLetterService {

    /**
     * Replaces «placeholder» tokens in a .docx template.
     *
     * @param templateStream  InputStream of the .docx template
     * @param replacements    Map of placeholder key → value, e.g.
     *                        {"Unternehmen" -> "SAP SE", "Straße" -> "Dietmar-Hopp-Allee 16", ...}
     * @return byte[] of the filled document
     */
    public byte[] fillTemplate(InputStream templateStream, Map<String, String> replacements) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(templateStream)) {

            for (XWPFParagraph paragraph : doc.getParagraphs()) {
                replacePlaceholders(paragraph, replacements);
            }

            // Also handle placeholders inside tables, if any
            for (XWPFTable table : doc.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        for (XWPFParagraph paragraph : cell.getParagraphs()) {
                            replacePlaceholders(paragraph, replacements);
                        }
                    }
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        }
    }

    private void replacePlaceholders(XWPFParagraph paragraph, Map<String, String> replacements) {
        List<XWPFRun> runs = paragraph.getRuns();
        if (runs.isEmpty()) return;

        // Merge all runs into the first one first
        StringBuilder full = new StringBuilder();
        for (XWPFRun run : runs) {
            String text = run.getText(0);
            if (text != null) full.append(text);
        }

        String result = full.toString();

        // Only process if paragraph contains a placeholder
        if (!result.contains("«")) return;

        boolean changed = false;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            String token = "«" + entry.getKey() + "»";
            if (result.contains(token)) {
                result = result.replace(token, entry.getValue());
                changed = true;
            }
        }

        if (!changed) return;

        // Write back to first run, clear the rest
        runs.get(0).setText(result, 0);
        for (int i = 1; i < runs.size(); i++) {
            runs.get(i).setText("", 0);
        }
    }
}
