package dev.hyo.deepline.server.rate

class RateLimiterRuntime(
  val rateLimiter: RateLimiter,
  private val closeAction: () -> Unit = {},
) : AutoCloseable {
  override fun close() {
    closeAction()
  }
}
