plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  androidTarget()
  jvm("server")
  iosArm64()
  iosSimulatorArm64()

  jvmToolchain(17)

  sourceSets {
    commonMain.dependencies {
      implementation(libs.kotlinx.coroutines.core)
      implementation(libs.kotlinx.serialization.json)
    }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
    }
  }
}

android {
  namespace = "dev.hyo.deepline.shared"
  compileSdk = 35

  defaultConfig {
    minSdk = 26
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}
