package no.nav.helse.spinder

import com.fasterxml.jackson.module.kotlin.readValue
import io.prometheus.client.Counter
import no.nav.helse.Environment
import no.nav.helse.oppslag.InfotrygdBeregningsgrunnlagOppslag
import no.nav.helse.oppslag.StsRestClient
import no.nav.helse.spinder.db.SpinderDb
import no.nav.helse.streams.defaultObjectMapper
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

class SpinderMatcher(val db: SpinderDb, val env: Environment) : Runnable {

    private val stsClient = StsRestClient(baseUrl = env.stsRestUrl, username = env.username, password = env.password)

    private val infotrygd = InfotrygdBeregningsgrunnlagOppslag(env.sparkelBaseUrl, stsClient)

    private val log = LoggerFactory.getLogger(SpinderMatcher::class.java.name)

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

    private val minimumWaitMS = 3000

    override fun run() {
        log.info("Starting SpinderMatcher")
        while (true) {
            db.hentIkkeSammenliknedeVedtakEldreEnn(
                ikkeFør = LocalDateTime.now().minusHours(env.ventTimerFørMatcheForsøk),
                ikkeEtter = LocalDateTime.now().minusDays(env.maksAlderPåSpaVedtakSomSkalSjekkesIDager))
                .forEach { dbRec ->
                    val behandlingOk: BehandlingOK = defaultObjectMapper.readValue(dbRec.spaVedtak)

                    hentInfotrygdGrunnlagForBehanding(behandlingOk).bimap({
                        matcheCounter.labels(INFOTRYGD_OPPSLAGSFEIL).inc()
                        log.error(
                            "Feil ved hentInfotrygdGrunnlagForBehanding for søknadId=${behandlingOk.originalSøknad.id}",
                            it
                        )
                        db.oppdaterMed(dbRec, SammenlikningsFeilÅrsak.INFOTRYGD_MANGLER_VEDTAK.toString(), INFOTRYGD_OPPSLAGSFEIL)
                    }, { infotrygdBeregningsgrunnlag ->
                        sammenliknVedtak(infotrygdBeregningsgrunnlag, behandlingOk).bimap({
                            matcheCounter.labels(if (it.feilArsaksType == SammenlikningsFeilÅrsak.INFOTRYGD_MANGLER_VEDTAK) INGENDATA else MISMATCH)
                                .inc()
                            log.info("VedtaksSammenlikningsFeil for søknadId=${behandlingOk.originalSøknad.id}: $it")
                            db.oppdaterMed(dbRec, it.feilArsaksType.toString(), "$it")
                        }, {
                            matcheCounter.labels(MATCH).inc()
                            log.info("VedtaksSammenlikningsMatch for søknadId=${behandlingOk.originalSøknad.id}: $it")
                            db.oppdaterMed(dbRec, MATCH, MATCH)
                        })
                    })
                }
            log.debug("Sleeping for ${env.ventMinutterMellomHvertNyeForsøk} minutes...")
            Thread.sleep(env.ventMinutterMellomHvertNyeForsøk * 60 * 1000 + minimumWaitMS)
        }
    }


    private fun hentInfotrygdGrunnlagForBehanding(behandling: BehandlingOK) =
        infotrygd.hentInfotrygdBeregningsgrunnlag(
            behandling.originalSøknad.aktorId,
            behandling.originalSøknad.fom,
            behandling.originalSøknad.tom
        )
}