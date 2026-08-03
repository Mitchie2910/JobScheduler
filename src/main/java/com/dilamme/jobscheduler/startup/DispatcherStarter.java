package com.dilamme.jobscheduler.startup;

import com.dilamme.jobscheduler.scheduler.JobDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Profile("!test")
public class DispatcherStarter {

  private final JobDispatcher dispatcher;

  @EventListener(ApplicationReadyEvent.class)
  public void start() {
    Thread.startVirtualThread(dispatcher::dispatchJobs);
    Thread.startVirtualThread(dispatcher::pollJobs);
  }
}
