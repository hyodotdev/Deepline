package dev.hyo.deepline.server.rate

data class RateLimitDecision(
  val allowed: Boolean,
  val retryAfterSeconds: Long,
)

interface RateLimiter {
  fun check(
    scope: String,
    subject: String,
    limit: Int,
    windowSeconds: Int,
  ): RateLimitDecision
}
