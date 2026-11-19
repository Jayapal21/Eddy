package app.eddy.browser

import app.eddy.browser.browser.BrowserTab
import app.eddy.browser.browser.ReaderMode
import app.eddy.browser.browser.ReaderThemeManager
import app.eddy.browser.browser.TtsState
import app.eddy.browser.data.database.SavedArticle
import app.eddy.browser.data.database.SavedArticleImage
import app.eddy.browser.data.models.ReaderThemeMode
import app.eddy.browser.data.models.Settings
import app.eddy.browser.data.models.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderModeTest {

    @Test
    fun `isCandidateUrl accepts web articles and rejects internal schemes`() {
        assertTrue(ReaderMode.isCandidateUrl("https://example.com/posts/hello-world"))
        assertTrue(ReaderMode.isCandidateUrl("http://news.example.org/article"))
        assertFalse(ReaderMode.isCandidateUrl(""))
        assertFalse(ReaderMode.isCandidateUrl("about:blank"))
        assertFalse(ReaderMode.isCandidateUrl("file:///sdcard/page.html"))
        assertFalse(ReaderMode.isCandidateUrl("data:text/html,<h1>Hi</h1>"))
    }

    @Test
    fun `new BrowserTab starts with reader mode inactive and unavailable`() {
        val tab = BrowserTab()
        assertFalse(tab.readerActive)
        assertFalse(tab.readerAvailable)
    }

    @Test
    fun `dark reading theme exposes exact centralized color tokens`() {
        val vars = ReaderThemeManager.cssVariables(ReaderThemeMode.DARK)
        assertEquals("#080B0D", vars["--er-bg"])
        assertEquals("#11161B", vars["--er-surface"])
        assertEquals("#F5F7FA", vars["--er-text"])
        assertEquals("#A7B0BC", vars["--er-muted"])
        assertEquals("#29313A", vars["--er-divider"])
        assertEquals("#36D6AE", vars["--er-accent"])
    }

    @Test
    fun `light reading theme exposes exact centralized color tokens`() {
        val vars = ReaderThemeManager.cssVariables(ReaderThemeMode.LIGHT)
        assertEquals("#F8F9FA", vars["--er-bg"])
        assertEquals("#FFFFFF", vars["--er-surface"])
        assertEquals("#171B22", vars["--er-text"])
        assertEquals("#626B78", vars["--er-muted"])
        assertEquals("#E5E7EB", vars["--er-divider"])
        assertEquals("#059669", vars["--er-accent"])
    }

    @Test
    fun `theme transition JS updates CSS variables without reloading or resetting scroll`() {
        val lightJs = ReaderMode.buildApplyThemeJs(ReaderThemeMode.LIGHT, reducedMotion = false)
        assertTrue(lightJs.contains("data-eddy-theme','light'"))
        assertTrue(lightJs.contains("data-eddy-reduced','0'"))
        assertTrue(lightJs.contains("root.style.setProperty('--er-bg','#F8F9FA')"))
        assertTrue(lightJs.contains("root.style.setProperty('--er-surface','#FFFFFF')"))
        assertTrue(lightJs.contains("root.style.setProperty('--er-text','#171B22')"))
        assertTrue(lightJs.contains("root.style.setProperty('--er-accent','#059669')"))
        assertFalse(lightJs.contains("reload"))
        assertFalse(lightJs.contains("scrollTo"))
        assertFalse(lightJs.contains("innerHTML"))

        val darkReducedJs = ReaderMode.buildApplyThemeJs(ReaderThemeMode.DARK, reducedMotion = true)
        assertTrue(darkReducedJs.contains("data-eddy-theme','dark'"))
        assertTrue(darkReducedJs.contains("data-eddy-reduced','1'"))
        assertTrue(darkReducedJs.contains("root.style.setProperty('--er-bg','#080B0D')"))
        assertTrue(darkReducedJs.contains("root.style.setProperty('--er-accent','#36D6AE')"))
    }

    @Test
    fun `frost header colors derive directly from reader theme palette`() {
        val darkPalette = ReaderThemeManager.paletteFor(ReaderThemeMode.DARK)
        val lightPalette = ReaderThemeManager.paletteFor(ReaderThemeMode.LIGHT)
        val darkHeader = ReaderThemeManager.toFrostHeaderColors(darkPalette)
        val lightHeader = ReaderThemeManager.toFrostHeaderColors(lightPalette)

        assertEquals(darkPalette.toolbarBackdrop, darkHeader.backdrop)
        assertEquals(darkPalette.primaryText, darkHeader.primaryText)
        assertEquals(darkPalette.accent, darkHeader.accent)
        assertEquals(darkPalette.dividers, darkHeader.border)

        assertEquals(lightPalette.toolbarBackdrop, lightHeader.backdrop)
        assertEquals(lightPalette.primaryText, lightHeader.primaryText)
        assertEquals(lightPalette.accent, lightHeader.accent)
        assertEquals(lightPalette.dividers, lightHeader.border)
        assertNotEquals(darkHeader.accent, lightHeader.accent)
    }

    @Test
    fun `readerTheme setting stays independent of browser themeMode`() {
        val settings = Settings(themeMode = ThemeMode.LIGHT, readerTheme = ReaderThemeMode.DARK)
        assertEquals(ThemeMode.LIGHT, settings.themeMode)
        assertEquals(ReaderThemeMode.DARK, settings.readerTheme)

        val updated = settings.copy(readerTheme = ReaderThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, updated.themeMode)
        assertEquals(ReaderThemeMode.LIGHT, updated.readerTheme)
    }

    @Test
    fun `extractTtsText JS targets reader article title byline and body blocks`() {
        val js = ReaderMode.buildExtractTtsTextJs()
        assertTrue(js.contains("eddy-reader"))
        assertTrue(js.contains(".byline"))
        assertTrue(js.contains(".eddy-reader-body"))
        assertTrue(js.contains("h2,h3,h4,p,li,blockquote"))
    }

    @Test
    fun `embedOfflineImages replaces matching and HTML-escaped image URLs with cached data URIs`() {
        val html = """
            <p>Lead paragraph.</p>
            <img src="https://cdn.example.com/a.jpg" alt="Photo A">
            <img src="https://cdn.example.com/b.png?w=600&amp;q=80" alt="Photo B">
            <img src="https://cdn.example.com/uncached.webp" alt="Uncached">
        """.trimIndent()
        val dataUris = mapOf(
            "https://cdn.example.com/a.jpg" to "data:image/jpeg;base64,AAA",
            "https://cdn.example.com/b.png?w=600&q=80" to "data:image/png;base64,BBB",
        )

        val embedded = ReaderMode.embedOfflineImages(html, dataUris)
        assertTrue(embedded.contains("""src="data:image/jpeg;base64,AAA""""))
        assertTrue(embedded.contains("""src="data:image/png;base64,BBB""""))
        assertTrue(embedded.contains("""src="https://cdn.example.com/uncached.webp""""))
        assertTrue(embedded.contains("<p>Lead paragraph.</p>"))
    }

    @Test
    fun `buildOfflineDocumentHtml produces standalone themed document with embedded base64 images`() {
        val article = SavedArticle(
            id = 42L,
            url = "https://example.com/articles/offline-test",
            title = "Offline <Reading> & Notes",
            siteName = "Example Press",
            byline = "Jane Doe",
            readMinutes = 4,
            textContent = "Hello offline reader.",
            contentHtml = "<p>Body text</p><img src=\"https://example.com/hero.png\" alt=\"Hero\">",
            imageCount = 1,
            savedAt = 1_700_000_000_000L,
        )
        val imageBytes = byteArrayOf(1, 2, 3, 4)
        val images = listOf(
            SavedArticleImage(
                id = 1L,
                articleId = 42L,
                originalUrl = "https://example.com/hero.png",
                mimeType = "image/png",
                imageData = imageBytes,
            ),
        )

        val html = ReaderMode.buildOfflineDocumentHtml(
            article = article,
            images = images,
            theme = ReaderThemeMode.LIGHT,
            reducedMotion = true,
        )

        assertTrue(html.contains("data-eddy-theme=\"light\""))
        assertTrue(html.contains("data-eddy-reduced=\"1\""))
        assertTrue(html.contains("window.__eddyReader=true;"))
        assertTrue(html.contains("window.__eddyOfflineArticle=true;"))
        assertTrue(html.contains("--er-bg:#F8F9FA"))
        assertTrue(html.contains("--er-accent:#059669"))
        assertTrue(html.contains("Offline &lt;Reading&gt; &amp; Notes"))
        assertTrue(html.contains("Example Press · 4 min read · Offline"))
        assertTrue(html.contains("Jane Doe"))
        assertTrue(html.contains("data:image/png;base64,AQIDBA=="))
    }

    @Test
    fun `ttsState exposes idle preparing playing and paused lifecycle states`() {
        assertEquals(
            setOf(TtsState.IDLE, TtsState.PREPARING, TtsState.PLAYING, TtsState.PAUSED),
            TtsState.entries.toSet(),
        )
    }
}
