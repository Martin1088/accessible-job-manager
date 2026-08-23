package de.samply.manager.services;

import de.samply.manager.dto.CompanyOverviewExport;
import de.samply.manager.exception.ApiException;
import de.samply.manager.model.Application;
import de.samply.manager.model.Company;
import de.samply.manager.model.CompanyLocation;
import de.samply.manager.model.CompanyPosition;
import de.samply.manager.repository.ApplicationRepository;
import de.samply.manager.repository.CompanyRepository;
import de.samply.manager.services.exporter.CsvExportWriter;
import de.samply.manager.services.exporter.XlsxExportWriter;
import de.samply.manager.types.Language;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);

    private static final List<String> COLUMN_KEYS = List.of(
            "export.column.company",
            "export.column.locations",
            "export.column.position",
            "export.column.contact",
            "export.column.contactEmail",
            "export.column.website",
            "export.column.positionNotes",
            "export.column.applicationStatus",
            "export.column.appliedDate",
            "export.column.applicationNotes");

    private final CompanyRepository companyRepository;
    private final ApplicationRepository applicationRepository;
    private final MessageSource messageSource;
    private final CsvExportWriter csvExportWriter;
    private final XlsxExportWriter xlsxExportWriter;

    @Transactional(readOnly = true)
    public List<CompanyOverviewExport> buildOverview(String userId) {
        List<Company> companies = companyRepository.findByUserId(userId);
        Map<Long, List<Application>> applicationsByPosition = applicationRepository.findByUserId(userId).stream()
                .collect(Collectors.groupingBy(a -> a.getCompanyPosition().getId()));

        List<CompanyOverviewExport> rows = new ArrayList<>();
        for (Company company : companies) {
            String locations = formatLocations(company.getLocations());
            if (company.getPositions().isEmpty()) {
                rows.add(rowFor(company, locations, null, null));
                continue;
            }
            for (CompanyPosition position : company.getPositions()) {
                List<Application> applications = applicationsByPosition.getOrDefault(position.getId(), List.of());
                if (applications.isEmpty()) {
                    rows.add(rowFor(company, locations, position, null));
                } else {
                    for (Application application : applications) {
                        rows.add(rowFor(company, locations, position, application));
                    }
                }
            }
        }
        return rows;
    }

    public byte[] toCsv(List<CompanyOverviewExport> rows, Language language) {
        return write(() -> csvExportWriter.write(columnHeaders(language), rows));
    }

    public byte[] toXlsx(List<CompanyOverviewExport> rows, Language language) {
        return write(() -> xlsxExportWriter.write(columnHeaders(language), rows));
    }

    private byte[] write(Supplier<byte[]> writer) {
        try {
            return writer.get();
        } catch (RuntimeException e) {
            log.error("Export could not be written", e);
            throw new ApiException.InternalServerError(message("error.export.writeFailed"));
        }
    }

    private String message(String key, Object... args) {
        return messageSource.getMessage(key, args, Locale.ROOT);
    }

    private List<String> columnHeaders(Language language) {
        return COLUMN_KEYS.stream()
                .map(key -> messageSource.getMessage(key, null, language.locale()))
                .toList();
    }

    private CompanyOverviewExport rowFor(Company company, String locations, CompanyPosition position, Application application) {
        return new CompanyOverviewExport(
                company.getName(),
                locations,
                position != null ? position.getTitle() : null,
                position != null ? contact(position) : null,
                position != null ? position.getEmail() : null,
                position != null ? position.getWebsite() : null,
                position != null ? position.getNotes() : null,
                application != null ? application.getStatus() : null,
                application != null ? application.getAppliedDate() : null,
                application != null ? application.getNotes() : null);
    }

    private String contact(CompanyPosition position) {
        String title = position.getContactTitle() != null ? position.getContactTitle() : "";
        String lastName = position.getContactLastName() != null ? position.getContactLastName() : "";
        String contact = (title + " " + lastName).trim();
        return contact.isEmpty() ? null : contact;
    }

    private String formatLocations(List<CompanyLocation> locations) {
        return locations.stream()
                .map(this::formatLocation)
                .collect(Collectors.joining("; "));
    }

    private String formatLocation(CompanyLocation location) {
        List<String> parts = new ArrayList<>();
        if (isNotBlank(location.getStreet())) {
            parts.add(location.getStreet());
        }
        String postcodeCity = ((isNotBlank(location.getPostcode()) ? location.getPostcode() + " " : "")
                + (location.getCity() != null ? location.getCity() : "")).trim();
        if (isNotBlank(postcodeCity)) {
            parts.add(postcodeCity);
        }
        if (isNotBlank(location.getCountry())) {
            parts.add(location.getCountry());
        }
        return String.join(", ", parts);
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
