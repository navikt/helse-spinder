package no.nav.helse.spinder.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.helse.Environment
import no.nav.vault.jdbc.hikaricp.HikariCPVaultUtil
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.ClassicConfiguration


fun migrate(dataSource: HikariDataSource, env : Environment) {
    val conf = ClassicConfiguration()

    conf.dataSource = dataSource
    conf.setLocationsAsStrings("db/migrations")
    conf.isBaselineOnMigrate = true
    if (env.dbUseVault) {
        conf.initSql = env.dbVaultRole.let {
            if (inneholderKunAlfanumeriskOgStrek(it)) "set role '" + it + "'" else
                throw Exception("Skumle tegn i dbVaultRole. Tør ikke kjøre set role med denne")
        }
    }

    val flyway = Flyway(conf)
    flyway.migrate()
}

fun inneholderKunAlfanumeriskOgStrek(s: String) =
        Regex("^[a-zA-Z0-9_-]+$").matches(s)

fun makeDatasource(env : Environment) : HikariDataSource {
    val config = HikariConfig()
    config.jdbcUrl = env.dbUrl
    config.minimumIdle = 0
    config.maxLifetime = 30001
    config.maximumPoolSize = 2
    config.connectionTimeout = 250
    config.idleTimeout = 10001
    return if (env.dbUseVault) {
        val ds = HikariCPVaultUtil.createHikariDataSourceWithVaultIntegration(config, env.dbVaultMountpath, env.dbVaultRole)
        if (ds == null) throw Exception("vault integrated datasource is null") else ds
    } else {
        config.username = env.dbUsername
        config.password = env.dbPassword
        HikariDataSource(config)
    }
}