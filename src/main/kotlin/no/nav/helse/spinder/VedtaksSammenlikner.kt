package no.nav.helse.spinder

import arrow.core.Either
import io.prometheus.client.Counter
import no.nav.helse.oppslag.*
import java.math.BigDecimal
import java.math.RoundingMode

val arbeidsdagerPrÅr = 260

private val sammenlikningsFeilCounter = Counter.build()
    .name("spinder_match_failures_totals")
    .labelNames("feiltype")
    .help("antall feil under sammenlikning, fordelt på type")
    .register()

fun sammenliknVedtak(infotrygdVedtak: InfotrygdBeregningsgrunnlag, spabehandling:BehandlingOK) : Either<VedtaksSammenlikningsFeil, VedtaksSammenlikningsMatch> {

    if (infotrygdVedtak.sykepengerListe == null || infotrygdVedtak.sykepengerListe.isEmpty()) return Either.Left(vedtakSammenlikningsFeilMetered(
        SammenlikningsFeilÅrsak.INFOTRYGD_MANGLER_VEDTAK, "sykepengeliste er tom"
    ))

    if (infotrygdVedtak.sykepengerListe.size != 1) return Either.Left(vedtakSammenlikningsFeilMetered(
        SammenlikningsFeilÅrsak.INFOTRYGD_FLERE_SYKEPENGELISTER, "fikk flere sykepengelister. Vet ikke hvordan håndtere dette"
    ))

    return sammenliknVedtaksPerioder(infotrygdVedtak.sykepengerListe[0], spabehandling.vedtak)
}

fun sammenliknVedtaksPerioder(infotrygd: PeriodeYtelse, spa: Vedtak) : Either<VedtaksSammenlikningsFeil, VedtaksSammenlikningsMatch> {
    if (infotrygd.arbeidsforholdListe == null || infotrygd.arbeidsforholdListe.size != 1) return Either.Left(vedtakSammenlikningsFeilMetered(
        SammenlikningsFeilÅrsak.INFOTRYGD_IKKE_ETT_ARBEIDSFORHOLD, "fikk flere eller ingen arbeidsforhold: ${infotrygd.arbeidsforholdListe?.size}"
    ))

    årsinntekt(infotrygd.arbeidsforholdListe.first()).bimap({ feilmelding ->
        return Either.Left(vedtakSammenlikningsFeilMetered(SammenlikningsFeilÅrsak.FORSTÅR_IKKE_DATA, feilmelding))
    }, {årsinntekt ->
        val infotrygdDagsats100 = dagsatsAvÅrsinntekt(årsinntekt)

        val infotrygdTilUtbetaling =
            infotrygd.vedtakListe.filter { it.utbetalingsgrad != null && it.utbetalingsgrad > 0 }
        val spaTilUtbetaling = spa.perioder.filter { it.dagsats > 0 }

        if (infotrygdTilUtbetaling.size != spaTilUtbetaling.size) {
            return Either.Left(
                vedtakSammenlikningsFeilMetered(
                    SammenlikningsFeilÅrsak.ULIKT_ANTALL_PERIODER_TIL_UTBETALING, // TODO: Kan fortsatt være interessant å sammenlikne innholdet
                    "infotrygd har ${infotrygdTilUtbetaling.size} perioder mens spa har ${spaTilUtbetaling.size}",
                    grunnlag = "infotrygd (100% dagsats = $infotrygdDagsats100): " + infotrygdTilUtbetaling.toString() + ", spa: " + spaTilUtbetaling.toString()
                )
            )
        }

        val periodeResultater = infotrygdTilUtbetaling.zip(spaTilUtbetaling)
            .map { sammenliknPeriode(PeriodeSammenlikningsGrunnlag(it.first, infotrygdDagsats100, it.second)) }

        if (periodeResultater.any { it.isLeft() }) {
            return Either.Left(vedtakSammenlikningsFeilMetered(SammenlikningsFeilÅrsak.VEDTAK_FOR_PERIODENE_MATCHER_IKKE,
                "en eller flere av perioden mather ikke",
                periodeResultater.filter { it.isLeft() }.map {
                    (it as Either.Left).a
                })
            )
        }

        return Either.Right(VedtaksSammenlikningsMatch())
    })
    return Either.Left(VedtaksSammenlikningsFeil(SammenlikningsFeilÅrsak.LOGIKK_FEIL, "shouldnt be here (sammenliknVedtaksPerioder)")) // FIXME
}

data class PeriodeSammenlikningsGrunnlag(
    val infotrygdVedtak: InfotrygdVedtak,
    val infotrygdDagsats100: Long,
    val spaVedtak: VedtaksPeriode
)

fun sammenliknPeriode(grunnlag: PeriodeSammenlikningsGrunnlag) : Either<VedtaksSammenlikningsFeil, VedtaksSammenlikningsMatch> {
    val infotrygdGradertDagsats = graderDagsats(grunnlag.infotrygdDagsats100, grunnlag.infotrygdVedtak.utbetalingsgrad)
    val underFeil: MutableList<VedtaksSammenlikningsFeil> = mutableListOf()

    if (infotrygdGradertDagsats != grunnlag.spaVedtak.dagsats) underFeil.add(vedtakSammenlikningsFeilMetered(SammenlikningsFeilÅrsak.PERIODE_ULIK_DAGSATS,
        "$infotrygdGradertDagsats ({${grunnlag.infotrygdVedtak.utbetalingsgrad} av ${grunnlag.infotrygdDagsats100}) != ${grunnlag.spaVedtak.dagsats}"))

    if (grunnlag.infotrygdVedtak.anvistPeriode.fom != grunnlag.spaVedtak.fom) underFeil.add(vedtakSammenlikningsFeilMetered(SammenlikningsFeilÅrsak.PERIODE_ULIK_FOM,
        "${grunnlag.infotrygdVedtak.anvistPeriode.fom} != ${grunnlag.spaVedtak.fom}"))

    if (grunnlag.infotrygdVedtak.anvistPeriode.tom != grunnlag.spaVedtak.tom) underFeil.add(vedtakSammenlikningsFeilMetered(SammenlikningsFeilÅrsak.PERIODE_ULIK_TOM,
        "${grunnlag.infotrygdVedtak.anvistPeriode.tom} != ${grunnlag.spaVedtak.tom}"))

    if (!underFeil.isEmpty()) {
        return Either.left(VedtaksSammenlikningsFeil(SammenlikningsFeilÅrsak.VEDTAK_FOR_PERIODE_MATCHER_IKKE, "vedtak for periode matcher ikke", underFeil, grunnlag.toString()))
    }

    return Either.right(VedtaksSammenlikningsMatch())
}

class VedtaksSammenlikningsMatch(

)

private fun vedtakSammenlikningsFeilMetered(
    feilArsaksType: SammenlikningsFeilÅrsak,
    feilBeskrivelse: String,
    underFeil: List<VedtaksSammenlikningsFeil> = emptyList(),
    grunnlag: String = ""
) : VedtaksSammenlikningsFeil {
    sammenlikningsFeilCounter.labels(feilArsaksType.toString()).inc()
    return VedtaksSammenlikningsFeil(feilArsaksType, feilBeskrivelse, underFeil, grunnlag)
}

data class VedtaksSammenlikningsFeil(
    val feilArsaksType: SammenlikningsFeilÅrsak,
    val feilBeskrivelse: String,
    val underFeil: List<VedtaksSammenlikningsFeil> = emptyList(),
    val grunnlag: String = ""
)

enum class SammenlikningsFeilÅrsak {
    INFOTRYGD_MANGLER_VEDTAK,
    INFOTRYGD_FLERE_SYKEPENGELISTER,
    INFOTRYGD_IKKE_ETT_ARBEIDSFORHOLD,
    ULIKT_ANTALL_PERIODER_TIL_UTBETALING,
    VEDTAK_FOR_PERIODENE_MATCHER_IKKE,
    VEDTAK_FOR_PERIODE_MATCHER_IKKE,
    PERIODE_ULIK_FOM,
    PERIODE_ULIK_TOM,
    PERIODE_ULIK_DAGSATS,
    FORSTÅR_IKKE_DATA,
    LOGIKK_FEIL
}

fun graderDagsats(dagsats: Long, utbetalingsgrad: Int) = BigDecimal.valueOf(dagsats * utbetalingsgrad)
    .divide(BigDecimal(100), 0, RoundingMode.HALF_UP)
    .longValueExact()

fun dagsatsAvÅrsinntekt(årsinntekt : Long) = BigDecimal.valueOf(årsinntekt)
    .divide(BigDecimal(arbeidsdagerPrÅr), 0, RoundingMode.HALF_UP)
    .longValueExact()

fun årsinntekt(arbeidsforhold: Arbeidsforhold) : Either<String, Long> =
    when (arbeidsforhold.inntektsPeriode.value) {
        InntektsPeriodeVerdi.M -> Either.Right(arbeidsforhold.inntektForPerioden * 12L)
        InntektsPeriodeVerdi.D -> Either.Right(arbeidsforhold.inntektForPerioden * arbeidsdagerPrÅr.toLong())
        InntektsPeriodeVerdi.Å -> Either.Right(arbeidsforhold.inntektForPerioden.toLong())
        InntektsPeriodeVerdi.U -> Either.Right(arbeidsforhold.inntektForPerioden * 52L) // ??
        InntektsPeriodeVerdi.F -> Either.Right(arbeidsforhold.inntektForPerioden * 26L) // ??
        else -> Either.Left("Fikk inntektsperiode: ${arbeidsforhold.inntektsPeriode.value}")
    }