package app.pwhs.blockads.utils

import app.pwhs.blockads.data.entities.ProxyProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket

class ProxyPingTesterTest {

    @Test
    fun ping_invalidHostOrPort_returnsError() = runBlocking {
        val resultEmpty = ProxyPingTester.ping("", 443)
        assertTrue(resultEmpty is NodePingResult.Error)

        val resultInvalidPort = ProxyPingTester.ping("127.0.0.1", -1)
        assertTrue(resultInvalidPort is NodePingResult.Error)
    }

    @Test
    fun ping_openPort_returnsSuccess() = runBlocking {
        val server = ServerSocket(0)
        val port = server.localPort
        try {
            val result = ProxyPingTester.ping("127.0.0.1", port, timeoutMs = 1000)
            assertTrue(result is NodePingResult.Success)
            val latency = (result as NodePingResult.Success).latencyMs
            assertTrue(latency >= 0)
        } finally {
            server.close()
        }
    }

    @Test
    fun pingAll_reportsResultsForAllProfiles() = runBlocking {
        val server = ServerSocket(0)
        val port = server.localPort
        try {
            val profiles = listOf(
                ProxyProfile(id = "p1", server = "127.0.0.1", port = port),
                ProxyProfile(id = "p2", server = "127.0.0.1", port = port)
            )
            val results = mutableMapOf<String, NodePingResult>()
            ProxyPingTester.pingAll(profiles) { id, res ->
                results[id] = res
            }
            assertEquals(2, results.size)
            assertTrue(results["p1"] is NodePingResult.Success)
            assertTrue(results["p2"] is NodePingResult.Success)
        } finally {
            server.close()
        }
    }

    @Test
    fun pingNode_fallback_openPort_returnsSuccess() = runBlocking {
        val server = ServerSocket(0)
        val port = server.localPort
        try {
            val profile = ProxyProfile(
                id = "p1",
                protocol = app.pwhs.blockads.data.entities.ProxyProtocol.SHADOWSOCKS,
                server = "127.0.0.1",
                port = port
            )
            val result = ProxyPingTester.pingNode(profile, timeoutMs = 1000)
            assertTrue(result is NodePingResult.Success)
        } finally {
            server.close()
        }
    }

    @Test
    fun pingNode_hysteria2_closedPort_returnsErrorOrTimeout() = runBlocking {
        val profile = ProxyProfile(
            id = "hy1",
            protocol = app.pwhs.blockads.data.entities.ProxyProtocol.HYSTERIA2,
            server = "127.0.0.1",
            port = 59999
        )
        val result = ProxyPingTester.pingNode(profile, timeoutMs = 500)
        assertTrue(result is NodePingResult.Error || result is NodePingResult.Timeout)
    }
}
