package app.pwhs.blockads.utils

import app.pwhs.blockads.data.entities.WireGuardConfig
import app.pwhs.blockads.data.entities.WireGuardInterface
import app.pwhs.blockads.data.entities.WireGuardPeer
import app.pwhs.blockads.data.entities.WireGuardProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket

class WireGuardPingTesterTest {

    @Test
    fun pingProfile_noEndpoint_returnsError() = runBlocking {
        val profile = WireGuardProfile(
            id = "wg1",
            name = "Test WG",
            config = WireGuardConfig(
                interfaceConfig = WireGuardInterface(
                    privateKey = "c29tZXByaXZhdGVrZXlzb21lcHJpdmF0ZWtleXNvPQ==",
                    address = listOf("10.0.0.2/32")
                ),
                peers = emptyList()
            )
        )
        val result = WireGuardPingTester.pingProfile(profile)
        assertTrue(result is NodePingResult.Error)
    }

    @Test
    fun pingProfile_fallback_openPort_returnsSuccess() = runBlocking {
        val server = ServerSocket(0)
        val port = server.localPort
        try {
            val profile = WireGuardProfile(
                id = "wg1",
                name = "Test WG",
                config = WireGuardConfig(
                    interfaceConfig = WireGuardInterface(
                        privateKey = "c29tZXByaXZhdGVrZXlzb21lcHJpdmF0ZWtleXNvPQ==",
                        address = listOf("10.0.0.2/32")
                    ),
                    peers = listOf(
                        WireGuardPeer(
                            publicKey = "c29tZXB1YmxpY2tleXNvbWVwdWJsaWNrZXlzb21lPQ==",
                            endpoint = "127.0.0.1:$port",
                            allowedIPs = listOf("0.0.0.0/0")
                        )
                    )
                )
            )
            val result = WireGuardPingTester.pingProfile(profile, timeoutMs = 1000)
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
                WireGuardProfile(
                    id = "wg1",
                    name = "WG 1",
                    config = WireGuardConfig(
                        interfaceConfig = WireGuardInterface(
                            privateKey = "c29tZXByaXZhdGVrZXlzb21lcHJpdmF0ZWtleXNvPQ==",
                            address = listOf("10.0.0.2/32")
                        ),
                        peers = listOf(
                            WireGuardPeer(
                                publicKey = "c29tZXB1YmxpY2tleXNvbWVwdWJsaWNrZXlzb21lPQ==",
                                endpoint = "127.0.0.1:$port",
                                allowedIPs = listOf("0.0.0.0/0")
                            )
                        )
                    )
                ),
                WireGuardProfile(
                    id = "wg2",
                    name = "WG 2",
                    config = WireGuardConfig(
                        interfaceConfig = WireGuardInterface(
                            privateKey = "c29tZXByaXZhdGVrZXlzb21lcHJpdmF0ZWtleXNvPQ==",
                            address = listOf("10.0.0.2/32")
                        ),
                        peers = listOf(
                            WireGuardPeer(
                                publicKey = "c29tZXB1YmxpY2tleXNvbWVwdWJsaWNrZXlzb21lPQ==",
                                endpoint = "127.0.0.1:$port",
                                allowedIPs = listOf("0.0.0.0/0")
                            )
                        )
                    )
                )
            )
            val results = mutableMapOf<String, NodePingResult>()
            WireGuardPingTester.pingAll(profiles) { id, res ->
                results[id] = res
            }
            assertEquals(2, results.size)
            assertTrue(results["wg1"] is NodePingResult.Success)
            assertTrue(results["wg2"] is NodePingResult.Success)
        } finally {
            server.close()
        }
    }
}
