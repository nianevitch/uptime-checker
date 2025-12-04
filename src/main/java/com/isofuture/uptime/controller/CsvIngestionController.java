package com.isofuture.uptime.controller;

import java.net.URI;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.isofuture.uptime.entity.CleansedCsvData;
import com.isofuture.uptime.entity.FileUpload;
import com.isofuture.uptime.entity.RawCsvData;
import com.isofuture.uptime.service.CsvIngestionService;

@RestController
@RequestMapping("/api/csv/ingest")
public class CsvIngestionController {

    private static final Logger log = LoggerFactory.getLogger(CsvIngestionController.class);

    private final CsvIngestionService csvIngestionService;

    public CsvIngestionController(CsvIngestionService csvIngestionService) {
        this.csvIngestionService = csvIngestionService;
    }

    @GetMapping
    public ResponseEntity<List<CsvFileSummary>> listFiles() {
        log.debug("GET /api/csv/ingest - Listing CSV files");
        List<FileUpload> files = csvIngestionService.listUserFiles();
        List<CsvFileSummary> summaries = files.stream()
            .map(file -> new CsvFileSummary(
                file.getId(),
                file.getFileName(),
                file.getHeaders() != null ? file.getHeaders().size() : 0,
                file.getRowCount(),
                file.getUploadedAt(),
                file.getUploadedBy()
            ))
            .toList();
        log.info("GET /api/csv/ingest - Found {} CSV files", summaries.size());
        return ResponseEntity.ok(summaries);
    }

    @PostMapping
    public ResponseEntity<CsvIngestionResponse> ingestCsv(
        @RequestParam("file") MultipartFile file
    ) {
        log.debug("POST /api/csv/ingest - Ingesting CSV file: {}", file.getOriginalFilename());
        
        if (file.isEmpty()) {
            log.warn("POST /api/csv/ingest - Empty file received");
            return ResponseEntity.badRequest()
                .body(new CsvIngestionResponse(null, "File is empty", false));
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase().endsWith(".csv")) {
            log.warn("POST /api/csv/ingest - Invalid file type: {}", fileName);
            return ResponseEntity.badRequest()
                .body(new CsvIngestionResponse(null, "File must be a CSV file", false));
        }

        try {
            FileUpload fileUpload = csvIngestionService.ingestRawCsv(
                file.getInputStream(), 
                fileName, 
                file.getSize()
            );
            log.info("POST /api/csv/ingest - CSV file ingested successfully: {} (FileUpload ID: {})", fileName, fileUpload.getId());
            
            CsvIngestionResponse response = new CsvIngestionResponse(
                fileUpload.getId(),
                "CSV file ingested, cleansed, and deduplicated successfully",
                true
            );
            
            return ResponseEntity
                .created(URI.create("/api/csv/ingest/" + fileUpload.getId()))
                .body(response);
        } catch (Exception e) {
            log.error("POST /api/csv/ingest - Error ingesting CSV file: {} - {}", fileName, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(new CsvIngestionResponse(null, "Failed to ingest CSV file: " + e.getMessage(), false));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<CsvFileData> getFileData(@PathVariable("id") String id) {
        log.debug("GET /api/csv/ingest/{} - Getting CSV file data", id);
        
        try {
            CleansedCsvData cleansedData = csvIngestionService.getCleansedFileById(id);
            CsvFileData fileData = new CsvFileData(
                cleansedData.getId(), // Use cleansed data ID
                cleansedData.getFileName(),
                cleansedData.getHeaders() != null ? cleansedData.getHeaders() : List.of(),
                cleansedData.getRows() != null ? cleansedData.getRows() : List.of()
            );
            log.info("GET /api/csv/ingest/{} - Returning CSV file data with {} rows", id, fileData.getRows().size());
            return ResponseEntity.ok(fileData);
        } catch (RuntimeException e) {
            log.error("GET /api/csv/ingest/{} - Error getting CSV file data: {}", id, e.getMessage(), e);
            if (e.getMessage().contains("not found")) {
                return ResponseEntity.notFound().build();
            } else if (e.getMessage().contains("permission")) {
                return ResponseEntity.status(403).build();
            }
            return ResponseEntity.internalServerError().build();
        }
    }

    @PutMapping("/{id}/merge")
    public ResponseEntity<Map<String, Object>> mergeDuplicateRows(
        @PathVariable("id") String id,
        @RequestBody MergeRequest request
    ) {
        log.debug("PUT /api/csv/ingest/{}/merge - Merging duplicate rows", id);
        
        try {
            String mergeStrategy = request.getMergeStrategy() != null ? request.getMergeStrategy() : "manual";
            Map<String, Object> result = csvIngestionService.mergeDuplicateRows(
                id, 
                request.getRowIndices(), 
                request.getRowIds(),
                request.getMergedRow(),
                request.isUseCustomIdentity() != null && request.isUseCustomIdentity(),
                mergeStrategy
            );
            int rowCount = request.getRowIds() != null && !request.getRowIds().isEmpty() 
                ? request.getRowIds().size() 
                : (request.getRowIndices() != null ? request.getRowIndices().size() : 0);
            log.info("PUT /api/csv/ingest/{}/merge - Successfully merged {} rows using strategy: {}", id, rowCount, mergeStrategy);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            log.error("PUT /api/csv/ingest/{}/merge - Error merging rows: {}", id, e.getMessage(), e);
            if (e.getMessage().contains("not found")) {
                return ResponseEntity.notFound().build();
            } else if (e.getMessage().contains("permission")) {
                return ResponseEntity.status(403).build();
            } else if (e.getMessage().contains("Invalid")) {
                return ResponseEntity.badRequest().build();
            }
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{id}/auto-merge-all")
    public ResponseEntity<Map<String, Object>> autoMergeAllDuplicates(
        @PathVariable("id") String id,
        @RequestBody AutoMergeAllRequest request
    ) {
        log.debug("POST /api/csv/ingest/{}/auto-merge-all - Auto-merging all duplicate groups", id);
        
        try {
            // Convert DuplicateGroup objects to Map format for service
            List<Map<String, Object>> groupsAsMaps = new ArrayList<>();
            for (DuplicateGroup group : request.getDuplicateGroups()) {
                Map<String, Object> groupMap = new HashMap<>();
                if (group.getRowIds() != null && !group.getRowIds().isEmpty()) {
                    groupMap.put("rowIds", group.getRowIds());
                }
                if (group.getRowIndices() != null && !group.getRowIndices().isEmpty()) {
                    groupMap.put("rowIndices", group.getRowIndices());
                }
                groupsAsMaps.add(groupMap);
            }
            
            Map<String, Object> result = csvIngestionService.autoMergeAllDuplicateGroups(
                id,
                groupsAsMaps,
                request.getMergeStrategy()
            );
            log.info("POST /api/csv/ingest/{}/auto-merge-all - Successfully processed {} duplicate groups using strategy: {}", 
                id, request.getDuplicateGroups().size(), request.getMergeStrategy());
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            log.error("POST /api/csv/ingest/{}/auto-merge-all - Error auto-merging: {}", id, e.getMessage(), e);
            if (e.getMessage().contains("not found")) {
                return ResponseEntity.notFound().build();
            } else if (e.getMessage().contains("permission")) {
                return ResponseEntity.status(403).build();
            } else if (e.getMessage().contains("Invalid")) {
                return ResponseEntity.badRequest().build();
            }
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{id}/merge-delete")
    public ResponseEntity<CsvFileData> batchMergeDelete(
        @PathVariable("id") String id,
        @RequestBody List<MergeDeleteRequest> requests
    ) {
        log.debug("POST /api/csv/ingest/{}/merge-delete - Processing {} merge-delete requests", id, requests.size());
        
        try {
            CleansedCsvData cleansedData = csvIngestionService.batchMergeDelete(id, requests);
            CsvFileData fileData = new CsvFileData(
                cleansedData.getId(),
                cleansedData.getFileName(),
                cleansedData.getHeaders() != null ? cleansedData.getHeaders() : List.of(),
                cleansedData.getRows() != null ? cleansedData.getRows() : List.of()
            );
            log.info("POST /api/csv/ingest/{}/merge-delete - Successfully processed {} merge-delete requests", id, requests.size());
            return ResponseEntity.ok(fileData);
        } catch (RuntimeException e) {
            log.error("POST /api/csv/ingest/{}/merge-delete - Error processing merge-delete: {}", id, e.getMessage(), e);
            if (e.getMessage().contains("not found")) {
                return ResponseEntity.notFound().build();
            } else if (e.getMessage().contains("permission")) {
                return ResponseEntity.status(403).build();
            } else if (e.getMessage().contains("Invalid")) {
                return ResponseEntity.badRequest().build();
            }
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{id}/recalculate-status")
    public ResponseEntity<CsvFileData> recalculateDuplicateStatus(@PathVariable("id") String id) {
        log.debug("POST /api/csv/ingest/{}/recalculate-status - Recalculating duplicate status", id);
        
        try {
            CleansedCsvData updatedData = csvIngestionService.recalculateDuplicateStatus(id);
            CsvFileData fileData = new CsvFileData(
                updatedData.getId(), // Use cleansed data ID
                updatedData.getFileName(),
                updatedData.getHeaders() != null ? updatedData.getHeaders() : List.of(),
                updatedData.getRows() != null ? updatedData.getRows() : List.of()
            );
            log.info("POST /api/csv/ingest/{}/recalculate-status - Successfully recalculated duplicate status", id);
            return ResponseEntity.ok(fileData);
        } catch (RuntimeException e) {
            log.error("POST /api/csv/ingest/{}/recalculate-status - Error: {}", id, e.getMessage(), e);
            if (e.getMessage().contains("not found")) {
                return ResponseEntity.notFound().build();
            } else if (e.getMessage().contains("permission")) {
                return ResponseEntity.status(403).build();
            }
            return ResponseEntity.internalServerError().build();
        }
    }

    @PutMapping("/{id}/update-field")
    public ResponseEntity<Map<String, Object>> updateSingleField(
        @PathVariable("id") String id,
        @RequestBody Map<String, Object> request
    ) {
        log.debug("PUT /api/csv/ingest/{}/update-field - Updating single field", id);
        
        try {
            Integer rowIndex = (Integer) request.get("rowIndex");
            String fieldName = (String) request.get("fieldName");
            String fieldValue = (String) request.get("fieldValue");
            
            if (rowIndex == null || fieldName == null) {
                return ResponseEntity.badRequest().build();
            }
            
            Map<String, Object> result = csvIngestionService.updateSingleField(id, rowIndex, fieldName, fieldValue);
            
            // Return only the delta (updated row and affected rows)
            Map<String, Object> response = new HashMap<>();
            response.put("updatedRow", result.get("updatedRow"));
            response.put("updatedRowIndex", result.get("updatedRowIndex"));
            response.put("affectedRows", result.get("affectedRows"));
            response.put("resolvedDuplicateIndices", result.get("resolvedDuplicateIndices"));
            response.put("hasResolvedDuplicates", result.get("hasResolvedDuplicates"));
            
            log.info("PUT /api/csv/ingest/{}/update-field - Successfully updated field {} in row {} (resolved {} duplicates)", 
                id, fieldName, rowIndex, ((List<?>) result.get("resolvedDuplicateIndices")).size());
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("PUT /api/csv/ingest/{}/update-field - Error: {}", id, e.getMessage(), e);
            if (e.getMessage().contains("not found")) {
                return ResponseEntity.notFound().build();
            } else if (e.getMessage().contains("permission")) {
                return ResponseEntity.status(403).build();
            }
            return ResponseEntity.internalServerError().build();
        }
    }

    @PutMapping("/{id}/update-rows")
    public ResponseEntity<CsvFileData> updateRowsAndRecalculateStatus(
        @PathVariable("id") String id,
        @RequestBody Map<String, Object> request
    ) {
        log.debug("PUT /api/csv/ingest/{}/update-rows - Updating rows and recalculating status", id);
        
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, String>> rows = (List<Map<String, String>>) request.get("rows");
            if (rows == null) {
                return ResponseEntity.badRequest().build();
            }
            
            CleansedCsvData updatedData = csvIngestionService.updateRowsAndRecalculateStatus(id, rows);
            CsvFileData fileData = new CsvFileData(
                updatedData.getId(), // Use cleansed data ID
                updatedData.getFileName(),
                updatedData.getHeaders() != null ? updatedData.getHeaders() : List.of(),
                updatedData.getRows() != null ? updatedData.getRows() : List.of()
            );
            log.info("PUT /api/csv/ingest/{}/update-rows - Successfully updated rows and recalculated status", id);
            return ResponseEntity.ok(fileData);
        } catch (RuntimeException e) {
            log.error("PUT /api/csv/ingest/{}/update-rows - Error: {}", id, e.getMessage(), e);
            if (e.getMessage().contains("not found")) {
                return ResponseEntity.notFound().build();
            } else if (e.getMessage().contains("permission")) {
                return ResponseEntity.status(403).build();
            }
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<String> exportCsv(@PathVariable("id") String id) {
        log.debug("GET /api/csv/ingest/{}/export - Exporting CSV file", id);
        
        try {
            String csvContent = csvIngestionService.exportCsvAsString(id);
            
            // Get file name for download
            CleansedCsvData cleansedData = csvIngestionService.getCleansedFileById(id);
            String fileName = cleansedData.getFileName();
            String exportFileName = fileName != null && fileName.endsWith(".csv") 
                ? fileName.replace(".csv", "_processed.csv")
                : fileName + "_processed.csv";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            headers.setContentDispositionFormData("attachment", exportFileName);
            
            log.info("GET /api/csv/ingest/{}/export - CSV exported successfully", id);
            return ResponseEntity.ok()
                .headers(headers)
                .body(csvContent);
        } catch (RuntimeException e) {
            log.error("GET /api/csv/ingest/{}/export - Error exporting CSV: {}", id, e.getMessage(), e);
            if (e.getMessage().contains("not found")) {
                return ResponseEntity.notFound().build();
            } else if (e.getMessage().contains("permission")) {
                return ResponseEntity.status(403).build();
            }
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFile(@PathVariable("id") String id) {
        log.debug("DELETE /api/csv/ingest/{} - Deleting CSV file", id);
        
        try {
            csvIngestionService.deleteFile(id);
            log.info("DELETE /api/csv/ingest/{} - CSV file deleted successfully", id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            log.error("DELETE /api/csv/ingest/{} - Error deleting CSV file: {}", id, e.getMessage(), e);
            if (e.getMessage().contains("not found")) {
                return ResponseEntity.notFound().build();
            } else if (e.getMessage().contains("permission")) {
                return ResponseEntity.status(403).build();
            }
            return ResponseEntity.internalServerError().build();
        }
    }

    public static class CsvIngestionResponse {
        private String id;
        private String message;
        private boolean success;

        public CsvIngestionResponse(String id, String message, boolean success) {
            this.id = id;
            this.message = message;
            this.success = success;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }
    }

    public static class CsvFileSummary {
        private String id;
        private String fileName;
        private int columnCount;
        private int rowCount;
        private java.time.Instant ingestedAt;
        private String ingestedBy;

        public CsvFileSummary(String id, String fileName, int columnCount, int rowCount, 
                java.time.Instant ingestedAt, String ingestedBy) {
            this.id = id;
            this.fileName = fileName;
            this.columnCount = columnCount;
            this.rowCount = rowCount;
            this.ingestedAt = ingestedAt;
            this.ingestedBy = ingestedBy;
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

        public int getColumnCount() {
            return columnCount;
        }

        public void setColumnCount(int columnCount) {
            this.columnCount = columnCount;
        }

        public int getRowCount() {
            return rowCount;
        }

        public void setRowCount(int rowCount) {
            this.rowCount = rowCount;
        }

        public java.time.Instant getIngestedAt() {
            return ingestedAt;
        }

        public void setIngestedAt(java.time.Instant ingestedAt) {
            this.ingestedAt = ingestedAt;
        }

        public String getIngestedBy() {
            return ingestedBy;
        }

        public void setIngestedBy(String ingestedBy) {
            this.ingestedBy = ingestedBy;
        }
    }

    public static class CsvFileData {
        private String id;
        private String fileName;
        private List<String> headers;
        private List<Map<String, String>> rows;

        public CsvFileData(String id, String fileName, List<String> headers, List<Map<String, String>> rows) {
            this.id = id;
            this.fileName = fileName;
            this.headers = headers;
            this.rows = rows;
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
    }

    public static class MergeRequest {
        private List<Integer> rowIndices; // Deprecated: use rowIds instead for better performance
        private List<String> rowIds; // Row IDs (preferred - stable across index shifts)
        private Map<String, String> mergedRow;
        private Boolean useCustomIdentity;
        private String mergeStrategy; // "manual", "merge-delete", "auto-merge"

        public MergeRequest() {
        }

        public List<Integer> getRowIndices() {
            return rowIndices;
        }

        public void setRowIndices(List<Integer> rowIndices) {
            this.rowIndices = rowIndices;
        }

        public List<String> getRowIds() {
            return rowIds;
        }

        public void setRowIds(List<String> rowIds) {
            this.rowIds = rowIds;
        }

        public Map<String, String> getMergedRow() {
            return mergedRow;
        }

        public void setMergedRow(Map<String, String> mergedRow) {
            this.mergedRow = mergedRow;
        }

        public Boolean isUseCustomIdentity() {
            return useCustomIdentity;
        }

        public void setUseCustomIdentity(Boolean useCustomIdentity) {
            this.useCustomIdentity = useCustomIdentity;
        }

        public String getMergeStrategy() {
            return mergeStrategy;
        }

        public void setMergeStrategy(String mergeStrategy) {
            this.mergeStrategy = mergeStrategy;
        }
    }

    public static class AutoMergeAllRequest {
        private List<DuplicateGroup> duplicateGroups;
        private String mergeStrategy;

        public List<DuplicateGroup> getDuplicateGroups() {
            return duplicateGroups;
        }

        public void setDuplicateGroups(List<DuplicateGroup> duplicateGroups) {
            this.duplicateGroups = duplicateGroups;
        }

        public String getMergeStrategy() {
            return mergeStrategy;
        }

        public void setMergeStrategy(String mergeStrategy) {
            this.mergeStrategy = mergeStrategy;
        }
    }

    public static class DuplicateGroup {
        private List<String> rowIds;
        private List<Integer> rowIndices;

        public List<String> getRowIds() {
            return rowIds;
        }

        public void setRowIds(List<String> rowIds) {
            this.rowIds = rowIds;
        }

        public List<Integer> getRowIndices() {
            return rowIndices;
        }

        public void setRowIndices(List<Integer> rowIndices) {
            this.rowIndices = rowIndices;
        }
    }

    public static class MergeDeleteRequest {
        private String mode; // "merge-delete"
        private String id1;
        private String id2;
        private List<String> ids; // For more than 2 IDs

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getId1() {
            return id1;
        }

        public void setId1(String id1) {
            this.id1 = id1;
        }

        public String getId2() {
            return id2;
        }

        public void setId2(String id2) {
            this.id2 = id2;
        }

        public List<String> getIds() {
            return ids;
        }

        public void setIds(List<String> ids) {
            this.ids = ids;
        }

        /**
         * Collects all row IDs from this request (id1, id2, and ids list).
         */
        public List<String> getAllRowIds() {
            List<String> allIds = new ArrayList<>();
            if (id1 != null && !id1.trim().isEmpty()) {
                allIds.add(id1);
            }
            if (id2 != null && !id2.trim().isEmpty()) {
                allIds.add(id2);
            }
            if (ids != null) {
                allIds.addAll(ids);
            }
            return allIds;
        }
    }
}

