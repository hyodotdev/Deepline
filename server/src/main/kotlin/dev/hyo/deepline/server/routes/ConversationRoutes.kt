package dev.hyo.deepline.server.routes

import dev.hyo.deepline.server.DeeplineServerConfig
import dev.hyo.deepline.server.push.PushService
import dev.hyo.deepline.server.rate.RateLimiter
import dev.hyo.deepline.server.store.DeeplineStore
import dev.hyo.deepline.server.ws.ConversationSocketHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import dev.hyo.deepline.shared.model.AddGroupMembersCommand
import dev.hyo.deepline.shared.model.CreateConversationCommand
import dev.hyo.deepline.shared.model.EncryptedEnvelope
import dev.hyo.deepline.shared.model.LeaveConversationCommand
import dev.hyo.deepline.shared.model.MarkMessageReadCommand
import dev.hyo.deepline.shared.model.RemoveGroupMembersCommand
import dev.hyo.deepline.shared.model.SendEncryptedMessageCommand
import dev.hyo.deepline.shared.model.UpdateConversationSettingsCommand
import dev.hyo.deepline.shared.model.UpdateMemberRoleCommand
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import kotlinx.serialization.json.Json

fun Route.installConversationRoutes(
  config: DeeplineServerConfig,
  store: DeeplineStore,
  socketHub: ConversationSocketHub,
  rateLimiter: RateLimiter,
  pushService: PushService,
) {
  val json = Json { ignoreUnknownKeys = true }

  route("/v1") {
    post("/conversations") {
      val command = call.receive<CreateConversationCommand>()
      rejectUnreadyGroupProtocolIfNeeded(config, command.protocolType)

      val conversation = store.createConversation(command)
      call.respond(conversation)
    }

    get("/users/{userId}/conversations") {
      val userId = requireNotNull(call.parameters["userId"]) {
        "userId is required"
      }

      call.respond(store.listConversations(userId))
    }

    get("/conversations/{conversationId}") {
      val conversationId = requireNotNull(call.parameters["conversationId"]) {
        "conversationId is required"
      }

      call.respond(store.getConversation(conversationId))
    }

    // Group member management routes
    get("/conversations/{conversationId}/members") {
      val conversationId = requireNotNull(call.parameters["conversationId"]) {
        "conversationId is required"
      }
      val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
      val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 50).coerceIn(1, 100)

      call.respond(store.listConversationMembers(conversationId, offset, limit))
    }

    post("/conversations/{conversationId}/members") {
      val conversationId = requireNotNull(call.parameters["conversationId"]) {
        "conversationId is required"
      }
      val command = call.receive<AddGroupMembersCommand>()
      require(command.conversationId == conversationId) {
        "conversationId in path must match command body"
      }

      enforceRateLimit(
        call = call,
        rateLimiter = rateLimiter,
        scope = "add_members",
        subject = rateLimitSubject(call, command.requestingUserId),
        limit = config.messageWriteLimitPerMinute,
        windowSeconds = 60,
      )

      call.respond(store.addConversationMembers(command))
    }

    delete("/conversations/{conversationId}/members") {
      val conversationId = requireNotNull(call.parameters["conversationId"]) {
        "conversationId is required"
      }
      val command = call.receive<RemoveGroupMembersCommand>()
      require(command.conversationId == conversationId) {
        "conversationId in path must match command body"
      }

      call.respond(store.removeConversationMembers(command))
    }

    patch("/conversations/{conversationId}/members/{userId}") {
      val conversationId = requireNotNull(call.parameters["conversationId"]) {
        "conversationId is required"
      }
      val userId = requireNotNull(call.parameters["userId"]) {
        "userId is required"
      }
      val command = call.receive<UpdateMemberRoleCommand>()
      require(command.conversationId == conversationId) {
        "conversationId in path must match command body"
      }
      require(command.targetUserId == userId) {
        "userId in path must match command body"
      }

      call.respond(store.updateMemberRole(command))
    }

    post("/conversations/{conversationId}/leave") {
      val conversationId = requireNotNull(call.parameters["conversationId"]) {
        "conversationId is required"
      }
      val command = call.receive<LeaveConversationCommand>()
      require(command.conversationId == conversationId) {
        "conversationId in path must match command body"
      }

      call.respond(store.leaveConversation(command))
    }

    patch("/conversations/{conversationId}") {
      val conversationId = requireNotNull(call.parameters["conversationId"]) {
        "conversationId is required"
      }
      val command = call.receive<UpdateConversationSettingsCommand>()
      require(command.conversationId == conversationId) {
        "conversationId in path must match command body"
      }

      call.respond(store.updateConversationSettings(command))
    }

    get("/conversations/{conversationId}/messages") {
      val conversationId = requireNotNull(call.parameters["conversationId"]) {
        "conversationId is required"
      }

      call.respond(store.listMessages(conversationId))
    }

    post("/messages") {
      val command = call.receive<SendEncryptedMessageCommand>()
      enforceRateLimit(
        call = call,
        rateLimiter = rateLimiter,
        scope = "send_message",
        subject = rateLimitSubject(call, command.envelope.senderUserId),
        limit = config.messageWriteLimitPerMinute,
        windowSeconds = 60,
      )
      rejectDemoEnvelopeIfNeeded(config, command.envelope)
      val saved = store.appendMessage(command)
      socketHub.publish(saved)

      // Send push notifications to offline recipients (fire and forget)
      if (pushService.isAvailable()) {
        CoroutineScope(Dispatchers.IO).launch {
          try {
            val tokens = store.getPushTokensForConversation(saved.conversationId)
              .filter { it.userId != saved.senderUserId } // Don't notify sender
            if (tokens.isNotEmpty()) {
              pushService.notifyNewMessage(saved, tokens)
            }
          } catch (_: Exception) {
            // Push notification failures should not affect message delivery
          }
        }
      }

      call.respond(saved)
    }

    post("/messages/read-receipts") {
      val command = call.receive<MarkMessageReadCommand>()
      call.respond(store.markMessageRead(command))
    }

    get("/messages/{messageId}/receipts") {
      val messageId = requireNotNull(call.parameters["messageId"]) {
        "messageId is required"
      }

      call.respond(store.listReceipts(messageId))
    }

    get("/messages/{messageId}/receipts/aggregated") {
      val messageId = requireNotNull(call.parameters["messageId"]) {
        "messageId is required"
      }

      call.respond(store.getAggregatedReadReceipt(messageId))
    }

    get("/users/{userId}/mentions") {
      val userId = requireNotNull(call.parameters["userId"]) {
        "userId is required"
      }
      val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
      val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 50).coerceIn(1, 100)

      call.respond(store.listMentionsForUser(userId, offset, limit))
    }

    webSocket("/ws/conversations/{conversationId}") {
      val conversationId = requireNotNull(call.parameters["conversationId"]) {
        "conversationId is required"
      }

      val subscription = socketHub.subscribe(conversationId)
      try {
        for (message in subscription) {
          outgoing.send(Frame.Text(json.encodeToString(EncryptedEnvelope.serializer(), message)))
        }
      } finally {
        socketHub.unsubscribe(conversationId, subscription)
      }
    }
  }
}
