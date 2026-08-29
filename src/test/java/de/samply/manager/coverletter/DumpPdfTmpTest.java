package de.samply.manager.coverletter;

import org.junit.jupiter.api.Test;
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider;
import org.verapdf.pdfa.Foundries;
import org.verapdf.pdfa.PDFAParser;
import org.verapdf.pdfa.PDFAValidator;
import org.verapdf.pdfa.flavours.PDFAFlavour;
import org.verapdf.pdfa.results.TestAssertion;
import org.verapdf.pdfa.results.ValidationResult;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

class DumpPdfTmpTest {
    @Test
    void validateFile() throws Exception {
        VeraGreenfieldFoundryProvider.initialise();
        Path dir = Path.of("/tmp/claude-0/-workspaces-ajm/4318d1ec-3d31-4a06-9ad2-fe6e6946a342/scratchpad");
        String name = System.getenv().getOrDefault("PDF_FILE", "ua.pdf");
        byte[] pdf = Files.readAllBytes(dir.resolve(name));
        ValidationResult result;
        try (PDFAParser parser = Foundries.defaultInstance().createParser(new ByteArrayInputStream(pdf), PDFAFlavour.PDFUA_1);
             PDFAValidator validator = Foundries.defaultInstance().createValidator(PDFAFlavour.PDFUA_1, false)) {
            result = validator.validate(parser);
        }
        System.out.println("### " + name + " compliant=" + result.isCompliant());
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, String> messages = new LinkedHashMap<>();
        for (TestAssertion a : result.getTestAssertions()) {
            if (a.getStatus() == TestAssertion.Status.PASSED) continue;
            String rule = a.getRuleId().getClause() + "-" + a.getRuleId().getTestNumber();
            counts.merge(rule, 1, Integer::sum);
            messages.putIfAbsent(rule, a.getMessage());
        }
        counts.forEach((k, v) -> System.out.println("### " + k + " (x" + v + "): " + messages.get(k)));
    }
}
