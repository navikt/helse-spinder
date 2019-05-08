package no.nav.helse.spinder

import arrow.core.Either
import com.fasterxml.jackson.module.kotlin.readValue
import io.prometheus.client.CollectorRegistry
import no.nav.helse.oppslag.InfotrygdBeregningsgrunnlag
import no.nav.helse.streams.defaultObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class VedtakSammenliknerTest {

    private val log = LoggerFactory.getLogger(VedtakSammenliknerTest::class.java.name)

    @Test
    fun testMatch() {
        val infotrygd: InfotrygdBeregningsgrunnlag = defaultObjectMapper.readValue(
            InfotrygdBeregningsgrunnlag::class.java.classLoader.getResourceAsStream("infotrygd_oppslag_1_match.json")
        )

        val behandling: BehandlingOK = defaultObjectMapper.readValue(
            Vedtak::class.java.classLoader.getResourceAsStream("vedtak_1.json")
        )

        val sammenlikningsResultat = sammenliknVedtak(infotrygd, behandling)
        doLog(sammenlikningsResultat, behandling)

        assertTrue(sammenlikningsResultat.isRight())
    }

    @Test
    fun testMatchSelvOmFlereArbeidsforholdIInfotrygd() {
        val infotrygd: InfotrygdBeregningsgrunnlag = defaultObjectMapper.readValue(
            InfotrygdBeregningsgrunnlag::class.java.classLoader.getResourceAsStream("infotrygd_oppslag_1_match_men_fordelt_paa_to_arbeidsforhold.json")
        )

        val behandling: BehandlingOK = defaultObjectMapper.readValue(
            Vedtak::class.java.classLoader.getResourceAsStream("vedtak_1.json")
        )

        val sammenlikningsResultat = sammenliknVedtak(infotrygd, behandling)
        doLog(sammenlikningsResultat, behandling)

        assertTrue(sammenlikningsResultat.isRight())
    }

    @Test
    fun testSlåSammenInfotrygdVedtak() {
        val infotrygd: InfotrygdBeregningsgrunnlag = defaultObjectMapper.readValue(
            InfotrygdBeregningsgrunnlag::class.java.classLoader.getResourceAsStream("infotrygd_oppslag_1_ulike_perioder_ellers_match.json")
        )
        val vedtaksListe = infotrygd.sykepengerListe.first().vedtakListe
        assertTrue(vedtaksListe.size > 1) // testen gir ikke så mye mening ellers

        val behandling: BehandlingOK = defaultObjectMapper.readValue(
            Vedtak::class.java.classLoader.getResourceAsStream("vedtak_1.json")
        )

        val infotrygdVedtak = (slåSammenInfotrygdVedtak(vedtaksListe) as Either.Right).b
        assertEquals(behandling.vedtak.perioder.first().fom, infotrygdVedtak.anvistPeriode.fom)
        assertEquals(behandling.vedtak.perioder.first().tom, infotrygdVedtak.anvistPeriode.tom)
    }

    @Test
    fun testMatchMedUlikPeriodeFordeling() {
        val infotrygd: InfotrygdBeregningsgrunnlag = defaultObjectMapper.readValue(
            InfotrygdBeregningsgrunnlag::class.java.classLoader.getResourceAsStream("infotrygd_oppslag_1_ulike_perioder_ellers_match.json")
        )

        val behandling: BehandlingOK = defaultObjectMapper.readValue(
            Vedtak::class.java.classLoader.getResourceAsStream("vedtak_1.json")
        )

        val sammenlikningsResultat = sammenliknVedtak(infotrygd, behandling)
        doLog(sammenlikningsResultat, behandling)

        assertTrue(sammenlikningsResultat.isRight())
    }

    @Test
    fun testFeilFomTom() {
        val infotrygd: InfotrygdBeregningsgrunnlag = defaultObjectMapper.readValue(
            InfotrygdBeregningsgrunnlag::class.java.classLoader.getResourceAsStream("infotrygd_oppslag_1_mismatchFomTom.json")
        )

        val behandling: BehandlingOK = defaultObjectMapper.readValue(
            Vedtak::class.java.classLoader.getResourceAsStream("vedtak_1.json")
        )

        val initCountMatcherIkke = meteredErrorCount(SammenlikningsFeilÅrsak.VEDTAK_FOR_PERIODENE_MATCHER_IKKE)
        val initCountUlikFOM = meteredErrorCount(SammenlikningsFeilÅrsak.PERIODE_ULIK_FOM)
        val initCountUlikTOM = meteredErrorCount(SammenlikningsFeilÅrsak.PERIODE_ULIK_TOM)

        val sammenlikningsResultat = sammenliknVedtak(infotrygd, behandling)
        doLog(sammenlikningsResultat, behandling)

        assertTrue(sammenlikningsResultat.isLeft())
        assertEquals(
            SammenlikningsFeilÅrsak.VEDTAK_FOR_PERIODENE_MATCHER_IKKE,
            (sammenlikningsResultat as Either.Left).a.feilArsaksType
        )
        assertEquals(
            SammenlikningsFeilÅrsak.VEDTAK_FOR_PERIODE_MATCHER_IKKE,
            (sammenlikningsResultat as Either.Left).a.underFeil.first().feilArsaksType
        )
        assertEquals(
            SammenlikningsFeilÅrsak.PERIODE_ULIK_FOM,
            (sammenlikningsResultat as Either.Left).a.underFeil.first().underFeil.first().feilArsaksType
        )
        assertEquals(
            SammenlikningsFeilÅrsak.PERIODE_ULIK_TOM,
            (sammenlikningsResultat as Either.Left).a.underFeil.first().underFeil[1].feilArsaksType
        )

        assertEquals(
            initCountMatcherIkke + 1.0,
            meteredErrorCount(SammenlikningsFeilÅrsak.VEDTAK_FOR_PERIODENE_MATCHER_IKKE)
        )
        assertEquals(initCountUlikFOM + 1.0, meteredErrorCount(SammenlikningsFeilÅrsak.PERIODE_ULIK_FOM))
        assertEquals(initCountUlikTOM + 1.0, meteredErrorCount(SammenlikningsFeilÅrsak.PERIODE_ULIK_TOM))
    }

    @Test
    fun testFeilDagsats() {
        val initCountMatcherIkke = meteredErrorCount(SammenlikningsFeilÅrsak.VEDTAK_FOR_PERIODENE_MATCHER_IKKE)
        val initCountUlikDagsats = meteredErrorCount(SammenlikningsFeilÅrsak.PERIODE_ULIK_DAGSATS)

        val infotrygd: InfotrygdBeregningsgrunnlag = defaultObjectMapper.readValue(
            InfotrygdBeregningsgrunnlag::class.java.classLoader.getResourceAsStream("infotrygd_oppslag_1_mismatch1.json")
        )

        val behandling: BehandlingOK = defaultObjectMapper.readValue(
            Vedtak::class.java.classLoader.getResourceAsStream("vedtak_1.json")
        )

        val sammenlikningsResultat = sammenliknVedtak(infotrygd, behandling)
        doLog(sammenlikningsResultat, behandling)

        assertTrue(sammenlikningsResultat.isLeft())
        assertEquals(
            SammenlikningsFeilÅrsak.VEDTAK_FOR_PERIODENE_MATCHER_IKKE,
            (sammenlikningsResultat as Either.Left).a.feilArsaksType
        )
        assertEquals(
            SammenlikningsFeilÅrsak.VEDTAK_FOR_PERIODE_MATCHER_IKKE,
            (sammenlikningsResultat as Either.Left).a.underFeil.first().feilArsaksType
        )
        assertEquals(
            SammenlikningsFeilÅrsak.PERIODE_ULIK_DAGSATS,
            (sammenlikningsResultat as Either.Left).a.underFeil.first().underFeil.first().feilArsaksType
        )

        assertEquals(
            initCountMatcherIkke + 1.0,
            meteredErrorCount(SammenlikningsFeilÅrsak.VEDTAK_FOR_PERIODENE_MATCHER_IKKE)
        )
        assertEquals(initCountUlikDagsats + 1.0, meteredErrorCount(SammenlikningsFeilÅrsak.PERIODE_ULIK_DAGSATS))
    }

    @Test
    fun testTomtResultatFraInfotrygd() {
        val infotrygd: InfotrygdBeregningsgrunnlag = defaultObjectMapper.readValue(
            InfotrygdBeregningsgrunnlag::class.java.classLoader.getResourceAsStream("infotrygd_oppslag_tomt_resultat.json")
        )

        val behandling: BehandlingOK = defaultObjectMapper.readValue(
            Vedtak::class.java.classLoader.getResourceAsStream("vedtak_1.json")
        )

        val sammenlikningsResultat = sammenliknVedtak(infotrygd, behandling)
        doLog(sammenlikningsResultat, behandling)

        assertTrue(sammenlikningsResultat.isLeft())
        assertEquals(
            SammenlikningsFeilÅrsak.INFOTRYGD_MANGLER_VEDTAK,
            (sammenlikningsResultat as Either.Left).a.feilArsaksType
        )
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


    private fun doLog(
        resultat: Either<VedtaksSammenlikningsFeil, VedtaksSammenlikningsMatch>,
        behandlingOk: BehandlingOK
    ) {
        resultat.bimap({
            log.info("VedtaksSammenlikningsFeil for søknadId=${behandlingOk.originalSøknad.id}: ${it}")
        }, {
            log.info("VedtaksSammenlikningsMatch for søknadId=${behandlingOk.originalSøknad.id}: ${it}")
        })
    }

    fun meteredErrorCount(årsak: SammenlikningsFeilÅrsak) =
        meteredValueFor_Name_LabelName_LabelValue("spinder_match_failures_totals", "feiltype", årsak.toString())

    fun meteredValueFor_Name_LabelName_LabelValue(name: String, labelName: String, labelValue: String): Double {
        val elems = CollectorRegistry.defaultRegistry.metricFamilySamples().toList().filter {
            it.name == name
        }
        if (elems.size == 0) return 0.0

        val met = elems.first().samples.filter {
            it.labelNames.contains(labelName) && it.labelValues.contains(labelValue)
        }
        if (met.size == 0) return 0.0

        assertEquals(1, met.size)
        return met.first().value
    }

}
