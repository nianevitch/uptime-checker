package com.isofuture.uptime.mapper;

import java.util.List;

import com.isofuture.uptime.dto.CheckResultDto;
import com.isofuture.uptime.dto.PingResponse;
import com.isofuture.uptime.entity.CheckResult;
import com.isofuture.uptime.entity.Ping;

public interface PingMapper {

    PingResponse toResponse(Ping entity, List<CheckResultDto> recentResults);

    CheckResultDto toDto(CheckResult entity);
}

