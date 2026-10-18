package com.meesho.gst.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Container for Table 12 HSN Summary.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HsnContainer {

    @JsonProperty("hsn_b2c")
    private List<HsnItem> hsnB2c = new ArrayList<>();

    public HsnContainer() {
    }

    public HsnContainer(List<HsnItem> hsnB2c) {
        this.hsnB2c = hsnB2c != null ? hsnB2c : new ArrayList<>();
    }

    public List<HsnItem> getHsnB2c() {
        return hsnB2c;
    }

    public void setHsnB2c(List<HsnItem> hsnB2c) {
        this.hsnB2c = hsnB2c;
    }

    public void addHsnItem(HsnItem item) {
        if (this.hsnB2c == null) {
            this.hsnB2c = new ArrayList<>();
        }
        this.hsnB2c.add(item);
    }
}
