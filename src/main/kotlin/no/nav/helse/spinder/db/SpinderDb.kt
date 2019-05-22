package no.nav.helse.spinder.db

import no.nav.helse.spinder.BehandlingOK
import no.nav.helse.spinder.SammenlikningsFeilÅrsak
import no.nav.helse.streams.defaultObjectMapper
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime
import javax.sql.DataSource

class SpinderDb(private val dataSource: DataSource) {

    private val log = LoggerFactory.getLogger(SpinderDb::class.java.name)

    fun lagreNyttSpaVedtak(behandling: BehandlingOK) {
        log.debug("lagrer vedtak for søknad ${behandling.originalSøknad.id}")
        var con: Connection? = null
        var pst: PreparedStatement? = null
        try {
            con = dataSource.connection
            pst = con.prepareStatement("insert into spabehandling (soknad_sendt_nav, spa_vurderingstidspunkt, spa_vedtak) values (?, ?, ?)")
            pst.setTimestamp(1, Timestamp.valueOf(behandling.originalSøknad.sendtNav))
            pst.setTimestamp(2, Timestamp.valueOf(behandling.avklarteVerdier.medlemsskap.vurderingstidspunkt))
            pst.setString(3, defaultObjectMapper.writeValueAsString(
                behandling
            ))
            val rowCount = pst.executeUpdate()
            log.trace("lagreNyttSpaVedtak: rowcount=$rowCount")
        } finally {
            pst?.close()
            con?.close()
        }
    }

    fun hentIkkeSammenliknedeVedtakEldreEnn(ikkeFør : LocalDateTime, ikkeEtter : LocalDateTime) : List<SpaBehandlingRecord> {
        log.debug("henter IkkeSammenliknedeVedtak eldre enn $ikkeFør")
        var con: Connection? = null
        var pst: PreparedStatement? = null
        var rs: ResultSet? = null
        try {
            con = dataSource.connection
            pst = con.prepareStatement("select * from spabehandling where spa_vurderingstidspunkt < ? and spa_vurderingstidspunkt > ?" +
                    " and (avstemming_resultat is null or avstemming_resultat = '${SammenlikningsFeilÅrsak.INFOTRYGD_MANGLER_VEDTAK}') " +
                    " and (neste_forsoek_ikke_foer is null or neste_forsoek_ikke_foer < localtimestamp)")
            pst.setTimestamp(1, Timestamp.valueOf(ikkeFør))
            pst.setTimestamp(2, Timestamp.valueOf(ikkeEtter))
            rs = pst.executeQuery()
            val mutableResult = mutableListOf<SpaBehandlingRecord>()
            while (rs.next()) {
                mutableResult.add(recOfResultSet(rs))
            }
            return mutableResult.toList()
        } finally {
            rs?.close()
            pst?.close()
            con?.close()
        }
    }

    fun oppdaterMed(behandling: SpaBehandlingRecord, avstemmingResultat: String, avstemmingDetaljer: String) {
        log.debug("oppdaterer behandling med internId = ${behandling.id}")
        var con: Connection? = null
        var pst: PreparedStatement? = null
        try {
            con = dataSource.connection
            pst = con.prepareStatement("update spabehandling set avstemming_resultat = ?, avstemming_detaljer = ?, endret = localtimestamp " +
                    " where id = ?")
            pst.setString(1, avstemmingResultat)
            pst.setString(2, avstemmingDetaljer)
            pst.setLong(3, behandling.id)
            val rowCount = pst.executeUpdate()
            log.trace("oppdaterMed: rowcount=$rowCount")
        } finally {
            pst?.close()
            con?.close()
        }
    }


    private fun recOfResultSet(rs: ResultSet) =
        SpaBehandlingRecord(
            id = rs.getLong("id"),
            soknadSendtNav = rs.getTimestamp("soknad_sendt_nav").toLocalDateTime(),
            spaVurderingsTidspunkt = rs.getTimestamp("spa_vurderingstidspunkt").toLocalDateTime(),
            spaVedtak = rs.getString("spa_vedtak"),
            opprettet = rs.getTimestamp("opprettet").toLocalDateTime(),
            endret = rs.getTimestamp("endret")?.toLocalDateTime(),
            nesteForsoekIkkeFoer = rs.getTimestamp("neste_forsoek_ikke_foer")?.toLocalDateTime(),
            avstemmingResultat = rs.getString("avstemming_resultat"),
            avstemmingDetaljer = rs.getString("avstemming_detaljer")
        )
}

data class SpaBehandlingRecord(
    val id:Long,
    val soknadSendtNav: LocalDateTime,
    val spaVurderingsTidspunkt: LocalDateTime,
    val spaVedtak: String,
    val opprettet: LocalDateTime,
    val endret: LocalDateTime?,
    val nesteForsoekIkkeFoer: LocalDateTime?,
    val avstemmingResultat: String?,
    val avstemmingDetaljer: String?
)