package com.dilamme.jobscheduler.scheduler;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import com.dilamme.jobscheduler.entities.Job;
import com.dilamme.jobscheduler.entities.JobDependency;
import com.dilamme.jobscheduler.enums.JobStatus;
import com.dilamme.jobscheduler.repository.JobDependencyRepository;
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

@ExtendWith(MockitoExtension.class)
public class DependencyGraphTest {
  @Mock private JobRepository repository;

  @Mock private JobDependencyRepository dependencyRepository;

  @InjectMocks private DependencyGraph dependencyGraph;

  private Job testJob;
  private UUID completedId;
  private UUID childA;
  private UUID childB;

  @BeforeEach
  void setUp() {

    testJob =
        Job.builder()
            .priority(1)
            .id(UUID.randomUUID())
            .retry_count(3)
            .status(JobStatus.PENDING)
            .scheduledTime(Instant.now())
            .createdAt(Instant.now())
            .build();
    JobDependency dependency1 = JobDependency.builder().dependsOnJobId(UUID.randomUUID()).build();
    JobDependency dependency2 = JobDependency.builder().dependsOnJobId(UUID.randomUUID()).build();
    JobDependency dependency3 = JobDependency.builder().dependsOnJobId(UUID.randomUUID()).build();

    List<JobDependency> dependencies = List.of(dependency1, dependency2, dependency3);

    lenient().when(dependencyRepository.findByJobId(any(UUID.class))).thenReturn(dependencies);

    completedId = UUID.randomUUID();
    childA = UUID.randomUUID();
    childB = UUID.randomUUID();
  }

  @Test
  void testAddShouldReturnJob() {
    // given
    when(repository.findByIdAndStatusIn(any(UUID.class), anyList()))
        .thenReturn(Optional.of(new Job()));

    // when
    Optional<Job> outcome = dependencyGraph.add(testJob);

    // then
    assertTrue(outcome.isPresent());
  }

  @Test
  void testAddShouldReturnEmptyJob() {
    // given
    when(repository.findByIdAndStatusIn(any(UUID.class), anyList())).thenReturn(Optional.empty());

    // when
    Optional<Job> outcome = dependencyGraph.add(testJob);

    // then
    assertFalse(outcome.isPresent());
  }

  @Test
  void returnsEmptyList_whenCompletedJobHasNoDependents() {
    List<Job> ready = dependencyGraph.complete(completedId);

    assertTrue(ready.isEmpty());
  }

  @Test
  void testCompleted_RemovesCompletedJobsFromBothMaps() {
    dependencyGraph.getDependents().put(completedId, List.of(childA));
    dependencyGraph.getRemainingDependencies().put(completedId, 0);
    dependencyGraph.getRemainingDependencies().put(childA, 1);

    Job jobA = mock(Job.class);

    when(repository.findById(childA)).thenReturn(Optional.of(jobA));

    dependencyGraph.complete(completedId);

    assertThat(dependencyGraph.getDependents()).doesNotContainKey(completedId);
    assertThat(dependencyGraph.getRemainingDependencies()).doesNotContainKey(completedId);
  }

  @Test
  void childBecomesReady_whenItsLastDependencyCompletes() {
    dependencyGraph.getDependents().put(completedId, List.of(childA));
    dependencyGraph.getRemainingDependencies().put(childA, 1);

    Job jobA = mock(Job.class);
    when(repository.findById(childA)).thenReturn(Optional.of(jobA));

    List<Job> ready = dependencyGraph.complete(completedId);

    assertThat(ready).containsExactly(jobA);
    assertThat(dependencyGraph.getRemainingDependencies().get(childA)).isZero();
  }

  @Test
  void childStaysNotReady_whenOtherDependenciesRemain() {
    dependencyGraph.getDependents().put(completedId, List.of(childA));
    dependencyGraph.getRemainingDependencies().put(childA, 2);

    List<Job> ready = dependencyGraph.complete(completedId);

    assertThat(ready).isEmpty();
    assertThat(dependencyGraph.getRemainingDependencies().get(childA)).isEqualTo(1);
    verify(repository, never()).findById(childA);
  }

  @Test
  void handlesMultipleChildren_mixedReadiness() {
    dependencyGraph.getDependents().put(completedId, List.of(childA, childB));
    dependencyGraph.getRemainingDependencies().put(childA, 1);
    dependencyGraph.getRemainingDependencies().put(childB, 2);

    Job jobA = mock(Job.class);
    when(repository.findById(childA)).thenReturn(Optional.of(jobA));

    List<Job> ready = dependencyGraph.complete(completedId);

    assertThat(ready).containsExactly(jobA);
    verify(repository, never()).findById(childB);
  }
}
