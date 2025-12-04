package com.isofuture.uptime.entity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "cleansed_csv_data")
public class CleansedCsvData {

    @Id
    private String id;

    private String rawCsvDataId;
    private String fileName;
    private List<String> headers;
    private List<Map<String, String>> rows;
    private Instant cleansedAt;
    private String cleansedBy;
    private Map<String, Object> cleansingMetadata;

    public CleansedCsvData() {
        this.cleansedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRawCsvDataId() {
        return rawCsvDataId;
    }

    public void setRawCsvDataId(String rawCsvDataId) {
        this.rawCsvDataId = rawCsvDataId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public List<String> getHeaders() {
        return headers;
    }

    public void setHeaders(List<String> headers) {
        this.headers = headers;
    }

    public List<Map<String, String>> getRows() {
        return rows;
    }

    public void setRows(List<Map<String, String>> rows) {
        this.rows = rows;
    }

    public Instant getCleansedAt() {
        return cleansedAt;
    }

    public void setCleansedAt(Instant cleansedAt) {
        this.cleansedAt = cleansedAt;
    }

    public String getCleansedBy() {
        return cleansedBy;
    }

    public void setCleansedBy(String cleansedBy) {
        this.cleansedBy = cleansedBy;
    }

    public Map<String, Object> getCleansingMetadata() {
        return cleansingMetadata;
    }

    public void setCleansingMetadata(Map<String, Object> cleansingMetadata) {
        this.cleansingMetadata = cleansingMetadata;
    }
}





