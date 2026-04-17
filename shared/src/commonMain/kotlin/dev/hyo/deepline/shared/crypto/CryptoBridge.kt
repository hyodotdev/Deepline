package dev.hyo.deepline.shared.crypto

import dev.hyo.deepline.shared.model.DeviceBundle
import dev.hyo.deepline.shared.model.EncryptedAttachmentReference
import dev.hyo.deepline.shared.model.ProtocolType

interface OneToOneCryptoBridge {
  val protocolVersion: String
  val protocolType: ProtocolType

  suspend fun createDeviceBundle(userId: String, deviceId: String): DeviceBundle

  suspend fun encryptMessage(
    plaintext: String,
    recipientBundle: DeviceBundle,
    attachmentReferences: List<EncryptedAttachmentReference> = emptyList(),
  ): EncryptedPayload

  suspend fun decryptMessage(
    ciphertext: String,
    encryptionMetadata: Map<String, String>,
  ): String
}

interface GroupCryptoBridge {
  val protocolType: ProtocolType
  val isSupported: Boolean
  val blocker: String?
}

interface AttachmentCryptoBridge {
  suspend fun encryptAttachment(
    fileName: String,
    bytes: ByteArray,
    mimeType: String,
  ): EncryptedAttachmentPayload

  suspend fun decryptAttachment(
    ciphertext: ByteArray,
    reference: EncryptedAttachmentReference,
  ): ByteArray
}

data class EncryptedPayload(
  val ciphertext: String,
  val encryptionMetadata: Map<String, String>,
  val protocolVersion: String,
)

data class EncryptedAttachmentPayload(
  val ciphertext: ByteArray,
  val ciphertextDigest: String,
  val ciphertextByteLength: Long,
  val metadataCiphertext: String,
  val protocolVersion: String,
)
