package com.isofuture.uptime.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.isofuture.uptime.entity.RawCsvData;

@Repository
public interface RawCsvDataRepository extends MongoRepository<RawCsvData, String> {
}





