import SwiftUI

struct RootView: View {
    @EnvironmentObject private var model: DeeplineAppModel
    @State private var navigationPath = NavigationPath()
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        NavigationStack(path: $navigationPath) {
            Group {
                if model.identityReady {
                    TabView {
                        ChatListView(openRoute: push)
                            .tabItem {
                                Label("Chats", systemImage: "bubble.left.and.bubble.right.fill")
                            }

                        SettingsView(openRoute: push)
                            .tabItem {
                                Label("Settings", systemImage: "gearshape.fill")
                            }
                    }
                } else {
                    WelcomeView(startSetup: { navigationPath.append(DeeplineRoute.identitySetup) })
                }
            }
            .navigationDestination(for: DeeplineRoute.self) { route in
                routeView(route)
            }
            .tint(DeeplineTheme.primary(colorScheme))
            .background(DeeplineTheme.background(colorScheme).ignoresSafeArea())
        }
        .onChange(of: model.pendingConversationId) { _, pendingConversationId in
            guard model.identityReady, let conversationId = pendingConversationId else { return }
            navigationPath = NavigationPath()
            navigationPath.append(DeeplineRoute.chat(conversationId))
            _ = model.consumePendingConversationId()
        }
        .task {
            await model.bootstrapIfNeeded()
        }
        .overlay(alignment: .center) {
            if model.isBusy {
                ProgressView("Syncing Deepline")
                    .padding(18)
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
        }
        .overlay(alignment: .bottom) {
            if let errorMessage = model.errorMessage {
                Text(errorMessage)
                    .font(.footnote)
                    .foregroundStyle(.white)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(Color.red.opacity(0.88), in: Capsule())
                    .padding(.bottom, 18)
                    .onTapGesture {
                        model.clearError()
                    }
            }
        }
        .preferredColorScheme(nil)
    }

    @ViewBuilder
    private func routeView(_ route: DeeplineRoute) -> some View {
        switch route {
        case .identitySetup:
            IdentitySetupView { name, device in
                Task {
                    _ = await model.completeIdentity(name: name, device: device)
                }
            }
        case .addFriend:
            AddFriendView()
        case .settings:
            SettingsView(openRoute: push)
        case .devices:
            DevicesView()
        case .chat(let id):
            ChatRoomView(conversationId: id, openRoute: push)
        case .createGroup:
            CreateGroupView { conversationId in
                navigationPath = NavigationPath()
                navigationPath.append(DeeplineRoute.chat(conversationId))
            }
        case .groupInfo(let id):
            GroupInfoView(conversationId: id)
        }
    }

    private func push(_ route: DeeplineRoute) {
        navigationPath.append(route)
    }
}

// MARK: - Theme with Dark Mode Support
private enum DeeplineTheme {
    // Brand colors
    static let deeplineBlue = Color(red: 0, green: 0.4, blue: 1)
    static let deeplineLightBlue = Color(red: 0.24, green: 0.545, blue: 1)
    static let deeplineDarkBlue = Color(red: 0, green: 0.322, blue: 0.8)

    static func primary(_ scheme: ColorScheme) -> Color {
        scheme == .dark ? Color(red: 0.345, green: 0.616, blue: 1) : deeplineBlue
    }

    static func background(_ scheme: ColorScheme) -> Color {
        scheme == .dark ? Color(red: 0.086, green: 0.102, blue: 0.114) : Color(red: 0.969, green: 0.973, blue: 0.98)
    }

    static func surface(_ scheme: ColorScheme) -> Color {
        scheme == .dark ? Color(red: 0.114, green: 0.129, blue: 0.145) : .white
    }

    static func surfaceVariant(_ scheme: ColorScheme) -> Color {
        scheme == .dark ? Color(red: 0.157, green: 0.180, blue: 0.2) : Color(red: 0.941, green: 0.949, blue: 0.961)
    }

    static func onSurface(_ scheme: ColorScheme) -> Color {
        scheme == .dark ? Color(red: 0.871, green: 0.894, blue: 0.918) : Color(red: 0.102, green: 0.114, blue: 0.137)
    }

    static func onSurfaceVariant(_ scheme: ColorScheme) -> Color {
        scheme == .dark ? Color(red: 0.624, green: 0.678, blue: 0.737) : Color(red: 0.267, green: 0.337, blue: 0.435)
    }

    static func outline(_ scheme: ColorScheme) -> Color {
        scheme == .dark ? Color(red: 0.271, green: 0.310, blue: 0.349) : Color(red: 0.875, green: 0.882, blue: 0.902)
    }

    static func header(_ scheme: ColorScheme) -> Color {
        scheme == .dark ? Color(red: 0.114, green: 0.129, blue: 0.145) : Color(red: 0.102, green: 0.114, blue: 0.137)
    }

    static func bubbleMine(_ scheme: ColorScheme) -> Color {
        scheme == .dark ? Color(red: 0.027, green: 0.278, blue: 0.651) : Color(red: 0, green: 0.4, blue: 1)
    }

    static func bubbleOther(_ scheme: ColorScheme) -> Color {
        scheme == .dark ? Color(red: 0.157, green: 0.180, blue: 0.2) : Color(red: 0.941, green: 0.949, blue: 0.961)
    }

    static func success(_ scheme: ColorScheme) -> Color {
        scheme == .dark ? Color(red: 0.290, green: 0.871, blue: 0.502) : Color(red: 0, green: 0.529, blue: 0.353)
    }
}

private struct WelcomeView: View {
    @Environment(\.colorScheme) private var colorScheme
    let startSetup: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .center, spacing: 32) {
                Spacer().frame(height: 40)

                ZStack {
                    Circle()
                        .fill(
                            LinearGradient(
                                colors: [
                                    DeeplineTheme.primary(colorScheme).opacity(0.3),
                                    DeeplineTheme.primary(colorScheme).opacity(0.1)
                                ],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .frame(width: 120, height: 120)

                    Circle()
                        .fill(DeeplineTheme.primary(colorScheme))
                        .frame(width: 96, height: 96)

                    Text("DL")
                        .font(.system(size: 40, weight: .bold, design: .rounded))
                        .foregroundStyle(.white)
                }
                .shadow(color: DeeplineTheme.primary(colorScheme).opacity(0.3), radius: 20, y: 10)

                VStack(spacing: 12) {
                    Text("Deepline")
                        .font(.system(size: 36, weight: .bold, design: .rounded))
                        .foregroundStyle(DeeplineTheme.onSurface(colorScheme))

                    Text("Private-by-default messaging")
                        .font(.title3)
                        .foregroundStyle(DeeplineTheme.onSurfaceVariant(colorScheme))
                }

                VStack(spacing: 16) {
                    WelcomeFeatureRow(
                        icon: "lock.shield.fill",
                        title: "End-to-End Encrypted",
                        description: "Messages are encrypted before leaving your device"
                    )
                    WelcomeFeatureRow(
                        icon: "server.rack",
                        title: "Zero-Knowledge Server",
                        description: "Server only sees encrypted blobs, never plaintext"
                    )
                    WelcomeFeatureRow(
                        icon: "iphone.and.arrow.forward",
                        title: "Native Performance",
                        description: "Built with SwiftUI for the best iOS experience"
                    )
                }
                .padding(.horizontal, 24)

                Spacer().frame(height: 20)

                Button(action: startSetup) {
                    HStack(spacing: 10) {
                        Text("Get Started")
                            .font(.headline)
                        Image(systemName: "arrow.right")
                            .font(.headline)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                    .background(DeeplineTheme.primary(colorScheme))
                    .foregroundStyle(.white)
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
                .padding(.horizontal, 24)

                Spacer().frame(height: 24)
            }
        }
        .background(DeeplineTheme.background(colorScheme).ignoresSafeArea())
    }
}

private struct WelcomeFeatureRow: View {
    @Environment(\.colorScheme) private var colorScheme
    let icon: String
    let title: String
    let description: String

    var body: some View {
        HStack(alignment: .top, spacing: 16) {
            Image(systemName: icon)
                .font(.system(size: 22))
                .foregroundStyle(DeeplineTheme.primary(colorScheme))
                .frame(width: 44, height: 44)
                .background(DeeplineTheme.primary(colorScheme).opacity(0.12))
                .clipShape(Circle())

            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(DeeplineTheme.onSurface(colorScheme))
                Text(description)
                    .font(.subheadline)
                    .foregroundStyle(DeeplineTheme.onSurfaceVariant(colorScheme))
            }
            Spacer()
        }
        .padding(16)
        .background(DeeplineTheme.surface(colorScheme))
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

private struct FeatureCard: View {
    @Environment(\.colorScheme) private var colorScheme
    let title: String
    let detail: String

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.headline)
                .foregroundStyle(DeeplineTheme.onSurface(colorScheme))
            Text(detail)
                .foregroundStyle(DeeplineTheme.onSurfaceVariant(colorScheme))
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(DeeplineTheme.surface(colorScheme), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(DeeplineTheme.outline(colorScheme), lineWidth: 1)
        )
    }
}

private struct IdentitySetupView: View {
    @Environment(\.colorScheme) private var colorScheme
    @State private var displayName = ""
    @State private var deviceName = "iPhone Secure"
    let complete: (String, String) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Create Your Identity")
                        .font(.title.bold())
                        .foregroundStyle(DeeplineTheme.onSurface(colorScheme))

                    Text("Identity keys stay on-device. Plaintext never belongs on the server.")
                        .foregroundStyle(DeeplineTheme.onSurfaceVariant(colorScheme))
                }

                VStack(spacing: 16) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Display Name")
                            .font(.subheadline.weight(.medium))
                            .foregroundStyle(DeeplineTheme.onSurfaceVariant(colorScheme))
                        TextField("Enter your name", text: $displayName)
                            .padding(14)
                            .background(DeeplineTheme.surfaceVariant(colorScheme))
                            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        Text("Device Label")
                            .font(.subheadline.weight(.medium))
                            .foregroundStyle(DeeplineTheme.onSurfaceVariant(colorScheme))
                        TextField("e.g. iPhone 15 Pro", text: $deviceName)
                            .padding(14)
                            .background(DeeplineTheme.surfaceVariant(colorScheme))
                            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                }

                Button {
                    complete(displayName, deviceName)
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "key.fill")
                        Text("Create Identity")
                    }
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                    .background(DeeplineTheme.primary(colorScheme))
                    .foregroundStyle(.white)
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
                .disabled(displayName.trimmingCharacters(in: .whitespaces).isEmpty)
                .opacity(displayName.trimmingCharacters(in: .whitespaces).isEmpty ? 0.5 : 1)
            }
            .padding(24)
        }
        .background(DeeplineTheme.background(colorScheme).ignoresSafeArea())
        .navigationTitle("Identity")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct ChatListView: View {
    @EnvironmentObject private var model: DeeplineAppModel
    @Environment(\.colorScheme) private var colorScheme
    let openRoute: (DeeplineRoute) -> Void

    var body: some View {
        VStack(spacing: 0) {
            // Action bar (no title, just actions like WhatsApp/KakaoTalk)
            HStack {
                Text("Chats")
                    .font(.title.weight(.bold))
                    .foregroundStyle(DeeplineTheme.onSurface(colorScheme))
                Spacer()
                Button {
                    Task { await model.refreshConversations() }
                } label: {
                    Image(systemName: "arrow.triangle.2.circlepath")
                        .font(.system(size: 20, weight: .medium))
                        .foregroundStyle(DeeplineTheme.onSurfaceVariant(colorScheme))
                }
                .frame(width: 44, height: 44)

                Button {
                    openRoute(.createGroup)
                } label: {
                    Image(systemName: "person.3.fill")
                        .font(.system(size: 18, weight: .medium))
                        .foregroundStyle(DeeplineTheme.primary(colorScheme))
                }
                .frame(width: 44, height: 44)

                Button {
                    openRoute(.addFriend)
                } label: {
                    Image(systemName: "square.and.pencil")
                        .font(.system(size: 20, weight: .medium))
                        .foregroundStyle(DeeplineTheme.primary(colorScheme))
                }
                .frame(width: 44, height: 44)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .background(DeeplineTheme.surface(colorScheme))

            // Chat list
            ScrollView {
                LazyVStack(spacing: 0) {
                    if model.chats.isEmpty {
                        VStack(spacing: 16) {
                            Image(systemName: "bubble.left.and.bubble.right")
                                .font(.system(size: 48))
                                .foregroundStyle(DeeplineTheme.onSurfaceVariant(colorScheme).opacity(0.5))
                            Text("No conversations yet")
                                .font(.headline)
                                .foregroundStyle(DeeplineTheme.onSurfaceVariant(colorScheme))
                            Text("Create an invite or import one to start chatting")
                                .font(.subheadline)
                                .foregroundStyle(DeeplineTheme.onSurfaceVariant(colorScheme).opacity(0.7))
                                .multilineTextAlignment(.center)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 60)
                    } else {
                        ForEach(model.chats) { chat in
                            Button {
                                openRoute(.chat(chat.id))
                            } label: {
                                ChatRow(chat: chat)
                            }
                            .buttonStyle(ChatRowButtonStyle())
                        }
                    }
                }
            }
            .background(DeeplineTheme.background(colorScheme))
        }
        .navigationBarHidden(true)
        .task {
            await model.refreshConversations()
        }
    }
}

private struct ChatRowButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.98 : 1.0)
            .animation(.easeInOut(duration: 0.1), value: configuration.isPressed)
    }
}

private struct ChatRow: View {
    @Environment(\.colorScheme) private var colorScheme
    let chat: DeeplineChat

    var body: some View {
        HStack(spacing: 14) {
            // Avatar
            ZStack {
                Circle()
                    .fill(avatarGradient)
                    .frame(width: 54, height: 54)
                Text(chat.title.prefix(1).uppercased())
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundStyle(.white)
            }

            // Content
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(chat.title)
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(DeeplineTheme.onSurface(colorScheme))
                    Spacer()
                    Text("now")
                        .font(.system(size: 13))
                        .foregroundStyle(DeeplineTheme.onSurfaceVariant(colorScheme))
                }

                HStack {
                    Text(chat.preview)
                        .font(.system(size: 14))
                        .foregroundStyle(DeeplineTheme.onSurfaceVariant(colorScheme))
                        .lineLimit(1)
                    Spacer()
                    Image(systemName: "lock.fill")
                        .font(.system(size: 12))
                        .foregroundStyle(DeeplineTheme.success(colorScheme))
                }
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 14)
        .background(DeeplineTheme.surface(colorScheme))
    }

    private var avatarGradient: LinearGradient {
        let colors: [Color] = [
            Color(red: 0.345, green: 0.616, blue: 1),
            Color(red: 0.2, green: 0.4, blue: 0.9)
        ]
        return LinearGradient(colors: colors, startPoint: .topLeading, endPoint: .bottomTrailing)
    }
}


private struct ChatRoomView: View {
    @EnvironmentObject private var model: DeeplineAppModel
    @Environment(\.colorScheme) private var colorScheme
    let conversationId: String
    let openRoute: (DeeplineRoute) -> Void
    @State private var draft = ""
    @FocusState private var isInputFocused: Bool

    var body: some View {
        VStack(spacing: 0) {
            // Messages area
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 12) {
                        if model.currentMessages(for: conversationId).isEmpty {
                            VStack(spacing: 16) {
                                Image(systemName: "lock.shield.fill")
                                    .font(.system(size: 48))
                                    .foregroundStyle(DeeplineTheme.primary(colorScheme).opacity(0.6))
                                Text("End-to-End Encrypted")
                                    .font(.headline)
                                    .foregroundStyle(DeeplineTheme.onSurfaceVariant(colorScheme))
                                Text("Messages are encrypted before leaving your device")
                                    .font(.subheadline)
                                    .foregroundStyle(DeeplineTheme.onSurfaceVariant(colorScheme).opacity(0.7))
                                    .multilineTextAlignment(.center)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 60)
                        } else {
                            ForEach(model.currentMessages(for: conversationId)) { message in
                                MessageBubble(message: message)
                                    .id(message.id)
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                }
                .onChange(of: model.currentMessages(for: conversationId).count) { _, _ in
                    if let lastMessage = model.currentMessages(for: conversationId).last {
                        withAnimation {
                            proxy.scrollTo(lastMessage.id, anchor: .bottom)
                        }
                    }
                }
            }

            // Input bar
            ChatInputBar(
                draft: $draft,
                isInputFocused: _isInputFocused,
                onSend: {
                    let outgoing = draft
                    draft = ""
                    Task { await model.sendMessage(conversationId: conversationId, text: outgoing) }
                }
            )
        }
        .background(DeeplineTheme.background(colorScheme).ignoresSafeArea())
        .navigationTitle(model.chatTitle(for: conversationId))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                HStack(spacing: 12) {
                    Button {
                        Task { await model.loadMessages(conversationId: conversationId) }
                    } label: {
                        Image(systemName: "arrow.triangle.2.circlepath")
                            .foregroundStyle(DeeplineTheme.primary(colorScheme))
                    }
                    if model.isGroup(conversationId) {
                        Button {
                            openRoute(.groupInfo(conversationId))
                        } label: {
                            Image(systemName: "info.circle")
                                .foregroundStyle(DeeplineTheme.primary(colorScheme))
                        }
                    }
                }
            }
        }
        .task {
            await model.loadMessages(conversationId: conversationId)
            if model.isGroup(conversationId) {
                await model.loadGroupMembers(conversationId: conversationId)
            }
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 3_000_000_000)
                if Task.isCancelled { break }
                await model.loadMessages(conversationId: conversationId)
            }
        }
    }
}

private struct MessageBubble: View {
    @Environment(\.colorScheme) private var colorScheme
    let message: DeeplineMessage

    var body: some View {
        if message.isSystem {
            HStack {
                Spacer()
                Text(message.body)
                    .font(.caption)
                    .foregroundStyle(DeeplineTheme.onSurfaceVariant(colorScheme))
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .background(DeeplineTheme.surfaceVariant(colorScheme).opacity(0.5))
                    .clipShape(Capsule())
                Spacer()
            }
        } else {
            HStack(alignment: .bottom, spacing: 8) {
                if message.isMine { Spacer(minLength: 60) }

                if !message.isMine {
                    // Avatar for other's messages
                    Circle()
                        .fill(DeeplineTheme.primary(colorScheme).opacity(0.2))
                        .frame(width: 32, height: 32)
                        .overlay(
                            Text(message.author.prefix(1).uppercased())
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundStyle(DeeplineTheme.primary(colorScheme))
                        )
                }

                VStack(alignment: message.isMine ? .trailing : .leading, spacing: 4) {
                    if !message.isMine {
                        Text(message.author)
                            .font(.caption.weight(.medium))
                            .foregroundStyle(DeeplineTheme.primary(colorScheme))
                    }

                    HStack(alignment: .bottom, spacing: 6) {
                        if message.isMine {
                            Text("now")
                                .font(.system(size: 11))
                                .foregroundStyle(message.isMine ? .white.opacity(0.7) : DeeplineTheme.onSurfaceVariant(colorScheme))
                        }

                        Text(message.body)
                            .foregroundStyle(message.isMine ? .white : DeeplineTheme.onSurface(colorScheme))

                        if !message.isMine {
                            Text("now")
                                .font(.system(size: 11))
                                .foregroundStyle(DeeplineTheme.onSurfaceVariant(colorScheme))
                        }
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(
                        message.isMine
                            ? DeeplineTheme.bubbleMine(colorScheme)
                            : DeeplineTheme.bubbleOther(colorScheme)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                }

                if !message.isMine { Spacer(minLength: 60) }
            }
        }
    }
}

private struct AddFriendView: View {
    @EnvironmentObject private var model: DeeplineAppModel
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.dismiss) private var dismiss
    @State private var inviteCode = ""

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                // QR Code Section
                VStack(spacing: 16) {
                    Image(systemName: "qrcode.viewfinder")
                        .font(.system(size: 64))
                        .foregroundStyle(DeeplineTheme.primary(colorScheme))
                        .frame(width: 120, height: 120)
                        .background(DeeplineTheme.primary(colorScheme).opacity(0.1))
                        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))

                    Text("Scan QR Code")
                        .font(.headline)
                        .foregroundStyle(DeeplineTheme.onSurface(colorScheme))

                    Text("Scan a friend's QR code to add them")
                        .font(.subheadline)
                        .foregroundStyle(DeeplineTheme.onSurfaceVariant(colorScheme))
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 24)
                .background(DeeplineTheme.surface(colorScheme))
                .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))

                // Generate Invite Section
                VStack(alignment: .leading, spacing: 16) {
                    HStack {
                        Image(systemName: "link")
                            .foregroundStyle(DeeplineTheme.primary(colorScheme))
                        Text("Share Invite Link")
                            .font(.headline)
                            .foregroundStyle(DeeplineTheme.onSurface(colorScheme))
                    }

                    if !model.latestInviteCode.isEmpty {
                        Text(model.latestInviteCode)
                            .font(.system(.caption, design: .monospaced))
                            .padding(14)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(DeeplineTheme.surfaceVariant(colorScheme))
                            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }

                    Button {
                        Task { await model.createInvite() }
                    } label: {
                        HStack {
                            Image(systemName: "square.and.arrow.up")
                            Text("Generate Invite Code")
                        }
                        .font(.subheadline.weight(.semibold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(DeeplineTheme.primary(colorScheme))
                        .foregroundStyle(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                }
                .padding(20)
                .background(DeeplineTheme.surface(colorScheme))
                .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))

                // Import Invite Section
                VStack(alignment: .leading, spacing: 16) {
                    HStack {
                        Image(systemName: "doc.text")
                            .foregroundStyle(DeeplineTheme.primary(colorScheme))
                        Text("Import Invite Code")
                            .font(.headline)
                            .foregroundStyle(DeeplineTheme.onSurface(colorScheme))
                    }

                    TextField("Paste invite code here", text: $inviteCode)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .padding(14)
                        .background(DeeplineTheme.surfaceVariant(colorScheme))
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))

                    Button {
                        Task {
                            if let conversationId = await model.importInvite(code: inviteCode) {
                                model.pendingConversationId = conversationId
                                dismiss()
                            }
                        }
                    } label: {
                        HStack {
                            Image(systemName: "person.badge.plus")
                            Text("Add Friend")
                        }
                        .font(.subheadline.weight(.semibold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(inviteCode.isEmpty ? DeeplineTheme.surfaceVariant(colorScheme) : DeeplineTheme.primary(colorScheme))
                        .foregroundStyle(inviteCode.isEmpty ? DeeplineTheme.onSurfaceVariant(colorScheme) : .white)
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                    .disabled(inviteCode.isEmpty)
                }
                .padding(20)
                .background(DeeplineTheme.surface(colorScheme))
                .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
            }
            .padding(20)
        }
        .background(DeeplineTheme.background(colorScheme).ignoresSafeArea())
        .navigationTitle("Add Friend")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct SettingsView: View {
    @EnvironmentObject private var model: DeeplineAppModel
    @Environment(\.colorScheme) private var colorScheme
    let openRoute: (DeeplineRoute) -> Void
    @State private var editableServerURL = ""
    private let cryptoGate = CryptoGate()

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                // Profile Card
                VStack(spacing: 16) {
                    ZStack {
                        Circle()
                            .fill(
                                LinearGradient(
                                    colors: [DeeplineTheme.primary(colorScheme), DeeplineTheme.primary(colorScheme).opacity(0.7)],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                            )
                            .frame(width: 80, height: 80)
                        Text((model.displayName.isEmpty ? "U" : model.displayName).prefix(1).uppercased())
                            .font(.system(size: 32, weight: .bold))
                            .foregroundStyle(.white)
                    }

                    VStack(spacing: 4) {
                        Text(model.displayName.isEmpty ? "Deepline User" : model.displayName)
                            .font(.title3.weight(.semibold))
                            .foregroundStyle(DeeplineTheme.onSurface(colorScheme))
                        Text(model.deviceName)
                            .font(.subheadline)
                            .foregroundStyle(DeeplineTheme.onSurfaceVariant(colorScheme))
                    }

                    if let userId = model.userId {
                        Text(userId)
                            .font(.system(.caption, design: .monospaced))
                            .foregroundStyle(DeeplineTheme.onSurfaceVariant(colorScheme).opacity(0.7))
                            .lineLimit(1)
                            .truncationMode(.middle)
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 24)
                .background(DeeplineTheme.surface(colorScheme))
                .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))

                // Server Configuration
                VStack(alignment: .leading, spacing: 16) {
                    HStack {
                        Image(systemName: "server.rack")
                            .foregroundStyle(DeeplineTheme.primary(colorScheme))
                        Text("Server Configuration")
                            .font(.headline)
                            .foregroundStyle(DeeplineTheme.onSurface(colorScheme))
                    }

                    TextField("Server URL", text: $editableServerURL)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .padding(14)
                        .background(DeeplineTheme.surfaceVariant(colorScheme))
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))

                    Button {
                        model.saveServerBaseURL(editableServerURL)
                        Task { await model.refreshConversations() }
                    } label: {
                        HStack {
                            Image(systemName: "arrow.triangle.2.circlepath")
                            Text("Save & Sync")
                        }
                        .font(.subheadline.weight(.semibold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(DeeplineTheme.primary(colorScheme))
                        .foregroundStyle(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                }
                .padding(20)
                .background(DeeplineTheme.surface(colorScheme))
                .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))

                // Security Status
                VStack(alignment: .leading, spacing: 16) {
                    HStack {
                        Image(systemName: cryptoGate.productionLaunchAllowed ? "checkmark.shield.fill" : "exclamationmark.shield.fill")
                            .foregroundStyle(cryptoGate.productionLaunchAllowed ? DeeplineTheme.success(colorScheme) : .orange)
                        Text("Security Status")
                            .font(.headline)
                            .foregroundStyle(DeeplineTheme.onSurface(colorScheme))
                    }

                    Text(cryptoGate.productionLaunchAllowed
                        ? "Native crypto bridge is ready for release gating."
                        : "Native Signal and MLS bridges are still gated before production release.")
                        .font(.subheadline)
                        .foregroundStyle(DeeplineTheme.onSurfaceVariant(colorScheme))

                    Button {
                        openRoute(.devices)
                    } label: {
                        HStack {
                            Image(systemName: "iphone.and.arrow.forward")
                            Text("Devices & Safety")
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.system(size: 14, weight: .semibold))
                        }
                        .foregroundStyle(DeeplineTheme.onSurface(colorScheme))
                        .padding(14)
                        .background(DeeplineTheme.surfaceVariant(colorScheme))
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                }
                .padding(20)
                .background(DeeplineTheme.surface(colorScheme))
                .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
            }
            .padding(20)
        }
        .background(DeeplineTheme.background(colorScheme).ignoresSafeArea())
        .navigationTitle("Settings")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            editableServerURL = model.serverBaseURL
        }
    }
}

private struct DevicesView: View {
    @EnvironmentObject private var model: DeeplineAppModel
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                // Current Device
                VStack(alignment: .leading, spacing: 16) {
                    HStack {
                        Image(systemName: "iphone")
                            .font(.system(size: 24))
                            .foregroundStyle(DeeplineTheme.primary(colorScheme))
                        VStack(alignment: .leading, spacing: 2) {
                            Text(model.deviceName)
                                .font(.headline)
                                .foregroundStyle(DeeplineTheme.onSurface(colorScheme))
                            Text("This device")
                                .font(.caption)
                                .foregroundStyle(DeeplineTheme.success(colorScheme))
                        }
                        Spacer()
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundStyle(DeeplineTheme.success(colorScheme))
                    }
                }
                .padding(20)
                .background(DeeplineTheme.surface(colorScheme))
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))

                // Verification Section
                VStack(alignment: .leading, spacing: 16) {
                    HStack {
                        Image(systemName: "person.2.badge.key.fill")
                            .foregroundStyle(DeeplineTheme.primary(colorScheme))
                        Text("Verification")
                            .font(.headline)
                            .foregroundStyle(DeeplineTheme.onSurface(colorScheme))
                    }

                    Text("Safety-number review and linked-device controls belong here.")
                        .font(.subheadline)
                        .foregroundStyle(DeeplineTheme.onSurfaceVariant(colorScheme))

                    HStack {
                        Image(systemName: "info.circle")
                            .foregroundStyle(.orange)
                        Text("Feature coming soon")
                            .font(.caption)
                            .foregroundStyle(.orange)
                    }
                    .padding(10)
                    .background(Color.orange.opacity(0.1))
                    .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                }
                .padding(20)
                .background(DeeplineTheme.surface(colorScheme))
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))

                // Transport Privacy Section
                VStack(alignment: .leading, spacing: 16) {
                    HStack {
                        Image(systemName: "envelope.badge.shield.half.filled")
                            .foregroundStyle(DeeplineTheme.primary(colorScheme))
                        Text("Transport Privacy")
                            .font(.headline)
                            .foregroundStyle(DeeplineTheme.onSurface(colorScheme))
                    }

                    Text("Sealed sender and push metadata minimization still require native crypto integration.")
                        .font(.subheadline)
                        .foregroundStyle(DeeplineTheme.onSurfaceVariant(colorScheme))
                }
                .padding(20)
                .background(DeeplineTheme.surface(colorScheme))
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
            .padding(20)
        }
        .background(DeeplineTheme.background(colorScheme).ignoresSafeArea())
        .navigationTitle("Devices")
        .navigationBarTitleDisplayMode(.inline)
    }
}

// MARK: - Chat Input Bar (WhatsApp/KakaoTalk style)
private struct ChatInputBar: View {
    @Environment(\.colorScheme) private var colorScheme
    @Binding var draft: String
    @FocusState var isInputFocused: Bool
    let onSend: () -> Void
    @State private var showAttachmentMenu = false

    var body: some View {
        VStack(spacing: 0) {
            // Attachment menu (expandable)
            if showAttachmentMenu {
                HStack(spacing: 24) {
                    AttachmentButton(icon: "camera.fill", label: "Camera", color: .orange) {
                        showAttachmentMenu = false
                    }
                    AttachmentButton(icon: "photo.fill", label: "Photos", color: .purple) {
                        showAttachmentMenu = false
                    }
                    AttachmentButton(icon: "doc.fill", label: "File", color: .blue) {
                        showAttachmentMenu = false
                    }
                    AttachmentButton(icon: "location.fill", label: "Location", color: .green) {
                        showAttachmentMenu = false
                    }
                    AttachmentButton(icon: "person.fill", label: "Contact", color: .cyan) {
                        showAttachmentMenu = false
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 16)
                .background(DeeplineTheme.surface(colorScheme))
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }

            Divider()
                .background(DeeplineTheme.outline(colorScheme))

            HStack(alignment: .bottom, spacing: 8) {
                // Attachment toggle button
                Button {
                    withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                        showAttachmentMenu.toggle()
                    }
                } label: {
                    Image(systemName: showAttachmentMenu ? "xmark.circle.fill" : "plus.circle.fill")
                        .font(.system(size: 28))
                        .foregroundStyle(showAttachmentMenu ? DeeplineTheme.onSurfaceVariant(colorScheme) : DeeplineTheme.primary(colorScheme))
                        .rotationEffect(.degrees(showAttachmentMenu ? 90 : 0))
                }
                .animation(.spring(response: 0.3), value: showAttachmentMenu)

                // Text input field
                HStack(alignment: .bottom, spacing: 8) {
                    TextField("Message", text: $draft, axis: .vertical)
                        .lineLimit(1...5)
                        .focused($isInputFocused)
                        .padding(.vertical, 10)
                        .padding(.leading, 14)

                    // Emoji button
                    Button {
                        // Emoji picker placeholder
                    } label: {
                        Image(systemName: "face.smiling")
                            .font(.system(size: 22))
                            .foregroundStyle(DeeplineTheme.onSurfaceVariant(colorScheme))
                    }
                    .padding(.trailing, 10)
                    .padding(.bottom, 8)
                }
                .background(DeeplineTheme.surfaceVariant(colorScheme))
                .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))

                // Send or voice button
                Button {
                    if !draft.isEmpty {
                        onSend()
                    }
                } label: {
                    ZStack {
                        Circle()
                            .fill(draft.isEmpty ? DeeplineTheme.surfaceVariant(colorScheme) : DeeplineTheme.primary(colorScheme))
                            .frame(width: 36, height: 36)

                        Image(systemName: draft.isEmpty ? "mic.fill" : "arrow.up")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundStyle(draft.isEmpty ? DeeplineTheme.onSurfaceVariant(colorScheme) : .white)
                    }
                }
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 8)
            .background(DeeplineTheme.surface(colorScheme))
        }
    }
}

private struct AttachmentButton: View {
    let icon: String
    let label: String
    let color: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 6) {
                ZStack {
                    Circle()
                        .fill(color.opacity(0.15))
                        .frame(width: 52, height: 52)
                    Image(systemName: icon)
                        .font(.system(size: 22))
                        .foregroundStyle(color)
                }
                Text(label)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
    }
}

// MARK: - Create Group View

private struct CreateGroupView: View {
    @EnvironmentObject private var model: DeeplineAppModel
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.dismiss) private var dismiss
    @State private var groupTitle = ""
    @State private var memberIdsInput = ""
    let onCreated: (String) -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                // Group avatar
                ZStack {
                    Circle()
                        .fill(
                            LinearGradient(
                                colors: [
                                    Color.purple.opacity(0.3),
                                    Color.purple.opacity(0.1)
                                ],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .frame(width: 100, height: 100)

                    Circle()
                        .fill(Color.purple.opacity(0.2))
                        .frame(width: 80, height: 80)

                    Image(systemName: "person.3.fill")
                        .font(.system(size: 32))
                        .foregroundStyle(Color.purple)
                }
                .padding(.top, 20)

                // Group name section
                VStack(alignment: .leading, spacing: 16) {
                    HStack {
                        Image(systemName: "pencil")
                            .foregroundStyle(DeeplineTheme.primary(colorScheme))
                        Text("Group Name")
                            .font(.headline)
                            .foregroundStyle(DeeplineTheme.onSurface(colorScheme))
                    }

                    TextField("e.g., Family, Work Team", text: $groupTitle)
                        .textInputAutocapitalization(.words)
                        .padding(14)
                        .background(DeeplineTheme.surfaceVariant(colorScheme))
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
                .padding(20)
                .background(DeeplineTheme.surface(colorScheme))
                .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))

                // Member IDs section
                VStack(alignment: .leading, spacing: 16) {
                    HStack {
                        Image(systemName: "person.badge.plus")
                            .foregroundStyle(DeeplineTheme.primary(colorScheme))
                        Text("Member User IDs")
                            .font(.headline)
                            .foregroundStyle(DeeplineTheme.onSurface(colorScheme))
                    }

                    TextField("Comma-separated user IDs", text: $memberIdsInput, axis: .vertical)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .lineLimit(2...4)
                        .padding(14)
                        .background(DeeplineTheme.surfaceVariant(colorScheme))
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))

                    Text("Enter user IDs separated by commas. You can get user IDs from invite codes.")
                        .font(.caption)
                        .foregroundStyle(DeeplineTheme.onSurfaceVariant(colorScheme))
                }
                .padding(20)
                .background(DeeplineTheme.surface(colorScheme))
                .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))

                // Create button
                Button {
                    Task {
                        let memberIds = memberIdsInput
                            .split(separator: ",")
                            .map { $0.trimmingCharacters(in: .whitespaces) }
                            .filter { !$0.isEmpty }
                        if let conversationId = await model.createGroup(title: groupTitle, memberUserIds: memberIds) {
                            onCreated(conversationId)
                        }
                    }
                } label: {
                    HStack {
                        Image(systemName: "person.3.fill")
                        Text("Create Group")
                    }
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                    .background(groupTitle.isEmpty ? DeeplineTheme.surfaceVariant(colorScheme) : DeeplineTheme.primary(colorScheme))
                    .foregroundStyle(groupTitle.isEmpty ? DeeplineTheme.onSurfaceVariant(colorScheme) : .white)
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
                .disabled(groupTitle.isEmpty)

                // Encryption info
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        Image(systemName: "lock.shield.fill")
                            .foregroundStyle(.green)
                        Text("Group Encryption")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(DeeplineTheme.onSurface(colorScheme))
                    }

                    Text("Groups use MLS protocol for end-to-end encryption. Currently using placeholder codec for development.")
                        .font(.caption)
                        .foregroundStyle(DeeplineTheme.onSurfaceVariant(colorScheme))
                }
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(DeeplineTheme.surfaceVariant(colorScheme).opacity(0.5))
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
            .padding(20)
        }
        .background(DeeplineTheme.background(colorScheme).ignoresSafeArea())
        .navigationTitle("Create Group")
        .navigationBarTitleDisplayMode(.inline)
    }
}

// MARK: - Group Info View

private struct GroupInfoView: View {
    @EnvironmentObject private var model: DeeplineAppModel
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.dismiss) private var dismiss
    let conversationId: String
    @State private var editingTitle = false
    @State private var newTitle = ""

    private var title: String {
        model.chatTitle(for: conversationId)
    }

    private var members: [GroupMember] {
        model.currentGroupMembers(for: conversationId)
    }

    private var myRole: GroupRole? {
        model.myRole(in: conversationId)
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                // Group header
                VStack(spacing: 16) {
                    ZStack {
                        Circle()
                            .fill(
                                LinearGradient(
                                    colors: [Color.purple.opacity(0.3), Color.purple.opacity(0.1)],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                            )
                            .frame(width: 90, height: 90)

                        Image(systemName: "person.3.fill")
                            .font(.system(size: 36))
                            .foregroundStyle(Color.purple)
                    }

                    if editingTitle {
                        VStack(spacing: 12) {
                            TextField("Group name", text: $newTitle)
                                .textFieldStyle(.roundedBorder)
                                .frame(maxWidth: 250)

                            HStack(spacing: 12) {
                                Button("Cancel") {
                                    editingTitle = false
                                    newTitle = title
                                }
                                .foregroundStyle(DeeplineTheme.onSurfaceVariant(colorScheme))

                                Button("Save") {
                                    Task {
                                        _ = await model.updateGroupTitle(conversationId: conversationId, title: newTitle)
                                        editingTitle = false
                                    }
                                }
                                .foregroundStyle(DeeplineTheme.primary(colorScheme))
                            }
                        }
                    } else {
                        HStack(spacing: 8) {
                            Text(title)
                                .font(.title2.weight(.bold))
                                .foregroundStyle(DeeplineTheme.onSurface(colorScheme))

                            if myRole == .OWNER || myRole == .ADMIN {
                                Button {
                                    newTitle = title
                                    editingTitle = true
                                } label: {
                                    Image(systemName: "pencil.circle.fill")
                                        .font(.system(size: 20))
                                        .foregroundStyle(DeeplineTheme.primary(colorScheme))
                                }
                            }
                        }
                    }

                    HStack(spacing: 6) {
                        Image(systemName: "lock.fill")
                            .font(.system(size: 12))
                            .foregroundStyle(DeeplineTheme.success(colorScheme))
                        Text("\(members.count) members • encrypted")
                            .font(.subheadline)
                            .foregroundStyle(DeeplineTheme.primary(colorScheme))
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 24)
                .background(DeeplineTheme.surface(colorScheme))
                .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))

                // Members section
                VStack(alignment: .leading, spacing: 16) {
                    Text("MEMBERS")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(DeeplineTheme.onSurfaceVariant(colorScheme))
                        .padding(.leading, 4)

                    ForEach(members) { member in
                        MemberRow(
                            member: member,
                            myRole: myRole,
                            onRemove: {
                                Task {
                                    _ = await model.removeGroupMembers(conversationId: conversationId, userIds: [member.userId])
                                }
                            }
                        )
                    }
                }

                // Leave group button
                Button {
                    Task {
                        if await model.leaveGroup(conversationId: conversationId) {
                            dismiss()
                        }
                    }
                } label: {
                    HStack {
                        Image(systemName: "rectangle.portrait.and.arrow.right")
                        Text("Leave Group")
                    }
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                    .background(Color.red)
                    .foregroundStyle(.white)
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
                .padding(.top, 16)
            }
            .padding(20)
        }
        .background(DeeplineTheme.background(colorScheme).ignoresSafeArea())
        .navigationTitle("Group Info")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            await model.loadGroupMembers(conversationId: conversationId)
        }
    }
}

private struct MemberRow: View {
    @Environment(\.colorScheme) private var colorScheme
    let member: GroupMember
    let myRole: GroupRole?
    let onRemove: () -> Void

    private var canRemove: Bool {
        guard let myRole else { return false }
        if member.role == .OWNER { return false }
        if myRole == .OWNER { return true }
        if myRole == .ADMIN && member.role == .MEMBER { return true }
        return false
    }

    var body: some View {
        HStack(spacing: 12) {
            // Avatar
            Circle()
                .fill(DeeplineTheme.primary(colorScheme).opacity(0.2))
                .frame(width: 44, height: 44)
                .overlay(
                    Text(member.userId.suffix(2).uppercased())
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(DeeplineTheme.primary(colorScheme))
                )

            VStack(alignment: .leading, spacing: 4) {
                Text("User \(member.userId.suffix(4))")
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(DeeplineTheme.onSurface(colorScheme))

                HStack(spacing: 6) {
                    Text(member.role.rawValue)
                        .font(.caption2.weight(.semibold))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(roleColor.opacity(0.15))
                        .foregroundStyle(roleColor)
                        .clipShape(Capsule())
                }
            }

            Spacer()

            if canRemove {
                Button {
                    onRemove()
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 22))
                        .foregroundStyle(.red.opacity(0.8))
                }
            }
        }
        .padding(16)
        .background(DeeplineTheme.surface(colorScheme))
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private var roleColor: Color {
        switch member.role {
        case .OWNER:
            return .blue
        case .ADMIN:
            return .purple
        case .MEMBER:
            return .gray
        }
    }
}
