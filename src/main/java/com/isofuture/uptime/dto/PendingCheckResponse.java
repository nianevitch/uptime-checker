package com.isofuture.uptime.dto;

public class PendingCheckResponse {

    private Long pingId;
    private String url;
    private String label;

    public PendingCheckResponse() {
    }

    public PendingCheckResponse(Long pingId, String url, String label) {
        this.pingId = pingId;
        this.url = url;
        this.label = label;
    }

    public Long getPingId() {
        return pingId;
    }

    public void setPingId(Long pingId) {
        this.pingId = pingId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}

