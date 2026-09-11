package com.omb9.glucosehero.data.cgm.xdrip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XdripInstalledAppsCacheTest {

    @Test
    fun `probe runs once until invalidate`() {
        var probes = 0
        val cache = XdripInstalledAppsCache {
            probes++
            true
        }
        assertTrue(cache.get())
        assertTrue(cache.get())
        assertEquals(1, probes)
        cache.invalidate()
        assertTrue(cache.get())
        assertEquals(2, probes)
    }

    @Test
    fun `refresh updates the cached value`() {
        var installed = false
        val cache = XdripInstalledAppsCache { installed }
        assertTrue(!cache.get())
        installed = true
        assertTrue(!cache.get())
        assertTrue(cache.refresh())
        assertTrue(cache.get())
    }
}
