package com.dilamme.jobscheduler.controller;

import io.swagger.v3.oas.annotations.Hidden;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/mockjobs")
@Hidden
public class FlakyJobController {

  private final Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();
  private volatile int failuresBeforeSuccessEmail = 2; // configurable at runtime
  private volatile int failuresBeforeFinalFailure = 2;

  @PostMapping("/email/{jobId}")
  public ResponseEntity<?> executeEmail(
      @PathVariable String jobId, @RequestBody(required = false) Map<String, Object> body) {
    int attempt = attempts.computeIfAbsent(jobId, k -> new AtomicInteger()).incrementAndGet();

    if (attempt <= failuresBeforeSuccessEmail) {
      return switch (attempt) {
        case 1 ->
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "unavailable", "attempt", attempt));
        case 2 ->
            ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "2")
                .body(Map.of("error", "rate_limited", "attempt", attempt));
        default ->
            ResponseEntity.internalServerError()
                .body(Map.of("error", "internal", "attempt", attempt));
      };
    }

    return ResponseEntity.ok(
        Map.of(
            "status", "completed",
            "jobId", jobId,
            "attempt", attempt));
  }

  @PostMapping("log/{jobId}")
  public ResponseEntity<?> executeLogProcessing(
      @PathVariable String jobId, @RequestBody(required = false) Map<String, Object> body) {
    int attempt = attempts.computeIfAbsent(jobId, k -> new AtomicInteger()).incrementAndGet();

    if (attempt <= failuresBeforeFinalFailure) {
      return switch (attempt) {
        case 1 ->
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "unavailable", "attempt", attempt));
        case 2 ->
            ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "2")
                .body(Map.of("error", "rate_limited", "attempt", attempt));
        default ->
            ResponseEntity.internalServerError()
                .body(Map.of("error", "internal", "attempt", attempt));
      };
    }

    return ResponseEntity.notFound().build();

    //        return ResponseEntity.ok(Map.of(
    //                "status", "completed",
    //                "jobId", jobId,
    //                "attempt", attempt
    //        ));
  }

  // --- admin/control endpoints, so you can steer behavior without restarting ---

  @PostMapping("/_admin/config")
  public ResponseEntity<?> configure(
      @RequestParam int failuresBeforeSuccess, @RequestParam int failuresBeforeFailure) {
    this.failuresBeforeSuccessEmail = failuresBeforeSuccess;
    this.failuresBeforeFinalFailure = failuresBeforeFailure;
    return ResponseEntity.ok(Map.of("failuresBeforeSuccess", failuresBeforeSuccess));
  }

  @PostMapping("/_admin/reset")
  public ResponseEntity<?> reset() {
    attempts.clear();
    return ResponseEntity.ok(Map.of("cleared", true));
  }

  @GetMapping("/_admin/attempts/{jobId}")
  public ResponseEntity<?> attemptsFor(@PathVariable String jobId) {
    int count = attempts.getOrDefault(jobId, new AtomicInteger()).get();
    return ResponseEntity.ok(Map.of("jobId", jobId, "attempts", count));
  }
}
