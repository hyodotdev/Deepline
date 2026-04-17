plugins {
  application
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  jvmToolchain(17)
}

application {
  mainClass.set("dev.hyo.deepline.server.MainKt")
}

dependencies {
  implementation(project(":shared"))
  implementation(libs.flyway.core)
  implementation(libs.flyway.database.postgresql)
  implementation(libs.hikari.cp)
  implementation(libs.jedis)
  implementation(libs.ktor.serialization.kotlinx.json)
  implementation(libs.ktor.server.auth)
  implementation(libs.ktor.server.call.logging)
  implementation(libs.ktor.server.content.negotiation)
  implementation(libs.ktor.server.core)
  implementation(libs.ktor.server.cors)
  implementation(libs.ktor.server.netty)
  implementation(libs.ktor.server.status.pages)
  implementation(libs.ktor.server.websockets)
  implementation(libs.ktor.client.core)
  implementation(libs.ktor.client.okhttp)
  implementation(libs.ktor.client.content.negotiation)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logback.classic)
  runtimeOnly(libs.postgresql)

  testImplementation(libs.h2)
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.ktor.server.test.host)
  testImplementation(kotlin("test"))
}

tasks.test {
  useJUnitPlatform()
}
