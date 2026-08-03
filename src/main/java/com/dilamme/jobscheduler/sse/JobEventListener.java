package com.dilamme.jobscheduler.sse;

import com.dilamme.jobscheduler.events.JobStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JobEventListener {

  private final JobEventEmitterRegistry registry;

  @Async
  @EventListener
  public void onJobCompletion(JobStatusChangedEvent event) {
    registry.publish(event);
  }
}
