package de.samply.manager.services;

import de.samply.manager.model.CompanyPosition;
import org.docx4j.model.fields.FieldUpdater;
import org.docx4j.model.fields.merge.DataFieldName;
import org.docx4j.model.fields.merge.MailMerger;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@Service
public class CoverLetterService {

    private final RestClient restClient;
    private final String gotenbergUrl;

    public CoverLetterService(@Value("${gotenberg.url}") String gotenbergUrl) {
        this.gotenbergUrl = gotenbergUrl;
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

    public byte[] fillPersonalFields(InputStream templateStream,
                                     Map<String, String> personalData) throws Exception {
        WordprocessingMLPackage wordPackage = WordprocessingMLPackage.load(templateStream);

        Map<DataFieldName, String> mergeData = new HashMap<>();
        personalData.forEach((k, v) -> mergeData.put(new DataFieldName(k), v));
        MailMerger.performMerge(wordPackage, mergeData, true);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wordPackage.save(out);
        return out.toByteArray();
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

    public String buildSalutation(CompanyPosition position) {
        if (position.getContactGender() == null) return "HR Team";
        return switch (position.getContactGender()) {
            case MALE   -> "Mr" + formatName(position);
            case FEMALE -> "Mrs" + formatName(position);
            case TEAM   -> "HR Team";
        };
    }

    private String formatName(CompanyPosition position) {
        String title = position.getContactTitle() != null ? position.getContactTitle() + " " : "";
        return title + position.getContactLastName();
    }
}
