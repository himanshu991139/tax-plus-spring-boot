package com.meesho.gst.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meesho.gst.dto.Gstr1Payload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MeeshoExactDataTest {

    private final MeeshoGstr1Service service = new MeeshoGstr1Service();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("Matches the user's exact Meesho September 2026 ground truth dataset and JSON schema")
    void testExactUserDataset() throws IOException {
        Path scratchDir = Path.of("C:\\Users\\himan\\.gemini\\antigravity\\brain\\8f3c8c3a-7800-4223-91e6-80457e1fa049\\scratch");

        byte[] salesBytes = Files.readAllBytes(scratchDir.resolve("tcs_sales.csv"));
        byte[] returnBytes = Files.readAllBytes(scratchDir.resolve("tcs_sales_return.csv"));
        byte[] invoiceBytes = Files.readAllBytes(scratchDir.resolve("tax_invoice_details.csv"));
        String expectedJsonStr = Files.readString(scratchDir.resolve("expected.json"));

        MockMultipartFile salesFile = new MockMultipartFile("tcs_sales", "tcs_sales.csv", "text/csv", salesBytes);
        MockMultipartFile returnFile = new MockMultipartFile("tcs_sales_return", "tcs_sales_return.csv", "text/csv", returnBytes);
        MockMultipartFile invoiceFile = new MockMultipartFile("tax_invoice_details", "tax_invoice_details.csv", "text/csv", invoiceBytes);

        String actualJsonStr = service.convertToGstr1JsonString(
                salesFile,
                returnFile,
                invoiceFile,
                "09OYVPS2177B2ZI",
                "092026",
                "09"
        );

        JsonNode actual = mapper.readTree(actualJsonStr);
        JsonNode expected = mapper.readTree(expectedJsonStr);

        // Verify root attributes
        assertEquals("09OYVPS2177B2ZI", actual.get("gstin").asText());
        assertEquals("092026", actual.get("fp").asText());
        assertEquals("GST3.2.3", actual.get("version").asText());
        assertEquals("hash", actual.get("hash").asText());

        // Verify B2CS count
        assertEquals(15, actual.get("b2cs").size());

        // Verify HSN
        JsonNode actualHsn = actual.get("hsn").get("hsn_b2c").get(0);
        JsonNode expectedHsn = expected.get("hsn").get("hsn_b2c").get(0);
        assertEquals(expectedHsn.get("hsn_sc").asText(), actualHsn.get("hsn_sc").asText());
        assertEquals(expectedHsn.get("qty").asInt(), actualHsn.get("qty").asInt());
        assertEquals(expectedHsn.get("txval").asDouble(), actualHsn.get("txval").asDouble());
        assertEquals(expectedHsn.get("iamt").asDouble(), actualHsn.get("iamt").asDouble());
        assertEquals(expectedHsn.get("camt").asDouble(), actualHsn.get("camt").asDouble());
        assertEquals(expectedHsn.get("samt").asDouble(), actualHsn.get("samt").asDouble());

        // Verify SUPECO (Table 14)
        JsonNode actualEco = actual.get("supeco").get("clttx").get(0);
        JsonNode expectedEco = expected.get("supeco").get("clttx").get(0);
        assertEquals(expectedEco.get("etin").asText(), actualEco.get("etin").asText());
        assertEquals(expectedEco.get("suppval").asDouble(), actualEco.get("suppval").asDouble());
        assertEquals(expectedEco.get("igst").asDouble(), actualEco.get("igst").asDouble());
        assertEquals(expectedEco.get("cgst").asDouble(), actualEco.get("cgst").asDouble());
        assertEquals(expectedEco.get("sgst").asDouble(), actualEco.get("sgst").asDouble());

        // Verify DOC ISSUE (Table 13)
        JsonNode actualDoc = actual.get("doc_issue").get("doc_det").get(0).get("docs").get(0);
        JsonNode expectedDoc = expected.get("doc_issue").get("doc_det").get(0).get("docs").get(0);
        assertEquals(expectedDoc.get("totnum").asInt(), actualDoc.get("totnum").asInt());
        assertEquals(expectedDoc.get("cancel").asInt(), actualDoc.get("cancel").asInt());
        assertEquals(expectedDoc.get("net_issue").asInt(), actualDoc.get("net_issue").asInt());
        assertEquals(expectedDoc.get("from").asText(), actualDoc.get("from").asText());
        assertEquals(expectedDoc.get("to").asText(), actualDoc.get("to").asText());
    }
}
