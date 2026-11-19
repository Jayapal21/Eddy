package app.eddy.browser.browser

import app.eddy.browser.data.database.SiteSettings
import java.net.URI

/**
 * Safe, non-destructive custom website dark mode engine.
 *
 * Uses a single managed `<style id="eddy-dark-mode-style">` element per document, selective CSS rules
 * without global `filter: invert(1)`, native dark-mode detection, bounded debounced DOM observation with
 * strict self-mutation guards, and automatic rollback fallback on failure.
 */
object WebsiteDarkMode {

    const val STYLE_ID = "eddy-dark-mode-style"
    const val ROOT_ATTR = "data-eddy-dark-mode"
    const val NATIVE_ATTR = "data-eddy-dark-native"
    const val NODE_ATTR = "data-eddy-dark-node"

    private const val MAX_ELEMENTS_PER_PASS = 120
    private const val MAX_QUEUED_ROOTS = 60
    private const val OBSERVER_DEBOUNCE_MS = 140

    enum class ApplyOutcome { APPLIED, NATIVE_DARK, DEFERRED, SKIPPED, ERROR }

    /** Normalizes a page URL to scheme://host[:port]/path (ignoring fragment) for fallback tracking. */
    fun pageKey(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return ""
        return runCatching {
            val uri = URI(trimmed)
            val scheme = uri.scheme?.lowercase().orEmpty()
            val host = uri.host?.lowercase().orEmpty()
            if (scheme.isEmpty() || host.isEmpty()) return trimmed.substringBefore('#')
            val port = if (uri.port > 0) ":${uri.port}" else ""
            val path = uri.rawPath.orEmpty().ifEmpty { "/" }
            val query = uri.rawQuery?.let { "?$it" }.orEmpty()
            "$scheme://$host$port$path$query"
        }.getOrElse { trimmed.substringBefore('#') }
    }

    /** Returns true only when custom dark mode should actively apply to [url]. */
    fun shouldApply(
        darkenPages: Boolean,
        siteDarkenSetting: Int?,
        readerActive: Boolean,
        url: String,
        failedPageKey: String?,
    ): Boolean {
        if (!darkenPages) return false
        if (siteDarkenSetting == SiteSettings.BLOCK) return false
        if (readerActive) return false
        if (!app.eddy.browser.util.UrlUtils.isWebUrl(url)) return false
        val currentKey = pageKey(url)
        if (failedPageKey != null && currentKey.isNotEmpty() && failedPageKey == currentKey) return false
        return true
    }

    internal val DARK_MODE_CSS: String = (
        ":root{color-scheme:dark!important}" +
            "html,body{background-color:#12151A!important;color:#E6EAF0!important}" +
            ":where(main,article,section,header,footer,nav,aside,dialog,[role=\"dialog\"],[role=\"main\"]){" +
            "background-color:#12151A;color:#E6EAF0}" +
            ":where(p,li,dt,dd,h1,h2,h3,h4,h5,h6,label,figcaption,blockquote,td,th,summary){color:#E6EAF0}" +
            ":where(a){color:#8AB4F8}" +
            ":where(input,textarea,select,button){background-color:#1C2129;color:#E6EAF0;border-color:#36404F}" +
            ":where(pre,code,kbd){background-color:#1A1F26;color:#E6EAF0}" +
            ":where(table,tr,td,th,hr){border-color:#2B333F}"
        )

    private fun jsStringLiteral(value: String): String = buildString(value.length + 2) {
        append('"')
        for (ch in value) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '<' -> append("\\u003C")
                '>' -> append("\\u003E")
                else -> append(ch)
            }
        }
        append('"')
    }

    /** Builds the idempotent, self-mutation-safe JS script that applies dark mode to a light page. */
    fun buildApplyJs(): String {
        val cssJson = jsStringLiteral(DARK_MODE_CSS)
        return """(function(){
try{
  var STYLE_ID='$STYLE_ID';
  var ROOT_ATTR='$ROOT_ATTR';
  var NATIVE_ATTR='$NATIVE_ATTR';
  var NODE_ATTR='$NODE_ATTR';
  var MAX_SCAN=$MAX_ELEMENTS_PER_PASS;
  var MAX_QUEUE=$MAX_QUEUED_ROOTS;
  var DEBOUNCE_MS=$OBSERVER_DEBOUNCE_MS;
  var CSS=$cssJson;

  if(!document) return 'DEFERRED';
  var root=document.documentElement;
  if(!document.head && !root) return 'DEFERRED';

  var state=window.__eddyDarkState;
  if(!state){
    state={
      enabled:false,
      applying:false,
      tornDown:false,
      observer:null,
      timer:null,
      processed:(typeof WeakSet==='function')?new WeakSet():null,
      pending:[]
    };
    window.__eddyDarkState=state;
  }
  if(state.applying) return 'SKIPPED';

  function cancelTimer(){
    if(state.timer){
      clearTimeout(state.timer);
      state.timer=null;
    }
  }

  function disconnectObserver(){
    cancelTimer();
    if(state.observer){
      try{ state.observer.disconnect(); }catch(e){}
      state.observer=null;
    }
    state.pending.length=0;
  }

  function restoreMarkedNodes(){
    try{
      var list=document.querySelectorAll('['+NODE_ATTR+']');
      for(var i=0;i<list.length;i++){
        var el=list[i];
        var ob=el.getAttribute('data-eddy-orig-bg');
        var obp=el.getAttribute('data-eddy-orig-bgp')||'';
        var oc=el.getAttribute('data-eddy-orig-col');
        var ocp=el.getAttribute('data-eddy-orig-colp')||'';
        if(ob){ el.style.setProperty('background-color',ob,obp); }
        else { el.style.removeProperty('background-color'); }
        if(oc!==null){
          if(oc){ el.style.setProperty('color',oc,ocp); }
          else { el.style.removeProperty('color'); }
        }
        el.removeAttribute('data-eddy-orig-bg');
        el.removeAttribute('data-eddy-orig-bgp');
        el.removeAttribute('data-eddy-orig-col');
        el.removeAttribute('data-eddy-orig-colp');
        el.removeAttribute(NODE_ATTR);
      }
    }catch(e){}
  }

  function cleanupDom(markNative){
    disconnectObserver();
    state.enabled=false;
    var s=document.getElementById(STYLE_ID);
    if(s && s.parentNode) s.parentNode.removeChild(s);
    restoreMarkedNodes();
    if(root){
      root.removeAttribute(ROOT_ATTR);
      if(markNative) root.setAttribute(NATIVE_ATTR,'1');
      else root.removeAttribute(NATIVE_ATTR);
    }
  }

  if(!window.__eddyDarkLifecycleBound){
    window.__eddyDarkLifecycleBound=true;
    var onLeave=function(){
      var st=window.__eddyDarkState;
      if(!st) return;
      st.tornDown=true;
      if(st.timer){ clearTimeout(st.timer); st.timer=null; }
      if(st.observer){ try{ st.observer.disconnect(); }catch(e){} st.observer=null; }
      st.pending.length=0;
    };
    window.addEventListener('pagehide',onLeave,{passive:true});
    window.addEventListener('beforeunload',onLeave,{passive:true});
  }

  function parseLuminance(bg){
    if(!bg || bg==='transparent' || bg==='rgba(0, 0, 0, 0)') return null;
    var m=bg.match(/rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)(?:\s*,\s*([\d.]+))?\s*\)/i);
    if(!m) return null;
    var a=m[4]!==undefined?parseFloat(m[4]):1;
    if(isNaN(a) || a<=0.15) return null;
    var r=parseFloat(m[1])/255;
    var g=parseFloat(m[2])/255;
    var b=parseFloat(m[3])/255;
    if(isNaN(r)||isNaN(g)||isNaN(b)) return null;
    return 0.2126*r+0.7152*g+0.0722*b;
  }

  function hasNativeDarkTheme(){
    try{
      var meta=document.querySelector('meta[name="color-scheme"]');
      var metaVal=((meta && meta.getAttribute('content'))||'').trim().toLowerCase();
      if(metaVal==='dark' || metaVal==='only dark') return true;
      var rs=root?getComputedStyle(root):null;
      var cs=(rs && rs.colorScheme || '').trim().toLowerCase();
      if(cs==='dark' || cs==='only dark') return true;
      var body=document.body;
      var bs=body?getComputedStyle(body):null;
      var bLum=bs?parseLuminance(bs.backgroundColor):null;
      if(bLum!==null) return bLum<0.25;
      var rLum=rs?parseLuminance(rs.backgroundColor):null;
      if(rLum!==null) return rLum<0.25;
    }catch(e){}
    return false;
  }

  var CONTAINER_TAGS={
    MAIN:1,ARTICLE:1,SECTION:1,HEADER:1,FOOTER:1,NAV:1,ASIDE:1,
    DIALOG:1,FORM:1,TABLE:1,UL:1,OL:1,DIV:1
  };
  var SKIP_TAGS={
    IMG:1,VIDEO:1,PICTURE:1,CANVAS:1,SVG:1,IFRAME:1,EMBED:1,OBJECT:1,
    SCRIPT:1,STYLE:1,LINK:1,META:1,NOSCRIPT:1
  };

  function maybeDarkenElement(el){
    if(!el || el.nodeType!==1 || el.id===STYLE_ID) return false;
    var tag=el.tagName;
    if(!tag || SKIP_TAGS[tag]) return false;
    if(state.processed && state.processed.has(el)) return false;
    if(state.processed) state.processed.add(el);
    if(el.hasAttribute(NODE_ATTR)) return false;
    if(!CONTAINER_TAGS[tag]) return true;
    var cs=getComputedStyle(el);
    if(cs.backgroundImage && cs.backgroundImage!=='none') return true;
    var bgLum=parseLuminance(cs.backgroundColor);
    if(bgLum!==null && bgLum>0.72){
      el.setAttribute('data-eddy-orig-bg',el.style.getPropertyValue('background-color')||'');
      el.setAttribute('data-eddy-orig-bgp',el.style.getPropertyPriority('background-color')||'');
      el.style.setProperty('background-color','#181C23','important');
      var fgLum=parseLuminance(cs.color);
      if(fgLum!==null && fgLum<0.35){
        el.setAttribute('data-eddy-orig-col',el.style.getPropertyValue('color')||'');
        el.setAttribute('data-eddy-orig-colp',el.style.getPropertyPriority('color')||'');
        el.style.setProperty('color','#E6EAF0','important');
      }
      el.setAttribute(NODE_ATTR,'1');
    }
    return true;
  }

  function scanSubtree(startEl, limit){
    if(!startEl || startEl.nodeType!==1 || limit<=0) return 0;
    var count=0;
    var stack=[startEl];
    while(stack.length>0 && count<limit){
      var cur=stack.pop();
      if(!cur || cur.nodeType!==1) continue;
      if(SKIP_TAGS[cur.tagName] || cur.id===STYLE_ID) continue;
      maybeDarkenElement(cur);
      count++;
      var kids=cur.children;
      if(kids){
        for(var k=kids.length-1;k>=0;k--){
          stack.push(kids[k]);
        }
      }
    }
    return count;
  }

  function flushPending(){
    state.timer=null;
    if(!state.enabled || state.tornDown || state.applying) return;
    state.applying=true;
    try{
      var budget=MAX_SCAN;
      while(state.pending.length>0 && budget>0){
        var node=state.pending.shift();
        if(!node || !node.isConnected) continue;
        budget-=scanSubtree(node,budget);
      }
      if(state.pending.length>0 && !state.tornDown && state.enabled){
        state.timer=setTimeout(flushPending,DEBOUNCE_MS);
      }
    }catch(e){
      cleanupDom(false);
      state.tornDown=true;
    }finally{
      state.applying=false;
    }
  }

  state.applying=true;
  state.tornDown=false;
  state.enabled=true;
  try{
    var existing=document.getElementById(STYLE_ID);
    if(existing) existing.disabled=true;
    var isNative=hasNativeDarkTheme();
    if(isNative){
      cleanupDom(true);
      return 'NATIVE_DARK';
    }
    if(existing){
      existing.disabled=false;
      if(existing.textContent!==CSS) existing.textContent=CSS;
    }else{
      var style=document.createElement('style');
      style.id=STYLE_ID;
      style.textContent=CSS;
      var target=document.head||root;
      if(!target){
        state.enabled=false;
        return 'DEFERRED';
      }
      target.appendChild(style);
    }
    if(root){
      root.removeAttribute(NATIVE_ATTR);
      if(root.getAttribute(ROOT_ATTR)!=='on') root.setAttribute(ROOT_ATTR,'on');
    }
    var scanRoot=document.body||root;
    if(scanRoot) scanSubtree(scanRoot,MAX_SCAN);

    if(!state.observer && typeof MutationObserver==='function' && (document.body||root)){
      var obs=new MutationObserver(function(mutations){
        if(!state.enabled || state.applying || state.tornDown) return;
        for(var i=0;i<mutations.length;i++){
          var m=mutations[i];
          if(!m || m.type!=='childList' || !m.addedNodes || !m.addedNodes.length) continue;
          if(m.target && m.target.id===STYLE_ID) continue;
          for(var j=0;j<m.addedNodes.length;j++){
            var n=m.addedNodes[j];
            if(!n || n.nodeType!==1 || n.id===STYLE_ID) continue;
            if(n.hasAttribute && n.hasAttribute(NODE_ATTR)) continue;
            if(state.pending.length<MAX_QUEUE) state.pending.push(n);
          }
        }
        if(state.pending.length>0 && !state.timer){
          state.timer=setTimeout(flushPending,DEBOUNCE_MS);
        }
      });
      obs.observe(document.body||root,{childList:true,subtree:true});
      state.observer=obs;
    }
    return 'APPLIED';
  }finally{
    state.applying=false;
  }
}catch(err){
  try{
    var st=window.__eddyDarkState;
    if(st){
      st.enabled=false;
      st.tornDown=true;
      if(st.timer){ clearTimeout(st.timer); st.timer=null; }
      if(st.observer){ try{ st.observer.disconnect(); }catch(e2){} st.observer=null; }
    }
    var bad=document.getElementById('$STYLE_ID');
    if(bad && bad.parentNode) bad.parentNode.removeChild(bad);
  }catch(e3){}
  return 'ERROR:'+(err&&err.message?err.message:String(err));
}
})()"""
    }

    /** Builds the cleanup JS script that disconnects observers and restores original page appearance. */
    fun buildDisableJs(): String = """(function(){
try{
  var st=window.__eddyDarkState;
  if(st){
    st.enabled=false;
    st.tornDown=true;
    if(st.timer){ clearTimeout(st.timer); st.timer=null; }
    if(st.observer){ try{ st.observer.disconnect(); }catch(e){} st.observer=null; }
    if(st.pending) st.pending.length=0;
  }
  var s=document.getElementById('$STYLE_ID');
  if(s && s.parentNode) s.parentNode.removeChild(s);
  var list=document.querySelectorAll('[$NODE_ATTR]');
  for(var i=0;i<list.length;i++){
    var el=list[i];
    var ob=el.getAttribute('data-eddy-orig-bg');
    var obp=el.getAttribute('data-eddy-orig-bgp')||'';
    var oc=el.getAttribute('data-eddy-orig-col');
    var ocp=el.getAttribute('data-eddy-orig-colp')||'';
    if(ob){ el.style.setProperty('background-color',ob,obp); }
    else { el.style.removeProperty('background-color'); }
    if(oc!==null){
      if(oc){ el.style.setProperty('color',oc,ocp); }
      else { el.style.removeProperty('color'); }
    }
    el.removeAttribute('data-eddy-orig-bg');
    el.removeAttribute('data-eddy-orig-bgp');
    el.removeAttribute('data-eddy-orig-col');
    el.removeAttribute('data-eddy-orig-colp');
    el.removeAttribute('$NODE_ATTR');
  }
  var root=document.documentElement;
  if(root){
    root.removeAttribute('$ROOT_ATTR');
    root.removeAttribute('$NATIVE_ATTR');
  }
  return 'DISABLED';
}catch(e){
  return 'DISABLED';
}
})()"""

    private fun decodeJsString(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.length < 2 || !trimmed.startsWith('"') || !trimmed.endsWith('"')) return trimmed
        val inner = trimmed.substring(1, trimmed.length - 1)
        return buildString(inner.length) {
            var i = 0
            while (i < inner.length) {
                val c = inner[i]
                if (c == '\\' && i + 1 < inner.length) {
                    val next = inner[i + 1]
                    when (next) {
                        '\\' -> { append('\\'); i += 2 }
                        '"' -> { append('"'); i += 2 }
                        '/' -> { append('/'); i += 2 }
                        'n' -> { append('\n'); i += 2 }
                        'r' -> { append('\r'); i += 2 }
                        't' -> { append('\t'); i += 2 }
                        'u' -> if (i + 5 < inner.length) {
                            val code = inner.substring(i + 2, i + 6).toIntOrNull(16)
                            if (code != null) { append(code.toChar()); i += 6 } else { append(c); i++ }
                        } else {
                            append(c); i++
                        }
                        else -> { append(next); i += 2 }
                    }
                } else {
                    append(c)
                    i++
                }
            }
        }
    }

    fun parseApplyOutcome(rawJsResult: String?): Pair<ApplyOutcome, String?> {
        if (rawJsResult.isNullOrBlank() || rawJsResult == "null") {
            return ApplyOutcome.DEFERRED to null
        }
        val decoded = decodeJsString(rawJsResult)
        return when {
            decoded == "APPLIED" -> ApplyOutcome.APPLIED to null
            decoded == "NATIVE_DARK" -> ApplyOutcome.NATIVE_DARK to null
            decoded == "DEFERRED" -> ApplyOutcome.DEFERRED to null
            decoded == "SKIPPED" -> ApplyOutcome.SKIPPED to null
            decoded.startsWith("ERROR:") -> ApplyOutcome.ERROR to decoded.removePrefix("ERROR:").trim()
            else -> ApplyOutcome.ERROR to "Unexpected dark-mode evaluation result"
        }
    }

    fun apply(view: EddyWebView, url: String, onError: (String) -> Unit) {
        applyWithDiagnostics(view, url) { failedUrl, _ -> onError(failedUrl) }
    }

    fun applyWithDiagnostics(view: EddyWebView, url: String, onError: (failedUrl: String, reason: String?) -> Unit) {
        if (view.isDestroyed) return
        val dispatched = view.safeEvaluateJavascript(buildApplyJs()) { raw ->
            if (view.isDestroyed) return@safeEvaluateJavascript
            val (outcome, reason) = parseApplyOutcome(raw)
            if (outcome == ApplyOutcome.ERROR) {
                remove(view)
                onError(url, reason)
            }
        }
        if (!dispatched && !view.isDestroyed) {
            onError(url, "evaluateJavascript_rejected")
        }
    }

    fun remove(view: EddyWebView) {
        if (view.isDestroyed) return
        view.safeEvaluateJavascript(buildDisableJs(), null)
    }
}
