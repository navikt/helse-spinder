package no.nav.helse.spinder.db

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RegexTest {

    @Test
    fun testInneholderKunAlfanumeriskOgStrek() {
        assertTrue(inneholderKunAlfanumeriskOgStrek("helse-postgres__role-admin"))

        assertFalse(inneholderKunAlfanumeriskOgStrek("helse- postgres__role-admin"))
        assertFalse(inneholderKunAlfanumeriskOgStrek("helse-;postgres__role-admin"))
        assertFalse(inneholderKunAlfanumeriskOgStrek("helse-postgres__role-admin;"))
        assertFalse(inneholderKunAlfanumeriskOgStrek("helse-postgres__role-admin'"))
        assertFalse(inneholderKunAlfanumeriskOgStrek(" helse-postgres__role-admin"))
        assertFalse(inneholderKunAlfanumeriskOgStrek("'helse-postgres__role-admin"))
    }




}