package com.omb9.glucosehero.data.cgm.nightscout

import org.junit.Assert.assertEquals
import org.junit.Test

class NightscoutUrlsTest {

    @Test
    fun entriesUrl_appendsV1PathAndDropsQuery() {
        val base = NightscoutUrls.parseBase("https://ns.example.com/?token=leak")!!
        val entries = NightscoutUrls.entriesUrl(base)
        assertEquals("https://ns.example.com/api/v1/entries/sgv.json", entries.toString())
    }

    @Test
    fun entriesUrl_keepsSubdirectoryAndStripsExistingApiPath() {
        val base = NightscoutUrls.parseBase("https://host.example/ns/api/v1")!!
        assertEquals(
            "https://host.example/ns/api/v1/entries/sgv.json",
            NightscoutUrls.entriesUrl(base).toString(),
        )
    }

    @Test
    fun requestUrl_setsFindDateAndCount() {
        val base = NightscoutUrls.parseBase("https://ns.example.com")!!
        val url = NightscoutUrls.requestUrl(base, 1_700_000_000_000L, 50).build()
        assertEquals("1700000000000", url.queryParameter(NightscoutUrls.QUERY_FIND_DATE_GT))
        assertEquals("50", url.queryParameter(NightscoutUrls.QUERY_COUNT))
    }

    @Test
    fun clampBackfillHours_hardCaps() {
        assertEquals(6, NightscoutLimits.clampBackfillHours(1))
        assertEquals(24, NightscoutLimits.clampBackfillHours(24))
        assertEquals(72, NightscoutLimits.clampBackfillHours(10_000))
    }
}
