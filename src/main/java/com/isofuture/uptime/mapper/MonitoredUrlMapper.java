package com.isofuture.uptime.mapper;

import java.util.List;

import com.isofuture.uptime.dto.CheckResultDto;
import com.isofuture.uptime.dto.MonitoredUrlResponse;
import com.isofuture.uptime.entity.CheckResult;
import com.isofuture.uptime.entity.MonitoredUrl;

public interface MonitoredUrlMapper {

    MonitoredUrlResponse toResponse(MonitoredUrl entity, List<CheckResultDto> recentResults);

    CheckResultDto toDto(CheckResult entity);
}

