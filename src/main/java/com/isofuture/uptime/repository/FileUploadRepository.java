package com.isofuture.uptime.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.isofuture.uptime.entity.FileUpload;

@Repository
public interface FileUploadRepository extends MongoRepository<FileUpload, String> {
    
    List<FileUpload> findByUploadedBy(String uploadedBy);
    
    List<FileUpload> findByStatus(String status);
}

