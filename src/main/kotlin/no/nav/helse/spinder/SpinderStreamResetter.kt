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
import no.nav.helse.Environment
import no.nav.helse.streams.Topics.VEDTAK_SYKEPENGER
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.config.SaslConfigs
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*


class SpinderStreamResetter(val env: Environment, private val appId : String) {

    private val log = LoggerFactory.getLogger(SpinderStreamResetter::class.java.name)

    fun run() {
        log.info("creating nais http-check endpoints")
        naisHttpChecks().start()
        log.info("created nais http-check endpoints")

        val prop = Properties()
        prop.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, env.bootstrapServersUrl)
        prop.put(
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
            "org.apache.kafka.common.serialization.StringDeserializer"
        )
        prop.put(
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            "org.apache.kafka.common.serialization.StringDeserializer"
        )
        prop.put(ConsumerConfig.GROUP_ID_CONFIG, appId);
        prop.put(ConsumerConfig.CLIENT_ID_CONFIG, "simple")
        prop.put("enable.auto.commit", "false");

        if ("true" != env.plainTextKafka) {
            prop.put("security.protocol", "SASL_SSL")
            prop.put("ssl.truststore.password", env.navTruststorePassword)
            prop.put("ssl.truststore.location", env.navTruststorePath)
            prop.put(SaslConfigs.SASL_JAAS_CONFIG,
                "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"${env.kafkaUsername}\" password=\"${env.kafkaPassword}\";")
            prop.put(SaslConfigs.SASL_MECHANISM, "PLAIN")
        }

        prop.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

        log.info("Creating consumer")
        val consumer = KafkaConsumer<String, String>(prop)
        log.info("subscribing to topic ${VEDTAK_SYKEPENGER.name}")
        consumer.subscribe(listOf(VEDTAK_SYKEPENGER.name));

        try {
            log.info("polling...")
            consumer.poll(Duration.ofSeconds(10))
        } catch (e:Exception) {
            log.warn("exception on poll", e)
        }
        log.info("seekToBeginning...")
        consumer.seekToBeginning(consumer.assignment())
        log.info("polling...")
        consumer.poll(Duration.ofSeconds(10))
        log.info("calling commitSync...")
        val offsets = consumer.assignment().associateBy({ it }, { OffsetAndMetadata (0) } )
        consumer.commitSync(offsets)
        log.info("closing consumer...")
        consumer.close()
        log.info("Done")
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
                        TextFormat.write004(this, CollectorRegistry.defaultRegistry.filteredMetricFamilySamples(names))
                    }
                }
            }
        }

}