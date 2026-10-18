package com.meesho.gst.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI (Swagger UI) Configuration.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI meeshoGstOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Meesho to GSTR-1 JSON Converter API")
                        .description("Production-ready utility service to parse Meesho e-commerce TCS sales and return Excel reports " +
                                "and convert them into official GSTR-1 Table 7 (B2CS) JSON files ready for GST portal upload.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("GST Automation Team")
                                .email("support@example.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")));
    }
}
