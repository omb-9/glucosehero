package com.omb9.glucosehero.data.cgm.nightscout

import com.omb9.glucosehero.data.local.entity.GlucoseSampleSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NightscoutMapperTest {

    @Test
    fun parseEntries_mapsGoodPayload() {
        val json = """
            [
              {
                "_id": "abc123",
                "sgv": 112,
                "date": 1700000000000,
                "direction": "Flat",
                "device": "xDrip-DexcomG6",
                "type": "sgv"
              }
            ]
        """.trimIndent()

        val samples = NightscoutMapper.parseEntries(json)
        assertEquals(1, samples.size)
        val sample = samples.single()
        assertEquals("abc123", sample.externalId)
        assertEquals(112.0, sample.glucoseMgdl, 0.0)
        assertEquals(1_700_000_000_000L, sample.timestamp)
        assertEquals("Flat", sample.trendArrow)
        assertEquals("xDrip-DexcomG6", sample.sourcePackage)
        assertEquals(GlucoseSampleSource.NIGHTSCOUT, sample.source)
        assertNull(sample.hcRecordId)
    }

    @Test
    fun parseEntries_emptyArrayIsSuccessWithNoRows() {
        assertTrue(NightscoutMapper.parseEntries("[]").isEmpty())
    }

    @Test
    fun parseEntries_skipsMalformedRowsInOtherwiseGoodArray() {
        val json = """
            [
              {"_id": "ok", "sgv": 100, "date": 1700000000000},
              {"sgv": 101, "date": 1700000001000},
              {"_id": "no-sgv", "date": 1700000002000},
              {"_id": "no-date", "sgv": 102},
              {"_id": "mills", "sgv": 99, "mills": 1700000003000, "direction": "SingleUp"}
            ]
        """.trimIndent()

        val samples = NightscoutMapper.parseEntries(json)
        assertEquals(2, samples.size)
        assertEquals("ok", samples[0].externalId)
        assertEquals("mills", samples[1].externalId)
        assertEquals("SingleUp", samples[1].trendArrow)
    }

    @Test
    fun parseEntries_objectEnvelopeIsMalformed() {
        assertThrows(NightscoutParseException::class.java) {
            NightscoutMapper.parseEntries("""{"status":200,"result":[]}""")
        }
    }

    @Test
    fun parseEntries_truncatedJsonIsMalformed() {
        assertThrows(NightscoutParseException::class.java) {
            NightscoutMapper.parseEntries("[{\"sgv\":")
        }
    }

    @Test
    fun parseEntries_blankBodyIsMalformed() {
        assertThrows(NightscoutParseException::class.java) {
            NightscoutMapper.parseEntries("  ")
        }
    }
}
