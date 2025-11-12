package com.isofuture.uptime.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.isofuture.uptime.entity.CheckResult;
import com.isofuture.uptime.entity.MonitoredUrl;

public interface CheckResultRepository extends JpaRepository<CheckResult, Long> {
    List<CheckResult> findByMonitoredUrlOrderByCheckedAtDesc(MonitoredUrl monitoredUrl);
}

