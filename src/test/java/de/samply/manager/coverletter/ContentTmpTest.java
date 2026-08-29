package de.samply.manager.coverletter;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

class ContentTmpTest {
    @Test
    void dump() throws Exception {
        Path dir = Path.of("/tmp/claude-0/-workspaces-ajm/4318d1ec-3d31-4a06-9ad2-fe6e6946a342/scratchpad");
        String name = System.getenv().getOrDefault("PDF_FILE", "ua2.pdf");
        try (PDDocument doc = Loader.loadPDF(dir.resolve(name).toFile())) {
            int i = 0;
            for (PDPage page : doc.getPages()) {
                Files.writeString(dir.resolve(name + ".page" + i++ + ".txt"),
                        new String(page.getContents().readAllBytes(), StandardCharsets.ISO_8859_1));
            }
        }
    }
}
