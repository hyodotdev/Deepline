package dev.hyo.deepline.android.ui

import android.util.Base64
import dev.hyo.deepline.shared.model.AddContactByInviteCodeCommand
import dev.hyo.deepline.shared.model.AddGroupMembersCommand
import dev.hyo.deepline.shared.model.AggregatedReadReceipt
import dev.hyo.deepline.shared.model.ContactRecord
import dev.hyo.deepline.shared.model.VerifyOtpResponse
import dev.hyo.deepline.shared.model.ConversationDescriptor
import dev.hyo.deepline.shared.model.CreateConversationCommand
import dev.hyo.deepline.shared.model.CreateInviteCodeCommand
import dev.hyo.deepline.shared.model.DeviceBundle
import dev.hyo.deepline.shared.model.EncryptedEnvelope
import dev.hyo.deepline.shared.model.GroupMemberPage
import dev.hyo.deepline.shared.model.InviteCodeRecord
import dev.hyo.deepline.shared.model.LeaveConversationCommand
import dev.hyo.deepline.shared.model.MentionNotification
import dev.hyo.deepline.shared.model.PreKeyBundleRecord
import dev.hyo.deepline.shared.model.PublishPreKeyBundleCommand
import dev.hyo.deepline.shared.model.PublishedOneTimePreKey
import dev.hyo.deepline.shared.model.RegisterDeviceCommand
import dev.hyo.deepline.shared.model.RegisterUserCommand
import dev.hyo.deepline.shared.model.RegisteredDeviceRecord
import dev.hyo.deepline.shared.model.RemoveGroupMembersCommand
import dev.hyo.deepline.shared.model.SendEncryptedMessageCommand
import dev.hyo.deepline.shared.model.UpdateConversationSettingsCommand
import dev.hyo.deepline.shared.model.UpdateMemberRoleCommand
import dev.hyo.deepline.shared.model.UserRecord
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.accept
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

sealed class DeeplineClientError(message: String) : Exception(message) {
  class InvalidUrl(value: String) : DeeplineClientError("Invalid server URL: $value")
  class HttpFailure(val code: Int, body: String) :
    DeeplineClientError(if (body.isBlank()) "HTTP $code" else "HTTP $code: $body")
}

object LocalOpaqueCodec {
  fun encode(plaintext: String): String =
    "devb64:" + Base64.encodeToString(plaintext.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

  fun decode(ciphertext: String?): String? {
    if (ciphertext == null || !ciphertext.startsWith("devb64:")) return null
    val base64 = ciphertext.removePrefix("devb64:")
    return runCatching {
      String(Base64.decode(base64, Base64.DEFAULT), Charsets.UTF_8)
    }.getOrNull()
  }
}

fun makeLocalDevBundle(userId: String, deviceId: String): DeviceBundle {
  fun token(prefix: String): String =
    "${prefix}_${UUID.randomUUID().toString().replace("-", "").lowercase()}"

  return DeviceBundle(
    userId = userId,
    deviceId = deviceId,
    identityKey = token("identity"),
    signedPreKey = token("signed_prekey"),
    signedPreKeySignature = token("signed_signature"),
    signingPublicKey = token("signing_public"),
    oneTimePreKeys = (1..5).map { index ->
      PublishedOneTimePreKey(
        keyId = "otk_${index}_$deviceId",
        publicKey = token("otk"),
      )
    },
    protocolVersion = "dev-local-v1",
  )
}

private val json = Json {
  ignoreUnknownKeys = true
  encodeDefaults = true
}

@Serializable
data class HealthResponse(
  val status: String,
  val environment: String,
  val storeMode: String,
  val rateLimiterMode: String,
)

@Serializable
data class SendOtpRequest(
  val phoneNumber: String,
  val countryCode: String,
)

@Serializable
data class SendOtpResponse(
  val verificationId: String,
  val expiresAtEpochMs: Long,
  val message: String,
)

@Serializable
data class VerifyOtpRequest(
  val verificationId: String,
  val otpCode: String,
)

@Serializable
data class RegisterPushTokenRequest(
  val userId: String,
  val deviceId: String,
  val platform: String,
  val token: String,
)

class DeeplineServerClient {
  private val client = HttpClient(OkHttp) {
    install(ContentNegotiation) {
      json(json)
    }
    install(HttpTimeout) {
      requestTimeoutMillis = 4_000
      connectTimeoutMillis = 4_000
      socketTimeoutMillis = 4_000
    }
    install(WebSockets)
    expectSuccess = false
  }

  private val activeConnections = ConcurrentHashMap<String, Boolean>()

  suspend fun health(baseUrl: String): HealthResponse =
    get(baseUrl, "/healthz")

  suspend fun registerUser(baseUrl: String, command: RegisterUserCommand): UserRecord =
    post(baseUrl, "/v1/users", command)

  suspend fun registerDevice(baseUrl: String, command: RegisterDeviceCommand): RegisteredDeviceRecord =
    post(baseUrl, "/v1/devices", command)

  suspend fun publishPreKeys(baseUrl: String, command: PublishPreKeyBundleCommand): PreKeyBundleRecord =
    post(baseUrl, "/v1/prekeys", command)

  suspend fun createConversation(baseUrl: String, command: CreateConversationCommand): ConversationDescriptor =
    post(baseUrl, "/v1/conversations", command)

  suspend fun listConversations(baseUrl: String, userId: String): List<ConversationDescriptor> =
    get(baseUrl, "/v1/users/$userId/conversations")

  suspend fun listMessages(baseUrl: String, conversationId: String): List<EncryptedEnvelope> =
    get(baseUrl, "/v1/conversations/$conversationId/messages")

  suspend fun sendMessage(baseUrl: String, command: SendEncryptedMessageCommand): EncryptedEnvelope =
    post(baseUrl, "/v1/messages", command)

  suspend fun createInvite(baseUrl: String, command: CreateInviteCodeCommand): InviteCodeRecord =
    post(baseUrl, "/v1/invites", command)

  suspend fun importInvite(baseUrl: String, command: AddContactByInviteCodeCommand): List<ContactRecord> =
    post(baseUrl, "/v1/contacts/by-invite", command)

  // Group member management

  suspend fun listConversationMembers(
    baseUrl: String,
    conversationId: String,
    offset: Int = 0,
    limit: Int = 50,
  ): GroupMemberPage =
    get(baseUrl, "/v1/conversations/$conversationId/members?offset=$offset&limit=$limit")

  suspend fun addConversationMembers(
    baseUrl: String,
    conversationId: String,
    command: AddGroupMembersCommand,
  ): GroupMemberPage =
    post(baseUrl, "/v1/conversations/$conversationId/members", command)

  suspend fun removeConversationMembers(
    baseUrl: String,
    conversationId: String,
    command: RemoveGroupMembersCommand,
  ): GroupMemberPage =
    delete(baseUrl, "/v1/conversations/$conversationId/members", command)

  suspend fun updateMemberRole(
    baseUrl: String,
    conversationId: String,
    userId: String,
    command: UpdateMemberRoleCommand,
  ): GroupMemberPage =
    patch(baseUrl, "/v1/conversations/$conversationId/members/$userId", command)

  suspend fun leaveConversation(
    baseUrl: String,
    conversationId: String,
    command: LeaveConversationCommand,
  ): ConversationDescriptor =
    post(baseUrl, "/v1/conversations/$conversationId/leave", command)

  suspend fun updateConversationSettings(
    baseUrl: String,
    conversationId: String,
    command: UpdateConversationSettingsCommand,
  ): ConversationDescriptor =
    patch(baseUrl, "/v1/conversations/$conversationId", command)

  // Mentions and aggregated receipts

  suspend fun listMentionsForUser(
    baseUrl: String,
    userId: String,
    offset: Int = 0,
    limit: Int = 50,
  ): List<MentionNotification> =
    get(baseUrl, "/v1/users/$userId/mentions?offset=$offset&limit=$limit")

  suspend fun getAggregatedReadReceipt(
    baseUrl: String,
    messageId: String,
  ): AggregatedReadReceipt =
    get(baseUrl, "/v1/messages/$messageId/receipts/aggregated")

  // Phone authentication

  suspend fun sendPhoneOtp(
    baseUrl: String,
    phoneNumber: String,
    countryCode: String,
  ): SendOtpResponse =
    post(baseUrl, "/v1/auth/phone/send-code", SendOtpRequest(phoneNumber, countryCode))

  suspend fun verifyPhoneOtp(
    baseUrl: String,
    verificationId: String,
    otpCode: String,
  ): VerifyOtpResponse =
    post(baseUrl, "/v1/auth/phone/verify", VerifyOtpRequest(verificationId, otpCode))

  // WebSocket methods

  fun connectToConversation(baseUrl: String, conversationId: String): Flow<EncryptedEnvelope> = flow {
    val wsUrl = normalizeBaseUrl(baseUrl)
      .replace("http://", "ws://")
      .replace("https://", "wss://")
    val fullPath = "/v1/ws/conversations/$conversationId"

    activeConnections[conversationId] = true

    try {
      client.webSocket(urlString = "$wsUrl$fullPath") {
        while (isActive && activeConnections[conversationId] == true) {
          try {
            val frame = incoming.receive()
            if (frame is Frame.Text) {
              val text = frame.readText()
              runCatching {
                json.decodeFromString(EncryptedEnvelope.serializer(), text)
              }.onSuccess { envelope ->
                emit(envelope)
              }
            }
          } catch (_: ClosedReceiveChannelException) {
            break
          }
        }
      }
    } finally {
      activeConnections.remove(conversationId)
    }
  }

  fun disconnectFromConversation(conversationId: String) {
    activeConnections.remove(conversationId)
  }

  fun isConnectedToConversation(conversationId: String): Boolean =
    activeConnections[conversationId] == true

  // Push token registration

  suspend fun registerPushToken(
    baseUrl: String,
    userId: String,
    deviceId: String,
    platform: String,
    token: String,
  ): Unit = post(baseUrl, "/v1/devices/$deviceId/push-token", RegisterPushTokenRequest(userId, deviceId, platform, token))

  suspend fun deletePushToken(
    baseUrl: String,
    deviceId: String,
  ): Unit {
    val url = normalizeBaseUrl(baseUrl) + "/v1/devices/$deviceId/push-token"
    val response = client.delete(url) {
      accept(ContentType.Application.Json)
    }
    val responseBody = response.bodyAsTextSafe()
    ensureSuccess(response.status, responseBody)
  }

  private suspend inline fun <reified Response> get(baseUrl: String, path: String): Response {
    val url = normalizeBaseUrl(baseUrl) + path
    val response = client.get(url) {
      accept(ContentType.Application.Json)
    }
    val responseBody = response.bodyAsTextSafe()
    ensureSuccess(response.status, responseBody)
    return json.decodeFromString(responseBody)
  }

  private suspend inline fun <reified Response, reified Body : Any> post(
    baseUrl: String,
    path: String,
    body: Body,
  ): Response {
    val url = normalizeBaseUrl(baseUrl) + path
    val response = client.post(url) {
      header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
      accept(ContentType.Application.Json)
      setBody(body)
    }
    val responseBody = response.bodyAsTextSafe()
    ensureSuccess(response.status, responseBody)
    return json.decodeFromString(responseBody)
  }

  private suspend inline fun <reified Response, reified Body : Any> delete(
    baseUrl: String,
    path: String,
    body: Body,
  ): Response {
    val url = normalizeBaseUrl(baseUrl) + path
    val response = client.delete(url) {
      header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
      accept(ContentType.Application.Json)
      setBody(body)
    }
    val responseBody = response.bodyAsTextSafe()
    ensureSuccess(response.status, responseBody)
    return json.decodeFromString(responseBody)
  }

  private suspend inline fun <reified Response, reified Body : Any> patch(
    baseUrl: String,
    path: String,
    body: Body,
  ): Response {
    val url = normalizeBaseUrl(baseUrl) + path
    val response = client.patch(url) {
      header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
      accept(ContentType.Application.Json)
      setBody(body)
    }
    val responseBody = response.bodyAsTextSafe()
    ensureSuccess(response.status, responseBody)
    return json.decodeFromString(responseBody)
  }

  private fun normalizeBaseUrl(baseUrl: String): String {
    val trimmed = baseUrl.trim().trimEnd('/')
    if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
      throw DeeplineClientError.InvalidUrl(baseUrl)
    }
    return trimmed
  }

  private fun ensureSuccess(status: HttpStatusCode, body: String) {
    if (status.value !in 200..299) {
      throw DeeplineClientError.HttpFailure(status.value, body)
    }
  }
}

private suspend fun io.ktor.client.statement.HttpResponse.bodyAsTextSafe(): String =
  body<String>()
