package dev.hyo.deepline.server

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
  val config = DeeplineServerConfig.fromEnvironment()

  embeddedServer(Netty, port = config.port, host = config.host) {
    deeplineModule(config)
  }.start(wait = true)
}
