package dev.hyo.deepline.server.blob

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

class LocalFilesystemBlobStore(
  private val root: Path,
) : BlobStore {
  init {
    Files.createDirectories(root)
  }

  override fun put(
    storageKey: String,
    bytes: ByteArray,
  ): StoredBlob {
    val path = resolvePath(storageKey)
    Files.createDirectories(path.parent)
    Files.write(
      path,
      bytes,
      StandardOpenOption.CREATE,
      StandardOpenOption.TRUNCATE_EXISTING,
      StandardOpenOption.WRITE,
    )

    return StoredBlob(
      storageKey = storageKey,
      ciphertextByteLength = bytes.size.toLong(),
      ciphertextDigest = sha256(bytes),
    )
  }

  override fun get(storageKey: String): ByteArray {
    val path = resolvePath(storageKey)
    require(Files.exists(path)) {
      "Blob $storageKey was not found."
    }
    return Files.readAllBytes(path)
  }

  private fun resolvePath(storageKey: String): Path {
    return root.resolve(storageKey)
  }

  private fun sha256(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return buildString(digest.size * 2) {
      digest.forEach { byte ->
        append("%02x".format(byte))
      }
    }
  }
}
