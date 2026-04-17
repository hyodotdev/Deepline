package dev.hyo.deepline.server.routes

import dev.hyo.deepline.server.DeeplineServerConfig
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
  val status: String,
  val environment: String,
  val strictCryptoEnforcement: Boolean,
  val storeMode: String,
  val rateLimiterMode: String,
)

fun Route.installHealthRoutes(config: DeeplineServerConfig) {
  get("/healthz") {
    call.respond(
      HealthResponse(
        status = "ok",
        environment = config.environment,
        strictCryptoEnforcement = config.strictCryptoEnforcement,
        storeMode = config.storeMode.name,
        rateLimiterMode = config.rateLimiterMode.name,
      ),
    )
  }
}
