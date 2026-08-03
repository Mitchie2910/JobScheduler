package com.dilamme.jobscheduler.repository;

import com.dilamme.jobscheduler.entities.Job;
import com.dilamme.jobscheduler.enums.JobStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobRepository extends JpaRepository<Job, UUID> {

  Optional<Job> findByIdAndStatusIn(UUID id, Collection<JobStatus> statuses);

  List<Job> findByStatusAndScheduledTimeLessThanEqual(JobStatus status, Instant scheduledTime);

  @Query(
      value =
          """
        SELECT *
        FROM jobs
        WHERE status = 'PENDING'
          AND scheduled_time <= NOW()
        ORDER BY priority, scheduled_time, created_at
        FOR UPDATE SKIP LOCKED
        LIMIT :limit
        """,
      nativeQuery = true)
  List<Job> findJobsToClaim(@Param("limit") int limit);
}
