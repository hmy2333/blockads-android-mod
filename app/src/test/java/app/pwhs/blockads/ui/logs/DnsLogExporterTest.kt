package app.pwhs.blockads.ui.logs

import app.pwhs.blockads.data.entities.DnsLogEntry
import app.pwhs.blockads.data.repository.FilterListRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Locale

class DnsLogExporterTest {

    private val fixedFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    @Test
    fun `writeCsv outputs correct headers and row values`() {
        val entry = DnsLogEntry(
            id = 1,
            domain = "example.com",
            timestamp = 1700000000000L,
            isBlocked = false,
            queryType = "AAAA",
            responseTimeMs = 15,
            appName = "Firefox",
            packageName = "org.mozilla.firefox",
            resolvedIp = "2606:2800:220:1:248:1893:25c8:1946",
            countryCode = "US"
        )

        val writer = StringWriter()
        DnsLogExporter.writeCsv(
            writer = writer,
            logs = listOf(entry),
            dateFormat = fixedFormat
        )

        val lines = writer.toString().trim().lines()
        assertEquals(2, lines.size)
        assertEquals(
            "Time,Domain,Status,Block Reason,App,Package Name,Query Type,Response Time (ms),Resolved IP,Country",
            lines[0]
        )
        val expectedTime = fixedFormat.format(java.util.Date(1700000000000L))
        assertEquals(
            "$expectedTime,example.com,Allowed,-,Firefox,org.mozilla.firefox,AAAA,15,2606:2800:220:1:248:1893:25c8:1946,US",
            lines[1]
        )
    }

    @Test
    fun `formatBlockReason maps known reasons and filter names`() {
        val filterMap = mapOf("1" to "AdGuard Base", "2" to "EasyPrivacy")

        assertEquals("-", DnsLogExporter.formatBlockReason("", isBlocked = false, filterMap))
        assertEquals("Custom Rule", DnsLogExporter.formatBlockReason(FilterListRepository.BLOCK_REASON_CUSTOM_RULE, true, filterMap))
        assertEquals("Security Protection", DnsLogExporter.formatBlockReason(FilterListRepository.BLOCK_REASON_SECURITY, true, filterMap))
        assertEquals("Firewall", DnsLogExporter.formatBlockReason(FilterListRepository.BLOCK_REASON_FIREWALL, true, filterMap))
        assertEquals("Upstream DNS", DnsLogExporter.formatBlockReason(FilterListRepository.BLOCK_REASON_UPSTREAM_DNS, true, filterMap))
        assertEquals("Filter List", DnsLogExporter.formatBlockReason(FilterListRepository.BLOCK_REASON_FILTER_LIST, true, filterMap))
        assertEquals("AdGuard Base", DnsLogExporter.formatBlockReason("1", true, filterMap))
        assertEquals("AdGuard Base; EasyPrivacy", DnsLogExporter.formatBlockReason("1, 2", true, filterMap))
        assertEquals("99", DnsLogExporter.formatBlockReason("99", true, filterMap))
        assertEquals("Blocked", DnsLogExporter.formatBlockReason("", true, filterMap))
    }

    @Test
    fun `escapeCsv handles commas, quotes, and newlines`() {
        assertEquals("\"Acme, Inc\"", DnsLogExporter.escapeCsv("Acme, Inc"))
        assertEquals("\"He said \"\"Hello\"\"\"", DnsLogExporter.escapeCsv("He said \"Hello\""))
        assertEquals("\"Line1\nLine2\"", DnsLogExporter.escapeCsv("Line1\nLine2"))
    }

    @Test
    fun `escapeCsv neutralizes spreadsheet formulas`() {
        val formula = "=HYPERLINK(\"http://evil.com\")"
        val escaped = DnsLogExporter.escapeCsv(formula)
        assertFalse(escaped.startsWith("="))
        assertTrue(escaped.contains("'=HYPERLINK"))

        val plusFormula = "+1+cmd|' /C calc'!A0"
        assertTrue(DnsLogExporter.escapeCsv(plusFormula).startsWith("'+1"))

        val atFormula = "@SUM(1,2)"
        assertTrue(DnsLogExporter.escapeCsv(atFormula).startsWith("\"'@SUM"))

        // Standard dash or negative number should remain unescaped
        assertEquals("-", DnsLogExporter.escapeCsv("-"))
        assertEquals("-15", DnsLogExporter.escapeCsv("-15"))
    }

    @Test
    fun `defaults are populated when fields are blank`() {
        val entry = DnsLogEntry(
            id = 2,
            domain = "tracker.com",
            timestamp = 1700000000000L,
            isBlocked = true,
            queryType = "",
            responseTimeMs = 0,
            appName = "",
            packageName = "",
            resolvedIp = "",
            countryCode = ""
        )

        val writer = StringWriter()
        DnsLogExporter.writeCsv(
            writer = writer,
            logs = listOf(entry),
            dateFormat = fixedFormat
        )

        val row = writer.toString().trim().lines()[1]
        val expectedTime = fixedFormat.format(java.util.Date(1700000000000L))
        assertEquals("$expectedTime,tracker.com,Blocked,Blocked,System,-,A,0,-,-", row)
    }

    @Test
    fun `appName falls back to packageName when appName is blank`() {
        val entry = DnsLogEntry(
            id = 3,
            domain = "api.com",
            timestamp = 1700000000000L,
            isBlocked = false,
            appName = "",
            packageName = "com.example.service"
        )

        val writer = StringWriter()
        DnsLogExporter.writeCsv(
            writer = writer,
            logs = listOf(entry),
            dateFormat = fixedFormat
        )

        val row = writer.toString().trim().lines()[1]
        assertTrue(row.contains("com.example.service,com.example.service"))
    }
}
