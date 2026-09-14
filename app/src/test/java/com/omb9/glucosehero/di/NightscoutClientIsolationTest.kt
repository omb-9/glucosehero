package com.omb9.glucosehero.di

import com.omb9.glucosehero.data.remote.CleartextGuardInterceptor
import com.omb9.glucosehero.data.remote.DynamicApiInterceptor
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.inject.Named

class NightscoutClientIsolationTest {

    @Test
    fun nightscoutClientDoesNotCarryDynamicApiInterceptor() {
        val base = NetworkModule.provideBaseOkHttpClient()
        val client = NetworkModule.provideNightscoutOkHttpClient(base)

        assertTrue(base.interceptors.none { it is DynamicApiInterceptor })
        assertTrue(client.interceptors.none { it is DynamicApiInterceptor })
        assertTrue(client.networkInterceptors.none { it is DynamicApiInterceptor })
        assertTrue(client.interceptors.any { it is CleartextGuardInterceptor })
        assertTrue(client.dispatcher === base.dispatcher)
        assertTrue(client.connectionPool === base.connectionPool)
    }

    @Test
    fun provideNightscoutOkHttpClientHasNamedNightscoutQualifier() {
        val method = NetworkModule::class.java.methods.first {
            it.name == "provideNightscoutOkHttpClient" &&
                it.parameterTypes.contains(OkHttpClient::class.java)
        }
        val named = method.getAnnotation(Named::class.java)
        assertNotNull(named)
        assertEquals("nightscout", named!!.value)
        val paramNamed = method.parameterAnnotations[0].filterIsInstance<Named>().firstOrNull()
        assertNotNull(paramNamed)
        assertEquals("okhttp-base", paramNamed!!.value)
    }
}
