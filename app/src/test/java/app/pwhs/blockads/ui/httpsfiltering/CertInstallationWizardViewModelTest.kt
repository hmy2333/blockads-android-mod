package app.pwhs.blockads.ui.httpsfiltering

import app.pwhs.blockads.testutil.FakeMediaStoreProvider
import app.pwhs.blockads.testutil.ShadowGoSeq
import app.pwhs.blockads.testutil.awaitTrue
import app.pwhs.blockads.ui.httpsfiltering.wizard.CertInstallationWizardUiEffect
import app.pwhs.blockads.ui.httpsfiltering.wizard.CertInstallationWizardUiIntent
import app.pwhs.blockads.ui.httpsfiltering.wizard.CertInstallationWizardViewModel
import app.pwhs.blockads.ui.httpsfiltering.wizard.WizardStep
import io.mockk.coVerify
import io.mockk.every
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Collections

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowGoSeq::class], instrumentedPackages = ["go", "tunnel"])
class CertInstallationWizardViewModelTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @After
    fun tearDown() {
        scope.cancel()
        stopKoin()
        unmockkAll()
    }

    private fun newWizard(
        existingCa: String = "",
    ): Triple<HttpsTestFixtures, CertInstallationWizardViewModel, MutableList<CertInstallationWizardUiEffect>> {
        val f = HttpsTestFixtures()
        every { f.engine.getMitmCACert(any()) } returns existingCa
        val vm = CertInstallationWizardViewModel(f.app)
        awaitTrue(message = "initial cert check") { vm.uiState.value.certStatus != CertStatus.UNKNOWN }
        val effects = Collections.synchronizedList(mutableListOf<CertInstallationWizardUiEffect>())
        scope.launch { vm.uiEffect.collect { effects += it } }
        return Triple(f, vm, effects)
    }

    @Test
    fun `initial state carries install steps`() {
        val (_, vm, _) = newWizard(existingCa = TEST_PEM)
        val state = vm.uiState.value
        assertEquals(WizardStep.EXPLANATION, state.currentStep)
        assertTrue(state.installSteps.isNotEmpty())
        assertEquals(CertStatus.NOT_INSTALLED, state.certStatus)
    }

    @Test
    fun `next and prev walk the steps and stop at both ends`() {
        val (_, vm, _) = newWizard(existingCa = TEST_PEM)

        vm.processIntent(CertInstallationWizardUiIntent.PrevStep)
        assertEquals(WizardStep.EXPLANATION, vm.uiState.value.currentStep)

        repeat(WizardStep.entries.size + 2) { vm.processIntent(CertInstallationWizardUiIntent.NextStep) }
        assertEquals(WizardStep.VERIFY, vm.uiState.value.currentStep)

        repeat(WizardStep.entries.size + 2) { vm.processIntent(CertInstallationWizardUiIntent.PrevStep) }
        assertEquals(WizardStep.EXPLANATION, vm.uiState.value.currentStep)
    }

    @Test
    fun `landing on verify triggers check`() {
        val (f, vm, _) = newWizard(existingCa = TEST_PEM)

        vm.processIntent(CertInstallationWizardUiIntent.GoToStep(WizardStep.VERIFY))
        awaitTrue { vm.uiState.value.certStatus != CertStatus.CHECKING }

        verify(atLeast = 1) { f.engine.getMitmCACert(any()) }
    }

    @Test
    fun `export writes the cert file and notifies the user`() {
        val media = FakeMediaStoreProvider.install()
        val (f, vm, effects) = newWizard(existingCa = TEST_PEM)

        vm.processIntent(CertInstallationWizardUiIntent.ExportCert)
        awaitTrue { effects.isNotEmpty() }

        assertTrue(vm.uiState.value.isCertExported)
        val effect = effects.single() as CertInstallationWizardUiEffect.ShowSnackbar
        assertTrue(effect.message.contains(vm.uiState.value.fileName))
        assertEquals(TEST_PEM, media.files.values.single().readText())
    }

    @Test
    fun `export with no cert available reports error`() {
        val (_, vm, effects) = newWizard(existingCa = "")

        vm.processIntent(CertInstallationWizardUiIntent.ExportCert)
        awaitTrue { effects.isNotEmpty() }

        assertFalse(vm.uiState.value.isCertExported)
        assertEquals(
            CertInstallationWizardUiEffect.ShowSnackbar("Error: could not generate CA certificate"),
            effects.single()
        )
    }

    @Test
    fun `open security settings emits the settings intent`() {
        val (_, vm, effects) = newWizard(existingCa = TEST_PEM)

        vm.processIntent(CertInstallationWizardUiIntent.OpenSecuritySettings)
        awaitTrue { effects.isNotEmpty() }

        val effect = effects.single() as CertInstallationWizardUiEffect.OpenSettings
        assertEquals("android.settings.SECURITY_SETTINGS", effect.intent.action)
    }

    @Test
    fun `finish enables HTTPS filtering and navigates back`() {
        val (f, vm, effects) = newWizard(existingCa = TEST_PEM)

        vm.processIntent(CertInstallationWizardUiIntent.FinishWizard)
        awaitTrue { effects.isNotEmpty() }

        assertEquals(CertInstallationWizardUiEffect.FinishAndNavigateBack, effects.single())
        coVerify { f.prefs.setHttpsFilteringEnabled(true) }
    }
}
