package no.nav.helse.spinder

import no.nav.helse.Either
import no.nav.helse.oppslag.Arbeidsforhold
import no.nav.helse.oppslag.InfotrygdBeregningsgrunnlag
import no.nav.helse.oppslag.InntektsPeriodeVerdi
import no.nav.helse.oppslag.PeriodeYtelse
import java.math.BigDecimal
import java.math.RoundingMode

val arbeidsdagerPrÅr = 260

fun sammenliknVedtak(infotrygdVedtak: InfotrygdBeregningsgrunnlag, spabehandling:BehandlingOK) : Either<VedtaksSammenlikningsFeil, VedtaksSammenlikningsMatch> {

    if (infotrygdVedtak.sykepengerListe == null || infotrygdVedtak.sykepengerListe.isEmpty()) return Either.Left(VedtaksSammenlikningsFeil(
        SammenlikningsFeilÅrsak.INFOTRYGD_MANGLER_VEDTAK, "sykepengeliste er tom"
    ))

    if (infotrygdVedtak.sykepengerListe.size != 1) return Either.Left(VedtaksSammenlikningsFeil(
        SammenlikningsFeilÅrsak.INFOTRYGD_FLERE_SYKEPENGELISTER, "fikk flere sykepengelister. Vet ikke hvordan håndtere dette"
    ))

    return sammenliknVedtaksPerioder(infotrygdVedtak.sykepengerListe[0], spabehandling.vedtak)

/*    assertEquals(infotrygd.sykepengerListe[0].vedtakListe[0].anvistPeriode.fom, behandling.vedtak.perioder[0].fom)
    assertEquals(infotrygd.sykepengerListe[0].vedtakListe[0].anvistPeriode.tom, behandling.vedtak.perioder[0].tom)
    assertEquals(dagsatsAvÅrsinntekt(årsinntekt(infotrygd.sykepengerListe[0].arbeidsforholdListe[0])), behandling.vedtak.perioder[0].dagsats)*/


}

fun sammenliknVedtaksPerioder(infotrygd: PeriodeYtelse, spa: Vedtak) : Either<VedtaksSammenlikningsFeil, VedtaksSammenlikningsMatch> {
    val infotrygdTilUtbetaling = infotrygd.vedtakListe.filter { it.utbetalingsgrad != null && it.utbetalingsgrad > 0 }
    val spaTilUtbetaling = spa.perioder.filter { it.dagsats > 0 }

    if (infotrygdTilUtbetaling.size != spaTilUtbetaling.size) {
        return Either.Left(VedtaksSammenlikningsFeil(
            SammenlikningsFeilÅrsak.ULIKT_ANTALL_PERIODER_TIL_UTBETALING, // TODO: Kan fortsatt være interessant å sammenlikne innholdet
            "infotrygd har ${infotrygdTilUtbetaling.size} perioder mens spa har ${spaTilUtbetaling.size}"))
    }

    infotrygdTilUtbetaling.zip(spaTilUtbetaling).forEach {

    }

    throw Exception("TODO")
}

class VedtaksSammenlikningsMatch(

)

data class VedtaksSammenlikningsFeil(
    val feilArsaksType: SammenlikningsFeilÅrsak,
    val feilBeskrivelse: String
)

enum class SammenlikningsFeilÅrsak {
    INFOTRYGD_MANGLER_VEDTAK,
    INFOTRYGD_FLERE_SYKEPENGELISTER,
    ULIKT_ANTALL_PERIODER_TIL_UTBETALING,
}

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