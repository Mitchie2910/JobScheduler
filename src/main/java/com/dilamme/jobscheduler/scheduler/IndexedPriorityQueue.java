package com.dilamme.jobscheduler.scheduler;

import com.dilamme.jobscheduler.entities.Job;
import java.time.Instant;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class IndexedPriorityQueue implements JobQueue {

  public static final Comparator<Job> DEFAULT_COMPARATOR =
      Comparator.comparingDouble(Job::getEffectivePriority)
          .thenComparing(Comparator.comparing(Job::getScheduledTime))
          .thenComparing(Comparator.comparing(Job::getCreatedAt));

  private final List<Job> heap;
  private final Map<UUID, Integer> positionOf;
  private final Comparator<Job> order;

  public IndexedPriorityQueue() {
    this(16, DEFAULT_COMPARATOR);
  }

  public IndexedPriorityQueue(Comparator<Job> comparator) {
    this(16, comparator);
  }

  public IndexedPriorityQueue(int initialCapacity, Comparator<Job> order) {
    this.heap = new ArrayList<>(Math.max(initialCapacity, 1));
    this.positionOf = new HashMap<>();
    this.order = order;
  }

  public Job peek(int idx) {
    if (heap.isEmpty()) {
      throw new NoSuchElementException("Priority Queue is Empty");
    }
    return heap.getFirst();
  }

  @Override
  public synchronized void insert(Job job) {
    if (job == null) {
      throw new IllegalArgumentException("job cannot be null: ");
    }
    if (positionOf.containsKey(job.getId())) {
      throw new IllegalArgumentException("Job with id " + job.getId() + " is already in the queue");
    }
    heap.add(job);
    int idx = heap.size() - 1;
    positionOf.put(job.getId(), idx);
    swim(idx);
    notify();
    log.info("Job {} inserted", job.getId());
  }

  @Override
  public synchronized Job extractMax() throws InterruptedException {
    while (heap.isEmpty()) {
      wait();
    }
    Job max = heap.getFirst();
    int last = heap.size() - 1;
    swap(0, last);
    heap.remove(last);
    positionOf.remove(max.getId());
    if (!heap.isEmpty()) {
      sink(0);
    }
    return max;
  }

  @Override
  public Job remove(UUID jobId) {
    Integer idx = positionOf.get(jobId);
    if (idx == null) {
      return null;
    }
    Job removed = heap.get(idx);
    int last = heap.size() - 1;
    swap(idx, last);
    heap.remove(last);
    positionOf.remove(jobId);

    if (idx < heap.size()) {
      sink(idx);
      swim(idx);
    }
    return removed;
  }

  public List<Job> showQueue() {
    return heap;
  }

  @Override
  public void reclassifyAll() {
    Instant now = Instant.now();

    for (Job job : heap) {
      job.refresh(now);
    }
    for (int i = heap.size() / 2 - 1; i >= 0; i--) {
      sink(i);
    }
  }

  private void reposition(int idx, int oldPriority) {
    Job job = heap.get(idx);
    if (job.getPriority() < oldPriority) {
      sink(idx);
    } else if (job.getPriority() > oldPriority) {
      swim(idx);
    }
  }

  private void swim(int i) {
    while (i > 0) {
      int parent = (i / 2);
      if (order.compare(heap.get(i), heap.get(parent)) < 0) {
        swap(i, parent);
        i = parent;
      } else {
        break;
      }
    }
  }

  private void sink(int i) {
    int n = heap.size();
    while (true) {
      int left = 2 * i + 1;
      int right = 2 * i + 2;
      int best = i;

      if (left < n && order.compare(heap.get(left), heap.get(best)) < 0) {
        best = left;
      }
      if (right < n && order.compare(heap.get(right), heap.get(best)) < 0) {
        best = right;
      }
      if (best == i) {
        break;
      }
      swap(i, best);
      i = best;
    }
  }

  private void swap(int i, int j) {
    Job temp = heap.get(i);
    heap.set(i, heap.get(j));
    heap.set(j, temp);
    positionOf.put(heap.get(i).getId(), i);
    positionOf.put(heap.get(j).getId(), j);
  }
}
