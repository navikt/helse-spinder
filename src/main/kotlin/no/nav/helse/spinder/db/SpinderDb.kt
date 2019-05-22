package no.nav.helse.spinder.db

import no.nav.helse.spinder.BehandlingOK
import no.nav.helse.spinder.SammenlikningsFeilÅrsak
import no.nav.helse.streams.defaultObjectMapper
import org.slf4j.LoggerFactory
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime
import javax.sql.DataSource

class SpinderDb(private val dataSource: DataSource) {

    private val log = LoggerFactory.getLogger(SpinderDb::class.java.name)

    fun lagreNyttSpaVedtak(behandling: BehandlingOK) {
        dataSource.connection.use {
            it.prepareStatement("insert into spabehandling (soknad_sendt_nav, spa_vurderingstidspunkt, spa_vedtak) values (?, ?, ?)").use {
                it.setTimestamp(1, Timestamp.valueOf(behandling.originalSøknad.sendtNav))
                it.setTimestamp(2, Timestamp.valueOf(behandling.avklarteVerdier.medlemsskap.vurderingstidspunkt))
                it.setString(
                    3, defaultObjectMapper.writeValueAsString(
                        behandling
                    )
                )
                val rowCount = it.executeUpdate()
                log.trace("lagreNyttSpaVedtak: rowcount=$rowCount")
            }
        }
    }

    fun hentIkkeSammenliknedeVedtakEldreEnn(ikkeFør : LocalDateTime, ikkeEtter : LocalDateTime) : List<SpaBehandlingRecord> {
        dataSource.connection.use {
            it.prepareStatement("select * from spabehandling where spa_vurderingstidspunkt < ? and spa_vurderingstidspunkt > ?" +
                    " and (avstemming_resultat is null or avstemming_resultat = '${SammenlikningsFeilÅrsak.INFOTRYGD_MANGLER_VEDTAK}') " +
                    " and (neste_forsoek_ikke_foer is null or neste_forsoek_ikke_foer < localtimestamp)").use {
                it.setTimestamp(1, Timestamp.valueOf(ikkeFør))
                it.setTimestamp(2, Timestamp.valueOf(ikkeEtter))
                it.executeQuery().use {
                    val mutableResult = mutableListOf<SpaBehandlingRecord>()
                    while (it.next()) {
                        mutableResult.add(recOfResultSet(it))
                    }
                    return mutableResult.toList()
                }
            }
        }
    }

    fun oppdaterMed(behandling: SpaBehandlingRecord, avstemmingResultat: String, avstemmingDetaljer: String) {
        dataSource.connection.use {
            it.prepareStatement("update spabehandling set avstemming_resultat = ?, avstemming_detaljer = ?, endret = localtimestamp " +
                    " where id = ?").use {
                it.setString(1, avstemmingResultat)
                it.setString(2, avstemmingDetaljer)
                it.setLong(3, behandling.id)
                val rowCount = it.executeUpdate()
                log.trace("oppdaterMed: rowcount=$rowCount")
            }
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