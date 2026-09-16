package com.omb9.glucosehero.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class GlucoseUnitLocaleTest {

    @Test
    fun usGermanyAndLatinAmericaUseMgdl() {
        listOf("US", "DE", "AT", "MX", "BR", "AR", "CO", "CL").forEach { country ->
            assertEquals(
                country,
                GlucoseUnit.MGDL,
                GlucoseUnit.defaultForLocale(locale(country)),
            )
        }
    }

    @Test
    fun ukCanadaAustraliaAndMostOfEuropeUseMmol() {
        listOf("GB", "CA", "AU", "NZ", "IE", "FR", "NL", "SE", "IT", "ES", "PL").forEach { country ->
            assertEquals(
                country,
                GlucoseUnit.MMOL,
                GlucoseUnit.defaultForLocale(locale(country)),
            )
        }
    }

    @Test
    fun unspecifiedRegionsFallBackToMgdl() {
        listOf("JP", "IN", "KR", "ZA", "").forEach { country ->
            assertEquals(
                country,
                GlucoseUnit.MGDL,
                GlucoseUnit.defaultForLocale(locale(country)),
            )
        }
    }

    private fun locale(country: String): Locale =
        Locale.Builder().setLanguage("en").setRegion(country).build()
}
