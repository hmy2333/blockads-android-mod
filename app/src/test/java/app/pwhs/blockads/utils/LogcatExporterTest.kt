package app.pwhs.blockads.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class LogcatExporterTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val logDir = File(context.cacheDir, "logs")

    @Before
    fun setUp() {
        logDir.deleteRecursively()
        logDir.mkdirs()
    }

    @Test
    fun `collectDeviceInfo includes application, device, and resource details`() {
        val info = LogcatExporter.collectDeviceInfo(context)

        assertTrue(info.contains("BLOCKADS DIAGNOSTIC & LOGCAT REPORT"))
        assertTrue(info.contains("--- [1] APPLICATION INFO ---"))
        assertTrue(info.contains("App Name:         BlockAds"))
        assertTrue(info.contains("Package:          ${context.packageName}"))
        assertTrue(info.contains("--- [2] DEVICE & OS ---"))
        assertTrue(info.contains("Android Version:"))
        assertTrue(info.contains("--- [3] SYSTEM RESOURCES ---"))
        assertTrue(info.contains("RAM Available:"))
        assertTrue(info.contains("JVM Heap:"))
        assertTrue(info.contains("--- [4] NETWORK & PROTECTION STATUS ---"))
        assertTrue(info.contains("VPN Permission:"))
    }

    @Test
    fun `readAppFileLogs handles missing and existing log files`() {
        val missingText = LogcatExporter.readAppFileLogs(context)
        assertTrue(missingText.contains("(No app event logs recorded yet)"))

        val logFile = File(logDir, "blockads_logs.txt")
        logFile.writeText("2026-10-06 10:00:00 I/[Engine] VPN started successfully\n")

        val existingText = LogcatExporter.readAppFileLogs(context)
        assertTrue(existingText.contains("VPN started successfully"))
    }

    @Test
    fun `generateReport structures all sections and logcat lines`() {
        val mockLogcat = listOf(
            "10-06 10:00:01.123 1234 1234 I TunnelEngine: DNS proxy listening on 127.0.0.1:53",
            "10-06 10:00:02.456 1234 1234 D PacketFlow: Route table verified"
        )

        val report = LogcatExporter.generateReport(context) { mockLogcat }

        assertTrue(report.contains("--- [1] APPLICATION INFO ---"))
        assertTrue(report.contains("[5] APPLICATION FILE LOGS"))
        assertTrue(report.contains("[6] SYSTEM LOGCAT DUMP"))
        assertTrue(report.contains("DNS proxy listening on 127.0.0.1:53"))
        assertTrue(report.contains("PacketFlow: Route table verified"))
        assertTrue(report.contains("END OF DIAGNOSTIC REPORT"))
    }

    @Test
    fun `createReportFile writes timestamped file to cache logs directory`() {
        val report = "Diagnostic content here"
        val file = LogcatExporter.createReportFile(context, report)

        assertNotNull(file)
        assertTrue(file.exists())
        assertTrue(file.name.startsWith("blockads_logcat_"))
        assertTrue(file.name.endsWith(".txt"))
        assertEquals("Diagnostic content here", file.readText())
    }
}
