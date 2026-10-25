package com.meesho.gst.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.meesho.gst.dto.*;
import com.meesho.gst.exception.GstConversionException;
import com.meesho.gst.service.MeeshoGstr1Service.PosRateKey;
import com.meesho.gst.service.MeeshoGstr1Service.HsnRateKey;
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
 * Service to process Amazon Merchant Tax Report (MTR) B2C reports (CSV or Excel)
 * with strict validations and generation of official GSTR-1 JSON matching GST portal specification:
 * - Table 7: B2CS
 * - Table 12: HSN Summary (hsn_b2c)
 * - Table 14: E-Commerce Operator supplies (supeco / clttx)
 * - Table 13: Document summary (doc_issue / doc_det)
 */
@Service
public class AmazonGstr1Service {

    private static final Logger log = LoggerFactory.getLogger(AmazonGstr1Service.class);

    private static final Pattern GSTIN_PATTERN =
            Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$");
    private static final Pattern PERIOD_PATTERN =
            Pattern.compile("^(0[1-9]|1[0-2])[2-9][0-9]{3}$");
    private static final Pattern DATE_PATTERN =
            Pattern.compile("([0-9]{4})-([0-9]{2})-[0-9]{2}");

    private final ObjectMapper objectMapper;

    public AmazonGstr1Service() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    public AmazonGstr1Service(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Converts uploaded Amazon MTR report into GSTR-1 JSON string matching official schema.
     */
    public String convertToGstr1JsonString(MultipartFile mtrFile,
                                           String gstin,
                                           String period,
                                           String sellerStateCode) {
        Gstr1Payload payload = processReport(mtrFile, gstin, period, sellerStateCode);
        return serializePayload(payload);
    }

    /**
     * Converts uploaded Amazon MTR report into GSTR-1 JSON string for Quarterly returns (QRMP scheme).
     */
    public String convertToGstr1JsonStringQuarterly(MultipartFile mtrFile,
                                                    String gstin,
                                                    String period,
                                                    String quarter,
                                                    String sellerStateCode) {
        Gstr1Payload payload = processReportQuarterly(mtrFile, gstin, period, quarter, sellerStateCode);
        return serializePayload(payload);
    }

    /**
     * Core processing logic handling validations, Table 7 (B2CS), Table 12 (HSN),
     * Table 14 (supeco), and Table 13 (doc_issue) for Amazon MTR data.
     */
    public Gstr1Payload processReport(MultipartFile mtrFile,
                                      String gstin,
                                      String period,
                                      String sellerStateCode) {
        validateInputs(gstin, period, sellerStateCode, mtrFile);

        String normalizedGstin = gstin.trim().toUpperCase();
        String normalizedPeriod = period.trim();
        String normalizedSellerState = StateCodeUtil.resolveStateCode(sellerStateCode);

        int expectedMonth = Integer.parseInt(normalizedPeriod.substring(0, 2));
        int expectedYear = Integer.parseInt(normalizedPeriod.substring(2, 6));

        return executeProcessing(mtrFile, normalizedGstin, normalizedPeriod, normalizedSellerState, expectedMonth, expectedYear);
    }

    /**
     * Quarterly processing for Amazon MTR data.
     */
    public Gstr1Payload processReportQuarterly(MultipartFile mtrFile,
                                              String gstin,
                                              String period,
                                              String quarter,
                                              String sellerStateCode) {
        MeeshoGstr1Service.QuarterInfo qi = MeeshoGstr1Service.resolveQuarterInfo(period, quarter);
        validateInputs(gstin, qi.fp, sellerStateCode, mtrFile);

        String normalizedGstin = gstin.trim().toUpperCase();
        String normalizedPeriod = qi.fp;
        String normalizedSellerState = StateCodeUtil.resolveStateCode(sellerStateCode);

        int expectedYear = qi.year;
        int expectedMonth = Integer.parseInt(qi.fp.substring(0, 2));

        return executeProcessing(mtrFile, normalizedGstin, normalizedPeriod, normalizedSellerState, expectedMonth, expectedYear);
    }

    private Gstr1Payload executeProcessing(MultipartFile mtrFile,
                                          String normalizedGstin,
                                          String normalizedPeriod,
                                          String normalizedSellerState,
                                          int expectedMonth,
                                          int expectedYear) {
        // Parse MTR rows (either CSV or Excel)
        List<AmazonMtrRecord> records = parseMtrRecords(mtrFile, normalizedGstin, expectedMonth, expectedYear, normalizedPeriod);

        // Aggregate by POS and Tax Rate
        Map<PosRateKey, AmazonAccumulator> posRateMap = new LinkedHashMap<>();
        List<String> invoiceNumbers = new ArrayList<>();
        int shipmentCount = 0;
        int refundCount = 0;

        for (AmazonMtrRecord rec : records) {
            if ("Cancel".equalsIgnoreCase(rec.transactionType)) {
                continue;
            }

            if ("Shipment".equalsIgnoreCase(rec.transactionType)) {
                shipmentCount++;
                if (rec.invoiceNumber != null && !rec.invoiceNumber.trim().isEmpty()) {
                    invoiceNumbers.add(rec.invoiceNumber.trim());
                }
            } else if ("Refund".equalsIgnoreCase(rec.transactionType)) {
                refundCount++;
                if (rec.invoiceNumber != null && !rec.invoiceNumber.trim().isEmpty()) {
                    invoiceNumbers.add(rec.invoiceNumber.trim());
                }
            }

            String pos = StateCodeUtil.resolveStateCode(rec.shipToState);
            double rate = rec.rate;
            PosRateKey key = new PosRateKey(pos, rate);

            AmazonAccumulator acc = posRateMap.computeIfAbsent(key, k -> new AmazonAccumulator(pos, rate, rec.hsn));

            // In Amazon MTR, refund txval is already negative, or we ensure sign consistency
            double effectiveTxval = rec.taxExclusiveGross;
            if ("Refund".equalsIgnoreCase(rec.transactionType) && effectiveTxval > 0) {
                effectiveTxval = -effectiveTxval;
            }

            double effectiveQty = rec.quantity;
            if ("Refund".equalsIgnoreCase(rec.transactionType)) {
                effectiveQty = -Math.abs(effectiveQty);
            }

            acc.taxableValue += effectiveTxval;
            acc.quantity += effectiveQty;
            acc.hsn = rec.hsn != null && !rec.hsn.trim().isEmpty() ? rec.hsn.trim() : acc.hsn;
        }

        // Filter positive states (netTxval > 0)
        List<B2csItem> b2csList = new ArrayList<>();
        Map<HsnRateKey, AmazonHsnAccumulator> hsnMap = new LinkedHashMap<>();

        double totalEcoTxval = 0.0;
        double totalEcoIgst = 0.0;
        double totalEcoCgst = 0.0;
        double totalEcoSgst = 0.0;

        List<PosRateKey> sortedKeys = new ArrayList<>(posRateMap.keySet());
        sortedKeys.sort(Comparator.comparing(PosRateKey::getPos));

        for (PosRateKey key : sortedKeys) {
            AmazonAccumulator acc = posRateMap.get(key);
            double netTxval = round(acc.taxableValue, 2);
            if (netTxval <= 0.0) {
                // In GST laws & portal Table 7, non-positive net supply for a state is excluded
                continue;
            }

            String pos = key.getPos();
            double rate = key.getRate();
            boolean isIntra = pos.equals(normalizedSellerState);

            B2csItem b2cItem = new B2csItem();
            b2cItem.setPos(pos);
            b2cItem.setRt(formatRate(rate));
            b2cItem.setTyp("OE");
            b2cItem.setTxval(netTxval);
            b2cItem.setCsamt(0);

            double itemTax = round(netTxval * (rate / 100.0), 2);

            if (isIntra) {
                b2cItem.setSply_ty("INTRA");
                double halfTax = round(netTxval * (rate / 200.0), 2);
                b2cItem.setCamt(halfTax);
                b2cItem.setSamt(halfTax);
                b2cItem.setIamt(null);
                totalEcoCgst += halfTax;
                totalEcoSgst += halfTax;
            } else {
                b2cItem.setSply_ty("INTER");
                b2cItem.setIamt(itemTax);
                b2cItem.setCamt(null);
                b2cItem.setSamt(null);
                totalEcoIgst += itemTax;
            }

            totalEcoTxval += netTxval;
            b2csList.add(b2cItem);

            // Aggregate to HSN for positive states
            String hsnCode = acc.hsn != null && !acc.hsn.trim().isEmpty() ? acc.hsn.trim() : "3926";
            HsnRateKey hsnKey = new HsnRateKey(hsnCode, rate);
            AmazonHsnAccumulator hsnAcc = hsnMap.computeIfAbsent(hsnKey, k -> new AmazonHsnAccumulator(hsnCode, rate));
            hsnAcc.quantity += acc.quantity;
            hsnAcc.taxableValue += netTxval;
            if (isIntra) {
                hsnAcc.camt += b2cItem.getCamt() != null ? b2cItem.getCamt().doubleValue() : 0.0;
                hsnAcc.samt += b2cItem.getSamt() != null ? b2cItem.getSamt().doubleValue() : 0.0;
            } else {
                hsnAcc.iamt += b2cItem.getIamt() != null ? b2cItem.getIamt().doubleValue() : 0.0;
            }
        }

        // Table 12 HSN Summary
        List<HsnItem> hsnList = new ArrayList<>();
        int hsnSeq = 1;
        for (Map.Entry<HsnRateKey, AmazonHsnAccumulator> entry : hsnMap.entrySet()) {
            HsnRateKey key = entry.getKey();
            AmazonHsnAccumulator hAcc = entry.getValue();

            HsnItem item = new HsnItem();
            item.setNum(hsnSeq++);
            item.setHsnSc(key.getHsn());
            item.setUserDesc("0");
            item.setUqc("PCS");
            item.setQty((int) Math.round(hAcc.quantity));
            item.setRt(formatRate(key.getRate()));
            item.setTxval(round(hAcc.taxableValue, 2));
            item.setIamt(round(hAcc.iamt, 2));
            item.setSamt(round(hAcc.samt, 2));
            item.setCamt(round(hAcc.camt, 2));
            item.setCsamt(0.0);
            hsnList.add(item);
        }

        // Table 14: E-Commerce Operator (Amazon)
        // Amazon TCS GSTIN for seller state (e.g. 09AAICA3918J1CR for UP)
        String amazonEtin = normalizedSellerState + "AAICA3918J1CR";
        List<SupecoItem> supecoList = new ArrayList<>();
        if (totalEcoTxval > 0) {
            SupecoItem supeco = new SupecoItem();
            supeco.setEtin(amazonEtin);
            supeco.setSuppval(round(totalEcoTxval, 2));
            supeco.setIgst(round(totalEcoIgst, 2));
            supeco.setCgst(round(totalEcoCgst, 2));
            supeco.setSgst(round(totalEcoSgst, 2));
            supeco.setCess(0.0);
            supeco.setFlag("N");
            supecoList.add(supeco);
        }

        // Table 13: Document series
        DocIssueContainer docIssueContainer = null;
        if (!invoiceNumbers.isEmpty()) {
            invoiceNumbers.sort((a, b) -> {
                int numA = extractLeadingNumber(a);
                int numB = extractLeadingNumber(b);
                return Integer.compare(numA, numB);
            });

            String fromInv = invoiceNumbers.get(0);
            String toInv = invoiceNumbers.get(invoiceNumbers.size() - 1);
            int totnum = shipmentCount + refundCount;
            int cancel = refundCount;
            int netIssue = shipmentCount;

            DocItem doc = new DocItem(1, fromInv, toInv, totnum, cancel, netIssue);
            DocDetItem docDet = new DocDetItem(1, "Invoices for outward supply", Collections.singletonList(doc));
            docIssueContainer = new DocIssueContainer(Collections.singletonList(docDet));
        }

        // Assemble Root Payload
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

        log.info("Successfully constructed Amazon GSTR-1 JSON with {} B2CS items, {} HSN items, {} ECO items",
                b2csList.size(), hsnList.size(), supecoList.size());

        return payload;
    }

    public String serializePayload(Gstr1Payload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new GstConversionException("Failed to serialize Amazon GSTR-1 payload to JSON", e);
        }
    }

    private void validateInputs(String gstin, String period, String sellerStateCode, MultipartFile mtrFile) {
        if (gstin == null || gstin.trim().isEmpty()) {
            throw new IllegalArgumentException("GSTIN parameter is required and cannot be empty.");
        }
        String cleanGstin = gstin.trim().toUpperCase();
        if (!GSTIN_PATTERN.matcher(cleanGstin).matches()) {
            throw new IllegalArgumentException("Invalid GSTIN format: '" + gstin + "'. Must be a 15-character alphanumeric GSTIN.");
        }

        if (period == null || period.trim().isEmpty()) {
            throw new IllegalArgumentException("Filing period parameter is required and cannot be empty.");
        }
        String cleanPeriod = period.trim();
        if (!PERIOD_PATTERN.matcher(cleanPeriod).matches()) {
            throw new IllegalArgumentException("Invalid return filing period format: '" + period + "'. Expected MMYYYY (e.g. 092026).");
        }

        if (sellerStateCode == null || sellerStateCode.trim().isEmpty()) {
            throw new IllegalArgumentException("sellerStateCode parameter is required.");
        }
        try {
            StateCodeUtil.resolveStateCode(sellerStateCode);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid sellerStateCode: " + e.getMessage());
        }

        if (mtrFile == null || mtrFile.isEmpty()) {
            throw new IllegalArgumentException("Amazon MTR file is required and cannot be empty.");
        }

        String filename = mtrFile.getOriginalFilename();
        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("Uploaded Amazon file has no filename.");
        }
        String clean = filename.trim().toLowerCase();
        if (!clean.endsWith(".csv") && !clean.endsWith(".xlsx") && !clean.endsWith(".xls")) {
            throw new IllegalArgumentException("Invalid file extension for Amazon MTR: '" + filename + "'. Supported formats are .csv, .xlsx, and .xls.");
        }
    }

    private List<AmazonMtrRecord> parseMtrRecords(MultipartFile file,
                                                  String expectedGstin,
                                                  int expectedMonth,
                                                  int expectedYear,
                                                  String period) {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        if (filename.endsWith(".csv")) {
            return parseCsvMtr(file, expectedGstin, expectedMonth, expectedYear, period);
        } else {
            return parseExcelMtr(file, expectedGstin, expectedMonth, expectedYear, period);
        }
    }

    private List<AmazonMtrRecord> parseCsvMtr(MultipartFile file,
                                              String expectedGstin,
                                              int expectedMonth,
                                              int expectedYear,
                                              String period) {
        List<AmazonMtrRecord> list = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = null;
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    headerLine = line;
                    break;
                }
            }

            if (headerLine == null) {
                throw new GstConversionException("Amazon MTR CSV file is empty.");
            }

            List<String> headers = parseCsvLine(headerLine);
            AmazonHeaderIndices h = findHeaderIndices(headers);

            int rowIdx = 1;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                rowIdx++;
                List<String> values = parseCsvLine(line);

                // Check in-file GSTIN
                if (h.sellerGstinCol != -1 && h.sellerGstinCol < values.size()) {
                    String fileGstin = values.get(h.sellerGstinCol).trim().toUpperCase();
                    if (!fileGstin.isEmpty() && !fileGstin.equalsIgnoreCase(expectedGstin)) {
                        throw new IllegalArgumentException("GSTIN mismatch in Amazon MTR on row " + rowIdx +
                                ": file contains GSTIN '" + fileGstin + "', but requested GSTIN is '" + expectedGstin + "'.");
                    }
                }

                AmazonMtrRecord rec = buildRecord(values, h);
                if (rec != null) {
                    list.add(rec);
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new GstConversionException("Failed to parse Amazon MTR CSV report: " + e.getMessage(), e);
        }
        return list;
    }

    private List<AmazonMtrRecord> parseExcelMtr(MultipartFile file,
                                                String expectedGstin,
                                                int expectedMonth,
                                                int expectedYear,
                                                String period) {
        List<AmazonMtrRecord> list = new ArrayList<>();
        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            if (workbook.getNumberOfSheets() == 0) {
                throw new GstConversionException("Amazon MTR Excel file contains no worksheets.");
            }

            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            Row headerRow = null;
            int headerRowNum = -1;
            for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row != null && !isRowEmpty(row)) {
                    headerRow = row;
                    headerRowNum = r;
                    break;
                }
            }

            if (headerRow == null) {
                throw new GstConversionException("Amazon MTR Excel file contains no header row.");
            }

            List<String> headers = new ArrayList<>();
            for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                headers.add(getCellString(headerRow.getCell(c), formatter, evaluator));
            }

            AmazonHeaderIndices h = findHeaderIndices(headers);

            for (int r = headerRowNum + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowEmpty(row)) continue;

                List<String> values = new ArrayList<>();
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    values.add(getCellString(row.getCell(c), formatter, evaluator));
                }

                if (h.sellerGstinCol != -1 && h.sellerGstinCol < values.size()) {
                    String fileGstin = values.get(h.sellerGstinCol).trim().toUpperCase();
                    if (!fileGstin.isEmpty() && !fileGstin.equalsIgnoreCase(expectedGstin)) {
                        throw new IllegalArgumentException("GSTIN mismatch in Amazon MTR on row " + (r + 1) +
                                ": file contains GSTIN '" + fileGstin + "', but requested GSTIN is '" + expectedGstin + "'.");
                    }
                }

                AmazonMtrRecord rec = buildRecord(values, h);
                if (rec != null) {
                    list.add(rec);
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new GstConversionException("Failed to parse Amazon MTR Excel report: " + e.getMessage(), e);
        }
        return list;
    }

    private AmazonMtrRecord buildRecord(List<String> values, AmazonHeaderIndices h) {
        String txType = getVal(values, h.txTypeCol);
        if (txType == null || txType.trim().isEmpty()) {
            return null;
        }

        AmazonMtrRecord rec = new AmazonMtrRecord();
        rec.transactionType = txType.trim();
        rec.sellerGstin = getVal(values, h.sellerGstinCol);
        rec.invoiceNumber = getVal(values, h.invoiceNoCol);
        rec.invoiceDate = getVal(values, h.invoiceDateCol);
        rec.shipToState = getVal(values, h.shipToStateCol);
        rec.hsn = getVal(values, h.hsnCol);
        rec.quantity = parseDouble(getVal(values, h.quantityCol), 1.0);
        rec.taxExclusiveGross = parseDouble(getVal(values, h.taxExclusiveGrossCol), 0.0);
        rec.totalTaxAmount = parseDouble(getVal(values, h.totalTaxCol), 0.0);

        double igstRate = parseDouble(getVal(values, h.igstRateCol), 0.0);
        double cgstRate = parseDouble(getVal(values, h.cgstRateCol), 0.0);
        double sgstRate = parseDouble(getVal(values, h.sgstRateCol), 0.0);

        double ratePercent = (igstRate > 0 ? igstRate : (cgstRate + sgstRate));
        if (ratePercent <= 1.0 && ratePercent > 0) {
            // E.g. 0.18 -> 18.0
            ratePercent = ratePercent * 100.0;
        }
        rec.rate = Math.round(ratePercent);

        rec.cgstTax = parseDouble(getVal(values, h.cgstTaxCol), 0.0);
        rec.sgstTax = parseDouble(getVal(values, h.sgstTaxCol), 0.0);
        rec.igstTax = parseDouble(getVal(values, h.igstTaxCol), 0.0);
        rec.creditNoteNo = getVal(values, h.creditNoteNoCol);
        rec.creditNoteDate = getVal(values, h.creditNoteDateCol);

        if (rec.shipToState == null || rec.shipToState.trim().isEmpty()) {
            return null;
        }

        return rec;
    }

    private String getVal(List<String> values, int col) {
        if (col >= 0 && col < values.size()) {
            return values.get(col);
        }
        return null;
    }

    private double parseDouble(String str, double defaultVal) {
        if (str == null || str.trim().isEmpty()) return defaultVal;
        try {
            return Double.parseDouble(str.trim().replaceAll("[,₹]", ""));
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private AmazonHeaderIndices findHeaderIndices(List<String> headers) {
        AmazonHeaderIndices h = new AmazonHeaderIndices();
        for (int i = 0; i < headers.size(); i++) {
            String clean = headers.get(i).replace("\"", "").trim().toLowerCase();
            if (clean.equals("seller gstin")) h.sellerGstinCol = i;
            else if (clean.equals("invoice number")) h.invoiceNoCol = i;
            else if (clean.equals("invoice date")) h.invoiceDateCol = i;
            else if (clean.equals("transaction type")) h.txTypeCol = i;
            else if (clean.equals("quantity")) h.quantityCol = i;
            else if (clean.equals("hsn/sac") || clean.equals("hsn") || clean.equals("sac")) h.hsnCol = i;
            else if (clean.equals("ship to state") || clean.equals("destination state")) h.shipToStateCol = i;
            else if (clean.equals("tax exclusive gross") || clean.equals("taxable value")) h.taxExclusiveGrossCol = i;
            else if (clean.equals("total tax amount") || clean.equals("total tax")) h.totalTaxCol = i;
            else if (clean.equals("cgst rate")) h.cgstRateCol = i;
            else if (clean.equals("sgst rate")) h.sgstRateCol = i;
            else if (clean.equals("igst rate")) h.igstRateCol = i;
            else if (clean.equals("cgst tax")) h.cgstTaxCol = i;
            else if (clean.equals("sgst tax")) h.sgstTaxCol = i;
            else if (clean.equals("igst tax")) h.igstTaxCol = i;
            else if (clean.equals("credit note no") || clean.equals("credit note number")) h.creditNoteNoCol = i;
            else if (clean.equals("credit note date")) h.creditNoteDateCol = i;
        }

        if (h.shipToStateCol == -1 || h.taxExclusiveGrossCol == -1) {
            throw new GstConversionException("Amazon MTR report is missing required columns ('Ship To State', 'Tax Exclusive Gross').");
        }

        return h;
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        boolean insideQuote = false;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                insideQuote = !insideQuote;
            } else if (c == ',' && !insideQuote) {
                values.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        values.add(sb.toString().trim());
        return values;
    }

    private String getCellString(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) return "";
        try {
            return formatter.formatCellValue(cell, evaluator).trim();
        } catch (Exception e) {
            return cell.toString().trim();
        }
    }

    private boolean isRowEmpty(Row row) {
        if (row == null) return true;
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String val = cell.toString().trim();
                if (!val.isEmpty()) return false;
            }
        }
        return true;
    }

    private int extractLeadingNumber(String inv) {
        if (inv == null) return 0;
        String digits = inv.replaceAll("\\D", "");
        if (digits.isEmpty()) return 0;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private double round(double val, int decimals) {
        BigDecimal bd = BigDecimal.valueOf(val);
        bd = bd.setScale(decimals, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    private Number formatRate(double rate) {
        if (rate == Math.floor(rate)) {
            return (int) rate;
        }
        return round(rate, 2);
    }

    private static class AmazonHeaderIndices {
        int sellerGstinCol = -1;
        int invoiceNoCol = -1;
        int invoiceDateCol = -1;
        int txTypeCol = -1;
        int quantityCol = -1;
        int hsnCol = -1;
        int shipToStateCol = -1;
        int taxExclusiveGrossCol = -1;
        int totalTaxCol = -1;
        int cgstRateCol = -1;
        int sgstRateCol = -1;
        int igstRateCol = -1;
        int cgstTaxCol = -1;
        int sgstTaxCol = -1;
        int igstTaxCol = -1;
        int creditNoteNoCol = -1;
        int creditNoteDateCol = -1;
    }

    private static class AmazonMtrRecord {
        String transactionType;
        String sellerGstin;
        String invoiceNumber;
        String invoiceDate;
        String shipToState;
        String hsn;
        double quantity;
        double taxExclusiveGross;
        double totalTaxAmount;
        double rate;
        double cgstTax;
        double sgstTax;
        double igstTax;
        String creditNoteNo;
        String creditNoteDate;
    }

    private static class AmazonAccumulator {
        String pos;
        double rate;
        String hsn;
        double taxableValue = 0.0;
        double quantity = 0.0;

        AmazonAccumulator(String pos, double rate, String hsn) {
            this.pos = pos;
            this.rate = rate;
            this.hsn = hsn;
        }
    }

    private static class AmazonHsnAccumulator {
        String hsn;
        double rate;
        double quantity = 0.0;
        double taxableValue = 0.0;
        double iamt = 0.0;
        double camt = 0.0;
        double samt = 0.0;

        AmazonHsnAccumulator(String hsn, double rate) {
            this.hsn = hsn;
            this.rate = rate;
        }
    }
}
