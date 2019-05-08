package no.nav.helse.spinder

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.oppslag.InfotrygdBeregningsgrunnlag
import no.nav.helse.streams.defaultObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TestdataTest {

    @Test
    fun sjekkAtMatcheTestData1HengerNogenlundePåGreip() {
        val infotrygd:InfotrygdBeregningsgrunnlag = defaultObjectMapper.readValue(InfotrygdBeregningsgrunnlag::class.java.
            classLoader.getResourceAsStream("infotrygd_oppslag_1_match.json"))

        val behandling:BehandlingOK = defaultObjectMapper.readValue(Vedtak::class.java.
            classLoader.getResourceAsStream("vedtak_1.json"))

        assertEquals(infotrygd.sykepengerListe[0].vedtakListe[0].anvistPeriode.fom, behandling.vedtak.perioder[0].fom)
        assertEquals(infotrygd.sykepengerListe[0].vedtakListe[0].anvistPeriode.tom, behandling.vedtak.perioder[0].tom)
        assertEquals(dagsatsAvÅrsinntekt(årsinntekt(infotrygd.sykepengerListe[0].arbeidsforholdListe)), behandling.vedtak.perioder[0].dagsats)
    }

}