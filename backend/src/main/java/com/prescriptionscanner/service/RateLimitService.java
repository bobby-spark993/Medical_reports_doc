package com.prescriptionscanner.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

/**
 * In-memory sliding-window rate limiter.
 *
 * <p>Fine for a single instance. Behind a load balancer, swap the map for Redis
 * (or Bucket4j) -- the {@link #check} signature can stay the same.
 */
@Service
public class RateLimitService {

	private record Window(Deque<Instant> hits) {
	}

	private final Map<String, Window> buckets = new ConcurrentHashMap<>();

	public record Decision(boolean allowed, int remaining, long retryAfterSeconds) {
	}

	public Decision check(String key, int max, Duration window) {
		Instant now = Instant.now();
		Window w = buckets.computeIfAbsent(key, k -> new Window(new ArrayDeque<>()));

		synchronized (w) {
			Deque<Instant> hits = w.hits();
			Instant cutoff = now.minus(window);
			while (!hits.isEmpty() && hits.peekFirst().isBefore(cutoff)) {
				hits.pollFirst();
			}
			hits.addLast(now);

			if (hits.size() > max) {
				Instant oldest = hits.peekFirst();
				long retry = Math.max(1, Duration.between(now, oldest.plus(window)).toSeconds());
				return new Decision(false, 0, retry);
			}
			return new Decision(true, max - hits.size(), 0);
		}
	}

	/** Periodic cleanup so a long-running process does not leak keys. */
	public void evictStale(Duration maxWindow) {
		Instant cutoff = Instant.now().minus(maxWindow);
		buckets.entrySet().removeIf(entry -> {
			Window w = entry.getValue();
			synchronized (w) {
				Deque<Instant> hits = w.hits();
				while (!hits.isEmpty() && hits.peekFirst().isBefore(cutoff)) {
					hits.pollFirst();
				}
				return hits.isEmpty();
			}
		});
	}

	public int trackedKeys() {
		return buckets.size();
	}
}
