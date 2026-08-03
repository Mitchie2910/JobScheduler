CREATE TABLE jobs (
                      id UUID PRIMARY KEY,
                      type VARCHAR(255) NOT NULL,
                      status VARCHAR(255) NOT NULL,

                      retry_count INTEGER NOT NULL DEFAULT 0,

                      payload TEXT,

                      priority INTEGER NOT NULL,

                      recurring_interval INTEGER NOT NULL DEFAULT 0,

                      scheduled_time TIMESTAMP WITH TIME ZONE NOT NULL,

                      error_message TEXT,

                      created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);


CREATE TABLE job_dependencies (
                                  id UUID PRIMARY KEY,

                                  job_id UUID NOT NULL,

                                  depends_on_job_id UUID NOT NULL,

                                  CONSTRAINT fk_job_dependencies_job
                                      FOREIGN KEY (job_id)
                                          REFERENCES jobs(id)
                                          ON DELETE CASCADE,

                                  CONSTRAINT fk_job_dependencies_parent
                                      FOREIGN KEY (depends_on_job_id)
                                          REFERENCES jobs(id)
                                          ON DELETE CASCADE,

                                  CONSTRAINT unique_job_dependency
                                      UNIQUE(job_id, depends_on_job_id)
);


CREATE INDEX idx_jobs_status
    ON jobs(status);


CREATE INDEX idx_jobs_scheduled_time
    ON jobs(scheduled_time);


CREATE INDEX idx_job_dependencies_job_id
    ON job_dependencies(job_id);


CREATE INDEX idx_job_dependencies_depends_on_job_id
    ON job_dependencies(depends_on_job_id);