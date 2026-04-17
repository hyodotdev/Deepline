package dev.hyo.deepline.server.store

import dev.hyo.deepline.server.DeeplineServerConfig
import dev.hyo.deepline.shared.model.AddContactByInviteCodeCommand
import dev.hyo.deepline.shared.model.AddGroupMembersCommand
import dev.hyo.deepline.shared.model.AggregatedReadReceipt
import dev.hyo.deepline.shared.model.AttachmentMetadataCommand
import dev.hyo.deepline.shared.model.AttachmentUploadSession
import dev.hyo.deepline.shared.model.ContactRecord
import dev.hyo.deepline.shared.model.ConversationDescriptor
import dev.hyo.deepline.shared.model.CreateAttachmentUploadSessionCommand
import dev.hyo.deepline.shared.model.CreateConversationCommand
import dev.hyo.deepline.shared.model.CreateInviteCodeCommand
import dev.hyo.deepline.shared.model.EncryptedAttachmentMetadata
import dev.hyo.deepline.shared.model.EncryptedEnvelope
import dev.hyo.deepline.shared.model.GroupMember
import dev.hyo.deepline.shared.model.GroupMemberPage
import dev.hyo.deepline.shared.model.GroupRole
import dev.hyo.deepline.shared.model.InviteCodeRecord
import dev.hyo.deepline.shared.model.LeaveConversationCommand
import dev.hyo.deepline.shared.model.MarkMessageReadCommand
import dev.hyo.deepline.shared.model.MentionNotification
import dev.hyo.deepline.shared.model.MessageReceipt
import dev.hyo.deepline.shared.model.PhoneAuthenticatedUser
import dev.hyo.deepline.shared.model.PhoneVerificationRequest
import dev.hyo.deepline.shared.model.PhoneVerificationResponse
import dev.hyo.deepline.shared.model.PhoneVerificationStatus
import dev.hyo.deepline.shared.model.PreKeyBundleRecord
import dev.hyo.deepline.shared.model.ProtocolType
import dev.hyo.deepline.shared.model.PublishPreKeyBundleCommand
import dev.hyo.deepline.shared.model.PushPlatform
import dev.hyo.deepline.shared.model.PushToken
import dev.hyo.deepline.shared.model.ReceiptType
import dev.hyo.deepline.shared.model.RegisterDeviceCommand
import dev.hyo.deepline.shared.model.RegisterPushTokenCommand
import dev.hyo.deepline.shared.model.RegisterUserCommand
import dev.hyo.deepline.shared.model.RegisteredDeviceRecord
import dev.hyo.deepline.shared.model.RemoveGroupMembersCommand
import dev.hyo.deepline.shared.model.SendEncryptedMessageCommand
import dev.hyo.deepline.shared.model.UpdateConversationSettingsCommand
import dev.hyo.deepline.shared.model.UpdateMemberRoleCommand
import dev.hyo.deepline.shared.model.UploadedBlobReceipt
import dev.hyo.deepline.shared.model.UserRecord
import dev.hyo.deepline.shared.model.VerifyOtpCommand
import dev.hyo.deepline.shared.model.VerifyOtpResponse
import java.sql.Connection
import java.util.UUID
import javax.sql.DataSource
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

class JdbcDeeplineStore(
  private val dataSource: DataSource,
  private val config: DeeplineServerConfig,
) : DeeplineStore {
  private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
  }

  override fun registerUser(command: RegisterUserCommand): UserRecord = tx { connection ->
    require(command.identityFingerprint.isNotBlank()) {
      "identityFingerprint is required."
    }

    val now = System.currentTimeMillis()
    val user = UserRecord(
      userId = generateId("user"),
      identityFingerprint = command.identityFingerprint,
      profileCiphertext = command.profileCiphertext,
      createdAtEpochMs = now,
    )

    connection.prepareStatement(
      """
      insert into users (
        user_id,
        identity_fingerprint,
        profile_ciphertext,
        created_at_epoch_ms
      ) values (?, ?, ?, ?)
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, user.userId)
      statement.setString(2, user.identityFingerprint)
      statement.setString(3, user.profileCiphertext)
      statement.setLong(4, user.createdAtEpochMs)
      statement.executeUpdate()
    }

    user
  }

  override fun registerDevice(command: RegisterDeviceCommand): RegisteredDeviceRecord = tx { connection ->
    requireKnownUser(connection, command.userId)
    require(command.deviceBundle.userId == command.userId) {
      "Device bundle userId must match registerDevice userId."
    }

    val existingCreatedAt = connection.prepareStatement(
      """
      select created_at_epoch_ms
      from devices
      where user_id = ? and device_id = ?
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, command.userId)
      statement.setString(2, command.deviceBundle.deviceId)
      statement.executeQuery().use { result ->
        if (result.next()) result.getLong("created_at_epoch_ms") else null
      }
    }

    val now = System.currentTimeMillis()
    val record = RegisteredDeviceRecord(
      userId = command.userId,
      deviceBundle = command.deviceBundle,
      createdAtEpochMs = existingCreatedAt ?: now,
      lastSeenAtEpochMs = now,
    )

    if (existingCreatedAt == null) {
      connection.prepareStatement(
        """
        insert into devices (
          user_id,
          device_id,
          bundle_json,
          created_at_epoch_ms,
          last_seen_at_epoch_ms
        ) values (?, ?, ?, ?, ?)
        """.trimIndent(),
      ).use { statement ->
        statement.setString(1, record.userId)
        statement.setString(2, record.deviceBundle.deviceId)
        statement.setString(3, encode(RegisteredDeviceRecord.serializer(), record))
        statement.setLong(4, record.createdAtEpochMs)
        statement.setLong(5, record.lastSeenAtEpochMs)
        statement.executeUpdate()
      }
    } else {
      connection.prepareStatement(
        """
        update devices
        set bundle_json = ?, last_seen_at_epoch_ms = ?
        where user_id = ? and device_id = ?
        """.trimIndent(),
      ).use { statement ->
        statement.setString(1, encode(RegisteredDeviceRecord.serializer(), record))
        statement.setLong(2, record.lastSeenAtEpochMs)
        statement.setString(3, record.userId)
        statement.setString(4, record.deviceBundle.deviceId)
        statement.executeUpdate()
      }
    }

    record
  }

  override fun publishPreKeyBundle(command: PublishPreKeyBundleCommand): PreKeyBundleRecord = tx { connection ->
    requireKnownUser(connection, command.userId)
    requireKnownDevice(connection, command.userId, command.deviceBundle.deviceId)
    require(command.deviceBundle.userId == command.userId) {
      "Device bundle userId must match publishPreKeyBundle userId."
    }

    val record = PreKeyBundleRecord(
      userId = command.userId,
      deviceId = command.deviceBundle.deviceId,
      deviceBundle = command.deviceBundle,
      publishedAtEpochMs = System.currentTimeMillis(),
    )

    val exists = exists(
      connection,
      """
      select 1
      from prekey_bundles
      where user_id = ? and device_id = ?
      """.trimIndent(),
      command.userId,
      command.deviceBundle.deviceId,
    )

    if (exists) {
      connection.prepareStatement(
        """
        update prekey_bundles
        set bundle_json = ?, published_at_epoch_ms = ?
        where user_id = ? and device_id = ?
        """.trimIndent(),
      ).use { statement ->
        statement.setString(1, encode(PreKeyBundleRecord.serializer(), record))
        statement.setLong(2, record.publishedAtEpochMs)
        statement.setString(3, record.userId)
        statement.setString(4, record.deviceId)
        statement.executeUpdate()
      }
    } else {
      connection.prepareStatement(
        """
        insert into prekey_bundles (
          user_id,
          device_id,
          bundle_json,
          published_at_epoch_ms
        ) values (?, ?, ?, ?)
        """.trimIndent(),
      ).use { statement ->
        statement.setString(1, record.userId)
        statement.setString(2, record.deviceId)
        statement.setString(3, encode(PreKeyBundleRecord.serializer(), record))
        statement.setLong(4, record.publishedAtEpochMs)
        statement.executeUpdate()
      }
    }

    record
  }

  override fun getDeviceBundle(userId: String, deviceId: String): PreKeyBundleRecord = tx { connection ->
    connection.prepareStatement(
      """
      select bundle_json
      from prekey_bundles
      where user_id = ? and device_id = ?
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, userId)
      statement.setString(2, deviceId)
      statement.executeQuery().use { result ->
        require(result.next()) {
          "No pre-key bundle found for $userId/$deviceId."
        }

        decode(PreKeyBundleRecord.serializer(), result.getString("bundle_json"))
      }
    }
  }

  override fun createConversation(command: CreateConversationCommand): ConversationDescriptor = tx { connection ->
    requireKnownUser(connection, command.createdByUserId)
    require(command.participantUserIds.isNotEmpty()) {
      "At least one participant is required."
    }
    command.participantUserIds.forEach { requireKnownUser(connection, it) }

    if (config.strictCryptoEnforcement && command.protocolType == ProtocolType.MLS_GROUP) {
      throw IllegalStateException("Strict mode does not allow unfinished group protocol rollout.")
    }

    val participantUserIds = (command.participantUserIds + command.createdByUserId).distinct()
    val now = System.currentTimeMillis()
    val descriptor = ConversationDescriptor(
      conversationId = generateId("conv"),
      protocolType = command.protocolType,
      encryptedTitle = command.encryptedTitle,
      participantUserIds = participantUserIds,
      createdAtEpochMs = now,
      updatedAtEpochMs = now,
    )

    connection.prepareStatement(
      """
      insert into conversations (
        conversation_id,
        protocol_type,
        encrypted_title,
        created_at_epoch_ms,
        updated_at_epoch_ms,
        max_members,
        member_count
      ) values (?, ?, ?, ?, ?, ?, ?)
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, descriptor.conversationId)
      statement.setString(2, descriptor.protocolType.name)
      statement.setString(3, descriptor.encryptedTitle)
      statement.setLong(4, descriptor.createdAtEpochMs)
      statement.setLong(5, descriptor.updatedAtEpochMs)
      statement.setInt(6, 1000)
      statement.setInt(7, participantUserIds.size)
      statement.executeUpdate()
    }

    connection.prepareStatement(
      """
      insert into conversation_members (
        conversation_id,
        user_id,
        role,
        added_by_user_id,
        joined_at_epoch_ms
      ) values (?, ?, ?, ?, ?)
      """.trimIndent(),
    ).use { statement ->
      participantUserIds.forEachIndexed { index, userId ->
        statement.setString(1, descriptor.conversationId)
        statement.setString(2, userId)
        statement.setString(3, if (userId == command.createdByUserId) GroupRole.OWNER.name else GroupRole.MEMBER.name)
        statement.setString(4, if (userId == command.createdByUserId) null else command.createdByUserId)
        statement.setLong(5, now + index) // Slight offset for ordering
        statement.addBatch()
      }
      statement.executeBatch()
    }

    descriptor
  }

  override fun listConversations(userId: String): List<ConversationDescriptor> = tx { connection ->
    requireKnownUser(connection, userId)

    connection.prepareStatement(
      """
      select
        c.conversation_id,
        c.protocol_type,
        c.encrypted_title,
        c.created_at_epoch_ms,
        c.updated_at_epoch_ms
      from conversations c
      inner join conversation_members cm
        on cm.conversation_id = c.conversation_id
      where cm.user_id = ?
      order by c.updated_at_epoch_ms desc
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, userId)
      statement.executeQuery().use { result ->
        buildList {
          while (result.next()) {
            add(conversationFromRow(connection, result))
          }
        }
      }
    }
  }

  override fun getConversation(conversationId: String): ConversationDescriptor = tx { connection ->
    getConversation(connection, conversationId)
  }

  override fun appendMessage(command: SendEncryptedMessageCommand): EncryptedEnvelope = tx { connection ->
    val conversation = getConversation(connection, command.conversationId)

    require(command.envelope.messageId.isNotBlank()) {
      "messageId is required."
    }
    require(conversation.conversationId == command.envelope.conversationId) {
      "Envelope conversation does not match the message command."
    }
    require(command.envelope.senderUserId in conversation.participantUserIds) {
      "Sender is not a participant in the conversation."
    }
    requireKnownDevice(connection, command.envelope.senderUserId, command.envelope.senderDeviceId)

    connection.prepareStatement(
      """
      insert into messages (
        message_id,
        conversation_id,
        envelope_json,
        created_at_epoch_ms
      ) values (?, ?, ?, ?)
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, command.envelope.messageId)
      statement.setString(2, command.conversationId)
      statement.setString(3, encode(EncryptedEnvelope.serializer(), command.envelope))
      statement.setLong(4, command.envelope.createdAtEpochMs)
      statement.executeUpdate()
    }

    connection.prepareStatement(
      """
      update conversations
      set updated_at_epoch_ms = ?
      where conversation_id = ?
      """.trimIndent(),
    ).use { statement ->
      statement.setLong(1, maxOf(conversation.updatedAtEpochMs, command.envelope.createdAtEpochMs))
      statement.setString(2, conversation.conversationId)
      statement.executeUpdate()
    }

    // Store mentions
    if (command.envelope.mentionedUserIds.isNotEmpty()) {
      connection.prepareStatement(
        """
        insert into message_mentions (
          message_id,
          conversation_id,
          sender_user_id,
          mentioned_user_id,
          created_at_epoch_ms
        ) values (?, ?, ?, ?, ?)
        on conflict (message_id, mentioned_user_id) do nothing
        """.trimIndent(),
      ).use { statement ->
        for (mentionedUserId in command.envelope.mentionedUserIds) {
          statement.setString(1, command.envelope.messageId)
          statement.setString(2, command.conversationId)
          statement.setString(3, command.envelope.senderUserId)
          statement.setString(4, mentionedUserId)
          statement.setLong(5, command.envelope.createdAtEpochMs)
          statement.addBatch()
        }
        statement.executeBatch()
      }
    }

    command.envelope
  }

  override fun listMessages(conversationId: String): List<EncryptedEnvelope> = tx { connection ->
    getConversation(connection, conversationId)

    connection.prepareStatement(
      """
      select envelope_json
      from messages
      where conversation_id = ?
      order by created_at_epoch_ms asc
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, conversationId)
      statement.executeQuery().use { result ->
        buildList {
          while (result.next()) {
            add(decode(EncryptedEnvelope.serializer(), result.getString("envelope_json")))
          }
        }
      }
    }
  }

  override fun markMessageRead(command: MarkMessageReadCommand): MessageReceipt = tx { connection ->
    val conversation = getConversation(connection, command.conversationId)

    require(command.userId in conversation.participantUserIds) {
      "Receipt user is not a participant in the conversation."
    }
    requireKnownDevice(connection, command.userId, command.deviceId)
    require(
      exists(
        connection,
        """
        select 1
        from messages
        where message_id = ? and conversation_id = ?
        """.trimIndent(),
        command.messageId,
        command.conversationId,
      ),
    ) {
      "Cannot add a receipt for an unknown message."
    }

    val receipt = MessageReceipt(
      messageId = command.messageId,
      conversationId = command.conversationId,
      userId = command.userId,
      deviceId = command.deviceId,
      type = ReceiptType.READ,
      createdAtEpochMs = command.readAtEpochMs,
    )

    val exists = exists(
      connection,
      """
      select 1
      from message_receipts
      where message_id = ? and user_id = ? and device_id = ?
      """.trimIndent(),
      receipt.messageId,
      receipt.userId,
      receipt.deviceId,
    )

    if (exists) {
      connection.prepareStatement(
        """
        update message_receipts
        set receipt_json = ?, created_at_epoch_ms = ?
        where message_id = ? and user_id = ? and device_id = ?
        """.trimIndent(),
      ).use { statement ->
        statement.setString(1, encode(MessageReceipt.serializer(), receipt))
        statement.setLong(2, receipt.createdAtEpochMs)
        statement.setString(3, receipt.messageId)
        statement.setString(4, receipt.userId)
        statement.setString(5, receipt.deviceId)
        statement.executeUpdate()
      }
    } else {
      connection.prepareStatement(
        """
        insert into message_receipts (
          message_id,
          conversation_id,
          user_id,
          device_id,
          receipt_json,
          created_at_epoch_ms
        ) values (?, ?, ?, ?, ?, ?)
        """.trimIndent(),
      ).use { statement ->
        statement.setString(1, receipt.messageId)
        statement.setString(2, receipt.conversationId)
        statement.setString(3, receipt.userId)
        statement.setString(4, receipt.deviceId)
        statement.setString(5, encode(MessageReceipt.serializer(), receipt))
        statement.setLong(6, receipt.createdAtEpochMs)
        statement.executeUpdate()
      }
    }

    receipt
  }

  override fun listReceipts(messageId: String): List<MessageReceipt> = tx { connection ->
    connection.prepareStatement(
      """
      select receipt_json
      from message_receipts
      where message_id = ?
      order by created_at_epoch_ms asc
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, messageId)
      statement.executeQuery().use { result ->
        buildList {
          while (result.next()) {
            add(decode(MessageReceipt.serializer(), result.getString("receipt_json")))
          }
        }
      }
    }
  }

  override fun createAttachmentUploadSession(
    command: CreateAttachmentUploadSessionCommand,
  ): AttachmentUploadSession = tx { connection ->
    val conversation = getConversation(connection, command.conversationId)

    require(command.ownerUserId in conversation.participantUserIds) {
      "Upload owner is not a participant in the conversation."
    }
    requireKnownDevice(connection, command.ownerUserId, command.senderDeviceId)
    require(command.expectedCiphertextByteLength > 0) {
      "expectedCiphertextByteLength must be positive."
    }

    val now = System.currentTimeMillis()
    val session = AttachmentUploadSession(
      sessionId = generateId("upload"),
      uploadToken = UUID.randomUUID().toString().replace("-", ""),
      conversationId = command.conversationId,
      ownerUserId = command.ownerUserId,
      senderDeviceId = command.senderDeviceId,
      expectedCiphertextByteLength = command.expectedCiphertextByteLength,
      expectedCiphertextDigest = command.expectedCiphertextDigest,
      expiresAtEpochMs = command.expiresAtEpochMs ?: now + 15 * 60 * 1000,
    )

    connection.prepareStatement(
      """
      insert into attachment_upload_sessions (
        session_id,
        upload_token,
        conversation_id,
        session_json,
        expires_at_epoch_ms,
        completed_at_epoch_ms
      ) values (?, ?, ?, ?, ?, ?)
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, session.sessionId)
      statement.setString(2, session.uploadToken)
      statement.setString(3, session.conversationId)
      statement.setString(4, encode(AttachmentUploadSession.serializer(), session))
      statement.setLong(5, session.expiresAtEpochMs)
      statement.setObject(6, null)
      statement.executeUpdate()
    }

    session
  }

  override fun getAttachmentUploadSession(sessionId: String): AttachmentUploadSession = tx { connection ->
    connection.prepareStatement(
      """
      select session_json
      from attachment_upload_sessions
      where session_id = ?
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, sessionId)
      statement.executeQuery().use { result ->
        require(result.next()) {
          "Upload session $sessionId was not found."
        }

        val session = decode(AttachmentUploadSession.serializer(), result.getString("session_json"))
        require(session.completedAtEpochMs == null) {
          "Upload session $sessionId is already complete."
        }
        require(session.expiresAtEpochMs > System.currentTimeMillis()) {
          "Upload session $sessionId has expired."
        }
        session
      }
    }
  }

  override fun completeAttachmentUpload(
    sessionId: String,
    uploadToken: String,
    storageKey: String,
    ciphertextByteLength: Long,
    ciphertextDigest: String,
    completedAtEpochMs: Long,
  ): UploadedBlobReceipt = tx { connection ->
    val session = getAttachmentUploadSession(connection, sessionId)

    require(session.uploadToken == uploadToken) {
      "Upload token is invalid."
    }
    require(session.expectedCiphertextByteLength == ciphertextByteLength) {
      "Uploaded ciphertext length did not match the reserved upload session."
    }
    val expectedDigest = session.expectedCiphertextDigest
    if (expectedDigest != null) {
      require(expectedDigest == ciphertextDigest) {
        "Uploaded ciphertext digest did not match the reserved upload session."
      }
    }

    val completedSession = session.copy(
      completedAtEpochMs = completedAtEpochMs,
      storageKey = storageKey,
    )

    connection.prepareStatement(
      """
      update attachment_upload_sessions
      set session_json = ?, completed_at_epoch_ms = ?, storage_key = ?
      where session_id = ?
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, encode(AttachmentUploadSession.serializer(), completedSession))
      statement.setLong(2, completedAtEpochMs)
      statement.setString(3, storageKey)
      statement.setString(4, sessionId)
      statement.executeUpdate()
    }

    UploadedBlobReceipt(
      sessionId = sessionId,
      storageKey = storageKey,
      ciphertextByteLength = ciphertextByteLength,
      ciphertextDigest = ciphertextDigest,
      completedAtEpochMs = completedAtEpochMs,
    )
  }

  override fun storeAttachmentMetadata(command: AttachmentMetadataCommand): EncryptedAttachmentMetadata = tx { connection ->
    val conversation = getConversation(connection, command.conversationId)

    require(command.ownerUserId in conversation.participantUserIds) {
      "Attachment owner is not a participant in the conversation."
    }
    requireKnownDevice(connection, command.ownerUserId, command.senderDeviceId)
    require(command.storageKey.isNotBlank()) {
      "storageKey is required."
    }

    val metadata = EncryptedAttachmentMetadata(
      attachmentId = generateId("att"),
      conversationId = command.conversationId,
      ownerUserId = command.ownerUserId,
      senderDeviceId = command.senderDeviceId,
      storageKey = command.storageKey,
      ciphertextDigest = command.ciphertextDigest,
      ciphertextByteLength = command.ciphertextByteLength,
      metadataCiphertext = command.metadataCiphertext,
      protocolVersion = command.protocolVersion,
      messageId = command.messageId,
      createdAtEpochMs = System.currentTimeMillis(),
    )

    connection.prepareStatement(
      """
      insert into encrypted_attachments (
        attachment_id,
        conversation_id,
        metadata_json,
        created_at_epoch_ms
      ) values (?, ?, ?, ?)
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, metadata.attachmentId)
      statement.setString(2, metadata.conversationId)
      statement.setString(3, encode(EncryptedAttachmentMetadata.serializer(), metadata))
      statement.setLong(4, metadata.createdAtEpochMs)
      statement.executeUpdate()
    }

    metadata
  }

  override fun listAttachments(conversationId: String): List<EncryptedAttachmentMetadata> = tx { connection ->
    getConversation(connection, conversationId)

    connection.prepareStatement(
      """
      select metadata_json
      from encrypted_attachments
      where conversation_id = ?
      order by created_at_epoch_ms asc
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, conversationId)
      statement.executeQuery().use { result ->
        buildList {
          while (result.next()) {
            add(decode(EncryptedAttachmentMetadata.serializer(), result.getString("metadata_json")))
          }
        }
      }
    }
  }

  override fun createInviteCode(command: CreateInviteCodeCommand): InviteCodeRecord = tx { connection ->
    requireKnownUser(connection, command.ownerUserId)
    require(command.encryptedInvitePayload.isNotBlank()) {
      "encryptedInvitePayload is required."
    }

    val record = InviteCodeRecord(
      inviteCode = generateId("invite"),
      ownerUserId = command.ownerUserId,
      encryptedInvitePayload = command.encryptedInvitePayload,
      expiresAtEpochMs = command.expiresAtEpochMs,
      createdAtEpochMs = System.currentTimeMillis(),
    )

    connection.prepareStatement(
      """
      insert into invite_codes (
        invite_code,
        owner_user_id,
        record_json,
        created_at_epoch_ms
      ) values (?, ?, ?, ?)
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, record.inviteCode)
      statement.setString(2, record.ownerUserId)
      statement.setString(3, encode(InviteCodeRecord.serializer(), record))
      statement.setLong(4, record.createdAtEpochMs)
      statement.executeUpdate()
    }

    record
  }

  override fun addContactByInviteCode(command: AddContactByInviteCodeCommand): List<ContactRecord> = tx { connection ->
    requireKnownUser(connection, command.ownerUserId)

    val invite = connection.prepareStatement(
      """
      select record_json
      from invite_codes
      where invite_code = ?
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, command.inviteCode)
      statement.executeQuery().use { result ->
        require(result.next()) {
          "Invite code ${command.inviteCode} was not found."
        }

        decode(InviteCodeRecord.serializer(), result.getString("record_json"))
      }
    }

    val now = System.currentTimeMillis()
    val inviteExpiry = invite.expiresAtEpochMs
    if (inviteExpiry != null && inviteExpiry < now) {
      throw IllegalStateException("Invite code has expired.")
    }
    require(invite.ownerUserId != command.ownerUserId) {
      "Invite owner cannot add themselves."
    }

    val ownerContact = ContactRecord(
      ownerUserId = command.ownerUserId,
      peerUserId = invite.ownerUserId,
      inviteCode = invite.inviteCode,
      encryptedAlias = command.encryptedAlias,
      createdAtEpochMs = now,
    )
    val peerContact = ContactRecord(
      ownerUserId = invite.ownerUserId,
      peerUserId = command.ownerUserId,
      inviteCode = invite.inviteCode,
      createdAtEpochMs = now,
    )

    upsertContact(connection, ownerContact)
    upsertContact(connection, peerContact)

    val updatedInvite = invite.copy(consumedByUserId = command.ownerUserId)
    connection.prepareStatement(
      """
      update invite_codes
      set record_json = ?
      where invite_code = ?
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, encode(InviteCodeRecord.serializer(), updatedInvite))
      statement.setString(2, invite.inviteCode)
      statement.executeUpdate()
    }

    listOf(ownerContact, peerContact)
  }

  // Group member management

  override fun listConversationMembers(conversationId: String, offset: Int, limit: Int): GroupMemberPage = tx { connection ->
    getConversation(connection, conversationId)

    val totalCount = connection.prepareStatement(
      """
      select count(*) as cnt
      from conversation_members
      where conversation_id = ?
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, conversationId)
      statement.executeQuery().use { result ->
        if (result.next()) result.getInt("cnt") else 0
      }
    }

    val members = connection.prepareStatement(
      """
      select user_id, role, added_by_user_id, joined_at_epoch_ms
      from conversation_members
      where conversation_id = ?
      order by joined_at_epoch_ms asc
      limit ? offset ?
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, conversationId)
      statement.setInt(2, limit)
      statement.setInt(3, offset)
      statement.executeQuery().use { result ->
        buildList {
          while (result.next()) {
            add(
              GroupMember(
                userId = result.getString("user_id"),
                role = GroupRole.valueOf(result.getString("role")),
                addedByUserId = result.getString("added_by_user_id"),
                joinedAtEpochMs = result.getLong("joined_at_epoch_ms"),
              )
            )
          }
        }
      }
    }

    GroupMemberPage(
      conversationId = conversationId,
      members = members,
      totalCount = totalCount,
      offset = offset,
      limit = limit,
    )
  }

  override fun addConversationMembers(command: AddGroupMembersCommand): GroupMemberPage = tx { connection ->
    getConversation(connection, command.conversationId)
    requireKnownUser(connection, command.requestingUserId)
    command.userIds.forEach { requireKnownUser(connection, it) }

    val requesterRole = getMemberRole(connection, command.conversationId, command.requestingUserId)
    require(requesterRole in setOf(GroupRole.OWNER, GroupRole.ADMIN)) {
      "Only owners and admins can add members."
    }

    require(command.userIds.size <= 100) {
      "Cannot add more than 100 members at once."
    }

    val (maxMembers, currentCount) = getConversationLimits(connection, command.conversationId)
    val existingUserIds = getExistingMemberUserIds(connection, command.conversationId, command.userIds)
    val newUserIds = command.userIds.filter { it !in existingUserIds }

    require(currentCount + newUserIds.size <= maxMembers) {
      "Adding ${newUserIds.size} members would exceed the maximum of $maxMembers."
    }

    if (newUserIds.isNotEmpty()) {
      val now = System.currentTimeMillis()
      connection.prepareStatement(
        """
        insert into conversation_members (
          conversation_id,
          user_id,
          role,
          added_by_user_id,
          joined_at_epoch_ms
        ) values (?, ?, ?, ?, ?)
        """.trimIndent(),
      ).use { statement ->
        newUserIds.forEachIndexed { index, userId ->
          statement.setString(1, command.conversationId)
          statement.setString(2, userId)
          statement.setString(3, GroupRole.MEMBER.name)
          statement.setString(4, command.requestingUserId)
          statement.setLong(5, now + index)
          statement.addBatch()
        }
        statement.executeBatch()
      }

      updateMemberCount(connection, command.conversationId)
      updateConversationTimestamp(connection, command.conversationId, now)
    }

    listConversationMembers(connection, command.conversationId, 0, 50)
  }

  override fun removeConversationMembers(command: RemoveGroupMembersCommand): GroupMemberPage = tx { connection ->
    getConversation(connection, command.conversationId)
    requireKnownUser(connection, command.requestingUserId)

    val requesterRole = getMemberRole(connection, command.conversationId, command.requestingUserId)
    require(requesterRole in setOf(GroupRole.OWNER, GroupRole.ADMIN)) {
      "Only owners and admins can remove members."
    }

    require(command.userIds.size <= 100) {
      "Cannot remove more than 100 members at once."
    }

    for (userId in command.userIds) {
      val targetRole = getMemberRoleOrNull(connection, command.conversationId, userId) ?: continue

      // Admins cannot remove owners or other admins
      if (requesterRole == GroupRole.ADMIN) {
        require(targetRole == GroupRole.MEMBER) {
          "Admins can only remove members, not owners or other admins."
        }
      }

      // Owner cannot be removed
      require(targetRole != GroupRole.OWNER) {
        "The owner cannot be removed from the conversation."
      }
    }

    connection.prepareStatement(
      """
      delete from conversation_members
      where conversation_id = ?
        and user_id = ?
        and role != 'OWNER'
      """.trimIndent(),
    ).use { statement ->
      command.userIds.forEach { userId ->
        statement.setString(1, command.conversationId)
        statement.setString(2, userId)
        statement.addBatch()
      }
      statement.executeBatch()
    }

    val now = System.currentTimeMillis()
    updateMemberCount(connection, command.conversationId)
    updateConversationTimestamp(connection, command.conversationId, now)

    listConversationMembers(connection, command.conversationId, 0, 50)
  }

  override fun updateMemberRole(command: UpdateMemberRoleCommand): GroupMemberPage = tx { connection ->
    getConversation(connection, command.conversationId)
    requireKnownUser(connection, command.requestingUserId)
    requireKnownUser(connection, command.targetUserId)

    val requesterRole = getMemberRole(connection, command.conversationId, command.requestingUserId)
    require(requesterRole == GroupRole.OWNER) {
      "Only the owner can change member roles."
    }

    getMemberRole(connection, command.conversationId, command.targetUserId)

    require(command.targetUserId != command.requestingUserId) {
      "Cannot change your own role."
    }

    require(command.newRole != GroupRole.OWNER) {
      "Ownership transfer is not supported via role change."
    }

    connection.prepareStatement(
      """
      update conversation_members
      set role = ?
      where conversation_id = ? and user_id = ?
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, command.newRole.name)
      statement.setString(2, command.conversationId)
      statement.setString(3, command.targetUserId)
      statement.executeUpdate()
    }

    listConversationMembers(connection, command.conversationId, 0, 50)
  }

  override fun leaveConversation(command: LeaveConversationCommand): ConversationDescriptor = tx { connection ->
    getConversation(connection, command.conversationId)
    requireKnownUser(connection, command.userId)

    val leavingRole = getMemberRole(connection, command.conversationId, command.userId)

    // Auto-transfer ownership if owner is leaving and there are other members
    if (leavingRole == GroupRole.OWNER) {
      val remainingMembers = connection.prepareStatement(
        """
        select user_id, role, joined_at_epoch_ms
        from conversation_members
        where conversation_id = ? and user_id != ?
        order by
          case when role = 'ADMIN' then 0 else 1 end,
          joined_at_epoch_ms asc
        limit 1
        """.trimIndent(),
      ).use { statement ->
        statement.setString(1, command.conversationId)
        statement.setString(2, command.userId)
        statement.executeQuery().use { result ->
          if (result.next()) result.getString("user_id") else null
        }
      }

      if (remainingMembers != null) {
        connection.prepareStatement(
          """
          update conversation_members
          set role = 'OWNER'
          where conversation_id = ? and user_id = ?
          """.trimIndent(),
        ).use { statement ->
          statement.setString(1, command.conversationId)
          statement.setString(2, remainingMembers)
          statement.executeUpdate()
        }
      }
    }

    connection.prepareStatement(
      """
      delete from conversation_members
      where conversation_id = ? and user_id = ?
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, command.conversationId)
      statement.setString(2, command.userId)
      statement.executeUpdate()
    }

    val now = System.currentTimeMillis()
    updateMemberCount(connection, command.conversationId)
    updateConversationTimestamp(connection, command.conversationId, now)

    getConversation(connection, command.conversationId)
  }

  override fun updateConversationSettings(command: UpdateConversationSettingsCommand): ConversationDescriptor = tx { connection ->
    getConversation(connection, command.conversationId)
    requireKnownUser(connection, command.requestingUserId)

    val requesterRole = getMemberRole(connection, command.conversationId, command.requestingUserId)
    require(requesterRole in setOf(GroupRole.OWNER, GroupRole.ADMIN)) {
      "Only owners and admins can update conversation settings."
    }

    val now = System.currentTimeMillis()

    if (command.encryptedTitle != null) {
      connection.prepareStatement(
        """
        update conversations
        set encrypted_title = ?, updated_at_epoch_ms = ?
        where conversation_id = ?
        """.trimIndent(),
      ).use { statement ->
        statement.setString(1, command.encryptedTitle)
        statement.setLong(2, now)
        statement.setString(3, command.conversationId)
        statement.executeUpdate()
      }
    }

    command.maxMembers?.let { maxMembers ->
      require(maxMembers in 2..1000) {
        "maxMembers must be between 2 and 1000."
      }
      connection.prepareStatement(
        """
        update conversations
        set max_members = ?, updated_at_epoch_ms = ?
        where conversation_id = ?
        """.trimIndent(),
      ).use { statement ->
        statement.setInt(1, maxMembers)
        statement.setLong(2, now)
        statement.setString(3, command.conversationId)
        statement.executeUpdate()
      }
    }

    if (command.encryptedTitle == null && command.maxMembers == null) {
      updateConversationTimestamp(connection, command.conversationId, now)
    }

    getConversation(connection, command.conversationId)
  }

  private fun listConversationMembers(connection: Connection, conversationId: String, offset: Int, limit: Int): GroupMemberPage {
    val totalCount = connection.prepareStatement(
      """
      select count(*) as cnt
      from conversation_members
      where conversation_id = ?
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, conversationId)
      statement.executeQuery().use { result ->
        if (result.next()) result.getInt("cnt") else 0
      }
    }

    val members = connection.prepareStatement(
      """
      select user_id, role, added_by_user_id, joined_at_epoch_ms
      from conversation_members
      where conversation_id = ?
      order by joined_at_epoch_ms asc
      limit ? offset ?
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, conversationId)
      statement.setInt(2, limit)
      statement.setInt(3, offset)
      statement.executeQuery().use { result ->
        buildList {
          while (result.next()) {
            add(
              GroupMember(
                userId = result.getString("user_id"),
                role = GroupRole.valueOf(result.getString("role")),
                addedByUserId = result.getString("added_by_user_id"),
                joinedAtEpochMs = result.getLong("joined_at_epoch_ms"),
              )
            )
          }
        }
      }
    }

    return GroupMemberPage(
      conversationId = conversationId,
      members = members,
      totalCount = totalCount,
      offset = offset,
      limit = limit,
    )
  }

  private fun getMemberRole(connection: Connection, conversationId: String, userId: String): GroupRole {
    return getMemberRoleOrNull(connection, conversationId, userId)
      ?: throw IllegalStateException("User $userId is not a member of conversation $conversationId.")
  }

  private fun getMemberRoleOrNull(connection: Connection, conversationId: String, userId: String): GroupRole? {
    return connection.prepareStatement(
      """
      select role
      from conversation_members
      where conversation_id = ? and user_id = ?
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, conversationId)
      statement.setString(2, userId)
      statement.executeQuery().use { result ->
        if (result.next()) GroupRole.valueOf(result.getString("role")) else null
      }
    }
  }

  private fun getConversationLimits(connection: Connection, conversationId: String): Pair<Int, Int> {
    return connection.prepareStatement(
      """
      select max_members, member_count
      from conversations
      where conversation_id = ?
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, conversationId)
      statement.executeQuery().use { result ->
        if (result.next()) {
          Pair(result.getInt("max_members"), result.getInt("member_count"))
        } else {
          Pair(1000, 0)
        }
      }
    }
  }

  private fun getExistingMemberUserIds(connection: Connection, conversationId: String, userIds: List<String>): Set<String> {
    if (userIds.isEmpty()) return emptySet()

    val placeholders = userIds.joinToString(",") { "?" }
    return connection.prepareStatement(
      """
      select user_id
      from conversation_members
      where conversation_id = ? and user_id in ($placeholders)
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, conversationId)
      userIds.forEachIndexed { index, userId ->
        statement.setString(index + 2, userId)
      }
      statement.executeQuery().use { result ->
        buildSet {
          while (result.next()) {
            add(result.getString("user_id"))
          }
        }
      }
    }
  }

  private fun updateMemberCount(connection: Connection, conversationId: String) {
    connection.prepareStatement(
      """
      update conversations
      set member_count = (
        select count(*) from conversation_members where conversation_id = ?
      )
      where conversation_id = ?
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, conversationId)
      statement.setString(2, conversationId)
      statement.executeUpdate()
    }
  }

  private fun updateConversationTimestamp(connection: Connection, conversationId: String, timestamp: Long) {
    connection.prepareStatement(
      """
      update conversations
      set updated_at_epoch_ms = ?
      where conversation_id = ?
      """.trimIndent(),
    ).use { statement ->
      statement.setLong(1, timestamp)
      statement.setString(2, conversationId)
      statement.executeUpdate()
    }
  }

  private fun upsertContact(
    connection: Connection,
    record: ContactRecord,
  ) {
    val exists = exists(
      connection,
      """
      select 1
      from contacts
      where owner_user_id = ? and peer_user_id = ?
      """.trimIndent(),
      record.ownerUserId,
      record.peerUserId,
    )

    if (exists) {
      connection.prepareStatement(
        """
        update contacts
        set record_json = ?, created_at_epoch_ms = ?
        where owner_user_id = ? and peer_user_id = ?
        """.trimIndent(),
      ).use { statement ->
        statement.setString(1, encode(ContactRecord.serializer(), record))
        statement.setLong(2, record.createdAtEpochMs)
        statement.setString(3, record.ownerUserId)
        statement.setString(4, record.peerUserId)
        statement.executeUpdate()
      }
    } else {
      connection.prepareStatement(
        """
        insert into contacts (
          owner_user_id,
          peer_user_id,
          record_json,
          created_at_epoch_ms
        ) values (?, ?, ?, ?)
        """.trimIndent(),
      ).use { statement ->
        statement.setString(1, record.ownerUserId)
        statement.setString(2, record.peerUserId)
        statement.setString(3, encode(ContactRecord.serializer(), record))
        statement.setLong(4, record.createdAtEpochMs)
        statement.executeUpdate()
      }
    }
  }

  private fun getConversation(
    connection: Connection,
    conversationId: String,
  ): ConversationDescriptor {
    return connection.prepareStatement(
      """
      select
        conversation_id,
        protocol_type,
        encrypted_title,
        created_at_epoch_ms,
        updated_at_epoch_ms
      from conversations
      where conversation_id = ?
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, conversationId)
      statement.executeQuery().use { result ->
        require(result.next()) {
          "Conversation $conversationId was not found."
        }

        conversationFromRow(connection, result)
      }
    }
  }

  private fun getAttachmentUploadSession(
    connection: Connection,
    sessionId: String,
  ): AttachmentUploadSession {
    return connection.prepareStatement(
      """
      select session_json
      from attachment_upload_sessions
      where session_id = ?
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, sessionId)
      statement.executeQuery().use { result ->
        require(result.next()) {
          "Upload session $sessionId was not found."
        }

        decode(AttachmentUploadSession.serializer(), result.getString("session_json"))
      }
    }
  }

  private fun conversationFromRow(
    connection: Connection,
    result: java.sql.ResultSet,
  ): ConversationDescriptor {
    val conversationId = result.getString("conversation_id")
    return ConversationDescriptor(
      conversationId = conversationId,
      protocolType = ProtocolType.valueOf(result.getString("protocol_type")),
      encryptedTitle = result.getString("encrypted_title"),
      participantUserIds = loadParticipantUserIds(connection, conversationId),
      createdAtEpochMs = result.getLong("created_at_epoch_ms"),
      updatedAtEpochMs = result.getLong("updated_at_epoch_ms"),
    )
  }

  private fun loadParticipantUserIds(
    connection: Connection,
    conversationId: String,
  ): List<String> {
    return connection.prepareStatement(
      """
      select user_id
      from conversation_members
      where conversation_id = ?
      order by user_id asc
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, conversationId)
      statement.executeQuery().use { result ->
        buildList {
          while (result.next()) {
            add(result.getString("user_id"))
          }
        }
      }
    }
  }

  private fun requireKnownUser(
    connection: Connection,
    userId: String,
  ) {
    require(
      exists(
        connection,
        """
        select 1
        from users
        where user_id = ?
        """.trimIndent(),
        userId,
      ),
    ) {
      "Unknown user $userId."
    }
  }

  private fun requireKnownDevice(
    connection: Connection,
    userId: String,
    deviceId: String,
  ) {
    require(
      exists(
        connection,
        """
        select 1
        from devices
        where user_id = ? and device_id = ?
        """.trimIndent(),
        userId,
        deviceId,
      ),
    ) {
      "Unknown device $userId/$deviceId."
    }
  }

  private fun exists(
    connection: Connection,
    sql: String,
    vararg values: String,
  ): Boolean {
    return connection.prepareStatement(sql).use { statement ->
      values.forEachIndexed { index, value ->
        statement.setString(index + 1, value)
      }
      statement.executeQuery().use { result -> result.next() }
    }
  }

  private fun <T> tx(block: (Connection) -> T): T {
    dataSource.connection.use { connection ->
      connection.autoCommit = false
      try {
        val result = block(connection)
        connection.commit()
        return result
      } catch (throwable: Throwable) {
        connection.rollback()
        throw throwable
      }
    }
  }

  private fun generateId(prefix: String): String {
    return "${prefix}_${UUID.randomUUID().toString().replace("-", "")}"
  }

  private fun <T> encode(
    serializer: KSerializer<T>,
    value: T,
  ): String = json.encodeToString(serializer, value)

  private fun <T> decode(
    serializer: KSerializer<T>,
    value: String,
  ): T = json.decodeFromString(serializer, value)

  // Mentions

  override fun listMentionsForUser(userId: String, offset: Int, limit: Int): List<MentionNotification> = tx { connection ->
    requireKnownUser(connection, userId)

    connection.prepareStatement(
      """
      select message_id, conversation_id, sender_user_id, mentioned_user_id, created_at_epoch_ms
      from message_mentions
      where mentioned_user_id = ?
      order by created_at_epoch_ms desc
      limit ? offset ?
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, userId)
      statement.setInt(2, limit)
      statement.setInt(3, offset)
      statement.executeQuery().use { result ->
        buildList {
          while (result.next()) {
            add(
              MentionNotification(
                messageId = result.getString("message_id"),
                conversationId = result.getString("conversation_id"),
                senderUserId = result.getString("sender_user_id"),
                mentionedUserId = result.getString("mentioned_user_id"),
                createdAtEpochMs = result.getLong("created_at_epoch_ms"),
              )
            )
          }
        }
      }
    }
  }

  // Aggregated read receipts

  override fun getAggregatedReadReceipt(messageId: String): AggregatedReadReceipt = tx { connection ->
    // Get conversation ID from message
    val conversationId = connection.prepareStatement(
      """
      select conversation_id
      from messages
      where message_id = ?
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, messageId)
      statement.executeQuery().use { result ->
        require(result.next()) {
          "Message $messageId not found."
        }
        result.getString("conversation_id")
      }
    }

    // Get read count and user IDs
    val readByUserIds = connection.prepareStatement(
      """
      select distinct user_id
      from message_receipts
      where message_id = ?
      order by user_id
      limit 20
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, messageId)
      statement.executeQuery().use { result ->
        buildList {
          while (result.next()) {
            add(result.getString("user_id"))
          }
        }
      }
    }

    val readCount = connection.prepareStatement(
      """
      select count(distinct user_id) as cnt
      from message_receipts
      where message_id = ?
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, messageId)
      statement.executeQuery().use { result ->
        if (result.next()) result.getInt("cnt") else 0
      }
    }

    // Get total member count
    val totalMembers = connection.prepareStatement(
      """
      select count(*) as cnt
      from conversation_members
      where conversation_id = ?
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, conversationId)
      statement.executeQuery().use { result ->
        if (result.next()) result.getInt("cnt") else 0
      }
    }

    AggregatedReadReceipt(
      messageId = messageId,
      conversationId = conversationId,
      readCount = readCount,
      totalMembers = totalMembers,
      readByUserIds = readByUserIds,
    )
  }

  // Phone authentication

  override fun createPhoneVerification(request: PhoneVerificationRequest, otpHash: String): PhoneVerificationResponse = tx { connection ->
    require(request.phoneNumber.isNotBlank()) { "Phone number is required." }
    require(request.countryCode.isNotBlank()) { "Country code is required." }

    val now = System.currentTimeMillis()
    val verificationId = generateId("verify")
    val expiresAt = now + 10 * 60 * 1000 // 10 minutes

    connection.prepareStatement(
      """
      insert into phone_verifications (
        verification_id,
        phone_number,
        country_code,
        otp_hash,
        status,
        attempts,
        max_attempts,
        created_at_epoch_ms,
        expires_at_epoch_ms
      ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, verificationId)
      statement.setString(2, request.phoneNumber)
      statement.setString(3, request.countryCode)
      statement.setString(4, otpHash)
      statement.setString(5, PhoneVerificationStatus.PENDING.name)
      statement.setInt(6, 0)
      statement.setInt(7, 3)
      statement.setLong(8, now)
      statement.setLong(9, expiresAt)
      statement.executeUpdate()
    }

    PhoneVerificationResponse(
      verificationId = verificationId,
      phoneNumber = request.phoneNumber,
      expiresAtEpochMs = expiresAt,
      status = PhoneVerificationStatus.PENDING,
    )
  }

  override fun verifyOtp(command: VerifyOtpCommand, otpHash: String): VerifyOtpResponse = tx { connection ->
    val verification = connection.prepareStatement(
      """
      select phone_number, country_code, otp_hash, status, attempts, expires_at_epoch_ms
      from phone_verifications
      where verification_id = ?
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, command.verificationId)
      statement.executeQuery().use { result ->
        if (!result.next()) {
          return@tx VerifyOtpResponse(success = false, errorMessage = "Verification not found.")
        }
        mapOf(
          "phoneNumber" to result.getString("phone_number"),
          "countryCode" to result.getString("country_code"),
          "otpHash" to result.getString("otp_hash"),
          "status" to result.getString("status"),
          "attempts" to result.getInt("attempts").toString(),
          "expiresAt" to result.getLong("expires_at_epoch_ms").toString(),
        )
      }
    }

    val now = System.currentTimeMillis()
    val phoneNumber = verification["phoneNumber"]!!
    val countryCode = verification["countryCode"]!!
    val storedOtpHash = verification["otpHash"]!!
    val status = verification["status"]!!
    val attempts = verification["attempts"]!!.toInt()
    val expiresAt = verification["expiresAt"]!!.toLong()

    if (expiresAt < now) {
      updateVerificationStatus(connection, command.verificationId, PhoneVerificationStatus.EXPIRED)
      return@tx VerifyOtpResponse(success = false, errorMessage = "Verification expired.")
    }

    if (status != PhoneVerificationStatus.PENDING.name) {
      return@tx VerifyOtpResponse(success = false, errorMessage = "Verification already processed.")
    }

    if (attempts >= 3) {
      updateVerificationStatus(connection, command.verificationId, PhoneVerificationStatus.FAILED)
      return@tx VerifyOtpResponse(success = false, errorMessage = "Too many attempts.")
    }

    if (storedOtpHash != otpHash) {
      connection.prepareStatement(
        """
        update phone_verifications
        set attempts = attempts + 1
        where verification_id = ?
        """.trimIndent(),
      ).use { statement ->
        statement.setString(1, command.verificationId)
        statement.executeUpdate()
      }
      return@tx VerifyOtpResponse(success = false, errorMessage = "Invalid code.")
    }

    // Success - mark as verified
    connection.prepareStatement(
      """
      update phone_verifications
      set status = ?, verified_at_epoch_ms = ?
      where verification_id = ?
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, PhoneVerificationStatus.VERIFIED.name)
      statement.setLong(2, now)
      statement.setString(3, command.verificationId)
      statement.executeUpdate()
    }

    // Check if user already exists
    val existingUserId = connection.prepareStatement(
      """
      select user_id
      from user_phone_numbers
      where phone_number = ? and country_code = ?
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, phoneNumber)
      statement.setString(2, countryCode)
      statement.executeQuery().use { result ->
        if (result.next()) result.getString("user_id") else null
      }
    }

    if (existingUserId != null) {
      return@tx VerifyOtpResponse(
        success = true,
        userId = existingUserId,
        isNewUser = false,
      )
    }

    // Create new user
    val newUserId = generateId("user")
    connection.prepareStatement(
      """
      insert into users (
        user_id,
        identity_fingerprint,
        profile_ciphertext,
        created_at_epoch_ms
      ) values (?, ?, ?, ?)
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, newUserId)
      statement.setString(2, "phone_${countryCode}_${phoneNumber}_$now")
      statement.setString(3, null)
      statement.setLong(4, now)
      statement.executeUpdate()
    }

    // Link phone to user
    connection.prepareStatement(
      """
      insert into user_phone_numbers (
        user_id,
        phone_number,
        country_code,
        verified_at_epoch_ms
      ) values (?, ?, ?, ?)
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, newUserId)
      statement.setString(2, phoneNumber)
      statement.setString(3, countryCode)
      statement.setLong(4, now)
      statement.executeUpdate()
    }

    VerifyOtpResponse(
      success = true,
      userId = newUserId,
      isNewUser = true,
    )
  }

  private fun updateVerificationStatus(connection: Connection, verificationId: String, status: PhoneVerificationStatus) {
    connection.prepareStatement(
      """
      update phone_verifications
      set status = ?
      where verification_id = ?
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, status.name)
      statement.setString(2, verificationId)
      statement.executeUpdate()
    }
  }

  override fun getUserByPhone(phoneNumber: String, countryCode: String): PhoneAuthenticatedUser? = tx { connection ->
    connection.prepareStatement(
      """
      select user_id, phone_number, country_code, verified_at_epoch_ms
      from user_phone_numbers
      where phone_number = ? and country_code = ?
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, phoneNumber)
      statement.setString(2, countryCode)
      statement.executeQuery().use { result ->
        if (result.next()) {
          PhoneAuthenticatedUser(
            userId = result.getString("user_id"),
            phoneNumber = result.getString("phone_number"),
            countryCode = result.getString("country_code"),
            verifiedAtEpochMs = result.getLong("verified_at_epoch_ms"),
          )
        } else {
          null
        }
      }
    }
  }

  override fun linkPhoneToUser(userId: String, phoneNumber: String, countryCode: String): PhoneAuthenticatedUser = tx { connection ->
    requireKnownUser(connection, userId)

    // Check if phone is already linked
    val existingUserId = connection.prepareStatement(
      """
      select user_id
      from user_phone_numbers
      where phone_number = ? and country_code = ?
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, phoneNumber)
      statement.setString(2, countryCode)
      statement.executeQuery().use { result ->
        if (result.next()) result.getString("user_id") else null
      }
    }

    require(existingUserId == null) {
      "Phone number is already linked to another user."
    }

    val now = System.currentTimeMillis()

    connection.prepareStatement(
      """
      insert into user_phone_numbers (
        user_id,
        phone_number,
        country_code,
        verified_at_epoch_ms
      ) values (?, ?, ?, ?)
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, userId)
      statement.setString(2, phoneNumber)
      statement.setString(3, countryCode)
      statement.setLong(4, now)
      statement.executeUpdate()
    }

    PhoneAuthenticatedUser(
      userId = userId,
      phoneNumber = phoneNumber,
      countryCode = countryCode,
      verifiedAtEpochMs = now,
    )
  }

  // Push tokens

  override fun registerPushToken(command: RegisterPushTokenCommand): PushToken = tx { connection ->
    requireKnownUser(connection, command.userId)
    requireKnownDevice(connection, command.userId, command.deviceId)

    val platform = try {
      PushPlatform.valueOf(command.platform.uppercase())
    } catch (_: IllegalArgumentException) {
      throw IllegalArgumentException("Invalid platform: ${command.platform}. Must be 'fcm' or 'apns'.")
    }

    val now = System.currentTimeMillis()

    // Check if token exists for upsert
    val existingCreatedAt = connection.prepareStatement(
      """
      select created_at_epoch_ms
      from push_tokens
      where user_id = ? and device_id = ?
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, command.userId)
      statement.setString(2, command.deviceId)
      statement.executeQuery().use { result ->
        if (result.next()) result.getLong("created_at_epoch_ms") else null
      }
    }

    val token = PushToken(
      userId = command.userId,
      deviceId = command.deviceId,
      platform = platform,
      token = command.token,
      createdAtEpochMs = existingCreatedAt ?: now,
      updatedAtEpochMs = now,
    )

    if (existingCreatedAt == null) {
      connection.prepareStatement(
        """
        insert into push_tokens (
          user_id,
          device_id,
          platform,
          token,
          created_at_epoch_ms,
          updated_at_epoch_ms
        ) values (?, ?, ?, ?, ?, ?)
        """.trimIndent(),
      ).use { statement ->
        statement.setString(1, token.userId)
        statement.setString(2, token.deviceId)
        statement.setString(3, token.platform.name.lowercase())
        statement.setString(4, token.token)
        statement.setLong(5, token.createdAtEpochMs)
        statement.setLong(6, token.updatedAtEpochMs)
        statement.executeUpdate()
      }
    } else {
      connection.prepareStatement(
        """
        update push_tokens
        set platform = ?, token = ?, updated_at_epoch_ms = ?
        where user_id = ? and device_id = ?
        """.trimIndent(),
      ).use { statement ->
        statement.setString(1, token.platform.name.lowercase())
        statement.setString(2, token.token)
        statement.setLong(3, token.updatedAtEpochMs)
        statement.setString(4, token.userId)
        statement.setString(5, token.deviceId)
        statement.executeUpdate()
      }
    }

    token
  }

  override fun getPushTokensForUser(userId: String): List<PushToken> = tx { connection ->
    requireKnownUser(connection, userId)

    connection.prepareStatement(
      """
      select user_id, device_id, platform, token, created_at_epoch_ms, updated_at_epoch_ms
      from push_tokens
      where user_id = ?
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, userId)
      statement.executeQuery().use { result ->
        buildList {
          while (result.next()) {
            add(pushTokenFromRow(result))
          }
        }
      }
    }
  }

  override fun getPushTokensForConversation(conversationId: String): List<PushToken> = tx { connection ->
    getConversation(connection, conversationId)

    connection.prepareStatement(
      """
      select pt.user_id, pt.device_id, pt.platform, pt.token, pt.created_at_epoch_ms, pt.updated_at_epoch_ms
      from push_tokens pt
      inner join conversation_members cm
        on cm.user_id = pt.user_id
      where cm.conversation_id = ?
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, conversationId)
      statement.executeQuery().use { result ->
        buildList {
          while (result.next()) {
            add(pushTokenFromRow(result))
          }
        }
      }
    }
  }

  override fun deletePushToken(userId: String, deviceId: String): Unit = tx { connection ->
    connection.prepareStatement(
      """
      delete from push_tokens
      where user_id = ? and device_id = ?
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, userId)
      statement.setString(2, deviceId)
      statement.executeUpdate()
    }
  }

  private fun pushTokenFromRow(result: java.sql.ResultSet): PushToken {
    return PushToken(
      userId = result.getString("user_id"),
      deviceId = result.getString("device_id"),
      platform = PushPlatform.valueOf(result.getString("platform").uppercase()),
      token = result.getString("token"),
      createdAtEpochMs = result.getLong("created_at_epoch_ms"),
      updatedAtEpochMs = result.getLong("updated_at_epoch_ms"),
    )
  }
}
