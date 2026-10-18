package com.meesho.gst.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.List;

/**
 * Document detail group for Table 13.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "doc_num",
        "doc_typ",
        "docs"
})
public class DocDetItem {

    @JsonProperty("doc_num")
    private Integer docNum = 1;

    @JsonProperty("doc_typ")
    private String docTyp = "Invoices for outward supply";

    @JsonProperty("docs")
    private List<DocItem> docs = new ArrayList<>();

    public DocDetItem() {
    }

    public DocDetItem(Integer docNum, String docTyp, List<DocItem> docs) {
        this.docNum = docNum != null ? docNum : 1;
        this.docTyp = docTyp != null ? docTyp : "Invoices for outward supply";
        this.docs = docs != null ? docs : new ArrayList<>();
    }

    public Integer getDocNum() {
        return docNum;
    }

    public void setDocNum(Integer docNum) {
        this.docNum = docNum;
    }

    public String getDocTyp() {
        return docTyp;
    }

    public void setDocTyp(String docTyp) {
        this.docTyp = docTyp;
    }

    public List<DocItem> getDocs() {
        return docs;
    }

    public void setDocs(List<DocItem> docs) {
        this.docs = docs;
    }

    public void addDocItem(DocItem docItem) {
        if (this.docs == null) {
            this.docs = new ArrayList<>();
        }
        this.docs.add(docItem);
    }
}
