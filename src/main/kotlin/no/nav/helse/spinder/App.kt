package no.nav.helse.spinder

import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.response.respondText
import io.ktor.response.respondTextWriter
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat
import org.slf4j.LoggerFactory


private val log = LoggerFactory.getLogger("Spinder")
private val collectorRegistry: CollectorRegistry = CollectorRegistry.defaultRegistry

fun main() {
    log.info("Spinder starter.")
    naisHttpChecks().start()
    Thread({
        while (true) {
            Thread.sleep(30000);
            log.info("Spinder still alive")
        }
    }).start()
}

private inline fun naisHttpChecks() =
    embeddedServer(Netty, 7000) {
        routing {

            get("/") {
                call.respondText("heihei", ContentType.Text.Plain)
            }

            get("/isalive") {
                call.respondText("ALIVE", ContentType.Text.Plain)
            }

            get("/isready") {
                call.respondText("READY", ContentType.Text.Plain)
            }

            get("/metrics") {
                val names = call.request.queryParameters.getAll("name[]")?.toSet() ?: emptySet()
                call.respondTextWriter(ContentType.parse(TextFormat.CONTENT_TYPE_004)) {
                    TextFormat.write004(this, collectorRegistry.filteredMetricFamilySamples(names))
                }
            }
        }
    }