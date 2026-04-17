package dev.hyo.deepline.server.rate

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class InMemoryRateLimiter : RateLimiter {
  private val counters = ConcurrentHashMap<String, AtomicInteger>()

  override fun check(
    scope: String,
    subject: String,
    limit: Int,
    windowSeconds: Int,
  ): RateLimitDecision {
    val now = Instant.now().epochSecond
    val windowStart = now / windowSeconds
    val key = "$scope:$subject:$windowStart"
    val counter = counters.computeIfAbsent(key) { AtomicInteger(0) }
    val current = counter.incrementAndGet()
    val retryAfter = windowSeconds - (now % windowSeconds)
    return RateLimitDecision(
      allowed = current <= limit,
      retryAfterSeconds = retryAfter,
    )
  }
}
