package de.samply.manager.services;

import de.samply.manager.model.CompanyPosition;
import org.docx4j.model.fields.FieldUpdater;
import org.docx4j.model.fields.merge.DataFieldName;
import org.docx4j.model.fields.merge.MailMerger;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

@Service
public class CoverLetterService {

    public byte[] fillTemplate(InputStream templateStream,
                               Map<String, String> replacements) throws Exception {

        WordprocessingMLPackage wordPackage =
                WordprocessingMLPackage.load(templateStream);

        Map<DataFieldName, String> mergeData = new HashMap<>();
        replacements.forEach((k, v) ->
                mergeData.put(new DataFieldName(k), v));

        MailMerger.performMerge(wordPackage, mergeData, true);
        FieldUpdater updater = new FieldUpdater(wordPackage);
        updater.update(true);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wordPackage.save(out);
        return out.toByteArray();
    }

    public String buildSalutation(CompanyPosition position) {
        return switch (position.getContactGender()) {
            case MALE   -> "Sehr geehrter Herr " + formatName(position);
            case FEMALE -> "Sehr geehrte Frau " + formatName(position);
            case TEAM   -> "Sehr geehrte Damen und Herren";
        };
    }

    private String formatName(CompanyPosition position) {
        String title = position.getContactTitle() != null ? position.getContactTitle() + " " : "";
        return title + position.getContactLastName();
    }

}
