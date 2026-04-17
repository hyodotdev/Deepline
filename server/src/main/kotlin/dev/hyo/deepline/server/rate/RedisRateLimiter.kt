package dev.hyo.deepline.server.rate

import java.time.Instant
import redis.clients.jedis.JedisPooled

class RedisRateLimiter(
  private val jedis: JedisPooled,
) : RateLimiter {
  override fun check(
    scope: String,
    subject: String,
    limit: Int,
    windowSeconds: Int,
  ): RateLimitDecision {
    val now = Instant.now().epochSecond
    val windowStart = now / windowSeconds
    val key = "deepline:rl:$scope:$subject:$windowStart"
    val current = jedis.incr(key)
    if (current == 1L) {
      jedis.expire(key, windowSeconds.toLong())
    }

    val retryAfter = windowSeconds - (now % windowSeconds)
    return RateLimitDecision(
      allowed = current <= limit,
      retryAfterSeconds = retryAfter,
    )
  }
}
