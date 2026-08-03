package com.dilamme.jobscheduler.scheduler;

import com.dilamme.jobscheduler.entities.Job;
import com.dilamme.jobscheduler.entities.JobDependency;
import com.dilamme.jobscheduler.enums.JobStatus;
import com.dilamme.jobscheduler.repository.JobDependencyRepository;
import com.dilamme.jobscheduler.repository.JobRepository;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Getter
@Slf4j
public class DependencyGraph {
  private final Map<UUID, Integer> remainingDependencies = new ConcurrentHashMap<>();
  private final Map<UUID, List<UUID>> dependents = new ConcurrentHashMap<>();
  private final JobRepository repository;
  private final JobDependencyRepository dependencyRepository;

  public Optional<Job> add(Job job) {

    List<JobDependency> dependentIds = dependencyRepository.findByJobId(job.getId());
    int remaining = 0;

    for (JobDependency dependentId : dependentIds) {
      UUID parent = dependentId.getDependsOnJobId();

      dependents.computeIfAbsent(parent, parentId -> new ArrayList<>()).add(job.getId());
      log.info("Parent {} now has children {}", parent, dependents.get(parent));

      if (!isCompleted(parent)) {
        remaining++;
      }
    }

    dependents.computeIfAbsent(job.getId(), uuid -> new ArrayList<>());
    remainingDependencies.put(job.getId(), remaining);

    if (remaining == 0) {
      log.info("Dependents map has {} entries", dependents.size());
      log.info("remainingDependencies map has {} entries", remainingDependencies.size());
      return Optional.of(job);
    } else {
      log.info("Job {} is pending until its dependencies are completed", job.getId());
      return Optional.empty();
    }
  }

  public List<Job> complete(UUID completedId) {
    List<Job> ready = new ArrayList<>();

    List<UUID> children = dependents.remove(completedId);
    remainingDependencies.remove(completedId);

    if (children == null) {
      log.info("Job {} has no children", completedId);
      return ready;
    }

    for (UUID child : children) {
      int remaining = decrementRemainingDependencies(child);
      if (remaining == 0) {
        Job job = repository.findById(child).orElse(null);
        ready.add(job);
      }
      log.info("Loading up Job {}", child);
    }
    return ready;
  }

  private boolean isCompleted(UUID jobId) {
    return repository
        .findByIdAndStatusIn(
            jobId, List.of(JobStatus.COMPLETED, JobStatus.CANCELED, JobStatus.FAILED))
        .isPresent();
  }

  private int decrementRemainingDependencies(UUID jobId) {
    return remainingDependencies.compute(
        jobId,
        (id, count) -> {
          if (count == null) {
            throw new IllegalArgumentException("Job not found: " + jobId);
          }
          return count - 1;
        });
  }

  public void clearMaps(UUID jobId) {
    remainingDependencies.remove(jobId);
    dependents.remove(jobId);
  }
}
