package com.dilamme.jobscheduler.scheduler;

import static com.dilamme.jobscheduler.enums.JobStatus.CANCELED;
import static com.dilamme.jobscheduler.enums.JobStatus.PROCESSING;

import com.dilamme.jobscheduler.entities.Job;
import com.dilamme.jobscheduler.enums.JobStatus;
import com.dilamme.jobscheduler.events.JobStatusChangedEvent;
import com.dilamme.jobscheduler.repository.JobRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Getter
@Slf4j
public class Scheduler {
  private final JobQueue readyJobs;
  private final DependencyGraph dependencyGraph;
  private final JobRepository repository;
  private final Map<UUID, Future<?>> runningJobs = new ConcurrentHashMap<>();
  private final ApplicationEventPublisher eventPublisher;

  @Scheduled(fixedRate = 1000)
  @Transactional
  public void add() {
    List<Job> jobs = repository.findJobsToClaim(20);
    for (Job job : jobs) {
      job.setStatus(PROCESSING);
      Optional<Job> readyJob = dependencyGraph.add(job);
      readyJob.ifPresent(readyJobs::insert);
    }
    log.info("Database polled, {} jobs found", jobs.size());
  }

  @Scheduled(fixedRate = 30000)
  public void reclassifyPriorityQueue() {
    readyJobs.reclassifyAll();
    log.info("Priority queue reclassified");
  }

  public void jobCompleted(Job job) {
    List<Job> nextJobs = dependencyGraph.complete(job.getId());
    System.out.println(nextJobs.size());

    nextJobs.forEach(readyJobs::insert);
    runningJobs.remove(job.getId());

    if (job.isRecurring()) {
      scheduleNextOccurrence(job);
    }
  }

  @Transactional
  public void jobPassed(Job job) {
    job.setStatus(JobStatus.COMPLETED);
    repository.save(job);
    eventPublisher.publishEvent(JobStatusChangedEvent.of(job.getId(), JobStatus.COMPLETED));
  }

  @Transactional
  public void jobFailed(Job job, String errorMessage) {
    job.setStatus(JobStatus.FAILED);
    job.setErrorMessage(errorMessage);
    repository.save(job);
    eventPublisher.publishEvent(JobStatusChangedEvent.of(job.getId(), JobStatus.FAILED));
  }

  public void jobFailed(Job job) {
    jobFailed(job, "Unknown error");
  }

  @Transactional
  public void jobCanceled(UUID jobId) {
    Job job = repository.findById(jobId).orElseThrow();

    if (job.getStatus() == JobStatus.COMPLETED) {
      throw new IllegalArgumentException("Job is already completed: " + jobId);
    }
    switch (job.getStatus()) {
      case PENDING -> {
        if (!(readyJobs.remove(job.getId()) == null)) {
          dependencyGraph.clearMaps(job.getId());
        }
        job.setStatus(CANCELED);
      }

      case PROCESSING -> {
        Future<?> future = runningJobs.remove(job.getId());

        if (future != null) {
          future.cancel(true);
        }
        job.setStatus(CANCELED);
      }

      default -> {
        job.setStatus(CANCELED);
      }
    }
    log.info("Job {} cancelled", jobId);
    eventPublisher.publishEvent(JobStatusChangedEvent.of(job.getId(), CANCELED));
  }

  public Job claimJob() throws InterruptedException {

    return readyJobs.extractMax();
  }

  public void registerRunningJob(UUID uuid, Future<?> future) {
    runningJobs.put(uuid, future);
  }

  @Transactional
  public void workerInterrupted(UUID jobId) {

    Job job = repository.findById(jobId).orElseThrow();

    if (job.getStatus() == PROCESSING) {
      job.setStatus(CANCELED);
    }
  }

  private void scheduleNextOccurrence(Job job) {
    Job scheduledJob =
        Job.builder()
            .id(UUID.randomUUID())
            .scheduledTime(job.getScheduledTime().plusSeconds(job.getInterval()))
            .type(job.getType())
            .priority(job.getPriority())
            .payload(job.getPayload())
            .status(JobStatus.PENDING)
            .interval(job.getInterval())
            .build();
    repository.save(scheduledJob);
  }
}
