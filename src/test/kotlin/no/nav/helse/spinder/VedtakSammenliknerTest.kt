package no.nav.helse.spinder

import arrow.core.Either
import com.fasterxml.jackson.module.kotlin.readValue
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
            InfotrygdBeregningsgrunnlag::class.java.
                classLoader.getResourceAsStream("infotrygd_oppslag_1_match.json"))

        val behandling:BehandlingOK = defaultObjectMapper.readValue(Vedtak::class.java.
            classLoader.getResourceAsStream("vedtak_1.json"))

        val sammenlikningsResultat = sammenliknVedtak(infotrygd, behandling)
        doLog(sammenlikningsResultat, behandling)

        assertTrue(sammenlikningsResultat.isRight())
    }

    @Test
    fun testFeilFomTom() {
        val infotrygd: InfotrygdBeregningsgrunnlag = defaultObjectMapper.readValue(
            InfotrygdBeregningsgrunnlag::class.java.
                classLoader.getResourceAsStream("infotrygd_oppslag_1_mismatchFomTom.json"))

        val behandling:BehandlingOK = defaultObjectMapper.readValue(Vedtak::class.java.
            classLoader.getResourceAsStream("vedtak_1.json"))

        val sammenlikningsResultat = sammenliknVedtak(infotrygd, behandling)
        doLog(sammenlikningsResultat, behandling)

        assertTrue(sammenlikningsResultat.isLeft())
        assertEquals(SammenlikningsFeilÅrsak.VEDTAK_FOR_PERIODENE_MATCHER_IKKE, (sammenlikningsResultat as Either.Left).a.feilArsaksType)
        assertEquals(SammenlikningsFeilÅrsak.VEDTAK_FOR_PERIODE_MATCHER_IKKE, (sammenlikningsResultat as Either.Left).a.underFeil.first().feilArsaksType)
        assertEquals(SammenlikningsFeilÅrsak.PERIODE_ULIK_FOM, (sammenlikningsResultat as Either.Left).a.underFeil.first().underFeil.first().feilArsaksType)
        assertEquals(SammenlikningsFeilÅrsak.PERIODE_ULIK_TOM, (sammenlikningsResultat as Either.Left).a.underFeil.first().underFeil[1].feilArsaksType)
    }

    @Test
    fun testFeilDagsats() {
        val infotrygd: InfotrygdBeregningsgrunnlag = defaultObjectMapper.readValue(
            InfotrygdBeregningsgrunnlag::class.java.
                classLoader.getResourceAsStream("infotrygd_oppslag_1_mismatch1.json"))

        val behandling:BehandlingOK = defaultObjectMapper.readValue(Vedtak::class.java.
            classLoader.getResourceAsStream("vedtak_1.json"))

        val sammenlikningsResultat = sammenliknVedtak(infotrygd, behandling)
        doLog(sammenlikningsResultat, behandling)

        assertTrue(sammenlikningsResultat.isLeft())
        assertEquals(SammenlikningsFeilÅrsak.VEDTAK_FOR_PERIODENE_MATCHER_IKKE, (sammenlikningsResultat as Either.Left).a.feilArsaksType)
        assertEquals(SammenlikningsFeilÅrsak.VEDTAK_FOR_PERIODE_MATCHER_IKKE, (sammenlikningsResultat as Either.Left).a.underFeil.first().feilArsaksType)
        assertEquals(SammenlikningsFeilÅrsak.PERIODE_ULIK_DAGSATS, (sammenlikningsResultat as Either.Left).a.underFeil.first().underFeil.first().feilArsaksType)
    }

    @Test
    fun testTomtResultatFraInfotrygd() {
        val infotrygd: InfotrygdBeregningsgrunnlag = defaultObjectMapper.readValue(
            InfotrygdBeregningsgrunnlag::class.java.
                classLoader.getResourceAsStream("infotrygd_oppslag_tomt_resultat.json"))

        val behandling:BehandlingOK = defaultObjectMapper.readValue(Vedtak::class.java.
            classLoader.getResourceAsStream("vedtak_1.json"))

        val sammenlikningsResultat = sammenliknVedtak(infotrygd, behandling)
        doLog(sammenlikningsResultat, behandling)

        assertTrue(sammenlikningsResultat.isLeft())
        assertEquals(SammenlikningsFeilÅrsak.INFOTRYGD_MANGLER_VEDTAK, (sammenlikningsResultat as Either.Left).a.feilArsaksType)
    }

    private fun doLog(resultat : Either<VedtaksSammenlikningsFeil, VedtaksSammenlikningsMatch>, behandlingOk: BehandlingOK) {
        resultat.bimap({
            log.info("VedtaksSammenlikningsFeil for søknadId=${behandlingOk.originalSøknad.id}: ${it}")
        }, {
            log.info("VedtaksSammenlikningsMatch for søknadId=${behandlingOk.originalSøknad.id}: ${it}")
        })
    }


}