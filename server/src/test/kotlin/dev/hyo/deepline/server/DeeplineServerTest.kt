package dev.hyo.deepline.server

import dev.hyo.deepline.server.RateLimiterMode
import dev.hyo.deepline.server.StoreMode
import dev.hyo.deepline.server.routes.AttachmentUploadSessionResponse
import dev.hyo.deepline.shared.model.AddContactByInviteCodeCommand
import dev.hyo.deepline.shared.model.AttachmentMetadataCommand
import dev.hyo.deepline.shared.model.CreateAttachmentUploadSessionCommand
import dev.hyo.deepline.shared.model.ConversationDescriptor
import dev.hyo.deepline.shared.model.CreateConversationCommand
import dev.hyo.deepline.shared.model.CreateInviteCodeCommand
import dev.hyo.deepline.shared.model.DeviceBundle
import dev.hyo.deepline.shared.model.EncryptedAttachmentMetadata
import dev.hyo.deepline.shared.model.EncryptedEnvelope
import dev.hyo.deepline.shared.model.InviteCodeRecord
import dev.hyo.deepline.shared.model.MarkMessageReadCommand
import dev.hyo.deepline.shared.model.PreKeyBundleRecord
import dev.hyo.deepline.shared.model.ProtocolType
import dev.hyo.deepline.shared.model.PublishPreKeyBundleCommand
import dev.hyo.deepline.shared.model.PublishedOneTimePreKey
import dev.hyo.deepline.shared.model.RegisterDeviceCommand
import dev.hyo.deepline.shared.model.RegisterUserCommand
import dev.hyo.deepline.shared.model.SendEncryptedMessageCommand
import dev.hyo.deepline.shared.model.UploadedBlobReceipt
import dev.hyo.deepline.shared.model.UserRecord
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json

class DeeplineServerTest {
  private val json = Json { ignoreUnknownKeys = true }

  @Test
  fun `health endpoint exposes enforcement mode`() = testApplication {
    application {
      deeplineModule(localConfig())
    }

    val client = createClient {
      expectSuccess = false
    }

    val response = client.get("/healthz")

    assertEquals(HttpStatusCode.OK, response.status)
    assertContains(response.bodyAsText(), "\"status\"")
    assertContains(response.bodyAsText(), "\"rateLimiterMode\"")
  }

  @Test
  fun `directory and messaging contracts round trip encrypted records`() = testApplication {
    application {
      deeplineModule(localConfig())
    }

    assertDirectoryAndMessagingRoundTrip()
  }

  @Test
  fun `postgres store mode round trips encrypted records`() = testApplication {
    application {
      deeplineModule(postgresConfig())
    }

    assertDirectoryAndMessagingRoundTrip()
  }

  @Test
  fun `strict mode rejects demo bundles messages and unfinished groups`() = testApplication {
    application {
      deeplineModule(productionConfig())
    }

    val client = createClient {
      expectSuccess = false
    }

    val alice = registerUser(client, "fp_alice")
    val bob = registerUser(client, "fp_bob")

    val demoDeviceResponse = postJson(
      client,
      "/v1/devices",
      RegisterDeviceCommand(
        alice.userId,
        sampleBundle(alice.userId, "alice_demo", "demo-signal-v1"),
      ),
    )
    assertEquals(HttpStatusCode.BadRequest, demoDeviceResponse.status)

    val aliceBundle = sampleBundle(alice.userId, "alice_phone", "signal-v1")
    val bobBundle = sampleBundle(bob.userId, "bob_phone", "signal-v1")

    postJson(client, "/v1/devices", RegisterDeviceCommand(alice.userId, aliceBundle))
    postJson(client, "/v1/devices", RegisterDeviceCommand(bob.userId, bobBundle))

    val groupResponse = postJson(
      client,
      "/v1/conversations",
      CreateConversationCommand(
        createdByUserId = alice.userId,
        participantUserIds = listOf(bob.userId),
        protocolType = ProtocolType.MLS_GROUP,
        encryptedTitle = "b64:group",
      ),
    )
    assertEquals(HttpStatusCode.BadRequest, groupResponse.status)

    val conversation = decodeResponse<ConversationDescriptor>(
      postJson(
        client,
        "/v1/conversations",
        CreateConversationCommand(
          createdByUserId = alice.userId,
          participantUserIds = listOf(bob.userId),
          protocolType = ProtocolType.SIGNAL_1TO1,
        ),
      ),
    )

    val demoMessageResponse = postJson(
      client,
      "/v1/messages",
      SendEncryptedMessageCommand(
        conversationId = conversation.conversationId,
        envelope = EncryptedEnvelope(
          messageId = "msg_demo",
          conversationId = conversation.conversationId,
          senderUserId = alice.userId,
          senderDeviceId = aliceBundle.deviceId,
          ciphertext = "b64:ciphertext",
          encryptionMetadata = mapOf("isDemo" to "true"),
          protocolVersion = "demo-box-v1",
          createdAtEpochMs = 123,
        ),
      ),
    )
    assertEquals(HttpStatusCode.BadRequest, demoMessageResponse.status)
  }

  @Test
  fun `rate limit rejects second message when configured for single write`() = testApplication {
    application {
      deeplineModule(rateLimitedConfig())
    }

    val client = createClient {
      expectSuccess = false
    }

    val alice = registerUser(client, "fp_alice")
    val bob = registerUser(client, "fp_bob")
    val aliceBundle = sampleBundle(alice.userId, "alice_phone", "signal-v1")
    val bobBundle = sampleBundle(bob.userId, "bob_phone", "signal-v1")

    postJson(client, "/v1/devices", RegisterDeviceCommand(alice.userId, aliceBundle))
    postJson(client, "/v1/devices", RegisterDeviceCommand(bob.userId, bobBundle))

    val conversation = decodeResponse<ConversationDescriptor>(
      postJson(
        client,
        "/v1/conversations",
        CreateConversationCommand(
          createdByUserId = alice.userId,
          participantUserIds = listOf(bob.userId),
          protocolType = ProtocolType.SIGNAL_1TO1,
        ),
      ),
    )

    val firstMessageResponse = postJson(
      client,
      "/v1/messages",
      SendEncryptedMessageCommand(
        conversationId = conversation.conversationId,
        envelope = EncryptedEnvelope(
          messageId = "msg_rate_1",
          conversationId = conversation.conversationId,
          senderUserId = alice.userId,
          senderDeviceId = aliceBundle.deviceId,
          ciphertext = "b64:first",
          encryptionMetadata = mapOf("header" to "b64:header"),
          protocolVersion = "signal-v1",
          createdAtEpochMs = 100,
        ),
      ),
    )
    assertEquals(HttpStatusCode.OK, firstMessageResponse.status)

    val secondMessageResponse = postJson(
      client,
      "/v1/messages",
      SendEncryptedMessageCommand(
        conversationId = conversation.conversationId,
        envelope = EncryptedEnvelope(
          messageId = "msg_rate_2",
          conversationId = conversation.conversationId,
          senderUserId = alice.userId,
          senderDeviceId = aliceBundle.deviceId,
          ciphertext = "b64:second",
          encryptionMetadata = mapOf("header" to "b64:header"),
          protocolVersion = "signal-v1",
          createdAtEpochMs = 101,
        ),
      ),
    )

    assertEquals(HttpStatusCode.TooManyRequests, secondMessageResponse.status)
    assertContains(secondMessageResponse.bodyAsText(), "Rate limit exceeded")
  }

  private suspend fun registerUser(
    client: io.ktor.client.HttpClient,
    fingerprint: String,
  ): UserRecord {
    return decodeResponse(
      postJson(
        client,
        "/v1/users",
        RegisterUserCommand(
          identityFingerprint = fingerprint,
          profileCiphertext = "b64:profile",
        ),
      ),
    )
  }

  private suspend fun postJson(
    client: io.ktor.client.HttpClient,
    path: String,
    body: Any,
  ) = client.post(path) {
    contentType(ContentType.Application.Json)
    setBody(json.encodeToString(serializerFor(body), body))
  }

  @Suppress("UNCHECKED_CAST")
  private fun serializerFor(body: Any) = when (body) {
    is RegisterUserCommand -> RegisterUserCommand.serializer()
    is RegisterDeviceCommand -> RegisterDeviceCommand.serializer()
    is PublishPreKeyBundleCommand -> PublishPreKeyBundleCommand.serializer()
    is CreateAttachmentUploadSessionCommand -> CreateAttachmentUploadSessionCommand.serializer()
    is CreateConversationCommand -> CreateConversationCommand.serializer()
    is SendEncryptedMessageCommand -> SendEncryptedMessageCommand.serializer()
    is MarkMessageReadCommand -> MarkMessageReadCommand.serializer()
    is AttachmentMetadataCommand -> AttachmentMetadataCommand.serializer()
    is CreateInviteCodeCommand -> CreateInviteCodeCommand.serializer()
    is AddContactByInviteCodeCommand -> AddContactByInviteCodeCommand.serializer()
    else -> error("No serializer registered for ${body::class.qualifiedName}")
  } as kotlinx.serialization.KSerializer<Any>

  private suspend inline fun <reified T> decodeResponse(response: io.ktor.client.statement.HttpResponse): T {
    val body = response.bodyAsText()
    assertEquals(HttpStatusCode.OK, response.status, body)
    return json.decodeFromString(body)
  }

  private fun sampleBundle(
    userId: String,
    deviceId: String,
    protocolVersion: String,
  ): DeviceBundle {
    return DeviceBundle(
      userId = userId,
      deviceId = deviceId,
      identityKey = "b64:identity_$deviceId",
      signedPreKey = "b64:signed_$deviceId",
      signedPreKeySignature = "b64:signature_$deviceId",
      signingPublicKey = "b64:signing_$deviceId",
      oneTimePreKeys = listOf(
        PublishedOneTimePreKey(
          keyId = "otk_$deviceId",
          publicKey = "b64:otk_$deviceId",
        ),
      ),
      protocolVersion = protocolVersion,
    )
  }

  private suspend fun io.ktor.server.testing.ApplicationTestBuilder.assertDirectoryAndMessagingRoundTrip() {
    val client = createClient {
      expectSuccess = false
    }

    val alice = registerUser(client, "fp_alice")
    val bob = registerUser(client, "fp_bob")

    val aliceBundle = sampleBundle(alice.userId, "alice_phone", "signal-v1")
    val bobBundle = sampleBundle(bob.userId, "bob_phone", "signal-v1")

    postJson(client, "/v1/devices", RegisterDeviceCommand(alice.userId, aliceBundle))
    postJson(client, "/v1/devices", RegisterDeviceCommand(bob.userId, bobBundle))
    postJson(client, "/v1/prekeys", PublishPreKeyBundleCommand(alice.userId, aliceBundle))
    postJson(client, "/v1/prekeys", PublishPreKeyBundleCommand(bob.userId, bobBundle))

    val fetchedBundleResponse = client.get("/v1/prekeys/${bob.userId}/${bobBundle.deviceId}")
    assertEquals(HttpStatusCode.OK, fetchedBundleResponse.status)
    val fetchedBundle = json.decodeFromString<PreKeyBundleRecord>(fetchedBundleResponse.bodyAsText())
    assertEquals(bobBundle.deviceId, fetchedBundle.deviceId)

    val conversation = decodeResponse<ConversationDescriptor>(
      postJson(
        client,
        "/v1/conversations",
        CreateConversationCommand(
          createdByUserId = alice.userId,
          participantUserIds = listOf(bob.userId),
          protocolType = ProtocolType.SIGNAL_1TO1,
          encryptedTitle = "b64:c2VjcmV0",
        ),
      ),
    )

    val userConversationResponse = client.get("/v1/users/${bob.userId}/conversations")
    assertEquals(HttpStatusCode.OK, userConversationResponse.status)
    assertContains(userConversationResponse.bodyAsText(), conversation.conversationId)

    val message = EncryptedEnvelope(
      messageId = "msg_1",
      conversationId = conversation.conversationId,
      senderUserId = alice.userId,
      senderDeviceId = aliceBundle.deviceId,
      ciphertext = "b64:ciphertext",
      encryptionMetadata = mapOf("header" to "b64:header"),
      protocolVersion = "signal-v1",
      createdAtEpochMs = 1_747_000_000_000,
    )

    val ciphertextBytes = "encrypted-ciphertext-blob".encodeToByteArray()
    val uploadSession = decodeResponse<AttachmentUploadSessionResponse>(
      postJson(
        client,
        "/v1/attachments/upload-sessions",
        CreateAttachmentUploadSessionCommand(
          conversationId = conversation.conversationId,
          ownerUserId = alice.userId,
          senderDeviceId = aliceBundle.deviceId,
          expectedCiphertextByteLength = ciphertextBytes.size.toLong(),
        ),
      ),
    )

    val uploadResponse = client.put(uploadSession.uploadPath) {
      contentType(ContentType.Application.OctetStream)
      header("x-deepline-upload-token", uploadSession.session.uploadToken)
      setBody(ciphertextBytes)
    }
    val uploadReceipt = decodeResponse<UploadedBlobReceipt>(uploadResponse)
    assertEquals(ciphertextBytes.size.toLong(), uploadReceipt.ciphertextByteLength)

    val downloadResponse = client.get("/v1/blobs/${uploadReceipt.storageKey}")
    assertEquals(HttpStatusCode.OK, downloadResponse.status)
    assertContentEquals(ciphertextBytes, downloadResponse.bodyAsBytes())

    val sendResponse = postJson(
      client,
      "/v1/messages",
      SendEncryptedMessageCommand(
        conversationId = conversation.conversationId,
        envelope = message,
      ),
    )
    assertEquals(HttpStatusCode.OK, sendResponse.status)

    val receiptResponse = postJson(
      client,
      "/v1/messages/read-receipts",
      MarkMessageReadCommand(
        messageId = message.messageId,
        conversationId = conversation.conversationId,
        userId = bob.userId,
        deviceId = bobBundle.deviceId,
        readAtEpochMs = 1_747_000_100_000,
      ),
    )
    assertEquals(HttpStatusCode.OK, receiptResponse.status)

    val receiptsResponse = client.get("/v1/messages/${message.messageId}/receipts")
    assertEquals(HttpStatusCode.OK, receiptsResponse.status)
    assertContains(receiptsResponse.bodyAsText(), "\"READ\"")

    val attachmentResponse = postJson(
      client,
      "/v1/attachments/metadata",
      AttachmentMetadataCommand(
        conversationId = conversation.conversationId,
        ownerUserId = alice.userId,
        senderDeviceId = aliceBundle.deviceId,
        storageKey = uploadReceipt.storageKey,
        ciphertextDigest = "sha256:b64digest",
        ciphertextByteLength = 1024,
        metadataCiphertext = "b64:metadata",
        protocolVersion = "file-v1",
        messageId = message.messageId,
      ),
    )
    val attachment = decodeResponse<EncryptedAttachmentMetadata>(attachmentResponse)
    assertEquals(uploadReceipt.storageKey, attachment.storageKey)

    val attachmentsResponse = client.get("/v1/conversations/${conversation.conversationId}/attachments")
    assertEquals(HttpStatusCode.OK, attachmentsResponse.status)
    assertContains(attachmentsResponse.bodyAsText(), attachment.attachmentId)

    val invite = decodeResponse<InviteCodeRecord>(
      postJson(
        client,
        "/v1/invites",
        CreateInviteCodeCommand(
          ownerUserId = alice.userId,
          encryptedInvitePayload = "b64:invite_payload",
        ),
      ),
    )

    val addContactResponse = postJson(
      client,
      "/v1/contacts/by-invite",
      AddContactByInviteCodeCommand(
        ownerUserId = bob.userId,
        inviteCode = invite.inviteCode,
        encryptedAlias = "b64:alias",
      ),
    )

    assertEquals(HttpStatusCode.OK, addContactResponse.status)
    assertContains(addContactResponse.bodyAsText(), bob.userId)
    assertContains(addContactResponse.bodyAsText(), alice.userId)
  }

  private fun localConfig(): DeeplineServerConfig {
    return DeeplineServerConfig(
      host = "127.0.0.1",
      port = 8080,
      environment = "local",
      strictCryptoEnforcement = false,
      storeMode = StoreMode.MEMORY,
      rateLimiterMode = RateLimiterMode.MEMORY,
      blobStorageRoot = buildBlobRoot("memory"),
    )
  }

  private fun postgresConfig(): DeeplineServerConfig {
    return DeeplineServerConfig(
      host = "127.0.0.1",
      port = 8080,
      environment = "local",
      strictCryptoEnforcement = false,
      storeMode = StoreMode.POSTGRES,
      rateLimiterMode = RateLimiterMode.MEMORY,
      databaseUrl = "jdbc:h2:mem:deepline;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
      databaseUser = "sa",
      databasePassword = "",
      blobStorageRoot = buildBlobRoot("postgres"),
    )
  }

  private fun productionConfig(): DeeplineServerConfig {
    return DeeplineServerConfig(
      host = "127.0.0.1",
      port = 8080,
      environment = "production",
      strictCryptoEnforcement = true,
      storeMode = StoreMode.MEMORY,
      rateLimiterMode = RateLimiterMode.MEMORY,
      blobStorageRoot = buildBlobRoot("production"),
    )
  }

  private fun rateLimitedConfig(): DeeplineServerConfig {
    return DeeplineServerConfig(
      host = "127.0.0.1",
      port = 8080,
      environment = "local",
      strictCryptoEnforcement = false,
      storeMode = StoreMode.MEMORY,
      rateLimiterMode = RateLimiterMode.MEMORY,
      messageWriteLimitPerMinute = 1,
      blobStorageRoot = buildBlobRoot("rate"),
    )
  }

  private fun buildBlobRoot(suffix: String): String {
    return "${System.getProperty("java.io.tmpdir")}/deepline-test-blobs-$suffix"
  }
}
