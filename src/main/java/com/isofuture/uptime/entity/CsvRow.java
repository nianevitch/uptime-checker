package com.isofuture.uptime.entity;

import java.time.Instant;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * CsvRow - Stores individual CSV rows as separate documents.
 * Each row contains file metadata and the row data.
 */
@Document(collection = "csv_rows")
public class CsvRow {

    @Id
    private String id;

    @Indexed
    private String fileUploadId; // Reference to the FileUpload document
    
    private int rowIndex; // Original row index in the CSV file
    
    private Map<String, String> rawData; // The raw row data before cleansing (field -> value)
    
    private Map<String, String> cleanData; // The cleansed row data after cleansing (field -> value)
    
    // File metadata (duplicated for easy querying)
    private java.util.List<String> headers;
    private Instant ingestedAt;
    private String ingestedBy;
    private Instant processedAt; // When this row was processed/cleansed
    
    // Status for cleansed rows
    private String status; // "pending", "duplicate", "processed"

    public CsvRow() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFileUploadId() {
        return fileUploadId;
    }

    public void setFileUploadId(String fileUploadId) {
        this.fileUploadId = fileUploadId;
    }

    public int getRowIndex() {
        return rowIndex;
    }

    public void setRowIndex(int rowIndex) {
        this.rowIndex = rowIndex;
    }

    public Map<String, String> getRawData() {
        return rawData;
    }

    public void setRawData(Map<String, String> rawData) {
        this.rawData = rawData;
    }

    public Map<String, String> getCleanData() {
        return cleanData;
    }

    public void setCleanData(Map<String, String> cleanData) {
        this.cleanData = cleanData;
    }

    public java.util.List<String> getHeaders() {
        return headers;
    }

    public void setHeaders(java.util.List<String> headers) {
        this.headers = headers;
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

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Checks if the row is soft-deleted by checking the _deleted flag in cleanData.
     */
    public boolean isDeleted() {
        if (cleanData == null) {
            return false;
        }
        String deletedFlag = cleanData.get("_deleted");
        return "true".equals(deletedFlag) || Boolean.TRUE.toString().equals(deletedFlag);
    }

}

