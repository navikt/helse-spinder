package no.nav.helse.spinder

import no.nav.helse.Environment
import org.slf4j.LoggerFactory


private val log = LoggerFactory.getLogger("Spinder")

private val kafkaAppId = "spinder-avstemmer-1"

fun main() {
    val env = Environment()
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
        log.info("Spinder starter.")
        val spinder = SpinderStream(env, kafkaAppId)
        log.info("The Spinder is open for E-Match")
        spinder.start()
    }
}
