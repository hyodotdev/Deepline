package dev.hyo.deepline.server.routes

import dev.hyo.deepline.server.DeeplineServerConfig
import dev.hyo.deepline.server.rate.RateLimiter
import dev.hyo.deepline.server.store.DeeplineStore
import dev.hyo.deepline.shared.model.AddContactByInviteCodeCommand
import dev.hyo.deepline.shared.model.CreateInviteCodeCommand
import dev.hyo.deepline.shared.model.PublishPreKeyBundleCommand
import dev.hyo.deepline.shared.model.RegisterDeviceCommand
import dev.hyo.deepline.shared.model.RegisterPushTokenCommand
import dev.hyo.deepline.shared.model.RegisterUserCommand
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.installAccountRoutes(
  config: DeeplineServerConfig,
  store: DeeplineStore,
  rateLimiter: RateLimiter,
) {
  route("/v1") {
    post("/users") {
      val command = call.receive<RegisterUserCommand>()
      call.respond(store.registerUser(command))
    }

    post("/devices") {
      val command = call.receive<RegisterDeviceCommand>()
      rejectDemoBundleIfNeeded(config, command.deviceBundle)
      call.respond(store.registerDevice(command))
    }

    post("/prekeys") {
      val command = call.receive<PublishPreKeyBundleCommand>()
      rejectDemoBundleIfNeeded(config, command.deviceBundle)
      call.respond(store.publishPreKeyBundle(command))
    }

    get("/prekeys/{userId}/{deviceId}") {
      val userId = requireNotNull(call.parameters["userId"]) {
        "userId is required"
      }
      val deviceId = requireNotNull(call.parameters["deviceId"]) {
        "deviceId is required"
      }

      call.respond(store.getDeviceBundle(userId, deviceId))
    }

    post("/invites") {
      val command = call.receive<CreateInviteCodeCommand>()
      enforceRateLimit(
        call = call,
        rateLimiter = rateLimiter,
        scope = "create_invite",
        subject = rateLimitSubject(call, command.ownerUserId),
        limit = config.inviteCreateLimitPerHour,
        windowSeconds = 60 * 60,
      )
      call.respond(store.createInviteCode(command))
    }

    post("/contacts/by-invite") {
      val command = call.receive<AddContactByInviteCodeCommand>()
      call.respond(store.addContactByInviteCode(command))
    }

    // Push token registration
    post("/devices/{deviceId}/push-token") {
      val deviceId = requireNotNull(call.parameters["deviceId"]) {
        "deviceId is required"
      }
      val command = call.receive<RegisterPushTokenCommand>()
      require(command.deviceId == deviceId) {
        "deviceId in path must match deviceId in body"
      }
      call.respond(store.registerPushToken(command))
    }

    delete("/devices/{deviceId}/push-token") {
      val deviceId = requireNotNull(call.parameters["deviceId"]) {
        "deviceId is required"
      }
      val userId = call.request.queryParameters["userId"]
        ?: throw IllegalArgumentException("userId query parameter is required")
      store.deletePushToken(userId, deviceId)
      call.respond(HttpStatusCode.NoContent)
    }
  }
}
