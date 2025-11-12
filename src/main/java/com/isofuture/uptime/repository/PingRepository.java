package com.isofuture.uptime.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.isofuture.uptime.entity.Ping;
import com.isofuture.uptime.entity.User;

public interface PingRepository extends JpaRepository<Ping, Long> {

    List<Ping> findByOwner(User owner);

    @Query("select p from Ping p where p.inProgress = false and (p.nextCheckAt is null or p.nextCheckAt <= :now) order by case when p.nextCheckAt is null then 0 else 1 end, p.nextCheckAt, p.id")
    List<Ping> findReadyForCheck(@Param("now") Instant now);

    Optional<Ping> findByIdAndOwnerId(Long id, Long ownerId);

    List<Ping> findByInProgressTrueOrderByUpdatedAtAsc();
}

