package com.isofuture.uptime.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.isofuture.uptime.entity.CleansedCsvData;

@Repository
public interface CleansedCsvDataRepository extends MongoRepository<CleansedCsvData, String> {
}





