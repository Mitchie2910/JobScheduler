package com.dilamme.jobscheduler.service;

import com.dilamme.jobscheduler.dtos.IncomingJob;
import com.dilamme.jobscheduler.entities.Job;
import com.dilamme.jobscheduler.entities.JobDependency;
import com.dilamme.jobscheduler.enums.JobStatus;
import com.dilamme.jobscheduler.events.JobStatusChangedEvent;
import com.dilamme.jobscheduler.repository.JobDependencyRepository;
import com.dilamme.jobscheduler.repository.JobRepository;
import com.dilamme.jobscheduler.scheduler.DispatchQueue;
import com.dilamme.jobscheduler.scheduler.Scheduler;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JobService {

  private final Scheduler scheduler;
  private final JobRepository repository;
  private final JobDependencyRepository dependencyRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final DispatchQueue dispatchQueue;

  public UUID createJob(IncomingJob incomingJob) {
    Job job =
        Job.builder()
            .id(UUID.randomUUID())
            .priority(incomingJob.priority())
            .payload(incomingJob.payload())
            .type(incomingJob.type())
            .status(JobStatus.PENDING)
            .interval(incomingJob.interval())
            .scheduledTime(incomingJob.scheduledTime())
            .build();

    List<UUID> dependencies = incomingJob.dependencies();

    for (UUID dependency : dependencies) {
      JobDependency dependencyEntry =
          JobDependency.builder()
              .id(UUID.randomUUID())
              .jobId(job.getId())
              .dependsOnJobId(dependency)
              .build();
      dependencyRepository.save(dependencyEntry);
    }

    eventPublisher.publishEvent(JobStatusChangedEvent.of(job.getId(), JobStatus.PENDING));
    repository.save(job);

    return job.getId();
  }

  public void cancelJob(UUID jobId) {
    scheduler.jobCanceled(jobId);
  }

  public void retryJob(UUID jobId) {
    Job job = repository.findById(jobId).orElseThrow();

    if (job.getStatus() != JobStatus.FAILED) {
      throw new IllegalArgumentException("Not a failed job: " + jobId);
    }

    dispatchQueue.submit(job);
  }
}
