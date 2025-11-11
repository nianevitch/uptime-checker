package com.isofuture.uptime.mapper;

import java.util.List;

import com.isofuture.uptime.dto.CheckResultDto;
import com.isofuture.uptime.dto.MonitoredUrlResponse;
import com.isofuture.uptime.entity.CheckResultEntity;
import com.isofuture.uptime.entity.MonitoredUrlEntity;

public interface MonitoredUrlMapper {

    MonitoredUrlResponse toResponse(MonitoredUrlEntity entity, List<CheckResultDto> recentResults);

    CheckResultDto toDto(CheckResultEntity entity);
}

