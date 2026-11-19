package app.eddy.browser.home

import app.eddy.browser.data.database.SiteVisit
import app.eddy.browser.util.UrlUtils
import kotlin.math.ln

data class RecommendedSite(
    val host: String,
    val title: String,
    val url: String,
    val fromHistory: Boolean = false,
)

/**
 * Computes local website recommendations by combining browsing frequency and recency with a
 * curated fallback pool. All scoring happens strictly on-device.
 */
object WebsiteRecommender {
    const val DEFAULT_LIMIT = 8

    private val knownNames = mapOf(
        "youtube.com" to "YouTube",
        "m.youtube.com" to "YouTube",
        "youtu.be" to "YouTube",
        "instagram.com" to "Instagram",
        "discord.com" to "Discord",
        "reddit.com" to "Reddit",
        "old.reddit.com" to "Reddit",
        "github.com" to "GitHub",
        "wikipedia.org" to "Wikipedia",
        "en.wikipedia.org" to "Wikipedia",
        "stackoverflow.com" to "Stack Overflow",
        "news.ycombinator.com" to "Hacker News",
        "twitch.tv" to "Twitch",
        "spotify.com" to "Spotify",
        "open.spotify.com" to "Spotify",
        "developer.mozilla.org" to "MDN Docs",
        "archive.org" to "Internet Archive",
        "x.com" to "X",
        "twitter.com" to "X",
        "linkedin.com" to "LinkedIn",
        "amazon.com" to "Amazon",
        "netflix.com" to "Netflix",
        "google.com" to "Google",
        "duckduckgo.com" to "DuckDuckGo",
        "nytimes.com" to "NY Times",
        "bbc.com" to "BBC",
        "bbc.co.uk" to "BBC",
        "medium.com" to "Medium",
    )

    val defaultSeeds: List<RecommendedSite> = listOf(
        RecommendedSite("youtube.com", "YouTube", "https://www.youtube.com"),
        RecommendedSite("instagram.com", "Instagram", "https://www.instagram.com"),
        RecommendedSite("discord.com", "Discord", "https://discord.com"),
        RecommendedSite("reddit.com", "Reddit", "https://www.reddit.com"),
        RecommendedSite("github.com", "GitHub", "https://github.com"),
        RecommendedSite("wikipedia.org", "Wikipedia", "https://www.wikipedia.org"),
        RecommendedSite("stackoverflow.com", "Stack Overflow", "https://stackoverflow.com"),
        RecommendedSite("news.ycombinator.com", "Hacker News", "https://news.ycombinator.com"),
        RecommendedSite("twitch.tv", "Twitch", "https://www.twitch.tv"),
        RecommendedSite("open.spotify.com", "Spotify", "https://open.spotify.com"),
        RecommendedSite("developer.mozilla.org", "MDN Docs", "https://developer.mozilla.org"),
        RecommendedSite("archive.org", "Internet Archive", "https://archive.org"),
    )

    fun recommend(
        historySites: List<SiteVisit>,
        excludedHosts: Set<String> = emptySet(),
        limit: Int = DEFAULT_LIMIT,
        nowMs: Long = System.currentTimeMillis(),
    ): List<RecommendedSite> {
        if (limit <= 0) return emptyList()

        val excluded = excludedHosts.map(::canonicalHost).filter { it.isNotEmpty() }.toSet()
        val seenHosts = HashSet<String>(limit * 2)
        seenHosts.addAll(excluded)

        data class ScoredSite(val site: RecommendedSite, val canonical: String, val score: Double)

        val historyRanked = historySites
            .mapNotNull { visit ->
                if (!isRecommendableUrl(visit.url)) return@mapNotNull null
                val rawHost = UrlUtils.displayHost(visit.url).ifEmpty { visit.host.trim().lowercase().removePrefix("www.") }
                val canonical = canonicalHost(rawHost)
                if (canonical.isEmpty() || !isValidPublicHost(canonical)) return@mapNotNull null

                val score = scoreVisit(visit.visits, visit.lastVisit, nowMs)
                val originUrl = UrlUtils.origin(visit.url) ?: "https://$rawHost"
                val title = deriveTitle(canonical, rawHost, visit.title)
                ScoredSite(
                    site = RecommendedSite(
                        host = rawHost,
                        title = title,
                        url = originUrl,
                        fromHistory = true,
                    ),
                    canonical = canonical,
                    score = score,
                )
            }
            .sortedByDescending { it.score }

        val result = ArrayList<RecommendedSite>(limit)
        for (item in historyRanked) {
            if (result.size >= limit) break
            if (seenHosts.add(item.canonical)) {
                result.add(item.site)
            }
        }

        if (result.size < limit) {
            for (seed in defaultSeeds) {
                if (result.size >= limit) break
                val canonical = canonicalHost(seed.host)
                if (seenHosts.add(canonical)) {
                    result.add(seed)
                }
            }
        }

        // If user pinned many seeds and history was empty, still honor limit with remaining non-duplicate seeds if any
        return result
    }

    internal fun scoreVisit(visits: Int, lastVisitMs: Long, nowMs: Long): Double {
        val safeVisits = visits.coerceAtLeast(1).toDouble()
        val ageHours = ((nowMs - lastVisitMs).coerceAtLeast(0L)) / 3_600_000.0
        val recencyBoost = 1.0 / (1.0 + ageHours / 48.0)
        val frequencyScore = ln(1.0 + safeVisits)
        return frequencyScore * (0.4 + 0.6 * recencyBoost) + recencyBoost * 1.5
    }

    internal fun canonicalHost(host: String): String {
        val cleaned = host.trim().lowercase().removePrefix("www.").removePrefix("m.")
        return when (cleaned) {
            "youtu.be" -> "youtube.com"
            "old.reddit.com" -> "reddit.com"
            "en.wikipedia.org" -> "wikipedia.org"
            "open.spotify.com" -> "spotify.com"
            "twitter.com" -> "x.com"
            "bbc.co.uk" -> "bbc.com"
            else -> cleaned
        }
    }

    internal fun isRecommendableUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.isEmpty() || !UrlUtils.isWebUrl(trimmed)) return false
        if (UrlUtils.isLocalHost(trimmed)) return false
        return true
    }

    private fun isValidPublicHost(host: String): Boolean {
        if (host.isBlank() || '.' !in host) return false
        if (host.startsWith('.') || host.endsWith('.')) return false
        if (host.any { it.isWhitespace() }) return false
        return true
    }

    private fun deriveTitle(canonical: String, rawHost: String, pageTitle: String): String {
        knownNames[canonical]?.let { return it }
        knownNames[rawHost]?.let { return it }

        val cleanedTitle = pageTitle.trim()
        if (cleanedTitle.isNotEmpty() && !cleanedTitle.startsWith("http", ignoreCase = true)) {
            val short = cleanedTitle
                .split(" - ", " | ", " · ", " — ", " – ")
                .lastOrNull()
                ?.trim()
                .orEmpty()
            if (short.length in 2..20) return short
            if (cleanedTitle.length in 2..20) return cleanedTitle
        }

        val label = rawHost.substringBefore('.')
        return label.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            .ifEmpty { rawHost }
    }
}
