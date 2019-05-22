package no.nav.helse.spinder

import no.nav.helse.Environment
import no.nav.helse.spinder.db.SpinderDb
import no.nav.helse.spinder.db.makeDatasource
import no.nav.helse.spinder.db.migrate
import org.slf4j.LoggerFactory


private val log = LoggerFactory.getLogger("Spinder")

private val kafkaAppId = "spinder-avstemmer-1"

fun main() {
    App(Environment()).start()
}

class App(val env: Environment) {
    private var spinder:SpinderStream? = null
    fun start() {
        if (env.resetStreamOnly) {
            log.info("SpinderStreamResetter starter.")
            val spinderResetter = SpinderStreamResetter(env, kafkaAppId)
            spinderResetter.run()
            log.info("SpinderStreamResetter kjørt.")
            Thread {
                while (true) {
                    log.info("SpinderStreamResetter - mission accomplished (Fjern env.RESET_STREAM_ONLY og start appen på nytt)")
                    Thread.sleep(30000);
                }
            }.start()
        } else {
            val dataSource = makeDatasource(env)
            migrate(dataSource, env)
            val db = SpinderDb(dataSource)
            log.info("Spinder starter.")
            spinder = SpinderStream(env, kafkaAppId, db)
            log.info("The Spinder is open for E-Match")
            spinder?.start()
            Thread(SpinderMatcher(db, env)).start()
        }
    }
    fun stop() {
        spinder?.stop()
    }
}

