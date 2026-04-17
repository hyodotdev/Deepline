package dev.hyo.deepline.shared.policy

enum class AppEnvironment {
  LOCAL,
  STAGING,
  PRODUCTION,
}

object ProductionPolicy {
  fun requireProductionCrypto(
    environment: AppEnvironment,
    oneToOneIsReady: Boolean,
    groupIsReady: Boolean,
  ) {
    if (environment == AppEnvironment.PRODUCTION && (!oneToOneIsReady || !groupIsReady)) {
      throw IllegalStateException(
        "Production builds must not boot until native crypto bridges are ready.",
      )
    }
  }
}
