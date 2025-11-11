package com.isofuture.uptime.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.isofuture.uptime.entity.CheckResultEntity;
import com.isofuture.uptime.entity.MonitoredUrlEntity;

public interface CheckResultRepository extends JpaRepository<CheckResultEntity, Long> {
    List<CheckResultEntity> findByMonitoredUrlOrderByCheckedAtDesc(MonitoredUrlEntity monitoredUrl);
}

