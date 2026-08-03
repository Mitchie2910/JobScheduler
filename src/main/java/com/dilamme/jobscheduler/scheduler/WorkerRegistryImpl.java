package com.dilamme.jobscheduler.scheduler;

import com.dilamme.jobscheduler.enums.JobType;
import com.dilamme.jobscheduler.workers.Worker;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class WorkerRegistryImpl implements WorkerRegistry {

  private final Map<JobType, Worker> workers;

  public WorkerRegistryImpl(List<Worker> workersList) {
    this.workers =
        workersList.stream().collect(Collectors.toMap(Worker::supports, Function.identity()));
  }

  @Override
  public Worker get(JobType jobType) {
    return workers.get(jobType);
  }
}
