package com.dilamme.jobscheduler.events;

import com.dilamme.jobscheduler.enums.JobStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Fired whenever a job's status changes. Kept independent of the Spring ApplicationEvent base class
 * so it can be reused as a plain DTO for the SSE payload without any Spring-specific baggage
 * leaking to the client.
 */
public record JobStatusChangedEvent(UUID jobId, JobStatus status, Instant occurredAt) {
  public static JobStatusChangedEvent of(UUID jobId, JobStatus status) {
    return new JobStatusChangedEvent(jobId, status, Instant.now());
  }
}
