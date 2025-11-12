package com.isofuture.uptime.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(
    name = "check_result",
    indexes = {
        @Index(name = "IX_check_result_monitored_url_id", columnList = "monitored_url_id"),
        @Index(name = "IX_check_result_checked_at", columnList = "checked_at")
    }
)
public class CheckResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "monitored_url_id",
        foreignKey = @ForeignKey(name = "FK_check_result_monitored_url_id")
    )
    private MonitoredUrl monitoredUrl;

    @Column(name = "http_code")
    private Integer httpCode;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "response_time_ms")
    private Double responseTimeMs;

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public MonitoredUrl getMonitoredUrl() {
        return monitoredUrl;
    }

    public void setMonitoredUrl(MonitoredUrl monitoredUrl) {
        this.monitoredUrl = monitoredUrl;
    }

    public Integer getHttpCode() {
        return httpCode;
    }

    public void setHttpCode(Integer httpCode) {
        this.httpCode = httpCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Double getResponseTimeMs() {
        return responseTimeMs;
    }

    public void setResponseTimeMs(Double responseTimeMs) {
        this.responseTimeMs = responseTimeMs;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }

    public void setCheckedAt(Instant checkedAt) {
        this.checkedAt = checkedAt;
    }
}

