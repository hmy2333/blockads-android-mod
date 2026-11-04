package app.pwhs.blockads.data.geoip

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPOutputStream

class GeoIpManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var filesDir: File

    @Before
    fun setUp() {
        filesDir = tempFolder.newFolder("files")
        context = mockk(relaxed = true) {
            every { filesDir } returns this@GeoIpManagerTest.filesDir
        }
    }

    private fun createSampleGeoIpBytes(): ByteArray {
        val count = 200 // > 1000 bytes requirement
        val buf = ByteBuffer.allocate(count * 10).order(ByteOrder.BIG_ENDIAN)
        for (i in 0 until count) {
            buf.putInt(i * 100)
            buf.putInt(i * 100 + 50)
            buf.put("VN".toByteArray(Charsets.US_ASCII))
        }
        return buf.array()
    }

    private fun gzip(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }

    @Test
    fun `isDatabaseAvailable reflects file existence`() {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { respond("OK") }
            }
        }
        val manager = GeoIpManager(context, client)
        assertFalse(manager.isDatabaseAvailable())

        File(filesDir, GeoIpManager.GEOIP_FILE_NAME).writeBytes(createSampleGeoIpBytes())
        assertTrue(manager.isDatabaseAvailable())
    }

    @Test
    fun `checkAndUpdate downloads and decompresses gz database successfully`() = runTest {
        val sampleBytes = createSampleGeoIpBytes()
        val gzipped = gzip(sampleBytes)

        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    if (request.url.encodedPath.endsWith(".gz")) {
                        respond(
                            content = gzipped,
                            status = HttpStatusCode.OK,
                            headers = headersOf("Content-Type", "application/gzip")
                        )
                    } else {
                        respond("Not Found", status = HttpStatusCode.NotFound)
                    }
                }
            }
        }

        val manager = GeoIpManager(context, client)
        val result = manager.checkAndUpdate(force = true)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull() == true)
        assertTrue(manager.isDatabaseAvailable())
        val downloaded = File(filesDir, GeoIpManager.GEOIP_FILE_NAME).readBytes()
        assertTrue(downloaded.contentEquals(sampleBytes))
    }

    @Test
    fun `checkAndUpdate skips when database is already fresh`() = runTest {
        val sampleBytes = createSampleGeoIpBytes()
        val targetFile = File(filesDir, GeoIpManager.GEOIP_FILE_NAME)
        targetFile.writeBytes(sampleBytes)
        targetFile.setLastModified(System.currentTimeMillis())

        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    throw AssertionError("Network request should not be made when fresh")
                }
            }
        }

        val manager = GeoIpManager(context, client)
        val result = manager.checkAndUpdate(force = false)

        assertTrue(result.isSuccess)
        assertFalse(result.getOrNull() == true) // false indicates skipped
    }

    @Test
    fun `importFromStream accepts valid gzipped custom database`() = runTest {
        val sampleBytes = createSampleGeoIpBytes()
        val gzipped = gzip(sampleBytes)

        val client = HttpClient(MockEngine) {
            engine { addHandler { respond("OK") } }
        }

        val manager = GeoIpManager(context, client)
        val result = manager.importFromStream(gzipped.inputStream())

        assertTrue(result.isSuccess)
        val count = result.getOrNull() ?: 0
        assertTrue(count == 200)

        val info = manager.getDatabaseInfo()
        assertTrue(info.isAvailable)
        assertTrue(info.recordCount == 200)
    }
}
