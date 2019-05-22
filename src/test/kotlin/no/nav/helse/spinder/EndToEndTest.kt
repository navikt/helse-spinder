package no.nav.helse.spinder

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import no.nav.common.JAASCredential
import no.nav.common.KafkaEnvironment
import no.nav.helse.Environment
import no.nav.helse.oppslag.InfotrygdBeregningsgrunnlag
import no.nav.helse.oppslag.StsRestClient
import no.nav.helse.streams.Topics
import no.nav.helse.streams.defaultObjectMapper
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.slf4j.LoggerFactory

class EndToEndTest {

    val søknadId = "1556094425588"

    @Test
    fun sammenliknVedtakSomMatcher() {
        val loggLinje = kjørTestMedVedtakOgReturnerFørsteLogglinje("vedtak_1.json", "infotrygd_oppslag_1_match.json")

        assertEquals("VedtaksSammenlikningsMatch for søknadId=$søknadId", loggLinje.split(":").first())
    }

    @Test
    fun sammenliknVedtakUtenInfotrygdBehandling() {
        val loggLinje = kjørTestMedVedtakOgReturnerFørsteLogglinje("vedtak_1.json", "infotrygd_oppslag_tomt_resultat.json")

        assertEquals("VedtaksSammenlikningsFeil for søknadId=$søknadId", loggLinje.split(":").first())
        assertTrue(loggLinje.contains("INFOTRYGD_MANGLER_VEDTAK"))
    }

    @Test
    fun sammenliknVedtakSomIkkeMatcher() {
        val loggLinje = kjørTestMedVedtakOgReturnerFørsteLogglinje("vedtak_1.json", "infotrygd_oppslag_1_mismatch1.json")

        assertEquals("VedtaksSammenlikningsFeil for søknadId=$søknadId", loggLinje.split(":").first())
        assertTrue(loggLinje.contains("PERIODE_ULIK_DAGSATS"))
    }

    @Test
    fun håndterSøppelMelding() {
        restStsStub()
        val logAppender = lagLogAppender(SpinderStream::class.java.name)

        produceOneMessage("12345678901234567890", "{ \"blørrilørribu\" : 123123123 }")

        assertEquals("SpaSykepengeVedtak deserialiserings feil", ventPåEnLoggLinje(logAppender))
    }

    ////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////


    companion object {
        private const val username = "srvkafkaclient"
        private const val password = "kafkaclient"

        val embeddedEnvironment = KafkaEnvironment(
            users = listOf(JAASCredential(username, password)),
            autoStart = false,
            withSchemaRegistry = false,
            withSecurity = true,
            topicNames = listOf(
                Topics.SYKEPENGESØKNADER_INN.name,
                Topics.VEDTAK_SYKEPENGER.name,
                Topics.SYKEPENGEBEHANDLINGSFEIL.name
            )
        )

        val server: WireMockServer = WireMockServer(WireMockConfiguration.options().dynamicPort())

        private lateinit var app: App

        @BeforeAll
        @JvmStatic
        fun start() {
            server.start()
            embeddedEnvironment.start()

            startSpinder()
        }

        @AfterAll
        @JvmStatic
        fun stop() {
            stopSpinder()

            server.stop()
            embeddedEnvironment.tearDown()
        }

        private fun startSpinder() {
            val env = Environment(
                username = username,
                password = password,
                kafkaUsername = username,
                kafkaPassword = password,
                bootstrapServersUrl = embeddedEnvironment.brokersURL,
                sparkelBaseUrl = server.baseUrl(),
                stsRestUrl = server.baseUrl(),
                dbUrl = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
                ventMinutterMellomHvertNyeForsøk = 0,
                ventTimerFørMatcheForsøk = 0,
                maksAlderPåSpaVedtakSomSkalSjekkesIDager = 3650
            )

            app = App(env) //SpinderStream(env, "spinder_e2e")
            app.start()
        }

        private fun stopSpinder() {
            app.stop()
        }
    }


    @BeforeEach
    fun configure() {
        WireMock.configureFor(server.port())
    }

    private fun kjørTestMedVedtakOgReturnerFørsteLogglinje(spaBehandlingResource : String, infotrygdOppslagResource: String) : String {
        val behandling: BehandlingOK = defaultObjectMapper.readValue(
            EndToEndTest::class.java.classLoader.getResourceAsStream(spaBehandlingResource)
        )
        val aktørId = behandling.originalSøknad.aktorId
        val behandlingString = getResourceAsString(spaBehandlingResource);

        restStsStub()
        infotrygdBeregningsgrunnlagStub(aktørId, getResourceAsInfotrygdBeregningsgrunnlag(infotrygdOppslagResource))

        val logAppender = lagLogAppender()

        produceOneMessage(behandling.originalSøknad.id, behandlingString)

        return ventPåEnLoggLinje(logAppender)
    }


    private fun lagLogAppender(loggerName:String = SpinderMatcher::class.java.name): ListAppender<ILoggingEvent> {
        val spinderStreamLogger: Logger = LoggerFactory.getLogger(loggerName) as Logger
        val listAppender = ListAppender<ILoggingEvent>();
        listAppender.start();
        spinderStreamLogger.addAppender(listAppender);
        return listAppender;
    }

    private fun ventPåEnLoggLinje(listAppender: ListAppender<ILoggingEvent>): String =
        ventPåLoggLinjer(listAppender, 1)?.first()


    private fun ventPåLoggLinjer(listAppender: ListAppender<ILoggingEvent>, antallVentede: Int = 1): List<String> {
        val end = System.currentTimeMillis() + 20 * 1000
        while (System.currentTimeMillis() < end) {
            Thread.sleep(100)
            val curSize = listAppender.list.size
            if (curSize >= antallVentede) {
                val ret = listAppender.list.map { it.message }
                listAppender.list.clear()
                return ret
            }
        }
        //
        throw Exception("Kom ikke noe logglinje etter 20 sekunder")
    }


    private fun restStsStub() {
        WireMock.stubFor(
            WireMock.any(WireMock.urlPathEqualTo("/rest/v1/sts/token"))
                .willReturn(
                    WireMock.okJson(
                        defaultObjectMapper.writeValueAsString(
                            StsRestClient.Token(
                                accessToken = "test token",
                                type = "Bearer",
                                expiresIn = 3600
                            )
                        )
                    )
                )
        )
    }

    val tomtInfotrygdBeregningsgrunnlag = InfotrygdBeregningsgrunnlag(
        paaroerendeSykdomListe = emptyList(),
        engangstoenadListe = emptyList(),
        sykepengerListe = emptyList(),
        foreldrepengerListe = emptyList()
    )

    private fun infotrygdBeregningsgrunnlagStub(aktørId: String, resultat: InfotrygdBeregningsgrunnlag= tomtInfotrygdBeregningsgrunnlag) {
        WireMock.stubFor(
            WireMock.any(WireMock.urlPathEqualTo("/api/infotrygdberegningsgrunnlag/$aktørId"))
                .willReturn(
                    WireMock.okJson(
                        defaultObjectMapper.writeValueAsString(
                            resultat
                        )
                    )
                )
        )
    }

    private fun getResourceAsInfotrygdBeregningsgrunnlag(name: String) : InfotrygdBeregningsgrunnlag =
        defaultObjectMapper.readValue(InfotrygdBeregningsgrunnlag::class.java.
            classLoader.getResourceAsStream(name))

    private fun getResourceAsString(name: String) =
        convertStreamToString(this.javaClass.classLoader.getResourceAsStream(name));

    private fun convertStreamToString(ins: java.io.InputStream): String {
        val s = java.util.Scanner(ins).useDelimiter("\\A");
        return if (s.hasNext()) s.next() else "";
    }

    private fun produceOneMessage(key: String, message: String) {
        val producer = KafkaProducer<String, String>(producerProperties(), StringSerializer(), StringSerializer())
        producer.send(ProducerRecord(Topics.VEDTAK_SYKEPENGER.name, key, message))
        producer.flush()
    }

    private fun producerProperties(): MutableMap<String, Any>? {
        return HashMap<String, Any>().apply {
            put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, embeddedEnvironment.brokersURL)
            put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT")
            put(SaslConfigs.SASL_MECHANISM, "PLAIN")
            put(
                SaslConfigs.SASL_JAAS_CONFIG,
                "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"$username\" password=\"$password\";"
            )
        }
    }


}