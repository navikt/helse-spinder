package no.nav.helse.spinder

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDate
import java.time.LocalDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
data class BehandlingOK(
    val originalSøknad:Søknad,
    val vedtak:Vedtak,
    val avklarteVerdier:AvklarteVerdier

)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AvklarteVerdier(
    val medlemsskap:Medlemsskap
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Medlemsskap(
    val vurderingstidspunkt: LocalDateTime
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Søknad(
    val id: String,
    val aktorId: String,
    val type: String,
    val fom: LocalDate,
    val tom: LocalDate
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