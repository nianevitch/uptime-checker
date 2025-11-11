package com.isofuture.uptime.dto;

public class PendingCheckResponse {

    private Long monitorId;
    private String url;
    private String label;

    public PendingCheckResponse() {
    }

    public PendingCheckResponse(Long monitorId, String url, String label) {
        this.monitorId = monitorId;
        this.url = url;
        this.label = label;
    }

    public Long getMonitorId() {
        return monitorId;
    }

    public void setMonitorId(Long monitorId) {
        this.monitorId = monitorId;
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

