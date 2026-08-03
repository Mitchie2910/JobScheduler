package com.dilamme.jobscheduler.client;

import com.dilamme.jobscheduler.entities.Job;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

@Component
public class HttpExecutor {

  private final RestClient restClient;

  public HttpExecutor(RestClient.Builder builder) {
    this.restClient =
        builder
            .defaultStatusHandler(
                HttpStatusCode::is4xxClientError,
                (request, response) -> {
                  throw HttpClientErrorException.create(
                      response.getStatusCode(),
                      response.getStatusText(),
                      response.getHeaders(),
                      response.getBody().readAllBytes(),
                      null);
                })
            .defaultStatusHandler(
                HttpStatusCode::is5xxServerError,
                (request, response) -> {
                  throw HttpServerErrorException.create(
                      response.getStatusCode(),
                      response.getStatusText(),
                      response.getHeaders(),
                      response.getBody().readAllBytes(),
                      null);
                })
            .build();
  }

  public ResponseEntity<String> execute(Job job) {
    return switch (job.getType()) {
      case EMAIL, WEBHOOK_DELIVERY -> emailService(job);
      case LOG_PROCESSING -> logService(job);
    };
  }

  private ResponseEntity<String> emailService(Job job) {
    String serviceURI = "http://localhost:8080/mockjobs/email/" + job.getId();
    String body = job.getPayload() != null ? job.getPayload() : "";
    return restClient
        .post()
        .uri(serviceURI)
        .header("Content-Type", "application/json")
        .body(body)
        .retrieve()
        .toEntity(String.class);
  }

  private ResponseEntity<String> logService(Job job) {
    String serviceURI = "http://localhost:8080/mockjobs/log/" + job.getId();
    String body = job.getPayload() != null ? job.getPayload() : "";
    return restClient
        .post()
        .uri(serviceURI)
        .header("Content-Type", "application/json")
        .body(body)
        .retrieve()
        .toEntity(String.class);
  }
}
