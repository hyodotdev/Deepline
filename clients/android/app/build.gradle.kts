import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "dev.hyo.deepline.android"
  compileSdk = 35

  defaultConfig {
    applicationId = "dev.hyo.deepline.android"
    minSdk = 26
    targetSdk = 35
    versionCode = 1
    versionName = "0.1.0-alpha"
  }

  buildFeatures {
    compose = true
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

kotlin {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_17)
  }
}

dependencies {
  implementation(project(":shared"))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.ktor.client.content.negotiation)
  implementation(libs.ktor.client.core)
  implementation(libs.ktor.client.okhttp)
  implementation(libs.ktor.client.websockets)
  implementation(libs.ktor.serialization.kotlinx.json)

  // Firebase Cloud Messaging (optional - requires google-services.json)
  implementation(libs.firebase.messaging)

  debugImplementation(libs.androidx.compose.ui.tooling)
}
