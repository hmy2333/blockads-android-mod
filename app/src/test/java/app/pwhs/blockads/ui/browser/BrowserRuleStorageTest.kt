package app.pwhs.blockads.ui.browser

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.pwhs.blockads.ui.browser.rules.BrowserRulePackage
import app.pwhs.blockads.ui.browser.rules.BrowserRuleStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class BrowserRuleStorageTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val storage = BrowserRuleStorage(context)
    private val cacheFile = File(context.filesDir, "browser_rules/browser_rules.json")

    private fun pkg(version: Long) = BrowserRulePackage(version = version, updatedAt = 5, adDomains = listOf("x.test"))

    @Test
    fun `no cache means no cached package`() {
        assertNull(storage.loadCachedPackage())
    }

    @Test
    fun `empty or corrupt cache is ignored`() {
        cacheFile.parentFile!!.mkdirs()
        cacheFile.writeText("")
        assertNull(storage.loadCachedPackage())
        cacheFile.writeText("{\"version\": \"oops\"")
        assertNull(storage.loadCachedPackage())
    }

    @Test
    fun `save then load round-trips and leaves no temp file`() {
        assertTrue(storage.savePackage(pkg(99)))
        assertEquals(pkg(99), storage.loadCachedPackage())
        assertFalse(File(cacheFile.parentFile, "browser_rules.json.tmp").exists())
    }

    @Test
    fun `save falls back to copy when rename fails`() {
        cacheFile.mkdirs()
        assertTrue(storage.savePackage(pkg(99)))
        assertEquals(pkg(99), storage.loadCachedPackage())
    }

    @Test
    fun `save reports failure when the target cannot be replaced`() {
        cacheFile.mkdirs()
        File(cacheFile, "blocker").writeText("x")
        assertFalse(storage.savePackage(pkg(99)))
    }

    @Test
    fun `defaults come from bundled json and assets`() {
        val defaults = storage.loadDefaultPackage()
        assertTrue(defaults.version > 0L)
        assertTrue(defaults.adDomains.isNotEmpty())
        assertTrue(defaults.modules.isNotEmpty())
        assertTrue(defaults.cosmeticCss!!.isNotBlank())
        assertTrue(defaults.scriptletsJs!!.isNotBlank())
    }

    @Test
    fun `active package prefers a newer cache over the bundled defaults`() {
        val baseVersion = storage.loadDefaultPackage().version
        storage.savePackage(pkg(baseVersion + 1))
        assertEquals(baseVersion + 1, storage.getActivePackage().version)
    }

    @Test
    fun `active package keeps an equal-version cache`() {
        val baseVersion = storage.loadDefaultPackage().version
        storage.savePackage(pkg(baseVersion))
        assertEquals(listOf("x.test"), storage.getActivePackage().adDomains)
    }

    @Test
    fun `active package falls back to defaults when the bundle is newer`() {
        val defaults = storage.loadDefaultPackage()
        storage.savePackage(pkg(defaults.version - 1))
        assertEquals(defaults.adDomains, storage.getActivePackage().adDomains)
    }

    @Test
    fun `reset deletes the cache and returns defaults`() {
        storage.savePackage(pkg(99))
        val defaults = storage.resetToDefaults()
        assertFalse(cacheFile.exists())
        assertTrue(defaults.version > 0L)
    }
}
