package de.samply.manager.services.exporter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;

import de.samply.manager.dto.CompanyOverviewExport;

@Component
public class CsvExportWriter {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    public byte[] write(List<String> headers, List<CompanyOverviewExport> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(UTF8_BOM);
            CSVFormat format = CSVFormat.DEFAULT.builder()
                    .setHeader(headers.toArray(new String[0]))
                    .build();
            try (OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
                 CSVPrinter printer = new CSVPrinter(writer, format)) {
                for (CompanyOverviewExport row : rows) {
                    printer.printRecord(
                            row.companyName(),
                            row.locations(),
                            row.positionTitle(),
                            row.contact(),
                            row.contactEmail(),
                            row.contactWebsite(),
                            row.positionNotes(),
                            row.applicationStatus() != null ? row.applicationStatus().name() : null,
                            row.appliedDate(),
                            row.applicationNotes());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

}
