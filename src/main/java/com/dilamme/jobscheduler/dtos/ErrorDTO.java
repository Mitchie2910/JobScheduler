package com.dilamme.jobscheduler.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.Builder;

@Builder
@Schema(description = "Standard API error response")
public record ErrorDTO(
    @Schema(description = "Time the error occurred") Instant timestamp,
    @Schema(description = "HTTP status code", example = "4xx") int status,
    @Schema(description = "HTTP status reason", example = "Error description") String error,
    @Schema(
            description = "Human-readable explanation of the error",
            example = "Job with id 550e8400-e29b-41d4-a716-446655440000 was not found")
        String message,
    @Schema(description = "Request path", example = "/jobs/550e8400-e29b-41d4-a716-446655440000")
        String path) {}
