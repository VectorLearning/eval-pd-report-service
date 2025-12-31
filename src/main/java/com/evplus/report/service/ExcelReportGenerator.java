package com.evplus.report.service;

import com.evplus.report.exception.ReportGenerationException;
import com.evplus.report.model.dto.ActivityByUserReportData;
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
import java.util.List;
import java.util.Map;

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
        } else if (reportData instanceof ActivityByUserReportData) {
            return generateActivityByUserExcel((ActivityByUserReportData) reportData);
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
     * Generate Excel for ActivityByUserReportData (PD tracking report).
     *
     * Creates a streaming Excel workbook with:
     * - Dynamic header row based on selected columns and credit types
     * - Data rows with activity records
     * - Credit value columns (floating-point)
     * - Summary row with totals by credit type
     * - Auto-sized columns
     *
     * @param reportData Activity by user report data with records and credit totals
     * @return Excel file as byte array
     * @throws ReportGenerationException if Excel generation fails
     */
    private byte[] generateActivityByUserExcel(ActivityByUserReportData reportData) {
        logger.info("Generating Activity By User Report Excel with {} records", reportData.getTotalRecords());

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) { // Keep 100 rows in memory
            SXSSFSheet sheet = workbook.createSheet("Activity By User Report");

            // Track all columns for auto-sizing (required for streaming workbook)
            sheet.trackAllColumnsForAutoSizing();

            // Create header row with styling
            CellStyle headerStyle = createHeaderStyle(workbook);
            Row headerRow = sheet.createRow(0);

            int colIndex = 0;

            // Add standard column headers (from columnHeaders list)
            List<String> columnHeaders = reportData.getColumnHeaders();
            if (columnHeaders != null) {
                for (String header : columnHeaders) {
                    Cell cell = headerRow.createCell(colIndex++);
                    cell.setCellValue(header);
                    cell.setCellStyle(headerStyle);
                }
            }

            // Add credit type headers
            List<String> creditHeaders = reportData.getCreditHeaders();
            if (creditHeaders != null) {
                for (String creditType : creditHeaders) {
                    Cell cell = headerRow.createCell(colIndex++);
                    cell.setCellValue(creditType);
                    cell.setCellStyle(headerStyle);
                }
            }

            // Add "Total Credits" column
            Cell totalCell = headerRow.createCell(colIndex++);
            totalCell.setCellValue("Total Credits");
            totalCell.setCellStyle(headerStyle);

            int totalColumns = colIndex;

            // Populate data rows
            int rowNum = 1;
            List<ActivityByUserReportData.ActivityRecord> records = reportData.getRecords();

            if (records != null) {
                for (ActivityByUserReportData.ActivityRecord record : records) {
                    Row row = sheet.createRow(rowNum++);
                    colIndex = 0;

                    // Add column values (user properties, event properties, standard fields)
                    if (record.getColumnValues() != null) {
                        for (String value : record.getColumnValues()) {
                            row.createCell(colIndex++).setCellValue(value != null ? value : "");
                        }
                    } else {
                        // Skip to credit columns if no column values
                        colIndex = columnHeaders != null ? columnHeaders.size() : 0;
                    }

                    // Add credit values
                    if (record.getCreditValues() != null) {
                        for (Float creditValue : record.getCreditValues()) {
                            Cell cell = row.createCell(colIndex++);
                            if (creditValue != null) {
                                cell.setCellValue(creditValue.doubleValue());
                            } else {
                                cell.setCellValue(0.0);
                            }
                        }
                    } else {
                        // Fill with zeros if no credit values
                        int creditCount = creditHeaders != null ? creditHeaders.size() : 0;
                        for (int i = 0; i < creditCount; i++) {
                            row.createCell(colIndex++).setCellValue(0.0);
                        }
                    }

                    // Add total credits
                    Cell totalCreditCell = row.createCell(colIndex++);
                    if (record.getTotalCredits() != null) {
                        totalCreditCell.setCellValue(record.getTotalCredits().doubleValue());
                    } else {
                        totalCreditCell.setCellValue(0.0);
                    }
                }
            }

            // Add blank row for separation
            rowNum++;

            // Add summary row with totals
            Row summaryRow = sheet.createRow(rowNum);
            CellStyle boldStyle = createHeaderStyle(workbook);

            // "TOTALS" label in first column
            Cell totalsLabelCell = summaryRow.createCell(0);
            totalsLabelCell.setCellValue("TOTALS");
            totalsLabelCell.setCellStyle(boldStyle);

            // Skip to credit columns
            colIndex = columnHeaders != null ? columnHeaders.size() : 0;

            // Add total values for each credit type
            Map<String, Float> totalCreditsByType = reportData.getTotalCreditsByType();
            if (creditHeaders != null && totalCreditsByType != null) {
                for (String creditType : creditHeaders) {
                    Cell cell = summaryRow.createCell(colIndex++);
                    Float totalValue = totalCreditsByType.get(creditType);
                    if (totalValue != null) {
                        cell.setCellValue(totalValue.doubleValue());
                    } else {
                        cell.setCellValue(0.0);
                    }
                    cell.setCellStyle(boldStyle);
                }
            }

            // Add grand total (sum of all credit types)
            Cell grandTotalCell = summaryRow.createCell(colIndex++);
            if (totalCreditsByType != null) {
                double grandTotal = totalCreditsByType.values().stream()
                        .mapToDouble(f -> f != null ? f.doubleValue() : 0.0)
                        .sum();
                grandTotalCell.setCellValue(grandTotal);
            } else {
                grandTotalCell.setCellValue(0.0);
            }
            grandTotalCell.setCellStyle(boldStyle);

            // Auto-size columns for readability
            for (int i = 0; i < totalColumns; i++) {
                sheet.autoSizeColumn(i);
            }

            // Write workbook to byte array
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);

            logger.info("Successfully generated Activity By User Excel with {} data rows",
                    reportData.getTotalRecords());
            return outputStream.toByteArray();

        } catch (IOException e) {
            logger.error("Failed to generate Excel for Activity By User Report", e);
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
