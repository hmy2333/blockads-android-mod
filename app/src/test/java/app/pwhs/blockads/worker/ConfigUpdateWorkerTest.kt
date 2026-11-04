package app.pwhs.blockads.worker

import androidx.work.ListenableWorker.Result
import androidx.work.WorkManager
import app.pwhs.blockads.data.dao.ConfigDao
import app.pwhs.blockads.data.datastore.AppPreferences
import app.pwhs.blockads.data.entities.ConfigProfile
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConfigUpdateWorkerTest {

    private var wifiOnly = false
    private val prefs: AppPreferences = mockk {
        every { autoUpdateWifiOnly } answers { flowOf(wifiOnly) }
        every { autoUpdateEnabled } returns flowOf(true)
        every { autoUpdateFrequency } returns flowOf(AppPreferences.UPDATE_FREQUENCY_24H)
    }

    private val configDao: ConfigDao = mockk(relaxed = true)

    private val mockHttpEngine = MockEngine { request ->
        if (request.url.toString() == "https://example.com/rules.conf") {
            respond("HOST, example.com, REJECT\nFINAL, DIRECT\n", HttpStatusCode.OK)
        } else {
            respond("error", HttpStatusCode.NotFound)
        }
    }
    private val httpClient = HttpClient(mockHttpEngine)

    private val harness = WorkerHarness(module {
        single { prefs }
        single { configDao }
        single { httpClient }
    })

    @After
    fun tearDown() = harness.close()

    @Test
    fun `worker completes successfully when no remote configs`() = runBlocking {
        coEvery { configDao.getAutoUpdateConfigs() } returns emptyList()

        val result = harness.run<ConfigUpdateWorker>()

        assertEquals(Result.success(), result)
        coVerify(exactly = 0) { configDao.updateContent(any(), any(), any()) }
    }

    @Test
    fun `worker fetches and updates modified remote config`() = runBlocking {
        val remoteConfig = ConfigProfile(
            id = 10,
            name = "Remote Rules",
            remoteUrl = "https://example.com/rules.conf",
            content = "old content",
            autoUpdate = true
        )
        coEvery { configDao.getAutoUpdateConfigs() } returns listOf(remoteConfig)

        val result = harness.run<ConfigUpdateWorker>()

        assertEquals(Result.success(), result)
        coVerify(exactly = 1) {
            configDao.updateContent(10L, match { it.contains("example.com") }, any())
        }
    }

    @Test
    fun `scheduler schedules periodic work request`() = runBlocking {
        ConfigUpdateScheduler.scheduleConfigUpdate(harness.app, prefs)

        val workInfo = harness.uniqueWork(ConfigUpdateWorker.WORK_NAME)

        assertTrue(workInfo.isNotEmpty())
    }
}
