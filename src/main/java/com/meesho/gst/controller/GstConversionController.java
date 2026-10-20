package com.meesho.gst.controller;

import com.meesho.gst.dto.CombineRequest;
import com.meesho.gst.dto.ErrorResponse;
import com.meesho.gst.dto.Gstr1Payload;
import com.meesho.gst.service.AmazonGstr1Service;
import com.meesho.gst.service.GstCombineService;
import com.meesho.gst.service.MeeshoGstr1Service;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

/**
 * REST Controller for converting e-commerce reports (Meesho, Amazon, or Combined)
 * into official GSTR-1 JSON. All endpoints require authentication.
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/gst")
@Tag(name = "GST Conversion", description = "Endpoints to convert e-commerce sales reports into GSTR-1 JSON returns")
public class GstConversionController {

    private static final Logger log = LoggerFactory.getLogger(GstConversionController.class);

    private final MeeshoGstr1Service meeshoGstr1Service;
    private final AmazonGstr1Service amazonGstr1Service;
    private final GstCombineService gstCombineService;

    public GstConversionController(MeeshoGstr1Service meeshoGstr1Service,
                                   AmazonGstr1Service amazonGstr1Service,
                                   GstCombineService gstCombineService) {
        this.meeshoGstr1Service = meeshoGstr1Service;
        this.amazonGstr1Service = amazonGstr1Service;
        this.gstCombineService = gstCombineService;
    }

    /**
     * Converts Meesho forward sales, return, and tax invoice Excel reports into a valid GSTR-1 JSON file attachment.
     */
    @PostMapping(
            value = "/convert-meesho",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Convert Meesho Excel reports into GSTR-1 Table 7 (B2CS) JSON",
            description = "Parses Meesho TCS Sales report (and optional TCS Sales Return report), aggregates " +
                    "taxable values and taxes by State/POS and Tax Rate with netting, applies intra/inter-state " +
                    "tax segregation, and returns an official GSTR-1 JSON file ready for filing on the GST portal."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successful conversion. Returns downloadable GSTR-1 JSON file attachment.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(type = "string", format = "binary")
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation or processing failure. Returns structured error details.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    public ResponseEntity<byte[]> convertMeesho(
            @Parameter(description = "Meesho TCS Sales Excel report (.xlsx) - Forward Orders", required = true)
            @RequestParam("tcs_sales") MultipartFile tcsSales,

            @Parameter(description = "Meesho TCS Sales Return Excel report (.xlsx) - Returns/RTO", required = false)
            @RequestParam(value = "tcs_sales_return", required = false) MultipartFile tcsSalesReturn,

            @Parameter(description = "Meesho Tax Invoice Details Excel report (.xlsx) - Document Series", required = false)
            @RequestParam(value = "tax_invoice_details", required = false) MultipartFile taxInvoiceDetails,

            @Parameter(description = "Seller GSTIN (15-character alphanumeric, e.g. 29AABCU9603R1ZX)", required = true, example = "29AABCU9603R1ZX")
            @RequestParam("gstin") String gstin,

            @Parameter(description = "Filing Period in MMYYYY format (e.g. 072026)", required = true, example = "072026")
            @RequestParam("period") String period,

            @Parameter(description = "Seller 2-digit GST State Code (e.g. 29 or 27)", required = true, example = "29")
            @RequestParam("sellerStateCode") String sellerStateCode
    ) {
        log.info("Received convert-meesho request for GSTIN: {}, Period: {}, SellerStateCode: {}",
                gstin, period, sellerStateCode);

        String jsonContent = meeshoGstr1Service.convertToGstr1JsonString(
                tcsSales,
                tcsSalesReturn,
                taxInvoiceDetails,
                gstin,
                period,
                sellerStateCode
        );

        byte[] jsonBytes = jsonContent.getBytes(StandardCharsets.UTF_8);
        String filename = "GSTR1_Meesho_" + period.trim() + ".json";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setContentLength(jsonBytes.length);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename);

        return new ResponseEntity<>(jsonBytes, headers, HttpStatus.OK);
    }

    /**
     * Converts Amazon MTR (Merchant Tax Report) B2C report into a valid GSTR-1 JSON file attachment.
     */
    @PostMapping(
            value = "/convert-amazon",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Convert Amazon MTR report into GSTR-1 JSON",
            description = "Parses Amazon Merchant Tax Report (MTR) B2C file (.csv or .xlsx), aggregates taxable values " +
                    "and taxes by Place of Supply (POS) and Tax Rate, segregates intra/inter-state supply, and returns " +
                    "an official GSTR-1 JSON file ready for filing on the GST portal."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successful conversion. Returns downloadable GSTR-1 JSON file attachment.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(type = "string", format = "binary")
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation or processing failure. Returns structured error details.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    public ResponseEntity<byte[]> convertAmazon(
            @Parameter(description = "Amazon Merchant Tax Report (MTR) file (.csv or .xlsx)", required = false)
            @RequestParam(value = "mtr_file", required = false) MultipartFile mtrFile,

            @Parameter(description = "Amazon MTR alias parameter name 'mtr'", required = false)
            @RequestParam(value = "mtr", required = false) MultipartFile mtr,

            @Parameter(description = "Seller GSTIN (15-character alphanumeric, e.g. 09CDQPT2928D1Z3)", required = true, example = "09CDQPT2928D1Z3")
            @RequestParam("gstin") String gstin,

            @Parameter(description = "Filing Period in MMYYYY format (e.g. 092026)", required = true, example = "092026")
            @RequestParam("period") String period,

            @Parameter(description = "Seller 2-digit GST State Code (e.g. 09)", required = true, example = "09")
            @RequestParam("sellerStateCode") String sellerStateCode
    ) {
        MultipartFile effectiveFile = mtrFile != null ? mtrFile : mtr;
        if (effectiveFile == null || effectiveFile.isEmpty()) {
            throw new IllegalArgumentException("Amazon MTR file (parameter 'mtr_file' or 'mtr') is required.");
        }

        log.info("Received convert-amazon request for GSTIN: {}, Period: {}, SellerStateCode: {}, File: {}",
                gstin, period, sellerStateCode, effectiveFile.getOriginalFilename());

        String jsonContent = amazonGstr1Service.convertToGstr1JsonString(
                effectiveFile,
                gstin,
                period,
                sellerStateCode
        );

        byte[] jsonBytes = jsonContent.getBytes(StandardCharsets.UTF_8);
        String filename = "GSTR1_Amazon_" + period.trim() + ".json";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setContentLength(jsonBytes.length);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename);

        return new ResponseEntity<>(jsonBytes, headers, HttpStatus.OK);
    }

    /**
     * Combines Meesho and Amazon GSTR-1 JSON payloads into a single unified GSTR-1 return.
     */
    @PostMapping(
            value = "/combine",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Combine Meesho and Amazon GSTR-1 returns into a unified GSTR-1",
            description = "Accepts Meesho and/or Amazon GSTR-1 payloads in request body, combines Table 7 (B2CS), " +
                    "Table 12 (HSN), Table 14 (SUPECO), and Table 13 (DOC ISSUE), and returns unified GSTR-1."
    )
    public ResponseEntity<byte[]> combineGstr1(@RequestBody CombineRequest request) {
        log.info("Received request to combine returns");

        Gstr1Payload combined = gstCombineService.combine(request.getMeesho(), request.getAmazon());
        if (combined == null) {
            throw new IllegalArgumentException("At least one platform return (meesho or amazon) must be provided in request body.");
        }

        String jsonContent = gstCombineService.serializePayload(combined);
        byte[] jsonBytes = jsonContent.getBytes(StandardCharsets.UTF_8);
        String period = combined.getFp() != null ? combined.getFp() : "period";
        String filename = "GSTR1_Combined_" + period + ".json";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setContentLength(jsonBytes.length);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename);

        return new ResponseEntity<>(jsonBytes, headers, HttpStatus.OK);
    }

    /**
     * Multipart endpoint to convert and combine Meesho, Amazon, or both directly from uploaded files.
     */
    @PostMapping(
            value = "/convert-combined",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Upload and combine Meesho and Amazon files in a single request",
            description = "Upload Meesho reports (tcs_sales, tcs_sales_return, tax_invoice_details) and/or Amazon report " +
                    "(mtr_file). Generates GSTR-1 for either or combined if both provided."
    )
    public ResponseEntity<byte[]> convertCombined(
            @RequestParam(value = "tcs_sales", required = false) MultipartFile tcsSales,
            @RequestParam(value = "tcs_sales_return", required = false) MultipartFile tcsSalesReturn,
            @RequestParam(value = "tax_invoice_details", required = false) MultipartFile taxInvoiceDetails,
            @RequestParam(value = "mtr_file", required = false) MultipartFile mtrFile,
            @RequestParam(value = "mtr", required = false) MultipartFile mtr,
            @RequestParam("gstin") String gstin,
            @RequestParam("period") String period,
            @RequestParam("sellerStateCode") String sellerStateCode
    ) {
        MultipartFile effectiveMtr = mtrFile != null ? mtrFile : mtr;

        boolean hasMeesho = tcsSales != null && !tcsSales.isEmpty();
        boolean hasAmazon = effectiveMtr != null && !effectiveMtr.isEmpty();

        if (!hasMeesho && !hasAmazon) {
            throw new IllegalArgumentException("Either Meesho report (tcs_sales) or Amazon report (mtr_file) must be uploaded.");
        }

        Gstr1Payload meeshoPayload = null;
        if (hasMeesho) {
            meeshoPayload = meeshoGstr1Service.processReports(tcsSales, tcsSalesReturn, taxInvoiceDetails, gstin, period, sellerStateCode);
        }

        Gstr1Payload amazonPayload = null;
        if (hasAmazon) {
            amazonPayload = amazonGstr1Service.processReport(effectiveMtr, gstin, period, sellerStateCode);
        }

        Gstr1Payload result = gstCombineService.combine(meeshoPayload, amazonPayload);
        String jsonContent = gstCombineService.serializePayload(result);
        byte[] jsonBytes = jsonContent.getBytes(StandardCharsets.UTF_8);

        String prefix = hasMeesho && hasAmazon ? "Combined" : (hasMeesho ? "Meesho" : "Amazon");
        String filename = "GSTR1_" + prefix + "_" + period.trim() + ".json";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setContentLength(jsonBytes.length);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename);

        return new ResponseEntity<>(jsonBytes, headers, HttpStatus.OK);
    }
}
