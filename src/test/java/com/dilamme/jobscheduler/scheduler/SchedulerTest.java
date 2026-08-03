package com.dilamme.jobscheduler.scheduler;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.*;

import com.dilamme.jobscheduler.entities.Job;
import com.dilamme.jobscheduler.enums.JobStatus;
import com.dilamme.jobscheduler.repository.JobRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
public class SchedulerTest {
  @Mock private DependencyGraph dependencyGraph;

  @Mock private JobRepository jobRepository;

  @Mock private ApplicationEventPublisher eventPublisher;

  @Mock private JobQueue jobQueue;

  @InjectMocks private Scheduler scheduler;

  private Job jobA;
  private Job jobB;

  @BeforeEach
  void setUp() {
    jobA =
        Job.builder()
            .priority(1)
            .id(UUID.randomUUID())
            .retry_count(3)
            .status(JobStatus.PENDING)
            .scheduledTime(Instant.now())
            .createdAt(Instant.now())
            .build();

    jobB =
        Job.builder()
            .priority(1)
            .id(UUID.randomUUID())
            .retry_count(3)
            .status(JobStatus.PROCESSING)
            .scheduledTime(Instant.now())
            .createdAt(Instant.now())
            .build();
  }

  @Test
  void testAddShouldAddReturnEmptyOptional() {
    // given
    Job job = mock(Job.class);

    lenient().when(jobRepository.findJobsToClaim(anyInt())).thenReturn(List.of(jobA));

    lenient().when(dependencyGraph.add(jobA)).thenReturn(Optional.empty());

    // when
    scheduler.add();

    // then
    assertThat(JobStatus.PROCESSING).isEqualTo(jobA.getStatus());
    verify(jobRepository).findJobsToClaim(anyInt());
    verify(dependencyGraph).add(jobA);
  }

  @Test
  void testAddShouldReturnPresentOptional() {
    // given
    Job job = mock(Job.class);

    when(jobRepository.findJobsToClaim(anyInt())).thenReturn(List.of(job));

    when(dependencyGraph.add(job)).thenReturn(Optional.of(job));

    // when
    scheduler.add();

    // then
    verify(jobRepository).findJobsToClaim(anyInt());
    verify(dependencyGraph).add(job);
  }

  @Test
  void testClaimJob() throws InterruptedException {
    // given
    when(jobQueue.extractMax()).thenReturn(jobB);

    Job claimedJob = scheduler.claimJob();

    assertThat(claimedJob.getStatus()).isEqualTo(JobStatus.PROCESSING);
  }

  @Test
  void testJobCancelledPendingStatus() {
    // given
    when(jobQueue.remove(jobA.getId())).thenReturn(jobA);
    when(jobRepository.findById(jobA.getId())).thenReturn(Optional.of(jobA));

    // when
    scheduler.jobCanceled(jobA.getId());

    // then
    assertThat(jobA.getStatus()).isEqualTo(JobStatus.CANCELED);
  }

  //    @Test
  //    void testJobCancelledProcessingStatus() {
  //        Future<?> future = mock(Future.class);
  //
  //        when(scheduler.getRunningJobs().remove(jobA.getId())).thenReturn(future);
  //    }
}
