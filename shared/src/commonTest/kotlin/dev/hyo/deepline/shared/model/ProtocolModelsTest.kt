package dev.hyo.deepline.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json

class ProtocolModelsTest {
  private val json = Json { ignoreUnknownKeys = true }

  @Test
  fun `encrypted envelope round trips through json`() {
    val envelope = EncryptedEnvelope(
      messageId = "msg_1",
      conversationId = "conv_1",
      senderUserId = "user_1",
      senderDeviceId = "device_1",
      ciphertext = "ZmFrZUNpcGhlcnRleHQ=",
      encryptionMetadata = mapOf("nonce" to "ZmFrZU5vbmNl"),
      protocolVersion = "signal-v1",
      attachments = listOf(
        EncryptedAttachmentReference(
          attachmentId = "att_1",
          ciphertextDigest = "ZmFrZURpZ2VzdA==",
          ciphertextByteLength = 32,
          metadataCiphertext = "ZmFrZU1ldGFkYXRh",
          protocolVersion = "file-v1",
        ),
      ),
      createdAtEpochMs = 1_747_000_000_000,
    )

    val decoded = json.decodeFromString<EncryptedEnvelope>(
      json.encodeToString(EncryptedEnvelope.serializer(), envelope),
    )

    assertEquals(envelope, decoded)
  }
}
