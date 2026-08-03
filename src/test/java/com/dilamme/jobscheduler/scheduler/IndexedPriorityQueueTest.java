package com.dilamme.jobscheduler.scheduler;

import static org.junit.jupiter.api.Assertions.*;

import com.dilamme.jobscheduler.entities.Job;
import com.dilamme.jobscheduler.enums.JobStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class IndexedPriorityQueueTest {
  private IndexedPriorityQueue queue;
  private Job high;
  private Job medium;
  private Job low;

  @BeforeEach
  void setUp() {
    queue = new IndexedPriorityQueue();
    high =
        Job.builder()
            .priority(1)
            .id(UUID.randomUUID())
            .retry_count(3)
            .status(JobStatus.PENDING)
            .scheduledTime(Instant.now())
            .createdAt(Instant.now())
            .build();
    medium =
        Job.builder()
            .priority(2)
            .id(UUID.randomUUID())
            .retry_count(3)
            .status(JobStatus.PENDING)
            .scheduledTime(Instant.now().minusMillis(3000))
            .createdAt(Instant.now())
            .build();
    low =
        Job.builder()
            .priority(3)
            .id(UUID.randomUUID())
            .retry_count(3)
            .status(JobStatus.PENDING)
            .scheduledTime(Instant.now().minusMillis(3000))
            .createdAt(Instant.now())
            .build();
    high.refresh(Instant.now());
    medium.refresh(Instant.now());
    low.refresh(Instant.now());
  }

  @Test
  void testInsertHighShouldBeRoot() {
    // given
    queue.insert(low);
    queue.insert(medium);
    queue.insert(high);

    // when
    Job job = queue.peek(0);

    // then
    System.out.println(queue.showQueue());
    assertEquals(job, high);
    assertEquals(1, job.getPriority());
  }

  @Test
  void testInsertShouldThrowIllegalArgumentException() {
    // given
    Job job = null;

    // when + then
    assertThrows(IllegalArgumentException.class, () -> queue.insert(job));
  }

  @Test
  void testExtractMaxHighShouldBeRoot() throws InterruptedException {
    // given
    queue.insert(low);
    queue.insert(medium);
    queue.insert(high);

    // when
    Job job = queue.extractMax();

    // then
    System.out.println(queue.showQueue());
    assertEquals(job, high);
    assertEquals(1, job.getPriority());
  }

  @Test
  void extractMaxShouldBlockUntilItemIsAvailable() throws Exception {

    ExecutorService executor = Executors.newSingleThreadExecutor();

    Future<Job> future = executor.submit(() -> queue.extractMax());

    // Give the worker time to block
    Thread.sleep(200);

    assertFalse(future.isDone());

    queue.insert(high);

    assertEquals(high, future.get(1, TimeUnit.SECONDS));

    executor.shutdown();
  }

  @Test
  void testRemoveShouldReturnRemovedJob() {
    // given
    queue.insert(low);
    queue.insert(medium);
    queue.insert(high);

    // when
    queue.remove(low.getId());

    // then
    List<Job> jobList = queue.showQueue();
    assertEquals(2, queue.showQueue().size());
    assertSame(jobList.getFirst(), high);
    assertSame(jobList.get(1), medium);
    jobList.forEach((job) -> assertNotEquals(job.getId(), low.getId()));
  }

  @Test
  void testReclassifyAll() {
    // given
    queue.insert(low);
    queue.insert(medium);
    queue.insert(high);

    high.setPriority(2);

    // when
    queue.reclassifyAll();

    // then
    List<Job> jobList = queue.showQueue();
    assertSame(jobList.getFirst(), medium);
  }

  @Test
  void testRemoveShouldThrowNoSuchElementException() {
    // when + then
    assertNull(queue.remove(UUID.randomUUID()));
  }
}
