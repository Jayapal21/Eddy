package app.eddy.browser

import app.eddy.browser.browser.WebViewFactory
import app.eddy.browser.browser.WebsiteDarkMode
import app.eddy.browser.browser.WebsiteDarkMode.ApplyOutcome
import app.eddy.browser.data.database.SiteSettings
import app.eddy.browser.privacy.SiteFeature
import app.eddy.browser.privacy.get
import app.eddy.browser.privacy.isEmpty
import app.eddy.browser.privacy.with
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebsiteDarkModeTest {

    @Test
    fun `shouldApply respects global toggle, per-site exclusion, reader mode, url scheme, and fallback key`() {
        val url = "https://example.com/articles/white-page"
        assertTrue(WebsiteDarkMode.shouldApply(darkenPages = true, siteDarkenSetting = null, readerActive = false, url = url, failedPageKey = null))
        assertTrue(WebsiteDarkMode.shouldApply(darkenPages = true, siteDarkenSetting = SiteSettings.ALLOW, readerActive = false, url = url, failedPageKey = null))
        assertFalse(WebsiteDarkMode.shouldApply(darkenPages = false, siteDarkenSetting = null, readerActive = false, url = url, failedPageKey = null))
        assertFalse(WebsiteDarkMode.shouldApply(darkenPages = true, siteDarkenSetting = SiteSettings.BLOCK, readerActive = false, url = url, failedPageKey = null))
        assertFalse(WebsiteDarkMode.shouldApply(darkenPages = true, siteDarkenSetting = null, readerActive = true, url = url, failedPageKey = null))
        assertFalse(WebsiteDarkMode.shouldApply(darkenPages = true, siteDarkenSetting = null, readerActive = false, url = "about:blank", failedPageKey = null))
        assertFalse(WebsiteDarkMode.shouldApply(darkenPages = true, siteDarkenSetting = null, readerActive = false, url = "file:///sdcard/test.html", failedPageKey = null))

        val failedKey = WebsiteDarkMode.pageKey("$url#section-2")
        assertFalse(WebsiteDarkMode.shouldApply(darkenPages = true, siteDarkenSetting = null, readerActive = false, url = url, failedPageKey = failedKey))
        assertTrue(WebsiteDarkMode.shouldApply(darkenPages = true, siteDarkenSetting = null, readerActive = false, url = "https://example.com/other", failedPageKey = failedKey))
    }

    @Test
    fun `pageKey normalizes host casing and strips fragments while keeping path and query`() {
        assertEquals(
            "https://example.com/Search?q=light",
            WebsiteDarkMode.pageKey("https://Example.COM/Search?q=light#top"),
        )
        assertEquals(
            "https://example.com/",
            WebsiteDarkMode.pageKey("https://example.com"),
        )
        assertEquals("", WebsiteDarkMode.pageKey("   "))
    }

    @Test
    fun `parseApplyOutcome decodes JS string results and errors safely`() {
        assertEquals(ApplyOutcome.APPLIED to null, WebsiteDarkMode.parseApplyOutcome("\"APPLIED\""))
        assertEquals(ApplyOutcome.NATIVE_DARK to null, WebsiteDarkMode.parseApplyOutcome("\"NATIVE_DARK\""))
        assertEquals(ApplyOutcome.DEFERRED to null, WebsiteDarkMode.parseApplyOutcome("\"DEFERRED\""))
        assertEquals(ApplyOutcome.SKIPPED to null, WebsiteDarkMode.parseApplyOutcome("\"SKIPPED\""))
        assertEquals(ApplyOutcome.DEFERRED to null, WebsiteDarkMode.parseApplyOutcome("null"))
        assertEquals(ApplyOutcome.DEFERRED to null, WebsiteDarkMode.parseApplyOutcome(null))

        val (errOutcome, msg) = WebsiteDarkMode.parseApplyOutcome("\"ERROR:TypeError in observer\"")
        assertEquals(ApplyOutcome.ERROR, errOutcome)
        assertEquals("TypeError in observer", msg)
    }

    @Test
    fun `apply script enforces single style element, bounded childList-only observer, media safety, and native dark fallback`() {
        val css = WebsiteDarkMode.DARK_MODE_CSS
        assertFalse(css.contains("invert("))
        assertFalse(css.contains("*{"))

        val applyJs = WebsiteDarkMode.buildApplyJs()
        assertTrue(applyJs.contains("STYLE_ID='${WebsiteDarkMode.STYLE_ID}'"))
        assertTrue(applyJs.contains("document.getElementById(STYLE_ID)"))
        assertTrue(applyJs.contains("if(state.applying) return 'SKIPPED'"))
        assertTrue(applyJs.contains("obs.observe(document.body||root,{childList:true,subtree:true})"))
        assertFalse(applyJs.contains("attributes:true"))
        assertFalse(applyJs.contains("characterData:true"))
        assertTrue(applyJs.contains("addEventListener('pagehide'"))
        assertTrue(applyJs.contains("addEventListener('beforeunload'"))
        assertTrue(applyJs.contains("SKIP_TAGS"))
        assertTrue(applyJs.contains("IMG:1,VIDEO:1,PICTURE:1,CANVAS:1,SVG:1"))
        assertTrue(applyJs.contains("hasNativeDarkTheme()"))
        assertTrue(applyJs.contains("existing.disabled=true"))
        assertTrue(applyJs.contains("backgroundImage!=='none'"))
        assertTrue(applyJs.contains("return 'ERROR:'"))
    }

    @Test
    fun `disable script disconnects observer, removes managed style, and restores original inline styles`() {
        val disableJs = WebsiteDarkMode.buildDisableJs()
        assertTrue(disableJs.contains("st.observer.disconnect()"))
        assertTrue(disableJs.contains("document.getElementById('${WebsiteDarkMode.STYLE_ID}')"))
        assertTrue(disableJs.contains("data-eddy-orig-bg"))
        assertTrue(disableJs.contains("data-eddy-orig-col"))
        assertTrue(disableJs.contains("root.removeAttribute('${WebsiteDarkMode.ROOT_ATTR}')"))
        assertTrue(disableJs.contains("return 'DISABLED'"))
    }

    @Test
    fun `SiteFeature DARKEN updates SiteSettings cleanly and preserves isEmpty semantics`() {
        val initial = SiteSettings(host = "example.com")
        assertNull(initial.get(SiteFeature.DARKEN))
        assertTrue(initial.isEmpty)

        val optedOut = initial.with(SiteFeature.DARKEN, SiteSettings.BLOCK)
        assertEquals(SiteSettings.BLOCK, optedOut.get(SiteFeature.DARKEN))
        assertFalse(optedOut.isEmpty)

        val reset = optedOut.with(SiteFeature.DARKEN, null)
        assertNull(reset.get(SiteFeature.DARKEN))
        assertTrue(reset.isEmpty)
    }

    @Test
    fun `cosmetic and desktop viewport startup scripts guard null roots, duplicates, and observer lifecycle`() {
        assertTrue(WebViewFactory.COSMETIC_SCRIPT.contains(WebViewFactory.COSMETIC_STYLE_ID))
        assertTrue(WebViewFactory.COSMETIC_SCRIPT.contains("getElementById(id)"))
        assertTrue(WebViewFactory.COSMETIC_SCRIPT.contains("if(!t)return"))
        assertTrue(WebViewFactory.COSMETIC_SCRIPT.contains("try{"))

        assertTrue(WebViewFactory.DESKTOP_VIEWPORT_SCRIPT.contains("DOMContentLoaded"))
        assertTrue(WebViewFactory.DESKTOP_VIEWPORT_SCRIPT.contains("pagehide"))
        assertTrue(WebViewFactory.DESKTOP_VIEWPORT_SCRIPT.contains("beforeunload"))
        assertTrue(WebViewFactory.DESKTOP_VIEWPORT_SCRIPT.contains("o.disconnect()"))
        assertTrue(WebViewFactory.DESKTOP_VIEWPORT_SCRIPT.contains("try{"))
    }
}
