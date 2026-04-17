package dev.hyo.deepline.server.store

class StoreRuntime(
  val store: DeeplineStore,
  private val closeAction: () -> Unit = {},
) : AutoCloseable {
  override fun close() {
    closeAction()
  }
}
