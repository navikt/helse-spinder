package no.nav.helse.spinder

import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("Spinder")

fun main() {
    log.info("Spinder starter. Heihei")
    while (true) {
        Thread.sleep(30000);
        log.info("Spinder still alive")
    }
}