import Foundation

enum DeeplineClientError: LocalizedError {
    case invalidURL(String)
    case invalidResponse(String)
    case httpFailure(Int, String)

    var errorDescription: String? {
        switch self {
        case .invalidURL(let value):
            return "Invalid server URL: \(value)"
        case .invalidResponse(let message):
            return message
        case .httpFailure(let code, let body):
            return body.isEmpty ? "HTTP \(code)" : "HTTP \(code): \(body)"
        }
    }
}

enum LocalOpaqueCodec {
    static func encode(_ plaintext: String) -> String {
        "devb64:" + Data(plaintext.utf8).base64EncodedString()
    }

    static func decode(_ ciphertext: String?) -> String? {
        guard let ciphertext, ciphertext.hasPrefix("devb64:") else { return nil }
        let base64 = String(ciphertext.dropFirst("devb64:".count))
        guard let data = Data(base64Encoded: base64) else { return nil }
        return String(data: data, encoding: .utf8)
    }
}

struct PublishedOneTimePreKey: Codable {
    let keyId: String
    let publicKey: String
}

struct DeviceBundle: Codable {
    let userId: String
    let deviceId: String
    let identityKey: String
    let signedPreKey: String
    let signedPreKeySignature: String
    let signingPublicKey: String
    let oneTimePreKeys: [PublishedOneTimePreKey]
    let protocolVersion: String

    static func makeLocalDevBundle(userId: String, deviceId: String) -> DeviceBundle {
        func token(_ prefix: String) -> String {
            "\(prefix)_\(UUID().uuidString.replacingOccurrences(of: "-", with: "").lowercased())"
        }

        return DeviceBundle(
            userId: userId,
            deviceId: deviceId,
            identityKey: token("identity"),
            signedPreKey: token("signed_prekey"),
            signedPreKeySignature: token("signed_signature"),
            signingPublicKey: token("signing_public"),
            oneTimePreKeys: (1...5).map { index in
                PublishedOneTimePreKey(keyId: "otk_\(index)_\(deviceId)", publicKey: token("otk"))
            },
            protocolVersion: "dev-local-v1"
        )
    }
}

struct RegisterUserCommand: Codable {
    let identityFingerprint: String
    let profileCiphertext: String?
}

struct UserRecord: Codable {
    let userId: String
    let identityFingerprint: String
    let profileCiphertext: String?
    let createdAtEpochMs: Int64
}

struct RegisterDeviceCommand: Codable {
    let userId: String
    let deviceBundle: DeviceBundle
}

struct PublishPreKeyBundleCommand: Codable {
    let userId: String
    let deviceBundle: DeviceBundle
}

struct RegisteredDeviceRecord: Codable {
    let userId: String
    let deviceBundle: DeviceBundle
    let createdAtEpochMs: Int64
    let lastSeenAtEpochMs: Int64
}

struct PreKeyBundleRecord: Codable {
    let userId: String
    let deviceId: String
    let deviceBundle: DeviceBundle
    let publishedAtEpochMs: Int64
}

struct CreateConversationCommand: Codable {
    let createdByUserId: String
    let participantUserIds: [String]
    let protocolType: DeeplineProtocol
    let encryptedTitle: String?
}

struct ConversationDescriptor: Codable {
    let conversationId: String
    let protocolType: DeeplineProtocol
    let encryptedTitle: String?
    let participantUserIds: [String]
    let createdAtEpochMs: Int64
    let updatedAtEpochMs: Int64
}

struct EncryptedAttachmentReference: Codable {
    let attachmentId: String
    let ciphertextDigest: String
    let ciphertextByteLength: Int64
    let metadataCiphertext: String
    let protocolVersion: String
}

struct EncryptedEnvelope: Codable {
    let messageId: String
    let conversationId: String
    let senderUserId: String
    let senderDeviceId: String
    let ciphertext: String
    let encryptionMetadata: [String: String]
    let protocolVersion: String
    let attachments: [EncryptedAttachmentReference]?
    let mentionedUserIds: [String]?
    let createdAtEpochMs: Int64
}

struct SendEncryptedMessageCommand: Codable {
    let conversationId: String
    let envelope: EncryptedEnvelope
}

struct CreateInviteCodeCommand: Codable {
    let ownerUserId: String
    let encryptedInvitePayload: String
    let expiresAtEpochMs: Int64?
}

struct InviteCodeRecord: Codable {
    let inviteCode: String
    let ownerUserId: String
    let encryptedInvitePayload: String
    let expiresAtEpochMs: Int64?
    let createdAtEpochMs: Int64
    let consumedByUserId: String?
}

struct AddContactByInviteCodeCommand: Codable {
    let ownerUserId: String
    let inviteCode: String
    let encryptedAlias: String?
}

struct ContactRecord: Codable {
    let ownerUserId: String
    let peerUserId: String
    let inviteCode: String
    let encryptedAlias: String?
    let createdAtEpochMs: Int64
}

// Group member management models

enum GroupRole: String, Codable {
    case OWNER
    case ADMIN
    case MEMBER
}

struct GroupMember: Codable, Identifiable {
    let userId: String
    let role: GroupRole
    let addedByUserId: String?
    let joinedAtEpochMs: Int64

    var id: String { userId }
}

struct GroupMemberPage: Codable {
    let conversationId: String
    let members: [GroupMember]
    let totalCount: Int
    let offset: Int
    let limit: Int
}

struct AddGroupMembersCommand: Codable {
    let conversationId: String
    let requestingUserId: String
    let userIds: [String]
}

struct RemoveGroupMembersCommand: Codable {
    let conversationId: String
    let requestingUserId: String
    let userIds: [String]
}

struct UpdateMemberRoleCommand: Codable {
    let conversationId: String
    let requestingUserId: String
    let targetUserId: String
    let newRole: GroupRole
}

struct LeaveConversationCommand: Codable {
    let conversationId: String
    let userId: String
}

struct UpdateConversationSettingsCommand: Codable {
    let conversationId: String
    let requestingUserId: String
    let encryptedTitle: String?
    let maxMembers: Int?
}

// Aggregated read receipts for groups
struct AggregatedReadReceipt: Codable {
    let messageId: String
    let conversationId: String
    let readCount: Int
    let totalMembers: Int
    let readByUserIds: [String]
}

// Mention notification
struct MentionNotification: Codable, Identifiable {
    let messageId: String
    let conversationId: String
    let senderUserId: String
    let mentionedUserId: String
    let createdAtEpochMs: Int64

    var id: String { "\(messageId)_\(mentionedUserId)" }
}

struct HealthResponse: Codable {
    let status: String
    let environment: String
    let strictCryptoEnforcement: Bool?
    let storeMode: String
    let rateLimiterMode: String
}

struct RegisterPushTokenRequest: Codable {
    let userId: String
    let deviceId: String
    let platform: String
    let token: String
}

struct EmptyResponse: Codable {}

struct PushTokenResponse: Codable {
    let userId: String
    let deviceId: String
    let platform: String
    let token: String
    let createdAtEpochMs: Int64
    let updatedAtEpochMs: Int64
}

final class DeeplineServerClient {
    private var activeWebSocketTasks: [String: URLSessionWebSocketTask] = [:]
    private let webSocketLock = NSLock()
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()
    private let session: URLSession

    init() {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 4
        configuration.timeoutIntervalForResource = 4
        configuration.waitsForConnectivity = false
        session = URLSession(configuration: configuration)
    }

    func health(baseURL: String) async throws -> HealthResponse {
        try await request(baseURL: baseURL, path: "/healthz", method: "GET", body: Optional<String>.none, responseType: HealthResponse.self)
    }

    func registerUser(baseURL: String, command: RegisterUserCommand) async throws -> UserRecord {
        try await request(baseURL: baseURL, path: "/v1/users", method: "POST", body: command, responseType: UserRecord.self)
    }

    func registerDevice(baseURL: String, command: RegisterDeviceCommand) async throws -> RegisteredDeviceRecord {
        try await request(baseURL: baseURL, path: "/v1/devices", method: "POST", body: command, responseType: RegisteredDeviceRecord.self)
    }

    func publishPreKeys(baseURL: String, command: PublishPreKeyBundleCommand) async throws -> PreKeyBundleRecord {
        try await request(baseURL: baseURL, path: "/v1/prekeys", method: "POST", body: command, responseType: PreKeyBundleRecord.self)
    }

    func createConversation(baseURL: String, command: CreateConversationCommand) async throws -> ConversationDescriptor {
        try await request(baseURL: baseURL, path: "/v1/conversations", method: "POST", body: command, responseType: ConversationDescriptor.self)
    }

    func listConversations(baseURL: String, userId: String) async throws -> [ConversationDescriptor] {
        try await request(baseURL: baseURL, path: "/v1/users/\(userId)/conversations", method: "GET", body: Optional<String>.none, responseType: [ConversationDescriptor].self)
    }

    func listMessages(baseURL: String, conversationId: String) async throws -> [EncryptedEnvelope] {
        try await request(baseURL: baseURL, path: "/v1/conversations/\(conversationId)/messages", method: "GET", body: Optional<String>.none, responseType: [EncryptedEnvelope].self)
    }

    func sendMessage(baseURL: String, command: SendEncryptedMessageCommand) async throws -> EncryptedEnvelope {
        try await request(baseURL: baseURL, path: "/v1/messages", method: "POST", body: command, responseType: EncryptedEnvelope.self)
    }

    func createInvite(baseURL: String, command: CreateInviteCodeCommand) async throws -> InviteCodeRecord {
        try await request(baseURL: baseURL, path: "/v1/invites", method: "POST", body: command, responseType: InviteCodeRecord.self)
    }

    func importInvite(baseURL: String, command: AddContactByInviteCodeCommand) async throws -> [ContactRecord] {
        try await request(baseURL: baseURL, path: "/v1/contacts/by-invite", method: "POST", body: command, responseType: [ContactRecord].self)
    }

    // Group member management

    func listConversationMembers(baseURL: String, conversationId: String, offset: Int = 0, limit: Int = 50) async throws -> GroupMemberPage {
        try await request(baseURL: baseURL, path: "/v1/conversations/\(conversationId)/members?offset=\(offset)&limit=\(limit)", method: "GET", body: Optional<String>.none, responseType: GroupMemberPage.self)
    }

    func addConversationMembers(baseURL: String, conversationId: String, command: AddGroupMembersCommand) async throws -> GroupMemberPage {
        try await request(baseURL: baseURL, path: "/v1/conversations/\(conversationId)/members", method: "POST", body: command, responseType: GroupMemberPage.self)
    }

    func removeConversationMembers(baseURL: String, conversationId: String, command: RemoveGroupMembersCommand) async throws -> GroupMemberPage {
        try await request(baseURL: baseURL, path: "/v1/conversations/\(conversationId)/members", method: "DELETE", body: command, responseType: GroupMemberPage.self)
    }

    func updateMemberRole(baseURL: String, conversationId: String, userId: String, command: UpdateMemberRoleCommand) async throws -> GroupMemberPage {
        try await request(baseURL: baseURL, path: "/v1/conversations/\(conversationId)/members/\(userId)", method: "PATCH", body: command, responseType: GroupMemberPage.self)
    }

    func leaveConversation(baseURL: String, conversationId: String, command: LeaveConversationCommand) async throws -> ConversationDescriptor {
        try await request(baseURL: baseURL, path: "/v1/conversations/\(conversationId)/leave", method: "POST", body: command, responseType: ConversationDescriptor.self)
    }

    func updateConversationSettings(baseURL: String, conversationId: String, command: UpdateConversationSettingsCommand) async throws -> ConversationDescriptor {
        try await request(baseURL: baseURL, path: "/v1/conversations/\(conversationId)", method: "PATCH", body: command, responseType: ConversationDescriptor.self)
    }

    // Mentions and aggregated receipts

    func listMentionsForUser(baseURL: String, userId: String, offset: Int = 0, limit: Int = 50) async throws -> [MentionNotification] {
        try await request(baseURL: baseURL, path: "/v1/users/\(userId)/mentions?offset=\(offset)&limit=\(limit)", method: "GET", body: Optional<String>.none, responseType: [MentionNotification].self)
    }

    func getAggregatedReadReceipt(baseURL: String, messageId: String) async throws -> AggregatedReadReceipt {
        try await request(baseURL: baseURL, path: "/v1/messages/\(messageId)/receipts/aggregated", method: "GET", body: Optional<String>.none, responseType: AggregatedReadReceipt.self)
    }

    // WebSocket methods

    func connectToConversation(baseURL: String, conversationId: String) -> AsyncStream<EncryptedEnvelope> {
        AsyncStream { continuation in
            let wsURL = baseURL
                .trimmingCharacters(in: .whitespacesAndNewlines)
                .trimmingCharacters(in: CharacterSet(charactersIn: "/"))
                .replacingOccurrences(of: "http://", with: "ws://")
                .replacingOccurrences(of: "https://", with: "wss://")

            guard let url = URL(string: "\(wsURL)/v1/ws/conversations/\(conversationId)") else {
                continuation.finish()
                return
            }

            let task = session.webSocketTask(with: url)

            webSocketLock.lock()
            activeWebSocketTasks[conversationId] = task
            webSocketLock.unlock()

            task.resume()

            continuation.onTermination = { [weak self] _ in
                self?.disconnectFromConversation(conversationId: conversationId)
            }

            Task {
                await receiveMessages(task: task, conversationId: conversationId, continuation: continuation)
            }
        }
    }

    private func receiveMessages(task: URLSessionWebSocketTask, conversationId: String, continuation: AsyncStream<EncryptedEnvelope>.Continuation) async {
        while task.state == .running {
            do {
                let message = try await task.receive()
                switch message {
                case .string(let text):
                    if let data = text.data(using: .utf8),
                       let envelope = try? decoder.decode(EncryptedEnvelope.self, from: data) {
                        continuation.yield(envelope)
                    }
                case .data(let data):
                    if let envelope = try? decoder.decode(EncryptedEnvelope.self, from: data) {
                        continuation.yield(envelope)
                    }
                @unknown default:
                    break
                }
            } catch {
                // Connection closed or error
                break
            }
        }

        webSocketLock.lock()
        activeWebSocketTasks.removeValue(forKey: conversationId)
        webSocketLock.unlock()

        continuation.finish()
    }

    func disconnectFromConversation(conversationId: String) {
        webSocketLock.lock()
        let task = activeWebSocketTasks.removeValue(forKey: conversationId)
        webSocketLock.unlock()

        task?.cancel(with: .normalClosure, reason: nil)
    }

    func isConnectedToConversation(_ conversationId: String) -> Bool {
        webSocketLock.lock()
        defer { webSocketLock.unlock() }
        return activeWebSocketTasks[conversationId]?.state == .running
    }

    // Push token registration

    func registerPushToken(baseURL: String, userId: String, deviceId: String, platform: String, token: String) async throws {
        let _: PushTokenResponse = try await request(
            baseURL: baseURL,
            path: "/v1/devices/\(deviceId)/push-token",
            method: "POST",
            body: RegisterPushTokenRequest(userId: userId, deviceId: deviceId, platform: platform, token: token),
            responseType: PushTokenResponse.self
        )
    }

    func deletePushToken(baseURL: String, deviceId: String) async throws {
        let _: EmptyResponse = try await request(
            baseURL: baseURL,
            path: "/v1/devices/\(deviceId)/push-token",
            method: "DELETE",
            body: Optional<String>.none,
            responseType: EmptyResponse.self
        )
    }

    private func request<Response: Decodable, Body: Encodable>(
        baseURL: String,
        path: String,
        method: String,
        body: Body?,
        responseType: Response.Type
    ) async throws -> Response {
        guard let url = URL(string: baseURL.trimmingCharacters(in: .whitespacesAndNewlines).trimmingCharacters(in: CharacterSet(charactersIn: "/")) + path) else {
            throw DeeplineClientError.invalidURL(baseURL)
        }

        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let body {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try encoder.encode(body)
        }

        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw DeeplineClientError.invalidResponse("Server did not return an HTTP response.")
        }
        guard (200 ... 299).contains(httpResponse.statusCode) else {
            let bodyText = String(data: data, encoding: .utf8) ?? ""
            throw DeeplineClientError.httpFailure(httpResponse.statusCode, bodyText)
        }

        // Handle 204 No Content responses
        if httpResponse.statusCode == 204 || data.isEmpty {
            if let emptyResponse = EmptyResponse() as? Response {
                return emptyResponse
            }
        }

        return try decoder.decode(Response.self, from: data)
    }
}
