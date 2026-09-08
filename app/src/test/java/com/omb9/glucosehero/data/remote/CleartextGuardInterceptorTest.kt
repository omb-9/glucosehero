package com.omb9.glucosehero.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors

class CleartextGuardInterceptorTest {

    @Test
    fun isPrivateOrLoopback_acceptsPrivateAndLoopbackHosts() {
        val hosts = listOf(
            "localhost",
            "127.0.0.1",
            "127.255.255.255",
            "::1",
            "10.0.0.1",
            "10.0.2.2",
            "172.16.0.1",
            "172.31.255.255",
            "192.168.0.1",
            "192.168.255.255",
            "169.254.0.1",
            "169.254.255.255",
            "printer.local",
        )
        for (host in hosts) {
            assertTrue("expected private/loopback: $host", isPrivateOrLoopback(host))
        }
    }

    @Test
    fun isPrivateOrLoopback_rejectsPublicHosts() {
        val hosts = listOf(
            "example.com",
            "api.openai.com",
            "8.8.8.8",
            "172.15.255.255",
            "172.32.0.1",
            "192.169.0.1",
            "169.255.0.1",
            "11.0.0.1",
            "myprinter",
        )
        for (host in hosts) {
            assertFalse("expected public: $host", isPrivateOrLoopback(host))
        }
    }

    @Test
    fun intercept_refusesPlainHttpToPublicHost() {
        val client = OkHttpClient.Builder()
            .addInterceptor(CleartextGuardInterceptor())
            .build()

        val e = assertThrows(IOException::class.java) {
            client.newCall(Request.Builder().url("http://example.com/").build()).execute()
        }

        assertTrue(e.message!!.contains("Refusing to send data"))
        assertTrue(e.message!!.contains("example.com"))
    }

    @Test
    fun intercept_allowsPlainHttpToPrivateAddress() {
        withLocalHttpServer { port ->
            val client = OkHttpClient.Builder()
                .addInterceptor(CleartextGuardInterceptor())
                .build()

            val response = client.newCall(
                Request.Builder().url("http://127.0.0.1:$port/").build()
            ).execute()

            assertEquals(200, response.code)
            assertEquals("ok", response.body.string())
            response.close()
        }
    }

    private fun withLocalHttpServer(block: (Int) -> Unit) {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val executor = Executors.newSingleThreadExecutor()
        executor.execute {
            try {
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                    }

                    val body = "ok"
                    socket.getOutputStream().write(
                        (
                            "HTTP/1.1 200 OK\r\n" +
                                "Content-Length: ${body.length}\r\n" +
                                "Connection: close\r\n" +
                                "\r\n" +
                                body
                            ).toByteArray(Charsets.US_ASCII)
                    )
                    socket.getOutputStream().flush()
                }
            } catch (_: Exception) {
                // Server closed before a connection arrived.
            }
        }
        try {
            block(server.localPort)
        } finally {
            server.close()
            executor.shutdownNow()
        }
    }
}
