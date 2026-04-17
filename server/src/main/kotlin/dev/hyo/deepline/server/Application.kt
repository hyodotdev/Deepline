package dev.hyo.deepline.server

import dev.hyo.deepline.server.blob.LocalFilesystemBlobStore
import dev.hyo.deepline.server.push.PushServiceFactory
import dev.hyo.deepline.server.routes.installAccountRoutes
import dev.hyo.deepline.server.routes.installAttachmentRoutes
import dev.hyo.deepline.server.routes.installConversationRoutes
import dev.hyo.deepline.server.routes.installHealthRoutes
import dev.hyo.deepline.server.routes.RateLimitExceededException
import dev.hyo.deepline.server.rate.createRateLimiterRuntime
import dev.hyo.deepline.server.store.createStoreRuntime
import dev.hyo.deepline.server.ws.ConversationSocketHub
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import java.nio.file.Paths
import kotlinx.serialization.json.Json

fun Application.deeplineModule(
  config: DeeplineServerConfig = DeeplineServerConfig.fromEnvironment(),
) {
  val storeRuntime = createStoreRuntime(config)
  val store = storeRuntime.store
  val rateLimiterRuntime = createRateLimiterRuntime(config)
  val rateLimiter = rateLimiterRuntime.rateLimiter
  val blobStore = LocalFilesystemBlobStore(Paths.get(config.blobStorageRoot))
  val socketHub = ConversationSocketHub()
  val pushService = PushServiceFactory.create()

  monitor.subscribe(ApplicationStopped) {
    storeRuntime.close()
    rateLimiterRuntime.close()
  }

  install(CallLogging)
  install(ContentNegotiation) {
    json(
      Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        prettyPrint = true
      },
    )
  }
  install(CORS) {
    anyHost()
    allowHeader("content-type")
    allowHeader("authorization")
  }
  install(WebSockets)
  install(StatusPages) {
    exception<RateLimitExceededException> { call, cause ->
      call.response.headers.append("Retry-After", cause.retryAfterSeconds.toString())
      call.respondText(
        text = cause.message ?: "Rate limit exceeded",
        status = io.ktor.http.HttpStatusCode.TooManyRequests,
      )
    }
    exception<IllegalStateException> { call, cause ->
      call.respondText(text = cause.message ?: "Invalid state", status = io.ktor.http.HttpStatusCode.BadRequest)
    }
    exception<IllegalArgumentException> { call, cause ->
      call.respondText(text = cause.message ?: "Invalid request", status = io.ktor.http.HttpStatusCode.BadRequest)
    }
  }

  routing {
    installHealthRoutes(config)
    installAccountRoutes(config, store, rateLimiter)
    installConversationRoutes(config, store, socketHub, rateLimiter, pushService)
    installAttachmentRoutes(config, store, blobStore, rateLimiter)
  }
}
