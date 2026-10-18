package com.meesho.gst.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Line item for Table 14: Supplies made through E-Commerce Operator (ECO).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "etin",
        "suppval",
        "igst",
        "cgst",
        "sgst",
        "cess",
        "flag"
})
public class SupecoItem {

    @JsonProperty("etin")
    private String etin;

    @JsonProperty("suppval")
    private Double suppval;

    @JsonProperty("igst")
    private Double igst = 0.0;

    @JsonProperty("cgst")
    private Double cgst = 0.0;

    @JsonProperty("sgst")
    private Double sgst = 0.0;

    @JsonProperty("cess")
    private Double cess = 0.0;

    @JsonProperty("flag")
    private String flag = "N";

    public SupecoItem() {
    }

    public SupecoItem(String etin, Double suppval, Double igst, Double cgst, Double sgst, Double cess, String flag) {
        this.etin = etin;
        this.suppval = suppval;
        this.igst = igst != null ? igst : 0.0;
        this.cgst = cgst != null ? cgst : 0.0;
        this.sgst = sgst != null ? sgst : 0.0;
        this.cess = cess != null ? cess : 0.0;
        this.flag = flag != null ? flag : "N";
    }

    public String getEtin() {
        return etin;
    }

    public void setEtin(String etin) {
        this.etin = etin;
    }

    public Double getSuppval() {
        return suppval;
    }

    public void setSuppval(Double suppval) {
        this.suppval = suppval;
    }

    public Double getIgst() {
        return igst;
    }

    public void setIgst(Double igst) {
        this.igst = igst;
    }

    public Double getCgst() {
        return cgst;
    }

    public void setCgst(Double cgst) {
        this.cgst = cgst;
    }

    public Double getSgst() {
        return sgst;
    }

    public void setSgst(Double sgst) {
        this.sgst = sgst;
    }

    public Double getCess() {
        return cess;
    }

    public void setCess(Double cess) {
        this.cess = cess;
    }

    public String getFlag() {
        return flag;
    }

    public void setFlag(String flag) {
        this.flag = flag;
    }
}
