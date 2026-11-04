package app.pwhs.blockads.ui.dnsprovider

import android.app.Application
import app.cash.turbine.test
import app.pwhs.blockads.R
import app.pwhs.blockads.data.datastore.AppPreferences
import app.pwhs.blockads.data.entities.DnsCategory
import app.pwhs.blockads.data.entities.DnsProtocol
import app.pwhs.blockads.data.entities.DnsProviders
import app.pwhs.blockads.service.ServiceController
import app.pwhs.blockads.ui.MainDispatcherRule
import app.pwhs.blockads.ui.keepHot
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DnsProviderViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val providerId = MutableStateFlow<String?>(null)
    private val upstream = MutableStateFlow(AppPreferences.DEFAULT_UPSTREAM_DNS)
    private val fallback = MutableStateFlow(AppPreferences.DEFAULT_FALLBACK_DNS)
    private val protocol = MutableStateFlow(DnsProtocol.PLAIN)
    private val doh = MutableStateFlow("")
    private val odohRelay = MutableStateFlow("")
    private val blockDoh = MutableStateFlow(false)
    private val blockWebRtc = MutableStateFlow(false)

    private val appPrefs: AppPreferences = mockk(relaxed = true) {
        every { dnsProviderId } returns providerId
        every { upstreamDns } returns upstream
        every { fallbackDns } returns fallback
        every { dnsProtocol } returns protocol
        every { dohUrl } returns doh
        every { odohRelayUrl } returns odohRelay
        every { blockDohBypass } returns blockDoh
        every { blockWebRtcLeak } returns blockWebRtc
        coEvery { setDnsProviderId(any()) } coAnswers { providerId.value = firstArg() }
        coEvery { setUpstreamDns(any()) } coAnswers { upstream.value = firstArg() }
        coEvery { setFallbackDns(any()) } coAnswers { fallback.value = firstArg() }
        coEvery { setDnsProtocol(any()) } coAnswers { protocol.value = firstArg() }
        coEvery { setDohUrl(any()) } coAnswers { doh.value = firstArg() }
        coEvery { setOdohRelayUrl(any()) } coAnswers { odohRelay.value = firstArg() }
        coEvery { setBlockDohBypass(any()) } coAnswers { blockDoh.value = firstArg() }
        coEvery { setBlockWebRtcLeak(any()) } coAnswers { blockWebRtc.value = firstArg() }
    }
    private val vm by lazy { DnsProviderViewModel(appPrefs, mockk<Application>(relaxed = true)) }

    @Before
    fun setUp() {
        mockkObject(ServiceController)
        every { ServiceController.requestRestart(any()) } just Runs
    }

    @After
    fun tearDown() = unmockkAll()

    private fun TestScope.hot() = keepHot(vm.state)

    @Test
    fun `parsed host strips schemes and DoH paths`() {
        val cases = mapOf(
            " 1.1.1.1 " to "1.1.1.1",
            "https://dns.google/dns-query" to "dns.google",
            "HTTPS://Dns.Example/q?x=1" to "Dns.Example",
            "tls://dns.google" to "dns.google",
            "TLS://dns.google" to "dns.google",
            "quic://dns.adguard-dns.com" to "dns.adguard-dns.com",
            "QUIC://dns.quad9.net:853" to "dns.quad9.net",
            "2606:4700:4700::1111" to "2606:4700:4700::1111",
        )
        cases.forEach { (input, host) -> assertEquals(input, host, vm.getParsedHost(input)) }
    }

    @Test
    fun `selecting a DoH provider stores its url and restarts`() = runTest {
        hot()
        vm.onIntent(DnsProviderUiIntent.SelectProvider(DnsProviders.CLOUDFLARE))
        assertEquals("cloudflare", providerId.value)
        assertEquals("1.1.1.1", upstream.value)
        assertEquals(DnsProtocol.DOH, protocol.value)
        assertEquals("https://cloudflare-dns.com/dns-query", doh.value)
        assertEquals("cloudflare", vm.state.value.selectedProviderId)
        assertFalse(vm.state.value.isCustomDns)
        verify { ServiceController.requestRestart(any()) }
    }

    @Test
    fun `selecting an ODoH provider stores its relay url and restarts`() = runTest {
        hot()
        vm.onIntent(DnsProviderUiIntent.SelectProvider(DnsProviders.CLOUDFLARE_ODOH))
        assertEquals("cloudflare_odoh", providerId.value)
        assertEquals("1.1.1.1", upstream.value)
        assertEquals(DnsProtocol.ODOH, protocol.value)
        assertEquals("https://odoh.cloudflare-dns.com/dns-query", doh.value)
        assertEquals("https://odoh-relay.edgecompute.app/", odohRelay.value)
        verify { ServiceController.requestRestart(any()) }
    }

    @Test
    fun `selecting a quic provider uses DoQ and a plain provider uses plain DNS`() = runTest {
        hot()
        vm.onIntent(DnsProviderUiIntent.SelectProvider(DnsProviders.QUAD9_DOQ))
        assertEquals(DnsProtocol.DOQ, protocol.value)
        assertEquals("quic://dns.quad9.net", doh.value)

        vm.onIntent(DnsProviderUiIntent.SelectProvider(DnsProviders.SYSTEM))
        assertEquals(DnsProtocol.PLAIN, protocol.value)
        assertEquals("0.0.0.0", upstream.value)

        vm.onIntent(DnsProviderUiIntent.SelectProvider(DnsProviders.OPENDNS))
        assertEquals(DnsProtocol.DOH, protocol.value)
        assertEquals("208.67.222.222", upstream.value)
        assertEquals("https://doh.opendns.com/dns-query", doh.value)
    }

    @Test
    fun `the fallback is swapped only when it would equal the new primary`() = runTest {
        hot()
        fallback.value = "1.1.1.1"
        vm.onIntent(DnsProviderUiIntent.SelectProvider(DnsProviders.GOOGLE))
        assertEquals("unchanged when distinct", "1.1.1.1", fallback.value)

        fallback.value = DnsProviders.QUAD9.ipAddress
        vm.onIntent(DnsProviderUiIntent.SelectProvider(DnsProviders.QUAD9_DOQ))
        assertEquals("149.112.112.112", fallback.value)

        fallback.value = DnsProviders.ADGUARD.ipAddress
        vm.onIntent(DnsProviderUiIntent.SelectProvider(DnsProviders.ADGUARD))
        assertEquals("94.140.14.15", fallback.value)

        fallback.value = DnsProviders.DNSBUNKER.ipAddress
        vm.onIntent(DnsProviderUiIntent.SelectProvider(DnsProviders.DNSBUNKER))
        assertEquals("185.250.250.62", fallback.value)
    }

    @Test
    fun `saving a blank fallback persists empty string and disables fallback`() = runTest {
        hot()
        fallback.value = "1.1.1.1"
        vm.onIntent(DnsProviderUiIntent.SaveFallbackDns("   "))
        assertEquals("", fallback.value)
        verify { ServiceController.requestRestart(any()) }
    }

    @Test
    fun `selecting a provider when fallback is blank keeps it blank`() = runTest {
        hot()
        fallback.value = ""
        vm.onIntent(DnsProviderUiIntent.SelectProvider(DnsProviders.QUAD9))
        assertEquals("", fallback.value)
    }

    @Test
    fun `a custom provider id is marked as custom`() = runTest {
        providerId.value = AppPreferences.CUSTOM_DNS_PROVIDER_ID
        upstream.value = "1.1.1.1"
        hot()
        assertNull(vm.state.value.selectedProviderId)
        assertTrue(vm.state.value.isCustomDns)
    }

    @Test
    fun `custom display shows the server in the form configured`() = runTest {
        hot()
        upstream.value = "8.8.8.8"
        assertEquals("8.8.8.8", vm.state.value.customDnsDisplay)

        doh.value = "https://dns.google/dns-query"
        protocol.value = DnsProtocol.DOH
        assertEquals("https://dns.google/dns-query", vm.state.value.customDnsDisplay)

        upstream.value = "dns.google"
        protocol.value = DnsProtocol.DOT
        assertEquals("tls://dns.google", vm.state.value.customDnsDisplay)

        protocol.value = DnsProtocol.DOQ
        assertEquals("quic://dns.google/dns-query", vm.state.value.customDnsDisplay)
        doh.value = "QUIC://dns.quad9.net"
        assertEquals("QUIC://dns.quad9.net", vm.state.value.customDnsDisplay)
    }

    @Test
    fun `custom DoH, ODoH, DoQ, DoT and plain entries set protocol and host`() = runTest {
        hot()
        vm.onIntent(DnsProviderUiIntent.SaveCustomDns(DnsProtocol.DOH, "https://dns.example/dns-query"))
        assertEquals(AppPreferences.CUSTOM_DNS_PROVIDER_ID, providerId.value)
        assertEquals(DnsProtocol.DOH, protocol.value)
        assertEquals("https://dns.example/dns-query", doh.value)
        assertEquals("dns.example", upstream.value)

        vm.onIntent(
            DnsProviderUiIntent.SaveCustomDns(
                protocol = DnsProtocol.ODOH,
                endpoint = "https://odoh.cloudflare-dns.com/dns-query",
                relayUrl = "https://odoh-relay.cloudflare.com/proxy"
            )
        )
        assertEquals(DnsProtocol.ODOH, protocol.value)
        assertEquals("https://odoh.cloudflare-dns.com/dns-query", doh.value)
        assertEquals("https://odoh-relay.cloudflare.com/proxy", odohRelay.value)

        vm.onIntent(DnsProviderUiIntent.SaveCustomDns(DnsProtocol.DOQ, "quic://doq.example"))
        assertEquals(DnsProtocol.DOQ, protocol.value)
        assertEquals("quic://doq.example", doh.value)

        vm.onIntent(DnsProviderUiIntent.SaveCustomDns(DnsProtocol.DOT, "tls://dot.example"))
        assertEquals(DnsProtocol.DOT, protocol.value)
        assertEquals("dot.example", upstream.value)

        vm.onIntent(DnsProviderUiIntent.SaveCustomDns(DnsProtocol.DOH3, "h3://dns.google/dns-query"))
        assertEquals(DnsProtocol.DOH3, protocol.value)
        assertEquals("h3://dns.google/dns-query", doh.value)

        vm.onIntent(DnsProviderUiIntent.SaveCustomDns(DnsProtocol.DOH, "sdns://AgMAAAAAAAAABzEuMS4xLjEAEmNsb3VkZmxhcmUtZG5zLmNvbQovZG5zLXF1ZXJ5"))
        assertEquals(DnsProtocol.DOH, protocol.value)
        assertEquals("https://cloudflare-dns.com/dns-query", doh.value)

        vm.onIntent(DnsProviderUiIntent.SaveCustomDns(DnsProtocol.PLAIN, "8.8.4.4"))
        assertEquals(DnsProtocol.PLAIN, protocol.value)
        assertEquals("8.8.4.4", upstream.value)
        verify(atLeast = 7) { ServiceController.requestRestart(any()) }
    }

    @Test
    fun `blank custom input is ignored`() = runTest {
        hot()
        vm.onIntent(DnsProviderUiIntent.SaveCustomDns(DnsProtocol.PLAIN, "   "))
        coVerify(exactly = 0) { appPrefs.setUpstreamDns(any()) }
    }

    @Test
    fun `a plain custom server equal to the fallback is refused`() = runTest {
        hot()
        fallback.value = "8.8.8.8"
        vm.effects.test {
            vm.onIntent(DnsProviderUiIntent.SaveCustomDns(DnsProtocol.PLAIN, "8.8.8.8"))
            assertEquals(DnsProviderUiEffect.ShowToast(R.string.dns_error_duplicate), awaitItem())
        }
        coVerify(exactly = 0) { appPrefs.setUpstreamDns(any()) }
    }

    @Test
    fun `a fallback equal to a plain upstream is refused`() = runTest {
        hot()
        upstream.value = "dns.example"
        protocol.value = DnsProtocol.PLAIN
        vm.effects.test {
            vm.onIntent(DnsProviderUiIntent.SaveFallbackDns("dns.example"))
            assertEquals(DnsProviderUiEffect.ShowToast(R.string.dns_error_duplicate), awaitItem())
        }
        coVerify(exactly = 0) { appPrefs.setFallbackDns(any()) }
    }

    @Test
    fun `tab and category selection updates state`() = runTest {
        hot()
        vm.onIntent(DnsProviderUiIntent.SelectTab(1))
        assertEquals(1, vm.state.value.selectedTab)

        vm.onIntent(DnsProviderUiIntent.SelectCategory(DnsCategory.PRIVACY))
        assertEquals(DnsCategory.PRIVACY, vm.state.value.selectedCategory)
    }

    @Test
    fun `DoH bypass blocking persists`() = runTest {
        hot()
        vm.onIntent(DnsProviderUiIntent.ToggleBlockDohBypass(true))
        coVerify { appPrefs.setBlockDohBypass(true) }
    }

    @Test
    fun `WebRTC leak blocking persists`() = runTest {
        hot()
        vm.onIntent(DnsProviderUiIntent.ToggleBlockWebRtcLeak(true))
        coVerify { appPrefs.setBlockWebRtcLeak(true) }
    }
}
