package com.meesho.gst.controller;

import com.meesho.gst.dto.ErrorResponse;
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
 * REST Controller exposing public endpoints for converting Meesho Excel reports into official GSTR-1 JSON.
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/gst")
@Tag(name = "GST Conversion", description = "Endpoints to convert e-commerce sales reports into GSTR-1 JSON returns")
public class GstConversionController {

    private static final Logger log = LoggerFactory.getLogger(GstConversionController.class);

    private final MeeshoGstr1Service meeshoGstr1Service;

    public GstConversionController(MeeshoGstr1Service meeshoGstr1Service) {
        this.meeshoGstr1Service = meeshoGstr1Service;
    }

    /**
     * Converts Meesho forward sales, return, and tax invoice Excel reports into a valid GSTR-1 JSON file attachment.
     *
     * @param tcsSales           Required forward orders report (tcs_sales.xlsx)
     * @param tcsSalesReturn     Optional returns and RTOs report (tcs_sales_return.xlsx)
     * @param taxInvoiceDetails  Optional document series report (tax_invoice_details.xlsx)
     * @param gstin              Seller's 15-character GSTIN
     * @param period             Filing return period in MMYYYY format (e.g. 072026)
     * @param sellerStateCode    Seller's 2-digit GST state code (e.g. 29 for Karnataka, 27 for Maharashtra)
     * @return Downloadable GSTR1_Meesho_<period>.json file attachment
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
}
