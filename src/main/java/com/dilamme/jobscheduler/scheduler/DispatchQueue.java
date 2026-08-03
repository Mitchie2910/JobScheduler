package com.dilamme.jobscheduler.scheduler;

import com.dilamme.jobscheduler.entities.Job;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.springframework.stereotype.Component;

@Component
public class DispatchQueue {
  private final BlockingQueue<Job> queue = new LinkedBlockingQueue<>();

  public boolean submit(Job job) {
    return queue.offer(job);
  }

  public Job take() throws InterruptedException {
    return queue.take();
  }
}
