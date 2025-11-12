package com.isofuture.uptime.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.isofuture.uptime.dto.CheckResultDto;
import com.isofuture.uptime.dto.MonitoredUrlResponse;
import com.isofuture.uptime.entity.CheckResult;
import com.isofuture.uptime.entity.MonitoredUrl;
import com.isofuture.uptime.entity.User;

@Component
public class MonitoredUrlMapperImpl implements MonitoredUrlMapper {

    @Override
    public MonitoredUrlResponse toResponse(MonitoredUrl entity, List<CheckResultDto> recentResults) {
        if (entity == null) {
            return null;
        }

        MonitoredUrlResponse response = new MonitoredUrlResponse();
        response.setId(entity.getId());

        User owner = entity.getOwner();
        response.setOwnerId(owner != null ? owner.getId() : null);

        response.setLabel(entity.getLabel());
        response.setUrl(entity.getUrl());
        response.setFrequencyMinutes(entity.getFrequencyMinutes());
        response.setNextCheckAt(entity.getNextCheckAt());
        response.setInProgress(entity.isInProgress());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        response.setRecentResults(recentResults);

        return response;
    }

    @Override
    public CheckResultDto toDto(CheckResult entity) {
        if (entity == null) {
            return null;
        }

        CheckResultDto dto = new CheckResultDto();
        dto.setId(entity.getId());
        dto.setHttpCode(entity.getHttpCode());
        dto.setErrorMessage(entity.getErrorMessage());
        dto.setResponseTimeMs(entity.getResponseTimeMs());
        dto.setCheckedAt(entity.getCheckedAt());

        return dto;
    }
}

