package com.dilamme.jobscheduler.workers;

import com.dilamme.jobscheduler.client.HttpExecutor;
import com.dilamme.jobscheduler.entities.Job;
import com.dilamme.jobscheduler.enums.JobType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailWorker implements Worker {

  private final HttpExecutor executor;

  @Override
  public ResponseEntity<String> execute(Job job) throws InterruptedException {
    return executor.execute(job);
  }

  @Override
  public JobType supports() {
    return JobType.EMAIL;
  }
}
