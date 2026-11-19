package app.eddy.browser

import app.eddy.browser.browser.Suggestion
import app.eddy.browser.browser.SuggestionKind
import app.eddy.browser.browser.SuggestionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionSourceTest {

    @Test
    fun `domainSuggestions matches known domain prefixes and respects exclusions`() {
        val yt = SuggestionSource.domainSuggestions("you")
        assertTrue(yt.any { it.kind == SuggestionKind.DOMAIN && it.title == "youtube.com" })

        val excluded = SuggestionSource.domainSuggestions("you", excludedHosts = setOf("youtube.com"))
        assertTrue(excluded.none { it.title == "youtube.com" })
    }

    @Test
    fun `domainSuggestions suggests valid dotted domain fallback and ignores single char`() {
        assertEquals(emptyList<Any>(), SuggestionSource.domainSuggestions("g"))
        val custom = SuggestionSource.domainSuggestions("example.org")
        assertEquals(1, custom.size)
        assertEquals(SuggestionKind.DOMAIN, custom[0].kind)
        assertEquals("https://example.org", custom[0].text)
        assertEquals("example.org", custom[0].host)
    }

    @Test
    fun `extractHost resolves site hosts for URLs and excludes plain search queries and internal schemes`() {
        val history = Suggestion(SuggestionKind.HISTORY, "https://www.github.com/trending", "GitHub Trending", "https://www.github.com/trending")
        assertEquals("github.com", history.host)

        val domainClipboard = Suggestion(SuggestionKind.CLIPBOARD, "reddit.com/r/androiddev", "reddit.com/r/androiddev")
        assertEquals("reddit.com", domainClipboard.host)

        val searchPhrase = Suggestion(SuggestionKind.SEARCH, "kotlin compose animation", "kotlin compose animation")
        assertEquals("", searchPhrase.host)

        val internalPage = Suggestion(SuggestionKind.HISTORY, "about:blank", "Blank", "about:blank")
        assertEquals("", internalPage.host)
    }
}
