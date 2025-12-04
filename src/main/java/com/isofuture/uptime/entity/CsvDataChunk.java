package com.isofuture.uptime.entity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * CsvDataChunk - Stores a chunk of CSV rows to avoid MongoDB's 16MB document size limit.
 * Large CSV files are split into multiple chunks.
 */
@Document(collection = "csv_data_chunks")
public class CsvDataChunk {

    @Id
    private String id;

    private String fileId; // Reference to the main file document
    private String chunkType; // "raw" or "cleansed"
    private int chunkIndex; // 0-based index of this chunk
    private List<Map<String, String>> rows; // Rows in this chunk
    private Instant createdAt;

    public CsvDataChunk() {
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public String getChunkType() {
        return chunkType;
    }

    public void setChunkType(String chunkType) {
        this.chunkType = chunkType;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public List<Map<String, String>> getRows() {
        return rows;
    }

    public void setRows(List<Map<String, String>> rows) {
        this.rows = rows;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

