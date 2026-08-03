package com.dilamme.jobscheduler.repository;

import com.dilamme.jobscheduler.entities.JobDependency;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobDependencyRepository extends JpaRepository<JobDependency, UUID> {

  public List<JobDependency> findByJobId(UUID jobId);
}
