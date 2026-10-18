package com.meesho.gst.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * Represents a single line item in Table 7 (B2CS - Business to Consumer Small supplies)
 * of the official GSTR-1 JSON schema.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "sply_ty",
        "rt",
        "typ",
        "pos",
        "txval",
        "iamt",
        "camt",
        "samt",
        "csamt"
})
public class B2csItem {

    @JsonProperty("sply_ty")
    private String sply_ty;

    @JsonProperty("rt")
    private Number rt;

    @JsonProperty("typ")
    private String typ = "OE";

    @JsonProperty("pos")
    private String pos;

    @JsonProperty("txval")
    private Double txval;

    @JsonProperty("iamt")
    private Double iamt;

    @JsonProperty("camt")
    private Double camt;

    @JsonProperty("samt")
    private Double samt;

    @JsonProperty("csamt")
    private Number csamt = 0;

    public B2csItem() {
    }

    public B2csItem(String sply_ty, Number rt, String typ, String pos, Double txval,
                    Double iamt, Double camt, Double samt, Number csamt) {
        this.sply_ty = sply_ty;
        this.rt = rt;
        this.typ = typ != null ? typ : "OE";
        this.pos = pos;
        this.txval = txval;
        this.iamt = iamt;
        this.camt = camt;
        this.samt = samt;
        this.csamt = csamt != null ? csamt : 0;
    }

    public String getSply_ty() {
        return sply_ty;
    }

    public void setSply_ty(String sply_ty) {
        this.sply_ty = sply_ty;
    }

    public Number getRt() {
        return rt;
    }

    public void setRt(Number rt) {
        this.rt = rt;
    }

    public String getTyp() {
        return typ;
    }

    public void setTyp(String typ) {
        this.typ = typ;
    }

    public String getPos() {
        return pos;
    }

    public void setPos(String pos) {
        this.pos = pos;
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

    public Double getCamt() {
        return camt;
    }

    public void setCamt(Double camt) {
        this.camt = camt;
    }

    public Double getSamt() {
        return samt;
    }

    public void setSamt(Double samt) {
        this.samt = samt;
    }

    public Number getCsamt() {
        return csamt;
    }

    public void setCsamt(Number csamt) {
        this.csamt = csamt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        B2csItem b2csItem = (B2csItem) o;
        return Objects.equals(sply_ty, b2csItem.sply_ty) &&
                Objects.equals(rt, b2csItem.rt) &&
                Objects.equals(typ, b2csItem.typ) &&
                Objects.equals(pos, b2csItem.pos) &&
                Objects.equals(txval, b2csItem.txval) &&
                Objects.equals(iamt, b2csItem.iamt) &&
                Objects.equals(camt, b2csItem.camt) &&
                Objects.equals(samt, b2csItem.samt) &&
                Objects.equals(csamt, b2csItem.csamt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sply_ty, rt, typ, pos, txval, iamt, camt, samt, csamt);
    }

    @Override
    public String toString() {
        return "B2csItem{" +
                "sply_ty='" + sply_ty + '\'' +
                ", rt=" + rt +
                ", typ='" + typ + '\'' +
                ", pos='" + pos + '\'' +
                ", txval=" + txval +
                ", iamt=" + iamt +
                ", camt=" + camt +
                ", samt=" + samt +
                ", csamt=" + csamt +
                '}';
    }
}
