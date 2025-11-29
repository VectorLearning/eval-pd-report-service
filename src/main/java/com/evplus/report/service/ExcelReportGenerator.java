package com.evplus.report.service;

import com.evplus.report.exception.ReportGenerationException;
import com.evplus.report.model.dto.DummyRecord;
import com.evplus.report.model.dto.DummyReportData;
import com.evplus.report.model.dto.ReportData;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;

/**
 * Excel Report Generator Service.
 *
 * Converts ReportData objects to Excel files using Apache POI.
 * Uses SXSSFWorkbook (Streaming Workbook) for memory-efficient processing
 * of large reports (keeps only 100 rows in memory at a time).
 */
@Service
public class ExcelReportGenerator {

    private static final Logger logger = LoggerFactory.getLogger(ExcelReportGenerator.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Generate Excel file from ReportData.
     *
     * Routes to specific generator method based on ReportData type.
     * Extensible for additional report types.
     *
     * @param reportData The report data to convert to Excel
     * @return Excel file as byte array
     * @throws ReportGenerationException if Excel generation fails
     */
    public byte[] generateExcel(ReportData reportData) {
        logger.info("Generating Excel for report type: {}", reportData.getClass().getSimpleName());

        if (reportData instanceof DummyReportData) {
            return generateDummyExcel((DummyReportData) reportData);
        }

        throw new UnsupportedOperationException(
            "Excel generation not yet implemented for report type: " + reportData.getClass().getSimpleName()
        );
    }

    /**
     * Generate Excel for DummyReportData (test report type).
     *
     * Creates a streaming Excel workbook with:
     * - Header row with bold styling
     * - Data rows with field values
     * - Auto-sized columns
     *
     * @param reportData Dummy report data with test records
     * @return Excel file as byte array
     * @throws ReportGenerationException if Excel generation fails
     */
    private byte[] generateDummyExcel(DummyReportData reportData) {
        logger.info("Generating Dummy Test Report Excel with {} records", reportData.getTotalRecords());

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) { // Keep 100 rows in memory
            SXSSFSheet sheet = workbook.createSheet("Dummy Test Report");

            // Track all columns for auto-sizing (required for streaming workbook)
            sheet.trackAllColumnsForAutoSizing();

            // Create header row with styling
            CellStyle headerStyle = createHeaderStyle(workbook);
            Row headerRow = sheet.createRow(0);
            String[] headers = {"Field 1", "Field 2", "Field 3", "Timestamp"};

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Populate data rows
            int rowNum = 1;
            for (DummyRecord record : reportData.getRecords()) {
                Row row = sheet.createRow(rowNum++);

                row.createCell(0).setCellValue(record.getField1());
                row.createCell(1).setCellValue(record.getField2());
                row.createCell(2).setCellValue(record.getField3());
                row.createCell(3).setCellValue(
                    record.getTimestamp() != null
                        ? record.getTimestamp().format(DATE_TIME_FORMATTER)
                        : ""
                );
            }

            // Auto-size columns for readability
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Write workbook to byte array
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);

            logger.info("Successfully generated Excel with {} data rows", reportData.getTotalRecords());
            return outputStream.toByteArray();

        } catch (IOException e) {
            logger.error("Failed to generate Excel for Dummy Test Report", e);
            throw new ReportGenerationException("Failed to generate Excel report", e);
        }
    }

    /**
     * Create header cell style with bold font.
     *
     * @param workbook The workbook to create the style in
     * @return CellStyle with bold font
     */
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }
}
