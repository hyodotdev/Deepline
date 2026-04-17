package dev.hyo.deepline.server.routes

import dev.hyo.deepline.server.DeeplineServerConfig
import dev.hyo.deepline.server.blob.BlobStore
import dev.hyo.deepline.server.rate.RateLimiter
import dev.hyo.deepline.server.store.DeeplineStore
import dev.hyo.deepline.shared.model.AttachmentMetadataCommand
import dev.hyo.deepline.shared.model.AttachmentUploadSession
import dev.hyo.deepline.shared.model.CreateAttachmentUploadSessionCommand
import io.ktor.http.ContentType
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable

@Serializable
data class AttachmentUploadSessionResponse(
  val session: AttachmentUploadSession,
  val uploadPath: String,
)

fun Route.installAttachmentRoutes(
  config: DeeplineServerConfig,
  store: DeeplineStore,
  blobStore: BlobStore,
  rateLimiter: RateLimiter,
) {
  route("/v1") {
    post("/attachments/upload-sessions") {
      val command = call.receive<CreateAttachmentUploadSessionCommand>()
      enforceRateLimit(
        call = call,
        rateLimiter = rateLimiter,
        scope = "create_upload_session",
        subject = rateLimitSubject(call, command.ownerUserId),
        limit = config.uploadSessionLimitPerMinute,
        windowSeconds = 60,
      )
      require(command.expectedCiphertextByteLength <= config.maxEncryptedUploadBytes) {
        "Encrypted upload exceeds the configured size limit."
      }

      val session = store.createAttachmentUploadSession(command)
      call.respond(
        AttachmentUploadSessionResponse(
          session = session,
          uploadPath = "/v1/uploads/${session.sessionId}",
        ),
      )
    }

    put("/uploads/{sessionId}") {
      val sessionId = requireNotNull(call.parameters["sessionId"]) {
        "sessionId is required"
      }
      val uploadToken = requireNotNull(call.request.headers["x-deepline-upload-token"]) {
        "x-deepline-upload-token is required"
      }
      val session = store.getAttachmentUploadSession(sessionId)
      enforceRateLimit(
        call = call,
        rateLimiter = rateLimiter,
        scope = "upload_blob",
        subject = rateLimitSubject(call, session.ownerUserId),
        limit = config.blobUploadLimitPerMinute,
        windowSeconds = 60,
      )

      val bytes = call.receive<ByteArray>()
      require(bytes.size.toLong() <= config.maxEncryptedUploadBytes) {
        "Encrypted upload exceeds the configured size limit."
      }

      val storageKey = "blob_${session.conversationId}_${session.sessionId}.bin"
      val storedBlob = blobStore.put(storageKey, bytes)
      val receipt = store.completeAttachmentUpload(
        sessionId = sessionId,
        uploadToken = uploadToken,
        storageKey = storedBlob.storageKey,
        ciphertextByteLength = storedBlob.ciphertextByteLength,
        ciphertextDigest = storedBlob.ciphertextDigest,
        completedAtEpochMs = System.currentTimeMillis(),
      )
      call.respond(receipt)
    }

    get("/blobs/{storageKey}") {
      val storageKey = requireNotNull(call.parameters["storageKey"]) {
        "storageKey is required"
      }
      val bytes = blobStore.get(storageKey)
      call.respondBytes(
        bytes = bytes,
        contentType = ContentType.Application.OctetStream,
      )
    }

    post("/attachments/metadata") {
      val command = call.receive<AttachmentMetadataCommand>()
      rejectDemoAttachmentIfNeeded(config, command)
      call.respond(store.storeAttachmentMetadata(command))
    }

    get("/conversations/{conversationId}/attachments") {
      val conversationId = requireNotNull(call.parameters["conversationId"]) {
        "conversationId is required"
      }

      call.respond(store.listAttachments(conversationId))
    }
  }
}
