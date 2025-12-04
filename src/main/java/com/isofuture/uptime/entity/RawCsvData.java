package com.isofuture.uptime.entity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "raw_csv_data")
public class RawCsvData {

    @Id
    private String id;

    private String fileName;
    private List<String> headers;
    private List<Map<String, String>> rows;
    private Instant ingestedAt;
    private String ingestedBy;

    public RawCsvData() {
        this.ingestedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public Instant getIngestedAt() {
        return ingestedAt;
    }

    public void setIngestedAt(Instant ingestedAt) {
        this.ingestedAt = ingestedAt;
    }

    public String getIngestedBy() {
        return ingestedBy;
    }

    public void setIngestedBy(String ingestedBy) {
        this.ingestedBy = ingestedBy;
    }
}





