import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
  repositories {
    google()
    gradlePluginPortal()
    mavenCentral()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "deepline"

include(":shared")
include(":server")
include(":androidApp")

project(":androidApp").projectDir = file("clients/android/app")
