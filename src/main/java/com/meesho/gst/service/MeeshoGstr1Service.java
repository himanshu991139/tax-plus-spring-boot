package com.meesho.gst.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.meesho.gst.dto.*;
import com.meesho.gst.exception.GstConversionException;
import com.meesho.gst.util.StateCodeUtil;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service to process Meesho e-commerce tax reports (Excel or CSV)
 * with strict validations:
 * - Exact filename validation matching uploaded field name (tcs_sales, tcs_sales_return, tax_invoice_details)
 * - GSTIN validation matching parameter against GSTIN in file rows
 * - Month and period validation matching MMYYYY against month_number and dates in files
 *
 * Generates official GSTR-1 JSON matching GST portal specification:
 * - Table 7: B2CS
 * - Table 12: HSN Summary (hsn_b2c)
 * - Table 14: E-Commerce Operator supplies (supeco / clttx)
 * - Table 13: Document summary (doc_issue / doc_det)
 */
@Service
public class MeeshoGstr1Service {

    private static final Logger log = LoggerFactory.getLogger(MeeshoGstr1Service.class);

    private static final Pattern GSTIN_PATTERN =
            Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$");
    private static final Pattern PERIOD_PATTERN =
            Pattern.compile("^(0[1-9]|1[0-2])[2-9][0-9]{3}$");
    private static final Pattern DATE_PATTERN =
            Pattern.compile("([0-9]{4})-([0-9]{2})-[0-9]{2}");

    private final ObjectMapper objectMapper;

    public MeeshoGstr1Service() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    public MeeshoGstr1Service(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Converts uploaded Meesho reports into GSTR-1 JSON string matching official schema.
     */
    public String convertToGstr1JsonString(MultipartFile tcsSalesFile,
                                           MultipartFile tcsSalesReturnFile,
                                           MultipartFile taxInvoiceDetailsFile,
                                           String gstin,
                                           String period,
                                           String sellerStateCode) {
        Gstr1Payload payload = processReports(tcsSalesFile, tcsSalesReturnFile,
                taxInvoiceDetailsFile, gstin, period, sellerStateCode);
        return serializePayload(payload);
    }

    /**
     * Core processing logic handling validations, Table 7 (B2CS), Table 12 (HSN),
     * Table 14 (supeco), and Table 13 (doc_issue).
     */
    public Gstr1Payload processReports(MultipartFile tcsSalesFile,
                                       MultipartFile tcsSalesReturnFile,
                                       MultipartFile taxInvoiceDetailsFile,
                                       String gstin,
                                       String period,
                                       String sellerStateCode) {
        // 1. Validate inputs and file names
        validateInputs(gstin, period, sellerStateCode, tcsSalesFile, tcsSalesReturnFile, taxInvoiceDetailsFile);

        String normalizedGstin = gstin.trim().toUpperCase();
        String normalizedPeriod = period.trim();
        String normalizedSellerState = StateCodeUtil.resolveStateCode(sellerStateCode);

        int expectedMonth = Integer.parseInt(normalizedPeriod.substring(0, 2));
        int expectedYear = Integer.parseInt(normalizedPeriod.substring(2, 6));

        // 2. Aggregation buckets for Table 7 (B2CS), Table 12 (HSN), and Table 14 (supeco)
        Map<PosRateKey, ValueAccumulator> b2csMap = new LinkedHashMap<>();
        Map<HsnRateKey, HsnAccumulator> hsnMap = new LinkedHashMap<>();
        Map<String, SupecoAccumulator> supecoMap = new LinkedHashMap<>();

        // 3. Process Forward Sales (+1.0 multiplier) with in-file GSTIN and Month validation
        log.info("Processing Meesho TCS Sales report: {}", tcsSalesFile.getOriginalFilename());
        processReportData(tcsSalesFile, 1.0, normalizedSellerState, normalizedGstin,
                expectedMonth, expectedYear, normalizedPeriod, b2csMap, hsnMap, supecoMap, "TCS Sales");

        // 4. Process Sales Returns (-1.0 multiplier) with in-file GSTIN and Month validation
        if (tcsSalesReturnFile != null && !tcsSalesReturnFile.isEmpty()) {
            log.info("Processing Meesho TCS Sales Return report: {}", tcsSalesReturnFile.getOriginalFilename());
            processReportData(tcsSalesReturnFile, -1.0, normalizedSellerState, normalizedGstin,
                    expectedMonth, expectedYear, normalizedPeriod, b2csMap, hsnMap, supecoMap, "TCS Sales Return");
        }

        // 5. Build Table 7: B2CS items
        List<B2csItem> b2csList = new ArrayList<>();
        for (Map.Entry<PosRateKey, ValueAccumulator> entry : b2csMap.entrySet()) {
            PosRateKey key = entry.getKey();
            ValueAccumulator bucket = entry.getValue();

            double netTxval = round(bucket.taxableValue, 2);
            // Discard zero net taxable values (preserve negative returns when sales are 0)
            if (netTxval == 0.0) {
                continue;
            }

            double netTax = round(bucket.taxAmount, 2);
            String pos = key.getPos();
            Number rate = formatRate(key.getRate());
            boolean isIntra = pos.equals(normalizedSellerState);

            B2csItem item = new B2csItem();
            item.setPos(pos);
            item.setRt(rate);
            item.setTyp("OE");
            item.setTxval(netTxval);
            item.setCsamt(0);

            if (isIntra) {
                item.setSply_ty("INTRA");
                double camt = round(bucket.camt, 2);
                double samt = round(bucket.samt, 2);
                item.setCamt(camt);
                item.setSamt(samt);
                item.setIamt(null); // Omitted in INTRA
            } else {
                item.setSply_ty("INTER");
                item.setIamt(netTax);
                item.setCamt(null); // Omitted in INTER
                item.setSamt(null); // Omitted in INTER
            }

            b2csList.add(item);
        }

        // Sort B2CS items by POS
        b2csList.sort(Comparator.comparing(B2csItem::getPos));

        // 6. Build Table 12: HSN Summary
        List<HsnItem> hsnList = new ArrayList<>();
        int hsnSeq = 1;
        for (Map.Entry<HsnRateKey, HsnAccumulator> entry : hsnMap.entrySet()) {
            HsnRateKey key = entry.getKey();
            HsnAccumulator acc = entry.getValue();

            HsnItem item = new HsnItem();
            item.setNum(hsnSeq++);
            item.setHsnSc(key.getHsn());
            item.setUserDesc("0");
            item.setUqc("PCS");
            item.setQty((int) Math.round(acc.quantity));
            item.setRt(formatRate(key.getRate()));
            item.setTxval(round(acc.taxableValue, 2));
            item.setIamt(round(acc.iamt, 2));
            item.setSamt(round(acc.samt, 2));
            item.setCamt(round(acc.camt, 2));
            item.setCsamt(0.0);

            hsnList.add(item);
        }

        // 7. Build Table 14: E-Commerce Operator (supeco)
        List<SupecoItem> supecoList = new ArrayList<>();
        for (Map.Entry<String, SupecoAccumulator> entry : supecoMap.entrySet()) {
            SupecoAccumulator acc = entry.getValue();
            SupecoItem item = new SupecoItem();
            item.setEtin(entry.getKey());
            item.setSuppval(round(acc.suppval, 2));
            item.setIgst(round(acc.igst, 2));
            item.setCgst(round(acc.cgst, 2));
            item.setSgst(round(acc.sgst, 2));
            item.setCess(0.0);
            item.setFlag("N");
            supecoList.add(item);
        }

        // 8. Build Table 13: Document summary (doc_issue) with month verification
        DocIssueContainer docIssueContainer = null;
        if (taxInvoiceDetailsFile != null && !taxInvoiceDetailsFile.isEmpty()) {
            docIssueContainer = processDocIssue(taxInvoiceDetailsFile, expectedMonth, expectedYear, normalizedPeriod);
        }

        // 9. Assemble Root Payload
        Gstr1Payload payload = new Gstr1Payload(normalizedGstin, normalizedPeriod);
        payload.setB2cs(b2csList);
        if (!hsnList.isEmpty()) {
            payload.setHsn(new HsnContainer(hsnList));
        }
        if (!supecoList.isEmpty()) {
            payload.setSupeco(new SupecoContainer(supecoList));
        }
        if (docIssueContainer != null) {
            payload.setDocIssue(docIssueContainer);
        }

        log.info("Successfully constructed GSTR-1 JSON with {} B2CS items, {} HSN items, {} ECO items",
                b2csList.size(), hsnList.size(), supecoList.size());

        return payload;
    }

    /**
     * Serializes Gstr1Payload to formatted JSON.
     */
    public String serializePayload(Gstr1Payload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new GstConversionException("Failed to serialize GSTR-1 payload to JSON", e);
        }
    }

    /**
     * Validates input parameters and file names against their respective field names.
     */
    private void validateInputs(String gstin, String period, String sellerStateCode,
                                MultipartFile tcsSalesFile,
                                MultipartFile tcsSalesReturnFile,
                                MultipartFile taxInvoiceDetailsFile) {
        if (gstin == null || gstin.trim().isEmpty()) {
            throw new IllegalArgumentException("GSTIN parameter is required and cannot be empty.");
        }
        String cleanGstin = gstin.trim().toUpperCase();
        if (!GSTIN_PATTERN.matcher(cleanGstin).matches()) {
            throw new IllegalArgumentException("Invalid GSTIN format: '" + gstin +
                    "'. Must be a 15-character alphanumeric GSTIN (e.g. 09OYVPS2177B2ZI).");
        }

        if (period == null || period.trim().isEmpty()) {
            throw new IllegalArgumentException("Filing period parameter is required and cannot be empty.");
        }
        String cleanPeriod = period.trim();
        if (!PERIOD_PATTERN.matcher(cleanPeriod).matches()) {
            throw new IllegalArgumentException("Invalid return filing period format: '" + period +
                    "'. Expected MMYYYY (e.g. 092026).");
        }

        if (sellerStateCode == null || sellerStateCode.trim().isEmpty()) {
            throw new IllegalArgumentException("sellerStateCode parameter is required.");
        }
        try {
            StateCodeUtil.resolveStateCode(sellerStateCode);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid sellerStateCode: " + e.getMessage());
        }

        if (tcsSalesFile == null || tcsSalesFile.isEmpty()) {
            throw new IllegalArgumentException("tcs_sales file is required and cannot be empty.");
        }

        // Strict File Name Validations: File names must match their respective field name
        validateFileName(tcsSalesFile, "tcs_sales", "tcs_sales");
        if (tcsSalesReturnFile != null && !tcsSalesReturnFile.isEmpty()) {
            validateFileName(tcsSalesReturnFile, "tcs_sales_return", "tcs_sales_return");
        }
        if (taxInvoiceDetailsFile != null && !taxInvoiceDetailsFile.isEmpty()) {
            validateFileName(taxInvoiceDetailsFile, "tax_invoice_details", "tax_invoice_details");
        }
    }

    /**
     * Validates that the uploaded file's base name matches the expected field name exactly (case-insensitive).
     */
    private void validateFileName(MultipartFile file, String expectedBaseName, String fieldName) {
        if (file == null || file.isEmpty()) return;
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            throw new IllegalArgumentException("Uploaded file for field '" + fieldName + "' has no filename.");
        }

        String clean = originalFilename.trim();
        int lastSlash = Math.max(clean.lastIndexOf('/'), clean.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            clean = clean.substring(lastSlash + 1);
        }

        String lower = clean.toLowerCase();
        if (!lower.endsWith(".xlsx") && !lower.endsWith(".xls") && !lower.endsWith(".csv")) {
            throw new IllegalArgumentException("Invalid file extension for '" + fieldName + "': '" + clean +
                    "'. Supported file extensions are .xlsx, .xls, and .csv.");
        }

        int lastDot = clean.lastIndexOf('.');
        String baseName = lastDot > 0 ? clean.substring(0, lastDot) : clean;

        if (!baseName.equalsIgnoreCase(expectedBaseName)) {
            throw new IllegalArgumentException("File name mismatch for field '" + fieldName + "': expected file named '" +
                    expectedBaseName + ".xlsx' or '" + expectedBaseName + ".csv', but received '" + clean + "'.");
        }
    }

    /**
     * Handles either CSV or Excel format for TCS sales/returns, with in-file GSTIN and Month validation.
     */
    private void processReportData(MultipartFile file,
                                   double multiplier,
                                   String sellerStateCode,
                                   String expectedGstin,
                                   int expectedMonth,
                                   int expectedYear,
                                   String period,
                                   Map<PosRateKey, ValueAccumulator> b2csMap,
                                   Map<HsnRateKey, HsnAccumulator> hsnMap,
                                   Map<String, SupecoAccumulator> supecoMap,
                                   String reportType) {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        if (filename.endsWith(".csv")) {
            processCsvReport(file, multiplier, sellerStateCode, expectedGstin, expectedMonth, expectedYear, period,
                    b2csMap, hsnMap, supecoMap, reportType);
        } else {
            processExcelReport(file, multiplier, sellerStateCode, expectedGstin, expectedMonth, expectedYear, period,
                    b2csMap, hsnMap, supecoMap, reportType);
        }
    }

    private void processExcelReport(MultipartFile file,
                                    double multiplier,
                                    String sellerStateCode,
                                    String expectedGstin,
                                    int expectedMonth,
                                    int expectedYear,
                                    String period,
                                    Map<PosRateKey, ValueAccumulator> b2csMap,
                                    Map<HsnRateKey, HsnAccumulator> hsnMap,
                                    Map<String, SupecoAccumulator> supecoMap,
                                    String reportType) {
        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            if (workbook.getNumberOfSheets() == 0) {
                throw new GstConversionException(reportType + " Excel file contains no worksheets.");
            }

            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter dataFormatter = new DataFormatter();
            FormulaEvaluator formulaEvaluator = workbook.getCreationHelper().createFormulaEvaluator();

            HeaderIndices h = findHeaderIndices(sheet, dataFormatter);
            log.info("Detected headers in {} on row {}: State={}, Rate={}, Taxable={}, Tax={}, HSN={}, Qty={}, ECO={}, GSTIN={}, Month={}",
                    reportType, h.headerRow, h.stateCol, h.rateCol, h.taxableCol, h.taxCol, h.hsnCol, h.qtyCol, h.ecoCol, h.gstinCol, h.monthCol);

            int totalRows = sheet.getLastRowNum();
            for (int r = h.headerRow + 1; r <= totalRows; r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowEmpty(row)) continue;

                // Validate in-file GSTIN
                if (h.gstinCol != -1) {
                    String fileGstin = getCellStringValue(row.getCell(h.gstinCol), dataFormatter, formulaEvaluator);
                    if (fileGstin != null && !fileGstin.trim().isEmpty()) {
                        String cleanFileGstin = fileGstin.trim().toUpperCase();
                        if (!cleanFileGstin.equalsIgnoreCase(expectedGstin)) {
                            throw new IllegalArgumentException("GSTIN mismatch in file '" + file.getOriginalFilename() +
                                    "' (row " + (r + 1) + "): expected GSTIN '" + expectedGstin +
                                    "', but found '" + cleanFileGstin + "'. Please upload report files belonging to the specified GSTIN.");
                        }
                    }
                }

                // Validate in-file Month
                if (h.monthCol != -1) {
                    Double monthNum = getCellDoubleValue(row.getCell(h.monthCol), formulaEvaluator);
                    if (monthNum != null) {
                        int m = (int) Math.round(monthNum);
                        if (m != expectedMonth) {
                            throw new IllegalArgumentException("Filing month mismatch in file '" + file.getOriginalFilename() +
                                    "' (row " + (r + 1) + "): expected month " + expectedMonth + " (from period '" + period +
                                    "'), but found month_number " + m + ". Please verify that the report matches the selected tax period.");
                        }
                    }
                }

                // 1. State / POS
                String rawState = getCellStringValue(row.getCell(h.stateCol), dataFormatter, formulaEvaluator);
                if (rawState == null || rawState.trim().isEmpty()) continue;

                String pos;
                try {
                    pos = StateCodeUtil.resolveStateCode(rawState);
                } catch (Exception ex) {
                    continue;
                }

                // 2. GST Rate
                Double rateVal = parseRateValue(row.getCell(h.rateCol), dataFormatter, formulaEvaluator);
                if (rateVal == null) continue;
                double rate = Math.round(rateVal);

                // 3. Taxable Value
                Double txval = getCellDoubleValue(row.getCell(h.taxableCol), formulaEvaluator);
                if (txval == null) txval = 0.0;

                // 4. Tax Amount
                Double tax = null;
                if (h.taxCol != -1) {
                    tax = getCellDoubleValue(row.getCell(h.taxCol), formulaEvaluator);
                }
                if (tax == null) {
                    tax = round(txval * (rate / 100.0), 2);
                }

                // 5. Quantity & HSN
                double qty = 1.0;
                if (h.qtyCol != -1) {
                    Double q = getCellDoubleValue(row.getCell(h.qtyCol), formulaEvaluator);
                    if (q != null && q > 0) qty = q;
                }
                String hsnCode = "392690";
                if (h.hsnCol != -1) {
                    String code = getCellStringValue(row.getCell(h.hsnCol), dataFormatter, formulaEvaluator);
                    if (code != null && !code.trim().isEmpty()) hsnCode = code.trim();
                }

                // 6. ECO GSTIN
                String ecoGstin = null;
                if (h.ecoCol != -1) {
                    String eco = getCellStringValue(row.getCell(h.ecoCol), dataFormatter, formulaEvaluator);
                    if (eco != null && !eco.trim().isEmpty()) ecoGstin = eco.trim();
                }

                accumulateRecord(pos, rate, txval * multiplier, tax * multiplier, qty * multiplier,
                        hsnCode, ecoGstin, sellerStateCode, b2csMap, hsnMap, supecoMap);
            }
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) throw (IllegalArgumentException) e;
            if (e instanceof GstConversionException) throw (GstConversionException) e;
            throw new GstConversionException("Failed to process " + reportType + ": " + e.getMessage(), e);
        }
    }

    private void processCsvReport(MultipartFile file,
                                  double multiplier,
                                  String sellerStateCode,
                                  String expectedGstin,
                                  int expectedMonth,
                                  int expectedYear,
                                  String period,
                                  Map<PosRateKey, ValueAccumulator> b2csMap,
                                  Map<HsnRateKey, HsnAccumulator> hsnMap,
                                  Map<String, SupecoAccumulator> supecoMap,
                                  String reportType) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) return;

            List<String> headers = parseCsvLine(headerLine);
            HeaderIndices h = findHeaderIndicesFromList(headers);

            String line;
            int rowIdx = 1;
            while ((line = reader.readLine()) != null) {
                rowIdx++;
                if (line.trim().isEmpty()) continue;
                List<String> cols = parseCsvLine(line);

                // Validate in-file GSTIN
                if (h.gstinCol != -1) {
                    String fileGstin = getColValue(cols, h.gstinCol);
                    if (fileGstin != null && !fileGstin.trim().isEmpty()) {
                        String cleanFileGstin = fileGstin.trim().toUpperCase();
                        if (!cleanFileGstin.equalsIgnoreCase(expectedGstin)) {
                            throw new IllegalArgumentException("GSTIN mismatch in file '" + file.getOriginalFilename() +
                                    "' (row " + rowIdx + "): expected GSTIN '" + expectedGstin +
                                    "', but found '" + cleanFileGstin + "'. Please upload report files belonging to the specified GSTIN.");
                        }
                    }
                }

                // Validate in-file Month
                if (h.monthCol != -1) {
                    String monthStr = getColValue(cols, h.monthCol);
                    if (monthStr != null && !monthStr.trim().isEmpty()) {
                        try {
                            int m = (int) Math.round(Double.parseDouble(monthStr.trim()));
                            if (m != expectedMonth) {
                                throw new IllegalArgumentException("Filing month mismatch in file '" + file.getOriginalFilename() +
                                        "' (row " + rowIdx + "): expected month " + expectedMonth + " (from period '" + period +
                                        "'), but found month_number " + m + ". Please verify that the report matches the selected tax period.");
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }

                String rawState = getColValue(cols, h.stateCol);
                if (rawState == null || rawState.trim().isEmpty()) continue;

                String pos;
                try {
                    pos = StateCodeUtil.resolveStateCode(rawState);
                } catch (Exception ex) {
                    continue;
                }

                Double rateVal = parseRateString(getColValue(cols, h.rateCol));
                if (rateVal == null) continue;
                double rate = Math.round(rateVal);

                double txval = parseDoubleSafe(getColValue(cols, h.taxableCol), 0.0);
                double tax = parseDoubleSafe(getColValue(cols, h.taxCol), txval * (rate / 100.0));
                double qty = parseDoubleSafe(getColValue(cols, h.qtyCol), 1.0);

                String hsnCode = getColValue(cols, h.hsnCol);
                if (hsnCode == null || hsnCode.trim().isEmpty()) hsnCode = "392690";

                String ecoGstin = getColValue(cols, h.ecoCol);

                accumulateRecord(pos, rate, txval * multiplier, tax * multiplier, qty * multiplier,
                        hsnCode, ecoGstin, sellerStateCode, b2csMap, hsnMap, supecoMap);
            }
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) throw (IllegalArgumentException) e;
            if (e instanceof GstConversionException) throw (GstConversionException) e;
            throw new GstConversionException("Failed to process CSV " + reportType + ": " + e.getMessage(), e);
        }
    }

    private void accumulateRecord(String pos, double rate, double txval, double tax, double qty,
                                  String hsnCode, String ecoGstin, String sellerStateCode,
                                  Map<PosRateKey, ValueAccumulator> b2csMap,
                                  Map<HsnRateKey, HsnAccumulator> hsnMap,
                                  Map<String, SupecoAccumulator> supecoMap) {
        boolean isIntra = pos.equals(sellerStateCode);

        // 1. Table 7: B2CS
        PosRateKey b2csKey = new PosRateKey(pos, rate);
        ValueAccumulator b2csAcc = b2csMap.computeIfAbsent(b2csKey, k -> new ValueAccumulator());
        b2csAcc.taxableValue += txval;
        b2csAcc.taxAmount += tax;
        if (isIntra) {
            double half = tax / 2.0;
            b2csAcc.camt += half;
            b2csAcc.samt += (tax - half);
        }

        // 2. Table 12: HSN
        HsnRateKey hsnKey = new HsnRateKey(hsnCode, rate);
        HsnAccumulator hsnAcc = hsnMap.computeIfAbsent(hsnKey, k -> new HsnAccumulator());
        hsnAcc.quantity += qty;
        hsnAcc.taxableValue += txval;
        if (isIntra) {
            double half = tax / 2.0;
            hsnAcc.camt += half;
            hsnAcc.samt += (tax - half);
        } else {
            hsnAcc.iamt += tax;
        }

        // 3. Table 14: ECO
        if (ecoGstin != null && !ecoGstin.trim().isEmpty()) {
            SupecoAccumulator supecoAcc = supecoMap.computeIfAbsent(ecoGstin.trim(), k -> new SupecoAccumulator());
            supecoAcc.suppval += txval;
            if (isIntra) {
                double half = tax / 2.0;
                supecoAcc.cgst += half;
                supecoAcc.sgst += (tax - half);
            } else {
                supecoAcc.igst += tax;
            }
        }
    }

    /**
     * Processes tax_invoice_details report for Table 13 (doc_issue) with invoice date validation.
     */
    private DocIssueContainer processDocIssue(MultipartFile file, int expectedMonth, int expectedYear, String period) {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        List<InvoiceRow> rows = new ArrayList<>();

        if (filename.endsWith(".csv")) {
            rows = parseInvoiceDetailsCsv(file);
        } else {
            rows = parseInvoiceDetailsExcel(file);
        }

        if (rows.isEmpty()) {
            return null;
        }

        int totnum = rows.size();
        int cancel = 0;
        List<String> invoiceNumbers = new ArrayList<>();
        int monthMatches = 0;
        int dateCount = 0;

        for (InvoiceRow r : rows) {
            String type = r.type != null ? r.type.trim().toUpperCase() : "";
            // Matches CREDIT NOTE as cancellation
            if (type.equals("CREDIT NOTE") || type.contains("CANCEL")) {
                cancel++;
            }
            if (r.invoiceNo != null && !r.invoiceNo.trim().isEmpty()) {
                invoiceNumbers.add(r.invoiceNo.trim());
            }

            // Verify order/invoice dates
            if (r.dateStr != null && !r.dateStr.trim().isEmpty()) {
                Matcher matcher = DATE_PATTERN.matcher(r.dateStr);
                if (matcher.find()) {
                    dateCount++;
                    int y = Integer.parseInt(matcher.group(1));
                    int m = Integer.parseInt(matcher.group(2));
                    if (y == expectedYear && m == expectedMonth) {
                        monthMatches++;
                    }
                }
            }
        }

        // If date column was present and none or minority of dates belong to the selected month
        if (dateCount > 0 && monthMatches == 0) {
            throw new IllegalArgumentException("Filing month mismatch in file '" + file.getOriginalFilename() +
                    "': expected period '" + period + "' (Month: " + expectedMonth + "), but invoices in file belong to a different month.");
        }

        int netIssue = totnum - cancel;
        invoiceNumbers.sort(String::compareTo);
        String from = !invoiceNumbers.isEmpty() ? invoiceNumbers.get(0) : "";
        String to = !invoiceNumbers.isEmpty() ? invoiceNumbers.get(invoiceNumbers.size() - 1) : "";

        DocItem docItem = new DocItem(1, from, to, totnum, cancel, netIssue);
        DocDetItem docDet = new DocDetItem(1, "Invoices for outward supply", List.of(docItem));
        return new DocIssueContainer(List.of(docDet));
    }

    private List<InvoiceRow> parseInvoiceDetailsExcel(MultipartFile file) {
        List<InvoiceRow> list = new ArrayList<>();
        try (InputStream is = file.getInputStream();
             Workbook wb = WorkbookFactory.create(is)) {
            if (wb.getNumberOfSheets() == 0) return list;
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            int typeCol = -1;
            int invCol = -1;
            int dateCol = -1;
            int headerRow = 0;

            for (int r = 0; r <= Math.min(5, sheet.getLastRowNum()); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    String text = formatter.formatCellValue(row.getCell(c)).trim().toLowerCase();
                    if (text.contains("type")) typeCol = c;
                    if (text.contains("invoice") && (text.contains("no") || text.contains("number"))) invCol = c;
                    if (text.contains("date")) dateCol = c;
                }
                if (typeCol != -1 && invCol != -1) {
                    headerRow = r;
                    break;
                }
            }

            for (int r = headerRow + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowEmpty(row)) continue;
                String type = formatter.formatCellValue(row.getCell(typeCol)).trim();
                String inv = formatter.formatCellValue(row.getCell(invCol)).trim();
                String dateStr = dateCol != -1 ? formatter.formatCellValue(row.getCell(dateCol)).trim() : null;
                list.add(new InvoiceRow(type, inv, dateStr));
            }
        } catch (Exception e) {
            log.warn("Failed to parse tax_invoice_details Excel: {}", e.getMessage());
        }
        return list;
    }

    private List<InvoiceRow> parseInvoiceDetailsCsv(MultipartFile file) {
        List<InvoiceRow> list = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) return list;

            List<String> headers = parseCsvLine(headerLine);
            int typeCol = -1;
            int invCol = -1;
            int dateCol = -1;
            for (int i = 0; i < headers.size(); i++) {
                String h = headers.get(i).trim().toLowerCase();
                if (h.contains("type")) typeCol = i;
                if (h.contains("invoice") && (h.contains("no") || h.contains("number"))) invCol = i;
                if (h.contains("date")) dateCol = i;
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                List<String> cols = parseCsvLine(line);
                String type = getColValue(cols, typeCol);
                String inv = getColValue(cols, invCol);
                String dateStr = getColValue(cols, dateCol);
                list.add(new InvoiceRow(type, inv, dateStr));
            }
        } catch (Exception e) {
            log.warn("Failed to parse tax_invoice_details CSV: {}", e.getMessage());
        }
        return list;
    }

    private static class InvoiceRow {
        final String type;
        final String invoiceNo;
        final String dateStr;

        InvoiceRow(String type, String invoiceNo, String dateStr) {
            this.type = type;
            this.invoiceNo = invoiceNo;
            this.dateStr = dateStr;
        }
    }

    private HeaderIndices findHeaderIndices(Sheet sheet, DataFormatter dataFormatter) {
        int maxScan = Math.min(10, sheet.getLastRowNum());
        for (int r = 0; r <= maxScan; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            List<String> headers = new ArrayList<>();
            for (int c = 0; c < row.getLastCellNum(); c++) {
                headers.add(dataFormatter.formatCellValue(row.getCell(c)).trim());
            }

            HeaderIndices h = findHeaderIndicesFromList(headers);
            if (h.stateCol != -1 && h.taxableCol != -1) {
                h.headerRow = r;
                return h;
            }
        }
        throw new GstConversionException("Could not identify required column headers in Excel report.");
    }

    private HeaderIndices findHeaderIndicesFromList(List<String> headers) {
        HeaderIndices h = new HeaderIndices();
        for (int i = 0; i < headers.size(); i++) {
            String norm = normalizeHeader(headers.get(i));
            if (h.stateCol == -1 && isStateHeader(norm)) h.stateCol = i;
            else if (h.rateCol == -1 && isRateHeader(norm)) h.rateCol = i;
            else if (h.taxableCol == -1 && isTaxableHeader(norm)) h.taxableCol = i;
            else if (h.taxCol == -1 && isTaxHeader(norm)) h.taxCol = i;
            else if (h.hsnCol == -1 && norm.contains("hsn")) h.hsnCol = i;
            else if (h.qtyCol == -1 && (norm.contains("quantity") || norm.equals("qty"))) h.qtyCol = i;
            else if (h.ecoCol == -1 && (norm.contains("eco") || norm.contains("tcs_gstin"))) h.ecoCol = i;
            else if (h.gstinCol == -1 && (norm.equals("gstin") || norm.equals("supplier_gstin") || norm.equals("seller_gstin") ||
                    (norm.contains("gstin") && !norm.contains("eco") && !norm.contains("tcs")))) h.gstinCol = i;
            else if (h.monthCol == -1 && (norm.equals("month_number") || norm.equals("month") || norm.contains("month_num"))) h.monthCol = i;
            else if (h.yearCol == -1 && (norm.equals("financial_year") || norm.equals("year"))) h.yearCol = i;
        }
        return h;
    }

    private String normalizeHeader(String header) {
        if (header == null) return "";
        return header.trim().toLowerCase().replaceAll("[^a-z0-9]", "_").replaceAll("_+", "_");
    }

    private boolean isStateHeader(String h) {
        return h.contains("state") || h.contains("pos") || h.contains("place_of_supply");
    }

    private boolean isRateHeader(String h) {
        return h.contains("gst_rate") || h.contains("tax_rate") || h.equals("rate") || h.contains("gst_percent");
    }

    private boolean isTaxableHeader(String h) {
        return h.contains("taxable") || h.contains("sale_value") || h.contains("taxable_amount");
    }

    private boolean isTaxHeader(String h) {
        return h.equals("tax_amount") || h.equals("total_tax_amount") || h.equals("gst_amount") || h.equals("tax");
    }

    private Number formatRate(double rate) {
        if (rate == Math.floor(rate)) {
            return (int) rate;
        }
        return round(rate, 2);
    }

    private double round(double value, int places) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return BigDecimal.valueOf(value).setScale(places, RoundingMode.HALF_UP).doubleValue();
    }

    private boolean isRowEmpty(Row row) {
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) return false;
        }
        return true;
    }

    private String getCellStringValue(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.FORMULA && evaluator != null) {
            CellValue cv = evaluator.evaluate(cell);
            if (cv != null && cv.getCellType() == CellType.STRING) return cv.getStringValue().trim();
        }
        return formatter.formatCellValue(cell).trim();
    }

    private Double getCellDoubleValue(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) return cell.getNumericCellValue();
        if (cell.getCellType() == CellType.FORMULA && evaluator != null) {
            CellValue cv = evaluator.evaluate(cell);
            if (cv != null && cv.getCellType() == CellType.NUMERIC) return cv.getNumberValue();
        }
        if (cell.getCellType() == CellType.STRING) {
            return parseDoubleSafe(cell.getStringCellValue(), null);
        }
        return null;
    }

    private Double parseRateValue(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) {
            double v = cell.getNumericCellValue();
            String df = cell.getCellStyle() != null ? cell.getCellStyle().getDataFormatString() : "";
            if (df != null && df.contains("%")) return v * 100.0;
            if (v > 0.0 && v <= 0.28 && (v == 0.05 || v == 0.12 || v == 0.18 || v == 0.28)) return v * 100.0;
            return v;
        }
        return parseRateString(getCellStringValue(cell, formatter, evaluator));
    }

    private Double parseRateString(String str) {
        if (str == null || str.trim().isEmpty()) return null;
        String clean = str.replace("%", "").trim();
        try {
            double val = Double.parseDouble(clean);
            if (val > 0.0 && val <= 0.28 && (val == 0.05 || val == 0.12 || val == 0.18 || val == 0.28)) {
                return val * 100.0;
            }
            return val;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private double parseDoubleSafe(String str, Double defaultVal) {
        if (str == null || str.trim().isEmpty()) return defaultVal != null ? defaultVal : 0.0;
        try {
            return Double.parseDouble(str.replace(",", "").replace("₹", "").trim());
        } catch (Exception e) {
            return defaultVal != null ? defaultVal : 0.0;
        }
    }

    private List<String> parseCsvLine(String line) {
        List<String> list = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                list.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        list.add(sb.toString().trim());
        return list;
    }

    private String getColValue(List<String> cols, int idx) {
        if (idx >= 0 && idx < cols.size()) {
            return cols.get(idx);
        }
        return null;
    }

    private static class HeaderIndices {
        int headerRow = 0;
        int stateCol = -1;
        int rateCol = -1;
        int taxableCol = -1;
        int taxCol = -1;
        int hsnCol = -1;
        int qtyCol = -1;
        int ecoCol = -1;
        int gstinCol = -1;
        int monthCol = -1;
        int yearCol = -1;
    }

    private static class ValueAccumulator {
        double taxableValue = 0.0;
        double taxAmount = 0.0;
        double camt = 0.0;
        double samt = 0.0;
    }

    private static class HsnAccumulator {
        double quantity = 0.0;
        double taxableValue = 0.0;
        double iamt = 0.0;
        double camt = 0.0;
        double samt = 0.0;
    }

    private static class SupecoAccumulator {
        double suppval = 0.0;
        double igst = 0.0;
        double cgst = 0.0;
        double sgst = 0.0;
    }

    public static class PosRateKey {
        private final String pos;
        private final double rate;

        public PosRateKey(String pos, double rate) {
            this.pos = pos;
            this.rate = rate;
        }

        public String getPos() {
            return pos;
        }

        public double getRate() {
            return rate;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PosRateKey that = (PosRateKey) o;
            return Double.compare(that.rate, rate) == 0 && Objects.equals(pos, that.pos);
        }

        @Override
        public int hashCode() {
            return Objects.hash(pos, rate);
        }
    }

    public static class HsnRateKey {
        private final String hsn;
        private final double rate;

        public HsnRateKey(String hsn, double rate) {
            this.hsn = hsn;
            this.rate = rate;
        }

        public String getHsn() {
            return hsn;
        }

        public double getRate() {
            return rate;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            HsnRateKey that = (HsnRateKey) o;
            return Double.compare(that.rate, rate) == 0 && Objects.equals(hsn, that.hsn);
        }

        @Override
        public int hashCode() {
            return Objects.hash(hsn, rate);
        }
    }
}
