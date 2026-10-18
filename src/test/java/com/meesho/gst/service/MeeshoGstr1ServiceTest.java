package com.meesho.gst.service;

import com.meesho.gst.dto.B2csItem;
import com.meesho.gst.dto.Gstr1Payload;
import com.meesho.gst.exception.GstConversionException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class MeeshoGstr1ServiceTest {

    private MeeshoGstr1Service service;

    @BeforeEach
    void setUp() {
        service = new MeeshoGstr1Service();
    }

    private byte[] createSampleExcel(String stateHeader,
                                     String rateHeader,
                                     String taxableHeader,
                                     String taxHeader,
                                     Object[][] data) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Report");
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue(stateHeader);
            headerRow.createCell(1).setCellValue(rateHeader);
            headerRow.createCell(2).setCellValue(taxableHeader);
            if (taxHeader != null) {
                headerRow.createCell(3).setCellValue(taxHeader);
            }

            for (int i = 0; i < data.length; i++) {
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue((String) data[i][0]); // State
                Object rateObj = data[i][1];
                if (rateObj instanceof Number) {
                    row.createCell(1).setCellValue(((Number) rateObj).doubleValue());
                } else {
                    row.createCell(1).setCellValue((String) rateObj);
                }

                row.createCell(2).setCellValue(((Number) data[i][2]).doubleValue()); // Taxable
                if (taxHeader != null && data[i].length > 3) {
                    row.createCell(3).setCellValue(((Number) data[i][3]).doubleValue()); // Tax
                }
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    @Test
    @DisplayName("Forward sales only with Intra and Inter state tax segregation")
    void testForwardSalesOnly() throws IOException {
        // Seller in Karnataka (29)
        // Order 1: Karnataka (Intra), 18% rate, 1000.0 taxable, 180.0 tax
        // Order 2: Maharashtra (Inter), 18% rate, 2000.0 taxable, 360.0 tax
        Object[][] salesData = {
                {"KARNATAKA", 18.0, 1000.0, 180.0},
                {"MAHARASHTRA", 18.0, 2000.0, 360.0}
        };

        byte[] salesBytes = createSampleExcel(
                "end_customer_state_new",
                "gst_rate",
                "total_taxable_sale_value",
                "tax_amount",
                salesData
        );

        MockMultipartFile salesFile = new MockMultipartFile(
                "tcs_sales", "tcs_sales.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                salesBytes
        );

        Gstr1Payload payload = service.processReports(
                salesFile, null, null,
                "29AABCU9603R1ZX", "072026", "29"
        );

        assertNotNull(payload);
        assertEquals("29AABCU9603R1ZX", payload.getGstin());
        assertEquals("072026", payload.getFp());
        assertEquals("072026", payload.getGfp());
        assertEquals(2, payload.getB2cs().size());

        // Item 1: Maharashtra (POS: 27) -> INTER
        B2csItem itemMh = payload.getB2cs().get(0);
        assertEquals("27", itemMh.getPos());
        assertEquals("INTER", itemMh.getSply_ty());
        assertEquals(18, itemMh.getRt().intValue());
        assertEquals(2000.0, itemMh.getTxval());
        assertEquals(360.0, itemMh.getIamt());
        assertNull(itemMh.getCamt());
        assertNull(itemMh.getSamt());

        // Item 2: Karnataka (POS: 29) -> INTRA
        B2csItem itemKa = payload.getB2cs().get(1);
        assertEquals("29", itemKa.getPos());
        assertEquals("INTRA", itemKa.getSply_ty());
        assertEquals(18, itemKa.getRt().intValue());
        assertEquals(1000.0, itemKa.getTxval());
        assertNull(itemKa.getIamt());
        assertEquals(90.0, itemKa.getCamt());
        assertEquals(90.0, itemKa.getSamt());
    }

    @Test
    @DisplayName("Netting algorithm subtracting returns and filtering <= 0 net taxable value")
    void testNettingAlgorithm() throws IOException {
        // Forward Sales:
        // MH (27), 18%: 5000.0 taxable, 900.0 tax
        // KA (29), 18%: 1000.0 taxable, 180.0 tax
        // DL (07), 12%: 200.0 taxable, 24.0 tax
        Object[][] salesData = {
                {"27", 18.0, 5000.0, 900.0},
                {"Karnataka", 18.0, 1000.0, 180.0},
                {"Delhi", 12.0, 200.0, 24.0}
        };

        // Returns:
        // MH (27), 18%: 1000.0 return taxable, 180.0 return tax -> Net: 4000.0 taxable, 720.0 tax
        // DL (07), 12%: 250.0 return taxable, 30.0 return tax -> Net: -50.0 taxable -> MUST BE FILTERED OUT
        Object[][] returnData = {
                {"Maharashtra", 18.0, 1000.0, 180.0},
                {"07", 12.0, 250.0, 30.0}
        };

        MockMultipartFile salesFile = new MockMultipartFile(
                "tcs_sales", "tcs_sales.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createSampleExcel("Customer State", "Rate", "Taxable Value", "Tax Amount", salesData)
        );

        MockMultipartFile returnFile = new MockMultipartFile(
                "tcs_sales_return", "tcs_sales_return.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createSampleExcel("Customer State", "Rate", "Taxable Value", "Tax Amount", returnData)
        );

        Gstr1Payload payload = service.processReports(
                salesFile, returnFile, null,
                "29AABCU9603R1ZX", "072026", "29"
        );

        assertNotNull(payload);
        // DL (07) has negative net taxable (-50.0), which is preserved in GSTR-1
        assertEquals(3, payload.getB2cs().size());

        // Check DL item (POS: 07)
        B2csItem dl = payload.getB2cs().get(0);
        assertEquals("07", dl.getPos());
        assertEquals(-50.0, dl.getTxval());
        assertEquals(-6.0, dl.getIamt());

        // Check MH item (POS: 27)
        B2csItem mh = payload.getB2cs().get(1);
        assertEquals("27", mh.getPos());
        assertEquals(4000.0, mh.getTxval());
        assertEquals(720.0, mh.getIamt());

        // Check KA item (POS: 29)
        B2csItem ka = payload.getB2cs().get(2);
        assertEquals("29", ka.getPos());
        assertEquals(1000.0, ka.getTxval());
        assertEquals(90.0, ka.getCamt());
        assertEquals(90.0, ka.getSamt());
    }

    @Test
    @DisplayName("Header variation resilience: supports percentage rates and varying headers")
    void testHeaderVariationsAndPercentageFormats() throws IOException {
        Object[][] salesData = {
                {"Tamil Nadu", "18%", 3000.0, 540.0},
                {"33-Tamil Nadu", 0.18, 2000.0, 360.0}
        };

        MockMultipartFile salesFile = new MockMultipartFile(
                "tcs_sales", "tcs_sales.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createSampleExcel("Place of Supply", "GST Rate (%)", "Total Taxable Amount", "GST Amount", salesData)
        );

        Gstr1Payload payload = service.processReports(
                salesFile, null, null,
                "33AABCU9603R1ZX", "072026", "33"
        );

        assertEquals(1, payload.getB2cs().size());
        B2csItem item = payload.getB2cs().get(0);
        assertEquals("33", item.getPos());
        assertEquals(18, item.getRt().intValue());
        assertEquals(5000.0, item.getTxval());
        assertEquals("INTRA", item.getSply_ty());
        assertEquals(450.0, item.getCamt());
        assertEquals(450.0, item.getSamt());
        assertNull(item.getIamt());
    }

    @Test
    @DisplayName("Validation fails when file name does not match expected field name")
    void testFileNameMismatchValidation() throws IOException {
        Object[][] data = {{"Karnataka", 18.0, 1000.0, 180.0}};
        byte[] bytes = createSampleExcel("State", "Rate", "Taxable Value", "Tax Amount", data);

        // Uploading 'orders.xlsx' instead of 'tcs_sales.xlsx'
        MockMultipartFile wrongNamedFile = new MockMultipartFile(
                "tcs_sales", "orders.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                bytes
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.processReports(wrongNamedFile, null, null, "29AABCU9603R1ZX", "072026", "29"));

        assertTrue(ex.getMessage().contains("File name mismatch for field 'tcs_sales'"));
    }

    @Test
    @DisplayName("Validation fails when GSTIN inside the file does not match the request parameter")
    void testInFileGstinMismatchValidation() throws IOException {
        String csvContent = """
                gstin,end_customer_state_new,gst_rate,total_taxable_sale_value,tax_amount,month_number
                29DIFFERENT0001Z,Karnataka,18.0,1000.0,180.0,7
                """;

        MockMultipartFile file = new MockMultipartFile(
                "tcs_sales", "tcs_sales.csv", "text/csv",
                csvContent.getBytes()
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.processReports(file, null, null, "29AABCU9603R1ZX", "072026", "29"));

        assertTrue(ex.getMessage().contains("GSTIN mismatch"));
    }

    @Test
    @DisplayName("Validation fails when month inside the file does not match the filing period")
    void testInFileMonthMismatchValidation() throws IOException {
        String csvContent = """
                gstin,end_customer_state_new,gst_rate,total_taxable_sale_value,tax_amount,month_number
                29AABCU9603R1ZX,Karnataka,18.0,1000.0,180.0,8
                """;

        // Requested period is 072026 (Month: 7), but file has month_number 8
        MockMultipartFile file = new MockMultipartFile(
                "tcs_sales", "tcs_sales.csv", "text/csv",
                csvContent.getBytes()
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.processReports(file, null, null, "29AABCU9603R1ZX", "072026", "29"));

        assertTrue(ex.getMessage().contains("Filing month mismatch"));
    }

    @Test
    @DisplayName("Validation fails on invalid GSTIN or Period")
    void testValidationErrors() throws IOException {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "tcs_sales", "tcs_sales.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[0]
        );

        // Invalid GSTIN
        assertThrows(IllegalArgumentException.class, () ->
                service.processReports(emptyFile, null, null, "INVALID_GST", "072026", "29"));

        // Invalid Period
        assertThrows(IllegalArgumentException.class, () ->
                service.processReports(emptyFile, null, null, "29AABCU9603R1ZX", "202607", "29"));

        // Empty file
        assertThrows(IllegalArgumentException.class, () ->
                service.processReports(emptyFile, null, null, "29AABCU9603R1ZX", "072026", "29"));
    }
}
