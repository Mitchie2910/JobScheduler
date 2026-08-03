package com.dilamme.jobscheduler.entities;

import com.dilamme.jobscheduler.enums.JobStatus;
import com.dilamme.jobscheduler.enums.JobType;
import jakarta.persistence.*;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
@Table(name = "jobs")
public class Job {

  @Id private UUID id = UUID.randomUUID();

  @Enumerated(EnumType.STRING)
  private JobType type;

  @Enumerated(EnumType.STRING)
  private JobStatus status;

  private int retry_count;

  private String payload;

  private int priority;

  @Column(name = "recurring_interval")
  private int interval;

  private Instant scheduledTime;

  private String errorMessage;

  @Transient private double effectivePriority = 0;

  @CreationTimestamp private Instant createdAt;

  static double computeEffectivePriority(
      double priority, Instant scheduledTime, Instant createdAt, Instant now) {
    double base = priority * 1000;

    long minutesOverdue = Duration.between(now, scheduledTime).toMinutes();
    double timeFactor;
    if (priority == 1 && minutesOverdue >= 5) {
      timeFactor = Math.min(minutesOverdue * 20, 2000);
    } else if (priority == 2 && minutesOverdue >= 5) {
      timeFactor = Math.min(minutesOverdue * 20, 1000);
    } else {
      timeFactor = 0;
    }
    double ageMinutes = Math.max(0, Duration.between(createdAt, now).toMinutes());
    double ageBonus = Math.min(ageMinutes * 0.05, 500.0);

    return base + timeFactor + ageBonus;
  }

  public void refresh(Instant now) {
    this.effectivePriority =
        computeEffectivePriority(this.priority, this.scheduledTime, this.createdAt, now);
  }

  public boolean isRecurring() {
    return interval > 0;
  }
}
