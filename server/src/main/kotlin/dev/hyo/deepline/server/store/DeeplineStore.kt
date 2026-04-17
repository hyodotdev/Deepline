package dev.hyo.deepline.server.store

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
import dev.hyo.deepline.shared.model.GroupMemberPage
import dev.hyo.deepline.shared.model.InviteCodeRecord
import dev.hyo.deepline.shared.model.LeaveConversationCommand
import dev.hyo.deepline.shared.model.MarkMessageReadCommand
import dev.hyo.deepline.shared.model.MentionNotification
import dev.hyo.deepline.shared.model.MessageReceipt
import dev.hyo.deepline.shared.model.PhoneAuthenticatedUser
import dev.hyo.deepline.shared.model.PhoneVerificationRequest
import dev.hyo.deepline.shared.model.PhoneVerificationResponse
import dev.hyo.deepline.shared.model.PreKeyBundleRecord
import dev.hyo.deepline.shared.model.PublishPreKeyBundleCommand
import dev.hyo.deepline.shared.model.PushToken
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

interface DeeplineStore {
  fun registerUser(command: RegisterUserCommand): UserRecord
  fun registerDevice(command: RegisterDeviceCommand): RegisteredDeviceRecord
  fun publishPreKeyBundle(command: PublishPreKeyBundleCommand): PreKeyBundleRecord
  fun getDeviceBundle(userId: String, deviceId: String): PreKeyBundleRecord
  fun createConversation(command: CreateConversationCommand): ConversationDescriptor
  fun listConversations(userId: String): List<ConversationDescriptor>
  fun getConversation(conversationId: String): ConversationDescriptor
  fun appendMessage(command: SendEncryptedMessageCommand): EncryptedEnvelope
  fun listMessages(conversationId: String): List<EncryptedEnvelope>
  fun markMessageRead(command: MarkMessageReadCommand): MessageReceipt
  fun listReceipts(messageId: String): List<MessageReceipt>
  fun createAttachmentUploadSession(command: CreateAttachmentUploadSessionCommand): AttachmentUploadSession
  fun getAttachmentUploadSession(sessionId: String): AttachmentUploadSession
  fun completeAttachmentUpload(
    sessionId: String,
    uploadToken: String,
    storageKey: String,
    ciphertextByteLength: Long,
    ciphertextDigest: String,
    completedAtEpochMs: Long,
  ): UploadedBlobReceipt
  fun storeAttachmentMetadata(command: AttachmentMetadataCommand): EncryptedAttachmentMetadata
  fun listAttachments(conversationId: String): List<EncryptedAttachmentMetadata>
  fun createInviteCode(command: CreateInviteCodeCommand): InviteCodeRecord
  fun addContactByInviteCode(command: AddContactByInviteCodeCommand): List<ContactRecord>

  // Group member management
  fun listConversationMembers(conversationId: String, offset: Int, limit: Int): GroupMemberPage
  fun addConversationMembers(command: AddGroupMembersCommand): GroupMemberPage
  fun removeConversationMembers(command: RemoveGroupMembersCommand): GroupMemberPage
  fun updateMemberRole(command: UpdateMemberRoleCommand): GroupMemberPage
  fun leaveConversation(command: LeaveConversationCommand): ConversationDescriptor
  fun updateConversationSettings(command: UpdateConversationSettingsCommand): ConversationDescriptor

  // Mentions
  fun listMentionsForUser(userId: String, offset: Int, limit: Int): List<MentionNotification>

  // Aggregated read receipts
  fun getAggregatedReadReceipt(messageId: String): AggregatedReadReceipt

  // Phone authentication
  fun createPhoneVerification(request: PhoneVerificationRequest, otpHash: String): PhoneVerificationResponse
  fun verifyOtp(command: VerifyOtpCommand, otpHash: String): VerifyOtpResponse
  fun getUserByPhone(phoneNumber: String, countryCode: String): PhoneAuthenticatedUser?
  fun linkPhoneToUser(userId: String, phoneNumber: String, countryCode: String): PhoneAuthenticatedUser

  // Push tokens
  fun registerPushToken(command: RegisterPushTokenCommand): PushToken
  fun getPushTokensForUser(userId: String): List<PushToken>
  fun getPushTokensForConversation(conversationId: String): List<PushToken>
  fun deletePushToken(userId: String, deviceId: String)
}
