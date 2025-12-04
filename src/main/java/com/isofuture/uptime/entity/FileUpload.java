package com.isofuture.uptime.entity;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * FileUpload - Stores metadata about uploaded CSV files.
 * Each file upload creates a unique document, even if the same file is uploaded by different users.
 */
@Document(collection = "file_uploads")
public class FileUpload {

    @Id
    private String id;

    @Indexed
    private String fileName;
    
    private long fileSizeBytes;
    
    private int rowCount;
    
    private int columnCount;
    
    private java.util.List<String> headers;
    
    private Instant uploadedAt;
    
    @Indexed
    private String uploadedBy;
    
    private String status; // "uploading", "processing", "completed", "error"
    
    private String errorMessage;

    public FileUpload() {
        this.uploadedAt = Instant.now();
        this.status = "uploading";
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

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public int getRowCount() {
        return rowCount;
    }

    public void setRowCount(int rowCount) {
        this.rowCount = rowCount;
    }

    public int getColumnCount() {
        return columnCount;
    }

    public void setColumnCount(int columnCount) {
        this.columnCount = columnCount;
    }

    public java.util.List<String> getHeaders() {
        return headers;
    }

    public void setHeaders(java.util.List<String> headers) {
        this.headers = headers;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(String uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}

