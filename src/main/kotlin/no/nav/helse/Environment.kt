package no.nav.helse

data class Environment(
        val username: String = getEnvVar("SERVICEUSER_USERNAME"),
        val password: String = getEnvVar("SERVICEUSER_PASSWORD"),
        val kafkaUsername: String? = getEnvVarOptional("SERVICEUSER_USERNAME"),
        val kafkaPassword: String? = getEnvVarOptional("SERVICEUSER_PASSWORD"),
        val bootstrapServersUrl: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS"),
        val navTruststorePath: String? = getEnvVarOptional("NAV_TRUSTSTORE_PATH"),
        val navTruststorePassword: String? = getEnvVarOptional("NAV_TRUSTSTORE_PASSWORD"),
        val sparkelBaseUrl: String = getEnvVar("SPARKEL_BASE_URL", "http://sparkel"),
        val stsRestUrl: String = getEnvVar("SECURITY_TOKEN_SERVICE_REST_URL"),
        val plainTextKafka: String? = getEnvVarOptional("PLAIN_TEXT_KAFKA"),
        val httpPort:Int = 7000,
        val resetStreamOnly : Boolean = if (getEnvVarOptional("RESET_STREAM_ONLY", "false") == "true") true else false,
        val dbUrl: String = getEnvVar("DATABASE_URL", "jdbc:postgresql://localhost:5432/helse-spinder"),
        var dbUseVault: Boolean = if (getEnvVarOptional("DATABASE_USE_VAULT", "false") == "true") true else false,
        // if dbUseVault
        var dbVaultMountpath: String = getEnvVar("DATABASE_VAULT_MOUNTPATH", "postgresql/preprod"),
        var dbVaultRole: String = getEnvVar("DATABASE_VAULT_ROLE", "helse-spinder-admin"),
        // else
        var dbUsername: String = getEnvVar("DATABASE_USERNAME", "spinder"),
        val dbPassword: String = getEnvVar("DATABASE_PASSWORD", "spinder"),
        val ventTimerFørMatcheForsøk: Long = 24*3,
        val ventMinutterMellomHvertNyeForsøk: Long = 60,
        val maksAlderPåSpaVedtakSomSkalSjekkesIDager: Long = 21
        // end if (dbUseVault)

)

private fun getEnvVar(varName: String, defaultValue: String? = null) =
        getEnvVarOptional(varName, defaultValue) ?: throw Exception("mangler verdi for $varName")

private fun getEnvVarOptional(varName: String, defaultValue: String? = null) =
        System.getenv(varName) ?: defaultValue


