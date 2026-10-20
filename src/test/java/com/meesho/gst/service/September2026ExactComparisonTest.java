package com.meesho.gst.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meesho.gst.dto.Gstr1Payload;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class September2026ExactComparisonTest {

    @Test
    void compareMeeshoPayload() throws Exception {
        File dir = new File("D:\\reports\\sep 2026 meesho");
        byte[] salesBytes = Files.readAllBytes(new File(dir, "tcs_sales.xlsx").toPath());
        byte[] returnBytes = Files.readAllBytes(new File(dir, "tcs_sales_return.xlsx").toPath());
        byte[] invoiceBytes = Files.readAllBytes(new File(dir, "Tax_invoice_details.xlsx").toPath());

        MockMultipartFile salesFile = new MockMultipartFile("tcs_sales", "tcs_sales.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", salesBytes);
        MockMultipartFile returnFile = new MockMultipartFile("tcs_sales_return", "tcs_sales_return.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", returnBytes);
        MockMultipartFile invoiceFile = new MockMultipartFile("tax_invoice_details", "tax_invoice_details.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", invoiceBytes);

        MeeshoGstr1Service service = new MeeshoGstr1Service();
        Gstr1Payload meeshoPayload = service.processReports(salesFile, returnFile, invoiceFile, "09CDQPT2928D1Z3", "092026", "09");

        ObjectMapper mapper = new ObjectMapper();
        System.out.println("=== MEESHO GENERATED PAYLOAD ===");
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(meeshoPayload));

        File expectedFile = new File("D:\\downloads\\September 2026 xcleani GSTR1 GSTProSeller 1792518594181.json");
        if (!expectedFile.exists()) {
            File[] matches = new File("D:\\downloads").listFiles((d, name) -> name.contains("GSTProSeller") && name.endsWith(".json"));
            if (matches != null && matches.length > 0) {
                expectedFile = matches[0];
            }
        }
        if (expectedFile != null && expectedFile.exists()) {
            JsonNode expected = mapper.readTree(expectedFile);
            System.out.println("=== EXPECTED COMBINED PAYLOAD ===");
            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(expected));
        }
    }
}
