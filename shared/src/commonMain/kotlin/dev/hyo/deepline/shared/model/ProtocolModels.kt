package dev.hyo.deepline.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class ProtocolType {
  SIGNAL_1TO1,
  MLS_GROUP,
}

@Serializable
enum class GroupRole {
  OWNER,
  ADMIN,
  MEMBER,
}

@Serializable
enum class ReceiptType {
  DELIVERED,
  READ,
}

@Serializable
data class DeviceBundle(
  val userId: String,
  val deviceId: String,
  val identityKey: String,
  val signedPreKey: String,
  val signedPreKeySignature: String,
  val signingPublicKey: String,
  val oneTimePreKeys: List<PublishedOneTimePreKey>,
  val protocolVersion: String,
)

@Serializable
data class PublishedOneTimePreKey(
  val keyId: String,
  val publicKey: String,
)

@Serializable
data class EncryptedAttachmentReference(
  val attachmentId: String,
  val ciphertextDigest: String,
  val ciphertextByteLength: Long,
  val metadataCiphertext: String,
  val protocolVersion: String,
)

@Serializable
data class EncryptedEnvelope(
  val messageId: String,
  val conversationId: String,
  val senderUserId: String,
  val senderDeviceId: String,
  val ciphertext: String,
  val encryptionMetadata: Map<String, String>,
  val protocolVersion: String,
  val attachments: List<EncryptedAttachmentReference> = emptyList(),
  val mentionedUserIds: List<String> = emptyList(),
  val createdAtEpochMs: Long,
)

@Serializable
data class ConversationDescriptor(
  val conversationId: String,
  val protocolType: ProtocolType,
  val encryptedTitle: String? = null,
  val participantUserIds: List<String>,
  val createdAtEpochMs: Long,
  val updatedAtEpochMs: Long,
)

@Serializable
data class UserRecord(
  val userId: String,
  val identityFingerprint: String,
  val profileCiphertext: String? = null,
  val createdAtEpochMs: Long,
)

@Serializable
data class RegisteredDeviceRecord(
  val userId: String,
  val deviceBundle: DeviceBundle,
  val createdAtEpochMs: Long,
  val lastSeenAtEpochMs: Long,
)

@Serializable
data class PreKeyBundleRecord(
  val userId: String,
  val deviceId: String,
  val deviceBundle: DeviceBundle,
  val publishedAtEpochMs: Long,
)

@Serializable
data class MessageReceipt(
  val messageId: String,
  val conversationId: String,
  val userId: String,
  val deviceId: String,
  val type: ReceiptType,
  val createdAtEpochMs: Long,
)

@Serializable
data class EncryptedAttachmentMetadata(
  val attachmentId: String,
  val conversationId: String,
  val ownerUserId: String,
  val senderDeviceId: String,
  val storageKey: String,
  val ciphertextDigest: String,
  val ciphertextByteLength: Long,
  val metadataCiphertext: String,
  val protocolVersion: String,
  val messageId: String? = null,
  val createdAtEpochMs: Long,
)

@Serializable
data class AttachmentUploadSession(
  val sessionId: String,
  val uploadToken: String,
  val conversationId: String,
  val ownerUserId: String,
  val senderDeviceId: String,
  val expectedCiphertextByteLength: Long,
  val expectedCiphertextDigest: String? = null,
  val expiresAtEpochMs: Long,
  val completedAtEpochMs: Long? = null,
  val storageKey: String? = null,
)

@Serializable
data class UploadedBlobReceipt(
  val sessionId: String,
  val storageKey: String,
  val ciphertextByteLength: Long,
  val ciphertextDigest: String,
  val completedAtEpochMs: Long,
)

@Serializable
data class InviteCodeRecord(
  val inviteCode: String,
  val ownerUserId: String,
  val encryptedInvitePayload: String,
  val expiresAtEpochMs: Long? = null,
  val createdAtEpochMs: Long,
  val consumedByUserId: String? = null,
)

@Serializable
data class ContactRecord(
  val ownerUserId: String,
  val peerUserId: String,
  val inviteCode: String,
  val encryptedAlias: String? = null,
  val createdAtEpochMs: Long,
)

@Serializable
data class RegisterUserCommand(
  val identityFingerprint: String,
  val profileCiphertext: String? = null,
)

@Serializable
data class RegisterDeviceCommand(
  val userId: String,
  val deviceBundle: DeviceBundle,
)

@Serializable
data class PublishPreKeyBundleCommand(
  val userId: String,
  val deviceBundle: DeviceBundle,
)

@Serializable
data class CreateConversationCommand(
  val createdByUserId: String,
  val participantUserIds: List<String>,
  val protocolType: ProtocolType,
  val encryptedTitle: String? = null,
)

@Serializable
data class SendEncryptedMessageCommand(
  val conversationId: String,
  val envelope: EncryptedEnvelope,
)

@Serializable
data class AttachmentMetadataCommand(
  val conversationId: String,
  val ownerUserId: String,
  val senderDeviceId: String,
  val storageKey: String,
  val ciphertextDigest: String,
  val ciphertextByteLength: Long,
  val metadataCiphertext: String,
  val protocolVersion: String,
  val messageId: String? = null,
)

@Serializable
data class CreateAttachmentUploadSessionCommand(
  val conversationId: String,
  val ownerUserId: String,
  val senderDeviceId: String,
  val expectedCiphertextByteLength: Long,
  val expectedCiphertextDigest: String? = null,
  val expiresAtEpochMs: Long? = null,
)

@Serializable
data class CreateInviteCodeCommand(
  val ownerUserId: String,
  val encryptedInvitePayload: String,
  val expiresAtEpochMs: Long? = null,
)

@Serializable
data class AddContactByInviteCodeCommand(
  val ownerUserId: String,
  val inviteCode: String,
  val encryptedAlias: String? = null,
)

@Serializable
data class MarkMessageReadCommand(
  val messageId: String,
  val conversationId: String,
  val userId: String,
  val deviceId: String,
  val readAtEpochMs: Long,
)

// Group member management models

@Serializable
data class GroupMember(
  val userId: String,
  val role: GroupRole,
  val addedByUserId: String? = null,
  val joinedAtEpochMs: Long,
)

@Serializable
data class GroupMemberPage(
  val conversationId: String,
  val members: List<GroupMember>,
  val totalCount: Int,
  val offset: Int,
  val limit: Int,
)

@Serializable
data class AddGroupMembersCommand(
  val conversationId: String,
  val requestingUserId: String,
  val userIds: List<String>,
)

@Serializable
data class RemoveGroupMembersCommand(
  val conversationId: String,
  val requestingUserId: String,
  val userIds: List<String>,
)

@Serializable
data class UpdateMemberRoleCommand(
  val conversationId: String,
  val requestingUserId: String,
  val targetUserId: String,
  val newRole: GroupRole,
)

@Serializable
data class LeaveConversationCommand(
  val conversationId: String,
  val userId: String,
)

@Serializable
data class UpdateConversationSettingsCommand(
  val conversationId: String,
  val requestingUserId: String,
  val encryptedTitle: String? = null,
  val maxMembers: Int? = null,
)

// Aggregated read receipts for groups
@Serializable
data class AggregatedReadReceipt(
  val messageId: String,
  val conversationId: String,
  val readCount: Int,
  val totalMembers: Int,
  val readByUserIds: List<String> = emptyList(),
)

// Mention notification
@Serializable
data class MentionNotification(
  val messageId: String,
  val conversationId: String,
  val senderUserId: String,
  val mentionedUserId: String,
  val createdAtEpochMs: Long,
)

// Media attachment types
@Serializable
enum class MediaType {
  IMAGE,
  VIDEO,
  AUDIO,
  DOCUMENT,
}

@Serializable
data class MediaMetadata(
  val mediaType: MediaType,
  val mimeType: String,
  val fileName: String,
  val width: Int? = null,
  val height: Int? = null,
  val durationMs: Long? = null,
  val thumbnailStorageKey: String? = null,
)

// Phone authentication models
@Serializable
enum class PhoneVerificationStatus {
  PENDING,
  VERIFIED,
  EXPIRED,
  FAILED,
}

@Serializable
data class PhoneVerificationRequest(
  val phoneNumber: String,
  val countryCode: String,
)

@Serializable
data class PhoneVerificationResponse(
  val verificationId: String,
  val phoneNumber: String,
  val expiresAtEpochMs: Long,
  val status: PhoneVerificationStatus,
)

@Serializable
data class VerifyOtpCommand(
  val verificationId: String,
  val otpCode: String,
)

@Serializable
data class VerifyOtpResponse(
  val success: Boolean,
  val userId: String? = null,
  val isNewUser: Boolean = false,
  val errorMessage: String? = null,
)

@Serializable
data class PhoneAuthenticatedUser(
  val userId: String,
  val phoneNumber: String,
  val countryCode: String,
  val verifiedAtEpochMs: Long,
)

// Push notification models

@Serializable
enum class PushPlatform {
  FCM,
  APNS,
}

@Serializable
data class PushToken(
  val userId: String,
  val deviceId: String,
  val platform: PushPlatform,
  val token: String,
  val createdAtEpochMs: Long,
  val updatedAtEpochMs: Long,
)

@Serializable
data class RegisterPushTokenCommand(
  val userId: String,
  val deviceId: String,
  val platform: String,
  val token: String,
)
