package com.meesho.gst.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Container for Table 14: Supplies made through E-Commerce Operators.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SupecoContainer {

    @JsonProperty("clttx")
    private List<SupecoItem> clttx = new ArrayList<>();

    public SupecoContainer() {
    }

    public SupecoContainer(List<SupecoItem> clttx) {
        this.clttx = clttx != null ? clttx : new ArrayList<>();
    }

    public List<SupecoItem> getClttx() {
        return clttx;
    }

    public void setClttx(List<SupecoItem> clttx) {
        this.clttx = clttx;
    }

    public void addSupecoItem(SupecoItem item) {
        if (this.clttx == null) {
            this.clttx = new ArrayList<>();
        }
        this.clttx.add(item);
    }
}
