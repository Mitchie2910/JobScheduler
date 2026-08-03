package com.dilamme.jobscheduler.controller;

import com.dilamme.jobscheduler.dtos.ErrorDTO;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ErrorHandler {

  @ExceptionHandler(NoSuchElementException.class)
  public ResponseEntity<ErrorDTO> handleJobNotFound(
      NoSuchElementException ex, HttpServletRequest request) {

    ErrorDTO error =
        ErrorDTO.builder()
            .timestamp(Instant.now())
            .status(HttpStatus.NOT_FOUND.value())
            .error("Job Not Found")
            .message(ex.getMessage())
            .path(request.getRequestURI())
            .build();

    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorDTO> handleJobNotFailed(
      NoSuchElementException ex, HttpServletRequest request) {

    ErrorDTO error =
        ErrorDTO.builder()
            .timestamp(Instant.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Job Not Failed")
            .message(ex.getMessage())
            .path(request.getRequestURI())
            .build();

    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
  }
}
