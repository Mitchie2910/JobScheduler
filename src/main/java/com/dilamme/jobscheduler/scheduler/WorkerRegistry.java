package com.dilamme.jobscheduler.scheduler;

import com.dilamme.jobscheduler.enums.JobType;
import com.dilamme.jobscheduler.workers.Worker;

public interface WorkerRegistry {

  Worker get(JobType jobType);
}
