package com.omb9.glucosehero.data.cgm.nightscout

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NightscoutAuthTest {

    @Test
    fun tokenAuth_isQueryParameterNotHeader() {
        val url = "https://ns.example/api/v1/entries/sgv.json".toHttpUrl().newBuilder()
        val request = Request.Builder()
        NightscoutAuth.apply(url, request, NightscoutAuthMode.TOKEN, "my-token")

        val builtUrl = url.build()
        val builtRequest = request.url(builtUrl).build()
        assertEquals("my-token", builtUrl.queryParameter("token"))
        assertNull(builtRequest.header("api-secret"))
        assertNull(builtRequest.header("Authorization"))
    }

    @Test
    fun apiSecret_isSha1HeaderNotQuery() {
        val url = "https://ns.example/api/v1/entries/sgv.json".toHttpUrl().newBuilder()
        val request = Request.Builder()
        NightscoutAuth.apply(url, request, NightscoutAuthMode.API_SECRET, "test")

        val builtUrl = url.build()
        val builtRequest = request.url(builtUrl).build()
        assertNull(builtUrl.queryParameter("token"))
        assertEquals(
            "a94a8fe5ccb19ba61c4c0873d391e987982fbbd3",
            builtRequest.header("api-secret"),
        )
    }
}
