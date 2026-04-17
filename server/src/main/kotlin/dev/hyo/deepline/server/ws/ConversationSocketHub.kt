package dev.hyo.deepline.server.ws

import dev.hyo.deepline.shared.model.EncryptedEnvelope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import java.util.concurrent.ConcurrentHashMap

class ConversationSocketHub {
  private val subscribers =
    ConcurrentHashMap<String, MutableSet<Channel<EncryptedEnvelope>>>()

  // Bounded concurrency for large group fanout (max 50 concurrent sends)
  private val fanoutSemaphore = Semaphore(50)

  suspend fun publish(envelope: EncryptedEnvelope) {
    val channels = subscribers[envelope.conversationId]?.toList() ?: return

    if (channels.size <= 10) {
      // Small group: sequential send is efficient enough
      channels.forEach { channel ->
        runCatching { channel.send(envelope) }
      }
    } else {
      // Large group: parallel fanout with bounded concurrency
      coroutineScope {
        channels.forEach { channel ->
          launch {
            fanoutSemaphore.acquire()
            try {
              runCatching { channel.send(envelope) }
            } finally {
              fanoutSemaphore.release()
            }
          }
        }
      }
    }
  }

  fun subscribe(conversationId: String): ReceiveChannel<EncryptedEnvelope> {
    val channel = Channel<EncryptedEnvelope>(Channel.BUFFERED)
    subscribers.computeIfAbsent(conversationId) { linkedSetOf() }.add(channel)
    return channel
  }

  fun unsubscribe(
    conversationId: String,
    channel: ReceiveChannel<EncryptedEnvelope>,
  ) {
    val typedChannel = channel as? Channel<EncryptedEnvelope> ?: return
    subscribers[conversationId]?.remove(typedChannel)
    typedChannel.close()
  }
}
