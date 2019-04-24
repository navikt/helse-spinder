package no.nav.helse.spinder

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.oppslag.Arbeidsforhold
import no.nav.helse.oppslag.InfotrygdBeregningsgrunnlag
import no.nav.helse.oppslag.InntektsPeriodeVerdi
import no.nav.helse.streams.defaultObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

class TestdataTest {

    @Test
    fun sjekkAtMatcheTestData1HengerNogenlundePåGreip() {
        val infotrygd:InfotrygdBeregningsgrunnlag = defaultObjectMapper.readValue(InfotrygdBeregningsgrunnlag::class.java.
            classLoader.getResourceAsStream("infotrygd_oppslag_1_match.json"))

        val behandling:BehandlingOK = defaultObjectMapper.readValue(Vedtak::class.java.
            classLoader.getResourceAsStream("vedtak_1.json"))

        assertEquals(infotrygd.sykepengerListe[0].vedtakListe[0].anvistPeriode.fom, behandling.vedtak.perioder[0].fom)
        assertEquals(infotrygd.sykepengerListe[0].vedtakListe[0].anvistPeriode.tom, behandling.vedtak.perioder[0].tom)
        assertEquals(dagsatsAvÅrsinntekt(årsinntekt(infotrygd.sykepengerListe[0].arbeidsforholdListe[0])), behandling.vedtak.perioder[0].dagsats)
    }

    val arbeidsdagerPrÅr = 260

    fun dagsatsAvÅrsinntekt(årsinntekt : Long) = BigDecimal.valueOf(årsinntekt)
        .divide(BigDecimal(arbeidsdagerPrÅr), 0, RoundingMode.HALF_UP)
        .longValueExact()

    fun årsinntekt(arbeidsforhold: Arbeidsforhold) : Long =
        when (arbeidsforhold.inntektsPeriode.value) {
            InntektsPeriodeVerdi.M -> arbeidsforhold.inntektForPerioden * 12L
            InntektsPeriodeVerdi.D -> arbeidsforhold.inntektForPerioden * arbeidsdagerPrÅr.toLong()
            InntektsPeriodeVerdi.Å -> arbeidsforhold.inntektForPerioden.toLong()
            InntektsPeriodeVerdi.U -> arbeidsforhold.inntektForPerioden * 52L // ??
            InntektsPeriodeVerdi.F -> arbeidsforhold.inntektForPerioden * 26L // ??
            else -> throw Exception("Fikk inntektsperiode: " + arbeidsforhold.inntektsPeriode.value)
        }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class BehandlingOK(
        val originalSøknad:Søknad,
        val vedtak:Vedtak
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Søknad(
        val aktorId: String
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Vedtak(
        val perioder: List<VedtaksPeriode>
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class VedtaksPeriode(
        val fom: LocalDate,
        val tom: LocalDate,
        val dagsats: Long
    /*"fordeling" : [ {
        "mottager" : "995816598",
        "andel" : 100
      } ]*/
    )

}