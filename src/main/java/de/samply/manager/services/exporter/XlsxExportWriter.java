package de.samply.manager.services.exporter;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import de.samply.manager.dto.CompanyOverviewExport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.List;

@Component
public class XlsxExportWriter {

    public byte[] write(List<String> headers, List<CompanyOverviewExport> rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Export");

            CellStyle headerStyle = workbook.createCellStyle();
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            headerStyle.setFont(boldFont);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
            }

            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));

            int rowIndex = 1;
            for (CompanyOverviewExport row : rows) {
                Row xlsxRow = sheet.createRow(rowIndex++);
                setCell(xlsxRow, 0, row.companyName());
                setCell(xlsxRow, 1, row.locations());
                setCell(xlsxRow, 2, row.positionTitle());
                setCell(xlsxRow, 3, row.contact());
                setCell(xlsxRow, 4, row.contactEmail());
                setCell(xlsxRow, 5, row.contactWebsite());
                setCell(xlsxRow, 6, row.positionNotes());
                setCell(xlsxRow, 7, row.applicationStatus() != null ? row.applicationStatus().name() : null);
                setDateCell(xlsxRow, 8, row.appliedDate(), dateStyle);
                setCell(xlsxRow, 9, row.applicationNotes());
            }

            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void setCell(Row row, int column, String value) {
        if (value != null) {
            row.createCell(column).setCellValue(value);
        }
    }

    private void setDateCell(Row row, int column, LocalDate value, CellStyle dateStyle) {
        if (value != null) {
            Cell cell = row.createCell(column);
            cell.setCellValue(value);
            cell.setCellStyle(dateStyle);
        }
    }
}
