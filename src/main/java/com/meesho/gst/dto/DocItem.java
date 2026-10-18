package com.meesho.gst.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Line item in Table 13 Document Details.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "num",
        "from",
        "to",
        "totnum",
        "cancel",
        "net_issue"
})
public class DocItem {

    @JsonProperty("num")
    private Integer num = 1;

    @JsonProperty("from")
    private String from;

    @JsonProperty("to")
    private String to;

    @JsonProperty("totnum")
    private Integer totnum;

    @JsonProperty("cancel")
    private Integer cancel;

    @JsonProperty("net_issue")
    private Integer netIssue;

    public DocItem() {
    }

    public DocItem(Integer num, String from, String to, Integer totnum, Integer cancel, Integer netIssue) {
        this.num = num != null ? num : 1;
        this.from = from;
        this.to = to;
        this.totnum = totnum;
        this.cancel = cancel;
        this.netIssue = netIssue;
    }

    public Integer getNum() {
        return num;
    }

    public void setNum(Integer num) {
        this.num = num;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public Integer getTotnum() {
        return totnum;
    }

    public void setTotnum(Integer totnum) {
        this.totnum = totnum;
    }

    public Integer getCancel() {
        return cancel;
    }

    public void setCancel(Integer cancel) {
        this.cancel = cancel;
    }

    public Integer getNetIssue() {
        return netIssue;
    }

    public void setNetIssue(Integer netIssue) {
        this.netIssue = netIssue;
    }
}
