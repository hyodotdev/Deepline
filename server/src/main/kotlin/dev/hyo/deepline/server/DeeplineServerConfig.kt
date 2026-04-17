package dev.hyo.deepline.server

enum class StoreMode {
  MEMORY,
  POSTGRES,
}

enum class RateLimiterMode {
  MEMORY,
  REDIS,
}

data class DeeplineServerConfig(
  val host: String,
  val port: Int,
  val environment: String,
  val strictCryptoEnforcement: Boolean,
  val storeMode: StoreMode,
  val rateLimiterMode: RateLimiterMode,
  val databaseUrl: String? = null,
  val databaseUser: String? = null,
  val databasePassword: String? = null,
  val databaseMaxPoolSize: Int = 10,
  val redisUrl: String? = null,
  val objectStorageBucket: String? = null,
  val blobStorageRoot: String = ".deepline/blobs",
  val maxEncryptedUploadBytes: Long = 25 * 1024 * 1024,
  val messageWriteLimitPerMinute: Int = 120,
  val uploadSessionLimitPerMinute: Int = 30,
  val blobUploadLimitPerMinute: Int = 20,
  val inviteCreateLimitPerHour: Int = 20,
) {
  companion object {
    fun fromEnvironment(): DeeplineServerConfig {
      val environment = System.getenv("DEEPLINE_APP_ENV") ?: "local"
      val strictMode =
        (System.getenv("DEEPLINE_CRYPTO_ENFORCEMENT_MODE") ?: "").lowercase()
      val storeMode = parseStoreMode(System.getenv("DEEPLINE_STORE_MODE"))
      val rateLimiterMode = parseRateLimiterMode(System.getenv("DEEPLINE_RATE_LIMITER_MODE"))

      return DeeplineServerConfig(
        host = System.getenv("DEEPLINE_HOST") ?: "0.0.0.0",
        port = (System.getenv("DEEPLINE_PORT") ?: "8080").toInt(),
        environment = environment,
        strictCryptoEnforcement = strictMode == "strict" || environment.lowercase() == "production",
        storeMode = storeMode,
        rateLimiterMode = rateLimiterMode,
        databaseUrl = System.getenv("DEEPLINE_DATABASE_URL"),
        databaseUser = System.getenv("DEEPLINE_DATABASE_USER"),
        databasePassword = System.getenv("DEEPLINE_DATABASE_PASSWORD"),
        databaseMaxPoolSize = (System.getenv("DEEPLINE_DATABASE_MAX_POOL_SIZE") ?: "10").toInt(),
        redisUrl = System.getenv("DEEPLINE_REDIS_URL"),
        objectStorageBucket = System.getenv("DEEPLINE_OBJECT_STORAGE_BUCKET"),
        blobStorageRoot = System.getenv("DEEPLINE_BLOB_STORAGE_ROOT") ?: ".deepline/blobs",
        maxEncryptedUploadBytes = (System.getenv("DEEPLINE_MAX_ENCRYPTED_UPLOAD_BYTES") ?: "${25 * 1024 * 1024}").toLong(),
        messageWriteLimitPerMinute = (System.getenv("DEEPLINE_MESSAGE_WRITE_LIMIT_PER_MINUTE") ?: "120").toInt(),
        uploadSessionLimitPerMinute = (System.getenv("DEEPLINE_UPLOAD_SESSION_LIMIT_PER_MINUTE") ?: "30").toInt(),
        blobUploadLimitPerMinute = (System.getenv("DEEPLINE_BLOB_UPLOAD_LIMIT_PER_MINUTE") ?: "20").toInt(),
        inviteCreateLimitPerHour = (System.getenv("DEEPLINE_INVITE_CREATE_LIMIT_PER_HOUR") ?: "20").toInt(),
      )
    }

    private fun parseStoreMode(value: String?): StoreMode {
      return when (value?.trim()?.uppercase()) {
        null, "", "MEMORY", "IN_MEMORY" -> StoreMode.MEMORY
        "POSTGRES", "POSTGRESQL" -> StoreMode.POSTGRES
        else -> throw IllegalArgumentException("Unsupported DEEPLINE_STORE_MODE: $value")
      }
    }

    private fun parseRateLimiterMode(value: String?): RateLimiterMode {
      return when (value?.trim()?.uppercase()) {
        null, "", "MEMORY", "IN_MEMORY" -> RateLimiterMode.MEMORY
        "REDIS" -> RateLimiterMode.REDIS
        else -> throw IllegalArgumentException("Unsupported DEEPLINE_RATE_LIMITER_MODE: $value")
      }
    }
  }
}
