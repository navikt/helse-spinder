package no.nav.helse.spinder

import arrow.core.Either
import io.prometheus.client.Counter
import io.prometheus.client.Histogram
import no.nav.helse.oppslag.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

val arbeidsdagerPrÅr = 260

private val sammenlikningsFeilCounter = Counter.build()
    .name("spinder_match_failures_totals")
    .labelNames("feiltype")
    .help("antall feil under sammenlikning, fordelt på type")
    .register()

private val inntektsPeriodeVerdiCounter = Counter.build()
    .name("spinder_inntektsperiodeverdi_totals")
    .labelNames("inntektsperiodeverdi")
    .help("antall forekomster i arbeidsforhold av angitt inntektsperiodeverdi")
    .register()

private val arbeidsforholdPerInntektHistogram = Histogram.build()
    .buckets(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 10.0)
    .name("arbeidsforhold_per_inntekt_sizes")
    .help("fordeling over hvor mange potensielle arbeidsforhold en inntekt har")
    .register()

fun sammenliknVedtak(
    infotrygdVedtak: InfotrygdBeregningsgrunnlag,
    spabehandling: BehandlingOK
): Either<VedtaksSammenlikningsFeil, VedtaksSammenlikningsMatch> {

    if (infotrygdVedtak.sykepengerListe.isEmpty()) return Either.Left(
        vedtakSammenlikningsFeilMetered(
            SammenlikningsFeilÅrsak.INFOTRYGD_MANGLER_VEDTAK, "sykepengeliste er tom"
        )
    )

    if (infotrygdVedtak.sykepengerListe.size != 1) return Either.Left(
        vedtakSammenlikningsFeilMetered(
            SammenlikningsFeilÅrsak.INFOTRYGD_FLERE_SYKEPENGELISTER,
            "fikk flere sykepengelister. Vet ikke hvordan håndtere dette"
        )
    )

    return sammenliknVedtaksPerioder(infotrygdVedtak.sykepengerListe[0], spabehandling.vedtak)
}

fun sammenliknVedtaksPerioder(
    infotrygd: PeriodeYtelse,
    spa: Vedtak
): Either<VedtaksSammenlikningsFeil, VedtaksSammenlikningsMatch> {
    if (infotrygd.arbeidsforholdListe.isEmpty()) return Either.Left(
        vedtakSammenlikningsFeilMetered(
            SammenlikningsFeilÅrsak.INFOTRYGD_INGEN_ARBEIDSFORHOLD,
            "fikk ingen arbeidsforhold: ${infotrygd.arbeidsforholdListe.size}"
        )
    )

    årsinntekt(infotrygd.arbeidsforholdListe).bimap({ feilmelding ->
        return Either.Left(vedtakSammenlikningsFeilMetered(SammenlikningsFeilÅrsak.FORSTÅR_IKKE_DATA, feilmelding))
    }, { årsinntekt ->

        var infotrygdTilUtbetaling =
            infotrygd.vedtakListe.filter { it.utbetalingsgrad != null && it.utbetalingsgrad > 0 }
        val spaTilUtbetaling = spa.perioder.filter { it.dagsats > 0 }

        if (infotrygdTilUtbetaling.isEmpty()) return Either.Left(
            vedtakSammenlikningsFeilMetered(
                SammenlikningsFeilÅrsak.INFOTRYGD_INGEN_UTBETALINGSPERIODER,
                "fant ingen infotrygd-utbetalingsperioder med utbetalingsgrad > 0",
                grunnlag = infotrygd.vedtakListe.toString()
            )
        )
        if (spaTilUtbetaling.isEmpty()) return Either.Left(
            vedtakSammenlikningsFeilMetered(
                SammenlikningsFeilÅrsak.SPA_INGEN_UTBETALINGSPERIODER,
                "fant ingen spa-utbetalingsperioder med dagsats > 0",
                grunnlag = spa.perioder.toString()
            )
        )

        val infotrygdDagsats100 = dagsatsAvÅrsinntekt(cap6G(årsinntekt, spa.perioder.first().fom))

        if (infotrygdTilUtbetaling.size != spaTilUtbetaling.size) {
            val sammenslåttInfotrygdVedtak = slåSammenInfotrygdVedtak(infotrygdTilUtbetaling)
            sammenslåttInfotrygdVedtak.bimap({
                return Either.left(it)
            }, {
                if (spaTilUtbetaling.size > 1) {
                    return Either.Left(
                        vedtakSammenlikningsFeilMetered(
                            SammenlikningsFeilÅrsak.ULIKT_ANTALL_PERIODER_TIL_UTBETALING, // TODO: Kan fortsatt være interessant å sammenlikne innholdet
                            "klarte å slå sammen infotrygdperioder til 1 periode, mens spa har ${spaTilUtbetaling.size} perioder",
                            grunnlag = "infotrygd (100% dagsats = $infotrygdDagsats100): " + infotrygdTilUtbetaling.toString() + ", spa: " + spaTilUtbetaling.toString()
                        ))
                }
                infotrygdTilUtbetaling = listOf(it)
            })
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
    return Either.Left(
        VedtaksSammenlikningsFeil(
            SammenlikningsFeilÅrsak.LOGIKK_FEIL,
            "shouldnt be here (sammenliknVedtaksPerioder)"
        )
    ) // FIXME
}

data class PeriodeSammenlikningsGrunnlag(
    val infotrygdVedtak: InfotrygdVedtak,
    val infotrygdDagsats100: Long,
    val spaVedtak: VedtaksPeriode
)

fun slåSammenInfotrygdVedtak(vedtaksListe : List<InfotrygdVedtak>) : Either<VedtaksSammenlikningsFeil, InfotrygdVedtak> {
    val sortertePerioder = vedtaksListe.sortedBy { it.anvistPeriode.fom }
    for (i in 0..sortertePerioder.size-1) {
        val erIkkeSistePeriode = (i < sortertePerioder.size-1)
        if (erIkkeSistePeriode && sortertePerioder[i].anvistPeriode.tom.plusDays(1) != sortertePerioder[i+1].anvistPeriode.fom) {
            return Either.left(vedtakSammenlikningsFeilMetered(SammenlikningsFeilÅrsak.INFOTRYGD_HULL_I_VEDTAKSPERIODE,
                "klarte ikke slå sammen infotrygdperioder fordi ${sortertePerioder[i+1].anvistPeriode.fom} ikke kommer rett etter ${sortertePerioder[i].anvistPeriode.tom}",
                grunnlag = vedtaksListe.toString()))
        }
        if (erIkkeSistePeriode && sortertePerioder[i].utbetalingsgrad != sortertePerioder[i+1].utbetalingsgrad) {
            return Either.left(vedtakSammenlikningsFeilMetered(SammenlikningsFeilÅrsak.INFOTRYGD_FLERE_GRADERINGER_I_VEDTAK,
                "klarte ikke slå sammen infotrygdperioder fordi utbetalingsgrad ${sortertePerioder[i+1].utbetalingsgrad} != ${sortertePerioder[i].utbetalingsgrad}",
                grunnlag = vedtaksListe.toString()))
        }
    }
    return Either.right(InfotrygdVedtak(AnvistPeriode(fom = sortertePerioder.first().anvistPeriode.fom, tom = sortertePerioder.last().anvistPeriode.tom), utbetalingsgrad = sortertePerioder.first().utbetalingsgrad))
}


fun sammenliknPeriode(grunnlag: PeriodeSammenlikningsGrunnlag): Either<VedtaksSammenlikningsFeil, VedtaksSammenlikningsMatch> {
    val infotrygdGradertDagsats = graderDagsats(grunnlag.infotrygdDagsats100, grunnlag.infotrygdVedtak.utbetalingsgrad?: 0)
    val underFeil: MutableList<VedtaksSammenlikningsFeil> = mutableListOf()



    if (infotrygdGradertDagsats != grunnlag.spaVedtak.dagsats) underFeil.add(
        vedtakSammenlikningsFeilMetered(
            SammenlikningsFeilÅrsak.PERIODE_ULIK_DAGSATS,
            "$infotrygdGradertDagsats ({${grunnlag.infotrygdVedtak.utbetalingsgrad} av ${grunnlag.infotrygdDagsats100}) != ${grunnlag.spaVedtak.dagsats}"
        )
    )

    if (grunnlag.infotrygdVedtak.anvistPeriode.fom != grunnlag.spaVedtak.fom) underFeil.add(
        vedtakSammenlikningsFeilMetered(
            SammenlikningsFeilÅrsak.PERIODE_ULIK_FOM,
            "${grunnlag.infotrygdVedtak.anvistPeriode.fom} != ${grunnlag.spaVedtak.fom}"
        )
    )

    if (grunnlag.infotrygdVedtak.anvistPeriode.tom != grunnlag.spaVedtak.tom) underFeil.add(
        vedtakSammenlikningsFeilMetered(
            SammenlikningsFeilÅrsak.PERIODE_ULIK_TOM,
            "${grunnlag.infotrygdVedtak.anvistPeriode.tom} != ${grunnlag.spaVedtak.tom}"
        )
    )

    if (!underFeil.isEmpty()) {
        return Either.left(
            VedtaksSammenlikningsFeil(
                SammenlikningsFeilÅrsak.VEDTAK_FOR_PERIODE_MATCHER_IKKE,
                "vedtak for periode matcher ikke",
                underFeil,
                grunnlag.toString()
            )
        )
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
): VedtaksSammenlikningsFeil {
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
    INFOTRYGD_HULL_I_VEDTAKSPERIODE,
    INFOTRYGD_FLERE_GRADERINGER_I_VEDTAK,
    INFOTRYGD_INGEN_ARBEIDSFORHOLD,
    INFOTRYGD_INGEN_UTBETALINGSPERIODER,
    SPA_INGEN_UTBETALINGSPERIODER,
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

fun dagsatsAvÅrsinntekt(årsinntekt: Long) = BigDecimal.valueOf(årsinntekt)
    .divide(BigDecimal(arbeidsdagerPrÅr), 0, RoundingMode.HALF_UP)
    .longValueExact()

fun cap6G(årsinntekt: Long, dato: LocalDate) : Long {
    val G6 = getGrunnbeløpForDato(dato) * 6
    return if (årsinntekt > G6) G6 else årsinntekt
}

fun årsinntekt(arbeidsforholdListe: List<Arbeidsforhold>): Either<String, Long> = // TODO: metrics for antall arbeidsforhold
    Either.right(
        arbeidsforholdListe.map { arbeidsforhold ->
            inntektsPeriodeVerdiCounter.labels(arbeidsforhold.inntektsPeriode.value.toString()).inc()
            when (arbeidsforhold.inntektsPeriode.value) {
                InntektsPeriodeVerdi.M -> arbeidsforhold.inntektForPerioden * 12L
                InntektsPeriodeVerdi.D -> arbeidsforhold.inntektForPerioden * arbeidsdagerPrÅr.toLong()
                InntektsPeriodeVerdi.Å -> arbeidsforhold.inntektForPerioden.toLong()
                InntektsPeriodeVerdi.U -> arbeidsforhold.inntektForPerioden * 52L // ??
                InntektsPeriodeVerdi.F -> arbeidsforhold.inntektForPerioden * 26L // ??
                else -> return Either.Left("Fikk inntektsperiode: ${arbeidsforhold.inntektsPeriode.value}")
            }
        }.reduce(Long::plus)
    )
