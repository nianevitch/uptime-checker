package com.isofuture.uptime.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.isofuture.uptime.entity.CleansedCsvData;
import com.isofuture.uptime.entity.CsvDataChunk;
import com.isofuture.uptime.entity.CsvRow;
import com.isofuture.uptime.entity.FileUpload;
import com.isofuture.uptime.entity.RawCsvData;
import com.isofuture.uptime.repository.CleansedCsvDataRepository;
import com.isofuture.uptime.repository.CsvDataChunkRepository;
import com.isofuture.uptime.repository.CsvRowRepository;
import com.isofuture.uptime.repository.FileUploadRepository;
import com.isofuture.uptime.repository.RawCsvDataRepository;
import com.isofuture.uptime.security.SecurityUser;

@Service
public class CsvIngestionService {

    private static final Logger log = LoggerFactory.getLogger(CsvIngestionService.class);

    private static final int MAX_ROWS_PER_CHUNK = 5000; // Approximate rows per chunk to stay under 14MB

    private final RawCsvDataRepository rawCsvDataRepository;
    private final CleansedCsvDataRepository cleansedCsvDataRepository;
    private final CsvDataChunkRepository csvDataChunkRepository;
    private final CsvRowRepository csvRowRepository;
    private final FileUploadRepository fileUploadRepository;
    private final UserContext userContext;
    private final IdentityResolver identityResolver;

    public CsvIngestionService(
        RawCsvDataRepository rawCsvDataRepository,
        CleansedCsvDataRepository cleansedCsvDataRepository,
        CsvDataChunkRepository csvDataChunkRepository,
        CsvRowRepository csvRowRepository,
        FileUploadRepository fileUploadRepository,
        UserContext userContext,
        IdentityResolver identityResolver
    ) {
        this.rawCsvDataRepository = rawCsvDataRepository;
        this.cleansedCsvDataRepository = cleansedCsvDataRepository;
        this.csvDataChunkRepository = csvDataChunkRepository;
        this.csvRowRepository = csvRowRepository;
        this.fileUploadRepository = fileUploadRepository;
        this.userContext = userContext;
        this.identityResolver = identityResolver;
    }

    public FileUpload ingestRawCsv(InputStream csvInputStream, String fileName, long fileSizeBytes) {
        log.debug("Ingesting raw CSV file: {} ({} bytes)", fileName, fileSizeBytes);
        
        FileUpload fileUpload = null;
        try {
            // Step 1: Create FileUpload document with metadata
            SecurityUser currentUser = userContext.getCurrentUser();
            String username = currentUser != null ? currentUser.getUsername() : "system";
            
            fileUpload = new FileUpload();
            fileUpload.setFileName(fileName);
            fileUpload.setFileSizeBytes(fileSizeBytes);
            fileUpload.setStatus("processing");
            fileUpload.setUploadedBy(username);
            fileUpload = fileUploadRepository.save(fileUpload);
            String fileUploadId = fileUpload.getId();
            
            log.info("Created FileUpload document with ID: {} for file: {}", fileUploadId, fileName);
            
            // Step 2: Parse CSV and save raw rows
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(csvInputStream, StandardCharsets.UTF_8))) {
                
                CSVParser parser = CSVFormat.DEFAULT
                    .builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setIgnoreHeaderCase(true)
                    .setTrim(true)
                    .build()
                    .parse(reader);

                List<String> headers = new ArrayList<>(parser.getHeaderNames());
                log.debug("CSV headers detected: {}", headers);

                List<Map<String, String>> rows = new ArrayList<>();
                for (CSVRecord record : parser) {
                    Map<String, String> row = new LinkedHashMap<>();
                    for (String header : headers) {
                        row.put(header, record.get(header));
                    }
                    rows.add(row);
                }

                log.info("Parsed {} rows from CSV file: {}", rows.size(), fileName);

                // Update FileUpload with row count and headers
                fileUpload.setRowCount(rows.size());
                fileUpload.setColumnCount(headers.size());
                fileUpload.setHeaders(headers);
                fileUpload = fileUploadRepository.save(fileUpload);

                // Step 3: Save all rows with raw data first
                List<CsvRow> csvRows = new ArrayList<>();
                for (int i = 0; i < rows.size(); i++) {
                    CsvRow csvRow = new CsvRow();
                    csvRow.setFileUploadId(fileUploadId);
                    csvRow.setRowIndex(i);
                    csvRow.setRawData(new LinkedHashMap<>(rows.get(i))); // Save raw data
                    csvRow.setHeaders(new ArrayList<>(headers));
                    csvRow.setIngestedAt(fileUpload.getUploadedAt());
                    csvRow.setIngestedBy(username);
                    csvRows.add(csvRow);
                }
                
                // Batch save all rows with raw data
                csvRowRepository.saveAll(csvRows);
                log.info("Saved {} rows with raw data for file: {}", rows.size(), fileName);
                
                // Step 4: Cleanse rows and add clean data to the same documents
                cleanseAndUpdateRows(fileUploadId, csvRows, headers);
                
                // Step 5: Deduplicate (only after cleansing is complete)
                deduplicateRows(fileUploadId);
                
                // Step 6: Update row count to reflect active (non-deleted) clean data rows
                updateFileUploadRowCount(fileUploadId);
                
                // Update FileUpload status to completed
                fileUpload.setStatus("completed");
                fileUpload = fileUploadRepository.save(fileUpload);
                
                log.info("CSV ingestion completed for file: {} (FileUpload ID: {})", fileName, fileUploadId);
                return fileUpload;
            }
        } catch (Exception e) {
            log.error("Error ingesting CSV file: {} - {}", fileName, e.getMessage(), e);
            if (fileUpload != null) {
                fileUpload.setStatus("error");
                fileUpload.setErrorMessage(e.getMessage());
                fileUploadRepository.save(fileUpload);
            }
            throw new RuntimeException("Failed to ingest CSV file: " + fileName, e);
        }
    }

    /**
     * Cleanses rows and adds clean data to the same documents.
     * Each row document contains both rawData (before cleansing) and cleanData (after cleansing).
     */
    private void cleanseAndUpdateRows(String fileUploadId, List<CsvRow> rows, List<String> headers) {
        log.debug("Cleansing rows for FileUpload: {}", fileUploadId);
        
        Instant processedAt = Instant.now();
        
        // Process each row: apply cleansers/observers and add clean data
        for (CsvRow row : rows) {
            // Start with raw data
            Map<String, String> cleanData = new LinkedHashMap<>(row.getRawData());
            
            // TODO: Apply observers/cleansers here
            // For now, we'll just copy raw data to clean data
            // In the future, this is where you'd apply cleansing logic:
            // - Trim whitespace
            // - Normalize formats
            // - Validate data
            // - etc.
            
            // Set clean data on the same row document
            row.setCleanData(cleanData);
            row.setProcessedAt(processedAt);
            // Initialize status as pending (deduplication happens later)
            row.setStatus(RowStatus.PENDING.getValue());
        }
        
        // Batch update all rows with clean data
        csvRowRepository.saveAll(rows);
        log.info("Cleansed and updated {} rows with clean data for FileUpload: {}", rows.size(), fileUploadId);
    }
    
    /**
     * Deduplicates rows. This runs after cleansing is complete.
     * Marks duplicate rows based on identity keys using clean data.
     */
    private void deduplicateRows(String fileUploadId) {
        log.debug("Deduplicating rows for FileUpload: {}", fileUploadId);
        
        // Load all active rows (excluding soft-deleted, they should all have clean data at this point)
        // Deleted flag is stored in cleanData as "_deleted": "true"
        List<CsvRow> allRows = csvRowRepository.findByFileUploadIdOrderByRowIndexAsc(fileUploadId);
        List<CsvRow> rows = allRows.stream()
            .filter(row -> {
                if (row.getCleanData() == null) return false;
                String deletedFlag = row.getCleanData().get("_deleted");
                boolean isDeleted = "true".equals(deletedFlag) || Boolean.TRUE.toString().equals(deletedFlag);
                return !isDeleted;
            })
            .collect(Collectors.toList());
        
        if (rows.isEmpty()) {
            log.warn("No active rows found for FileUpload: {}", fileUploadId);
            return;
        }
        
        // Group rows by identity key (using clean data)
        Map<String, List<CsvRow>> identityGroups = new HashMap<>();
        for (CsvRow row : rows) {
            if (row.getCleanData() == null) {
                log.warn("Row {} for FileUpload {} has no clean data, skipping deduplication", row.getId(), fileUploadId);
                continue;
            }
            String identityKey = identityResolver.getIdentityKey(row.getCleanData());
            if (identityKey != null) {
                identityGroups.computeIfAbsent(identityKey, k -> new ArrayList<>()).add(row);
            }
        }
        
        // Mark duplicates and processed rows
        int duplicateCount = 0;
        for (Map.Entry<String, List<CsvRow>> entry : identityGroups.entrySet()) {
            List<CsvRow> group = entry.getValue();
            if (group.size() > 1) {
                // Multiple rows with same identity - mark as duplicates
                // For each row, store IDs of all other duplicate rows in cleanData.duplicateOf
                for (CsvRow row : group) {
                    row.setStatus(RowStatus.DUPLICATE.getValue());
                    
                    // Collect IDs of all other rows in this duplicate group
                    List<String> duplicateOfIds = new ArrayList<>();
                    for (CsvRow otherRow : group) {
                        if (!row.getId().equals(otherRow.getId()) && otherRow.getId() != null) {
                            duplicateOfIds.add(otherRow.getId());
                        }
                    }
                    
                    // Store duplicateOf as comma-separated string in cleanData
                    if (row.getCleanData() != null && !duplicateOfIds.isEmpty()) {
                        String duplicateOfValue = String.join(",", duplicateOfIds);
                        row.getCleanData().put("duplicateOf", duplicateOfValue);
                    }
                    
                    duplicateCount++;
                }
                log.debug("Found {} duplicate rows for identity: {} (stored duplicateOf references)", group.size(), entry.getKey());
            } else {
                // Single row - mark as processed and remove duplicateOf if it exists
                CsvRow singleRow = group.get(0);
                singleRow.setStatus(RowStatus.PROCESSED.getValue());
                if (singleRow.getCleanData() != null) {
                    singleRow.getCleanData().remove("duplicateOf");
                }
            }
        }
        
        // Mark remaining rows without identity as processed
        for (CsvRow row : rows) {
            if (RowStatus.PENDING.getValue().equals(row.getStatus())) {
                row.setStatus(RowStatus.PROCESSED.getValue());
            }
        }
        
        // Save updated rows (only active rows, soft-deleted rows are not updated)
        csvRowRepository.saveAll(rows);
        log.info("Deduplication complete for FileUpload: {} ({} duplicates found)", fileUploadId, duplicateCount);
    }

    // Legacy method - kept for backward compatibility
    private void cleanseAndSave(RawCsvData rawData, List<Map<String, String>> rawRows) {
        log.debug("Cleansing CSV data for file: {} (ID: {})", rawData.getFileName(), rawData.getId());
        
        CleansedCsvData cleansedData = new CleansedCsvData();
        cleansedData.setRawCsvDataId(rawData.getId());
        cleansedData.setFileName(rawData.getFileName());
        cleansedData.setHeaders(new ArrayList<>(rawData.getHeaders()));
        
        // Get rows - prefer provided rows, otherwise load from individual row documents
        List<Map<String, String>> rawRowsList = rawRows;
        if (rawRowsList == null || rawRowsList.isEmpty()) {
            // Load from individual row documents (legacy - using rawData ID as fileUploadId for backward compatibility)
            List<CsvRow> rawCsvRows = csvRowRepository.findByFileUploadIdOrderByRowIndexAsc(rawData.getId());
            rawRowsList = rawCsvRows.stream()
                .map(row -> row.getRawData() != null ? row.getRawData() : row.getCleanData())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            
            if (rawRowsList.isEmpty()) {
                log.warn("No rows found in row documents, attempting to load from chunks as fallback");
                rawRowsList = loadRowsFromChunks(rawData.getId(), "raw");
            }
        }
        
        // Deep copy rows and add status field
        List<Map<String, String>> cleansedRows = new ArrayList<>();
        Map<String, List<Integer>> identityGroups = new HashMap<>(); // identity -> list of row indices
        
        // First pass: identify duplicates
        for (int i = 0; i < rawRowsList.size(); i++) {
            Map<String, String> row = rawRowsList.get(i);
            Map<String, String> cleansedRow = new LinkedHashMap<>(row);
            
            String identityKey = identityResolver.getIdentityKey(row);
            if (identityKey != null) {
                identityGroups.computeIfAbsent(identityKey, k -> new ArrayList<>()).add(i);
            }
            
            // Initialize status as PENDING
            cleansedRow.put("_status", RowStatus.PENDING.getValue());
            cleansedRows.add(cleansedRow);
        }
        
        // Second pass: mark duplicates
        int duplicateCount = 0;
        for (Map.Entry<String, List<Integer>> entry : identityGroups.entrySet()) {
            List<Integer> indices = entry.getValue();
            if (indices.size() > 1) {
                // Multiple rows with same identity - mark as duplicates
                for (Integer index : indices) {
                    cleansedRows.get(index).put("_status", RowStatus.DUPLICATE.getValue());
                    duplicateCount++;
                }
                log.debug("Found {} duplicate rows for identity: {}", indices.size(), entry.getKey());
            } else {
                // Single row - mark as processed
                cleansedRows.get(indices.get(0)).put("_status", RowStatus.PROCESSED.getValue());
            }
        }
        
        // Mark remaining rows without identity as processed
        for (Map<String, String> row : cleansedRows) {
            if (RowStatus.PENDING.getValue().equals(row.get("_status"))) {
                row.put("_status", RowStatus.PROCESSED.getValue());
            }
        }
        
        // Save cleansed file metadata (rows stored as individual documents)
        cleansedData.setRows(new ArrayList<>()); // Empty - rows stored as individual documents
        cleansedData.setCleansedAt(Instant.now());
        cleansedData.setCleansedBy(rawData.getIngestedBy());
        
        // Metadata about cleansing process
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("cleansingVersion", "1.0");
        metadata.put("cleansingApplied", true);
        metadata.put("rowCount", cleansedRows.size());
        metadata.put("columnCount", rawData.getHeaders().size());
        metadata.put("duplicateCount", duplicateCount);
        metadata.put("processedCount", cleansedRows.size() - duplicateCount);
        cleansedData.setCleansingMetadata(metadata);

        // Save cleansed file metadata
        CleansedCsvData saved = cleansedCsvDataRepository.save(cleansedData);
        String cleansedFileId = saved.getId();
        
        // Store each cleansed row as a separate document
        Instant processedAt = saved.getCleansedAt();
        List<CsvRow> cleansedCsvRows = new ArrayList<>();
        for (int i = 0; i < cleansedRows.size(); i++) {
            Map<String, String> cleansedRow = cleansedRows.get(i);
            String status = cleansedRow.remove("_status"); // Remove status from data, store separately
            
            CsvRow csvRow = new CsvRow();
            csvRow.setFileUploadId(cleansedFileId);
            csvRow.setRowIndex(i);
            csvRow.setCleanData(new LinkedHashMap<>(cleansedRow));
            csvRow.setHeaders(new ArrayList<>(rawData.getHeaders()));
            csvRow.setIngestedAt(rawData.getIngestedAt());
            csvRow.setIngestedBy(rawData.getIngestedBy());
            csvRow.setProcessedAt(processedAt);
            csvRow.setStatus(status);
            cleansedCsvRows.add(csvRow);
        }
        
        // Batch save all cleansed rows
        csvRowRepository.saveAll(cleansedCsvRows);
        log.info("Cleansed CSV data saved with ID: {} for file: {} ({} rows stored as individual documents, {} duplicates found)", 
            cleansedFileId, rawData.getFileName(), cleansedRows.size(), duplicateCount);
    }

    private void saveRowsInChunks(String fileId, String chunkType, List<Map<String, String>> rows) {
        int chunkIndex = 0;
        for (int i = 0; i < rows.size(); i += MAX_ROWS_PER_CHUNK) {
            int endIndex = Math.min(i + MAX_ROWS_PER_CHUNK, rows.size());
            List<Map<String, String>> chunkRows = rows.subList(i, endIndex);
            
            CsvDataChunk chunk = new CsvDataChunk();
            chunk.setFileId(fileId);
            chunk.setChunkType(chunkType);
            chunk.setChunkIndex(chunkIndex);
            chunk.setRows(new ArrayList<>(chunkRows));
            
            csvDataChunkRepository.save(chunk);
            log.debug("Saved chunk {} for file {} ({} rows)", chunkIndex, fileId, chunkRows.size());
            chunkIndex++;
        }
    }

    private List<Map<String, String>> loadRowsFromChunks(String fileId, String chunkType) {
        List<CsvDataChunk> chunks = csvDataChunkRepository.findByFileIdAndChunkTypeOrderByChunkIndexAsc(fileId, chunkType);
        List<Map<String, String>> allRows = new ArrayList<>();
        for (CsvDataChunk chunk : chunks) {
            allRows.addAll(chunk.getRows());
        }
        log.debug("Loaded {} rows from {} chunks for file {}", allRows.size(), chunks.size(), fileId);
        return allRows;
    }

    public List<FileUpload> listUserFiles() {
        SecurityUser currentUser = userContext.getCurrentUser();
        String username = currentUser != null ? currentUser.getUsername() : null;
        
        log.debug("Listing CSV files for user: {}", username);
        
        if (userContext.isAdmin()) {
            // Admins can see all files
            List<FileUpload> allFiles = fileUploadRepository.findAll();
            log.info("Admin user - returning all {} CSV files", allFiles.size());
            return allFiles;
        } else {
            // Regular users see only their own files
            List<FileUpload> userFiles = fileUploadRepository.findByUploadedBy(username != null ? username : "");
            log.info("Returning {} CSV files for user: {}", userFiles.size(), username);
            return userFiles;
        }
    }

    public RawCsvData getFileById(String fileId) {
        log.debug("Getting CSV file with ID: {}", fileId);
        
        RawCsvData rawData = rawCsvDataRepository.findById(fileId)
            .orElseThrow(() -> new RuntimeException("CSV file not found with ID: " + fileId));
        
        SecurityUser currentUser = userContext.getCurrentUser();
        String username = currentUser != null ? currentUser.getUsername() : null;
        
        // Check permissions: users can only view their own files, admins can view any
        if (!userContext.isAdmin() && (username == null || !username.equals(rawData.getIngestedBy()))) {
            log.warn("User {} attempted to view file {} owned by {}", username, fileId, rawData.getIngestedBy());
            throw new RuntimeException("You do not have permission to view this file");
        }
        
        log.info("Returning CSV file with ID: {} (fileName: {})", fileId, rawData.getFileName());
        return rawData;
    }

    public CleansedCsvData getCleansedFileById(String fileId) {
        log.debug("Getting cleansed CSV file with ID: {}", fileId);
        
        // Try to find FileUpload first (new structure)
        FileUpload fileUpload = fileUploadRepository.findById(fileId).orElse(null);
        
        CleansedCsvData cleansedData = null;
        if (fileUpload != null) {
            // New structure: create CleansedCsvData from FileUpload
            cleansedData = new CleansedCsvData();
            cleansedData.setId(fileUpload.getId());
            cleansedData.setFileName(fileUpload.getFileName());
            cleansedData.setHeaders(fileUpload.getHeaders());
            cleansedData.setCleansedAt(fileUpload.getUploadedAt());
            cleansedData.setCleansedBy(fileUpload.getUploadedBy());
            
            // Check permissions for FileUpload
            SecurityUser currentUser = userContext.getCurrentUser();
            String username = currentUser != null ? currentUser.getUsername() : null;
            
            if (!userContext.isAdmin() && (username == null || !username.equals(fileUpload.getUploadedBy()))) {
                log.warn("User {} attempted to view file {} owned by {}", username, fileId, fileUpload.getUploadedBy());
                throw new RuntimeException("You do not have permission to view this file");
            }
        } else {
            // Legacy: Try to find by cleansed data ID
            cleansedData = cleansedCsvDataRepository.findById(fileId).orElse(null);
            
            // If not found, try to find by raw CSV data ID (for backward compatibility)
            if (cleansedData == null) {
                RawCsvData rawDataCheck = rawCsvDataRepository.findById(fileId).orElse(null);
                if (rawDataCheck != null) {
                    String rawId = fileId; // Make effectively final for lambda
                    cleansedData = cleansedCsvDataRepository.findAll().stream()
                        .filter(data -> rawId.equals(data.getRawCsvDataId()))
                        .findFirst()
                        .orElse(null);
                }
            }
            
            if (cleansedData == null) {
                log.warn("CSV file not found with ID: {}", fileId);
                throw new RuntimeException("CSV file not found with ID: " + fileId);
            }
            
            // Get raw data for permission check (legacy)
            final CleansedCsvData finalCleansedData = cleansedData; // Make effectively final
            RawCsvData rawData = rawCsvDataRepository.findById(finalCleansedData.getRawCsvDataId())
                .orElseThrow(() -> new RuntimeException("Raw CSV file not found with ID: " + finalCleansedData.getRawCsvDataId()));
            
            SecurityUser currentUser = userContext.getCurrentUser();
            String username = currentUser != null ? currentUser.getUsername() : null;
            
            // Check permissions
            if (!userContext.isAdmin() && (username == null || !username.equals(rawData.getIngestedBy()))) {
                log.warn("User {} attempted to view cleansed file {} owned by {}", username, fileId, rawData.getIngestedBy());
                throw new RuntimeException("You do not have permission to view this file");
            }
        }
        
        final CleansedCsvData finalCleansedData = cleansedData;
        String fileUploadId = fileUpload != null ? fileUpload.getId() : finalCleansedData.getId();
        
        // Load rows from individual documents (excluding soft-deleted rows)
        // Deleted flag is stored in cleanData as "_deleted": "true"
        log.debug("Loading rows for file: {} (fileUploadId: {})", fileId, fileUploadId);
        List<CsvRow> csvRows = csvRowRepository.findByFileUploadIdOrderByRowIndexAsc(fileUploadId);
        // Filter out soft-deleted rows (check _deleted flag in cleanData)
        List<CsvRow> activeRows = csvRows.stream()
            .filter(row -> {
                if (row.getCleanData() == null) {
                    return false; // No clean data means not active
                }
                String deletedFlag = row.getCleanData().get("_deleted");
                boolean isDeleted = "true".equals(deletedFlag) || Boolean.TRUE.toString().equals(deletedFlag);
                return !isDeleted;
            })
            .collect(Collectors.toList());
        log.debug("Loaded {} rows for file: {} ({} active, {} soft-deleted)", 
            csvRows.size(), fileId, activeRows.size(), csvRows.size() - activeRows.size());
        if (activeRows.isEmpty()) {
            // Fallback: try loading from chunks (for backward compatibility)
            log.debug("No active rows found in individual documents, attempting to load from chunks");
            List<Map<String, String>> rows = loadRowsFromChunks(finalCleansedData.getId(), "cleansed");
            finalCleansedData.setRows(rows);
        } else {
            // Convert CsvRow documents to Map format (using clean data, excluding soft-deleted)
            List<Map<String, String>> rows = new ArrayList<>();
            for (CsvRow csvRow : activeRows) {
                // Use clean data if available, otherwise fall back to raw data
                Map<String, String> rowData = csvRow.getCleanData() != null 
                    ? new LinkedHashMap<>(csvRow.getCleanData())
                    : (csvRow.getRawData() != null ? new LinkedHashMap<>(csvRow.getRawData()) : new LinkedHashMap<>());
                // Add status back to row data if present
                if (csvRow.getStatus() != null) {
                    rowData.put("_status", csvRow.getStatus());
                }
                // Add row ID for efficient lookups (won't change when indices shift)
                if (csvRow.getId() != null) {
                    rowData.put("_id", csvRow.getId());
                }
                rows.add(rowData);
            }
            finalCleansedData.setRows(rows);
        }
        
        log.info("Returning cleansed CSV file with ID: {} (fileName: {}, {} rows)", 
            finalCleansedData.getId(), finalCleansedData.getFileName(), 
            finalCleansedData.getRows() != null ? finalCleansedData.getRows().size() : 0);
        return finalCleansedData;
    }

    /**
     * Batch merge-delete operation: removes all duplicate rows referenced in the requests.
     * Accepts a collection of merge-delete requests, each containing row IDs to delete.
     * Returns the updated cleansed file data.
     */
    public CleansedCsvData batchMergeDelete(String fileId, List<com.isofuture.uptime.controller.CsvIngestionController.MergeDeleteRequest> requests) {
        log.debug("Batch merge-delete for file: {} with {} requests", fileId, requests.size());
        
        // Verify file exists and user has permission
        FileUpload fileUpload = fileUploadRepository.findById(fileId).orElse(null);
        if (fileUpload == null) {
            throw new RuntimeException("CSV file not found with ID: " + fileId);
        }
        
        SecurityUser currentUser = userContext.getCurrentUser();
        String username = currentUser != null ? currentUser.getUsername() : null;
        if (!userContext.isAdmin() && (username == null || !username.equals(fileUpload.getUploadedBy()))) {
            log.warn("User {} attempted to merge-delete file {} owned by {}", username, fileId, fileUpload.getUploadedBy());
            throw new RuntimeException("You do not have permission to modify this file");
        }
        
        // Collect all row IDs from all requests
        Set<String> allRowIdsToDelete = new HashSet<>();
        for (com.isofuture.uptime.controller.CsvIngestionController.MergeDeleteRequest request : requests) {
            if (!"merge-delete".equals(request.getMode())) {
                log.warn("Invalid mode in merge-delete request: {}, expected 'merge-delete'", request.getMode());
                continue;
            }
            List<String> rowIds = request.getAllRowIds();
            if (rowIds.size() < 2) {
                log.warn("Merge-delete request must have at least 2 row IDs, found: {}", rowIds.size());
                continue;
            }
            allRowIdsToDelete.addAll(rowIds);
        }
        
        if (allRowIdsToDelete.isEmpty()) {
            log.warn("No valid row IDs found in merge-delete requests");
            return getCleansedFileById(fileId);
        }
        
        log.debug("Deleting {} unique rows from file: {}", allRowIdsToDelete.size(), fileId);
        
        // Load all rows to delete
        List<CsvRow> rowsToDelete = new ArrayList<>();
        for (String rowId : allRowIdsToDelete) {
            Optional<CsvRow> rowOpt = csvRowRepository.findById(rowId);
            if (rowOpt.isEmpty()) {
                log.warn("Row not found with ID: {} for file: {}", rowId, fileId);
                continue;
            }
            CsvRow row = rowOpt.get();
            // Verify the row belongs to this file
            if (!fileId.equals(row.getFileUploadId())) {
                log.warn("Row {} does not belong to file: {}", rowId, fileId);
                continue;
            }
            rowsToDelete.add(row);
        }
        
        if (rowsToDelete.isEmpty()) {
            log.warn("No valid rows found to soft-delete");
            return getCleansedFileById(fileId);
        }
        
        // Soft-delete all rows (set _deleted=true in cleanData)
        for (CsvRow row : rowsToDelete) {
            if (row.getCleanData() != null) {
                row.getCleanData().put("_deleted", "true");
            }
        }
        csvRowRepository.saveAll(rowsToDelete);
        log.debug("Soft-deleted {} rows from file: {}", rowsToDelete.size(), fileId);
        
        log.info("Batch merge-delete complete for file: {} - Soft-deleted {} rows", 
            fileId, rowsToDelete.size());
        
        // Update FileUpload row count to reflect active rows
        updateFileUploadRowCount(fileId);
        
        // Return updated file data
        return getCleansedFileById(fileId);
    }

    public Map<String, Object> mergeDuplicateRows(String fileId, List<Integer> rowIndices, List<String> rowIds, Map<String, String> mergedRow, boolean useCustomIdentity, String mergeStrategy) {
        log.debug("Merging duplicate rows for file: {}, row indices: {}, row IDs: {}, useCustomIdentity: {}, strategy: {}", fileId, rowIndices, rowIds, useCustomIdentity, mergeStrategy);
        
        // Load only the rows being merged (not the entire file)
        // Prefer row IDs over indices for better performance and stability
        List<CsvRow> rowsToMerge = new ArrayList<>();
        if (rowIds != null && !rowIds.isEmpty()) {
            // Use row IDs (faster and more reliable)
            for (String rowId : rowIds) {
                Optional<CsvRow> rowOpt = csvRowRepository.findById(rowId);
                if (rowOpt.isEmpty()) {
                    throw new RuntimeException("Row not found with ID " + rowId + " for file: " + fileId);
                }
                CsvRow row = rowOpt.get();
                // Verify the row belongs to this file, has clean data, and is not already soft-deleted
                if (!fileId.equals(row.getFileUploadId()) || row.getCleanData() == null) {
                    throw new RuntimeException("Row " + rowId + " does not belong to file: " + fileId + " or has no clean data");
                }
                // Check if already soft-deleted
                String deletedFlag = row.getCleanData().get("_deleted");
                boolean isDeleted = "true".equals(deletedFlag) || Boolean.TRUE.toString().equals(deletedFlag);
                if (isDeleted) {
                    throw new RuntimeException("Row " + rowId + " is already soft-deleted");
                }
                rowsToMerge.add(row);
            }
        } else if (rowIndices != null && !rowIndices.isEmpty()) {
            // Fallback to indices (for backward compatibility)
            for (Integer index : rowIndices) {
                Optional<CsvRow> rowOpt = csvRowRepository.findByFileUploadIdAndRowIndex(fileId, index);
                if (rowOpt.isEmpty()) {
                    throw new RuntimeException("Row not found at index " + index + " for file: " + fileId);
                }
                CsvRow row = rowOpt.get();
                // Verify the row belongs to this file, has clean data, and is not already soft-deleted
                if (!fileId.equals(row.getFileUploadId()) || row.getCleanData() == null) {
                    throw new RuntimeException("Row at index " + index + " does not belong to file: " + fileId + " or has no clean data");
                }
                // Check if already soft-deleted
                String deletedFlag = row.getCleanData().get("_deleted");
                boolean isDeleted = "true".equals(deletedFlag) || Boolean.TRUE.toString().equals(deletedFlag);
                if (isDeleted) {
                    throw new RuntimeException("Row at index " + index + " is already soft-deleted");
                }
                rowsToMerge.add(row);
            }
        } else {
            throw new RuntimeException("Either rowIds or rowIndices must be provided");
        }
        
        if (rowsToMerge.isEmpty()) {
            throw new RuntimeException("No rows found to merge");
        }
        
        // Handle "merge-delete" strategy: if 2+ duplicates, soft-delete all rows
        if ("merge-delete".equals(mergeStrategy) && rowsToMerge.size() >= 2) {
            return mergeDeleteRows(fileId, rowsToMerge);
            
        }
        
        // Get headers from first row
        List<String> headers = rowsToMerge.get(0).getHeaders();
        if (headers == null || headers.isEmpty()) {
            // Fallback: get headers from FileUpload
            Optional<FileUpload> fileUploadOpt = fileUploadRepository.findById(fileId);
            if (fileUploadOpt.isPresent()) {
                headers = fileUploadOpt.get().getHeaders();
            }
        }
        
        // For "auto-merge" strategy, automatically determine merged row data
        Map<String, String> mergedRowData;
        if ("auto-merge".equals(mergeStrategy)) {
            mergedRowData = autoMergeRows(rowsToMerge, headers);
        } else {
            // Manual merge: use provided mergedRow
            mergedRowData = new LinkedHashMap<>(mergedRow);
            mergedRowData.remove("_status");
        }
        
        // Determine status for merged row
        String mergedStatus;
        List<CsvRow> rowsToUpdateStatus = new ArrayList<>();
        if (useCustomIdentity) {
            // If custom identity, need to check if merged row creates duplicates
            String mergedIdentityKey = identityResolver.getIdentityKey(mergedRowData);
            if (mergedIdentityKey != null) {
                // Check if there are other rows with the same identity (excluding rows being merged)
                // Get row indices from rowsToMerge to exclude them
                Set<Integer> mergedRowIndices = rowsToMerge.stream()
                    .map(CsvRow::getRowIndex)
                    .collect(Collectors.toSet());
                
                List<CsvRow> allRowsWithSameIdentity = csvRowRepository.findByFileUploadIdOrderByRowIndexAsc(fileId)
                    .stream()
                    .filter(row -> {
                        if (row.getCleanData() == null || row.isDeleted()) return false;
                        String rowIdentity = identityResolver.getIdentityKey(row.getCleanData());
                        return mergedIdentityKey.equals(rowIdentity) && !mergedRowIndices.contains(row.getRowIndex());
                    })
                    .collect(Collectors.toList());
                
                if (allRowsWithSameIdentity.isEmpty()) {
                    mergedStatus = RowStatus.PROCESSED.getValue();
                } else {
                    mergedStatus = RowStatus.DUPLICATE.getValue();
                    // Mark other rows with same identity as duplicates (batch update)
                    for (CsvRow row : allRowsWithSameIdentity) {
                        row.setStatus(RowStatus.DUPLICATE.getValue());
                        rowsToUpdateStatus.add(row);
                    }
                }
            } else {
                mergedStatus = RowStatus.PROCESSED.getValue();
            }
        } else {
            // Standard merge - merged row is processed
            mergedStatus = RowStatus.PROCESSED.getValue();
        }
        
        // Batch save status updates if any
        if (!rowsToUpdateStatus.isEmpty()) {
            csvRowRepository.saveAll(rowsToUpdateStatus);
        }
        
        // Extract row indices from rowsToMerge (in case rowIndices parameter was null)
        List<Integer> actualRowIndices = rowsToMerge.stream()
            .map(CsvRow::getRowIndex)
            .collect(Collectors.toList());
        
        // Use the minimum index as the merged row index
        int mergedRowIndex = actualRowIndices.stream().mapToInt(Integer::intValue).min().orElse(0);
        
        // Delete the rows being merged (batch delete using IDs from rowsToMerge)
        List<String> rowIdsToDelete = rowsToMerge.stream()
            .map(CsvRow::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        if (!rowIdsToDelete.isEmpty()) {
            csvRowRepository.deleteAllById(rowIdsToDelete);
        }
        
        // Shift remaining rows' indices down to fill gaps
        // Get all rows with index greater than mergedRowIndex
        // We need to shift them down by (number of deleted rows - 1) since we're inserting 1 merged row
        int shiftAmount = actualRowIndices.size() - 1;
        List<CsvRow> allRemainingRows = csvRowRepository.findByFileUploadIdOrderByRowIndexAsc(fileId);
        List<CsvRow> rowsToShift = allRemainingRows.stream()
            .filter(row -> row.getRowIndex() > mergedRowIndex)
            .collect(Collectors.toList());
        
        // Shift indices down (batch update)
        if (!rowsToShift.isEmpty()) {
            for (CsvRow row : rowsToShift) {
                row.setRowIndex(row.getRowIndex() - shiftAmount);
            }
            csvRowRepository.saveAll(rowsToShift);
        }
        
        // Create the merged row document
        CsvRow mergedCsvRow = new CsvRow();
        mergedCsvRow.setFileUploadId(fileId);
        mergedCsvRow.setRowIndex(mergedRowIndex);
        mergedCsvRow.setCleanData(new LinkedHashMap<>(mergedRowData));
        mergedCsvRow.setHeaders(headers != null ? new ArrayList<>(headers) : new ArrayList<>());
        mergedCsvRow.setIngestedAt(rowsToMerge.get(0).getIngestedAt());
        mergedCsvRow.setIngestedBy(rowsToMerge.get(0).getIngestedBy());
        mergedCsvRow.setProcessedAt(Instant.now());
        mergedCsvRow.setStatus(mergedStatus);
        // Note: rawRowId is intentionally left null for merged rows since they represent
        // data combined from multiple raw rows, not a single raw row
        
        // Save the merged row
        csvRowRepository.save(mergedCsvRow);
        
        // Build response with merged row and affected rows
        Map<String, String> mergedRowResponse = new LinkedHashMap<>(mergedRowData);
        mergedRowResponse.put("_status", mergedStatus);
        
        Map<Integer, Map<String, String>> affectedRows = new HashMap<>();
        affectedRows.put(mergedRowIndex, mergedRowResponse);
        
        // Add shifted rows to affected rows
        for (CsvRow shiftedRow : rowsToShift) {
            Map<String, String> shiftedRowData = shiftedRow.getCleanData() != null 
                ? new LinkedHashMap<>(shiftedRow.getCleanData())
                : (shiftedRow.getRawData() != null ? new LinkedHashMap<>(shiftedRow.getRawData()) : new LinkedHashMap<>());
            shiftedRowData.put("_status", shiftedRow.getStatus());
            affectedRows.put(shiftedRow.getRowIndex(), shiftedRowData);
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("mergedRow", mergedRowResponse);
        result.put("mergedRowIndex", mergedRowIndex);
        result.put("affectedRows", affectedRows);
        result.put("deletedRowIndices", actualRowIndices);
        
        log.info("Merged {} duplicate rows for file: {} into row at index {} using strategy: {}", actualRowIndices.size(), fileId, mergedRowIndex, mergeStrategy);
        
        // Update FileUpload row count to reflect active rows (if merge-delete was used, rows were soft-deleted)
        if ("merge-delete".equals(mergeStrategy)) {
            updateFileUploadRowCount(fileId);
        }
        
        return result;
    }
    
    /**
     * Strategy: Merge-delete - soft-delete all duplicate rows (if 2+ duplicates detected, soft-delete all rows).
     * This does not create a merged row - it simply soft-deletes all duplicate rows by setting _deleted=true in cleanData.
     */
    private Map<String, Object> mergeDeleteRows(String fileId, List<CsvRow> rowsToMerge) {
        log.debug("Soft-deleting all {} duplicate rows for file: {} (merge-delete strategy)", rowsToMerge.size(), fileId);
        
        // Extract row indices from rowsToMerge
        List<Integer> rowIndices = rowsToMerge.stream()
            .map(CsvRow::getRowIndex)
            .collect(Collectors.toList());
        
        // Soft-delete all duplicate rows (set _deleted=true in cleanData)
        for (CsvRow row : rowsToMerge) {
            if (row.getCleanData() != null) {
                row.getCleanData().put("_deleted", "true");
            } else {
                log.warn("Row {} has no cleanData, cannot soft-delete", row.getId());
            }
        }
        csvRowRepository.saveAll(rowsToMerge);
        
        // Update FileUpload row count to reflect active rows
        updateFileUploadRowCount(fileId);
        
        // Build response (no merged row, just soft-deleted rows)
        Map<String, Object> result = new HashMap<>();
        result.put("mergedRow", null); // No merged row created
        result.put("mergedRowIndex", -1); // No merged row
        result.put("affectedRows", new HashMap<>()); // No affected rows
        result.put("deletedRowIndices", rowIndices);
        
        log.info("Soft-deleted all {} duplicate rows for file: {} (no merged row created)", rowIndices.size(), fileId);
        
        return result;
    }
    
    /**
     * Updates the FileUpload row count to reflect the current number of active (non-deleted) clean data rows.
     */
    private void updateFileUploadRowCount(String fileUploadId) {
        log.debug("Updating row count for FileUpload: {}", fileUploadId);
        
        Optional<FileUpload> fileUploadOpt = fileUploadRepository.findById(fileUploadId);
        if (fileUploadOpt.isEmpty()) {
            log.warn("FileUpload not found with ID: {}", fileUploadId);
            return;
        }
        
        // Count active (non-deleted) rows with clean data
        List<CsvRow> allRows = csvRowRepository.findByFileUploadIdOrderByRowIndexAsc(fileUploadId);
        long activeRowCount = allRows.stream()
            .filter(row -> {
                if (row.getCleanData() == null) return false;
                // Check if _deleted flag is set in cleanData
                String deletedFlag = row.getCleanData().get("_deleted");
                boolean isDeleted = "true".equals(deletedFlag) || Boolean.TRUE.toString().equals(deletedFlag);
                return !isDeleted;
            })
            .count();
        
        FileUpload fileUpload = fileUploadOpt.get();
        int previousCount = fileUpload.getRowCount();
        fileUpload.setRowCount((int) activeRowCount);
        fileUploadRepository.save(fileUpload);
        
        log.debug("Updated FileUpload {} row count: {} -> {}", fileUploadId, previousCount, activeRowCount);
    }
    
    /**
     * Batch auto-merge all duplicate groups for a file.
     * Processes all groups in a single transaction for better performance.
     * For merge-delete strategy, collects all rows to delete and soft-deletes them in one batch.
     */
    public Map<String, Object> autoMergeAllDuplicateGroups(String fileId, List<Map<String, Object>> duplicateGroups, String mergeStrategy) {
        log.debug("Auto-merging {} duplicate groups for file: {} using strategy: {}", duplicateGroups.size(), fileId, mergeStrategy);
        
        // Optimize merge-delete: collect all rows to delete and soft-delete in one batch
        if ("merge-delete".equals(mergeStrategy)) {
            return batchMergeDeleteAllGroups(fileId, duplicateGroups);
        }
        
        int totalProcessed = 0;
        int totalSuccess = 0;
        List<String> errors = new ArrayList<>();
        
        // Process all groups (for non-merge-delete strategies)
        for (int i = 0; i < duplicateGroups.size(); i++) {
            Map<String, Object> group = duplicateGroups.get(i);
            try {
                @SuppressWarnings("unchecked")
                List<String> rowIds = (List<String>) group.get("rowIds");
                @SuppressWarnings("unchecked")
                List<Integer> rowIndices = (List<Integer>) group.get("rowIndices");
                
                // Use rowIds if available, otherwise rowIndices
                List<String> groupRowIds = (rowIds != null && !rowIds.isEmpty()) ? rowIds : null;
                List<Integer> groupRowIndices = (groupRowIds == null && rowIndices != null && !rowIndices.isEmpty()) ? rowIndices : null;
                
                if (groupRowIds == null && groupRowIndices == null) {
                    errors.add("Group " + (i + 1) + ": No rowIds or rowIndices provided");
                    continue;
                }
                
                // Merge this group (empty mergedRow for auto-merge strategies)
                Map<String, String> emptyMergedRow = new HashMap<>();
                mergeDuplicateRows(fileId, groupRowIndices, groupRowIds, emptyMergedRow, false, mergeStrategy);
                totalSuccess++;
            } catch (Exception e) {
                log.warn("Failed to merge group {}: {}", i + 1, e.getMessage());
                errors.add("Group " + (i + 1) + ": " + e.getMessage());
            }
            totalProcessed++;
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("totalGroups", duplicateGroups.size());
        result.put("processedGroups", totalProcessed);
        result.put("successfulGroups", totalSuccess);
        result.put("failedGroups", totalProcessed - totalSuccess);
        if (!errors.isEmpty()) {
            result.put("errors", errors);
        }
        
        log.info("Auto-merge complete for file: {} - Processed: {}, Successful: {}, Failed: {}", 
            fileId, totalProcessed, totalSuccess, totalProcessed - totalSuccess);
        return result;
    }
    
    /**
     * Batch merge-delete: collects all rows from all duplicate groups and soft-deletes them in one operation.
     * This is much faster than processing each group separately.
     */
    private Map<String, Object> batchMergeDeleteAllGroups(String fileId, List<Map<String, Object>> duplicateGroups) {
        log.debug("Batch merge-delete: collecting all rows from {} duplicate groups for file: {}", duplicateGroups.size(), fileId);
        
        // Collect all row IDs from all groups
        Set<String> allRowIds = new HashSet<>();
        int totalProcessed = 0;
        int totalSuccess = 0;
        List<String> errors = new ArrayList<>();
        
        for (int i = 0; i < duplicateGroups.size(); i++) {
            Map<String, Object> group = duplicateGroups.get(i);
            try {
                @SuppressWarnings("unchecked")
                List<String> rowIds = (List<String>) group.get("rowIds");
                @SuppressWarnings("unchecked")
                List<Integer> rowIndices = (List<Integer>) group.get("rowIndices");
                
                // Use rowIds if available, otherwise rowIndices
                List<String> groupRowIds = (rowIds != null && !rowIds.isEmpty()) ? rowIds : null;
                List<Integer> groupRowIndices = (groupRowIds == null && rowIndices != null && !rowIndices.isEmpty()) ? rowIndices : null;
                
                if (groupRowIds == null && groupRowIndices == null) {
                    errors.add("Group " + (i + 1) + ": No rowIds or rowIndices provided");
                    continue;
                }
                
                // Collect row IDs from this group
                if (groupRowIds != null && !groupRowIds.isEmpty()) {
                    allRowIds.addAll(groupRowIds);
                    totalSuccess++;
                } else if (groupRowIndices != null && !groupRowIndices.isEmpty()) {
                    // Load rows by indices to get their IDs
                    for (Integer index : groupRowIndices) {
                        Optional<CsvRow> rowOpt = csvRowRepository.findByFileUploadIdAndRowIndex(fileId, index);
                        if (rowOpt.isPresent()) {
                            CsvRow row = rowOpt.get();
                            if (row.getId() != null) {
                                allRowIds.add(row.getId());
                            }
                        }
                    }
                    totalSuccess++;
                }
            } catch (Exception e) {
                log.warn("Failed to process group {}: {}", i + 1, e.getMessage());
                errors.add("Group " + (i + 1) + ": " + e.getMessage());
            }
            totalProcessed++;
        }
        
        if (allRowIds.isEmpty()) {
            log.warn("No valid row IDs found in duplicate groups for batch merge-delete");
            Map<String, Object> result = new HashMap<>();
            result.put("totalGroups", duplicateGroups.size());
            result.put("processedGroups", totalProcessed);
            result.put("successfulGroups", totalSuccess);
            result.put("failedGroups", totalProcessed - totalSuccess);
            if (!errors.isEmpty()) {
                result.put("errors", errors);
            }
            return result;
        }
        
        log.debug("Batch merge-delete: soft-deleting {} unique rows from {} groups", allRowIds.size(), duplicateGroups.size());
        
        // Load all rows to delete in one batch
        List<CsvRow> rowsToDelete = new ArrayList<>();
        for (String rowId : allRowIds) {
            Optional<CsvRow> rowOpt = csvRowRepository.findById(rowId);
            if (rowOpt.isEmpty()) {
                log.warn("Row not found with ID: {} for file: {}", rowId, fileId);
                continue;
            }
            CsvRow row = rowOpt.get();
            // Verify the row belongs to this file and is not already soft-deleted
            if (!fileId.equals(row.getFileUploadId())) {
                log.warn("Row {} does not belong to file: {}", rowId, fileId);
                continue;
            }
            if (row.getCleanData() == null) {
                log.warn("Row {} has no cleanData, skipping", rowId);
                continue;
            }
            // Check if already soft-deleted
            String deletedFlag = row.getCleanData().get("_deleted");
            boolean isDeleted = "true".equals(deletedFlag) || Boolean.TRUE.toString().equals(deletedFlag);
            if (isDeleted) {
                log.debug("Row {} is already soft-deleted, skipping", rowId);
                continue;
            }
            rowsToDelete.add(row);
        }
        
        if (rowsToDelete.isEmpty()) {
            log.warn("No valid rows found to soft-delete");
            Map<String, Object> result = new HashMap<>();
            result.put("totalGroups", duplicateGroups.size());
            result.put("processedGroups", totalProcessed);
            result.put("successfulGroups", totalSuccess);
            result.put("failedGroups", totalProcessed - totalSuccess);
            if (!errors.isEmpty()) {
                result.put("errors", errors);
            }
            return result;
        }
        
        // Soft-delete all rows in one batch operation
        for (CsvRow row : rowsToDelete) {
            row.getCleanData().put("_deleted", "true");
        }
        csvRowRepository.saveAll(rowsToDelete);
        log.debug("Batch soft-deleted {} rows from file: {}", rowsToDelete.size(), fileId);
        
        // Update FileUpload row count once at the end
        updateFileUploadRowCount(fileId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("totalGroups", duplicateGroups.size());
        result.put("processedGroups", totalProcessed);
        result.put("successfulGroups", totalSuccess);
        result.put("failedGroups", totalProcessed - totalSuccess);
        result.put("deletedRowsCount", rowsToDelete.size());
        if (!errors.isEmpty()) {
            result.put("errors", errors);
        }
        
        log.info("Batch merge-delete complete for file: {} - Soft-deleted {} rows from {} groups", 
            fileId, rowsToDelete.size(), duplicateGroups.size());
        return result;
    }
    
    /**
     * Strategy: Auto-merge rows - row with more data (non-empty fields) wins.
     * For each field, if multiple rows have values, prefer the one from the row with most data.
     */
    private Map<String, String> autoMergeRows(List<CsvRow> rowsToMerge, List<String> headers) {
        log.debug("Auto-merging {} rows (row with more data wins)", rowsToMerge.size());
        
        // Calculate data richness for each row (count of non-empty fields in clean data)
        Map<CsvRow, Integer> rowDataCount = new HashMap<>();
        for (CsvRow row : rowsToMerge) {
            int count = 0;
            Map<String, String> data = row.getCleanData();
            if (data != null) {
                for (String value : data.values()) {
                    if (value != null && !value.trim().isEmpty()) {
                        count++;
                    }
                }
            }
            rowDataCount.put(row, count);
        }
        
        // Find the row with the most data
        CsvRow richestRow = rowsToMerge.stream()
            .max(Comparator.comparingInt(rowDataCount::get))
            .orElse(rowsToMerge.get(0));
        
        // Start with the richest row's clean data
        Map<String, String> mergedData = richestRow.getCleanData() != null 
            ? new LinkedHashMap<>(richestRow.getCleanData())
            : new LinkedHashMap<>();
        
        // For each field, if the richest row has empty value, try to fill from other rows
        // Prefer values from rows with more data
        List<CsvRow> sortedRows = rowsToMerge.stream()
            .sorted(Comparator.comparingInt(rowDataCount::get).reversed())
            .collect(Collectors.toList());
        
        for (String header : headers) {
            String currentValue = mergedData.get(header);
            // If current value is empty, try to get from other rows (prioritize rows with more data)
            if (currentValue == null || currentValue.trim().isEmpty()) {
                for (CsvRow row : sortedRows) {
                    Map<String, String> rowData = row.getCleanData();
                    if (rowData != null) {
                        String value = rowData.get(header);
                        if (value != null && !value.trim().isEmpty()) {
                            mergedData.put(header, value);
                            break; // Use first non-empty value found
                        }
                    }
                }
            }
        }
        
        log.debug("Auto-merged {} rows, using row with {} non-empty fields as base", 
            rowsToMerge.size(), rowDataCount.get(richestRow));
        return mergedData;
    }

    /**
     * Re-evaluates duplicate status for all rows based on identity keys.
     * This is called when a custom identity value is used in merge.
     */
    private void reEvaluateDuplicateStatus(List<Map<String, String>> rows) {
        log.debug("Re-evaluating duplicate status for {} rows", rows.size());
        
        // Reset all statuses to PENDING
        for (Map<String, String> row : rows) {
            row.put("_status", RowStatus.PENDING.getValue());
        }
        
        // Group rows by identity key
        Map<String, List<Integer>> identityGroups = new HashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            String identityKey = identityResolver.getIdentityKey(row);
            if (identityKey != null) {
                identityGroups.computeIfAbsent(identityKey, k -> new ArrayList<>()).add(i);
            }
        }
        
        // Mark duplicates and processed rows, and set duplicateOf references
        for (Map.Entry<String, List<Integer>> entry : identityGroups.entrySet()) {
            List<Integer> indices = entry.getValue();
            if (indices.size() > 1) {
                // Multiple rows with same identity - mark as duplicates
                // For each row, store IDs of all other duplicate rows in duplicateOf
                for (int i = 0; i < indices.size(); i++) {
                    Integer index = indices.get(i);
                    Map<String, String> row = rows.get(index);
                    row.put("_status", RowStatus.DUPLICATE.getValue());
                    
                    // Collect IDs of all other rows in this duplicate group
                    List<String> duplicateOfIds = new ArrayList<>();
                    for (int j = 0; j < indices.size(); j++) {
                        if (i != j) {
                            Integer otherIndex = indices.get(j);
                            Map<String, String> otherRow = rows.get(otherIndex);
                            String otherRowId = otherRow.get("_id");
                            if (otherRowId != null) {
                                duplicateOfIds.add(otherRowId);
                            }
                        }
                    }
                    
                    // Store duplicateOf as comma-separated string
                    if (!duplicateOfIds.isEmpty()) {
                        String duplicateOfValue = String.join(",", duplicateOfIds);
                        row.put("duplicateOf", duplicateOfValue);
                    }
                }
                log.debug("Found {} duplicate rows for identity: {} (stored duplicateOf references)", indices.size(), entry.getKey());
            } else {
                // Single row - mark as processed and remove duplicateOf if it exists
                Map<String, String> row = rows.get(indices.get(0));
                row.put("_status", RowStatus.PROCESSED.getValue());
                row.remove("duplicateOf");
            }
        }
        
        // Mark remaining rows without identity as processed
        for (Map<String, String> row : rows) {
            if (RowStatus.PENDING.getValue().equals(row.get("_status"))) {
                row.put("_status", RowStatus.PROCESSED.getValue());
            }
        }
        
        log.debug("Re-evaluation complete");
    }

    public CleansedCsvData recalculateDuplicateStatus(String fileId) {
        log.debug("Recalculating duplicate status for file: {}", fileId);
        
        CleansedCsvData cleansedData = getCleansedFileById(fileId);
        List<Map<String, String>> rows = cleansedData.getRows();
        
        // Re-evaluate duplicate status
        reEvaluateDuplicateStatus(rows);
        
        // Update metadata
        Map<String, Object> metadata = cleansedData.getCleansingMetadata();
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        int duplicateCount = rows.stream()
            .mapToInt(row -> RowStatus.DUPLICATE.getValue().equals(row.get("_status")) ? 1 : 0)
            .sum();
        metadata.put("duplicateCount", duplicateCount);
        metadata.put("processedCount", rows.size() - duplicateCount);
        cleansedData.setCleansingMetadata(metadata);
        cleansedData.setCleansedAt(Instant.now());
        
        // Save file metadata
        CleansedCsvData saved = cleansedCsvDataRepository.save(cleansedData);
        
        // Update individual row documents (legacy - using fileId as fileUploadId for backward compatibility)
        List<CsvRow> allRows = csvRowRepository.findByFileUploadIdOrderByRowIndexAsc(fileId);
        List<CsvRow> existingRows = allRows.stream()
            .filter(row -> !row.isDeleted())
            .collect(Collectors.toList());
        
        // Delete existing active rows and create new ones (soft-deleted rows are preserved)
        List<String> activeRowIds = existingRows.stream()
            .map(CsvRow::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        if (!activeRowIds.isEmpty()) {
            csvRowRepository.deleteAllById(activeRowIds);
        }
        
        // First pass: Create all rows and save to get IDs
        List<CsvRow> updatedCsvRows = new ArrayList<>();
        Instant processedAt = saved.getCleansedAt();
        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            String status = row.remove("_status");
            // Remove duplicateOf temporarily - we'll set it correctly after we have all IDs
            row.remove("duplicateOf");
            
            CsvRow csvRow = new CsvRow();
            csvRow.setFileUploadId(fileId);
            csvRow.setRowIndex(i);
            csvRow.setCleanData(new LinkedHashMap<>(row));
            csvRow.setHeaders(new ArrayList<>(saved.getHeaders()));
            csvRow.setIngestedAt(existingRows.isEmpty() ? Instant.now() : existingRows.get(0).getIngestedAt());
            csvRow.setIngestedBy(existingRows.isEmpty() ? saved.getCleansedBy() : existingRows.get(0).getIngestedBy());
            csvRow.setProcessedAt(processedAt);
            csvRow.setStatus(status);
            updatedCsvRows.add(csvRow);
        }
        
        // Save all rows to get IDs
        csvRowRepository.saveAll(updatedCsvRows);
        
        // Second pass: Update duplicateOf references now that we have all IDs
        // Re-group by identity to find duplicate groups
        Map<String, List<CsvRow>> identityGroups = new HashMap<>();
        for (CsvRow csvRow : updatedCsvRows) {
            if (csvRow.getCleanData() != null) {
                String identityKey = identityResolver.getIdentityKey(csvRow.getCleanData());
                if (identityKey != null) {
                    identityGroups.computeIfAbsent(identityKey, k -> new ArrayList<>()).add(csvRow);
                }
            }
        }
        
        // Update duplicateOf for each duplicate group
        for (Map.Entry<String, List<CsvRow>> entry : identityGroups.entrySet()) {
            List<CsvRow> group = entry.getValue();
            if (group.size() > 1) {
                // Multiple rows with same identity - set duplicateOf for each
                for (CsvRow row : group) {
                    List<String> duplicateOfIds = new ArrayList<>();
                    for (CsvRow otherRow : group) {
                        if (!row.getId().equals(otherRow.getId()) && otherRow.getId() != null) {
                            duplicateOfIds.add(otherRow.getId());
                        }
                    }
                    if (row.getCleanData() != null && !duplicateOfIds.isEmpty()) {
                        String duplicateOfValue = String.join(",", duplicateOfIds);
                        row.getCleanData().put("duplicateOf", duplicateOfValue);
                    }
                }
            }
        }
        
        // Save updated rows with duplicateOf references
        csvRowRepository.saveAll(updatedCsvRows);
        log.info("Recalculated duplicate status for file: {} ({} duplicates found)", fileId, duplicateCount);
        
        // Update FileUpload row count to reflect active rows
        updateFileUploadRowCount(fileId);
        
        return getCleansedFileById(fileId);
    }

    /**
     * Updates a single field in a single row. Only updates the specific row document.
     * Recalculates duplicate status for rows that were duplicates.
     * Returns information about which duplicate groups were resolved.
     */
    public Map<String, Object> updateSingleField(String fileId, int rowIndex, String fieldName, String fieldValue) {
        log.debug("Updating field {} in row {} for file: {}", fieldName, rowIndex, fileId);
        
        // Find the specific row document
        Optional<CsvRow> rowOpt = csvRowRepository.findByFileUploadIdAndRowIndex(fileId, rowIndex);
        if (rowOpt.isEmpty()) {
            throw new RuntimeException("Row not found at index " + rowIndex + " for file: " + fileId);
        }
        
        CsvRow updatedRow = rowOpt.get();
        boolean wasDuplicate = RowStatus.DUPLICATE.getValue().equals(updatedRow.getStatus());
        
        // Get old identity key before update (if this is an identity field)
        String oldIdentityKey = null;
        Map<String, String> data = updatedRow.getCleanData();
        if (data == null) {
            data = new LinkedHashMap<>();
            updatedRow.setCleanData(data);
        } else {
            oldIdentityKey = identityResolver.getIdentityKey(data);
        }
        
        // Update the field value in the row's clean data
        data.put(fieldName, fieldValue);
        
        // Get new identity key after update
        String newIdentityKey = identityResolver.getIdentityKey(data);
        
        // Save the updated row
        csvRowRepository.save(updatedRow);
        
        // Recalculate duplicate status only for affected identity groups
        List<Integer> resolvedDuplicateIndices = new ArrayList<>();
        
        // Only recalculate if identity changed or if this was a duplicate
        boolean identityChanged = !Objects.equals(oldIdentityKey, newIdentityKey);
        if (identityChanged || wasDuplicate) {
            Set<String> affectedIdentityKeys = new HashSet<>();
            if (oldIdentityKey != null) {
                affectedIdentityKeys.add(oldIdentityKey);
            }
            if (newIdentityKey != null && identityChanged) {
                affectedIdentityKeys.add(newIdentityKey);
            }
            
            // Load only rows that might be affected
            List<CsvRow> rowsToProcess = new ArrayList<>();
            if (!affectedIdentityKeys.isEmpty()) {
                // Load all rows to find those with affected identity keys
                // TODO: Optimize this further by querying directly by email field if possible
                List<CsvRow> allRows = csvRowRepository.findByFileUploadIdOrderByRowIndexAsc(fileId);
                for (CsvRow row : allRows) {
                    if (row.getCleanData() == null || row.isDeleted()) continue;
                    String rowIdentity = identityResolver.getIdentityKey(row.getCleanData());
                    if (affectedIdentityKeys.contains(rowIdentity)) {
                        rowsToProcess.add(row);
                    }
                }
            } else {
                // If no identity keys, just get the updated row
                rowsToProcess.add(updatedRow);
            }
            
            // Recalculate duplicates for affected rows
            Map<String, List<CsvRow>> identityGroups = new HashMap<>();
            for (CsvRow row : rowsToProcess) {
                if (row.getCleanData() == null) continue;
                // Check if soft-deleted (deleted flag is in cleanData)
                String deletedFlag = row.getCleanData().get("_deleted");
                boolean isDeleted = "true".equals(deletedFlag) || Boolean.TRUE.toString().equals(deletedFlag);
                if (isDeleted) continue;
                String identityKey = identityResolver.getIdentityKey(row.getCleanData());
                if (identityKey != null) {
                    identityGroups.computeIfAbsent(identityKey, k -> new ArrayList<>()).add(row);
                }
            }
            
            // Update status for affected identity groups
            for (Map.Entry<String, List<CsvRow>> entry : identityGroups.entrySet()) {
                List<CsvRow> group = entry.getValue();
                if (group.size() == 1) {
                    // Single row - mark as processed (resolved duplicate)
                    CsvRow row = group.get(0);
                    if (RowStatus.DUPLICATE.getValue().equals(row.getStatus())) {
                        row.setStatus(RowStatus.PROCESSED.getValue());
                        // Remove duplicateOf since it's no longer a duplicate
                        if (row.getCleanData() != null) {
                            row.getCleanData().remove("duplicateOf");
                        }
                        csvRowRepository.save(row);
                        resolvedDuplicateIndices.add(row.getRowIndex());
                    }
                } else if (group.size() > 1) {
                    // Multiple rows - mark all as duplicates and update duplicateOf references
                    for (CsvRow row : group) {
                        if (!RowStatus.DUPLICATE.getValue().equals(row.getStatus())) {
                            row.setStatus(RowStatus.DUPLICATE.getValue());
                        }
                        
                        // Update duplicateOf to include all other rows in this group
                        if (row.getCleanData() != null) {
                            List<String> duplicateOfIds = new ArrayList<>();
                            for (CsvRow otherRow : group) {
                                if (!row.getId().equals(otherRow.getId()) && otherRow.getId() != null) {
                                    duplicateOfIds.add(otherRow.getId());
                                }
                            }
                            if (!duplicateOfIds.isEmpty()) {
                                String duplicateOfValue = String.join(",", duplicateOfIds);
                                row.getCleanData().put("duplicateOf", duplicateOfValue);
                            } else {
                                row.getCleanData().remove("duplicateOf");
                            }
                        }
                        
                        csvRowRepository.save(row);
                    }
                }
            }
            
            // Mark affected rows without identity as processed if they were duplicates
            for (CsvRow row : rowsToProcess) {
                if (row.getCleanData() == null) continue;
                // Check if soft-deleted (deleted flag is in cleanData)
                String deletedFlag = row.getCleanData().get("_deleted");
                boolean isDeleted = "true".equals(deletedFlag) || Boolean.TRUE.toString().equals(deletedFlag);
                if (isDeleted) continue;
                String identityKey = identityResolver.getIdentityKey(row.getCleanData());
                if (identityKey == null && RowStatus.DUPLICATE.getValue().equals(row.getStatus())) {
                    row.setStatus(RowStatus.PROCESSED.getValue());
                    csvRowRepository.save(row);
                    resolvedDuplicateIndices.add(row.getRowIndex());
                }
            }
        }
        
        log.info("Updated field {} in row {} for file: {} (resolved {} duplicate groups)", 
            fieldName, rowIndex, fileId, resolvedDuplicateIndices.size());
        
        // Return only the updated row and affected rows (not the entire file)
        Map<String, Object> result = new HashMap<>();
        
        // Build updated row data
        Map<String, String> updatedRowData = updatedRow.getCleanData() != null 
            ? new LinkedHashMap<>(updatedRow.getCleanData())
            : (updatedRow.getRawData() != null ? new LinkedHashMap<>(updatedRow.getRawData()) : new LinkedHashMap<>());
        updatedRowData.put("_status", updatedRow.getStatus());
        
        // Get affected rows (rows that changed status)
        Map<Integer, Map<String, String>> affectedRows = new HashMap<>();
        affectedRows.put(rowIndex, updatedRowData);
        
        if (!resolvedDuplicateIndices.isEmpty()) {
            // Query only the specific rows that changed status
            for (Integer affectedIndex : resolvedDuplicateIndices) {
                if (affectedIndex != rowIndex) {
                    Optional<CsvRow> affectedRowOpt = csvRowRepository.findByFileUploadIdAndRowIndex(fileId, affectedIndex);
                    if (affectedRowOpt.isPresent()) {
                        CsvRow affectedRow = affectedRowOpt.get();
                        Map<String, String> affectedRowData = affectedRow.getCleanData() != null 
                            ? new LinkedHashMap<>(affectedRow.getCleanData())
                            : (affectedRow.getRawData() != null ? new LinkedHashMap<>(affectedRow.getRawData()) : new LinkedHashMap<>());
                        affectedRowData.put("_status", affectedRow.getStatus());
                        affectedRows.put(affectedIndex, affectedRowData);
                    }
                }
            }
        }
        
        result.put("updatedRow", updatedRowData);
        result.put("updatedRowIndex", rowIndex);
        result.put("affectedRows", affectedRows);
        result.put("resolvedDuplicateIndices", resolvedDuplicateIndices);
        result.put("hasResolvedDuplicates", !resolvedDuplicateIndices.isEmpty());
        
        return result;
    }

    public CleansedCsvData updateRowsAndRecalculateStatus(String fileId, List<Map<String, String>> updatedRows) {
        log.debug("Updating rows and recalculating duplicate status for file: {}", fileId);
        
        CleansedCsvData cleansedData = getCleansedFileById(fileId);
        
        // Validate row count matches
        if (updatedRows.size() != cleansedData.getRows().size()) {
            throw new RuntimeException("Row count mismatch: expected " + cleansedData.getRows().size() + 
                ", got " + updatedRows.size());
        }
        
        // Instead of updating all rows, update only changed rows
        List<CsvRow> existingRows = csvRowRepository.findByFileUploadIdOrderByRowIndexAsc(fileId);
        
        // Update only rows that have changed
        for (int i = 0; i < updatedRows.size() && i < existingRows.size(); i++) {
            Map<String, String> updatedRow = updatedRows.get(i);
            CsvRow existingRow = existingRows.get(i);
            
            // Check if row has changed
            boolean hasChanged = existingRow.getCleanData() == null || !existingRow.getCleanData().equals(updatedRow);
            if (hasChanged) {
                // Update only this row
                String status = updatedRow.remove("_status");
                existingRow.setCleanData(new LinkedHashMap<>(updatedRow));
                if (status != null) {
                    existingRow.setStatus(status);
                }
                existingRow.setProcessedAt(Instant.now());
                csvRowRepository.save(existingRow);
            }
        }
        
        // Re-evaluate duplicate status for all rows (but don't save all at once)
        List<Map<String, String>> rows = cleansedData.getRows();
        reEvaluateDuplicateStatus(rows);
        
        // Update only rows that changed status
        for (int i = 0; i < rows.size() && i < existingRows.size(); i++) {
            String newStatus = rows.get(i).get("_status");
            CsvRow row = existingRows.get(i);
            if (newStatus != null && !newStatus.equals(row.getStatus())) {
                row.setStatus(newStatus);
                csvRowRepository.save(row);
            }
        }
        
        // Update metadata
        Map<String, Object> metadata = cleansedData.getCleansingMetadata();
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        int duplicateCount = rows.stream()
            .mapToInt(row -> RowStatus.DUPLICATE.getValue().equals(row.get("_status")) ? 1 : 0)
            .sum();
        metadata.put("duplicateCount", duplicateCount);
        metadata.put("processedCount", rows.size() - duplicateCount);
        cleansedData.setCleansingMetadata(metadata);
        cleansedData.setCleansedAt(Instant.now());
        
        // Save file metadata only
        cleansedCsvDataRepository.save(cleansedData);
        
        log.info("Updated rows and recalculated duplicate status for file: {} ({} duplicates found)", fileId, duplicateCount);
        return getCleansedFileById(fileId);
    }

    /**
     * Exports cleansed CSV data as a CSV string.
     * Only exports rows with status "processed" (excludes duplicates and pending rows).
     * 
     * @param fileId The cleansed file ID
     * @return CSV content as a string
     */
    public String exportCsvAsString(String fileId) {
        log.debug("Exporting CSV for file: {}", fileId);
        
        CleansedCsvData cleansedData = getCleansedFileById(fileId);
        List<String> headers = cleansedData.getHeaders();
        List<Map<String, String>> allRows = cleansedData.getRows();
        
        // Filter to only processed rows (exclude duplicates and pending)
        List<Map<String, String>> processedRows = allRows.stream()
            .filter(row -> RowStatus.PROCESSED.getValue().equals(row.get("_status")))
            .collect(Collectors.toList());
        
        if (processedRows.isEmpty()) {
            log.warn("No processed rows found for file: {}", fileId);
            throw new RuntimeException("No processed rows to export");
        }
        
        try (StringWriter writer = new StringWriter();
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                 .setHeader(headers.toArray(new String[0]))
                 .build())) {
            
            // Write rows (excluding _status field)
            for (Map<String, String> row : processedRows) {
                List<String> values = new ArrayList<>();
                for (String header : headers) {
                    String value = row.get(header);
                    values.add(value != null ? value : "");
                }
                printer.printRecord(values);
            }
            
            printer.flush();
            String csvContent = writer.toString();
            log.info("Exported {} processed rows as CSV for file: {}", processedRows.size(), fileId);
            return csvContent;
        } catch (IOException e) {
            log.error("Error exporting CSV for file: {} - {}", fileId, e.getMessage(), e);
            throw new RuntimeException("Failed to export CSV: " + e.getMessage(), e);
        }
    }

    public void deleteFile(String fileId) {
        log.debug("Deleting CSV file with ID: {}", fileId);
        
        SecurityUser currentUser = userContext.getCurrentUser();
        String username = currentUser != null ? currentUser.getUsername() : null;
        String fileName = null;
        
        // Try to find as FileUpload first (new flow)
        Optional<FileUpload> fileUploadOpt = fileUploadRepository.findById(fileId);
        if (fileUploadOpt.isPresent()) {
            FileUpload fileUpload = fileUploadOpt.get();
            fileName = fileUpload.getFileName();
            
            // Check permissions: users can only delete their own files, admins can delete any
            if (!userContext.isAdmin() && (username == null || !username.equals(fileUpload.getUploadedBy()))) {
                log.warn("User {} attempted to delete file {} owned by {}", username, fileId, fileUpload.getUploadedBy());
                throw new RuntimeException("You do not have permission to delete this file");
            }
            
            // Delete all row documents associated with this FileUpload
            csvRowRepository.deleteByFileUploadId(fileId);
            log.debug("Deleted row documents for FileUpload: {} (with cascade delete)", fileId);
            
            // Delete any CleansedCsvData that might be associated (check by ID match)
            Optional<CleansedCsvData> cleansedDataOpt = cleansedCsvDataRepository.findById(fileId);
            if (cleansedDataOpt.isPresent()) {
                CleansedCsvData cleansedData = cleansedDataOpt.get();
                // Delete chunks if any
                csvDataChunkRepository.deleteByFileIdAndChunkType(cleansedData.getId(), "cleansed");
                cleansedCsvDataRepository.deleteById(cleansedData.getId());
                log.debug("Deleted cleansed CSV data with ID: {}", cleansedData.getId());
            }
            
            // Delete chunks (for backward compatibility)
            csvDataChunkRepository.deleteByFileIdAndChunkType(fileId, "raw");
            csvDataChunkRepository.deleteByFileIdAndChunkType(fileId, "cleansed");
            
            // Delete the FileUpload
            fileUploadRepository.deleteById(fileId);
            log.info("Deleted CSV file with ID: {} (fileName: {})", fileId, fileName);
            return;
        }
        
        // Fallback: Try to find as RawCsvData (legacy flow)
        Optional<RawCsvData> rawDataOpt = rawCsvDataRepository.findById(fileId);
        if (rawDataOpt.isPresent()) {
            RawCsvData rawData = rawDataOpt.get();
            fileName = rawData.getFileName();
            
            // Check permissions: users can only delete their own files, admins can delete any
            if (!userContext.isAdmin() && (username == null || !username.equals(rawData.getIngestedBy()))) {
                log.warn("User {} attempted to delete file {} owned by {}", username, fileId, rawData.getIngestedBy());
                throw new RuntimeException("You do not have permission to delete this file");
            }
            
            // Delete associated cleansed data, chunks, and row documents
            List<CleansedCsvData> cleansedDataList = cleansedCsvDataRepository.findAll().stream()
                .filter(data -> fileId.equals(data.getRawCsvDataId()))
                .collect(Collectors.toList());
            
            for (CleansedCsvData cleansedData : cleansedDataList) {
                // Delete row documents (legacy - using cleansedData ID as fileUploadId for backward compatibility)
                csvRowRepository.deleteByFileUploadId(cleansedData.getId());
                // Delete chunks (for backward compatibility)
                csvDataChunkRepository.deleteByFileIdAndChunkType(cleansedData.getId(), "cleansed");
                cleansedCsvDataRepository.deleteById(cleansedData.getId());
                log.debug("Deleted cleansed CSV data with ID: {}", cleansedData.getId());
            }
            
            // Delete row documents (legacy - using fileId as fileUploadId for backward compatibility)
            csvRowRepository.deleteByFileUploadId(fileId);
            // Delete raw data chunks (for backward compatibility)
            csvDataChunkRepository.deleteByFileIdAndChunkType(fileId, "raw");
            
            // Delete raw data
            rawCsvDataRepository.deleteById(fileId);
            log.info("Deleted CSV file with ID: {} (fileName: {})", fileId, fileName);
            return;
        }
        
        // File not found in either collection
        throw new RuntimeException("CSV file not found with ID: " + fileId);
    }
    
    // Note: Raw and clean data are now in the same document, so no need for separate raw/clean row references
}

