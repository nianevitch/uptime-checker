package com.isofuture.uptime.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.isofuture.uptime.entity.CsvDataChunk;

@Repository
public interface CsvDataChunkRepository extends MongoRepository<CsvDataChunk, String> {
    List<CsvDataChunk> findByFileIdAndChunkTypeOrderByChunkIndexAsc(String fileId, String chunkType);
    void deleteByFileIdAndChunkType(String fileId, String chunkType);
}

