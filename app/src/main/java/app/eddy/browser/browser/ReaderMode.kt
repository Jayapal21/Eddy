package app.eddy.browser.browser

import android.content.Context
import android.webkit.WebView
import java.util.Base64
import app.eddy.browser.data.database.SavedArticle
import app.eddy.browser.data.database.SavedArticleImage
import app.eddy.browser.data.models.MotionPref
import app.eddy.browser.data.models.ReaderThemeMode
import app.eddy.browser.data.models.Settings
import app.eddy.browser.util.UrlUtils
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reader mode. Mozilla's Readability runs inside the page on a clone of the document, detecting
 * compatible articles cleanly and replacing cluttered layouts with focused text and imagery.
 */
object ReaderMode {

    private const val ASSET = "readability.js"
    private var source: String? = null

    private fun source(context: Context): String =
        source ?: context.assets.open(ASSET).bufferedReader().use { it.readText() }.also { source = it }

    fun isReducedMotion(context: Context, settings: Settings): Boolean {
        if (settings.motion == MotionPref.REDUCED) return true
        val scale = runCatching {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f)
        return scale == 0f
    }

    private fun evalJs(view: WebView, script: String, callback: ((String?) -> Unit)? = null) {
        val eddy = view as? EddyWebView
        if (eddy != null) {
            eddy.safeEvaluateJavascript(script, callback)
        } else {
            runCatching { view.evaluateJavascript(script, callback) }.onFailure { callback?.invoke(null) }
        }
    }

    /**
     * Non-destructively detects whether the current document is a compatible article for Reader Mode.
     * Runs Readability on a clone of the document without altering the live page.
     */
    fun checkAvailability(context: Context, view: WebView, url: String = view.url.orEmpty(), onResult: (Boolean) -> Unit) {
        if (!isCandidateUrl(url)) {
            onResult(false)
            return
        }
        val js = """(function(){
            if(window.__eddyReader) return true;
            if(!document.body) return false;
            var bodyText=(document.body.innerText||'').trim();
            if(bodyText.length<280) return false;
            ${source(context)}
            var article=null;
            try{ article=new Readability(document.cloneNode(true)).parse(); }catch(e){}
            if(!article||!article.content) return false;
            var cleanLen=(article.textContent||'').trim().length;
            if(cleanLen<220) return false;
            var blocks=document.querySelectorAll('p, article, blockquote, pre').length;
            return blocks>=2||cleanLen>=400;
        })()"""
        evalJs(view, js) { onResult(it == "true") }
    }

    internal fun isCandidateUrl(url: String): Boolean {
        if (!UrlUtils.isWebUrl(url)) return false
        return true
    }

    /**
     * Builds the JavaScript snippet that updates CSS custom properties in-place inside an active
     * Reader Mode DOM without reloading the page or disturbing scroll, progress, or text selection.
     */
    internal fun buildApplyThemeJs(theme: ReaderThemeMode, reducedMotion: Boolean): String {
        val vars = ReaderThemeManager.cssVariables(theme)
            .entries
            .joinToString("") { (k, v) -> "root.style.setProperty('$k','$v');" }
        val scheme = if (theme == ReaderThemeMode.DARK) "dark" else "light"
        val reduced = if (reducedMotion) "1" else "0"
        return """(function(){
            var root=document.documentElement;
            if(!root) return false;
            root.setAttribute('data-eddy-reduced','$reduced');
            root.setAttribute('data-eddy-theme','$scheme');
            root.style.colorScheme='$scheme';
            $vars
            var cs=document.querySelector('meta[name=color-scheme]');
            if(cs) cs.content='$scheme';
            return true;
        })()"""
    }

    /** Smoothly updates the live Reader Mode stylesheet variables without reloading or reflowing. */
    fun applyTheme(view: WebView, theme: ReaderThemeMode, reducedMotion: Boolean = false) {
        evalJs(view, buildApplyThemeJs(theme, reducedMotion), null)
    }

    internal fun readerStylesheetCss(theme: ReaderThemeMode): String {
        val durationMs = ReaderThemeManager.TRANSITION_DURATION_MS
        val initialVars = ReaderThemeManager.cssVariables(theme)
            .entries
            .joinToString(";") { (k, v) -> "$k:$v" }
        return ":root{$initialVars;--er-ease:${durationMs}ms cubic-bezier(0.42,0,0.58,1);--er-progress:0}" +
            "html[data-eddy-reduced=\"1\"]{--er-ease:0ms}" +
            "html{-webkit-text-size-adjust:100%;background-color:var(--er-bg);color:var(--er-text)}" +
            "body{margin:0;padding:12px 0 96px;background-color:var(--er-bg);color:var(--er-text);-webkit-font-smoothing:antialiased}" +
            "html,body,#eddy-reader,#eddy-reader *,#eddy-reader-progress{" +
            "transition:background-color var(--er-ease),color var(--er-ease),border-color var(--er-ease),box-shadow var(--er-ease)}" +
            "html[data-eddy-reduced=\"1\"],html[data-eddy-reduced=\"1\"] *{transition:none!important}" +
            "#eddy-reader-progress{position:fixed;top:0;left:0;width:100%;height:2.5px;background-color:var(--er-accent);" +
            "transform-origin:left center;transform:scaleX(var(--er-progress,0));pointer-events:none;z-index:9999}" +
            "#eddy-reader{width:min(42em,calc(100% - 20px));box-sizing:border-box;margin:0 auto;padding:26px 20px 36px;" +
            "border-radius:16px;background-color:var(--er-surface);border:1px solid var(--er-divider);" +
            "font:18px/1.72 Georgia,\"Noto Serif\",serif;word-wrap:break-word;color:var(--er-text)}" +
            "#eddy-reader .eddy-meta{margin:0 0 10px;font:600 12px/1.4 system-ui,-apple-system,sans-serif;" +
            "letter-spacing:0.04em;color:var(--er-muted);text-transform:uppercase}" +
            "#eddy-reader h1{margin:0 0 10px;font-family:system-ui,-apple-system,sans-serif;font-size:1.65em;line-height:1.24;font-weight:700;color:var(--er-text)}" +
            "#eddy-reader h2,#eddy-reader h3,#eddy-reader h4{font-family:system-ui,-apple-system,sans-serif;line-height:1.3;margin-top:1.5em;color:var(--er-text)}" +
            "#eddy-reader .byline{margin:0;font:400 14px/1.45 system-ui,-apple-system,sans-serif;color:var(--er-muted)}" +
            "#eddy-reader hr,#eddy-reader .eddy-divider{border:none;border-top:1px solid var(--er-divider);margin:18px 0 22px}" +
            "#eddy-reader .eddy-reader-body p{margin:0 0 1.15em}" +
            "#eddy-reader a{color:var(--er-accent);text-decoration-thickness:1.5px;text-underline-offset:3px}" +
            "#eddy-reader figure{margin:24px 0}" +
            "#eddy-reader figcaption{margin-top:8px;font:400 13px/1.45 system-ui,-apple-system,sans-serif;color:var(--er-muted);text-align:center}" +
            "#eddy-reader img,#eddy-reader video{display:block;max-width:100%;height:auto;margin:20px auto;border-radius:12px}" +
            "#eddy-reader blockquote{margin:20px 0;padding:10px 16px;border-left:3px solid var(--er-accent);" +
            "border-radius:0 10px 10px 0;background-color:var(--er-quote-bg);color:var(--er-muted)}" +
            "#eddy-reader pre{padding:14px 16px;border-radius:10px;background-color:var(--er-code-bg);" +
            "border:1px solid var(--er-divider);overflow-x:auto;font-size:14px;line-height:1.5;color:var(--er-text)}" +
            "#eddy-reader code{padding:2px 5px;border-radius:5px;background-color:var(--er-code-bg);" +
            "border:1px solid var(--er-divider);font-size:.88em;color:var(--er-text)}" +
            "#eddy-reader pre code{padding:0;background:transparent;border:none}" +
            "#eddy-reader table{width:100%;border-collapse:collapse;margin:20px 0}" +
            "#eddy-reader th,#eddy-reader td{border:1px solid var(--er-divider);padding:8px 10px;text-align:left}"
    }

    /** Strips page clutter and renders the extracted article text and imagery with [theme] applied. */
    fun show(
        context: Context,
        view: WebView,
        theme: ReaderThemeMode = ReaderThemeMode.DARK,
        reducedMotion: Boolean = false,
        onResult: (Boolean) -> Unit = {},
    ) {
        val scheme = if (theme == ReaderThemeMode.DARK) "dark" else "light"
        val reducedFlag = if (reducedMotion) "1" else "0"
        val cssJs = JSONObject.quote(readerStylesheetCss(theme))

        val js = """(function(){
            if(window.__eddyReader){
                ${buildApplyThemeJs(theme, reducedMotion)}
                return true;
            }
            ${source(context)}
            var article=null;
            try{ article=new Readability(document.cloneNode(true)).parse(); }catch(e){}
            if(!article||!article.content) return false;
            if(window.__eddyDarkState){
                window.__eddyDarkState.enabled=false;
                window.__eddyDarkState.tornDown=true;
                if(window.__eddyDarkState.timer){clearTimeout(window.__eddyDarkState.timer);window.__eddyDarkState.timer=null;}
                if(window.__eddyDarkState.observer){try{window.__eddyDarkState.observer.disconnect();}catch(e){}window.__eddyDarkState.observer=null;}
                if(window.__eddyDarkState.pending) window.__eddyDarkState.pending.length=0;
            }
            if(document.documentElement){
                document.documentElement.removeAttribute('${WebsiteDarkMode.ROOT_ATTR}');
                document.documentElement.removeAttribute('${WebsiteDarkMode.NATIVE_ATTR}');
            }
            window.__eddyReader=true;

            var reader=document.createElement('article');
            reader.id='eddy-reader';

            var siteName=(article.siteName||'').trim();
            var plainText=(article.textContent||'').trim();
            var readMinutes=1;
            if(plainText){
                var words=plainText.split(/\s+/).filter(Boolean).length;
                readMinutes=Math.max(1,Math.round(words/220));
            }
            reader.setAttribute('data-site-name',siteName);
            reader.setAttribute('data-read-minutes',String(readMinutes));

            var metaLine=[];
            if(siteName) metaLine.push(siteName);
            if(plainText) metaLine.push(readMinutes+' min read');
            if(metaLine.length){
                var meta=document.createElement('p');
                meta.className='eddy-meta';
                meta.textContent=metaLine.join(' · ');
                reader.appendChild(meta);
            }

            if(article.title){
                var h1=document.createElement('h1');
                h1.textContent=article.title;
                reader.appendChild(h1);
            }

            if(article.byline&&article.byline.trim()){
                var byline=document.createElement('p');
                byline.className='byline';
                byline.textContent=article.byline.trim();
                reader.appendChild(byline);
            }

            var divider=document.createElement('hr');
            divider.className='eddy-divider';
            reader.appendChild(divider);

            var content=document.createElement('div');
            content.className='eddy-reader-body';
            content.innerHTML=article.content;

            content.querySelectorAll('script,style,noscript,form,button,input,select,textarea').forEach(function(n){
                n.remove();
            });
            content.querySelectorAll('img').forEach(function(img){
                var lazy=img.getAttribute('data-src')||img.getAttribute('data-original')||
                    img.getAttribute('data-lazy-src')||img.getAttribute('data-url');
                var cur=img.getAttribute('src')||'';
                if(lazy&&(!cur||cur.indexOf('data:image/svg')===0||cur.indexOf('data:image/gif')===0)){
                    img.setAttribute('src',lazy);
                }
                var lazySet=img.getAttribute('data-srcset');
                if(lazySet&&!img.getAttribute('srcset')) img.setAttribute('srcset',lazySet);
                img.removeAttribute('width');
                img.removeAttribute('height');
                img.removeAttribute('style');
            });
            content.querySelectorAll('[style]').forEach(function(el){
                el.removeAttribute('style');
            });

            reader.appendChild(content);

            var progressBar=document.createElement('div');
            progressBar.id='eddy-reader-progress';
            progressBar.setAttribute('aria-hidden','true');

            document.body.innerHTML='';
            document.body.className='';
            document.documentElement.className='';
            document.documentElement.setAttribute('data-eddy-theme','$scheme');
            document.documentElement.setAttribute('data-eddy-reduced','$reducedFlag');
            document.documentElement.style.colorScheme='$scheme';
            document.body.appendChild(progressBar);
            document.body.appendChild(reader);

            document.querySelectorAll('link[rel=stylesheet],style,meta[name=color-scheme]').forEach(function(n){n.remove();});
            if(!document.querySelector('meta[name=viewport]')){
                var vp=document.createElement('meta');
                vp.name='viewport';
                vp.content='width=device-width,initial-scale=1';
                document.head.appendChild(vp);
            }
            var cs=document.createElement('meta');
            cs.name='color-scheme';
            cs.content='$scheme';
            document.head.appendChild(cs);

            var s=document.createElement('style');
            s.id='eddy-reader-style';
            s.textContent=$cssJs;
            document.head.appendChild(s);

            if(!window.__eddyReaderScrollBound){
                window.__eddyReaderScrollBound=true;
                var raf=0;
                var updateProgress=function(){
                    raf=0;
                    var doc=document.documentElement;
                    var max=(doc.scrollHeight||0)-window.innerHeight;
                    var p=max>0?Math.min(1,Math.max(0,window.scrollY/max)):0;
                    doc.style.setProperty('--er-progress',p.toFixed(4));
                };
                window.addEventListener('scroll',function(){
                    if(!raf) raf=window.requestAnimationFrame(updateProgress);
                },{passive:true});
                updateProgress();
            }

            window.scrollTo(0,0);
            return true;
        })()"""
        evalJs(view, js) { onResult(it == "true") }
    }

    /** Extracts clean article text from an active Reader Mode article for TTS playback. */
    internal fun buildExtractTtsTextJs(): String = """(function(){
        var reader=document.getElementById('eddy-reader');
        if(!reader){
            return (document.body&&document.body.innerText||'').trim();
        }
        var parts=[];
        var h1=reader.querySelector('h1');
        if(h1&&h1.innerText) parts.push(h1.innerText.trim());
        var byline=reader.querySelector('.byline');
        if(byline&&byline.innerText) parts.push(byline.innerText.trim());
        var body=reader.querySelector('.eddy-reader-body');
        if(body){
            var blocks=body.querySelectorAll('h2,h3,h4,p,li,blockquote');
            if(blocks.length>0){
                blocks.forEach(function(el){
                    var t=(el.innerText||'').trim();
                    if(t) parts.push(t);
                });
            } else if(body.innerText){
                parts.push(body.innerText.trim());
            }
        }
        return parts.join('\n\n');
    })()"""

    fun extractTtsText(view: WebView, onResult: (String) -> Unit) {
        evalJs(view, buildExtractTtsTextJs()) { raw ->
            val text = runCatching { JSONArray("[$raw]").getString(0) }.getOrNull().orEmpty()
            onResult(text)
        }
    }

    /** Extracts article text, metadata, sanitized HTML, and resolved image URLs for local DB saving. */
    internal fun buildExtractArticleJs(pageUrl: String): String {
        val urlJson = JSONObject.quote(pageUrl)
        return """(function(){
            var reader=document.getElementById('eddy-reader');
            if(!reader) return null;
            var h1=reader.querySelector('h1');
            var bylineEl=reader.querySelector('.byline');
            var body=reader.querySelector('.eddy-reader-body');
            if(!body) return null;
            var clone=body.cloneNode(true);
            var urls=[];
            var seen={};
            var liveImgs=body.querySelectorAll('img');
            var cloneImgs=clone.querySelectorAll('img');
            for(var i=0;i<cloneImgs.length;i++){
                var cImg=cloneImgs[i];
                var lImg=liveImgs[i];
                var resolved=(lImg&&(lImg.currentSrc||lImg.src))||cImg.getAttribute('src')||'';
                if(resolved){
                    cImg.setAttribute('src',resolved);
                    if(!seen[resolved]){
                        seen[resolved]=true;
                        urls.push(resolved);
                    }
                }
                cImg.removeAttribute('srcset');
                cImg.removeAttribute('loading');
            }
            var title=(h1&&h1.textContent||'').trim()||(document.title||'').trim();
            var siteName=(reader.getAttribute('data-site-name')||'').trim();
            var byline=(bylineEl&&bylineEl.textContent||'').trim();
            var mins=parseInt(reader.getAttribute('data-read-minutes')||'1',10)||1;
            var text=(h1&&h1.innerText?h1.innerText.trim()+'\n\n':'')+(body.innerText||'').trim();
            return JSON.stringify({
                url:$urlJson,
                title:title,
                siteName:siteName,
                byline:byline,
                readMinutes:mins,
                textContent:text,
                contentHtml:clone.innerHTML,
                imageUrls:urls
            });
        })()"""
    }

    internal fun parseExtractedArticleJson(rawJsResult: String?, fallbackUrl: String): ExtractedReaderArticle? {
        if (rawJsResult.isNullOrBlank() || rawJsResult == "null") return null
        val jsonStr = runCatching { JSONArray("[$rawJsResult]").getString(0) }.getOrNull() ?: return null
        if (jsonStr.isBlank() || jsonStr == "null") return null
        val obj = runCatching { JSONObject(jsonStr) }.getOrNull() ?: return null
        val html = obj.optString("contentHtml", "")
        val text = obj.optString("textContent", "")
        if (html.isBlank() && text.isBlank()) return null
        val url = obj.optString("url", fallbackUrl).ifBlank { fallbackUrl }
        val urlsJson = obj.optJSONArray("imageUrls") ?: JSONArray()
        val imageUrls = List(urlsJson.length()) { idx -> urlsJson.optString(idx) }.filter { it.isNotBlank() }
        return ExtractedReaderArticle(
            url = url,
            title = obj.optString("title", "").ifBlank { UrlUtils.displayHost(url) },
            siteName = obj.optString("siteName", ""),
            byline = obj.optString("byline", ""),
            readMinutes = obj.optInt("readMinutes", 1).coerceAtLeast(1),
            textContent = text,
            contentHtml = html,
            imageUrls = imageUrls,
        )
    }

    fun extractArticle(view: WebView, pageUrl: String, onResult: (ExtractedReaderArticle?) -> Unit) {
        evalJs(view, buildExtractArticleJs(pageUrl)) { raw ->
            onResult(parseExtractedArticleJson(raw, pageUrl))
        }
    }

    /**
     * Builds a self-contained HTML document for offline Reader Mode playback/viewing.
     * Saved images are embedded as base64 data URIs so zero network requests are needed.
     */
    fun buildOfflineDocumentHtml(
        article: SavedArticle,
        images: List<SavedArticleImage>,
        theme: ReaderThemeMode,
        reducedMotion: Boolean,
    ): String {
        val scheme = if (theme == ReaderThemeMode.DARK) "dark" else "light"
        val reducedFlag = if (reducedMotion) "1" else "0"
        val imageMap = images.associate { img ->
            val b64 = Base64.getEncoder().encodeToString(img.imageData)
            img.originalUrl to "data:${img.mimeType};base64,$b64"
        }
        val offlineBodyHtml = embedOfflineImages(article.contentHtml, imageMap)

        val metaParts = buildList {
            if (article.siteName.isNotBlank()) add(article.siteName.trim())
            add("${article.readMinutes.coerceAtLeast(1)} min read")
            add("Offline")
        }
        val metaHtml = "<p class=\"eddy-meta\">${escapeHtml(metaParts.joinToString(" · "))}</p>"
        val titleHtml = "<h1>${escapeHtml(article.title)}</h1>"
        val bylineHtml = if (article.byline.isNotBlank()) {
            "<p class=\"byline\">${escapeHtml(article.byline)}</p>"
        } else {
            ""
        }

        return """<!doctype html>
<html data-eddy-theme="$scheme" data-eddy-reduced="$reducedFlag" style="color-scheme:$scheme">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="color-scheme" content="$scheme">
<title>${escapeHtml(article.title)}</title>
<style id="eddy-reader-style">${readerStylesheetCss(theme)}</style>
</head>
<body>
<div id="eddy-reader-progress" aria-hidden="true"></div>
<article id="eddy-reader" data-site-name="${escapeHtml(article.siteName)}" data-read-minutes="${article.readMinutes}">
$metaHtml
$titleHtml
$bylineHtml
<hr class="eddy-divider">
<div class="eddy-reader-body">$offlineBodyHtml</div>
</article>
<script>
window.__eddyReader=true;
window.__eddyOfflineArticle=true;
if(!window.__eddyReaderScrollBound){
  window.__eddyReaderScrollBound=true;
  var raf=0;
  var updateProgress=function(){
    raf=0;
    var doc=document.documentElement;
    var max=(doc.scrollHeight||0)-window.innerHeight;
    var p=max>0?Math.min(1,Math.max(0,window.scrollY/max)):0;
    doc.style.setProperty('--er-progress',p.toFixed(4));
  };
  window.addEventListener('scroll',function(){
    if(!raf) raf=window.requestAnimationFrame(updateProgress);
  },{passive:true});
  updateProgress();
}
</script>
</body>
</html>"""
    }

    internal fun embedOfflineImages(html: String, imageDataUris: Map<String, String>): String {
        if (imageDataUris.isEmpty() || html.isBlank()) return html
        val srcRegex = Regex("""(<img\b[^>]*?\bsrc\s*=\s*)(["'])([^"']*)\2""", RegexOption.IGNORE_CASE)
        return srcRegex.replace(html) { match ->
            val prefix = match.groupValues[1]
            val quote = match.groupValues[2]
            val rawUrl = match.groupValues[3]
            val decoded = unescapeBasicHtmlEntities(rawUrl)
            val replacement = imageDataUris[rawUrl] ?: imageDataUris[decoded]
            if (replacement != null) "$prefix$quote$replacement$quote" else match.value
        }
    }

    private fun unescapeBasicHtmlEntities(value: String): String {
        if (!value.contains('&')) return value
        return value
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }

    private fun escapeHtml(text: String): String = buildString(text.length) {
        text.forEach { ch ->
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(ch)
            }
        }
    }

    /** The original page is gone from the DOM, so leaving reader mode means loading it again. */
    fun hide(view: WebView) = view.reload()
}
