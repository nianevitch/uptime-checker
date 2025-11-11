package com.isofuture.uptime.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.isofuture.uptime.entity.MonitoredUrlEntity;
import com.isofuture.uptime.entity.UserEntity;

public interface MonitoredUrlRepository extends JpaRepository<MonitoredUrlEntity, Long> {

    List<MonitoredUrlEntity> findByOwner(UserEntity owner);

    @Query("select m from MonitoredUrlEntity m where m.inProgress = false and (m.nextCheckAt is null or m.nextCheckAt <= :now) order by case when m.nextCheckAt is null then 0 else 1 end, m.nextCheckAt, m.id")
    List<MonitoredUrlEntity> findReadyForCheck(@Param("now") Instant now);

    Optional<MonitoredUrlEntity> findByIdAndOwnerId(Long id, Long ownerId);

    List<MonitoredUrlEntity> findByInProgressTrueOrderByUpdatedAtAsc();
}

