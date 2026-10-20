package com.meesho.gst.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request payload for combining multiple platform returns (e.g. Meesho + Amazon) into a single GSTR-1.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CombineRequest {

    @JsonProperty("meesho")
    private Gstr1Payload meesho;

    @JsonProperty("amazon")
    private Gstr1Payload amazon;

    public CombineRequest() {
    }

    public CombineRequest(Gstr1Payload meesho, Gstr1Payload amazon) {
        this.meesho = meesho;
        this.amazon = amazon;
    }

    public Gstr1Payload getMeesho() {
        return meesho;
    }

    public void setMeesho(Gstr1Payload meesho) {
        this.meesho = meesho;
    }

    public Gstr1Payload getAmazon() {
        return amazon;
    }

    public void setAmazon(Gstr1Payload amazon) {
        this.amazon = amazon;
    }
}
