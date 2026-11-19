package app.eddy.browser

import android.webkit.ConsoleMessage
import app.eddy.browser.browser.BrowserTab
import app.eddy.browser.browser.ConsoleDiagnosticSeverity
import app.eddy.browser.browser.LogEntry
import app.eddy.browser.browser.WebViewDiagnosticKind
import app.eddy.browser.browser.WebViewDiagnosticRecorder
import app.eddy.browser.browser.collectDarkModeDiagnosticError
import app.eddy.browser.browser.collectWebViewCrashReport
import app.eddy.browser.browser.mergeAppLogs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewDiagnosticsTest {

    @Test
    fun `collects and logs renderer crash report with dark mode context and sanitized url`() {
        var now = 1_700_000_000_000L
        val logs = mutableListOf<Triple<Char, String, String>>()
        val recorder = WebViewDiagnosticRecorder(
            maxEntries = 10,
            clock = { now++ },
            logger = { lvl, tag, msg -> logs += Triple(lvl, tag, msg) },
        )
        val tab = BrowserTab(id = "tab-12345678", incognito = false, initialUrl = "https://example.com/page?token=secret#frag")

        val entry = collectWebViewCrashReport(
            tab = tab,
            viewUrl = "https://example.com/page?token=secret#frag",
            didCrash = true,
            rendererPriorityAtExit = 2,
            darkModeEnabled = true,
            failedDarkModePageKey = "https://example.com/page?token=secret",
            target = recorder,
        )

        assertEquals(WebViewDiagnosticKind.RENDERER_CRASH, entry.kind)
        assertEquals("https://example.com/page", entry.url)
        assertFalse(entry.message.contains("secret"))
        assertTrue(entry.message.contains("didCrash=true"))
        assertTrue(entry.message.contains("priority=BOUND_FOREGROUND"))
        assertTrue(entry.message.contains("darkMode=true"))
        assertEquals(1, logs.size)
        assertEquals('E', logs.first().first)
        assertEquals(WebViewDiagnosticRecorder.TAG, logs.first().second)

        val report = recorder.buildReport()
        assertEquals(1, report.rendererCrashCount)
        assertEquals(1, report.totalEntries)
        assertTrue(report.formatSummary().contains("RENDERER_CRASH"))
    }

    @Test
    fun `redacts incognito urls, sourceIds, inline urls, and dark mode keys completely`() {
        val logs = mutableListOf<Triple<Char, String, String>>()
        val recorder = WebViewDiagnosticRecorder(
            clock = { 1_700_000_001_000L },
            logger = { lvl, tag, msg -> logs += Triple(lvl, tag, msg) },
        )
        val incognitoTab = BrowserTab(id = "incog-tab-1", incognito = true, initialUrl = "https://private.secret.example/login?user=alice")

        val consoleEntry = recorder.recordConsoleDiagnostic(
            severity = ConsoleDiagnosticSeverity.ERROR,
            message = "Uncaught Error fetching https://private.secret.example/api?key=xyz",
            sourceId = "https://private.secret.example/app.js?v=2",
            lineNumber = 42,
            pageUrl = incognitoTab.url,
            tabId = incognitoTab.id,
            incognito = true,
            darkModeEnabled = true,
            failedDarkModePageKey = "https://private.secret.example/login",
        )
        assertNotNull(consoleEntry)
        assertEquals("<incognito>", consoleEntry!!.url)
        assertEquals("<incognito>", consoleEntry.source)
        assertEquals("<incognito>", consoleEntry.failedDarkModePageKey)
        assertFalse(consoleEntry.message.contains("private.secret.example"))
        assertTrue(consoleEntry.message.contains("<incognito>"))

        val crashEntry = collectWebViewCrashReport(
            tab = incognitoTab,
            viewUrl = incognitoTab.url,
            didCrash = true,
            rendererPriorityAtExit = 1,
            darkModeEnabled = true,
            failedDarkModePageKey = "https://private.secret.example/login",
            target = recorder,
        )
        assertEquals("<incognito>", crashEntry.url)
        assertEquals("<incognito>", crashEntry.failedDarkModePageKey)
        assertFalse(logs.any { it.third.contains("private.secret.example") })
    }

    @Test
    fun `ignores non-error non-warning console levels and respects bounded buffer capacity`() {
        val recorder = WebViewDiagnosticRecorder(maxEntries = 3, clock = { 1_700_000_002_000L }, logger = { _, _, _ -> })

        assertNull(
            recorder.recordConsoleDiagnostic(
                severity = WebViewDiagnosticRecorder.mapConsoleLevel(ConsoleMessage.MessageLevel.LOG),
                message = "harmless log",
                sourceId = "https://example.com/a.js",
                lineNumber = 1,
                pageUrl = "https://example.com",
            ),
        )
        assertEquals(0, recorder.snapshot().size)

        recorder.recordConsoleDiagnostic(
            severity = ConsoleDiagnosticSeverity.WARNING,
            message = "warn 1",
            sourceId = "https://example.com/a.js",
            lineNumber = 10,
            pageUrl = "https://example.com",
            tabId = "t1",
        )
        recorder.recordDarkModeError("t1", false, "https://example.com/white", "CSS injection failed")
        recorder.recordConsoleDiagnostic(
            severity = ConsoleDiagnosticSeverity.ERROR,
            message = "err 2",
            sourceId = "https://example.com/b.js",
            lineNumber = 20,
            pageUrl = "https://example.com",
            tabId = "t1",
        )
        recorder.recordRendererCrash("t1", false, "https://example.com", true, 2, true, null)

        val entries = recorder.snapshot()
        assertEquals(3, entries.size)
        assertEquals(WebViewDiagnosticKind.DARK_MODE_ERROR, entries[0].kind)
        assertEquals(WebViewDiagnosticKind.CONSOLE_ERROR, entries[1].kind)
        assertEquals(WebViewDiagnosticKind.RENDERER_CRASH, entries[2].kind)
    }

    @Test
    fun `formatted diagnostic logcat lines parse cleanly via LogEntry and merge without duplicates`() {
        var now = 1_700_000_005_000L
        val recorder = WebViewDiagnosticRecorder(clock = { val t = now; now += 1_000L; t }, logger = { _, _, _ -> })
        val tab = BrowserTab(id = "tab-log-1", incognito = false, initialUrl = "https://whitebg.example/home")
        collectDarkModeDiagnosticError(
            tab = tab,
            pageUrl = tab.url,
            reason = "MutationObserver quota exceeded",
            darkModeEnabled = true,
            target = recorder,
        )
        collectWebViewCrashReport(
            tab = tab,
            viewUrl = tab.url,
            didCrash = true,
            rendererPriorityAtExit = 2,
            darkModeEnabled = true,
            failedDarkModePageKey = "https://whitebg.example/home",
            target = recorder,
        )

        val logcatFormatted = recorder.formatAsLogcatLines(pid = 4242)
        val parsed = LogEntry.parse(logcatFormatted)
        assertEquals(2, parsed.size)
        assertEquals(WebViewDiagnosticRecorder.TAG, parsed[0].tag)
        assertEquals('E', parsed[0].level)
        assertTrue(parsed[0].message.contains("[RENDERER_CRASH]"))
        assertTrue(parsed[1].message.contains("[DARK_MODE_ERROR]"))

        val existingLogcat = "10-01 10:51:53.624 I/eddy( 4242): app started\n${logcatFormatted.lineSequence().first()}"
        val merged = mergeAppLogs(existingLogcat, logcatFormatted)
        val mergedParsed = LogEntry.parse(merged)
        assertEquals(3, mergedParsed.size)
    }
}
