package com.dilamme.jobscheduler.controller;

import com.dilamme.jobscheduler.dtos.ErrorDTO;
import com.dilamme.jobscheduler.dtos.IncomingJob;
import com.dilamme.jobscheduler.service.JobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/jobs")
@Tag(name = "Jobs", description = "Operations for managing scheduled jobs")
@RequiredArgsConstructor
public class JobController {

  private final JobService jobService;

  @PostMapping
  @Operation(description = "Create a job from JSON")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Job Created",
        content =
            @Content(
                mediaType = "application/json",
                schema =
                    @Schema(
                        type = "object",
                        example =
                            """
            {
              "id": "123"
            }
            """))),
    @ApiResponse(
        responseCode = "400",
        description = "Bad Request",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorDTO.class)))
  })
  public ResponseEntity<?> createJob(@RequestBody IncomingJob incomingJob) {
    UUID jobId = jobService.createJob(incomingJob);

    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", jobId));
  }

  @GetMapping
  @DeleteMapping("/{jobId}")
  @Operation(description = "Cancel a Job")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "No Content"),
    @ApiResponse(
        responseCode = "404",
        description = "Not Found",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorDTO.class)))
  })
  public ResponseEntity<Void> cancelJob(@PathVariable UUID jobId) {
    jobService.cancelJob(jobId);

    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{jobId}/retry")
  @Operation(description = "Retry a failed job")
  @ApiResponses({
    @ApiResponse(responseCode = "202", description = "Accepted"),
    @ApiResponse(
        responseCode = "404",
        description = "Not Found",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorDTO.class)))
  })
  public ResponseEntity<Void> retryJob(@PathVariable UUID jobId) {
    jobService.retryJob(jobId);

    return ResponseEntity.accepted().build();
  }
}
