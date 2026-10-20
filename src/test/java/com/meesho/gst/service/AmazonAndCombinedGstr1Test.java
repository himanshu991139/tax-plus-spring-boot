package com.meesho.gst.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meesho.gst.dto.Gstr1Payload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class AmazonAndCombinedGstr1Test {

    private final MeeshoGstr1Service meeshoService = new MeeshoGstr1Service();
    private final AmazonGstr1Service amazonService = new AmazonGstr1Service();
    private final GstCombineService combineService = new GstCombineService();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("Tests Amazon standalone MTR report conversion")
    void testAmazonStandalone() throws Exception {
        File dir = new File("D:\\reports\\sep 2026 meesho");
        byte[] mtrBytes = Files.readAllBytes(new File(dir, "MTR_B2C-SEPTEMBER-2026-AGALJ4XGBAA7L.csv").toPath());
        MockMultipartFile mtrFile = new MockMultipartFile("mtr_file", "MTR_B2C-SEPTEMBER-2026-AGALJ4XGBAA7L.csv", "text/csv", mtrBytes);

        Gstr1Payload amazonPayload = amazonService.processReport(mtrFile, "09CDQPT2928D1Z3", "092026", "09");
        assertNotNull(amazonPayload);
        assertEquals("09CDQPT2928D1Z3", amazonPayload.getGstin());
        assertEquals("092026", amazonPayload.getFp());

        // Table 7 B2CS items count: 8 positive states
        assertEquals(8, amazonPayload.getB2cs().size());

        // Table 12 HSN: 3926, qty 17, txval 3121.15
        assertNotNull(amazonPayload.getHsn());
        assertEquals(1, amazonPayload.getHsn().getHsnB2c().size());
        assertEquals("3926", amazonPayload.getHsn().getHsnB2c().get(0).getHsnSc());
        assertEquals(17, amazonPayload.getHsn().getHsnB2c().get(0).getQty());
        assertEquals(3121.15, amazonPayload.getHsn().getHsnB2c().get(0).getTxval());
        assertEquals(430.32, amazonPayload.getHsn().getHsnB2c().get(0).getIamt());
        assertEquals(65.74, amazonPayload.getHsn().getHsnB2c().get(0).getCamt());
        assertEquals(65.74, amazonPayload.getHsn().getHsnB2c().get(0).getSamt());

        // Table 14 ECO: Amazon 09AAICA3918J1CR, suppval 3121.15
        assertNotNull(amazonPayload.getSupeco());
        assertEquals(1, amazonPayload.getSupeco().getClttx().size());
        assertEquals("09AAICA3918J1CR", amazonPayload.getSupeco().getClttx().get(0).getEtin());
        assertEquals(3121.15, amazonPayload.getSupeco().getClttx().get(0).getSuppval());
        assertEquals(430.32, amazonPayload.getSupeco().getClttx().get(0).getIgst());
        assertEquals(65.74, amazonPayload.getSupeco().getClttx().get(0).getCgst());
        assertEquals(65.74, amazonPayload.getSupeco().getClttx().get(0).getSgst());

        // Table 13 DOCS: from IN-77, to IN-129, totnum 37, cancel 11, net_issue 26
        assertNotNull(amazonPayload.getDocIssue());
        assertEquals(1, amazonPayload.getDocIssue().getDocDet().get(0).getDocs().size());
        assertEquals("IN-77", amazonPayload.getDocIssue().getDocDet().get(0).getDocs().get(0).getFrom());
        assertEquals("IN-129", amazonPayload.getDocIssue().getDocDet().get(0).getDocs().get(0).getTo());
        assertEquals(37, amazonPayload.getDocIssue().getDocDet().get(0).getDocs().get(0).getTotnum());
        assertEquals(11, amazonPayload.getDocIssue().getDocDet().get(0).getDocs().get(0).getCancel());
        assertEquals(26, amazonPayload.getDocIssue().getDocDet().get(0).getDocs().get(0).getNetIssue());
    }

    @Test
    @DisplayName("Tests Combined Meesho + Amazon conversion matching exact user ground truth")
    void testCombinedExactMatch() throws Exception {
        File dir = new File("D:\\reports\\sep 2026 meesho");
        byte[] salesBytes = Files.readAllBytes(new File(dir, "tcs_sales.xlsx").toPath());
        byte[] returnBytes = Files.readAllBytes(new File(dir, "tcs_sales_return.xlsx").toPath());
        byte[] invoiceBytes = Files.readAllBytes(new File(dir, "Tax_invoice_details.xlsx").toPath());
        byte[] mtrBytes = Files.readAllBytes(new File(dir, "MTR_B2C-SEPTEMBER-2026-AGALJ4XGBAA7L.csv").toPath());

        MockMultipartFile salesFile = new MockMultipartFile("tcs_sales", "tcs_sales.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", salesBytes);
        MockMultipartFile returnFile = new MockMultipartFile("tcs_sales_return", "tcs_sales_return.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", returnBytes);
        MockMultipartFile invoiceFile = new MockMultipartFile("tax_invoice_details", "tax_invoice_details.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", invoiceBytes);
        MockMultipartFile mtrFile = new MockMultipartFile("mtr_file", "MTR_B2C-SEPTEMBER-2026-AGALJ4XGBAA7L.csv", "text/csv", mtrBytes);

        Gstr1Payload meeshoPayload = meeshoService.processReports(salesFile, returnFile, invoiceFile, "09CDQPT2928D1Z3", "092026", "09");
        Gstr1Payload amazonPayload = amazonService.processReport(mtrFile, "09CDQPT2928D1Z3", "092026", "09");
        Gstr1Payload combinedPayload = combineService.combine(meeshoPayload, amazonPayload);

        File expectedFile = new File("D:\\downloads\\September 2026 xcleani GSTR1 GSTProSeller 1792518594181.json");
        if (!expectedFile.exists()) {
            File[] matches = new File("D:\\downloads").listFiles((d, name) -> name.contains("GSTProSeller") && name.endsWith(".json"));
            if (matches != null && matches.length > 0) {
                expectedFile = matches[0];
            }
        }
        JsonNode expected = mapper.readTree(expectedFile);
        JsonNode actual = mapper.readTree(combineService.serializePayload(combinedPayload));

        // Root checks
        assertEquals(expected.get("gstin").asText(), actual.get("gstin").asText());
        assertEquals(expected.get("fp").asText(), actual.get("fp").asText());
        assertEquals(expected.get("version").asText(), actual.get("version").asText());
        assertEquals(expected.get("hash").asText(), actual.get("hash").asText());

        // B2CS checks (17 items)
        assertEquals(expected.get("b2cs").size(), actual.get("b2cs").size());
        for (int i = 0; i < expected.get("b2cs").size(); i++) {
            JsonNode expItem = expected.get("b2cs").get(i);
            String pos = expItem.get("pos").asText();
            JsonNode actItem = null;
            for (JsonNode n : actual.get("b2cs")) {
                if (n.get("pos").asText().equals(pos)) {
                    actItem = n;
                    break;
                }
            }
            assertNotNull(actItem, "Missing POS in actual B2CS: " + pos);
            assertEquals(expItem.get("sply_ty").asText(), actItem.get("sply_ty").asText());
            assertEquals(expItem.get("rt").asInt(), actItem.get("rt").asInt());
            assertEquals(expItem.get("txval").asDouble(), actItem.get("txval").asDouble(), 0.0001, "Txval mismatch for POS " + pos);
            if (expItem.has("iamt")) {
                assertEquals(expItem.get("iamt").asDouble(), actItem.get("iamt").asDouble(), 0.0001, "Iamt mismatch for POS " + pos);
            }
            if (expItem.has("camt")) {
                assertEquals(expItem.get("camt").asDouble(), actItem.get("camt").asDouble(), 0.0001, "Camt mismatch for POS " + pos);
            }
            if (expItem.has("samt")) {
                assertEquals(expItem.get("samt").asDouble(), actItem.get("samt").asDouble(), 0.0001, "Samt mismatch for POS " + pos);
            }
        }

        // HSN checks (2 items: Amazon 3926 and Meesho 392690)
        assertEquals(2, actual.get("hsn").get("hsn_b2c").size());
        JsonNode actualHsn1 = actual.get("hsn").get("hsn_b2c").get(0);
        JsonNode actualHsn2 = actual.get("hsn").get("hsn_b2c").get(1);

        assertEquals("3926", actualHsn1.get("hsn_sc").asText());
        assertEquals(17, actualHsn1.get("qty").asInt());
        assertEquals(3121.15, actualHsn1.get("txval").asDouble(), 0.01);
        assertEquals(430.32, actualHsn1.get("iamt").asDouble(), 0.01);
        assertEquals(65.74, actualHsn1.get("camt").asDouble(), 0.01);
        assertEquals(65.74, actualHsn1.get("samt").asDouble(), 0.01);

        assertEquals("392690", actualHsn2.get("hsn_sc").asText());
        assertEquals(33, actualHsn2.get("qty").asInt());
        assertEquals(3901.66, actualHsn2.get("txval").asDouble(), 0.01);
        assertEquals(552.66, actualHsn2.get("iamt").asDouble(), 0.01);
        assertEquals(74.82, actualHsn2.get("camt").asDouble(), 0.01);
        assertEquals(74.82, actualHsn2.get("samt").asDouble(), 0.01);

        // SUPECO checks (2 items: Amazon 09AAICA3918J1CR and Meesho 09AARCM9332R1CM)
        assertEquals(2, actual.get("supeco").get("clttx").size());
        JsonNode eco1 = actual.get("supeco").get("clttx").get(0);
        JsonNode eco2 = actual.get("supeco").get("clttx").get(1);

        assertEquals("09AAICA3918J1CR", eco1.get("etin").asText());
        assertEquals(3121.15, eco1.get("suppval").asDouble(), 0.0001);
        assertEquals(430.32, eco1.get("igst").asDouble(), 0.0001);
        assertEquals(65.74, eco1.get("cgst").asDouble(), 0.0001);
        assertEquals(65.74, eco1.get("sgst").asDouble(), 0.0001);

        assertEquals("09AARCM9332R1CM", eco2.get("etin").asText());
        assertEquals(3901.66, eco2.get("suppval").asDouble(), 0.0001);
        assertEquals(552.66, eco2.get("igst").asDouble(), 0.0001);
        assertEquals(74.82, eco2.get("cgst").asDouble(), 0.0001);
        assertEquals(74.82, eco2.get("sgst").asDouble(), 0.0001);

        // DOC ISSUE checks (2 items: Meesho 6zxvu27351 to 6zxvu27CM7, Amazon IN-77 to IN-129)
        assertEquals(2, actual.get("doc_issue").get("doc_det").get(0).get("docs").size());
        JsonNode doc1 = actual.get("doc_issue").get("doc_det").get(0).get("docs").get(0);
        JsonNode doc2 = actual.get("doc_issue").get("doc_det").get(0).get("docs").get(1);

        assertEquals("6zxvu27351", doc1.get("from").asText());
        assertEquals("6zxvu27CM7", doc1.get("to").asText());
        assertEquals(116, doc1.get("totnum").asInt());
        assertEquals(43, doc1.get("cancel").asInt());
        assertEquals(73, doc1.get("net_issue").asInt());

        assertEquals("IN-77", doc2.get("from").asText());
        assertEquals("IN-129", doc2.get("to").asText());
        assertEquals(37, doc2.get("totnum").asInt());
        assertEquals(11, doc2.get("cancel").asInt());
        assertEquals(26, doc2.get("net_issue").asInt());
    }
}
