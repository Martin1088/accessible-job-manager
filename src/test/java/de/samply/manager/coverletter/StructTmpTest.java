package de.samply.manager.coverletter;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

class StructTmpTest {
    @Test
    void tree() throws Exception {
        Path dir = Path.of("/tmp/claude-0/-workspaces-ajm/4318d1ec-3d31-4a06-9ad2-fe6e6946a342/scratchpad");
        String name = System.getenv().getOrDefault("PDF_FILE", "ua2.pdf");
        try (PDDocument doc = Loader.loadPDF(dir.resolve(name).toFile())) {
            COSDictionary root = (COSDictionary) doc.getDocumentCatalog().getCOSObject().getDictionaryObject("StructTreeRoot");
            print(root.getDictionaryObject(COSName.K), 0);
        }
    }

    private void print(COSBase base, int depth) {
        base = base instanceof COSObject o ? o.getObject() : base;
        if (base instanceof COSArray arr) {
            for (COSBase b : arr) print(b, depth);
            return;
        }
        if (!(base instanceof COSDictionary d)) {
            System.out.println("### " + "  ".repeat(depth) + base);
            return;
        }
        String s = String.valueOf(d.getDictionaryObject(COSName.S));
        System.out.println("### " + "  ".repeat(depth) + s
                + (d.containsKey(COSName.ALT) ? " Alt=" + d.getString(COSName.ALT) : "")
                + (d.containsKey(COSName.getPDFName("ActualText")) ? " ActualText" : ""));
        COSBase kids = d.getDictionaryObject(COSName.K);
        if (kids != null && depth < 6) print(kids, depth + 1);
    }
}
