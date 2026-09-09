package com.omb9.glucosehero.data.remote.off

import com.omb9.glucosehero.data.local.entity.FoodEntity
import com.omb9.glucosehero.domain.model.FoodSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class OpenFoodFactsThrottleInterceptorTest {

    @Test
    fun parksUntilMinIntervalElapsesBetweenCalls() {
        val parked = AtomicInteger(0)
        val interceptor = OpenFoodFactsThrottleInterceptor(
            minIntervalMs = 1_000L,
            nanoTime = clock(0L, 0L, 1_000_000L, 1_000_000L),
            park = { parked.incrementAndGet() },
        )
        val client = clientWith(interceptor)
        val request = Request.Builder()
            .url("https://world.openfoodfacts.org/api/v2/product/1.json")
            .build()

        client.newCall(request).execute().close()
        client.newCall(request).execute().close()

        assertEquals(1, parked.get())
    }

    @Test
    fun firstRequestDoesNotPark() {
        val parked = AtomicInteger(0)
        val interceptor = OpenFoodFactsThrottleInterceptor(
            minIntervalMs = 1_000L,
            nanoTime = { 0L },
            park = { parked.incrementAndGet() },
        )
        val client = clientWith(interceptor)
        client.newCall(
            Request.Builder()
                .url("https://world.openfoodfacts.org/api/v2/product/1.json")
                .build(),
        ).execute().close()

        assertEquals(0, parked.get())
    }

    @Test
    fun serializesConcurrentCallers() {
        val interceptor = OpenFoodFactsThrottleInterceptor(
            minIntervalMs = 5L,
            nanoTime = { System.nanoTime() },
            park = { ms -> if (ms > 0L) Thread.sleep(ms) },
        )
        val client = clientWith(interceptor)
        val request = Request.Builder()
            .url("https://world.openfoodfacts.org/api/v2/product/1.json")
            .build()
        val started = CountDownLatch(1)
        val done = CountDownLatch(4)
        val pool = Executors.newFixedThreadPool(4)
        repeat(4) {
            pool.execute {
                started.await()
                client.newCall(request).execute().close()
                done.countDown()
            }
        }
        started.countDown()
        assertTrue(done.await(5, TimeUnit.SECONDS))
        pool.shutdownNow()
    }

    private fun clientWith(interceptor: OpenFoodFactsThrottleInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

    private fun clock(vararg nanos: Long): () -> Long {
        val values = nanos.iterator()
        return { if (values.hasNext()) values.next() else nanos.last() }
    }
}

class OffBarcodeLookupTest {

    @Test
    fun cacheHitNeverGoesToNetworkEvenWhenLookupEnabled() {
        val cached = FoodEntity(
            name = "Yogurt",
            barcode = "123",
            carbsGrams = 12.0,
            source = FoodSource.OPEN_FOOD_FACTS,
            createdAt = 1L,
        )
        assertEquals(
            OffLookupSource.CACHE,
            OffBarcodeLookup.resolveSource(cached, lookupEnabled = true),
        )
    }

    @Test
    fun cacheMissWithLookupDisabledStaysOffline() {
        assertEquals(
            OffLookupSource.DISABLED,
            OffBarcodeLookup.resolveSource(cached = null, lookupEnabled = false),
        )
    }

    @Test
    fun cacheMissWithLookupEnabledHitsNetwork() {
        assertEquals(
            OffLookupSource.NETWORK,
            OffBarcodeLookup.resolveSource(cached = null, lookupEnabled = true),
        )
    }
}
