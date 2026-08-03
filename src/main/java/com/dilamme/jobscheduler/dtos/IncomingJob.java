package com.dilamme.jobscheduler.dtos;

import com.dilamme.jobscheduler.enums.JobType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record IncomingJob(
    JobType type,
    int priority,
    String payload,
    int interval,
    List<UUID> dependencies,
    Instant scheduledTime) {
  public IncomingJob {
    dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
  }
}
