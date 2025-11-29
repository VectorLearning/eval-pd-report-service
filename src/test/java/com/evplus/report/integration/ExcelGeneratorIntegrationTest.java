package com.evplus.report.integration;

import com.evplus.report.model.dto.DummyRecord;
import com.evplus.report.model.dto.DummyReportData;
import com.evplus.report.service.ExcelReportGenerator;
import com.evplus.report.service.S3Service;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for ExcelReportGenerator with S3Service.
 *
 * Prerequisites:
 * - LocalStack must be running via docker-compose: docker-compose -f docker-compose-localstack.yml up -d
 * - S3 bucket 'ev-plus-reports-local' is created automatically by localstack-init.sh
 *
 * Tests complete workflow:
 * 1. Generate Excel from DummyReportData
 * 2. Upload Excel to S3
 * 3. Download Excel from S3
 * 4. Verify Excel file integrity and content
 * 5. Test with large datasets (10K+ records)
 */
@SpringBootTest
@ActiveProfiles("local")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ExcelGeneratorIntegrationTest {

    private static final String BUCKET_NAME = "ev-plus-reports-local";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private ExcelReportGenerator excelGenerator;

    @Autowired
    private S3Service s3Service;

    @Autowired
    private S3Client s3Client;

    @Test
    @Order(1)
    @DisplayName("Should generate Excel → upload to S3 → download → verify content")
    void testCompleteExcelWorkflow() throws Exception {
        // Given - Create test report data
        DummyReportData reportData = createDummyReportData(100);

        // When - Generate Excel
        byte[] excelBytes = excelGenerator.generateExcel(reportData);

        // Then - Verify Excel is valid
        assertNotNull(excelBytes);
        assertTrue(excelBytes.length > 0, "Excel file should not be empty");

        // Upload to S3
        String s3Key = s3Service.uploadReport(123, "excel-test-001", excelBytes, "TEST_REPORT.xlsx");
        assertNotNull(s3Key);

        // Download from S3
        byte[] downloadedBytes = s3Service.downloadReport(s3Key);
        assertNotNull(downloadedBytes);
        assertEquals(excelBytes.length, downloadedBytes.length, "Downloaded file size should match");

        // Verify downloaded Excel file is valid and readable
        Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(downloadedBytes));
        Sheet sheet = workbook.getSheetAt(0);
        assertNotNull(sheet, "Excel should have at least one sheet");
        assertEquals("Dummy Test Report", sheet.getSheetName());

        // Verify header row
        Row headerRow = sheet.getRow(0);
        assertNotNull(headerRow);
        assertEquals("Field 1", headerRow.getCell(0).getStringCellValue());
        assertEquals("Field 2", headerRow.getCell(1).getStringCellValue());
        assertEquals("Field 3", headerRow.getCell(2).getStringCellValue());
        assertEquals("Timestamp", headerRow.getCell(3).getStringCellValue());

        // Verify data rows
        assertEquals(101, sheet.getPhysicalNumberOfRows(), "Should have 1 header + 100 data rows");

        // Verify first data row
        Row firstDataRow = sheet.getRow(1);
        assertNotNull(firstDataRow);
        assertTrue(firstDataRow.getCell(0).getStringCellValue().startsWith("Field1-"));
        assertTrue(firstDataRow.getCell(1).getStringCellValue().startsWith("Field2-"));
        assertTrue(firstDataRow.getCell(2).getStringCellValue().startsWith("Field3-"));
        assertNotNull(firstDataRow.getCell(3).getStringCellValue()); // Timestamp

        workbook.close();
    }

    @Test
    @Order(2)
    @DisplayName("Should handle large datasets (10K records) efficiently")
    void testLargeDatasetGeneration() throws Exception {
        // Given - Create large report data (10,000 records)
        int recordCount = 10000;
        DummyReportData reportData = createDummyReportData(recordCount);

        // When - Generate Excel
        long startTime = System.currentTimeMillis();
        byte[] excelBytes = excelGenerator.generateExcel(reportData);
        long generationTime = System.currentTimeMillis() - startTime;

        // Then - Verify Excel is generated successfully
        assertNotNull(excelBytes);
        assertTrue(excelBytes.length > 0);
        System.out.println("✓ Generated Excel with " + recordCount + " records in " + generationTime + "ms");

        // Upload to S3
        String s3Key = s3Service.uploadReport(456, "excel-large-test", excelBytes, "LARGE_REPORT.xlsx");
        assertNotNull(s3Key);

        // Download and verify
        byte[] downloadedBytes = s3Service.downloadReport(s3Key);
        assertNotNull(downloadedBytes);

        // Verify Excel content
        Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(downloadedBytes));
        Sheet sheet = workbook.getSheetAt(0);

        assertEquals(recordCount + 1, sheet.getPhysicalNumberOfRows(),
                "Should have header + " + recordCount + " data rows");

        // Spot check some rows
        Row midRow = sheet.getRow(5000);
        assertNotNull(midRow, "Row 5000 should exist");
        assertTrue(midRow.getCell(0).getStringCellValue().contains("4999"));

        Row lastRow = sheet.getRow(recordCount);
        assertNotNull(lastRow, "Last row should exist");
        assertTrue(lastRow.getCell(0).getStringCellValue().contains(String.valueOf(recordCount - 1)));

        workbook.close();
    }

    @Test
    @Order(3)
    @DisplayName("Should preserve data types and formatting in Excel")
    void testExcelFormattingAndDataTypes() throws Exception {
        // Given
        DummyReportData reportData = createDummyReportData(50);

        // When
        byte[] excelBytes = excelGenerator.generateExcel(reportData);
        String s3Key = s3Service.uploadReport(789, "excel-format-test", excelBytes, "FORMAT_TEST.xlsx");
        byte[] downloadedBytes = s3Service.downloadReport(s3Key);

        // Then - Verify Excel formatting
        Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(downloadedBytes));
        Sheet sheet = workbook.getSheetAt(0);

        // Verify header row is bold
        Row headerRow = sheet.getRow(0);
        Cell headerCell = headerRow.getCell(0);
        CellStyle headerStyle = headerCell.getCellStyle();
        Font headerFont = workbook.getFontAt(headerStyle.getFontIndex());
        assertTrue(headerFont.getBold(), "Header should be bold");

        // Verify all cells have string data type
        Row dataRow = sheet.getRow(1);
        for (int i = 0; i < 4; i++) {
            Cell cell = dataRow.getCell(i);
            assertNotNull(cell, "Cell " + i + " should not be null");
            assertEquals(CellType.STRING, cell.getCellType(),
                    "Cell " + i + " should be STRING type");
        }

        // Verify timestamp format
        String timestamp = dataRow.getCell(3).getStringCellValue();
        assertNotNull(timestamp);
        assertDoesNotThrow(() -> LocalDateTime.parse(timestamp, DATE_TIME_FORMATTER),
                "Timestamp should be in correct format (yyyy-MM-dd HH:mm:ss)");

        workbook.close();
    }

    @Test
    @Order(4)
    @DisplayName("Should generate valid Excel that opens without errors")
    void testExcelFileIntegrity() throws Exception {
        // Given
        DummyReportData reportData = createDummyReportData(1000);

        // When - Complete workflow
        byte[] excelBytes = excelGenerator.generateExcel(reportData);
        String s3Key = s3Service.uploadReport(111, "excel-integrity-test", excelBytes, "INTEGRITY_TEST.xlsx");
        byte[] downloadedBytes = s3Service.downloadReport(s3Key);

        // Then - Verify file can be opened as valid Excel (no corruption)
        assertDoesNotThrow(() -> {
            Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(downloadedBytes));
            assertNotNull(workbook.getSheetAt(0));
            workbook.close();
        }, "Excel file should open without errors");
    }

    @Test
    @Order(5)
    @DisplayName("Should handle null timestamps gracefully")
    void testNullTimestampHandling() throws Exception {
        // Given - Create report data with null timestamp
        DummyReportData reportData = new DummyReportData();
        List<DummyRecord> records = new ArrayList<>();
        DummyRecord record = new DummyRecord();
        record.setField1("Test1");
        record.setField2("Test2");
        record.setField3("Test3");
        record.setTimestamp(null); // NULL timestamp
        records.add(record);
        reportData.setRecords(records);
        reportData.setTotalRecords(1);
        reportData.setGeneratedAt(LocalDateTime.now());

        // When
        byte[] excelBytes = excelGenerator.generateExcel(reportData);
        String s3Key = s3Service.uploadReport(222, "null-test", excelBytes, "NULL_TEST.xlsx");
        byte[] downloadedBytes = s3Service.downloadReport(s3Key);

        // Then - Verify Excel handles null gracefully
        Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(downloadedBytes));
        Sheet sheet = workbook.getSheetAt(0);
        Row dataRow = sheet.getRow(1);

        // Timestamp cell should be empty string (not null cell)
        Cell timestampCell = dataRow.getCell(3);
        assertNotNull(timestampCell);
        String value = timestampCell.getStringCellValue();
        assertTrue(value.isEmpty() || value.isBlank(), "Null timestamp should result in empty cell");

        workbook.close();
    }

    @Test
    @Order(6)
    @DisplayName("Should generate presigned URL for downloadable Excel")
    void testPresignedUrlForExcel() throws Exception {
        // Given
        DummyReportData reportData = createDummyReportData(100);
        byte[] excelBytes = excelGenerator.generateExcel(reportData);
        String s3Key = s3Service.uploadReport(333, "presigned-excel-test", excelBytes, "PRESIGNED_EXCEL.xlsx");

        // When - Generate presigned URL
        String presignedUrl = s3Service.generatePresignedUrl(s3Key, Duration.ofDays(7));

        // Then
        assertNotNull(presignedUrl);
        assertTrue(presignedUrl.contains(BUCKET_NAME));
        assertTrue(presignedUrl.contains("PRESIGNED_EXCEL.xlsx"));
    }

    @Test
    @Order(7)
    @DisplayName("Should verify column widths are auto-sized")
    void testColumnAutoSizing() throws Exception {
        // Given
        DummyReportData reportData = createDummyReportData(10);

        // When
        byte[] excelBytes = excelGenerator.generateExcel(reportData);

        // Then
        Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelBytes));
        Sheet sheet = workbook.getSheetAt(0);

        // Verify columns have been auto-sized (width > 0)
        for (int i = 0; i < 4; i++) {
            int columnWidth = sheet.getColumnWidth(i);
            assertTrue(columnWidth > 0, "Column " + i + " should be auto-sized (width > 0)");
        }

        workbook.close();
    }

    /**
     * Helper method to create DummyReportData with specified number of records
     */
    private DummyReportData createDummyReportData(int recordCount) {
        List<DummyRecord> records = IntStream.range(0, recordCount)
                .mapToObj(i -> {
                    DummyRecord record = new DummyRecord();
                    record.setField1("Field1-" + i);
                    record.setField2("Field2-" + i);
                    record.setField3("Field3-" + i);
                    record.setTimestamp(LocalDateTime.now().minusHours(i));
                    return record;
                })
                .toList();

        DummyReportData reportData = new DummyReportData();
        reportData.setRecords(records);
        reportData.setTotalRecords(records.size());
        reportData.setGeneratedAt(LocalDateTime.now());
        return reportData;
    }

    @AfterEach
    void cleanup() {
        // Clean up test files
        try {
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                    .bucket(BUCKET_NAME)
                    .prefix("reports/")
                    .build();

            ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);

            for (S3Object s3Object : listResponse.contents()) {
                DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                        .bucket(BUCKET_NAME)
                        .key(s3Object.key())
                        .build();
                s3Client.deleteObject(deleteRequest);
            }
        } catch (Exception e) {
            System.err.println("Cleanup warning: " + e.getMessage());
        }
    }
}
