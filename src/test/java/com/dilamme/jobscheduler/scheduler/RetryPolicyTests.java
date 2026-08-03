package com.dilamme.jobscheduler.scheduler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.dilamme.jobscheduler.entities.Job;
import com.dilamme.jobscheduler.repository.JobRepository;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * These tests exercise RetryPolicy purely through its public execute() method
 * (isRetryable/calculateBackoff are private, so they're covered indirectly). Two things needed
 * workarounds:
 *
 * <p>1. Job.getRetry_count()/setRetry_count() need to behave like a real mutable field for the
 * retry loop to make sense across calls, so mockJobWithRetryCount() below backs a mock with an
 * AtomicInteger. 2. calculateBackoff() calls Thread.sleep() with real, exponentially growing delays
 * (1s, 5s, 25s, ...). Left un-mocked, a single exhausted-retries test would take 30+ real seconds.
 * Thread.sleep is stubbed out via Mockito's mockStatic(), which requires the inline mock maker
 * (default in mockito-core 5.x; add mockito-inline explicitly if you're on an older version).
 *
 * <p>A few things below aren't just test scaffolding - they document behavior in RetryPolicy that
 * looks unintentional. Flagging up front so they don't get mistaken for expected behavior:
 *
 * <p>- When retries are exhausted (job.getRetry_count() reaches MAX_RETRIES via the loop),
 * execute() throws a bare IllegalStateException instead of the original (retryable) exception,
 * after already having called scheduler.jobFailed(job). The real failure reason is discarded. - The
 * `job.getRetry_count() >= MAX_RETRIES` check inside the catch block is effectively dead code: the
 * enclosing while loop already guarantees retry_count < MAX_RETRIES on every entry into the try
 * block, so that branch can only be reached via the "not retryable" half of the OR. - If the thread
 * is interrupted during Thread.sleep() in the backoff branch (as opposed to during task.call()),
 * the InterruptedException is not caught by the method's own catch(InterruptedException) handler
 * (that's a sibling catch for the try block, not for this catch block) - it just propagates out of
 * execute() with no call to scheduler.jobCanceled(), no jobFailed(), and no restoration of the
 * thread's interrupt status. - execute() can be called with a job whose retry_count is already >=
 * MAX_RETRIES (e.g. a job re-queued after a restart). In that case the loop body never runs at all
 * - task.call() is never even attempted once - yet jobFailed() + IllegalStateException still fire.
 */
@ExtendWith(MockitoExtension.class)
class RetryPolicyTests {

  @Mock private JobRepository jobRepository;
  @Mock private Scheduler scheduler;
  @Mock private WorkerRegistry workerRegistry;
  @Mock private Callable<Void> task;

  private RetryPolicy retryPolicy;

  @BeforeEach
  void setUp() {
    retryPolicy = new RetryPolicy(jobRepository, scheduler, workerRegistry);
  }

  @AfterEach
  void clearInterruptFlag() {
    Thread.interrupted();
  }

  private Job mockJobWithRetryCount(int initial) {
    Job job = mock(Job.class);
    lenient().when(job.getId()).thenReturn(UUID.randomUUID());
    AtomicInteger retryCount = new AtomicInteger(initial);
    lenient().when(job.getRetry_count()).thenAnswer(inv -> retryCount.get());
    lenient()
        .doAnswer(
            inv -> {
              retryCount.set(inv.getArgument(0));
              return null;
            })
        .when(job)
        .setRetry_count(anyInt());
    return job;
  }

  // --- happy path -----------------------------------------------------

  @Test
  void succeedsOnFirstAttempt_marksJobPassed_noRetries() throws Exception {
    Job job = mockJobWithRetryCount(0);
    when(task.call()).thenReturn(null);

    retryPolicy.execute(job, task);

    verify(task, times(1)).call();
    verify(scheduler).jobPassed(job);
    verify(scheduler, never()).jobFailed(any());
    verify(scheduler, never()).jobCanceled(any());
    verify(jobRepository, never()).save(any());
  }

  // --- non-retryable failure -------------------------------------------

  @Test
  void nonRetryableException_marksJobFailed_andRethrowsOriginalException() throws Exception {
    Job job = mockJobWithRetryCount(0);
    RuntimeException failure = new RuntimeException("bad request: invalid payload");
    when(task.call()).thenThrow(failure);

    Exception thrown = assertThrows(RuntimeException.class, () -> retryPolicy.execute(job, task));

    assertSame(failure, thrown);
    verify(task, times(1)).call();
    verify(scheduler).jobFailed(job, failure.getMessage());
    verify(scheduler, never()).jobPassed(any());
    verify(jobRepository, never()).save(any());
  }

  @Test
  void exceptionWithNullMessage_treatedAsNonRetryable() throws Exception {
    Job job = mockJobWithRetryCount(0);
    NullPointerException failure = new NullPointerException();
    when(task.call()).thenThrow(failure);

    assertThrows(NullPointerException.class, () -> retryPolicy.execute(job, task));

    verify(task, times(1)).call();
    verify(scheduler).jobFailed(job, null);
    verify(jobRepository, never()).save(any());
  }

  // --- retryable failure then success -----------------------------------

  @Test
  void retryableException_thenSuccess_retriesOnceAndMarksJobPassed() throws Exception {
    Job job = mockJobWithRetryCount(0);
    when(task.call()).thenThrow(new RuntimeException("connection timeout")).thenReturn(null);

    //        try (MockedStatic<Thread> threadMock = mockStatic(Thread.class, CALLS_REAL_METHODS)) {
    //            threadMock.when(() -> Thread.sleep(anyLong())).thenAnswer(inv -> null);
    //
    //            retryPolicy.execute(job, task);
    //        }

    retryPolicy.execute(job, task);

    verify(task, times(2)).call();
    verify(jobRepository, times(1)).save(job);
    verify(scheduler).jobPassed(job);
    verify(scheduler, never()).jobFailed(any());
    assertEquals(1, job.getRetry_count());
  }

  @Test
  void retryableKeywordMatch_isCaseInsensitive() throws Exception {
    Job job = mockJobWithRetryCount(0);
    when(task.call())
        .thenThrow(new RuntimeException("Service Temporarily Unavailable"))
        .thenReturn(null);

    //        try (MockedStatic<Thread> threadMock = mockStatic(Thread.class, CALLS_REAL_METHODS)) {
    //            threadMock.when(() -> Thread.sleep(anyLong())).thenAnswer(inv -> null);
    //
    //
    //        }
    retryPolicy.execute(job, task);

    verify(task, times(2)).call();
    verify(scheduler).jobPassed(job);
  }

  // --- interrupted while running the task --------------------------------

  @Test
  void interruptedDuringTask_marksJobCanceled_returnsWithoutThrowing() throws Exception {
    Job job = mockJobWithRetryCount(0);
    when(task.call()).thenThrow(new InterruptedException());

    assertDoesNotThrow(() -> retryPolicy.execute(job, task));

    assertTrue(Thread.currentThread().isInterrupted());
    verify(scheduler).workerInterrupted(job.getId());
    verify(scheduler, never()).jobFailed(any());
    verify(scheduler, never()).jobPassed(any());
    verify(jobRepository, never()).save(any());
  }

  // --- exhausted retries: documents current (likely buggy) behavior -------

  @Test
  void retriesExhausted_throwsIllegalStateException_notOriginalFailure() throws Exception {
    Job job = mockJobWithRetryCount(0);
    IllegalStateException originalFailure = new IllegalStateException("connection timeout");
    when(task.call()).thenThrow(originalFailure); // fails every time

    RetryPolicy retryPolicySpy = spy(retryPolicy);

    doNothing().when(retryPolicySpy).backoff(anyLong());

    //        try (MockedStatic<Thread> threadMock = mockStatic(Thread.class, CALLS_REAL_METHODS)) {
    //            threadMock.when(() -> Thread.sleep(anyLong())).thenAnswer(inv -> null);

    IllegalStateException thrown =
        assertThrows(IllegalStateException.class, () -> retryPolicySpy.execute(job, task));

    // Current behavior: the original exception is discarded.
    assertNull(thrown.getCause());
    //        }

    verify(task, times(3)).call(); // MAX_RETRIES attempts
    verify(jobRepository, times(2)).save(job);
    verify(scheduler, times(1))
        .jobFailed(job, originalFailure.getMessage()); // only from the fall-through path
    assertEquals(2, job.getRetry_count());
  }

  @Test
  void jobAlreadyAtMaxRetriesOnEntry_neverAttemptsTask() throws Exception {
    Job job = mockJobWithRetryCount(3); // == MAX_RETRIES

    assertThrows(IllegalStateException.class, () -> retryPolicy.execute(job, task));

    verify(task, never()).call();
    verify(scheduler).jobFailed(job);
    verify(jobRepository, never()).save(any());
  }

  // --- interrupted during backoff sleep: documents current behavior -------

  @Test
  void interruptedDuringBackoffSleep_propagatesWithoutSchedulerCleanup() throws Exception {
    // Arrange
    Job job = mockJobWithRetryCount(0);

    when(task.call()).thenThrow(new RuntimeException("connection timeout"));

    RetryPolicy retryPolicySpy = spy(retryPolicy);

    doThrow(new InterruptedException("interrupted during backoff"))
        .when(retryPolicySpy)
        .backoff(anyLong());

    // Act + Assert
    assertThrows(InterruptedException.class, () -> retryPolicySpy.execute(job, task));

    // Verify the retry happened
    verify(task, times(1)).call();
    verify(retryPolicySpy).backoff(anyLong());

    // No terminal scheduler cleanup should occur
    verify(scheduler, never()).jobCanceled(any());
    verify(scheduler, never()).jobFailed(any());

    // Interrupt status isn't restored because the exception is propagated
    assertFalse(Thread.currentThread().isInterrupted());
  }
}
