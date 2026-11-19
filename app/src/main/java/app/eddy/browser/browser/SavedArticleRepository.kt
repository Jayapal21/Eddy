package app.eddy.browser.browser

import app.eddy.browser.data.database.SavedArticle
import app.eddy.browser.data.database.SavedArticleDao
import app.eddy.browser.data.database.SavedArticleImage
import app.eddy.browser.data.models.ReaderThemeMode
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class ExtractedReaderArticle(
    val url: String,
    val title: String,
    val siteName: String,
    val byline: String,
    val readMinutes: Int,
    val textContent: String,
    val contentHtml: String,
    val imageUrls: List<String>,
)

/**
 * Repository backing offline Reader Mode saves. Fetches and persists extracted article content
 * together with article images in Room so saved articles open cleanly without internet access.
 */
class SavedArticleRepository(private val dao: SavedArticleDao) {

    val articles: Flow<List<SavedArticle>> = dao.all()

    fun isSaved(url: String): Flow<Boolean> = dao.isSaved(url)

    suspend fun findByUrl(url: String): SavedArticle? = withContext(Dispatchers.IO) {
        dao.findByUrl(url)
    }

    suspend fun saveArticle(
        extracted: ExtractedReaderArticle,
        cookieHeader: String = "",
        imageFetcher: ((String, String, String) -> Pair<String, ByteArray>?)? = null,
    ): SavedArticle = withContext(Dispatchers.IO) {
        val fetcher = imageFetcher ?: ::downloadImage
        val downloaded = extracted.imageUrls
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_IMAGES)
            .mapNotNull { imageUrl ->
                val resolved = decodeDataUri(imageUrl) ?: fetcher(imageUrl, extracted.url, cookieHeader)
                resolved?.let { (mime, bytes) ->
                    SavedArticleImage(
                        articleId = 0L,
                        originalUrl = imageUrl,
                        mimeType = mime,
                        imageData = bytes,
                    )
                }
            }
            .toList()

        val article = SavedArticle(
            url = extracted.url,
            title = extracted.title.ifBlank { extracted.url },
            siteName = extracted.siteName,
            byline = extracted.byline,
            readMinutes = extracted.readMinutes.coerceAtLeast(1),
            textContent = extracted.textContent,
            contentHtml = extracted.contentHtml,
            imageCount = downloaded.size,
            savedAt = System.currentTimeMillis(),
        )
        val id = dao.saveWithImages(article, downloaded)
        article.copy(id = id)
    }

    suspend fun loadOfflineHtml(
        articleId: Long,
        theme: ReaderThemeMode,
        reducedMotion: Boolean,
    ): Pair<SavedArticle, String>? = withContext(Dispatchers.IO) {
        val article = dao.get(articleId) ?: return@withContext null
        val ids = dao.imageIdsForArticle(article.id)
        val images = ids.mapNotNull { dao.getImage(it) }
        val html = ReaderMode.buildOfflineDocumentHtml(article, images, theme, reducedMotion)
        article to html
    }

    suspend fun delete(articleId: Long) = withContext(Dispatchers.IO) {
        dao.deleteById(articleId)
    }

    suspend fun deleteByUrl(url: String) = withContext(Dispatchers.IO) {
        dao.deleteByUrl(url)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        dao.clear()
    }

    internal fun decodeDataUri(uri: String): Pair<String, ByteArray>? {
        if (!uri.startsWith("data:image/", ignoreCase = true)) return null
        val comma = uri.indexOf(',')
        if (comma <= 0) return null
        val header = uri.substring(5, comma)
        if (!header.contains(";base64", ignoreCase = true)) return null
        val mime = header.substringBefore(';').ifBlank { "image/png" }
        val rawBase64 = uri.substring(comma + 1).replace("\\s".toRegex(), "")
        val bytes = runCatching { Base64.getDecoder().decode(rawBase64) }.getOrNull() ?: return null
        if (bytes.isEmpty() || bytes.size > MAX_IMAGE_BYTES) return null
        return mime to bytes
    }

    private fun downloadImage(
        imageUrl: String,
        refererUrl: String,
        cookieHeader: String,
    ): Pair<String, ByteArray>? {
        if (!imageUrl.startsWith("https://", ignoreCase = true) &&
            !imageUrl.startsWith("http://", ignoreCase = true)
        ) {
            return null
        }
        return runCatching {
            val conn = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true
                if (refererUrl.isNotBlank()) setRequestProperty("Referer", refererUrl)
                if (cookieHeader.isNotBlank()) setRequestProperty("Cookie", cookieHeader)
            }
            try {
                if (conn.responseCode !in 200..299) return@runCatching null
                val mime = conn.contentType
                    ?.substringBefore(';')
                    ?.trim()
                    ?.takeIf { it.startsWith("image/", ignoreCase = true) }
                    ?: guessMimeFromUrl(imageUrl)
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var total = 0
                conn.inputStream.use { input ->
                    while (true) {
                        val read = input.read(buf)
                        if (read <= 0) break
                        total += read
                        if (total > MAX_IMAGE_BYTES) return@runCatching null
                        out.write(buf, 0, read)
                    }
                }
                val bytes = out.toByteArray()
                if (bytes.isEmpty()) null else mime to bytes
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    private fun guessMimeFromUrl(url: String): String {
        val clean = url.substringBefore('?').substringBefore('#').lowercase()
        return when {
            clean.endsWith(".png") -> "image/png"
            clean.endsWith(".webp") -> "image/webp"
            clean.endsWith(".gif") -> "image/gif"
            clean.endsWith(".svg") -> "image/svg+xml"
            else -> "image/jpeg"
        }
    }

    companion object {
        const val MAX_IMAGES = 20
        const val MAX_IMAGE_BYTES = 512 * 1024
        private const val TIMEOUT_MS = 8_000
    }
}
