import Foundation

enum DeeplineRoute: Hashable {
    case identitySetup
    case addFriend
    case settings
    case devices
    case chat(String)
    case createGroup
    case groupInfo(String)
}

enum DeeplineProtocol: String, Codable {
    case signal1to1 = "SIGNAL_1TO1"
    case mlsGroup = "MLS_GROUP"

    var displayLabel: String {
        switch self {
        case .signal1to1:
            return "signal 1to1"
        case .mlsGroup:
            return "mls group"
        }
    }
}

struct DeeplineChat: Identifiable, Hashable {
    let id: String
    let title: String
    let protocolType: DeeplineProtocol
    let subtitle: String
    let preview: String
}

struct DeeplineMessage: Identifiable, Hashable {
    let id: String
    let conversationId: String
    let author: String
    let body: String
    let isMine: Bool
    let isSystem: Bool
}

@MainActor
final class DeeplineAppModel: ObservableObject {
    @Published var displayName: String
    @Published var deviceName: String
    @Published var serverBaseURL: String
    @Published var identityReady: Bool
    @Published var userId: String?
    @Published var deviceId: String?
    @Published var chats: [DeeplineChat] = []
    @Published var messages: [String: [DeeplineMessage]] = [:]
    @Published var groupMembers: [String: [GroupMember]] = [:]
    @Published var latestInviteCode = ""
    @Published var errorMessage: String?
    @Published var isBusy = false
    @Published var pendingConversationId: String?
    @Published var isWebSocketConnected = false

    private let defaults = UserDefaults.standard
    private let client = DeeplineServerClient()
    private var webSocketTask: Task<Void, Never>?
    private var activeWebSocketConversationId: String?

    init() {
        if ProcessInfo.processInfo.arguments.contains("-resetDeeplineState") {
            defaults.removeObject(forKey: Keys.displayName)
            defaults.removeObject(forKey: Keys.deviceName)
            defaults.removeObject(forKey: Keys.serverBaseURL)
            defaults.removeObject(forKey: Keys.userId)
            defaults.removeObject(forKey: Keys.deviceId)
        }
        let env = ProcessInfo.processInfo.environment
        if let overrideUserId = env["DEEPLINE_USER_ID"]?.trimmingCharacters(in: .whitespacesAndNewlines), !overrideUserId.isEmpty,
           let overrideDeviceId = env["DEEPLINE_DEVICE_ID"]?.trimmingCharacters(in: .whitespacesAndNewlines), !overrideDeviceId.isEmpty {
            defaults.set(overrideUserId, forKey: Keys.userId)
            defaults.set(overrideDeviceId, forKey: Keys.deviceId)
            if let overrideDisplayName = env["DEEPLINE_DISPLAY_NAME"]?.trimmingCharacters(in: .whitespacesAndNewlines), !overrideDisplayName.isEmpty {
                defaults.set(overrideDisplayName, forKey: Keys.displayName)
            }
            if let overrideDeviceName = env["DEEPLINE_DEVICE_NAME"]?.trimmingCharacters(in: .whitespacesAndNewlines), !overrideDeviceName.isEmpty {
                defaults.set(overrideDeviceName, forKey: Keys.deviceName)
            }
        }
        let storedUserId = defaults.string(forKey: Keys.userId)
        let storedDeviceId = defaults.string(forKey: Keys.deviceId)
        let launchServerURL = ProcessInfo.processInfo.environment["DEEPLINE_SERVER_URL"]
        displayName = defaults.string(forKey: Keys.displayName) ?? ""
        deviceName = defaults.string(forKey: Keys.deviceName) ?? "iPhone Secure"
        serverBaseURL = launchServerURL ?? defaults.string(forKey: Keys.serverBaseURL) ?? "http://localhost:9091"
        userId = storedUserId
        deviceId = storedDeviceId
        identityReady = storedUserId != nil && storedDeviceId != nil
    }

    func bootstrapIfNeeded() async {
        guard identityReady else { return }
        do {
            try await ensureReachableServerBaseURL()
        } catch {
            errorMessage = readableError(error)
            return
        }
        await refreshConversations()
    }

    func completeIdentity(name: String, device: String) async -> String? {
        await performBusyTask { [self] in
            try await self.ensureReachableServerBaseURL()
            let resolvedName = name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Deepline User" : name.trimmingCharacters(in: .whitespacesAndNewlines)
            let resolvedDevice = device.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Secure Device" : device.trimmingCharacters(in: .whitespacesAndNewlines)
            let fingerprint = "fp_\(UUID().uuidString.replacingOccurrences(of: "-", with: "").lowercased())"
            let user = try await self.client.registerUser(
                baseURL: self.serverBaseURL,
                command: RegisterUserCommand(
                    identityFingerprint: fingerprint,
                    profileCiphertext: LocalOpaqueCodec.encode(resolvedName)
                )
            )
            let generatedDeviceId = "ios_\(UUID().uuidString.replacingOccurrences(of: "-", with: "").lowercased())"
            let bundle = DeviceBundle.makeLocalDevBundle(userId: user.userId, deviceId: generatedDeviceId)
            _ = try await self.client.registerDevice(
                baseURL: self.serverBaseURL,
                command: RegisterDeviceCommand(userId: user.userId, deviceBundle: bundle)
            )
            _ = try await self.client.publishPreKeys(
                baseURL: self.serverBaseURL,
                command: PublishPreKeyBundleCommand(userId: user.userId, deviceBundle: bundle)
            )

            self.displayName = resolvedName
            self.deviceName = resolvedDevice
            self.userId = user.userId
            self.deviceId = generatedDeviceId
            self.identityReady = true
            self.persistIdentity()

            let conversationId = try await self.ensureStarterConversation(userId: user.userId, deviceId: generatedDeviceId)
            self.pendingConversationId = conversationId
            return conversationId
        }
    }

    func refreshConversations() async {
        guard let userId else { return }
        await performSilentTask { [self] in
            try await self.ensureReachableServerBaseURL()
            let conversations = try await self.client.listConversations(baseURL: self.serverBaseURL, userId: userId)
            var hydratedMessages: [String: [DeeplineMessage]] = [:]
            for conversation in conversations {
                let envelopes = try await self.client.listMessages(baseURL: self.serverBaseURL, conversationId: conversation.conversationId)
                hydratedMessages[conversation.conversationId] = self.parseMessages(currentUserId: userId, envelopes: envelopes)
            }
            self.messages.merge(hydratedMessages) { _, new in new }
            self.chats = conversations.map { conversation in
                let title = LocalOpaqueCodec.decode(conversation.encryptedTitle) ?? "Conversation \(conversation.conversationId.suffix(4))"
                let memberCount = conversation.participantUserIds.count
                let preview = hydratedMessages[conversation.conversationId]?.last?.body ?? self.defaultPreview(conversation)
                return DeeplineChat(
                    id: conversation.conversationId,
                    title: title,
                    protocolType: conversation.protocolType,
                    subtitle: "\(conversation.protocolType.displayLabel) • \(memberCount) member\(memberCount == 1 ? "" : "s")",
                    preview: preview
                )
            }
        }
    }

    func loadMessages(conversationId: String) async {
        guard let userId else { return }
        await performSilentTask { [self] in
            try await self.ensureReachableServerBaseURL()
            let envelopes = try await self.client.listMessages(baseURL: self.serverBaseURL, conversationId: conversationId)
            let parsed = self.parseMessages(currentUserId: userId, envelopes: envelopes)
            self.messages[conversationId] = parsed
            self.updateConversationPreview(conversationId: conversationId, preview: parsed.last?.body)
        }
    }

    // WebSocket real-time messaging

    func connectToConversation(_ conversationId: String) {
        guard let userId else { return }

        // Disconnect from any existing connection
        if let active = activeWebSocketConversationId, active != conversationId {
            disconnectFromConversation()
        }

        if activeWebSocketConversationId == conversationId {
            return // Already connected
        }

        activeWebSocketConversationId = conversationId

        webSocketTask = Task { [weak self] in
            guard let self else { return }

            var retryCount = 0
            let maxRetries = 3

            while !Task.isCancelled && retryCount < maxRetries {
                for await envelope in self.client.connectToConversation(baseURL: self.serverBaseURL, conversationId: conversationId) {
                    await MainActor.run {
                        self.isWebSocketConnected = true
                        self.handleIncomingEnvelope(currentUserId: userId, envelope: envelope)
                    }
                }

                // Stream ended, try to reconnect
                await MainActor.run {
                    self.isWebSocketConnected = false
                }

                retryCount += 1
                if retryCount < maxRetries {
                    try? await Task.sleep(nanoseconds: UInt64(pow(2.0, Double(retryCount))) * 1_000_000_000)
                }
            }
        }
    }

    func disconnectFromConversation() {
        if let conversationId = activeWebSocketConversationId {
            client.disconnectFromConversation(conversationId: conversationId)
        }
        webSocketTask?.cancel()
        webSocketTask = nil
        activeWebSocketConversationId = nil
        isWebSocketConnected = false
    }

    private func handleIncomingEnvelope(currentUserId: String, envelope: EncryptedEnvelope) {
        let conversationId = envelope.conversationId
        let existingMessages = messages[conversationId] ?? []

        // Check if message already exists (dedup)
        if existingMessages.contains(where: { $0.id == envelope.messageId }) {
            return
        }

        let newMessage = parseMessage(currentUserId: currentUserId, envelope: envelope)
        messages[conversationId] = existingMessages + [newMessage]
        updateConversationPreview(conversationId: conversationId, preview: newMessage.body)
    }

    private func parseMessage(currentUserId: String, envelope: EncryptedEnvelope) -> DeeplineMessage {
        let kind = envelope.encryptionMetadata["kind"] ?? ""
        let isSystem = kind.hasPrefix("starter_")
        return DeeplineMessage(
            id: envelope.messageId,
            conversationId: envelope.conversationId,
            author: isSystem ? "Deepline" : (envelope.senderUserId == currentUserId ? (displayName.isEmpty ? "Me" : displayName) : "Peer \(envelope.senderUserId.suffix(4))"),
            body: LocalOpaqueCodec.decode(envelope.ciphertext) ?? "Encrypted payload",
            isMine: envelope.senderUserId == currentUserId && !isSystem,
            isSystem: isSystem
        )
    }

    func sendMessage(conversationId: String, text: String) async {
        guard let userId, let deviceId else { return }
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }

        _ = await performBusyTask { [self] in
            try await self.sendEnvelope(
                conversationId: conversationId,
                senderUserId: userId,
                senderDeviceId: deviceId,
                body: trimmed,
                kind: "user_note"
            )
            await self.loadMessages(conversationId: conversationId)
            await self.refreshConversations()
            return true
        }
    }

    func createInvite() async {
        guard let userId else { return }
        _ = await performBusyTask { [self] in
            let invite = try await self.client.createInvite(
                baseURL: self.serverBaseURL,
                command: CreateInviteCodeCommand(
                    ownerUserId: userId,
                    encryptedInvitePayload: LocalOpaqueCodec.encode(self.displayName.isEmpty ? "Deepline User" : self.displayName),
                    expiresAtEpochMs: nil
                )
            )
            self.latestInviteCode = invite.inviteCode
            return true
        }
    }

    func importInvite(code: String) async -> String? {
        guard let userId else { return nil }
        let normalized = code.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalized.isEmpty else { return nil }

        return await performBusyTask { [self] in
            let contacts = try await self.client.importInvite(
                baseURL: self.serverBaseURL,
                command: AddContactByInviteCodeCommand(
                    ownerUserId: userId,
                    inviteCode: normalized,
                    encryptedAlias: LocalOpaqueCodec.encode("Imported \(normalized)")
                )
            )
            guard let mine = contacts.first(where: { $0.ownerUserId == userId }) else {
                throw DeeplineClientError.invalidResponse("Invite import did not return the caller contact.")
            }
            let conversation = try await self.client.createConversation(
                baseURL: self.serverBaseURL,
                command: CreateConversationCommand(
                    createdByUserId: userId,
                    participantUserIds: [mine.peerUserId],
                    protocolType: .signal1to1,
                    encryptedTitle: LocalOpaqueCodec.encode("Contact \(mine.peerUserId.suffix(4))")
                )
            )
            await self.refreshConversations()
            return conversation.conversationId
        }
    }

    func saveServerBaseURL(_ value: String) {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        serverBaseURL = trimmed.isEmpty ? "http://localhost:9091" : trimmed
        defaults.set(serverBaseURL, forKey: Keys.serverBaseURL)
    }

    // Group management

    func createGroup(title: String, memberUserIds: [String]) async -> String? {
        guard let userId else { return nil }
        return await performBusyTask { [self] in
            let conversation = try await self.client.createConversation(
                baseURL: self.serverBaseURL,
                command: CreateConversationCommand(
                    createdByUserId: userId,
                    participantUserIds: memberUserIds,
                    protocolType: .mlsGroup,
                    encryptedTitle: LocalOpaqueCodec.encode(title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Group Chat" : title.trimmingCharacters(in: .whitespacesAndNewlines))
                )
            )
            await self.refreshConversations()
            return conversation.conversationId
        }
    }

    func loadGroupMembers(conversationId: String) async {
        await performSilentTask { [self] in
            let page = try await self.client.listConversationMembers(baseURL: self.serverBaseURL, conversationId: conversationId)
            self.groupMembers[conversationId] = page.members
        }
    }

    func addGroupMembers(conversationId: String, userIds: [String]) async -> Bool {
        guard let userId else { return false }
        return await performBusyTask { [self] in
            _ = try await self.client.addConversationMembers(
                baseURL: self.serverBaseURL,
                conversationId: conversationId,
                command: AddGroupMembersCommand(
                    conversationId: conversationId,
                    requestingUserId: userId,
                    userIds: userIds
                )
            )
            await self.loadGroupMembers(conversationId: conversationId)
            await self.refreshConversations()
            return true
        } ?? false
    }

    func removeGroupMembers(conversationId: String, userIds: [String]) async -> Bool {
        guard let userId else { return false }
        return await performBusyTask { [self] in
            _ = try await self.client.removeConversationMembers(
                baseURL: self.serverBaseURL,
                conversationId: conversationId,
                command: RemoveGroupMembersCommand(
                    conversationId: conversationId,
                    requestingUserId: userId,
                    userIds: userIds
                )
            )
            await self.loadGroupMembers(conversationId: conversationId)
            await self.refreshConversations()
            return true
        } ?? false
    }

    func updateMemberRole(conversationId: String, targetUserId: String, newRole: GroupRole) async -> Bool {
        guard let userId else { return false }
        return await performBusyTask { [self] in
            _ = try await self.client.updateMemberRole(
                baseURL: self.serverBaseURL,
                conversationId: conversationId,
                userId: targetUserId,
                command: UpdateMemberRoleCommand(
                    conversationId: conversationId,
                    requestingUserId: userId,
                    targetUserId: targetUserId,
                    newRole: newRole
                )
            )
            await self.loadGroupMembers(conversationId: conversationId)
            return true
        } ?? false
    }

    func leaveGroup(conversationId: String) async -> Bool {
        guard let userId else { return false }
        return await performBusyTask { [self] in
            _ = try await self.client.leaveConversation(
                baseURL: self.serverBaseURL,
                conversationId: conversationId,
                command: LeaveConversationCommand(
                    conversationId: conversationId,
                    userId: userId
                )
            )
            self.groupMembers.removeValue(forKey: conversationId)
            await self.refreshConversations()
            return true
        } ?? false
    }

    func updateGroupTitle(conversationId: String, title: String) async -> Bool {
        guard let userId else { return false }
        return await performBusyTask { [self] in
            _ = try await self.client.updateConversationSettings(
                baseURL: self.serverBaseURL,
                conversationId: conversationId,
                command: UpdateConversationSettingsCommand(
                    conversationId: conversationId,
                    requestingUserId: userId,
                    encryptedTitle: LocalOpaqueCodec.encode(title),
                    maxMembers: nil
                )
            )
            await self.refreshConversations()
            return true
        } ?? false
    }

    func currentGroupMembers(for conversationId: String) -> [GroupMember] {
        groupMembers[conversationId] ?? []
    }

    func isGroup(_ conversationId: String) -> Bool {
        chats.first(where: { $0.id == conversationId })?.protocolType == .mlsGroup
    }

    func myRole(in conversationId: String) -> GroupRole? {
        guard let userId else { return nil }
        return groupMembers[conversationId]?.first(where: { $0.userId == userId })?.role
    }

    func consumePendingConversationId() -> String? {
        defer { pendingConversationId = nil }
        return pendingConversationId
    }

    func chatTitle(for conversationId: String) -> String {
        chats.first(where: { $0.id == conversationId })?.title ?? "Conversation"
    }

    func currentMessages(for conversationId: String) -> [DeeplineMessage] {
        messages[conversationId] ?? []
    }

    func primaryConversationId() -> String? {
        chats.first?.id
    }

    func clearError() {
        errorMessage = nil
    }

    // Push token registration

    func registerApnsToken(_ tokenData: Data) async {
        guard let userId, let deviceId else { return }

        let tokenString = tokenData.map { String(format: "%02.2hhx", $0) }.joined()

        await performSilentTask { [self] in
            try await self.client.registerPushToken(
                baseURL: self.serverBaseURL,
                userId: userId,
                deviceId: deviceId,
                platform: "apns",
                token: tokenString
            )
        }
    }

    func unregisterPushToken() async {
        guard let deviceId else { return }

        await performSilentTask { [self] in
            try await self.client.deletePushToken(baseURL: self.serverBaseURL, deviceId: deviceId)
        }
    }

    private func ensureStarterConversation(userId: String, deviceId: String) async throws -> String {
        let currentConversations = try await self.client.listConversations(baseURL: self.serverBaseURL, userId: userId)
        let conversation = if let first = currentConversations.first {
            first
        } else {
            try await self.client.createConversation(
                baseURL: self.serverBaseURL,
                command: CreateConversationCommand(
                    createdByUserId: userId,
                    participantUserIds: [userId],
                    protocolType: .signal1to1,
                    encryptedTitle: LocalOpaqueCodec.encode("Local Notes")
                )
            )
        }
        try await self.seedStarterMessagesIfNeeded(conversationId: conversation.conversationId, senderUserId: userId, senderDeviceId: deviceId)
        await self.refreshConversations()
        return conversation.conversationId
    }

    private func seedStarterMessagesIfNeeded(conversationId: String, senderUserId: String, senderDeviceId: String) async throws {
        let existing = try await self.client.listMessages(baseURL: self.serverBaseURL, conversationId: conversationId)
        guard existing.isEmpty else { return }
        for starter in StarterMessages.all {
            try await self.sendEnvelope(
                conversationId: conversationId,
                senderUserId: senderUserId,
                senderDeviceId: senderDeviceId,
                body: starter.body,
                kind: starter.kind
            )
        }
    }

    private func sendEnvelope(
        conversationId: String,
        senderUserId: String,
        senderDeviceId: String,
        body: String,
        kind: String
    ) async throws {
        let envelope = EncryptedEnvelope(
            messageId: "msg_\(UUID().uuidString.replacingOccurrences(of: "-", with: "").lowercased())",
            conversationId: conversationId,
            senderUserId: senderUserId,
            senderDeviceId: senderDeviceId,
            ciphertext: LocalOpaqueCodec.encode(body),
            encryptionMetadata: ["encoding": "devb64", "kind": kind],
            protocolVersion: "dev-local-v1",
            attachments: [],
            mentionedUserIds: nil,
            createdAtEpochMs: Int64(Date().timeIntervalSince1970 * 1000)
        )
        _ = try await self.client.sendMessage(
            baseURL: self.serverBaseURL,
            command: SendEncryptedMessageCommand(conversationId: conversationId, envelope: envelope)
        )
    }

    private func parseMessages(currentUserId: String, envelopes: [EncryptedEnvelope]) -> [DeeplineMessage] {
        envelopes.map { envelope in
            let kind = envelope.encryptionMetadata["kind"] ?? ""
            let isSystem = kind.hasPrefix("starter_")
            return DeeplineMessage(
                id: envelope.messageId,
                conversationId: envelope.conversationId,
                author: isSystem ? "Deepline" : (envelope.senderUserId == currentUserId ? (self.displayName.isEmpty ? "Me" : self.displayName) : "Peer \(envelope.senderUserId.suffix(4))"),
                body: LocalOpaqueCodec.decode(envelope.ciphertext) ?? "Encrypted payload",
                isMine: envelope.senderUserId == currentUserId && !isSystem,
                isSystem: isSystem
            )
        }
    }

    private func updateConversationPreview(conversationId: String, preview: String?) {
        guard let preview else { return }
        chats = chats.map { chat in
            if chat.id == conversationId {
                return DeeplineChat(
                    id: chat.id,
                    title: chat.title,
                    protocolType: chat.protocolType,
                    subtitle: chat.subtitle,
                    preview: preview
                )
            }
            return chat
        }
    }

    private func defaultPreview(_ conversation: ConversationDescriptor) -> String {
        if LocalOpaqueCodec.decode(conversation.encryptedTitle) == "Local Notes" {
            return "Your encrypted scratchpad is ready."
        }
        return "Encrypted conversation ready."
    }

    private func persistIdentity() {
        defaults.set(displayName, forKey: Keys.displayName)
        defaults.set(deviceName, forKey: Keys.deviceName)
        defaults.set(serverBaseURL, forKey: Keys.serverBaseURL)
        defaults.set(userId, forKey: Keys.userId)
        defaults.set(deviceId, forKey: Keys.deviceId)
    }

    private func ensureReachableServerBaseURL() async throws {
        var lastError: Error?
        for candidate in serverBaseURLCandidates() {
            do {
                _ = try await client.health(baseURL: candidate)
                if serverBaseURL != candidate {
                    serverBaseURL = candidate
                    defaults.set(serverBaseURL, forKey: Keys.serverBaseURL)
                }
                return
            } catch {
                lastError = error
            }
        }
        throw lastError ?? DeeplineClientError.invalidResponse("Could not reach the local Deepline server.")
    }

    private func serverBaseURLCandidates() -> [String] {
        var values: [String] = []
        let defaultsURL = defaults.string(forKey: Keys.serverBaseURL)
        let launchServerURL = ProcessInfo.processInfo.environment["DEEPLINE_SERVER_URL"]
        [launchServerURL, serverBaseURL, defaultsURL, "http://localhost:9091", "http://127.0.0.1:9091", "http://localhost:8080", "http://127.0.0.1:8080", "http://localhost:9090", "http://127.0.0.1:9090"]
            .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
            .forEach { candidate in
                if !values.contains(candidate) {
                    values.append(candidate)
                }
            }
        return values
    }

    private func performSilentTask(_ operation: @escaping () async throws -> Void) async {
        do {
            try await operation()
        } catch {
            errorMessage = readableError(error)
        }
    }

    private func performBusyTask<T>(_ operation: @escaping () async throws -> T) async -> T? {
        isBusy = true
        errorMessage = nil
        defer { isBusy = false }
        do {
            return try await operation()
        } catch {
            errorMessage = readableError(error)
            return nil
        }
    }

    private func readableError(_ error: Error) -> String {
        let raw: String
        if let clientError = error as? DeeplineClientError {
            raw = clientError.localizedDescription
        } else {
            raw = error.localizedDescription
        }
        if raw.localizedCaseInsensitiveContains("HTTP 302") {
            return "Server URL redirected unexpectedly. Check the local server address."
        }
        if raw.localizedCaseInsensitiveContains("timeout") {
            return "Connection timed out. Check the local server address."
        }
        if raw.localizedCaseInsensitiveContains("resolve host") {
            return "Could not reach the local server."
        }
        return raw.components(separatedBy: "\n").first?.components(separatedBy: " [url=").first?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false
            ? raw.components(separatedBy: "\n").first!.components(separatedBy: " [url=").first!.trimmingCharacters(in: .whitespacesAndNewlines)
            : "Something went wrong while syncing Deepline."
    }

    private enum Keys {
        static let displayName = "deepline.displayName"
        static let deviceName = "deepline.deviceName"
        static let serverBaseURL = "deepline.serverBaseURL"
        static let userId = "deepline.userId"
        static let deviceId = "deepline.deviceId"
    }

    private enum StarterMessages {
        static let all: [(kind: String, body: String)] = [
            ("starter_intro", "Local Notes is your private scratchpad. Only encrypted payloads leave the device."),
            ("starter_tip", "Send yourself a note below to verify end-to-end encrypted message sync."),
        ]
    }
}
