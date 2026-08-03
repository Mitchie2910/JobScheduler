package com.dilamme.jobscheduler.scheduler;

import com.dilamme.jobscheduler.entities.Job;
import java.util.UUID;

public interface JobQueue {
  void insert(Job job);

  Job extractMax() throws InterruptedException;

  Job remove(UUID id);

  void reclassifyAll();
}
