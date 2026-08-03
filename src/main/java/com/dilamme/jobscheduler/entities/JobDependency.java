package com.dilamme.jobscheduler.entities;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "job_dependencies")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobDependency {

  @Id private UUID id = UUID.randomUUID();

  @Column(name = "job_id")
  private UUID jobId;

  @Column(name = "depends_on_job_id")
  private UUID dependsOnJobId;
}
