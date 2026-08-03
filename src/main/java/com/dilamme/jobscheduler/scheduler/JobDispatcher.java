package com.dilamme.jobscheduler.scheduler;

import com.dilamme.jobscheduler.entities.Job;
import com.dilamme.jobscheduler.workers.Worker;
import java.util.concurrent.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
public class JobDispatcher {
  private final WorkerRegistry registry;
  private final Scheduler scheduler;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  private final RetryPolicy retryPolicy;
  private final DispatchQueue dispatchQueue;

  public void dispatchJobs() {
    log.info("Job dispatch thread is active");
    while (!Thread.currentThread().isInterrupted()) {
      try {
        Job job = dispatchQueue.take();
        log.info("Job {} has been claimed from the queue", job.getId());
        Future<?> future =
            executor.submit(
                () -> {
                  try {
                    retryPolicy.execute(
                        job,
                        () -> {
                          Worker worker = registry.get(job.getType());
                          worker.execute(job);
                          return null;
                        });
                  } catch (Exception e) {
                    log.info("Error encountered: {}", e.getMessage());
                  } finally {
                    scheduler.jobCompleted(job);
                    log.info("Job {} is marked completed", job.getId());
                  }
                });

        scheduler.registerRunningJob(job.getId(), future);

      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.info("Thread {} interrupted", Thread.currentThread().getName());
        break;
      }
    }
    executor.shutdown();
  }

  public void pollJobs() {
    log.info("Job polling thread is active");
    while (!Thread.currentThread().isInterrupted()) {
      try {
        Job claimedJob = scheduler.claimJob();
        dispatchQueue.submit(claimedJob);
        log.info("Job {} has been claimed from the scheduler", claimedJob.getId());

      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
  }
}
