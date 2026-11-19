package app.eddy.browser.browser

import app.eddy.browser.bookmarks.BookmarkManager
import app.eddy.browser.data.models.Settings
import app.eddy.browser.history.HistoryManager
import app.eddy.browser.home.WebsiteRecommender
import app.eddy.browser.util.UrlUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

enum class SuggestionKind { CLIPBOARD, BOOKMARK, HISTORY, DOMAIN, SEARCH }

class Suggestion(
    val kind: SuggestionKind,
    val text: String,
    val title: String,
    val subtitle: String = "",
    val host: String = SuggestionSource.extractHost(text),
)

/** Local suggestions (bookmarks, history, popular domains) are instant; remote DuckDuckGo completions arrive afterwards. */
class SuggestionSource(
    private val history: HistoryManager,
    private val bookmarks: BookmarkManager,
    private val settings: () -> Settings,
) {
    suspend fun initial(incognito: Boolean): List<Suggestion> {
        val recentHistory = if (!incognito) {
            history.recentSites(3).first()
                .filter { WebsiteRecommender.isRecommendableUrl(it.url) }
                .map { Suggestion(SuggestionKind.HISTORY, it.url, it.title.ifBlank { it.host }, it.url) }
        } else {
            emptyList()
        }
        val seenHosts = recentHistory.mapTo(HashSet()) { UrlUtils.displayHost(it.text) }
        val domains = WebsiteRecommender.defaultSeeds
            .filter { it.host !in seenHosts }
            .take(5 - recentHistory.size)
            .map { Suggestion(SuggestionKind.DOMAIN, it.url, it.title, it.host) }
        return recentHistory + domains
    }

    suspend fun local(query: String): List<Suggestion> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        val marked = bookmarks.suggest(q, 3).map { Suggestion(SuggestionKind.BOOKMARK, it.url, it.title, it.url) }
        val seenUrls = marked.mapTo(HashSet()) { it.text }
        val visited = history.suggest(q, 5).filter { it.url !in seenUrls }.take(4)
            .map { Suggestion(SuggestionKind.HISTORY, it.url, it.title.ifBlank { it.host }, it.url) }
        val seenHosts = (marked + visited).mapTo(HashSet()) { UrlUtils.displayHost(it.text) }
        val domains = domainSuggestions(q, seenHosts, limit = 2)
        return marked + visited + domains
    }

    suspend fun remote(query: String): List<Suggestion> {
        val template = settings().searchEngine.suggestUrl ?: return emptyList()
        if (query.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            runCatching {
                currentCoroutineContext().ensureActive()
                val conn = URL(template.replace("%s", URLEncoder.encode(query, "UTF-8"))).openConnection() as HttpURLConnection
                conn.connectTimeout = 2500
                conn.readTimeout = 2500
                conn.setRequestProperty("Accept", "application/json")
                try {
                    currentCoroutineContext().ensureActive()
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    currentCoroutineContext().ensureActive()
                    val list = JSONArray(body).optJSONArray(1) ?: return@runCatching emptyList()
                    (0 until minOf(list.length(), 6)).mapNotNull { i ->
                        val item = list.opt(i)
                        val text = if (item is org.json.JSONObject) item.optString("phrase") else item?.toString()
                        text?.takeIf { it.isNotBlank() }?.let { Suggestion(SuggestionKind.SEARCH, it, it) }
                    }
                } finally {
                    conn.disconnect()
                }
            }.getOrDefault(emptyList())
        }
    }

    companion object {
        internal fun extractHost(input: String): String {
            val trimmed = input.trim()
            if (trimmed.isEmpty() || !UrlUtils.isUrl(trimmed)) return ""
            if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                return UrlUtils.displayHost(trimmed)
            }
            if ("://" in trimmed || trimmed.startsWith("about:", ignoreCase = true) ||
                trimmed.startsWith("file:", ignoreCase = true) || trimmed.startsWith("data:", ignoreCase = true) ||
                trimmed.startsWith("view-source:", ignoreCase = true)
            ) {
                return ""
            }
            return UrlUtils.displayHost("https://$trimmed")
        }

        internal fun domainSuggestions(query: String, excludedHosts: Set<String> = emptySet(), limit: Int = 2): List<Suggestion> {
            val q = query.trim().lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.")
            if (q.length < 2) return emptyList()
            val matches = WebsiteRecommender.defaultSeeds
                .filter { seed ->
                    seed.host !in excludedHosts &&
                        (seed.host.startsWith(q) || seed.title.lowercase().startsWith(q) || seed.host.contains(q))
                }
                .take(limit)
                .map { Suggestion(SuggestionKind.DOMAIN, it.url, it.host, it.title) }
            if (matches.isNotEmpty() || limit <= 0) return matches
            // Suggest completing a typed domain prefix without spaces (e.g. "example." -> "example.com")
            if ('.' in q && !q.endsWith('.') && UrlUtils.isUrl("https://$q")) {
                return listOf(Suggestion(SuggestionKind.DOMAIN, "https://$q", q, "https://$q"))
            }
            return emptyList()
        }
    }
}
