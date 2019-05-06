package no.nav.helse.spinder

import arrow.core.Either
import com.fasterxml.jackson.databind.JsonNode
import io.prometheus.client.Counter
import no.nav.helse.Environment
import no.nav.helse.oppslag.InfotrygdBeregningsgrunnlagOppslag
import no.nav.helse.oppslag.StsRestClient
import no.nav.helse.streams.*
import no.nav.helse.streams.Topics.VEDTAK_SYKEPENGER
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.errors.LogAndFailExceptionHandler
import org.slf4j.LoggerFactory
import java.util.*

class SpinderStream(val env: Environment) {

    private val stsClient = StsRestClient(baseUrl = env.stsRestUrl, username = env.username, password = env.password)

    private val appId = "spinder-avstemmer-1"

    private val consumer: StreamConsumer

    private val log = LoggerFactory.getLogger(SpinderStream::class.java.name)

    private val MATCH = "match";
    private val MISMATCH = "mismatch";
    private val INGENDATA = "ingendata";
    private val SPA_VEDTAK_DESERIALISERINGSFEIL = "spa_vedtak_deserialiseringsfeil";
    private val INFOTRYGD_OPPSLAGSFEIL = "infotrygd_oppslagsfeil";

    private val matcheCounter = Counter.build()
        .name("spinder_match_attempts_totals")
        .labelNames("resultat")
        .help("antall matcheforsøk gjort, fordelt på $MATCH, $MISMATCH, $INGENDATA (i.e: fant ikke vedtak i infotrygd), $SPA_VEDTAK_DESERIALISERINGSFEIL, $INFOTRYGD_OPPSLAGSFEIL")
        .register()

    private val infotrygd = InfotrygdBeregningsgrunnlagOppslag(env.sparkelBaseUrl, stsClient)

    init {
        val streamConfig = if ("true" == env.plainTextKafka) streamConfigPlainTextKafka() else streamConfig(
            appId, env.bootstrapServersUrl,
            env.kafkaUsername to env.kafkaPassword,
            env.navTruststorePath to env.navTruststorePassword
        )
        consumer = StreamConsumer(appId, KafkaStreams(topology(), streamConfig))
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

    private fun topology(): Topology {
        val builder = StreamsBuilder()
        builder.consumeTopic(VEDTAK_SYKEPENGER)
            .foreach { key, value ->
                value.deserializeSpaSykepengeVedtak().bimap({
                    matcheCounter.labels(SPA_VEDTAK_DESERIALISERINGSFEIL).inc()
                    log.error("SpaSykepengeVedtak deserialiserings feil", it)
                }, { behandlingOk ->
                    hentInfotrygdGrunnlagForBehanding(behandlingOk).bimap({
                        matcheCounter.labels(INFOTRYGD_OPPSLAGSFEIL).inc()
                        log.error(
                            "Feil ved hentInfotrygdGrunnlagForBehanding for søknadId=${behandlingOk.originalSøknad.id}",
                            it
                        )
                    }, { infotrygdBeregningsgrunnlag ->
                        sammenliknVedtak(infotrygdBeregningsgrunnlag, behandlingOk).bimap({
                            matcheCounter.labels(if (it.feilArsaksType == SammenlikningsFeilÅrsak.INFOTRYGD_MANGLER_VEDTAK) INGENDATA else MISMATCH).inc()
                            log.info("VedtaksSammenlikningsFeil for søknadId=${behandlingOk.originalSøknad.id}: ${it}")
                        }, {
                            matcheCounter.labels(MATCH).inc()
                            log.info("VedtaksSammenlikningsMatch for søknadId=${behandlingOk.originalSøknad.id}: ${it}")
                        })
                    })
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


    private fun hentInfotrygdGrunnlagForBehanding(behandling: BehandlingOK) =
        infotrygd.hentInfotrygdBeregningsgrunnlag(
            behandling.originalSøknad.aktorId,
            behandling.originalSøknad.fom,
            behandling.originalSøknad.tom
        )

    fun start() {
        consumer.start()
    }

    fun stop() {
        consumer.stop()
    }
}
