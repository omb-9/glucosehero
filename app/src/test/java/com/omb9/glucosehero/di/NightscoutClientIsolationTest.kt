package com.omb9.glucosehero.di

import com.omb9.glucosehero.data.remote.CleartextGuardInterceptor
import com.omb9.glucosehero.data.remote.DynamicApiInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.inject.Named

class NightscoutClientIsolationTest {

    @Test
    fun nightscoutClientDoesNotCarryDynamicApiInterceptor() {
        val client = NetworkModule.provideNightscoutOkHttpClient()

        assertTrue(client.interceptors.none { it is DynamicApiInterceptor })
        assertTrue(client.networkInterceptors.none { it is DynamicApiInterceptor })
        assertTrue(client.interceptors.any { it is CleartextGuardInterceptor })
    }

    @Test
    fun provideNightscoutOkHttpClientHasNamedNightscoutQualifier() {
        val method = NetworkModule::class.java.methods.first {
            it.name == "provideNightscoutOkHttpClient" && it.parameterCount == 0
        }
        val named = method.getAnnotation(Named::class.java)
        assertNotNull(named)
        assertEquals("nightscout", named!!.value)
    }
}
