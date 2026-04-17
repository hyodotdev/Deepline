package dev.hyo.deepline.server.rate

import dev.hyo.deepline.server.DeeplineServerConfig
import dev.hyo.deepline.server.RateLimiterMode
import redis.clients.jedis.JedisPooled

fun createRateLimiterRuntime(config: DeeplineServerConfig): RateLimiterRuntime {
  return when (config.rateLimiterMode) {
    RateLimiterMode.MEMORY -> RateLimiterRuntime(InMemoryRateLimiter())
    RateLimiterMode.REDIS -> {
      val redisUrl = requireNotNull(config.redisUrl) {
        "DEEPLINE_REDIS_URL is required in REDIS rate limiter mode."
      }
      val jedis = JedisPooled(redisUrl)
      RateLimiterRuntime(
        rateLimiter = RedisRateLimiter(jedis),
        closeAction = jedis::close,
      )
    }
  }
}
