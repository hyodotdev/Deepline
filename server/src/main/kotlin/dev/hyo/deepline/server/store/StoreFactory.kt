package dev.hyo.deepline.server.store

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import dev.hyo.deepline.server.DeeplineServerConfig
import dev.hyo.deepline.server.StoreMode
import org.flywaydb.core.Flyway

fun createStoreRuntime(config: DeeplineServerConfig): StoreRuntime {
  return when (config.storeMode) {
    StoreMode.MEMORY -> StoreRuntime(
      store = InMemoryDeeplineStore(config),
    )

    StoreMode.POSTGRES -> {
      val databaseUrl = requireNotNull(config.databaseUrl) {
        "DEEPLINE_DATABASE_URL is required in POSTGRES mode."
      }

      val hikariConfig = HikariConfig().apply {
        jdbcUrl = databaseUrl
        username = config.databaseUser
        password = config.databasePassword
        maximumPoolSize = config.databaseMaxPoolSize
        minimumIdle = minOf(2, config.databaseMaxPoolSize)
        poolName = "deepline-store"
        isAutoCommit = false
        validate()
      }

      val dataSource = HikariDataSource(hikariConfig)
      Flyway.configure()
        .dataSource(dataSource)
        .load()
        .migrate()

      StoreRuntime(
        store = JdbcDeeplineStore(dataSource, config),
        closeAction = dataSource::close,
      )
    }
  }
}
