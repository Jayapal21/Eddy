package app.eddy.browser.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** Memory + disk favicon store keyed by host. Incognito icons stay in memory only. */
class FaviconCache(context: Context, private val scope: CoroutineScope) {
    private val memory = LruCache<String, Bitmap>(96)
    private val dir by lazy { File(context.cacheDir, "favicons").apply { mkdirs() } }
    private val misses = ConcurrentHashMap.newKeySet<String>()
    private val inFlight = ConcurrentHashMap<String, Deferred<Bitmap?>>()

    fun peek(host: String): Bitmap? = memory.get(host)

    fun put(host: String, bitmap: Bitmap, persist: Boolean) {
        if (host.isEmpty()) return
        val icon = if (bitmap.width > MAX_SIZE) Bitmap.createScaledBitmap(bitmap, MAX_SIZE, MAX_SIZE, true) else bitmap
        memory.put(host, icon)
        misses.remove(host)
        if (persist) scope.launch(Dispatchers.IO) {
            runCatching { fileFor(host).outputStream().use { icon.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        }
    }

    suspend fun load(host: String): Bitmap? {
        peek(host)?.let { return it }
        return withContext(Dispatchers.IO) {
            val f = fileFor(host)
            if (!f.exists()) return@withContext null
            BitmapFactory.decodeFile(f.path)?.also { memory.put(host, it) }
        }
    }

    /** Best effort direct fetch of the site's favicon (never a third-party service). */
    suspend fun fetch(host: String): Bitmap? {
        if (host.isEmpty() || host in misses) return null
        peek(host)?.let { return it }
        val existing = inFlight[host]
        if (existing != null) return existing.await()

        val deferred = scope.async(Dispatchers.IO) {
            try {
                fetchDirect(host)?.also { put(host, it, persist = true) }
                    ?: run { misses.add(host); null }
            } finally {
                inFlight.remove(host)
            }
        }
        val raced = inFlight.putIfAbsent(host, deferred)
        return if (raced != null) {
            deferred.cancel()
            raced.await()
        } else {
            deferred.await()
        }
    }

    private fun fetchDirect(host: String): Bitmap? {
        val candidates = buildHostCandidates(host)
        for (h in candidates) {
            decodeImageFromUrl("https://$h/favicon.ico")?.let { return it }
        }
        for (h in candidates) {
            for (iconUrl in findIconUrlsFromHtml("https://$h/")) {
                decodeImageFromUrl(iconUrl)?.let { return it }
            }
        }
        return null
    }

    private fun buildHostCandidates(host: String): List<String> {
        val list = ArrayList<String>(4)
        list.add(host)
        val labels = host.split('.')
        if (!host.startsWith("www.") && labels.size <= 2) {
            list.add("www.$host")
        }
        if (labels.size >= 3 && !host.startsWith("www.")) {
            val parent = labels.takeLast(2).joinToString(".")
            list.add(parent)
            list.add("www.$parent")
        }
        return list.distinct()
    }

    private fun decodeImageFromUrl(startUrl: String): Bitmap? {
        val response = fetchBytes(startUrl, MAX_ICON_BYTES) ?: return null
        return runCatching {
            BitmapFactory.decodeByteArray(response.bytes, 0, response.bytes.size)
                ?.takeIf { it.width > 0 && it.height > 0 }
        }.getOrNull()
    }

    private fun findIconUrlsFromHtml(pageUrl: String): List<String> {
        val response = fetchBytes(pageUrl, MAX_HTML_BYTES) ?: return emptyList()
        val html = response.bytes.toString(Charsets.UTF_8)
        return LINK_TAG_REGEX.findAll(html)
            .mapNotNull { match ->
                val tag = match.value
                val rel = ATTR_REL_REGEX.find(tag)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }?.lowercase()
                    ?: return@mapNotNull null
                if (!rel.contains("icon")) return@mapNotNull null
                val href = ATTR_HREF_REGEX.find(tag)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }?.trim()?.takeIf { it.isNotEmpty() }
                    ?: return@mapNotNull null
                val resolved = runCatching { URL(URL(response.finalUrl), href).toString() }.getOrNull()
                    ?: return@mapNotNull null
                val path = runCatching { URL(resolved).path }.getOrDefault("")
                if (!resolved.startsWith("https://", ignoreCase = true) || path.endsWith(".svg", ignoreCase = true)) {
                    return@mapNotNull null
                }
                val priority = when {
                    rel.contains("apple-touch-icon") -> 0
                    path.endsWith(".png", ignoreCase = true) -> 1
                    else -> 2
                }
                priority to resolved
            }
            .sortedBy { it.first }
            .map { it.second }
            .distinct()
            .toList()
    }

    private class FetchResult(val finalUrl: String, val bytes: ByteArray)

    private fun fetchBytes(startUrl: String, maxBytes: Int): FetchResult? = runCatching {
        var current = startUrl
        repeat(MAX_REDIRECTS + 1) {
            val url = URL(current)
            if (!url.protocol.equals("https", ignoreCase = true)) return null
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", FETCH_USER_AGENT)
            }
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location")?.trim() ?: return null
                    current = URL(url, location).toString()
                    return@repeat
                }
                if (code != HttpURLConnection.HTTP_OK) return null
                if (conn.contentLength > maxBytes) return null
                val out = ByteArrayOutputStream()
                val buf = ByteArray(4096)
                conn.inputStream.use { input ->
                    var total = 0
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        total += n
                        if (total > maxBytes) return null
                        out.write(buf, 0, n)
                    }
                }
                return FetchResult(current, out.toByteArray())
            } finally {
                conn.disconnect()
            }
        }
        null
    }.getOrNull()

    fun clear() {
        memory.evictAll()
        misses.clear()
        dir.listFiles()?.forEach { it.delete() }
    }

    // A 32-bit hashCode collides often enough to show one site's icon next to another's name.
    private fun fileFor(host: String) = File(dir, digest(host) + ".png")

    private fun digest(host: String): String = MessageDigest.getInstance("SHA-256")
        .digest(host.toByteArray()).take(16).joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_SIZE = 96
        const val MAX_ICON_BYTES = 256 * 1024
        const val MAX_HTML_BYTES = 48 * 1024
        const val MAX_REDIRECTS = 3
        const val FETCH_USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        val LINK_TAG_REGEX = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE)
        val ATTR_REL_REGEX = Regex("""rel\s*=\s*(?:["']([^"']+)["']|([^\s"'>]+))""", RegexOption.IGNORE_CASE)
        val ATTR_HREF_REGEX = Regex("""href\s*=\s*(?:["']([^"']+)["']|([^\s"'>]+))""", RegexOption.IGNORE_CASE)
    }
}
