package dev.hyo.deepline.server.blob

data class StoredBlob(
  val storageKey: String,
  val ciphertextByteLength: Long,
  val ciphertextDigest: String,
)

interface BlobStore {
  fun put(
    storageKey: String,
    bytes: ByteArray,
  ): StoredBlob

  fun get(storageKey: String): ByteArray
}
