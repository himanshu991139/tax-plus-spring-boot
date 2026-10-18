package com.meesho.gst;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Utility to generate realistic sample Meesho Excel reports (Sales, Return, and Invoice Details)
 * for testing the conversion API locally and via Swagger UI.
 */
public class SampleDataGenerator {

    public static void main(String[] args) throws IOException {
        File outputDir = new File("sample-data");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        generateSalesFile(new File(outputDir, "tcs_sales.xlsx"));
        generateReturnFile(new File(outputDir, "tcs_sales_return.xlsx"));
        generateTaxInvoiceFile(new File(outputDir, "tax_invoice_details.xlsx"));

        System.out.println("Sample Excel files generated successfully in 'sample-data/' folder!");
    }

    public static void generateSalesFile(File target) throws IOException {
        try (Workbook wb = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(target)) {
            Sheet sheet = wb.createSheet("TCS_Sales");

            // Header row
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Sub Order No");
            header.createCell(1).setCellValue("End Customer State (New)");
            header.createCell(2).setCellValue("GST Rate");
            header.createCell(3).setCellValue("Total Taxable Sale Value");
            header.createCell(4).setCellValue("Tax Amount");

            // Sample rows:
            // 1. Karnataka (Intra for KA seller 29) 18%
            addRow(sheet, 1, "ORD1001", "Karnataka", 18.0, 1000.00, 180.00);
            addRow(sheet, 2, "ORD1002", "KARNATAKA", 18.0, 1500.00, 270.00);
            addRow(sheet, 3, "ORD1003", "Karnataka", 5.0, 2000.00, 100.00);

            // 2. Maharashtra (Inter) 18%
            addRow(sheet, 4, "ORD1004", "Maharashtra", 18.0, 5000.00, 900.00);
            addRow(sheet, 5, "ORD1005", "27-Maharashtra", 18.0, 3000.00, 540.00);

            // 3. Delhi (Inter) 12%
            addRow(sheet, 6, "ORD1006", "Delhi", 12.0, 2500.00, 300.00);

            // 4. Tamil Nadu (Inter) 18%
            addRow(sheet, 7, "ORD1007", "Tamil Nadu", 18.0, 4200.00, 756.00);

            // 5. Uttar Pradesh (Inter) 5%
            addRow(sheet, 8, "ORD1008", "Uttar Pradesh", 5.0, 1800.00, 90.00);

            // Auto-size columns
            for (int i = 0; i < 5; i++) {
                sheet.autoSizeColumn(i);
            }

            wb.write(fos);
        }
    }

    public static void generateReturnFile(File target) throws IOException {
        try (Workbook wb = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(target)) {
            Sheet sheet = wb.createSheet("TCS_Sales_Return");

            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Sub Order No");
            header.createCell(1).setCellValue("End Customer State (New)");
            header.createCell(2).setCellValue("GST Rate");
            header.createCell(3).setCellValue("Total Taxable Sale Value");
            header.createCell(4).setCellValue("Tax Amount");

            // Return/RTO rows to net against forward sales:
            // Maharashtra: return 1000.00 (from 8000.00 total)
            addRow(sheet, 1, "ORD1004-R", "Maharashtra", 18.0, 1000.00, 180.00);

            // Karnataka: return 500.00
            addRow(sheet, 2, "ORD1002-R", "Karnataka", 18.0, 500.00, 90.00);

            // Delhi: return 500.00
            addRow(sheet, 3, "ORD1006-R", "Delhi", 12.0, 500.00, 60.00);

            for (int i = 0; i < 5; i++) {
                sheet.autoSizeColumn(i);
            }

            wb.write(fos);
        }
    }

    public static void generateTaxInvoiceFile(File target) throws IOException {
        try (Workbook wb = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(target)) {
            Sheet sheet = wb.createSheet("Tax_Invoice_Details");

            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Invoice Number");
            header.createCell(1).setCellValue("Invoice Date");
            header.createCell(2).setCellValue("Sub Order No");
            header.createCell(3).setCellValue("Invoice Value");

            addRow(sheet, 1, "INV/2026/001", "2026-07-05", "ORD1001", 1180.00);
            addRow(sheet, 2, "INV/2026/002", "2026-07-06", "ORD1002", 1770.00);
            addRow(sheet, 3, "INV/2026/003", "2026-07-10", "ORD1004", 5900.00);

            for (int i = 0; i < 4; i++) {
                sheet.autoSizeColumn(i);
            }

            wb.write(fos);
        }
    }

    private static void addRow(Sheet sheet, int rowIdx, Object... values) {
        Row row = sheet.createRow(rowIdx);
        for (int i = 0; i < values.length; i++) {
            Cell cell = row.createCell(i);
            Object v = values[i];
            if (v instanceof Number) {
                cell.setCellValue(((Number) v).doubleValue());
            } else if (v != null) {
                cell.setCellValue(v.toString());
            }
        }
    }
}
