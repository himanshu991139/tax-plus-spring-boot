package com.meesho.gst.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Line item for Table 12: HSN Summary of Outward Supplies (B2C).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "num",
        "hsn_sc",
        "user_desc",
        "uqc",
        "qty",
        "rt",
        "txval",
        "iamt",
        "samt",
        "camt",
        "csamt"
})
public class HsnItem {

    @JsonProperty("num")
    private Integer num;

    @JsonProperty("hsn_sc")
    private String hsnSc;

    @JsonProperty("user_desc")
    private String userDesc = "0";

    @JsonProperty("uqc")
    private String uqc = "PCS";

    @JsonProperty("qty")
    private Number qty;

    @JsonProperty("rt")
    private Number rt;

    @JsonProperty("txval")
    private Double txval;

    @JsonProperty("iamt")
    private Double iamt = 0.0;

    @JsonProperty("samt")
    private Double samt = 0.0;

    @JsonProperty("camt")
    private Double camt = 0.0;

    @JsonProperty("csamt")
    private Double csamt = 0.0;

    public HsnItem() {
    }

    public HsnItem(Integer num, String hsnSc, String userDesc, String uqc, Number qty,
                   Number rt, Double txval, Double iamt, Double samt, Double camt, Double csamt) {
        this.num = num;
        this.hsnSc = hsnSc;
        this.userDesc = userDesc != null ? userDesc : "0";
        this.uqc = uqc != null ? uqc : "PCS";
        this.qty = qty;
        this.rt = rt;
        this.txval = txval;
        this.iamt = iamt != null ? iamt : 0.0;
        this.samt = samt != null ? samt : 0.0;
        this.camt = camt != null ? camt : 0.0;
        this.csamt = csamt != null ? csamt : 0.0;
    }

    public Integer getNum() {
        return num;
    }

    public void setNum(Integer num) {
        this.num = num;
    }

    public String getHsnSc() {
        return hsnSc;
    }

    public void setHsnSc(String hsnSc) {
        this.hsnSc = hsnSc;
    }

    public String getUserDesc() {
        return userDesc;
    }

    public void setUserDesc(String userDesc) {
        this.userDesc = userDesc;
    }

    public String getUqc() {
        return uqc;
    }

    public void setUqc(String uqc) {
        this.uqc = uqc;
    }

    public Number getQty() {
        return qty;
    }

    public void setQty(Number qty) {
        this.qty = qty;
    }

    public Number getRt() {
        return rt;
    }

    public void setRt(Number rt) {
        this.rt = rt;
    }

    public Double getTxval() {
        return txval;
    }

    public void setTxval(Double txval) {
        this.txval = txval;
    }

    public Double getIamt() {
        return iamt;
    }

    public void setIamt(Double iamt) {
        this.iamt = iamt;
    }

    public Double getSamt() {
        return samt;
    }

    public void setSamt(Double samt) {
        this.samt = samt;
    }

    public Double getCamt() {
        return camt;
    }

    public void setCamt(Double camt) {
        this.camt = camt;
    }

    public Double getCsamt() {
        return csamt;
    }

    public void setCsamt(Double csamt) {
        this.csamt = csamt;
    }
}
