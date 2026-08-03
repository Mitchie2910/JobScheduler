package com.dilamme.jobscheduler.scheduler;

import com.dilamme.jobscheduler.entities.Job;
import com.dilamme.jobscheduler.repository.JobRepository;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.Callable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;

@Component
@RequiredArgsConstructor
@Slf4j
public class RetryPolicy {

  private final JobRepository jobRepository;
  private final Scheduler scheduler;
  private final WorkerRegistry workerRegistry;

  private static final int MAX_RETRIES = 3;
  private static final Duration initialDelay = Duration.ofMillis(1000);
  private static final Set<String> RETRYABLE_KEYWORDS =
      Set.of(
          "timeout", "connection", "rate limit", "quota", "503", "429", "temporarily unavailable");

  public void execute(Job job, Callable<Void> task) throws Exception {

    while (job.getRetry_count() < MAX_RETRIES) {
      try {
        task.call();
        scheduler.jobPassed(job);
        log.info("Job {} passed successfully", job.getId());
        return;

      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        scheduler.workerInterrupted(job.getId());
        return;
      } catch (Exception e) {

        if (!isRetryable(e) || job.getRetry_count() == MAX_RETRIES - 1) {
          scheduler.jobFailed(job, e.getMessage());
          throw e;
        }
        log.info("Retrying job {}", job.getId());
        long waitMillis = calculateBackoff(job.getRetry_count());
        backoff(waitMillis);
        incrementRetryCount(job);
      }
    }
    scheduler.jobFailed(job);
    throw new IllegalStateException("Retry loop completed without returning or throwing");
  }

  private void incrementRetryCount(Job job) {
    job.setRetry_count(job.getRetry_count() + 1);
    jobRepository.save(job);
  }

  protected void backoff(long millis) throws InterruptedException {
    Thread.sleep(millis);
  }

  private long calculateBackoff(int attempt) {
    long delay = initialDelay.toMillis();

    for (int i = 0; i < attempt; i++) {
      delay *= 5;
    }
    long jitter = (long) (Math.random() * 1000);
    return delay + jitter;
  }

  private boolean isRetryable(Exception e) {
    String errorMessage = e.getMessage();
    if (errorMessage == null) {
      return false;
    }
    String lowerMessage = errorMessage.toLowerCase();
    return e instanceof HttpServerErrorException
        || RETRYABLE_KEYWORDS.stream().anyMatch(lowerMessage::contains);
  }
}
