package app.eddy.browser

import app.eddy.browser.data.database.SiteVisit
import app.eddy.browser.home.WebsiteRecommender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebsiteRecommenderTest {

    private val now = 1_750_000_000_000L
    private val hourMs = 3_600_000L

    @Test
    fun `empty history returns default curated seeds including required sites`() {
        val recs = WebsiteRecommender.recommend(emptyList(), nowMs = now)
        assertEquals(WebsiteRecommender.DEFAULT_LIMIT, recs.size)
        val hosts = recs.map { it.host }
        assertTrue(hosts.containsAll(listOf("youtube.com", "instagram.com", "discord.com", "reddit.com", "github.com", "wikipedia.org")))
        assertTrue(recs.none { it.fromHistory })
    }

    @Test
    fun `frequent and recent history sites outrank stale or low-visit sites`() {
        val history = listOf(
            SiteVisit("https://old.example.org/", "Old", "old.example.org", visits = 2, lastVisit = now - 720 * hourMs),
            SiteVisit("https://news.ycombinator.com/", "Hacker News", "news.ycombinator.com", visits = 18, lastVisit = now - 1 * hourMs),
            SiteVisit("https://kotlinlang.org/docs/home.html", "Kotlin", "kotlinlang.org", visits = 10, lastVisit = now - 3 * hourMs),
        )
        val recs = WebsiteRecommender.recommend(history, limit = 8, nowMs = now)
        assertEquals("news.ycombinator.com", recs[0].host)
        assertEquals("kotlinlang.org", recs[1].host)
        assertEquals("old.example.org", recs[2].host)
        assertTrue(recs[0].fromHistory)
        assertEquals(8, recs.size)
    }

    @Test
    fun `recency boosts recently visited site when visit counts are comparable`() {
        val history = listOf(
            SiteVisit("https://alpha.org/", "Alpha", "alpha.org", visits = 5, lastVisit = now - 240 * hourMs),
            SiteVisit("https://beta.org/", "Beta", "beta.org", visits = 5, lastVisit = now - 1 * hourMs),
        )
        val recs = WebsiteRecommender.recommend(history, limit = 4, nowMs = now)
        assertEquals("beta.org", recs[0].host)
        assertEquals("alpha.org", recs[1].host)
    }

    @Test
    fun `recommendations adapt dynamically as browsing activity changes`() {
        val initial = listOf(
            SiteVisit("https://alpha.org/", "Alpha", "alpha.org", visits = 6, lastVisit = now - 2 * hourMs),
            SiteVisit("https://gamma.dev/", "Gamma", "gamma.dev", visits = 2, lastVisit = now - 48 * hourMs),
        )
        val firstPass = WebsiteRecommender.recommend(initial, limit = 4, nowMs = now)
        assertEquals("alpha.org", firstPass[0].host)

        val updated = listOf(
            SiteVisit("https://alpha.org/", "Alpha", "alpha.org", visits = 6, lastVisit = now - 2 * hourMs),
            SiteVisit("https://gamma.dev/", "Gamma", "gamma.dev", visits = 20, lastVisit = now),
        )
        val secondPass = WebsiteRecommender.recommend(updated, limit = 4, nowMs = now)
        assertEquals("gamma.dev", secondPass[0].host)
    }

    @Test
    fun `deduplicates canonical domains and excludes pinned shortcuts`() {
        val history = listOf(
            SiteVisit("https://www.youtube.com/watch?v=1", "YouTube", "www.youtube.com", visits = 20, lastVisit = now - hourMs),
            SiteVisit("https://m.youtube.com/", "YouTube Mobile", "m.youtube.com", visits = 5, lastVisit = now - hourMs),
            SiteVisit("https://youtu.be/abc", "YouTube Short", "youtu.be", visits = 3, lastVisit = now - hourMs),
            SiteVisit("https://en.wikipedia.org/wiki/Android", "Android - Wikipedia", "en.wikipedia.org", visits = 7, lastVisit = now - hourMs),
            SiteVisit("https://github.com/Jayapal21/Eddy", "Eddy", "github.com", visits = 12, lastVisit = now - hourMs),
        )
        val recs = WebsiteRecommender.recommend(
            historySites = history,
            excludedHosts = setOf("github.com"),
            limit = 8,
            nowMs = now,
        )
        val hosts = recs.map { WebsiteRecommender.canonicalHost(it.host) }
        assertEquals(hosts.distinct(), hosts)
        assertFalse(hosts.contains("github.com"))
        assertEquals(1, hosts.count { it == "youtube.com" })
        assertEquals(1, hosts.count { it == "wikipedia.org" })
    }

    @Test
    fun `excludes internal invalid and local urls`() {
        val history = listOf(
            SiteVisit("about:blank", "Blank", "blank", visits = 50, lastVisit = now),
            SiteVisit("file:///sdcard/test.html", "Local", "file", visits = 40, lastVisit = now),
            SiteVisit("javascript:void(0)", "JS", "js", visits = 30, lastVisit = now),
            SiteVisit("http://localhost:8080/", "Dev", "localhost", visits = 25, lastVisit = now),
            SiteVisit("http://192.168.1.1/", "Router", "192.168.1.1", visits = 25, lastVisit = now),
            SiteVisit("not a url", "Bad", "invalid", visits = 25, lastVisit = now),
            SiteVisit("https://valid.dev/app", "Valid Dev", "valid.dev", visits = 2, lastVisit = now),
        )
        val recs = WebsiteRecommender.recommend(history, limit = 8, nowMs = now)
        assertEquals("valid.dev", recs.first().host)
        assertFalse(recs.any { it.host in listOf("blank", "file", "js", "localhost", "192.168.1.1", "invalid") })
    }
}
