package com.dilamme.jobscheduler.workers;

import com.dilamme.jobscheduler.entities.Job;
import com.dilamme.jobscheduler.enums.JobType;
import org.springframework.http.ResponseEntity;

public interface Worker {
  JobType supports();

  ResponseEntity<String> execute(Job job) throws InterruptedException;
}
