package com.meesho.gst.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.List;

/**
 * Root GSTR-1 JSON schema payload conforming strictly to GST portal specification.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "gstin",
        "fp",
        "version",
        "hash",
        "b2cs",
        "hsn",
        "supeco",
        "doc_issue"
})
public class Gstr1Payload {

    @JsonProperty("gstin")
    private String gstin;

    @JsonProperty("fp")
    private String fp;

    @JsonProperty("version")
    private String version = "GST3.2.3";

    @JsonProperty("hash")
    private String hash = "hash";

    @JsonProperty("b2cs")
    private List<B2csItem> b2cs = new ArrayList<>();

    @JsonProperty("hsn")
    private HsnContainer hsn;

    @JsonProperty("supeco")
    private SupecoContainer supeco;

    @JsonProperty("doc_issue")
    private DocIssueContainer docIssue;

    public Gstr1Payload() {
    }

    public Gstr1Payload(String gstin, String period) {
        this.gstin = gstin;
        this.fp = period;
        this.version = "GST3.2.3";
        this.hash = "hash";
    }

    public String getGstin() {
        return gstin;
    }

    public void setGstin(String gstin) {
        this.gstin = gstin;
    }

    public String getFp() {
        return fp;
    }

    public void setFp(String fp) {
        this.fp = fp;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getGfp() {
        return fp;
    }

    public void setGfp(String gfp) {
        this.fp = gfp;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public List<B2csItem> getB2cs() {
        return b2cs;
    }

    public void setB2cs(List<B2csItem> b2cs) {
        this.b2cs = b2cs;
    }

    public void addB2csItem(B2csItem item) {
        if (this.b2cs == null) {
            this.b2cs = new ArrayList<>();
        }
        this.b2cs.add(item);
    }

    public HsnContainer getHsn() {
        return hsn;
    }

    public void setHsn(HsnContainer hsn) {
        this.hsn = hsn;
    }

    public SupecoContainer getSupeco() {
        return supeco;
    }

    public void setSupeco(SupecoContainer supeco) {
        this.supeco = supeco;
    }

    public DocIssueContainer getDocIssue() {
        return docIssue;
    }

    public void setDocIssue(DocIssueContainer docIssue) {
        this.docIssue = docIssue;
    }
}
