package no.nav.helse.spinder

import no.nav.helse.Environment
import org.slf4j.LoggerFactory


private val log = LoggerFactory.getLogger("Spinder")

fun main() {
    log.info("Spinder starter.")
    val spinder = SpinderStream(Environment())
    log.info("The Spinder is open for E-Match")
    spinder.start()
}
