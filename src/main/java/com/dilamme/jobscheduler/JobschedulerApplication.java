package com.dilamme.jobscheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class JobschedulerApplication {

  public static void main(String[] args) {
    SpringApplication.run(JobschedulerApplication.class, args);
  }
}
