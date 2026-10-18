package com.meesho.gst.controller;

import com.meesho.gst.dto.B2csItem;
import com.meesho.gst.dto.Gstr1Payload;
import com.meesho.gst.service.MeeshoGstr1Service;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import com.meesho.gst.util.JwtUtil;
import com.meesho.gst.service.UserService;
import com.meesho.gst.config.OAuth2AuthenticationSuccessHandler;

@WebMvcTest(GstConversionController.class)
@AutoConfigureMockMvc(addFilters = false)
class GstConversionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MeeshoGstr1Service meeshoGstr1Service;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UserService userService;

    @MockBean
    private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;

    @Test
    @DisplayName("POST /api/v1/gst/convert-meesho returns 200 and downloadable GSTR-1 JSON file attachment")
    void testConvertMeeshoSuccess() throws Exception {
        MockMultipartFile tcsSales = new MockMultipartFile(
                "tcs_sales", "tcs_sales.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "dummy content".getBytes()
        );

        String sampleJson = """
                {
                  "gstin": "29AABCU9603R1ZX",
                  "fp": "072026",
                  "gfp": "072026",
                  "b2cs": [
                    {
                      "sply_ty": "INTER",
                      "pos": "27",
                      "typ": "OE",
                      "rt": 18.0,
                      "txval": 5000.0,
                      "iamt": 900.0,
                      "camt": 0.0,
                      "samt": 0.0,
                      "csamt": 0.0
                    }
                  ]
                }
                """;

        when(meeshoGstr1Service.convertToGstr1JsonString(
                any(), any(), any(),
                eq("29AABCU9603R1ZX"), eq("072026"), eq("29")
        )).thenReturn(sampleJson);

        mockMvc.perform(multipart("/api/v1/gst/convert-meesho")
                        .file(tcsSales)
                        .param("gstin", "29AABCU9603R1ZX")
                        .param("period", "072026")
                        .param("sellerStateCode", "29"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=GSTR1_Meesho_072026.json"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.gstin").value("29AABCU9603R1ZX"))
                .andExpect(jsonPath("$.fp").value("072026"))
                .andExpect(jsonPath("$.b2cs[0].pos").value("27"))
                .andExpect(jsonPath("$.b2cs[0].sply_ty").value("INTER"))
                .andExpect(jsonPath("$.b2cs[0].iamt").value(900.0));
    }

    @Test
    @DisplayName("POST /api/v1/gst/convert-meesho returns 400 when required file is missing")
    void testConvertMeeshoMissingFileReturns400() throws Exception {
        mockMvc.perform(multipart("/api/v1/gst/convert-meesho")
                        .param("gstin", "29AABCU9603R1ZX")
                        .param("period", "072026")
                        .param("sellerStateCode", "29"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("POST /api/v1/gst/convert-meesho returns 400 when required parameter is missing")
    void testConvertMeeshoMissingParamReturns400() throws Exception {
        MockMultipartFile tcsSales = new MockMultipartFile(
                "tcs_sales", "tcs_sales.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "dummy content".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/gst/convert-meesho")
                        .file(tcsSales)
                        .param("period", "072026")
                        .param("sellerStateCode", "29"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Required parameter 'gstin' is missing"));
    }
}
