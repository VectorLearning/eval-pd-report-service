package com.evplus.report.service;

import com.evplus.report.exception.ReportGenerationException;
import com.evplus.report.model.dto.DummyRecord;
import com.evplus.report.model.dto.DummyReportData;
import com.evplus.report.model.dto.ReportData;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ExcelReportGenerator.
 */
class ExcelReportGeneratorTest {

    private ExcelReportGenerator excelGenerator;

    @BeforeEach
    void setUp() {
        excelGenerator = new ExcelReportGenerator();
    }

    @Test
    void generateExcel_withDummyReportData_shouldGenerateValidExcel() throws IOException {
        // Given
        DummyReportData reportData = createDummyReportData(3);

        // When
        byte[] excelBytes = excelGenerator.generateExcel(reportData);

        // Then
        assertNotNull(excelBytes);
        assertTrue(excelBytes.length > 0);

        // Verify Excel structure
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals("Dummy Test Report", sheet.getSheetName());

            // Verify header row
            Row headerRow = sheet.getRow(0);
            assertNotNull(headerRow);
            assertEquals("Field 1", headerRow.getCell(0).getStringCellValue());
            assertEquals("Field 2", headerRow.getCell(1).getStringCellValue());
            assertEquals("Field 3", headerRow.getCell(2).getStringCellValue());
            assertEquals("Timestamp", headerRow.getCell(3).getStringCellValue());

            // Verify header styling (bold font)
            CellStyle headerStyle = headerRow.getCell(0).getCellStyle();
            Font font = workbook.getFontAt(headerStyle.getFontIndex());
            assertTrue(font.getBold());

            // Verify data rows
            assertEquals(4, sheet.getPhysicalNumberOfRows()); // 1 header + 3 data rows

            Row dataRow1 = sheet.getRow(1);
            assertEquals("Field1-0", dataRow1.getCell(0).getStringCellValue());
            assertEquals("Field2-0", dataRow1.getCell(1).getStringCellValue());
            assertEquals("Field3-0", dataRow1.getCell(2).getStringCellValue());
            assertNotNull(dataRow1.getCell(3).getStringCellValue());
        }
    }

    @Test
    void generateExcel_withEmptyRecordList_shouldGenerateExcelWithHeaderOnly() throws IOException {
        // Given
        DummyReportData reportData = createDummyReportData(0);

        // When
        byte[] excelBytes = excelGenerator.generateExcel(reportData);

        // Then
        assertNotNull(excelBytes);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals(1, sheet.getPhysicalNumberOfRows()); // Only header row
        }
    }

    @Test
    void generateExcel_withLargeDataSet_shouldHandleMemoryEfficiently() throws IOException {
        // Given - Large dataset to test streaming workbook
        DummyReportData reportData = createDummyReportData(1000);

        // When
        byte[] excelBytes = excelGenerator.generateExcel(reportData);

        // Then
        assertNotNull(excelBytes);
        assertTrue(excelBytes.length > 0);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals(1001, sheet.getPhysicalNumberOfRows()); // 1 header + 1000 data rows
        }
    }

    @Test
    void generateExcel_withNullTimestamp_shouldHandleGracefully() throws IOException {
        // Given
        DummyReportData reportData = new DummyReportData();
        List<DummyRecord> records = new ArrayList<>();
        records.add(new DummyRecord("Field1", "Field2", "Field3", null));
        reportData.setRecords(records);
        reportData.setTotalRecords(1);

        // When
        byte[] excelBytes = excelGenerator.generateExcel(reportData);

        // Then
        assertNotNull(excelBytes);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row dataRow = sheet.getRow(1);
            assertEquals("", dataRow.getCell(3).getStringCellValue()); // Empty string for null timestamp
        }
    }

    @Test
    void generateExcel_withUnsupportedReportType_shouldThrowException() {
        // Given
        ReportData unsupportedReport = new ReportData() {
            // Anonymous subclass for testing
        };

        // When/Then
        assertThrows(UnsupportedOperationException.class, () -> {
            excelGenerator.generateExcel(unsupportedReport);
        });
    }

    @Test
    void generateExcel_shouldFormatTimestampCorrectly() throws IOException {
        // Given
        LocalDateTime testTime = LocalDateTime.of(2025, 1, 15, 14, 30, 45);
        DummyReportData reportData = new DummyReportData();
        List<DummyRecord> records = new ArrayList<>();
        records.add(new DummyRecord("F1", "F2", "F3", testTime));
        reportData.setRecords(records);
        reportData.setTotalRecords(1);

        // When
        byte[] excelBytes = excelGenerator.generateExcel(reportData);

        // Then
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row dataRow = sheet.getRow(1);
            String timestamp = dataRow.getCell(3).getStringCellValue();
            assertEquals("2025-01-15 14:30:45", timestamp);
        }
    }

    // Helper methods

    private DummyReportData createDummyReportData(int recordCount) {
        DummyReportData reportData = new DummyReportData();
        List<DummyRecord> records = new ArrayList<>();

        for (int i = 0; i < recordCount; i++) {
            records.add(new DummyRecord(
                "Field1-" + i,
                "Field2-" + i,
                "Field3-" + i,
                LocalDateTime.now().minusHours(i)
            ));
        }

        reportData.setRecords(records);
        reportData.setTotalRecords(recordCount);
        return reportData;
    }
}
