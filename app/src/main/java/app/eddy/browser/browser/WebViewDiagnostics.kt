package app.eddy.browser.browser

import android.util.Log
import android.webkit.ConsoleMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class WebViewDiagnosticKind {
    RENDERER_CRASH,
    CONSOLE_ERROR,
    CONSOLE_WARNING,
    DARK_MODE_ERROR,
}

enum class ConsoleDiagnosticSeverity {
    ERROR,
    WARNING,
    IGNORED,
}

data class WebViewDiagnosticEntry(
    val timestampMs: Long,
    val kind: WebViewDiagnosticKind,
    val tabId: String,
    val incognito: Boolean,
    val url: String,
    val source: String,
    val lineNumber: Int? = null,
    val message: String,
    val didCrash: Boolean? = null,
    val rendererPriorityAtExit: Int? = null,
    val darkModeEnabled: Boolean = false,
    val failedDarkModePageKey: String? = null,
)

data class WebViewDiagnosticReport(
    val generatedAtMs: Long,
    val totalEntries: Int,
    val rendererCrashCount: Int,
    val consoleErrorCount: Int,
    val consoleWarningCount: Int,
    val darkModeErrorCount: Int,
    val entries: List<WebViewDiagnosticEntry>,
) {
    fun formatSummary(): String = buildString {
        appendLine("WebView Diagnostic Report (entries=$totalEntries, crashes=$rendererCrashCount, consoleErrors=$consoleErrorCount, consoleWarnings=$consoleWarningCount, darkModeErrors=$darkModeErrorCount)")
        for (entry in entries) {
            appendLine(WebViewDiagnosticRecorder.formatEntryLine(entry))
        }
    }.trimEnd()

    fun formatLogcatLines(pid: Int): String =
        WebViewDiagnosticRecorder.formatEntriesAsLogcatLines(entries, pid)
}

/**
 * Thread-safe bounded diagnostic collector for WebView renderer process terminations,
 * page console errors/warnings, and dark-mode injection failures.
 *
 * Strictly redacts URLs, source IDs, and page keys whenever [incognito] is true, and strips
 * queries/fragments from regular URLs so no credentials or private parameters are ever logged.
 */
class WebViewDiagnosticRecorder(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val logger: (level: Char, tag: String, message: String) -> Unit = ::defaultAndroidLogger,
) {
    private val entries = ArrayDeque<WebViewDiagnosticEntry>(maxEntries.coerceAtLeast(1))

    @Synchronized
    fun recordRendererCrash(
        tabId: String,
        incognito: Boolean,
        viewUrl: String?,
        didCrash: Boolean,
        rendererPriorityAtExit: Int,
        darkModeEnabled: Boolean,
        failedDarkModePageKey: String?,
    ): WebViewDiagnosticEntry {
        val safeUrl = sanitizeUrl(viewUrl, incognito)
        val safeFailedKey = sanitizePageKey(failedDarkModePageKey, incognito)
        val priorityLabel = rendererPriorityLabel(rendererPriorityAtExit)
        val msg = buildString {
            append("didCrash=").append(didCrash)
            append(" priority=").append(priorityLabel)
            append(" darkMode=").append(darkModeEnabled)
            if (!safeFailedKey.isNullOrEmpty()) append(" darkFailedKey=").append(safeFailedKey)
            if (safeUrl.isNotEmpty()) append(" url=").append(safeUrl)
        }
        val entry = WebViewDiagnosticEntry(
            timestampMs = clock(),
            kind = WebViewDiagnosticKind.RENDERER_CRASH,
            tabId = tabId,
            incognito = incognito,
            url = safeUrl,
            source = "renderer",
            message = truncate(msg, MAX_MESSAGE_CHARS),
            didCrash = didCrash,
            rendererPriorityAtExit = rendererPriorityAtExit,
            darkModeEnabled = darkModeEnabled,
            failedDarkModePageKey = safeFailedKey,
        )
        appendEntry(entry)
        safeLog('E', TAG, formatEntryLine(entry))
        return entry
    }

    @Synchronized
    fun recordConsoleDiagnostic(
        severity: ConsoleDiagnosticSeverity,
        message: String?,
        sourceId: String?,
        lineNumber: Int,
        pageUrl: String?,
        tabId: String = "",
        incognito: Boolean = false,
        darkModeEnabled: Boolean = false,
        failedDarkModePageKey: String? = null,
    ): WebViewDiagnosticEntry? {
        val kind = when (severity) {
            ConsoleDiagnosticSeverity.ERROR -> WebViewDiagnosticKind.CONSOLE_ERROR
            ConsoleDiagnosticSeverity.WARNING -> WebViewDiagnosticKind.CONSOLE_WARNING
            ConsoleDiagnosticSeverity.IGNORED -> return null
        }
        val safeUrl = sanitizeUrl(pageUrl, incognito)
        val safeSource = sanitizeSource(sourceId, incognito)
        val safeFailedKey = sanitizePageKey(failedDarkModePageKey, incognito)
        val rawMsg = message.orEmpty().trim().ifEmpty { "<empty console message>" }
        val sanitizedMsg = truncate(sanitizeConsoleText(rawMsg, incognito), MAX_MESSAGE_CHARS)
        val entry = WebViewDiagnosticEntry(
            timestampMs = clock(),
            kind = kind,
            tabId = tabId,
            incognito = incognito,
            url = safeUrl,
            source = safeSource,
            lineNumber = lineNumber.takeIf { it > 0 },
            message = sanitizedMsg,
            darkModeEnabled = darkModeEnabled,
            failedDarkModePageKey = safeFailedKey,
        )
        appendEntry(entry)
        val level = if (kind == WebViewDiagnosticKind.CONSOLE_ERROR) 'E' else 'W'
        safeLog(level, TAG, formatEntryLine(entry))
        return entry
    }

    @Synchronized
    fun recordDarkModeError(
        tabId: String,
        incognito: Boolean,
        pageUrl: String?,
        reason: String?,
        darkModeEnabled: Boolean = true,
    ): WebViewDiagnosticEntry {
        val safeUrl = sanitizeUrl(pageUrl, incognito)
        val cleanReason = truncate(reason.orEmpty().trim().ifEmpty { "dark_mode_failed" }, MAX_MESSAGE_CHARS)
        val entry = WebViewDiagnosticEntry(
            timestampMs = clock(),
            kind = WebViewDiagnosticKind.DARK_MODE_ERROR,
            tabId = tabId,
            incognito = incognito,
            url = safeUrl,
            source = "WebsiteDarkMode",
            message = cleanReason,
            darkModeEnabled = darkModeEnabled,
            failedDarkModePageKey = if (incognito) INCOGNITO_MARKER else WebsiteDarkMode.pageKey(safeUrl).ifEmpty { null },
        )
        appendEntry(entry)
        safeLog('E', TAG, formatEntryLine(entry))
        return entry
    }

    @Synchronized
    fun snapshot(
        tabId: String? = null,
        kind: WebViewDiagnosticKind? = null,
    ): List<WebViewDiagnosticEntry> = entries.filter { entry ->
        (tabId == null || entry.tabId == tabId) &&
            (kind == null || entry.kind == kind)
    }

    @Synchronized
    fun buildReport(tabId: String? = null): WebViewDiagnosticReport {
        val filtered = snapshot(tabId = tabId)
        return WebViewDiagnosticReport(
            generatedAtMs = clock(),
            totalEntries = filtered.size,
            rendererCrashCount = filtered.count { it.kind == WebViewDiagnosticKind.RENDERER_CRASH },
            consoleErrorCount = filtered.count { it.kind == WebViewDiagnosticKind.CONSOLE_ERROR },
            consoleWarningCount = filtered.count { it.kind == WebViewDiagnosticKind.CONSOLE_WARNING },
            darkModeErrorCount = filtered.count { it.kind == WebViewDiagnosticKind.DARK_MODE_ERROR },
            entries = filtered,
        )
    }

    @Synchronized
    fun formatAsLogcatLines(pid: Int): String =
        formatEntriesAsLogcatLines(entries.toList(), pid)

    @Synchronized
    fun clear() {
        entries.clear()
    }

    private fun appendEntry(entry: WebViewDiagnosticEntry) {
        val cap = maxEntries.coerceAtLeast(1)
        while (entries.size >= cap) {
            entries.removeFirst()
        }
        entries.addLast(entry)
    }

    private fun safeLog(level: Char, tag: String, message: String) {
        runCatching { logger(level, tag, message) }
    }

    companion object {
        const val TAG = "EddyWebViewDiag"
        const val INCOGNITO_MARKER = "<incognito>"
        const val DEFAULT_MAX_ENTRIES = 120
        private const val MAX_URL_CHARS = 240
        private const val MAX_MESSAGE_CHARS = 600
        private val INLINE_URL_REGEX = Regex("""https?://[^\s"'<>]+""", RegexOption.IGNORE_CASE)

        fun sanitizeUrl(url: String?, incognito: Boolean): String {
            if (incognito) return INCOGNITO_MARKER
            val trimmed = url.orEmpty().trim()
            if (trimmed.isEmpty()) return ""
            val stripped = trimmed.substringBefore('#').substringBefore('?')
            return truncate(stripped, MAX_URL_CHARS)
        }

        fun sanitizeSource(sourceId: String?, incognito: Boolean): String {
            if (incognito) return INCOGNITO_MARKER
            val trimmed = sourceId.orEmpty().trim()
            if (trimmed.isEmpty()) return ""
            val stripped = trimmed.substringBefore('#').substringBefore('?')
            return truncate(stripped, MAX_URL_CHARS)
        }

        fun sanitizePageKey(pageKey: String?, incognito: Boolean): String? {
            if (pageKey.isNullOrBlank()) return null
            if (incognito) return INCOGNITO_MARKER
            return truncate(pageKey.substringBefore('#').substringBefore('?'), MAX_URL_CHARS)
        }

        fun sanitizeConsoleText(message: String, incognito: Boolean): String {
            if (!incognito) return message.replace('\n', ' ').replace('\r', ' ')
            return INLINE_URL_REGEX.replace(message, INCOGNITO_MARKER)
                .replace('\n', ' ')
                .replace('\r', ' ')
        }

        fun mapConsoleLevel(level: ConsoleMessage.MessageLevel?): ConsoleDiagnosticSeverity = when (level) {
            ConsoleMessage.MessageLevel.ERROR -> ConsoleDiagnosticSeverity.ERROR
            ConsoleMessage.MessageLevel.WARNING -> ConsoleDiagnosticSeverity.WARNING
            else -> ConsoleDiagnosticSeverity.IGNORED
        }

        fun rendererPriorityLabel(priority: Int): String = when (priority) {
            0 -> "BOUND_WAIVED"
            1 -> "BOUND_BACKGROUND"
            2 -> "BOUND_FOREGROUND"
            else -> "PRIORITY_$priority"
        }

        fun formatEntryLine(entry: WebViewDiagnosticEntry): String = buildString {
            append('[').append(entry.kind.name).append(']')
            if (entry.tabId.isNotEmpty()) append(" tab=").append(entry.tabId.take(8))
            if (entry.incognito) append(" incognito=true")
            append(" darkMode=").append(entry.darkModeEnabled)
            if (entry.kind != WebViewDiagnosticKind.RENDERER_CRASH && entry.url.isNotEmpty()) {
                append(" url=").append(entry.url)
            }
            if (entry.source.isNotEmpty() && entry.kind != WebViewDiagnosticKind.RENDERER_CRASH) {
                append(" src=").append(entry.source)
                entry.lineNumber?.let { append(':').append(it) }
            }
            if (!entry.failedDarkModePageKey.isNullOrEmpty() && entry.kind != WebViewDiagnosticKind.RENDERER_CRASH) {
                append(" darkFailedKey=").append(entry.failedDarkModePageKey)
            }
            append(" | ").append(entry.message)
        }

        fun formatEntriesAsLogcatLines(entries: List<WebViewDiagnosticEntry>, pid: Int): String {
            if (entries.isEmpty()) return ""
            val formatter = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
            return entries.joinToString("\n") { entry ->
                val level = when (entry.kind) {
                    WebViewDiagnosticKind.CONSOLE_WARNING -> 'W'
                    else -> 'E'
                }
                val stamp = formatter.format(Date(entry.timestampMs))
                "$stamp $level/$TAG( $pid): ${formatEntryLine(entry)}"
            }
        }

        private fun truncate(value: String, maxChars: Int): String =
            if (value.length <= maxChars) value else value.take(maxChars - 1) + "…"

        private fun defaultAndroidLogger(level: Char, tag: String, message: String) {
            when (level) {
                'E', 'F' -> Log.e(tag, message)
                'W' -> Log.w(tag, message)
                'I' -> Log.i(tag, message)
                else -> Log.d(tag, message)
            }
        }
    }
}

/** App-wide singleton recorder used by WebView callbacks and diagnostic log export. */
object WebViewDiagnostics {
    val recorder = WebViewDiagnosticRecorder()

    fun snapshot(tabId: String? = null, kind: WebViewDiagnosticKind? = null): List<WebViewDiagnosticEntry> =
        recorder.snapshot(tabId, kind)

    fun buildReport(tabId: String? = null): WebViewDiagnosticReport =
        recorder.buildReport(tabId)

    fun formatAsLogcatLines(pid: Int): String =
        recorder.formatAsLogcatLines(pid)

    fun clear() = recorder.clear()
}

/** Collects and logs a WebView renderer termination report (`onRenderProcessGone`). */
fun collectWebViewCrashReport(
    tab: BrowserTab?,
    viewUrl: String?,
    didCrash: Boolean,
    rendererPriorityAtExit: Int,
    darkModeEnabled: Boolean,
    failedDarkModePageKey: String?,
    target: WebViewDiagnosticRecorder = WebViewDiagnostics.recorder,
): WebViewDiagnosticEntry = target.recordRendererCrash(
    tabId = tab?.id.orEmpty(),
    incognito = tab?.incognito == true,
    viewUrl = viewUrl ?: tab?.url,
    didCrash = didCrash,
    rendererPriorityAtExit = rendererPriorityAtExit,
    darkModeEnabled = darkModeEnabled,
    failedDarkModePageKey = failedDarkModePageKey,
)

/** Collects and logs WebView JavaScript console errors and warnings (`onConsoleMessage`). */
fun collectWebViewConsoleError(
    tab: BrowserTab?,
    consoleMessage: ConsoleMessage,
    darkModeEnabled: Boolean,
    failedDarkModePageKey: String?,
    target: WebViewDiagnosticRecorder = WebViewDiagnostics.recorder,
): WebViewDiagnosticEntry? = target.recordConsoleDiagnostic(
    severity = WebViewDiagnosticRecorder.mapConsoleLevel(consoleMessage.messageLevel()),
    message = consoleMessage.message(),
    sourceId = consoleMessage.sourceId(),
    lineNumber = consoleMessage.lineNumber(),
    pageUrl = tab?.url,
    tabId = tab?.id.orEmpty(),
    incognito = tab?.incognito == true,
    darkModeEnabled = darkModeEnabled,
    failedDarkModePageKey = failedDarkModePageKey,
)

/** Collects and logs custom website dark-mode evaluation/application failures. */
fun collectDarkModeDiagnosticError(
    tab: BrowserTab?,
    pageUrl: String?,
    reason: String?,
    darkModeEnabled: Boolean = true,
    target: WebViewDiagnosticRecorder = WebViewDiagnostics.recorder,
): WebViewDiagnosticEntry = target.recordDarkModeError(
    tabId = tab?.id.orEmpty(),
    incognito = tab?.incognito == true,
    pageUrl = pageUrl ?: tab?.url,
    reason = reason,
    darkModeEnabled = darkModeEnabled,
)

/** Merges raw process logcat output with buffered WebView diagnostic lines without duplicates. */
internal fun mergeAppLogs(logcatText: String, diagLines: String): String {
    val cleanLogcat = logcatText.trimEnd()
    val cleanDiag = diagLines.trim()
    if (cleanDiag.isEmpty()) return cleanLogcat
    if (cleanLogcat.isEmpty()) return cleanDiag
    val missing = cleanDiag.lineSequence().filter { line ->
        val payload = line.substringAfter("): ", line)
        payload.isNotBlank() && !cleanLogcat.contains(payload)
    }.toList()
    if (missing.isEmpty()) return cleanLogcat
    return buildString {
        append(cleanLogcat)
        append('\n')
        append(missing.joinToString("\n"))
    }
}
