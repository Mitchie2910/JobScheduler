package com.dilamme.jobscheduler.sse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/jobs")
@Tag(name = "Job Events", description = "Operations for Server Side Events")
@RequiredArgsConstructor
public class JobEventsController {

  private final JobEventEmitterRegistry registry;

  /** Live status stream for a single job — e.g. a job detail page. */
  @Operation(
      summary = "Stream events for a single job",
      description =
          """
                    Opens a Server-Sent Events stream for a specific job.
                    The connection remains open and receives job status updates
                    as they occur.
                    """)
  @ApiResponse(
      responseCode = "200",
      description = "SSE stream established",
      content =
          @Content(
              mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
              schema = @Schema(implementation = SseEmitter.class)))
  @GetMapping(path = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter streamJobEvents(@Parameter(description = "Job ID") @PathVariable UUID id) {

    return registry.subscribeToJob(id);
  }

  /** Live status stream across all jobs — e.g. a dashboard/list view. */
  @Operation(
      summary = "Stream events for all jobs",
      description =
          """
                    Opens a Server-Sent Events stream that receives status
                    updates for all jobs.
                    """)
  @ApiResponse(
      responseCode = "200",
      description = "SSE stream established",
      content =
          @Content(
              mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
              schema = @Schema(implementation = SseEmitter.class)))
  @GetMapping(path = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter streamAllJobEvents() {
    return registry.subscribeToAll();
  }
}
