package com.isofuture.uptime.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.isofuture.uptime.entity.CsvRow;

@Repository
public interface CsvRowRepository extends MongoRepository<CsvRow, String> {
    
    List<CsvRow> findByFileUploadIdOrderByRowIndexAsc(String fileUploadId);
    
    Optional<CsvRow> findByFileUploadIdAndRowIndex(String fileUploadId, int rowIndex);
    
    void deleteByFileUploadId(String fileUploadId);
    
    long countByFileUploadId(String fileUploadId);
    
    // Find rows that have clean data (for querying cleansed rows)
    List<CsvRow> findByFileUploadIdAndCleanDataIsNotNullOrderByRowIndexAsc(String fileUploadId);
}

