package de.samply.manager.services;

import de.samply.manager.model.CompanyPosition;
import de.samply.manager.model.Gender;
import de.samply.manager.model.Language;
import jakarta.xml.bind.JAXBElement;
import org.docx4j.jaxb.Context;
import org.docx4j.model.fields.FieldUpdater;
import org.docx4j.model.fields.merge.DataFieldName;
import org.docx4j.model.fields.merge.MailMerger;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.HeaderPart;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.relationships.Relationship;
import org.docx4j.wml.Body;
import org.docx4j.wml.FldChar;
import org.docx4j.wml.HdrFtrRef;
import org.docx4j.wml.HeaderReference;
import org.docx4j.wml.Jc;
import org.docx4j.wml.JcEnumeration;
import org.docx4j.wml.ObjectFactory;
import org.docx4j.wml.P;
import org.docx4j.wml.PPr;
import org.docx4j.wml.PPrBase;
import org.docx4j.wml.R;
import org.docx4j.wml.RFonts;
import org.docx4j.wml.RPr;
import org.docx4j.wml.STFldCharType;
import org.docx4j.wml.SectPr;
import org.docx4j.wml.Text;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class CoverLetterService {

    private final RestClient restClient;
    private final String gotenbergUrl;
    private final MessageSource messageSource;

    public CoverLetterService(@Value("${gotenberg.url}") String gotenbergUrl,
                              MessageSource messageSource) {
        this.gotenbergUrl = gotenbergUrl;
        this.messageSource = messageSource;
        this.restClient = RestClient.create();
    }

    public byte[] fillTemplate(InputStream templateStream,
                               Map<String, String> replacements) throws Exception {
        WordprocessingMLPackage wordPackage = WordprocessingMLPackage.load(templateStream);

        Map<DataFieldName, String> mergeData = new HashMap<>();
        replacements.forEach((k, v) -> mergeData.put(new DataFieldName(k), v));

        MailMerger.performMerge(wordPackage, mergeData, true);
        FieldUpdater updater = new FieldUpdater(wordPackage);
        updater.update(true);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wordPackage.save(out);
        return out.toByteArray();
    }

    /**
     * Plain-text rendering of a filled cover letter's body paragraphs (recipient
     * address, date, subject, greeting, letter text), for use as an email body.
     * The sender block lives in the header/footer part and is deliberately
     * excluded, since the sender is already implied by the email's From address.
     */
    public String extractPlainText(byte[] docxBytes) throws Exception {
        WordprocessingMLPackage wordPackage = WordprocessingMLPackage.load(new ByteArrayInputStream(docxBytes));
        MainDocumentPart mainDocumentPart = wordPackage.getMainDocumentPart();
        Body body = mainDocumentPart.getJaxbElement().getBody();

        return body.getContent().stream()
                .filter(P.class::isInstance)
                .map(P.class::cast)
                .map(p -> paragraphText(p, mainDocumentPart))
                .filter(text -> !text.isEmpty())
                .collect(Collectors.joining("\n\n"));
    }

    /**
     * Concatenates a paragraph's visible run text, skipping field-code runs
     * (<w:instrText>, e.g. " DATE \@ \"dd.MM.yyyy\" ") that sit alongside the
     * literal cached result Word actually displays for live fields like DATE
     * (MERGEFIELDs don't have this problem: MailMerger replaces them with
     * plain literal runs, leaving no field code behind).
     * <p>
     * Runs inside a <w:hyperlink> aren't direct children of the paragraph -
     * they're nested one level deeper in a {@link P.Hyperlink} wrapper - so
     * they're handled separately and the resolved target URL is appended,
     * since plain text has no other way to carry a link.
     */
    private String paragraphText(P paragraph, MainDocumentPart mainDocumentPart) {
        StringBuilder sb = new StringBuilder();
        for (Object item : paragraph.getContent()) {
            Object value = item instanceof JAXBElement<?> el ? el.getValue() : item;
            if (value instanceof R run) {
                appendRunText(run, sb);
            } else if (value instanceof P.Hyperlink hyperlink) {
                appendHyperlinkText(hyperlink, mainDocumentPart, sb);
            }
        }
        return sb.toString().trim();
    }

    private void appendRunText(R run, StringBuilder sb) {
        for (Object item : run.getContent()) {
            if (item instanceof JAXBElement<?> el && "instrText".equals(el.getName().getLocalPart())) continue;
            Object value = item instanceof JAXBElement<?> el ? el.getValue() : item;
            if (value instanceof Text text) sb.append(text.getValue());
        }
    }

    private void appendHyperlinkText(P.Hyperlink hyperlink, MainDocumentPart mainDocumentPart, StringBuilder sb) {
        int start = sb.length();
        for (Object runObj : hyperlink.getContent()) {
            if (runObj instanceof R run) appendRunText(run, sb);
        }
        String linkText = sb.substring(start);

        String target = hyperlink.getId() == null ? null
                : Optional.ofNullable(mainDocumentPart.getRelationshipsPart())
                        .map(rp -> rp.getRelationshipByID(hyperlink.getId()))
                        .map(Relationship::getTarget)
                        .orElse(null);
        if (target != null && !target.equals(linkText)) {
            sb.append(" (").append(target).append(")");
        }
    }

    /**
     * Builds a brand-new cover letter template (header + mail-merge skeleton),
     * mirroring the DIN 5008 style layout: centered sender block in the header,
     * recipient address / date / subject / greeting as MERGEFIELDs in the body,
     * and an empty placeholder where the letter text goes.
     */
    public byte[] createTemplateWithHeader(Map<String, String> personalData) throws Exception {
        WordprocessingMLPackage wordPackage = WordprocessingMLPackage.createPackage();
        ObjectFactory factory = Context.getWmlObjectFactory();

        String senderName = personalData.getOrDefault("senderName", "");
        String senderStreet = personalData.getOrDefault("senderStreet", "");
        String senderPostalCode = personalData.getOrDefault("senderPostalCode", "");
        String senderCity = personalData.getOrDefault("senderCity", "");
        String senderEmail = personalData.getOrDefault("senderEmail", "");

        HeaderPart headerPart = new HeaderPart();
        headerPart.getContent().add(centeredParagraph(factory, senderName));
        headerPart.getContent().add(centeredParagraph(factory,
                joinNonBlank(", ", senderStreet, joinNonBlank(" ", senderPostalCode, senderCity))));
        headerPart.getContent().add(centeredParagraph(factory, senderEmail));

        MainDocumentPart mainDocumentPart = wordPackage.getMainDocumentPart();
        Body body = mainDocumentPart.getJaxbElement().getBody();
        List<Object> content = body.getContent();

        content.add(factory.createP());
        content.add(fieldParagraphNoSpacing(factory, "company", "«company»"));
        content.add(fieldParagraphNoSpacing(factory, "street", "«street»"));
        content.add(fieldParagraphNoSpacing(factory, "city", "«city»"));
        content.add(factory.createP());
        content.add(dateFieldParagraph(factory));

        List<R> subjectRuns = new ArrayList<>(List.of(literalRun(factory, "Bewerbung als ")));
        subjectRuns.addAll(mergeFieldRuns(factory, "position", "«position»"));
        content.add(paragraphOf(factory, subjectRuns));

        content.add(factory.createP());

        List<R> greetingRuns = new ArrayList<>(List.of(literalRun(factory, "Sehr ")));
        greetingRuns.addAll(mergeFieldRuns(factory, "contact", "«contact»"));
        greetingRuns.add(literalRun(factory, ","));
        content.add(paragraphOf(factory, greetingRuns));

        content.add(factory.createP());
        content.add(factory.createP());
        content.add(factory.createP());

        SectPr sectPr = body.getSectPr();
        Relationship headerRelationship = mainDocumentPart.addTargetPart(headerPart);
        HeaderReference headerReference = factory.createHeaderReference();
        headerReference.setType(HdrFtrRef.DEFAULT);
        headerReference.setId(headerRelationship.getId());
        sectPr.getEGHdrFtrReferences().add(headerReference);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wordPackage.save(out);
        return out.toByteArray();
    }

    private String joinNonBlank(String separator, String... parts) {
        return Stream.of(parts)
                .filter(p -> p != null && !p.isBlank())
                .collect(Collectors.joining(separator));
    }

    private P paragraph(ObjectFactory factory, String text) {
        return paragraphOf(factory, List.of(literalRun(factory, text)));
    }

    private P centeredParagraph(ObjectFactory factory, String text) {
        P p = paragraph(factory, text);
        PPr ppr = factory.createPPr();
        Jc jc = factory.createJc();
        jc.setVal(JcEnumeration.CENTER);
        ppr.setJc(jc);
        ppr.setSpacing(noSpacing(factory));
        p.setPPr(ppr);
        return p;
    }

    private PPrBase.Spacing noSpacing(ObjectFactory factory) {
        PPrBase.Spacing spacing = factory.createPPrBaseSpacing();
        spacing.setBefore(BigInteger.ZERO);
        spacing.setAfter(BigInteger.ZERO);
        return spacing;
    }

    private P fieldParagraphNoSpacing(ObjectFactory factory, String fieldName, String cachedText) {
        P p = paragraphOf(factory, mergeFieldRuns(factory, fieldName, cachedText));
        PPr ppr = factory.createPPr();
        ppr.setSpacing(noSpacing(factory));
        p.setPPr(ppr);
        return p;
    }

    private P dateFieldParagraph(ObjectFactory factory) {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        List<R> runs = List.of(
                fldCharRun(factory, STFldCharType.BEGIN),
                instrTextRun(factory, " DATE \\@ \"dd.MM.yyyy\" "),
                fldCharRun(factory, STFldCharType.SEPARATE),
                literalRun(factory, today),
                fldCharRun(factory, STFldCharType.END));
        P p = paragraphOf(factory, runs);
        PPr ppr = factory.createPPr();
        Jc jc = factory.createJc();
        jc.setVal(JcEnumeration.RIGHT);
        ppr.setJc(jc);
        p.setPPr(ppr);
        return p;
    }

    private P paragraphOf(ObjectFactory factory, List<R> runs) {
        P p = factory.createP();
        p.getContent().addAll(runs);
        return p;
    }

    private List<R> mergeFieldRuns(ObjectFactory factory, String fieldName, String cachedText) {
        return List.of(
                fldCharRun(factory, STFldCharType.BEGIN),
                instrTextRun(factory, " MERGEFIELD " + fieldName + " "),
                fldCharRun(factory, STFldCharType.SEPARATE),
                literalRun(factory, cachedText),
                fldCharRun(factory, STFldCharType.END));
    }

    private R fldCharRun(ObjectFactory factory, STFldCharType type) {
        FldChar fldChar = factory.createFldChar();
        fldChar.setFldCharType(type);
        R r = factory.createR();
        r.setRPr(textFontRPr(factory));
        r.getContent().add(factory.createRFldChar(fldChar));
        return r;
    }

    private R instrTextRun(ObjectFactory factory, String instr) {
        Text t = factory.createText();
        t.setValue(instr);
        t.setSpace("preserve");
        R r = factory.createR();
        r.setRPr(textFontRPr(factory));
        r.getContent().add(factory.createRInstrText(t));
        return r;
    }

    private R literalRun(ObjectFactory factory, String text) {
        Text t = factory.createText();
        t.setValue(text);
        t.setSpace("preserve");
        R r = factory.createR();
        r.setRPr(textFontRPr(factory));
        r.getContent().add(t);
        return r;
    }

    private static final String TEXT_FONT = "Roboto";

    private RPr textFontRPr(ObjectFactory factory) {
        RPr rPr = factory.createRPr();
        RFonts rFonts = factory.createRFonts();
        rFonts.setAscii(TEXT_FONT);
        rFonts.setHAnsi(TEXT_FONT);
        rFonts.setCs(TEXT_FONT);
        rPr.setRFonts(rFonts);
        return rPr;
    }

    public byte[] toPdf(byte[] docxBytes) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("files", new ByteArrayResource(docxBytes) {
            @Override
            public String getFilename() { return "document.docx"; }
        });

        return restClient.post()
                .uri(gotenbergUrl + "/forms/libreoffice/convert")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(byte[].class);
    }

    public String buildSalutation(CompanyPosition position, Language language) {
        Gender gender = position.getContactGender() != null ? position.getContactGender() : Gender.TEAM;
        Locale locale = toLocale(language);
        String phrase = messageSource.getMessage("salutation." + gender.name(), null, locale);
        return gender == Gender.TEAM ? phrase : phrase + " " + formatName(position);
    }

    private static Locale toLocale(Language language) {
        if (language == null) return Locale.GERMAN;
        return switch (language) {
            case GERMAN -> Locale.GERMAN;
            case ENGLISH -> Locale.ENGLISH;
            case DUTCH -> Locale.forLanguageTag("nl");
        };
    }

    private String formatName(CompanyPosition position) {
        String title = position.getContactTitle() != null ? position.getContactTitle() + " " : "";
        return title + position.getContactLastName();
    }
}
