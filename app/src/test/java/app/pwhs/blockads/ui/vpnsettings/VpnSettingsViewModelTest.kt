package app.pwhs.blockads.ui.vpnsettings

import android.app.Application
import app.cash.turbine.test
import app.pwhs.blockads.data.datastore.AppPreferences
import app.pwhs.blockads.service.ServiceController
import app.pwhs.blockads.ui.MainDispatcherRule
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class VpnSettingsViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val preferredIPTypeFlow = MutableStateFlow(AppPreferences.PREFERRED_IP_AUTO)
    private val excludeLanFlow = MutableStateFlow(false)
    private val allowAppBypassFlow = MutableStateFlow(false)
    private val splitDnsZonesFlow = MutableStateFlow("")
    private val blockWebRtcLeakFlow = MutableStateFlow(false)
    private val blockDohBypassFlow = MutableStateFlow(false)

    private val appPrefs: AppPreferences = mockk {
        every { preferredIPType } returns preferredIPTypeFlow
        every { excludeLan } returns excludeLanFlow
        every { allowAppBypass } returns allowAppBypassFlow
        every { splitDnsZones } returns splitDnsZonesFlow
        every { blockWebRtcLeak } returns blockWebRtcLeakFlow
        every { blockDohBypass } returns blockDohBypassFlow

        coEvery { setPreferredIPType(any()) } coAnswers { preferredIPTypeFlow.value = firstArg() }
        coEvery { setExcludeLan(any()) } coAnswers { excludeLanFlow.value = firstArg() }
        coEvery { setAllowAppBypass(any()) } coAnswers { allowAppBypassFlow.value = firstArg() }
        coEvery { setSplitDnsZones(any()) } coAnswers { splitDnsZonesFlow.value = firstArg() }
        coEvery { setBlockWebRtcLeak(any()) } coAnswers { blockWebRtcLeakFlow.value = firstArg() }
        coEvery { setBlockDohBypass(any()) } coAnswers { blockDohBypassFlow.value = firstArg() }
    }

    private val application: Application = mockk(relaxed = true)
    private lateinit var viewModel: VpnSettingsViewModel

    @Before
    fun setUp() {
        mockkObject(ServiceController)
        every { ServiceController.requestRestart(any()) } just Runs
        viewModel = VpnSettingsViewModel(application, appPrefs)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `initial uiState reflects app preferences`() = runTest {
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(AppPreferences.PREFERRED_IP_AUTO, state.preferredIPType)
            assertEquals(false, state.excludeLan)
            assertEquals(false, state.allowAppBypass)
            assertEquals("", state.splitDnsZones)
            assertEquals(false, state.blockWebRtcLeak)
            assertEquals(false, state.blockDohBypass)
        }
    }

    @Test
    fun `setPreferredIPType updates preferences`() = runTest {
        viewModel.sendIntent(VpnSettingsUiIntent.SetPreferredIPType(AppPreferences.PREFERRED_IP_IPV4))
        coVerify { appPrefs.setPreferredIPType(AppPreferences.PREFERRED_IP_IPV4) }
    }

    @Test
    fun `setExcludeLan updates preferences and requests restart`() = runTest {
        viewModel.sendIntent(VpnSettingsUiIntent.SetExcludeLan(true))
        coVerify { appPrefs.setExcludeLan(true) }
    }

    @Test
    fun `setAllowAppBypass updates preferences and requests restart`() = runTest {
        viewModel.sendIntent(VpnSettingsUiIntent.SetAllowAppBypass(true))
        coVerify { appPrefs.setAllowAppBypass(true) }
    }

    @Test
    fun `setSplitDnsZones updates preferences`() = runTest {
        viewModel.sendIntent(VpnSettingsUiIntent.SetSplitDnsZones("corp.local"))
        coVerify { appPrefs.setSplitDnsZones("corp.local") }
    }

    @Test
    fun `setBlockWebRtcLeak updates preferences`() = runTest {
        viewModel.sendIntent(VpnSettingsUiIntent.SetBlockWebRtcLeak(true))
        coVerify { appPrefs.setBlockWebRtcLeak(true) }
    }

    @Test
    fun `setBlockDohBypass updates preferences`() = runTest {
        viewModel.sendIntent(VpnSettingsUiIntent.SetBlockDohBypass(true))
        coVerify { appPrefs.setBlockDohBypass(true) }
    }
}
