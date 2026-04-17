package dev.hyo.deepline.android.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.hyo.deepline.shared.model.AddContactByInviteCodeCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.launch
import dev.hyo.deepline.shared.model.AddGroupMembersCommand
import dev.hyo.deepline.shared.model.ConversationDescriptor
import dev.hyo.deepline.shared.model.CreateConversationCommand
import dev.hyo.deepline.shared.model.CreateInviteCodeCommand
import dev.hyo.deepline.shared.model.EncryptedEnvelope
import dev.hyo.deepline.shared.model.GroupMember
import dev.hyo.deepline.shared.model.GroupRole
import dev.hyo.deepline.shared.model.LeaveConversationCommand
import dev.hyo.deepline.shared.model.ProtocolType
import dev.hyo.deepline.shared.model.PublishPreKeyBundleCommand
import dev.hyo.deepline.shared.model.RegisterDeviceCommand
import dev.hyo.deepline.shared.model.RegisterUserCommand
import dev.hyo.deepline.shared.model.RemoveGroupMembersCommand
import dev.hyo.deepline.shared.model.SendEncryptedMessageCommand
import dev.hyo.deepline.shared.model.UpdateConversationSettingsCommand
import dev.hyo.deepline.shared.model.UpdateMemberRoleCommand
import java.util.UUID

data class DeeplineLaunchConfig(
  val resetState: Boolean = false,
  val overrideServerUrl: String? = null,
  val overrideUserId: String? = null,
  val overrideDeviceId: String? = null,
  val overrideDisplayName: String? = null,
  val overrideDeviceName: String? = null,
)

data class DeeplineChat(
  val id: String,
  val title: String,
  val protocolType: ProtocolType,
  val subtitle: String,
  val preview: String,
)

data class DeeplineMessage(
  val id: String,
  val conversationId: String,
  val author: String,
  val body: String,
  val isMine: Boolean,
  val isSystem: Boolean,
)

val ProtocolType.displayLabel: String
  get() = when (this) {
    ProtocolType.SIGNAL_1TO1 -> "signal 1to1"
    ProtocolType.MLS_GROUP -> "mls group"
  }

class DeeplineAppModel(
  context: Context,
  launchConfig: DeeplineLaunchConfig,
) {
  private val appContext = context.applicationContext
  private val prefs = appContext.getSharedPreferences(PrefFile, Context.MODE_PRIVATE)
  private val client = DeeplineServerClient()

  var displayName by mutableStateOf("")
    private set

  var deviceName by mutableStateOf("Android Secure")
    private set

  var serverBaseUrl by mutableStateOf(DefaultServerUrl)
    private set

  var identityReady by mutableStateOf(false)
    private set

  var userId by mutableStateOf<String?>(null)
    private set

  var deviceId by mutableStateOf<String?>(null)
    private set

  var chats by mutableStateOf(emptyList<DeeplineChat>())
    private set

  var messages by mutableStateOf<Map<String, List<DeeplineMessage>>>(emptyMap())
    private set

  var groupMembers by mutableStateOf<Map<String, List<GroupMember>>>(emptyMap())
    private set

  var latestInviteCode by mutableStateOf("")
    private set

  var errorMessage by mutableStateOf<String?>(null)
    private set

  var isBusy by mutableStateOf(false)
    private set

  var pendingConversationId by mutableStateOf<String?>(null)
    private set

  // Phone authentication state
  var phoneVerificationId by mutableStateOf<String?>(null)
    private set

  var phoneAuthMessage by mutableStateOf<String?>(null)
    private set

  // WebSocket state
  private val modelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private var webSocketJob: Job? = null
  private var activeWebSocketConversationId: String? = null

  var isWebSocketConnected by mutableStateOf(false)
    private set

  init {
    if (launchConfig.resetState) {
      prefs.edit().clear().apply()
    }
    val overrideUserId = launchConfig.overrideUserId?.takeIf { it.isNotBlank() }
    val overrideDeviceId = launchConfig.overrideDeviceId?.takeIf { it.isNotBlank() }
    if (overrideUserId != null && overrideDeviceId != null) {
      prefs.edit()
        .putString(KeyUserId, overrideUserId)
        .putString(KeyDeviceId, overrideDeviceId)
        .apply {
          launchConfig.overrideDisplayName?.takeIf { it.isNotBlank() }?.let {
            putString(KeyDisplayName, it)
          }
          launchConfig.overrideDeviceName?.takeIf { it.isNotBlank() }?.let {
            putString(KeyDeviceName, it)
          }
        }
        .apply()
    }
    val storedUserId = prefs.getString(KeyUserId, null)
    val storedDeviceId = prefs.getString(KeyDeviceId, null)
    displayName = prefs.getString(KeyDisplayName, "") ?: ""
    deviceName = prefs.getString(KeyDeviceName, "Android Secure") ?: "Android Secure"
    serverBaseUrl = launchConfig.overrideServerUrl
      ?: prefs.getString(KeyServerUrl, DefaultServerUrl)
      ?: DefaultServerUrl
    userId = storedUserId
    deviceId = storedDeviceId
    identityReady = storedUserId != null && storedDeviceId != null
  }

  suspend fun bootstrapIfNeeded() {
    if (!identityReady) return
    runCatching { ensureReachableServerBaseUrl() }
      .onFailure { errorMessage = readableError(it) }
      .getOrNull() ?: return
    refreshConversations()
  }

  suspend fun completeIdentity(name: String, device: String): String? =
    performBusyTask {
      ensureReachableServerBaseUrl()
      val resolvedName = name.trim().ifEmpty { "Deepline User" }
      val resolvedDevice = device.trim().ifEmpty { "Secure Device" }
      val fingerprint = randomToken("fp")
      val user = client.registerUser(
        baseUrl = serverBaseUrl,
        command = RegisterUserCommand(
          identityFingerprint = fingerprint,
          profileCiphertext = LocalOpaqueCodec.encode(resolvedName),
        ),
      )
      val generatedDeviceId = randomToken("android")
      val bundle = makeLocalDevBundle(userId = user.userId, deviceId = generatedDeviceId)
      client.registerDevice(
        baseUrl = serverBaseUrl,
        command = RegisterDeviceCommand(
          userId = user.userId,
          deviceBundle = bundle,
        ),
      )
      client.publishPreKeys(
        baseUrl = serverBaseUrl,
        command = PublishPreKeyBundleCommand(
          userId = user.userId,
          deviceBundle = bundle,
        ),
      )

      displayName = resolvedName
      deviceName = resolvedDevice
      userId = user.userId
      deviceId = generatedDeviceId
      identityReady = true
      persistIdentity()

      ensureStarterConversation(user.userId, generatedDeviceId).also {
        pendingConversationId = it
      }
    }

  suspend fun refreshConversations() {
    val currentUserId = userId ?: return
    performSilentTask {
      ensureReachableServerBaseUrl()
      val conversations = client.listConversations(serverBaseUrl, currentUserId)
      val hydratedMessages = conversations.associate { conversation ->
        conversation.conversationId to parseMessages(
          currentUserId = currentUserId,
          envelopes = client.listMessages(serverBaseUrl, conversation.conversationId),
        )
      }
      messages = messages + hydratedMessages
      chats = conversations.map { conversation ->
        val title = LocalOpaqueCodec.decode(conversation.encryptedTitle)
          ?: "Conversation ${conversation.conversationId.takeLast(4)}"
        val memberCount = conversation.participantUserIds.size
        val preview = hydratedMessages[conversation.conversationId]
          ?.lastOrNull()
          ?.body
          ?: defaultPreview(conversation)
        DeeplineChat(
          id = conversation.conversationId,
          title = title,
          protocolType = conversation.protocolType,
          subtitle = "${conversation.protocolType.displayLabel} • $memberCount member${if (memberCount == 1) "" else "s"}",
          preview = preview,
        )
      }
    }
  }

  suspend fun loadMessages(conversationId: String) {
    val currentUserId = userId ?: return
    performSilentTask {
      ensureReachableServerBaseUrl()
      val parsedMessages = parseMessages(
        currentUserId = currentUserId,
        envelopes = client.listMessages(serverBaseUrl, conversationId),
      )
      messages = messages + (conversationId to parsedMessages)
      updateConversationPreview(conversationId, parsedMessages.lastOrNull()?.body)
    }
  }

  // WebSocket real-time messaging

  fun connectToConversation(conversationId: String) {
    val currentUserId = userId ?: return

    // Disconnect from any existing connection
    if (activeWebSocketConversationId != null && activeWebSocketConversationId != conversationId) {
      disconnectFromConversation()
    }

    if (activeWebSocketConversationId == conversationId && webSocketJob?.isActive == true) {
      return // Already connected to this conversation
    }

    activeWebSocketConversationId = conversationId

    webSocketJob = modelScope.launch {
      client.connectToConversation(serverBaseUrl, conversationId)
        .retry(3) { cause ->
          delay(1000L * (1 shl minOf(3, 3))) // Exponential backoff up to 8 seconds
          cause !is kotlinx.coroutines.CancellationException
        }
        .catch { error ->
          isWebSocketConnected = false
          // Log error but don't show to user for background connection issues
        }
        .collect { envelope ->
          isWebSocketConnected = true
          handleIncomingEnvelope(currentUserId, envelope)
        }
    }
  }

  fun disconnectFromConversation() {
    activeWebSocketConversationId?.let { conversationId ->
      client.disconnectFromConversation(conversationId)
    }
    webSocketJob?.cancel()
    webSocketJob = null
    activeWebSocketConversationId = null
    isWebSocketConnected = false
  }

  private fun handleIncomingEnvelope(currentUserId: String, envelope: EncryptedEnvelope) {
    val conversationId = envelope.conversationId
    val existingMessages = messages[conversationId].orEmpty()

    // Check if message already exists (dedup)
    if (existingMessages.any { it.id == envelope.messageId }) {
      return
    }

    val newMessage = parseMessage(currentUserId, envelope)
    messages = messages + (conversationId to existingMessages + newMessage)
    updateConversationPreview(conversationId, newMessage.body)
  }

  private fun parseMessage(currentUserId: String, envelope: EncryptedEnvelope): DeeplineMessage {
    val kind = envelope.encryptionMetadata["kind"].orEmpty()
    val isSystem = kind.startsWith("starter_")
    return DeeplineMessage(
      id = envelope.messageId,
      conversationId = envelope.conversationId,
      author = when {
        isSystem -> "Deepline"
        envelope.senderUserId == currentUserId -> displayName.ifEmpty { "Me" }
        else -> "Peer ${envelope.senderUserId.takeLast(4)}"
      },
      body = LocalOpaqueCodec.decode(envelope.ciphertext) ?: "Encrypted payload",
      isMine = envelope.senderUserId == currentUserId && !isSystem,
      isSystem = isSystem,
    )
  }

  suspend fun sendMessage(conversationId: String, text: String) {
    val currentUserId = userId ?: return
    val currentDeviceId = deviceId ?: return
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return

    performBusyTask {
      sendEnvelope(
        conversationId = conversationId,
        senderUserId = currentUserId,
        senderDeviceId = currentDeviceId,
        body = trimmed,
        kind = "user_note",
      )
      loadMessages(conversationId)
      refreshConversations()
    }
  }

  suspend fun createInvite() {
    val currentUserId = userId ?: return
    performBusyTask {
      val invite = client.createInvite(
        baseUrl = serverBaseUrl,
        command = CreateInviteCodeCommand(
          ownerUserId = currentUserId,
          encryptedInvitePayload = LocalOpaqueCodec.encode(displayName.ifEmpty { "Deepline User" }),
          expiresAtEpochMs = null,
        ),
      )
      latestInviteCode = invite.inviteCode
    }
  }

  suspend fun importInvite(code: String): String? {
    val currentUserId = userId ?: return null
    val normalized = code.trim()
    if (normalized.isEmpty()) return null

    return performBusyTask {
      val contacts = client.importInvite(
        baseUrl = serverBaseUrl,
        command = AddContactByInviteCodeCommand(
          ownerUserId = currentUserId,
          inviteCode = normalized,
          encryptedAlias = LocalOpaqueCodec.encode("Imported $normalized"),
        ),
      )
      val mine = contacts.firstOrNull { it.ownerUserId == currentUserId }
        ?: error("Invite import did not return the caller contact.")
      val conversation = client.createConversation(
        baseUrl = serverBaseUrl,
        command = CreateConversationCommand(
          createdByUserId = currentUserId,
          participantUserIds = listOf(mine.peerUserId),
          protocolType = ProtocolType.SIGNAL_1TO1,
          encryptedTitle = LocalOpaqueCodec.encode("Contact ${mine.peerUserId.takeLast(4)}"),
        ),
      )
      refreshConversations()
      conversation.conversationId
    }
  }

  fun saveServerBaseUrl(value: String) {
    serverBaseUrl = value.trim().ifEmpty { DefaultServerUrl }
    prefs.edit().putString(KeyServerUrl, serverBaseUrl).apply()
  }

  // Group management

  suspend fun createGroup(title: String, memberUserIds: List<String>): String? {
    val currentUserId = userId ?: return null
    return performBusyTask {
      val conversation = client.createConversation(
        baseUrl = serverBaseUrl,
        command = CreateConversationCommand(
          createdByUserId = currentUserId,
          participantUserIds = memberUserIds,
          protocolType = ProtocolType.MLS_GROUP,
          encryptedTitle = LocalOpaqueCodec.encode(title.trim().ifEmpty { "Group Chat" }),
        ),
      )
      refreshConversations()
      conversation.conversationId
    }
  }

  suspend fun loadGroupMembers(conversationId: String) {
    performSilentTask {
      val page = client.listConversationMembers(serverBaseUrl, conversationId)
      groupMembers = groupMembers + (conversationId to page.members)
    }
  }

  suspend fun addGroupMembers(conversationId: String, userIds: List<String>): Boolean {
    val currentUserId = userId ?: return false
    return performBusyTask {
      client.addConversationMembers(
        baseUrl = serverBaseUrl,
        conversationId = conversationId,
        command = AddGroupMembersCommand(
          conversationId = conversationId,
          requestingUserId = currentUserId,
          userIds = userIds,
        ),
      )
      loadGroupMembers(conversationId)
      refreshConversations()
      true
    } ?: false
  }

  suspend fun removeGroupMembers(conversationId: String, userIds: List<String>): Boolean {
    val currentUserId = userId ?: return false
    return performBusyTask {
      client.removeConversationMembers(
        baseUrl = serverBaseUrl,
        conversationId = conversationId,
        command = RemoveGroupMembersCommand(
          conversationId = conversationId,
          requestingUserId = currentUserId,
          userIds = userIds,
        ),
      )
      loadGroupMembers(conversationId)
      refreshConversations()
      true
    } ?: false
  }

  suspend fun updateMemberRole(conversationId: String, targetUserId: String, newRole: GroupRole): Boolean {
    val currentUserId = userId ?: return false
    return performBusyTask {
      client.updateMemberRole(
        baseUrl = serverBaseUrl,
        conversationId = conversationId,
        userId = targetUserId,
        command = UpdateMemberRoleCommand(
          conversationId = conversationId,
          requestingUserId = currentUserId,
          targetUserId = targetUserId,
          newRole = newRole,
        ),
      )
      loadGroupMembers(conversationId)
      true
    } ?: false
  }

  suspend fun leaveGroup(conversationId: String): Boolean {
    val currentUserId = userId ?: return false
    return performBusyTask {
      client.leaveConversation(
        baseUrl = serverBaseUrl,
        conversationId = conversationId,
        command = LeaveConversationCommand(
          conversationId = conversationId,
          userId = currentUserId,
        ),
      )
      groupMembers = groupMembers - conversationId
      refreshConversations()
      true
    } ?: false
  }

  suspend fun updateGroupTitle(conversationId: String, title: String): Boolean {
    val currentUserId = userId ?: return false
    return performBusyTask {
      client.updateConversationSettings(
        baseUrl = serverBaseUrl,
        conversationId = conversationId,
        command = UpdateConversationSettingsCommand(
          conversationId = conversationId,
          requestingUserId = currentUserId,
          encryptedTitle = LocalOpaqueCodec.encode(title),
        ),
      )
      refreshConversations()
      true
    } ?: false
  }

  fun currentGroupMembers(conversationId: String): List<GroupMember> =
    groupMembers[conversationId].orEmpty()

  fun isGroup(conversationId: String): Boolean =
    chats.find { it.id == conversationId }?.protocolType == ProtocolType.MLS_GROUP

  fun myRoleIn(conversationId: String): GroupRole? {
    val currentUserId = userId ?: return null
    return groupMembers[conversationId]?.find { it.userId == currentUserId }?.role
  }

  fun chatTitle(conversationId: String): String =
    chats.firstOrNull { it.id == conversationId }?.title ?: "Conversation"

  fun currentMessages(conversationId: String): List<DeeplineMessage> =
    messages[conversationId].orEmpty()

  fun primaryConversationId(): String? =
    chats.firstOrNull()?.id

  fun clearError() {
    errorMessage = null
  }

  fun consumePendingConversationId(): String? =
    pendingConversationId.also {
      pendingConversationId = null
    }

  // Phone authentication

  suspend fun sendPhoneOtp(phoneNumber: String, countryCode: String): Boolean {
    return performBusyTask {
      ensureReachableServerBaseUrl()
      val response = client.sendPhoneOtp(serverBaseUrl, phoneNumber, countryCode)
      phoneVerificationId = response.verificationId
      phoneAuthMessage = response.message
      true
    } ?: false
  }

  suspend fun verifyPhoneOtp(otpCode: String): String? {
    val verificationId = phoneVerificationId ?: return null
    return performBusyTask {
      ensureReachableServerBaseUrl()
      val response = client.verifyPhoneOtp(serverBaseUrl, verificationId, otpCode)

      if (!response.success) {
        phoneAuthMessage = response.errorMessage ?: "Verification failed"
        return@performBusyTask null
      }

      val verifiedUserId = response.userId ?: error("No userId returned from verification")

      // Setup identity if new user
      if (response.isNewUser) {
        val generatedDeviceId = randomToken("android")
        val bundle = makeLocalDevBundle(userId = verifiedUserId, deviceId = generatedDeviceId)
        client.registerDevice(
          baseUrl = serverBaseUrl,
          command = RegisterDeviceCommand(
            userId = verifiedUserId,
            deviceBundle = bundle,
          ),
        )
        client.publishPreKeys(
          baseUrl = serverBaseUrl,
          command = PublishPreKeyBundleCommand(
            userId = verifiedUserId,
            deviceBundle = bundle,
          ),
        )

        displayName = ""
        deviceId = generatedDeviceId
      } else {
        // Existing user - get a new device ID for this device
        val existingDeviceId = prefs.getString(KeyDeviceId, null)
        if (existingDeviceId == null || userId != verifiedUserId) {
          val generatedDeviceId = randomToken("android")
          val bundle = makeLocalDevBundle(userId = verifiedUserId, deviceId = generatedDeviceId)
          client.registerDevice(
            baseUrl = serverBaseUrl,
            command = RegisterDeviceCommand(
              userId = verifiedUserId,
              deviceBundle = bundle,
            ),
          )
          client.publishPreKeys(
            baseUrl = serverBaseUrl,
            command = PublishPreKeyBundleCommand(
              userId = verifiedUserId,
              deviceBundle = bundle,
            ),
          )
          deviceId = generatedDeviceId
        }
      }

      userId = verifiedUserId
      identityReady = true
      phoneVerificationId = null
      phoneAuthMessage = null
      persistIdentity()

      // Ensure starter conversation exists
      ensureStarterConversation(verifiedUserId, deviceId!!)
    }
  }

  fun clearPhoneAuth() {
    phoneVerificationId = null
    phoneAuthMessage = null
  }

  // Push token registration

  suspend fun registerFcmToken(token: String): Boolean {
    val currentUserId = userId ?: return false
    val currentDeviceId = deviceId ?: return false

    return performSilentTask {
      client.registerPushToken(
        baseUrl = serverBaseUrl,
        userId = currentUserId,
        deviceId = currentDeviceId,
        platform = "fcm",
        token = token,
      )
    }.let { true }
  }

  suspend fun unregisterPushToken(): Boolean {
    val currentDeviceId = deviceId ?: return false

    return performSilentTask {
      client.deletePushToken(
        baseUrl = serverBaseUrl,
        deviceId = currentDeviceId,
      )
    }.let { true }
  }

  private suspend fun ensureStarterConversation(userId: String, deviceId: String): String {
    val currentConversations = client.listConversations(serverBaseUrl, userId)
    val conversation = currentConversations.firstOrNull()
      ?: client.createConversation(
        baseUrl = serverBaseUrl,
        command = CreateConversationCommand(
          createdByUserId = userId,
          participantUserIds = listOf(userId),
          protocolType = ProtocolType.SIGNAL_1TO1,
          encryptedTitle = LocalOpaqueCodec.encode("Local Notes"),
        ),
      )
    seedStarterMessagesIfNeeded(conversation.conversationId, userId, deviceId)
    refreshConversations()
    return conversation.conversationId
  }

  private suspend fun seedStarterMessagesIfNeeded(
    conversationId: String,
    senderUserId: String,
    senderDeviceId: String,
  ) {
    val existing = client.listMessages(serverBaseUrl, conversationId)
    if (existing.isNotEmpty()) return
    StarterMessages.forEach { starter ->
      sendEnvelope(
        conversationId = conversationId,
        senderUserId = senderUserId,
        senderDeviceId = senderDeviceId,
        body = starter.body,
        kind = starter.kind,
      )
    }
  }

  private suspend fun sendEnvelope(
    conversationId: String,
    senderUserId: String,
    senderDeviceId: String,
    body: String,
    kind: String,
  ) {
    val envelope = EncryptedEnvelope(
      messageId = randomToken("msg"),
      conversationId = conversationId,
      senderUserId = senderUserId,
      senderDeviceId = senderDeviceId,
      ciphertext = LocalOpaqueCodec.encode(body),
      encryptionMetadata = mapOf("encoding" to "devb64", "kind" to kind),
      protocolVersion = "dev-local-v1",
      attachments = emptyList(),
      createdAtEpochMs = System.currentTimeMillis(),
    )
    client.sendMessage(
      baseUrl = serverBaseUrl,
      command = SendEncryptedMessageCommand(
        conversationId = conversationId,
        envelope = envelope,
      ),
    )
  }

  private fun parseMessages(
    currentUserId: String,
    envelopes: List<EncryptedEnvelope>,
  ): List<DeeplineMessage> =
    envelopes.map { envelope ->
      val kind = envelope.encryptionMetadata["kind"].orEmpty()
      val isSystem = kind.startsWith("starter_")
      DeeplineMessage(
        id = envelope.messageId,
        conversationId = envelope.conversationId,
        author = when {
          isSystem -> "Deepline"
          envelope.senderUserId == currentUserId -> displayName.ifEmpty { "Me" }
          else -> "Peer ${envelope.senderUserId.takeLast(4)}"
        },
        body = LocalOpaqueCodec.decode(envelope.ciphertext) ?: "Encrypted payload",
        isMine = envelope.senderUserId == currentUserId && !isSystem,
        isSystem = isSystem,
      )
    }

  private fun updateConversationPreview(conversationId: String, preview: String?) {
    if (preview == null) return
    chats = chats.map { chat ->
      if (chat.id == conversationId) chat.copy(preview = preview) else chat
    }
  }

  private fun defaultPreview(conversation: ConversationDescriptor): String =
    when (LocalOpaqueCodec.decode(conversation.encryptedTitle)) {
      "Local Notes" -> "Your encrypted scratchpad is ready."
      else -> "Encrypted conversation ready."
    }

  private fun persistIdentity() {
    prefs.edit()
      .putString(KeyDisplayName, displayName)
      .putString(KeyDeviceName, deviceName)
      .putString(KeyServerUrl, serverBaseUrl)
      .putString(KeyUserId, userId)
      .putString(KeyDeviceId, deviceId)
      .apply()
  }

  private suspend fun ensureReachableServerBaseUrl(): String {
    var lastError: Throwable? = null
    for (candidate in serverBaseUrlCandidates()) {
      try {
        client.health(candidate)
        if (serverBaseUrl != candidate) {
          serverBaseUrl = candidate
          prefs.edit().putString(KeyServerUrl, serverBaseUrl).apply()
        }
        return candidate
      } catch (error: Throwable) {
        lastError = error
      }
    }
    throw lastError ?: IllegalStateException("Could not reach the local Deepline server.")
  }

  private fun serverBaseUrlCandidates(): List<String> {
    val storedUrl = prefs.getString(KeyServerUrl, null)
    return listOf(
      serverBaseUrl,
      storedUrl,
      "http://10.0.2.2:9091",
      "http://127.0.0.1:9091",
      "http://127.0.0.1:8080",
      "http://10.0.2.2:8080",
      "http://127.0.0.1:9090",
      "http://10.0.2.2:9090",
    ).filterNotNull()
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .distinct()
  }

  private suspend fun performSilentTask(operation: suspend () -> Unit) {
    try {
      operation()
    } catch (error: Throwable) {
      errorMessage = readableError(error)
    }
  }

  private suspend fun <T> performBusyTask(operation: suspend () -> T): T? {
    isBusy = true
    errorMessage = null
    return try {
      operation()
    } catch (error: Throwable) {
      errorMessage = readableError(error)
      null
    } finally {
      isBusy = false
    }
  }

  private fun readableError(error: Throwable): String {
    val raw = error.message ?: error.toString()
    return when {
      raw.contains("HTTP 302", ignoreCase = true) ->
        "Server URL redirected unexpectedly. Check the local server address."
      raw.contains("timeout", ignoreCase = true) ->
        "Connection timed out. Check the local server address."
      raw.contains("Unable to resolve host", ignoreCase = true) ->
        "Could not reach the local server."
      else -> raw
        .lineSequence()
        .firstOrNull()
        ?.substringBefore(" [url=")
        ?.trim()
        .orEmpty()
        .ifBlank { "Something went wrong while syncing Deepline." }
    }
  }

  private fun randomToken(prefix: String): String =
    "${prefix}_${UUID.randomUUID().toString().replace("-", "").lowercase()}"

  private data class StarterMessageTemplate(
    val kind: String,
    val body: String,
  )

  private companion object {
    const val PrefFile = "deepline-android"
    const val DefaultServerUrl = "http://10.0.2.2:9091"
    const val KeyDisplayName = "deepline.displayName"
    const val KeyDeviceName = "deepline.deviceName"
    const val KeyServerUrl = "deepline.serverBaseUrl"
    const val KeyUserId = "deepline.userId"
    const val KeyDeviceId = "deepline.deviceId"

    val StarterMessages = listOf(
      StarterMessageTemplate(
        kind = "starter_intro",
        body = "Local Notes is your private scratchpad. Only encrypted payloads leave the device.",
      ),
      StarterMessageTemplate(
        kind = "starter_tip",
        body = "Send yourself a note below to verify end-to-end encrypted message sync.",
      ),
    )
  }
}
