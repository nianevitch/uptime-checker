package com.isofuture.uptime.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.isofuture.uptime.entity.MonitoredUrl;
import com.isofuture.uptime.entity.User;

public interface MonitoredUrlRepository extends JpaRepository<MonitoredUrl, Long> {

    List<MonitoredUrl> findByOwner(User owner);

    @Query("select m from MonitoredUrl m where m.inProgress = false and (m.nextCheckAt is null or m.nextCheckAt <= :now) order by case when m.nextCheckAt is null then 0 else 1 end, m.nextCheckAt, m.id")
    List<MonitoredUrl> findReadyForCheck(@Param("now") Instant now);

    Optional<MonitoredUrl> findByIdAndOwnerId(Long id, Long ownerId);

    List<MonitoredUrl> findByInProgressTrueOrderByUpdatedAtAsc();
}

