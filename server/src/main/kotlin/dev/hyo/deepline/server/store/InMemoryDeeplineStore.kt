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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.UUID

class InMemoryDeeplineStore(
  private val config: DeeplineServerConfig,
) : DeeplineStore {
  private val userCounter = AtomicInteger()
  private val conversationCounter = AtomicInteger()
  private val attachmentCounter = AtomicInteger()
  private val inviteCounter = AtomicInteger()

  private val users = ConcurrentHashMap<String, UserRecord>()
  private val devices = ConcurrentHashMap<String, RegisteredDeviceRecord>()
  private val preKeyBundles = ConcurrentHashMap<String, PreKeyBundleRecord>()
  private val conversations = ConcurrentHashMap<String, ConversationDescriptor>()
  private val conversationMembers = ConcurrentHashMap<String, MutableMap<String, GroupMember>>()
  private val conversationMaxMembers = ConcurrentHashMap<String, Int>()
  private val messages = ConcurrentHashMap<String, MutableList<EncryptedEnvelope>>()
  private val receipts = ConcurrentHashMap<String, MutableList<MessageReceipt>>()
  private val attachments = ConcurrentHashMap<String, MutableList<EncryptedAttachmentMetadata>>()
  private val uploadSessions = ConcurrentHashMap<String, AttachmentUploadSession>()
  private val invites = ConcurrentHashMap<String, InviteCodeRecord>()
  private val contacts = ConcurrentHashMap<String, ContactRecord>()
  private val mentions = ConcurrentHashMap<String, MutableList<MentionNotification>>()
  private val phoneVerifications = ConcurrentHashMap<String, PhoneVerificationData>()
  private val userPhones = ConcurrentHashMap<String, PhoneAuthenticatedUser>()
  private val phoneToUser = ConcurrentHashMap<String, String>()
  private val pushTokens = ConcurrentHashMap<String, PushToken>()

  private data class PhoneVerificationData(
    val verificationId: String,
    val phoneNumber: String,
    val countryCode: String,
    val otpHash: String,
    val status: PhoneVerificationStatus,
    val attempts: Int,
    val createdAtEpochMs: Long,
    val expiresAtEpochMs: Long,
  )

  override fun registerUser(command: RegisterUserCommand): UserRecord {
    require(command.identityFingerprint.isNotBlank()) {
      "identityFingerprint is required."
    }

    val now = System.currentTimeMillis()
    val user = UserRecord(
      userId = "user_${userCounter.incrementAndGet()}",
      identityFingerprint = command.identityFingerprint,
      profileCiphertext = command.profileCiphertext,
      createdAtEpochMs = now,
    )

    users[user.userId] = user
    return user
  }

  override fun registerDevice(command: RegisterDeviceCommand): RegisteredDeviceRecord {
    requireKnownUser(command.userId)
    require(command.deviceBundle.userId == command.userId) {
      "Device bundle userId must match registerDevice userId."
    }

    val now = System.currentTimeMillis()
    val record = RegisteredDeviceRecord(
      userId = command.userId,
      deviceBundle = command.deviceBundle,
      createdAtEpochMs = now,
      lastSeenAtEpochMs = now,
    )

    devices[deviceKey(command.userId, command.deviceBundle.deviceId)] = record
    return record
  }

  override fun publishPreKeyBundle(command: PublishPreKeyBundleCommand): PreKeyBundleRecord {
    requireKnownUser(command.userId)
    require(command.deviceBundle.userId == command.userId) {
      "Device bundle userId must match publishPreKeyBundle userId."
    }
    requireKnownDevice(command.userId, command.deviceBundle.deviceId)

    val record = PreKeyBundleRecord(
      userId = command.userId,
      deviceId = command.deviceBundle.deviceId,
      deviceBundle = command.deviceBundle,
      publishedAtEpochMs = System.currentTimeMillis(),
    )

    preKeyBundles[deviceKey(command.userId, command.deviceBundle.deviceId)] = record
    return record
  }

  override fun getDeviceBundle(userId: String, deviceId: String): PreKeyBundleRecord {
    return requireNotNull(preKeyBundles[deviceKey(userId, deviceId)]) {
      "No pre-key bundle found for $userId/$deviceId."
    }
  }

  override fun createConversation(command: CreateConversationCommand): ConversationDescriptor {
    requireKnownUser(command.createdByUserId)
    require(command.participantUserIds.isNotEmpty()) {
      "At least one participant is required."
    }
    command.participantUserIds.forEach(::requireKnownUser)

    if (config.strictCryptoEnforcement && command.protocolType == ProtocolType.MLS_GROUP) {
      throw IllegalStateException("Strict mode does not allow unfinished group protocol rollout.")
    }

    val now = System.currentTimeMillis()
    val allParticipants = (command.participantUserIds + command.createdByUserId).distinct()
    val descriptor = ConversationDescriptor(
      conversationId = "conv_${conversationCounter.incrementAndGet()}",
      protocolType = command.protocolType,
      encryptedTitle = command.encryptedTitle,
      participantUserIds = allParticipants,
      createdAtEpochMs = now,
      updatedAtEpochMs = now,
    )

    conversations[descriptor.conversationId] = descriptor
    messages.putIfAbsent(descriptor.conversationId, mutableListOf())
    attachments.putIfAbsent(descriptor.conversationId, mutableListOf())

    // Initialize member tracking with creator as OWNER
    val members = mutableMapOf<String, GroupMember>()
    allParticipants.forEachIndexed { index, userId ->
      members[userId] = GroupMember(
        userId = userId,
        role = if (userId == command.createdByUserId) GroupRole.OWNER else GroupRole.MEMBER,
        addedByUserId = if (userId == command.createdByUserId) null else command.createdByUserId,
        joinedAtEpochMs = now + index, // Slight offset for ordering
      )
    }
    conversationMembers[descriptor.conversationId] = members
    conversationMaxMembers[descriptor.conversationId] = 1000

    return descriptor
  }

  override fun listConversations(userId: String): List<ConversationDescriptor> {
    requireKnownUser(userId)
    return conversations.values
      .filter { userId in it.participantUserIds }
      .sortedByDescending { it.updatedAtEpochMs }
  }

  override fun getConversation(conversationId: String): ConversationDescriptor {
    return requireNotNull(conversations[conversationId]) {
      "Conversation $conversationId was not found."
    }
  }

  override fun appendMessage(command: SendEncryptedMessageCommand): EncryptedEnvelope {
    val conversation = getConversation(command.conversationId)

    require(command.envelope.messageId.isNotBlank()) {
      "messageId is required."
    }
    require(conversation.conversationId == command.envelope.conversationId) {
      "Envelope conversation does not match the message command."
    }
    require(command.envelope.senderUserId in conversation.participantUserIds) {
      "Sender is not a participant in the conversation."
    }
    requireKnownDevice(command.envelope.senderUserId, command.envelope.senderDeviceId)

    val updatedConversation = conversation.copy(updatedAtEpochMs = maxOf(conversation.updatedAtEpochMs, command.envelope.createdAtEpochMs))
    conversations[conversation.conversationId] = updatedConversation
    messages.computeIfAbsent(command.conversationId) { mutableListOf() }
      .add(command.envelope)

    // Store mentions
    for (mentionedUserId in command.envelope.mentionedUserIds) {
      val notification = MentionNotification(
        messageId = command.envelope.messageId,
        conversationId = command.conversationId,
        senderUserId = command.envelope.senderUserId,
        mentionedUserId = mentionedUserId,
        createdAtEpochMs = command.envelope.createdAtEpochMs,
      )
      mentions.computeIfAbsent(mentionedUserId) { mutableListOf() }.add(notification)
    }

    return command.envelope
  }

  override fun listMessages(conversationId: String): List<EncryptedEnvelope> {
    getConversation(conversationId)
    return messages[conversationId]
      ?.sortedBy { it.createdAtEpochMs }
      .orEmpty()
  }

  override fun markMessageRead(command: MarkMessageReadCommand): MessageReceipt {
    val conversation = getConversation(command.conversationId)

    require(command.userId in conversation.participantUserIds) {
      "Receipt user is not a participant in the conversation."
    }
    requireKnownDevice(command.userId, command.deviceId)

    val messageExists = messages[command.conversationId]
      ?.any { it.messageId == command.messageId }
      ?: false

    require(messageExists) {
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

    val messageReceipts = receipts.computeIfAbsent(command.messageId) { mutableListOf() }
    val existingIndex = messageReceipts.indexOfFirst { it.userId == command.userId && it.deviceId == command.deviceId }

    if (existingIndex >= 0) {
      messageReceipts[existingIndex] = receipt
    } else {
      messageReceipts.add(receipt)
    }

    return receipt
  }

  override fun listReceipts(messageId: String): List<MessageReceipt> {
    return receipts[messageId]
      ?.sortedBy { it.createdAtEpochMs }
      .orEmpty()
  }

  override fun createAttachmentUploadSession(
    command: CreateAttachmentUploadSessionCommand,
  ): AttachmentUploadSession {
    val conversation = getConversation(command.conversationId)

    require(command.ownerUserId in conversation.participantUserIds) {
      "Upload owner is not a participant in the conversation."
    }
    requireKnownDevice(command.ownerUserId, command.senderDeviceId)
    require(command.expectedCiphertextByteLength > 0) {
      "expectedCiphertextByteLength must be positive."
    }

    val now = System.currentTimeMillis()
    val session = AttachmentUploadSession(
      sessionId = "upload_${UUID.randomUUID().toString().replace("-", "")}",
      uploadToken = UUID.randomUUID().toString().replace("-", ""),
      conversationId = command.conversationId,
      ownerUserId = command.ownerUserId,
      senderDeviceId = command.senderDeviceId,
      expectedCiphertextByteLength = command.expectedCiphertextByteLength,
      expectedCiphertextDigest = command.expectedCiphertextDigest,
      expiresAtEpochMs = command.expiresAtEpochMs ?: now + 15 * 60 * 1000,
    )

    uploadSessions[session.sessionId] = session
    return session
  }

  override fun getAttachmentUploadSession(sessionId: String): AttachmentUploadSession {
    val session = requireNotNull(uploadSessions[sessionId]) {
      "Upload session $sessionId was not found."
    }
    require(session.completedAtEpochMs == null) {
      "Upload session $sessionId is already complete."
    }
    require(session.expiresAtEpochMs > System.currentTimeMillis()) {
      "Upload session $sessionId has expired."
    }
    return session
  }

  override fun completeAttachmentUpload(
    sessionId: String,
    uploadToken: String,
    storageKey: String,
    ciphertextByteLength: Long,
    ciphertextDigest: String,
    completedAtEpochMs: Long,
  ): UploadedBlobReceipt {
    val session = getAttachmentUploadSession(sessionId)
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

    uploadSessions[sessionId] = session.copy(
      completedAtEpochMs = completedAtEpochMs,
      storageKey = storageKey,
    )

    return UploadedBlobReceipt(
      sessionId = sessionId,
      storageKey = storageKey,
      ciphertextByteLength = ciphertextByteLength,
      ciphertextDigest = ciphertextDigest,
      completedAtEpochMs = completedAtEpochMs,
    )
  }

  override fun storeAttachmentMetadata(command: AttachmentMetadataCommand): EncryptedAttachmentMetadata {
    val conversation = getConversation(command.conversationId)

    require(command.ownerUserId in conversation.participantUserIds) {
      "Attachment owner is not a participant in the conversation."
    }
    requireKnownDevice(command.ownerUserId, command.senderDeviceId)
    require(command.storageKey.isNotBlank()) {
      "storageKey is required."
    }

    val metadata = EncryptedAttachmentMetadata(
      attachmentId = "att_${attachmentCounter.incrementAndGet()}",
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

    attachments.computeIfAbsent(command.conversationId) { mutableListOf() }
      .add(metadata)

    return metadata
  }

  override fun listAttachments(conversationId: String): List<EncryptedAttachmentMetadata> {
    getConversation(conversationId)
    return attachments[conversationId]
      ?.sortedBy { it.createdAtEpochMs }
      .orEmpty()
  }

  override fun createInviteCode(command: CreateInviteCodeCommand): InviteCodeRecord {
    requireKnownUser(command.ownerUserId)
    require(command.encryptedInvitePayload.isNotBlank()) {
      "encryptedInvitePayload is required."
    }

    val record = InviteCodeRecord(
      inviteCode = "invite_${inviteCounter.incrementAndGet()}",
      ownerUserId = command.ownerUserId,
      encryptedInvitePayload = command.encryptedInvitePayload,
      expiresAtEpochMs = command.expiresAtEpochMs,
      createdAtEpochMs = System.currentTimeMillis(),
    )

    invites[record.inviteCode] = record
    return record
  }

  override fun addContactByInviteCode(command: AddContactByInviteCodeCommand): List<ContactRecord> {
    requireKnownUser(command.ownerUserId)
    val invite = requireNotNull(invites[command.inviteCode]) {
      "Invite code ${command.inviteCode} was not found."
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

    contacts[contactKey(ownerContact.ownerUserId, ownerContact.peerUserId)] = ownerContact
    contacts[contactKey(peerContact.ownerUserId, peerContact.peerUserId)] = peerContact
    invites[invite.inviteCode] = invite.copy(consumedByUserId = command.ownerUserId)

    return listOf(ownerContact, peerContact)
  }

  private fun requireKnownUser(userId: String) {
    require(users.containsKey(userId)) {
      "Unknown user $userId."
    }
  }

  private fun requireKnownDevice(userId: String, deviceId: String) {
    require(devices.containsKey(deviceKey(userId, deviceId))) {
      "Unknown device $userId/$deviceId."
    }
  }

  private fun deviceKey(userId: String, deviceId: String): String = "$userId:$deviceId"

  private fun contactKey(ownerUserId: String, peerUserId: String): String = "$ownerUserId:$peerUserId"

  // Group member management

  override fun listConversationMembers(conversationId: String, offset: Int, limit: Int): GroupMemberPage {
    getConversation(conversationId)
    val members = conversationMembers[conversationId]?.values?.toList().orEmpty()
      .sortedBy { it.joinedAtEpochMs }

    val totalCount = members.size
    val pagedMembers = members.drop(offset).take(limit)

    return GroupMemberPage(
      conversationId = conversationId,
      members = pagedMembers,
      totalCount = totalCount,
      offset = offset,
      limit = limit,
    )
  }

  override fun addConversationMembers(command: AddGroupMembersCommand): GroupMemberPage {
    val conversation = getConversation(command.conversationId)
    requireKnownUser(command.requestingUserId)
    command.userIds.forEach(::requireKnownUser)

    val members = conversationMembers[command.conversationId]
      ?: throw IllegalStateException("Conversation ${command.conversationId} has no member tracking.")

    val requesterMember = members[command.requestingUserId]
      ?: throw IllegalStateException("User ${command.requestingUserId} is not a member of the conversation.")

    require(requesterMember.role in setOf(GroupRole.OWNER, GroupRole.ADMIN)) {
      "Only owners and admins can add members."
    }

    require(command.userIds.size <= 100) {
      "Cannot add more than 100 members at once."
    }

    val maxMembers = conversationMaxMembers[command.conversationId] ?: 1000
    val currentCount = members.size
    val newUserIds = command.userIds.filter { it !in members }

    require(currentCount + newUserIds.size <= maxMembers) {
      "Adding ${newUserIds.size} members would exceed the maximum of $maxMembers."
    }

    val now = System.currentTimeMillis()
    newUserIds.forEachIndexed { index, userId ->
      members[userId] = GroupMember(
        userId = userId,
        role = GroupRole.MEMBER,
        addedByUserId = command.requestingUserId,
        joinedAtEpochMs = now + index,
      )
    }

    // Update conversation descriptor with new participant list
    val updatedDescriptor = conversation.copy(
      participantUserIds = members.keys.toList(),
      updatedAtEpochMs = now,
    )
    conversations[command.conversationId] = updatedDescriptor

    return listConversationMembers(command.conversationId, 0, 50)
  }

  override fun removeConversationMembers(command: RemoveGroupMembersCommand): GroupMemberPage {
    val conversation = getConversation(command.conversationId)
    requireKnownUser(command.requestingUserId)

    val members = conversationMembers[command.conversationId]
      ?: throw IllegalStateException("Conversation ${command.conversationId} has no member tracking.")

    val requesterMember = members[command.requestingUserId]
      ?: throw IllegalStateException("User ${command.requestingUserId} is not a member of the conversation.")

    require(requesterMember.role in setOf(GroupRole.OWNER, GroupRole.ADMIN)) {
      "Only owners and admins can remove members."
    }

    require(command.userIds.size <= 100) {
      "Cannot remove more than 100 members at once."
    }

    for (userId in command.userIds) {
      val targetMember = members[userId] ?: continue

      // Admins cannot remove owners or other admins
      if (requesterMember.role == GroupRole.ADMIN) {
        require(targetMember.role == GroupRole.MEMBER) {
          "Admins can only remove members, not owners or other admins."
        }
      }

      // Owner cannot be removed
      require(targetMember.role != GroupRole.OWNER) {
        "The owner cannot be removed from the conversation."
      }

      members.remove(userId)
    }

    // Update conversation descriptor
    val now = System.currentTimeMillis()
    val updatedDescriptor = conversation.copy(
      participantUserIds = members.keys.toList(),
      updatedAtEpochMs = now,
    )
    conversations[command.conversationId] = updatedDescriptor

    return listConversationMembers(command.conversationId, 0, 50)
  }

  override fun updateMemberRole(command: UpdateMemberRoleCommand): GroupMemberPage {
    getConversation(command.conversationId)
    requireKnownUser(command.requestingUserId)
    requireKnownUser(command.targetUserId)

    val members = conversationMembers[command.conversationId]
      ?: throw IllegalStateException("Conversation ${command.conversationId} has no member tracking.")

    val requesterMember = members[command.requestingUserId]
      ?: throw IllegalStateException("User ${command.requestingUserId} is not a member of the conversation.")

    require(requesterMember.role == GroupRole.OWNER) {
      "Only the owner can change member roles."
    }

    val targetMember = members[command.targetUserId]
      ?: throw IllegalStateException("User ${command.targetUserId} is not a member of the conversation.")

    require(command.targetUserId != command.requestingUserId) {
      "Cannot change your own role."
    }

    require(command.newRole != GroupRole.OWNER) {
      "Ownership transfer is not supported via role change."
    }

    members[command.targetUserId] = targetMember.copy(role = command.newRole)

    return listConversationMembers(command.conversationId, 0, 50)
  }

  override fun leaveConversation(command: LeaveConversationCommand): ConversationDescriptor {
    val conversation = getConversation(command.conversationId)
    requireKnownUser(command.userId)

    val members = conversationMembers[command.conversationId]
      ?: throw IllegalStateException("Conversation ${command.conversationId} has no member tracking.")

    val leavingMember = members[command.userId]
      ?: throw IllegalStateException("User ${command.userId} is not a member of the conversation.")

    // Owner cannot leave without transferring ownership
    if (leavingMember.role == GroupRole.OWNER && members.size > 1) {
      // Auto-transfer ownership to the first admin, or first member by join time
      val newOwner = members.values
        .filter { it.userId != command.userId }
        .sortedWith(compareBy({ if (it.role == GroupRole.ADMIN) 0 else 1 }, { it.joinedAtEpochMs }))
        .firstOrNull()

      if (newOwner != null) {
        members[newOwner.userId] = newOwner.copy(role = GroupRole.OWNER)
      }
    }

    members.remove(command.userId)

    val now = System.currentTimeMillis()
    val updatedDescriptor = conversation.copy(
      participantUserIds = members.keys.toList(),
      updatedAtEpochMs = now,
    )
    conversations[command.conversationId] = updatedDescriptor

    return updatedDescriptor
  }

  override fun updateConversationSettings(command: UpdateConversationSettingsCommand): ConversationDescriptor {
    val conversation = getConversation(command.conversationId)
    requireKnownUser(command.requestingUserId)

    val members = conversationMembers[command.conversationId]
      ?: throw IllegalStateException("Conversation ${command.conversationId} has no member tracking.")

    val requesterMember = members[command.requestingUserId]
      ?: throw IllegalStateException("User ${command.requestingUserId} is not a member of the conversation.")

    require(requesterMember.role in setOf(GroupRole.OWNER, GroupRole.ADMIN)) {
      "Only owners and admins can update conversation settings."
    }

    val now = System.currentTimeMillis()
    var updatedDescriptor = conversation.copy(updatedAtEpochMs = now)

    if (command.encryptedTitle != null) {
      updatedDescriptor = updatedDescriptor.copy(encryptedTitle = command.encryptedTitle)
    }

    command.maxMembers?.let { maxMembers ->
      require(maxMembers in 2..1000) {
        "maxMembers must be between 2 and 1000."
      }
      conversationMaxMembers[command.conversationId] = maxMembers
    }

    conversations[command.conversationId] = updatedDescriptor

    return updatedDescriptor
  }

  // Mentions

  override fun listMentionsForUser(userId: String, offset: Int, limit: Int): List<MentionNotification> {
    requireKnownUser(userId)
    return mentions[userId]
      ?.sortedByDescending { it.createdAtEpochMs }
      ?.drop(offset)
      ?.take(limit)
      .orEmpty()
  }

  // Aggregated read receipts

  override fun getAggregatedReadReceipt(messageId: String): AggregatedReadReceipt {
    val messageReceipts = receipts[messageId].orEmpty()
    val readReceipts = messageReceipts.filter { it.type == ReceiptType.READ }
    val readByUserIds = readReceipts.map { it.userId }.distinct()

    // Find the conversation to get total member count
    val conversationId = messageReceipts.firstOrNull()?.conversationId
      ?: messages.values.flatten().find { it.messageId == messageId }?.conversationId
      ?: throw IllegalStateException("Message $messageId not found.")

    val totalMembers = conversationMembers[conversationId]?.size ?: 0

    return AggregatedReadReceipt(
      messageId = messageId,
      conversationId = conversationId,
      readCount = readByUserIds.size,
      totalMembers = totalMembers,
      readByUserIds = readByUserIds.take(20), // Only return first 20 for UI preview
    )
  }

  // Phone authentication

  override fun createPhoneVerification(request: PhoneVerificationRequest, otpHash: String): PhoneVerificationResponse {
    require(request.phoneNumber.isNotBlank()) { "Phone number is required." }
    require(request.countryCode.isNotBlank()) { "Country code is required." }

    val now = System.currentTimeMillis()
    val verificationId = "verify_${UUID.randomUUID().toString().replace("-", "")}"

    val data = PhoneVerificationData(
      verificationId = verificationId,
      phoneNumber = request.phoneNumber,
      countryCode = request.countryCode,
      otpHash = otpHash,
      status = PhoneVerificationStatus.PENDING,
      attempts = 0,
      createdAtEpochMs = now,
      expiresAtEpochMs = now + 10 * 60 * 1000, // 10 minutes
    )

    phoneVerifications[verificationId] = data

    return PhoneVerificationResponse(
      verificationId = verificationId,
      phoneNumber = request.phoneNumber,
      expiresAtEpochMs = data.expiresAtEpochMs,
      status = PhoneVerificationStatus.PENDING,
    )
  }

  override fun verifyOtp(command: VerifyOtpCommand, otpHash: String): VerifyOtpResponse {
    val data = phoneVerifications[command.verificationId]
      ?: return VerifyOtpResponse(success = false, errorMessage = "Verification not found.")

    val now = System.currentTimeMillis()

    if (data.expiresAtEpochMs < now) {
      phoneVerifications[command.verificationId] = data.copy(status = PhoneVerificationStatus.EXPIRED)
      return VerifyOtpResponse(success = false, errorMessage = "Verification expired.")
    }

    if (data.status != PhoneVerificationStatus.PENDING) {
      return VerifyOtpResponse(success = false, errorMessage = "Verification already processed.")
    }

    if (data.attempts >= 3) {
      phoneVerifications[command.verificationId] = data.copy(status = PhoneVerificationStatus.FAILED)
      return VerifyOtpResponse(success = false, errorMessage = "Too many attempts.")
    }

    if (data.otpHash != otpHash) {
      phoneVerifications[command.verificationId] = data.copy(attempts = data.attempts + 1)
      return VerifyOtpResponse(success = false, errorMessage = "Invalid code.")
    }

    // Success - mark as verified
    phoneVerifications[command.verificationId] = data.copy(status = PhoneVerificationStatus.VERIFIED)

    // Check if user already exists
    val phoneKey = "${data.countryCode}:${data.phoneNumber}"
    val existingUserId = phoneToUser[phoneKey]

    if (existingUserId != null) {
      return VerifyOtpResponse(
        success = true,
        userId = existingUserId,
        isNewUser = false,
      )
    }

    // Create new user
    val newUser = registerUser(RegisterUserCommand(
      identityFingerprint = "phone_${phoneKey}_${now}",
    ))

    val phoneUser = PhoneAuthenticatedUser(
      userId = newUser.userId,
      phoneNumber = data.phoneNumber,
      countryCode = data.countryCode,
      verifiedAtEpochMs = now,
    )

    userPhones[newUser.userId] = phoneUser
    phoneToUser[phoneKey] = newUser.userId

    return VerifyOtpResponse(
      success = true,
      userId = newUser.userId,
      isNewUser = true,
    )
  }

  override fun getUserByPhone(phoneNumber: String, countryCode: String): PhoneAuthenticatedUser? {
    val phoneKey = "$countryCode:$phoneNumber"
    val userId = phoneToUser[phoneKey] ?: return null
    return userPhones[userId]
  }

  override fun linkPhoneToUser(userId: String, phoneNumber: String, countryCode: String): PhoneAuthenticatedUser {
    requireKnownUser(userId)

    val phoneKey = "$countryCode:$phoneNumber"
    require(phoneToUser[phoneKey] == null) {
      "Phone number is already linked to another user."
    }

    val now = System.currentTimeMillis()
    val phoneUser = PhoneAuthenticatedUser(
      userId = userId,
      phoneNumber = phoneNumber,
      countryCode = countryCode,
      verifiedAtEpochMs = now,
    )

    userPhones[userId] = phoneUser
    phoneToUser[phoneKey] = userId

    return phoneUser
  }

  // Push tokens

  override fun registerPushToken(command: RegisterPushTokenCommand): PushToken {
    requireKnownUser(command.userId)
    requireKnownDevice(command.userId, command.deviceId)

    val platform = try {
      PushPlatform.valueOf(command.platform.uppercase())
    } catch (_: IllegalArgumentException) {
      throw IllegalArgumentException("Invalid platform: ${command.platform}. Must be 'fcm' or 'apns'.")
    }

    val now = System.currentTimeMillis()
    val existing = pushTokens[pushTokenKey(command.userId, command.deviceId)]

    val token = PushToken(
      userId = command.userId,
      deviceId = command.deviceId,
      platform = platform,
      token = command.token,
      createdAtEpochMs = existing?.createdAtEpochMs ?: now,
      updatedAtEpochMs = now,
    )

    pushTokens[pushTokenKey(command.userId, command.deviceId)] = token
    return token
  }

  override fun getPushTokensForUser(userId: String): List<PushToken> {
    requireKnownUser(userId)
    return pushTokens.values.filter { it.userId == userId }
  }

  override fun getPushTokensForConversation(conversationId: String): List<PushToken> {
    val conversation = conversations[conversationId]
      ?: throw IllegalArgumentException("Conversation $conversationId not found.")
    return conversation.participantUserIds.flatMap { userId ->
      pushTokens.values.filter { it.userId == userId }
    }
  }

  override fun deletePushToken(userId: String, deviceId: String) {
    pushTokens.remove(pushTokenKey(userId, deviceId))
  }

  private fun pushTokenKey(userId: String, deviceId: String): String = "$userId:$deviceId"
}
