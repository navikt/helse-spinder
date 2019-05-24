package no.nav.helse.spinder

import arrow.core.Either
import com.fasterxml.jackson.databind.JsonNode
import io.prometheus.client.Counter
import no.nav.helse.Environment
import no.nav.helse.oppslag.InfotrygdBeregningsgrunnlagOppslag
import no.nav.helse.oppslag.StsRestClient
import no.nav.helse.spinder.db.SpinderDb
import no.nav.helse.streams.StreamConsumer
import no.nav.helse.streams.Topics.VEDTAK_SYKEPENGER
import no.nav.helse.streams.consumeTopic
import no.nav.helse.streams.defaultObjectMapper
import no.nav.helse.streams.streamConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.errors.LogAndFailExceptionHandler
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*

class SpinderStream(val env: Environment, private val appId : String, private val db: SpinderDb) {

    private val consumer: StreamConsumer

    private val log = LoggerFactory.getLogger(SpinderStream::class.java.name)

    init {
        val streamConfig = if ("true" == env.plainTextKafka) streamConfigPlainTextKafka() else streamConfig(
            appId, env.bootstrapServersUrl,
            env.kafkaUsername to env.kafkaPassword,
            env.navTruststorePath to env.navTruststorePassword
        )
        consumer = StreamConsumer(appId, KafkaStreams(topology(), streamConfig), env.httpPort)
    }

    private fun streamConfigPlainTextKafka(): Properties = Properties().apply {
        put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, env.bootstrapServersUrl)
        put(StreamsConfig.APPLICATION_ID_CONFIG, appId)
        put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
        put(
            StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG,
            LogAndFailExceptionHandler::class.java
        )
    }

    private fun skalSjekkes(behandlingOK: BehandlingOK) : Boolean {
        val tidligst = LocalDateTime.now().minusWeeks(3)
        val senest = LocalDateTime.now().minusDays(3)
        val behandlet = behandlingOK.avklarteVerdier.medlemsskap.vurderingstidspunkt
        return (behandlet >= tidligst && behandlet <= senest)
    }

    private fun topology(): Topology {
        val builder = StreamsBuilder()
        builder.consumeTopic(VEDTAK_SYKEPENGER)
            .foreach { _, value ->
                value.deserializeSpaSykepengeVedtak().bimap({
                    //matcheCounter.labels(SPA_VEDTAK_DESERIALISERINGSFEIL).inc()
                    log.error("SpaSykepengeVedtak deserialiserings feil", it)
                }, { behandlingOk ->
                    db.lagreNyttSpaVedtak(behandlingOk)

                })
            }
        return builder.build()
    }

    private fun JsonNode.deserializeSpaSykepengeVedtak(): Either<Exception, BehandlingOK> =
        try {
            Either.Right(defaultObjectMapper.treeToValue(this, BehandlingOK::class.java))
        } catch (e: Exception) {
            Either.Left(e)
        }



    fun start() {
        consumer.start()
    }

    fun stop() {
        consumer.stop()
    }
}
