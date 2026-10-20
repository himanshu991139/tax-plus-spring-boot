package com.meesho.gst.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CompanyDto {
    @NotBlank(message = "Company name is required")
    @Size(max = 100, message = "Company name must be at most 100 characters")
    private String companyName;

    // GSTIN format: 15 alphanumeric characters (simplified regex)
    @NotBlank(message = "GSTIN is required")
    @Pattern(regexp = "[0-9A-Z]{15}", message = "GSTIN must be 15 alphanumeric characters in uppercase")
    private String gstin;

    // Getters and Setters
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getGstin() { return gstin; }
    public void setGstin(String gstin) { this.gstin = gstin; }
}
