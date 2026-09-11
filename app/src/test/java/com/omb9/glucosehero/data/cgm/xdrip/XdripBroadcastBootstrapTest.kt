package com.omb9.glucosehero.data.cgm.xdrip

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class XdripBroadcastBootstrapTest {

    @Test
    fun `start is idempotent`() = runTest {
        val enabled = MutableStateFlow(false)
        var binds = 0
        var unbinds = 0
        val bootstrap = XdripBroadcastBootstrap(
            enabled = enabled,
            onEnabled = { binds++ },
            onDisabled = { unbinds++ },
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )
        bootstrap.start()
        bootstrap.start()
        assertTrue(bootstrap.hasStarted())
        assertEquals(0, binds)
        assertEquals(1, unbinds)
    }

    @Test
    fun `flag flipping binds and unbinds once per change`() = runTest {
        val enabled = MutableStateFlow(false)
        var binds = 0
        var unbinds = 0
        val bootstrap = XdripBroadcastBootstrap(
            enabled = enabled,
            onEnabled = { binds++ },
            onDisabled = { unbinds++ },
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )
        bootstrap.start()
        assertEquals(1, unbinds)
        assertEquals(0, binds)

        enabled.value = true
        assertEquals(1, binds)
        assertEquals(1, unbinds)

        enabled.value = true
        assertEquals(1, binds)
        assertEquals(1, unbinds)

        enabled.value = false
        assertEquals(1, binds)
        assertEquals(2, unbinds)

        enabled.value = false
        assertEquals(1, binds)
        assertEquals(2, unbinds)

        enabled.value = true
        assertEquals(2, binds)
        assertEquals(2, unbinds)
    }
}
