package com.dilamme.jobscheduler.sse;

import com.dilamme.jobscheduler.events.JobStatusChangedEvent;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Holds open SseEmitters and pushes JobStatusChangedEvents to them.
 *
 * <p>Two subscription shapes are supported: - per-job: a client watching one job's detail page -
 * broadcast: a client watching a dashboard/list view across all jobs
 *
 * <p>Emitters are removed on completion, timeout, and error so this map never accumulates dead
 * connections. This is the part that's easy to get wrong with SseEmitter — leaking here means
 * memory growth for every client that closes a tab without a clean disconnect.
 */
@Component
public class JobEventEmitterRegistry {

  private static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1000L; // 30 min

  private final Map<UUID, List<SseEmitter>> perJobEmitters = new ConcurrentHashMap<>();
  private final List<SseEmitter> broadcastEmitters = new CopyOnWriteArrayList<>();

  public SseEmitter subscribeToJob(UUID jobId) {
    SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
    List<SseEmitter> emitters =
        perJobEmitters.computeIfAbsent(jobId, id -> new CopyOnWriteArrayList<>());
    emitters.add(emitter);

    registerCleanup(emitter, () -> removeFromJob(jobId, emitter));
    return emitter;
  }

  public SseEmitter subscribeToAll() {
    SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
    broadcastEmitters.add(emitter);

    registerCleanup(emitter, () -> broadcastEmitters.remove(emitter));
    return emitter;
  }

  public void publish(JobStatusChangedEvent event) {
    List<SseEmitter> jobEmitters = perJobEmitters.get(event.jobId());
    if (jobEmitters != null) {
      sendToAll(jobEmitters, event, emitter -> removeFromJob(event.jobId(), emitter));
    }
    sendToAll(broadcastEmitters, event, broadcastEmitters::remove);
  }

  private void sendToAll(
      List<SseEmitter> emitters,
      JobStatusChangedEvent event,
      java.util.function.Consumer<SseEmitter> onDead) {
    for (SseEmitter emitter : emitters) {
      try {
        emitter.send(
            SseEmitter.event()
                .id(event.jobId().toString() + "-" + event.occurredAt().toEpochMilli())
                .name("job-status")
                .data(event));
      } catch (IOException | IllegalStateException e) {
        // Client disconnected without a clean close (e.g. closed tab, network drop).
        // Drop it here rather than waiting for onError, since a failed send won't
        // always trigger the emitter's own error callback.
        emitter.complete();
        onDead.accept(emitter);
      }
    }
  }

  private void registerCleanup(SseEmitter emitter, Runnable removeAction) {
    emitter.onCompletion(removeAction);
    emitter.onTimeout(
        () -> {
          emitter.complete();
          removeAction.run();
        });
    emitter.onError(throwable -> removeAction.run());
  }

  private void removeFromJob(UUID jobId, SseEmitter emitter) {
    perJobEmitters.computeIfPresent(
        jobId,
        (id, emitters) -> {
          emitters.remove(emitter);
          return emitters.isEmpty() ? null : emitters;
        });
  }
}
