package com.meesho.gst.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Container for Table 13: Documents Issued during the tax period.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocIssueContainer {

    @JsonProperty("doc_det")
    private List<DocDetItem> docDet = new ArrayList<>();

    public DocIssueContainer() {
    }

    public DocIssueContainer(List<DocDetItem> docDet) {
        this.docDet = docDet != null ? docDet : new ArrayList<>();
    }

    public List<DocDetItem> getDocDet() {
        return docDet;
    }

    public void setDocDet(List<DocDetItem> docDet) {
        this.docDet = docDet;
    }

    public void addDocDet(DocDetItem item) {
        if (this.docDet == null) {
            this.docDet = new ArrayList<>();
        }
        this.docDet.add(item);
    }
}
