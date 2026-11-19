# Eddy Browser — Technical Audit & MCP Integration Blueprint

> **Audit Date:** 2026-11-19  
> **Scope:** Read-only architectural, WebView/CSS, persistence, security, and Model Context Protocol (MCP) feasibility audit of `app.eddy.browser` (`v0.6.36-beta`, `versionCode 46`).

---

## 1. Project Overview

| Field | Value |
| :--- | :--- |
| **Application Name** | `Eddy` (`app/src/main/res/values/strings.xml`, `settings.gradle.kts`) |
| **Package Name / Application ID** | `app.eddy.browser` (`applicationIdSuffix = ".debug"` in `debug` buildType) |
| **Version** | `0.6.36-beta` (`versionCode = 46`) |
| **Architecture** | **Single-Activity MVVM** with manual Dependency Injection (`EddyApp` -> `AppContainer`) and custom `Screen` enum + `settingsStack` state machine (`MainActivity` + `BrowserViewModel` implementing `BrowserHost`) |
| **Programming Languages** | **100% Kotlin** (`app/src/main/java/app/eddy/browser/` — 99 `.kt` files, 0 `.java` files) |
| **UI Framework** | **100% Jetpack Compose + Material 3 Expressive** (0 XML layout screens; 1 XML remote widget layout `widget_search`) |
| **Browser Engine** | **Android System `android.webkit.WebView`** + `androidx.webkit:webkit:1.17.0` (`WebViewCompat`, `WebSettingsCompat`, `ProfileStore`, `BackForwardCache`, `PrerenderUrlAsync`, `WebAuthn`) |
| **Persistence** | **Room** (`eddy.db`, v5), **Jetpack DataStore Preferences** (`eddy_settings`), **`AtomicFile` Parcelable binary tab state** (`filesDir/tabstate/*.bin`), **`tabs.json`**, **Disk LRU / File caches** (`thumbs/`, `favicons/`), **Android Keystore AES-GCM** (`PasswordVault`) |
| **Minimum SDK** | `29` (Android 10) |
| **Target SDK** | `36` |
| **Compile SDK** | `36` (`buildToolsVersion = "36.0.0"`) |
| **Java Compatibility** | Java `21` |
| **Gradle Version** | `9.3.1` (`gradle/wrapper/gradle-wrapper.properties`) |
| **Android Gradle Plugin (AGP)** | `9.1.1` (`gradle/libs.versions.toml`) |
| **Kotlin / KSP Versions** | Kotlin `2.2.10` (`kotlin-compose`), KSP `2.3.5` |
| **Main Entry Activity** | `app.eddy.browser.MainActivity` (launched via 11 `<activity-alias>` entries; `.Launcher` enabled by default) |
| **Application Class** | `app.eddy.browser.EddyApp` |
| **Background Execution** | Native Android `Service` (`DownloadService`, `MediaService`, `ExitCleanupService`) & `JobService` (`FilterUpdateService`); **no `WorkManager`**, **no Fragments**, **no Hilt**, **no Navigation Compose** |

---

## 2. Technology Stack

| Layer | Libraries & Platform APIs (`gradle/libs.versions.toml`, `app/build.gradle.kts`) |
| :--- | :--- |
| **Core Kotlin & Coroutines** | Kotlin `2.2.10`, `kotlinx-coroutines-android:1.10.2`, `kotlinx-serialization-json:1.9.0` |
| **Jetpack Compose & UI** | `compose-bom:2026.02.01`, `androidx.compose.material3:1.5.0-alpha13` (`MaterialExpressiveTheme`, `MotionScheme.expressive()`), `material-icons-extended`, `androidx.activity:activity-compose:1.13.0`, `androidx.graphics:graphics-shapes:1.1.0`, `androidx.dynamicanimation:dynamicanimation:1.1.0` |
| **Lifecycle & Core AndroidX** | `androidx.core:core-ktx:1.18.0`, `androidx.lifecycle:lifecycle-runtime-compose:2.10.0`, `androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0`, `androidx.biometric:biometric:1.1.0` |
| **WebView & Browser Engine** | `android.webkit.WebView`, `androidx.webkit:webkit:1.17.0` (`DOCUMENT_START_SCRIPT`, `WEB_MESSAGE_LISTENER`, `ALGORITHMIC_DARKENING`, `MULTI_PROFILE`, `USER_AGENT_METADATA`, `BACK_FORWARD_CACHE`, `PRERENDER_URL`, `WEB_AUTHENTICATION`) |
| **Storage & Database** | `androidx.room:room-runtime:2.8.5`, `room-ktx`, `room-compiler` (KSP), `androidx.datastore:datastore-preferences:1.2.1`, `android.security.keystore` (`AES/GCM/NoPadding`), `MediaStore.Downloads`, `FileProvider` |
| **Networking** | Standard JDK `java.net.HttpURLConnection` / `java.net.URL` + WebView Chromium network stack (**no OkHttp, Retrofit, or Ktor**). **No Coil/Glide** (`BitmapFactory` + `LruCache` used instead) |
| **Platform Hardware / OS APIs** | `android.speech.tts.TextToSpeech`, `android.speech.SpeechRecognizer`, `android.graphics.pdf.PdfRenderer`, `android.print.PrintManager`, `android.app.role.RoleManager`, `android.app.job.JobScheduler`, `android.app.AlarmManager`, `android.content.ClipboardManager` |
| **Bundled Web Assets** | `app/src/main/assets/readability.js` (Mozilla Readability), `eruda.min.js` (optional on-page DevTools console), `filters/eddy-ads.txt`, `filters/eddy-trackers.txt` |
| **Testing** | `junit:4.13.2`, `robolectric:4.14.1`, `androidx.test.core:1.6.1`, `kotlinx-coroutines-test:1.10.2`, `androidx.room:room-testing:2.8.5` |

---

## 3. Complete Project Structure

```text
/
├── build.gradle.kts / settings.gradle.kts
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/gradle-wrapper.properties
└── app/
    ├── build.gradle.kts / proguard-rules.pro
    ├── schemas/app.eddy.browser.data.database.EddyDatabase/ (1.json .. 5.json)
    └── src/main/
        ├── AndroidManifest.xml
        ├── assets/
        │   ├── eruda.min.js
        │   ├── readability.js
        │   └── filters/
        │       ├── eddy-ads.txt
        │       └── eddy-trackers.txt
        ├── res/
        │   ├── values/ & values-night/ (colors.xml, themes.xml, strings.xml)
        │   ├── drawable*/, mipmap*/, xml/ (file_paths.xml, widget_search_info.xml), layout/ (widget_search.xml)
        └── java/app/eddy/browser/
            ├── EddyApp.kt
            ├── AppContainer.kt
            ├── MainActivity.kt
            ├── bookmarks/     (BookmarkManager.kt, BookmarksScreen.kt)
            ├── browser/       (AutofillScript.kt, BrowserHost.kt, BrowserScreen.kt, BrowserTab.kt,
            │                   BrowserViewModel.kt, ClipboardBridge.kt, DevTools.kt, EddyChromeClient.kt,
            │                   EddyWebViewClient.kt, EddyWebView.kt, ErrorPage.kt, FaviconCache.kt,
            │                   LinkSheet.kt, LoginPrompt.kt, MediaService.kt, MenuSheet.kt, Omnibox.kt,
            │                   PromptDialogs.kt, Prompts.kt, ReadAloud.kt, ReaderMode.kt, ReaderTheme.kt,
            │                   SavedArticleRepository.kt, SavedArticlesScreen.kt, SiteInfoSheet.kt,
            │                   SourceScreen.kt, SuggestionSource.kt, TabManager.kt, Translate.kt,
            │                   UserAgents.kt, VoiceInput.kt, WebViewFactory.kt)
            ├── data/
            │   ├── database/    (Daos.kt, EddyDatabase.kt, Entities.kt)
            │   ├── models/      (Models.kt)
            │   └── preferences/ (SettingsStore.kt)
            ├── downloads/     (BlobDownloader.kt, DownloadEngine.kt, DownloadNames.kt, DownloadPolicy.kt,
            │                   DownloadRanges.kt, DownloadService.kt, DownloadsScreen.kt, PdfScreen.kt)
            ├── history/       (HistoryManager.kt, HistoryScreen.kt)
            ├── home/          (HomeScreen.kt, ShortcutEditor.kt, ShortcutGrid.kt, WebsiteRecommender.kt)
            ├── onboarding/    (OnboardingScreen.kt)
            ├── passwords/     (ClipboardClearReceiver.kt, Csv.kt, PasswordManager.kt, PasswordsScreen.kt, PasswordVault.kt)
            ├── privacy/       (ContentBlocker.kt, ExitCleanupService.kt, FilterUpdateService.kt, NetworkRule.kt, SitePermissions.kt)
            ├── settings/      (AppearanceSettings.kt, AppIcon.kt, LegalSettings.kt, PrivacySettings.kt,
            │                   SearchSettings.kt, SettingsComponents.kt, SettingsPage.kt, SettingsScreen.kt,
            │                   SettingsSearch.kt, SupportSettings.kt)
            ├── tabs/          (TabSwitcher.kt, TabThumbnailManager.kt)
            ├── ui/
            │   ├── animation/ (Entrance.kt, Haptics.kt, Motion.kt)
            │   ├── components/(Basics.kt, ScreenScaffold.kt, ShortcutStyle.kt, SiteIcon.kt)
            │   ├── sakuga/    (SakugaAnimationController.kt, SakugaButton.kt, SakugaControls.kt,
            │   │               SakugaElasticComponents.kt, SakugaImpactEffect.kt, SakugaMotionController.kt,
            │   │               SakugaParticleRenderer.kt, SakugaSurfaces.kt)
            │   ├── shapes/    (ExpressiveShapes.kt)
            │   └── theme/     (Dimens.kt, Palette.kt, Theme.kt, Type.kt)
            ├── util/          (Streams.kt, Strings.kt, UrlUtils.kt)
            └── widget/        (SearchWidget.kt)
```

---

## 4. Activity & Class Inventory

> Every class, object, interface, service, receiver, and top-level UI module across all 99 `.kt` source files in `app/src/main/java/app/eddy/browser/` is inventoried below. (**0 Fragments**, **0 custom ContentProviders** beyond AndroidX `FileProvider` declared in `AndroidManifest.xml`).

### A. Application, Entry Activity & Dependency Container

| Class / Symbol | File Path | Responsibility | Key Public Methods / Properties | Dependencies | MCP Relevance |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `EddyApp` | `app/src/main/java/app/eddy/browser/EddyApp.kt` | Application subclass; installs `Strings`, holds `AppContainer`, registers `LowMemory` / `TrimMemory` hooks | `val container: AppContainer`, `onCreate()`, `onTrimMemory()`, `onLowMemory()`, `scheduleStartupWarmup()` | `AppContainer`, `Strings`, `MediaService` | **HIGH** — Ideal anchor for `McpContainer` / Service lifecycle |
| `AppContainer` | `app/src/main/java/app/eddy/browser/AppContainer.kt` | Manual lazy DI graph owning DB, managers, stores, and `SupervisorJob` scope | `settingsStore`, `settings`, `database`, `favicons`, `blocker`, `sitePermissions`, `passwordManager`, `history`, `bookmarks`, `savedArticles`, `thumbnails`, `shortcuts`, `downloadEngine`, `warmDatabase()` | Room, DataStore, `Context` | **CRITICAL** — Single entry point for headless MCP backend access |
| `MainActivity` | `app/src/main/java/app/eddy/browser/MainActivity.kt` | Single `ComponentActivity` host; handles edge-to-edge, Intent routing, PiP, runtime permissions, file chooser, Compose root | `onCreate()`, `onNewIntent()`, `onPause()`, `onStop()`, `onUserLeaveHint()`, `onPictureInPictureModeChanged()` | `BrowserViewModel`, `BrowserScreen`, `EddyTheme`, `AppIcon` | **HIGH** — Intent routing (`ACTION_VIEW`, `WEB_SEARCH`, `SEND`) & foreground UI host |

### B. Core Browser Engine, ViewModel, Tabs & WebView Wrappers

| Class / Symbol | File Path | Responsibility | Key Public Methods / Properties | Dependencies | MCP Relevance |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `BrowserHost` (interface) | `app/src/main/java/app/eddy/browser/browser/BrowserHost.kt` | Contract decoupling WebView/Clients from `BrowserViewModel` | `blocker`, `sites`, `favicons`, `settings`, `pageBackground`, `onPageFinished()`, `onProgress()`, `onReceivedIcon()`, `onDownload()`, `requestPermission()`, `requestGeolocation()`, `openNewWindow()`, `closeWindow()`, `handleExternalIntent()`, `onRenderGone()` | `ContentBlocker`, `SitePermissions`, `FaviconCache`, `Settings` | **HIGH** — Abstraction boundary for browser callbacks |
| `BrowserViewModel` | `app/src/main/java/app/eddy/browser/browser/BrowserViewModel.kt` | Primary `AndroidViewModel` implementing `BrowserHost`; orchestrates tabs, navigation, bookmarks, history, reader, downloads, passwords, TTS, PiP, UI overlays | `navigate()`, `navigate(tab, input)`, `openInNewTab()`, `newTab()`, `closeTab()`, `selectTab()`, `restoreClosedTab()`, `goBack()`, `goForward()`, `reload()`, `stop()`, `toggleBookmark()`, `toggleDesktop()`, `toggleReader()`, `setReaderTheme()`, `launchSettings()`, `startFind()`, `findNext()`, `toggleReadAloud()`, `viewPageSource()`, `clearBrowsingData()` | `TabManager`, `WebViewFactory`, `HistoryManager`, `BookmarkManager`, `DownloadEngine`, `PasswordManager`, `SavedArticleRepository`, `ReadAloud` | **CRITICAL** — Primary control plane for all MCP actions |
| `Screen`, `FindState`, `HitTarget`, `OpenPdf`, `OpenSource`, `DataType` | `app/src/main/java/app/eddy/browser/browser/BrowserViewModel.kt` | Routing enum & transient UI state holders for modals/screens | `Screen.{BROWSER, TABS, HISTORY, BOOKMARKS, DOWNLOADS, PDF, SOURCE, PASSWORDS, SAVED_ARTICLES, SETTINGS}` | — | **MEDIUM** — Mirrors active app screen state |
| `BrowserTab`, `Security`, `ErrorKind`, `PageError` | `app/src/main/java/app/eddy/browser/browser/BrowserTab.kt` | Observable (`mutableStateOf`) state holder for a single tab & its attached `EddyWebView` | `id`, `incognito`, `title`, `url`, `pageHost`, `isLoading`, `progress`, `canGoBack`, `canGoForward`, `security`, `favicon`, `error`, `desktopActive`, `readerActive`, `readerAvailable`, `devToolsActive`, `blockedCount`, `host`, `isHome`, `attach()`, `detach()` | `EddyWebView`, `Bitmap`, `SslErrorHandler` | **CRITICAL** — Canonical read/write model of each browser tab |
| `TabManager` | `app/src/main/java/app/eddy/browser/browser/TabManager.kt` | Manages normal + incognito tab lists, LRU eviction (`MAX_LIVE_WEBVIEWS = 4`), `tabs.json` & `AtomicFile` Parcelable state persistence, closed-tab stack | `normalTabs`, `incognitoTabs`, `tabs`, `selected`, `closedCount`, `add()`, `select()`, `close()`, `closeAll()`, `restoreClosed()`, `move()`, `restore()`, `persist()`, `peekWebView()`, `requireWebView()`, `clearSavedState()` | `BrowserTab`, `TabThumbnailManager`, `Bundle` | **CRITICAL** — Tab CRUD, enumeration, and WebView hydration |
| `EddyWebView` | `app/src/main/java/app/eddy/browser/browser/EddyWebView.kt` | `WebView` subclass adding nested scrolling fix, page background sync, `postVisualStateCallback` paint tracking, and origin-checked JS bridges | `attach()`, `applyBackgroundColor()`, `onScrollChanged`, `addPageBridge()`, `saveState()`, `restoreState()` | `WebView`, `WebViewCompat` | **HIGH** — Target for `evaluateJavascript`, scroll, capture |
| `WebViewFactory` | `app/src/main/java/app/eddy/browser/browser/WebViewFactory.kt` | Configures `WebSettings`, `ProfileStore` (incognito isolation), `CookieManager`, HTTPS upgrades, `ALGORITHMIC_DARKENING`, and injects document-start scripts | `create()`, `applyGlobal()`, `prepare()`, `upgrade()`, `allowCertificateException()`, `hasCertificateException()`, `isOnline()`, `profileSupported`, `pageBridgeSupported` | `BrowserHost`, `WebViewCompat`, `WebSettingsCompat`, `ProfileStore` | **CRITICAL** — Insertion point for dark-mode / CSS `addDocumentStartJavaScript` |
| `EddyWebViewClient` | `app/src/main/java/app/eddy/browser/browser/EddyWebViewClient.kt` | `WebViewClient` for HTTPS-only upgrades, ad/tracker request interception (`shouldInterceptRequest`), SSL/HTTP errors, HTTP auth, and external `intent://` handling | `shouldOverrideUrlLoading()`, `shouldInterceptRequest()`, `onPageStarted()`, `onPageCommitVisible()`, `onPageFinished()`, `doUpdateVisitedHistory()`, `onReceivedError()`, `onReceivedSslError()`, `onReceivedHttpAuthRequest()` | `BrowserTab`, `BrowserHost`, `WebViewFactory` | **HIGH** — Navigation completion & blocking telemetry |
| `EddyChromeClient` | `app/src/main/java/app/eddy/browser/browser/EddyChromeClient.kt` | `WebChromeClient` for title, progress, favicons, JS `alert`/`confirm`/`prompt`, popup windows, fullscreen video, file chooser, geolocation & permissions | `onProgressChanged()`, `onReceivedTitle()`, `onReceivedIcon()`, `onCreateWindow()`, `onCloseWindow()`, `onShowCustomView()`, `onHideCustomView()`, `onShowFileChooser()`, `onPermissionRequest()`, `onGeolocationPermissionsShowPrompt()` | `BrowserTab`, `BrowserHost` | **MEDIUM** — Progress, title, and dialog handling |

### C. Webpage Intelligence, Reader Mode, Translation & Utilities

| Class / Symbol | File Path | Responsibility | Key Public Methods / Properties | Dependencies | MCP Relevance |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `ReaderMode` | `app/src/main/java/app/eddy/browser/browser/ReaderMode.kt` | Runs Mozilla `readability.js` to test readability, transform DOM into clean semantic reader HTML with `--er-*` CSS vars, extract articles for offline saving & TTS | `checkAvailability()`, `show()`, `applyTheme()`, `extractArticle()`, `extractReadableText()`, `buildOfflineDocumentHtml()`, `isReducedMotion()` | `readability.js`, `WebView.evaluateJavascript` | **CRITICAL** — Existing structured page extraction + CSS-var live theming engine |
| `ReaderThemeManager`, `ReaderPalette`, `ReaderToolbarPill` | `app/src/main/java/app/eddy/browser/browser/ReaderTheme.kt` | Resolves Dark/Light Reader Mode palettes and renders floating Reader toolbar pill | `ReaderThemeManager.paletteFor()`, `ReaderThemeManager.nextMode()` | `ReaderThemeMode` | **HIGH** — Reference model for live Light/Dark webpage theme switching |
| `SavedArticleRepository`, `ExtractedReaderArticle` | `app/src/main/java/app/eddy/browser/browser/SavedArticleRepository.kt` | Fetches article images & stores offline Reader Mode articles in Room | `articles`, `isSaved()`, `findByUrl()`, `saveArticle()`, `loadOfflineHtml()`, `delete()`, `deleteByUrl()`, `clearAll()` | `SavedArticleDao`, `ReaderMode` | **MEDIUM** — Offline readable content store |
| `ReadAloud`, `SpeechChunks` | `app/src/main/java/app/eddy/browser/browser/ReadAloud.kt` | Sentence-chunked `TextToSpeech` playback with pause/resume/progress | `speak()`, `pause()`, `resume()`, `stop()`, `release()`, `SpeechChunks.split()` | Android `TextToSpeech` | **LOW** — Audio readout tool option |
| `Translate` | `app/src/main/java/app/eddy/browser/browser/Translate.kt` | Builds Google Translate web-proxy URLs without API keys | `languages()`, `label()`, `isTranslated()`, `url()` | `Locale` | **MEDIUM** — Could expose `translate_page` |
| `SuggestionSource`, `Suggestion`, `SuggestionKind` | `app/src/main/java/app/eddy/browser/browser/SuggestionSource.kt` | Combines clipboard, bookmarks, history, popular domains, and remote search engine suggest JSON | `initial()`, `local()`, `remote()` | `HistoryManager`, `BookmarkManager`, `WebsiteRecommender` | **MEDIUM** — Search/omnibox autocomplete |
| `DevTools` | `app/src/main/java/app/eddy/browser/browser/DevTools.kt` | Injects/hides bundled `eruda.min.js` on-page console | `available()`, `show()`, `hide()` | `eruda.min.js`, `WebView` | **MEDIUM** — Precedent for DOM/console JS injection |
| `ErrorPage` | `app/src/main/java/app/eddy/browser/browser/ErrorPage.kt` | Compose full-screen error overlay for offline/DNS/SSL/blocked pages | `ErrorPage()` Composable | `PageError` | **LOW** — UI state |
| `FaviconCache` | `app/src/main/java/app/eddy/browser/browser/FaviconCache.kt` | 96-entry `LruCache` + SHA-256 disk PNG cache (`cacheDir/favicons`), direct HTTPS HTML `<link rel=icon>` fetcher | `peek()`, `put()`, `load()`, `fetch()`, `clear()` | `HttpURLConnection` | **LOW** — Site metadata |
| `UserAgents` | `app/src/main/java/app/eddy/browser/browser/UserAgents.kt` | Strips `wv` token for Chrome parity, builds Linux desktop UA & Client Hints metadata | `mobile()`, `desktop()`, `asChrome()` | `UserAgentMetadata` | **LOW** — Desktop/mobile UA switching |
| `AutofillScript`, `ClipboardBridge` | `app/src/main/java/app/eddy/browser/browser/AutofillScript.kt`, `ClipboardBridge.kt` | Document-start JS scripts for password form detection/fill and gated `navigator.clipboard.readText()` | `SOURCE`, `NAME`, `PASSWORD_FIELD_VISIBLE` | `EddyWebView.addPageBridge` | **SECURITY CRITICAL** — **Must never be exposed to MCP** |
| `MediaService` | `app/src/main/java/app/eddy/browser/browser/MediaService.kt` | `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK` keeping background WebView audio alive | `start()`, `stop()`, `isPlaying()`, `createChannel()` | `AudioManager`, `MainActivity` | **MEDIUM** — Background keep-alive pattern reference |
| `Prompts`, `Prompt`, `UiEffect`, `SnackbarMessage`, `WebPermissionCoordinator` | `app/src/main/java/app/eddy/browser/browser/Prompts.kt` | Converts WebView permission/geolocation/SSL/auth/paste/risky-download events into queued `Prompt`s & `UiEffect`s | `onWebRequest()`, `onGeolocation()`, `cancel()` | `SitePermissions`, `ContextCompat` | **HIGH** — Reference model for MCP user-approval prompts |
| `VoiceState`, `VoiceInputButton`, `voiceTrace` | `app/src/main/java/app/eddy/browser/browser/VoiceInput.kt` | On-device `SpeechRecognizer` omnibox dictation button & visualizer | `VoiceInputButton()`, `rememberVoiceState()` | `SpeechRecognizer` | **LOW** — UI input |
| `UrlUtils` | `app/src/main/java/app/eddy/browser/util/UrlUtils.kt` | URL/host parsing, navigation normalization, same-site eTLD+1 comparison, HTTPS checks | `isUrl()`, `normalize()`, `search()`, `host()`, `displayHost()`, `compactDomain()`, `origin()`, `isWebUrl()`, `isHttps()`, `isLocalHost()`, `sameSite()` | `Uri`, `Patterns` | **HIGH** — Host/origin validation for MCP & CSS filters |
| `Streams`, `Strings` | `app/src/main/java/app/eddy/browser/util/Streams.kt`, `Strings.kt` | 16 MB capped stream reader (`readTextCapped`), global string resource lookup (`str`, `plural`) | `readTextCapped()`, `Strings.install()`, `str()`, `plural()` | `Resources` | **LOW** — Shared utilities |

### D. Data, Persistence, Privacy & Passwords

| Class / Symbol | File Path | Responsibility | Key Public Methods / Properties | Dependencies | MCP Relevance |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `EddyDatabase` | `app/src/main/java/app/eddy/browser/data/database/EddyDatabase.kt` | Room database (`"eddy.db"`, v5, AutoMigrations 1->5) | `history()`, `bookmarks()`, `downloads()`, `sites()`, `logins()`, `savedArticles()` | Room | **HIGH** — Core local data store |
| `Entities.kt` (9 Entities) | `app/src/main/java/app/eddy/browser/data/database/Entities.kt` | `HistoryEntry`, `SiteVisit`, `Bookmark`, `BookmarkFolder`, `DownloadEntry`, `SiteSettings`, `SavedLogin`, `LoginBlock`, `SavedArticle`, `SavedArticleImage` | Schema models & constants (`SiteSettings.ALLOW`/`BLOCK`) | Room | **HIGH** — Defines DB schema & gaps (no `darkMode` col yet) |
| `Daos.kt` (6 DAOs) | `app/src/main/java/app/eddy/browser/data/database/Daos.kt` | `HistoryDao`, `BookmarkDao`, `DownloadDao`, `SiteSettingsDao`, `LoginDao`, `SavedArticleDao` | Reactive `Flow` queries, `suggest()`, `upsert()`, `delete()`, `clear()`, FKs | Room | **HIGH** — Direct query layer for MCP read/write tools |
| `Models.kt` | `app/src/main/java/app/eddy/browser/data/models/Models.kt` | Immutable config models & enums (`Settings`, `ThemeMode`, `ReaderThemeMode`, `SearchEngine`, `FilterList`, `Shortcut`, `MotionPref`, `UserAgentMode`, etc.) | Data classes (`@Serializable`) & built-in search/filter catalogs | `kotlinx.serialization` | **HIGH** — Complete settings surface for MCP |
| `SettingsStore` | `app/src/main/java/app/eddy/browser/data/preferences/SettingsStore.kt` | DataStore Preferences persistence + JSON backup export/import + legacy migration | `settings: Flow<Settings>`, `update()`, `exportJson()`, `importJson()` | `DataStore<Preferences>`, `Json` | **HIGH** — Settings read/write & backup |
| `HistoryManager` | `app/src/main/java/app/eddy/browser/history/HistoryManager.kt` | Records non-incognito visits (`ignoreErrors`), searches/suggests, computes frequent/recent sites, 90-day pruning | `all()`, `search()`, `suggest()`, `frequent()`, `recentSites()`, `record()`, `remove()`, `clearRange()`, `clear()`, `prune()` | `HistoryDao` | **HIGH** — Backs MCP history tools |
| `BookmarkManager`, `BookmarksSnapshot` | `app/src/main/java/app/eddy/browser/bookmarks/BookmarkManager.kt` | Folder & bookmark CRUD, `isBookmarked`, Netscape HTML import/export | `observe()`, `bookmarksIn()`, `isBookmarked()`, `toggle()`, `add()`, `update()`, `remove()`, `createFolder()`, `renameFolder()`, `deleteFolder()`, `suggest()`, `exportHtml()`, `importHtml()` | `BookmarkDao` | **HIGH** — Backs MCP bookmark tools |
| `TabThumbnailManager` | `app/src/main/java/app/eddy/browser/tabs/TabThumbnailManager.kt` | 480px JPEG preview capture (`webView.draw(canvas)`) + LRU cache + `filesDir/thumbs/*.jpg` | `peek()`, `capture()`, `load()`, `remove()`, `trim()`, `clear()`, `prune()` | `BrowserTab` | **MEDIUM** — Re-usable screenshot capture pattern |
| `DownloadEngine`, `DownloadSnapshot`, `DownloadOutcome` | `app/src/main/java/app/eddy/browser/downloads/DownloadEngine.kt` | Multi-attempt HTTP(S) resumable downloader (`.part` files + `DownloadRanges`), `data:`/`blob:` import, MediaStore export, Room status sync | `all()`, `active`, `enqueue()`, `importFile()`, `pause()`, `resume()`, `retry()`, `cancel()`, `remove()`, `clearCompleted()`, `openDownloadedFile()` | `DownloadDao`, `DownloadService`, `DownloadPolicy`, `DownloadNames` | **HIGH** — Backs MCP download tools |
| `DownloadService` | `app/src/main/java/app/eddy/browser/downloads/DownloadService.kt` | `FOREGROUND_SERVICE_TYPE_DATA_SYNC` with live progress & completion notifications | `start()`, `update()`, `finish()`, `createChannel()` | `DownloadEngine` | **HIGH** — Reference `ForegroundService` implementation |
| `BlobDownloader`, `DownloadNames`, `DownloadPolicy`, `DownloadRanges` | `app/src/main/java/app/eddy/browser/downloads/*.kt` | Origin-verified chunked `EddyBlob` reader, filename/MIME parser, strict referrer policy, HTTP `Content-Range` validator | `BlobDownloader.start/onMessage/cancelTab`, `DownloadNames.forDownload/isExecutable`, `DownloadPolicy.referrerFor`, `DownloadRanges.total` | `DownloadEngine` | **LOW–MEDIUM** — Download sub-components |
| `ContentBlocker`, `NetworkRule`, `NetworkRules` | `app/src/main/java/app/eddy/browser/privacy/ContentBlocker.kt`, `NetworkRule.kt` | Ad/tracker hosts + Adblock URL rule matcher (`eddy-ads.txt`, `eddy-trackers.txt`, remote lists) | `isBlocked()`, `isRequestBlocked()`, `ensureLoaded()`, `reload()`, `update()`, `cachedRuleCount()`, `deleteCache()` | `FilterList`, `UrlUtils` | **MEDIUM** — Read blocker stats / trigger updates |
| `SitePermissions`, `SiteFeature` | `app/src/main/java/app/eddy/browser/privacy/SitePermissions.kt` | In-memory `ConcurrentHashMap` + Room cache for synchronous `WebView` permission/zoom/JS/cookie/desktop lookups + incognito volatile overrides | `peek()`, `all()`, `set()`, `clearPrivate()`, `reset()`, `resetAll()` | `SiteSettingsDao` | **HIGH** — Best store for future per-site `DARK_MODE` override |
| `ExitCleanupService`, `ExitCleanup` | `app/src/main/java/app/eddy/browser/privacy/ExitCleanupService.kt` | Clears history, favicons, tabs, thumbs, cookies, WebStorage, WebView cache on swipe-exit or next cold start when `clearOnExit` is enabled | `ExitCleanupService.arm()`, `ExitCleanup.pending()`, `ExitCleanup.run()` | `AppContainer`, `CookieManager`, `WebStorage` | **MEDIUM** — Lifecycle impact on MCP persistent state |
| `FilterUpdateService` | `app/src/main/java/app/eddy/browser/privacy/FilterUpdateService.kt` | `JobService` (`JOB_ID = 4201`) refreshing remote filter lists every 24h when online & battery not low | `schedule()`, `onStartJob()`, `onStopJob()` | `ContentBlocker`, `SettingsStore` | **LOW** — Periodic sync pattern |
| `PasswordVault` | `app/src/main/java/app/eddy/browser/passwords/PasswordVault.kt` | Android Keystore (`eddy_login_key`, AES-256-GCM) credential cipher | `encrypt()`, `decrypt()`, `hasLock()` | `KeyStore`, `KeyguardManager` | **CRITICAL (RESTRICTED)** — Never expose to MCP |
| `PasswordManager` | `app/src/main/java/app/eddy/browser/passwords/PasswordManager.kt` | Saved login matching by site, password save/update, blocklist, encrypted storage, CSV import/export | `all()`, `count()`, `forSite()`, `hasSite()`, `find()`, `save()`, `update()`, `delete()`, `deleteAll()`, `block()`, `exportCsv()`, `importCsv()` | `LoginDao`, `PasswordVault`, `Csv` | **CRITICAL (RESTRICTED)** — Never expose to MCP |
| `ClipboardClearReceiver`, `Csv` | `app/src/main/java/app/eddy/browser/passwords/ClipboardClearReceiver.kt`, `Csv.kt` | `AlarmManager` 60s secret clipboard wiper; RFC 4180 CSV parser | `ClipboardClearReceiver.schedule()`, `Csv.parse()`, `Csv.row()` | `ClipboardManager`, `AlarmManager` | **NONE** — Internal security helper |
| `SearchWidget` | `app/src/main/java/app/eddy/browser/widget/SearchWidget.kt` | `AppWidgetProvider` launching `MainActivity` for search, voice, or incognito | `onUpdate()` | `MainActivity` | **LOW** — Widget launcher |
| `WebsiteRecommender`, `RecommendedSite` | `app/src/main/java/app/eddy/browser/home/WebsiteRecommender.kt` | On-device frequency+recency scoring over `SiteVisit` + 12 curated seed sites | `recommend()`, `defaultSeeds` | `SiteVisit`, `UrlUtils` | **LOW** — Home feed |

### E. Jetpack Compose UI Screens, Sheets, Settings & Sakuga Motion System

*(All UI composables consume `BrowserViewModel` / `Settings` state and do not contain separate business logic.)*

| File / Module | File Path Prefix (`app/src/main/java/app/eddy/browser/`) | Components & Purpose |
| :--- | :--- | :--- |
| **Browser Chrome & Sheets** | `browser/BrowserScreen.kt`, `Omnibox.kt`, `MenuSheet.kt`, `LinkSheet.kt`, `SiteInfoSheet.kt`, `PromptDialogs.kt`, `LoginPrompt.kt`, `SourceScreen.kt`, `SavedArticlesScreen.kt` | Top/bottom Dynamic Island-style `BrowserBar`, smooth top loading bar, `OmniboxEditor`, `FindBar`, `TabCountButton`, `MenuSheet`, `LinkSheet`, `SiteInfoSheet`, `PromptHost`, `LoginSaveBar`/`AutofillSheet`, `SourceScreen` (HTML/Logcat viewer), `SavedArticlesScreen` |
| **Library & Tab Screens** | `bookmarks/BookmarksScreen.kt`, `history/HistoryScreen.kt`, `downloads/DownloadsScreen.kt`, `downloads/PdfScreen.kt`, `passwords/PasswordsScreen.kt`, `tabs/TabSwitcher.kt`, `home/HomeScreen.kt`, `ShortcutGrid.kt`, `ShortcutEditor.kt`, `onboarding/OnboardingScreen.kt` | Material 3 Expressive screens with Sakuga motion (`TabSwitcher` grid/list drag & swipe-to-close, `PdfScreen` via `PdfRenderer`, `PasswordsScreen` gated by `BiometricPrompt`, `BookmarksScreen` HTML import/export, `HomeScreen` ambient drifting `MorphShape` blobs & shortcut grid) |
| **Settings UI** | `settings/SettingsScreen.kt`, `SettingsPage.kt`, `SettingsSearch.kt`, `SettingsComponents.kt`, `AppearanceSettings.kt`, `PrivacySettings.kt`, `SearchSettings.kt`, `LegalSettings.kt`, `SupportSettings.kt`, `AppIcon.kt` | Multi-page settings stack with search index (`settingsIndex`), export/import JSON, 11 dynamic `<activity-alias>` launcher icons (`AppIcon`), theme/AMOLED/dynamic color/`darkenPages` toggles, privacy & filter list management |
| **Theme & Motion** | `ui/theme/Theme.kt`, `Palette.kt`, `Dimens.kt`, `Type.kt`; `ui/shapes/ExpressiveShapes.kt`; `ui/animation/Entrance.kt`, `Haptics.kt`, `Motion.kt`; `ui/components/Basics.kt`, `ScreenScaffold.kt`, `ShortcutStyle.kt`, `SiteIcon.kt` | 6 seeded palettes (`TEAL`, `CORAL`, `INDIGO`, `SAGE`, `AMBER`, `SLATE`) + `toAmoled()` + `FrostHeaderColors`; `EddyPolygons`/`MorphShape`; haptic feedback & reduced-motion support |
| **Sakuga VFX Engine** | `ui/sakuga/SakugaAnimationController.kt`, `SakugaMotionController.kt`, `SakugaParticleRenderer.kt`, `SakugaImpactEffect.kt`, `SakugaButton.kt`, `SakugaControls.kt`, `SakugaSurfaces.kt`, `SakugaElasticComponents.kt` | View (`SpringAnimation`, `ValueAnimator`) & Compose motion presets (`SOFT`, `PREMIUM`, `PLAYFUL`, `SNAPPY`), particle bursts, press squash modifiers, animated `SakugaSwitch`/`SakugaSlider`/`SakugaAlertDialog`/`SakugaModalBottomSheet` |

---

## 5. Browser Feature Inventory

| Feature Category | Status | Evidence & Implementation Details |
| :--- | :--- | :--- |
| **A. WebView & Rendering** | | |
| WebView page loading & navigation | **IMPLEMENTED** | `WebViewFactory`, `EddyWebView`, `EddyWebViewClient` (`shouldOverrideUrlLoading`, `onPageStarted/CommitVisible/Finished`), `BrowserViewModel` (`navigate`, `goBack`, `goForward`, `reload`, `stop`) |
| Page metadata (URL, title, progress, security, favicon) | **IMPLEMENTED** | Reactive `BrowserTab` states (`url`, `title`, `progress`, `isLoading`, `security`, `favicon`, `canGoBack`, `canGoForward`) updated by `EddyChromeClient` & `EddyWebViewClient` |
| Desktop / Mobile User-Agent & Client Hints | **IMPLEMENTED** | `UserAgents.mobile/desktop/asChrome()`, global (`UserAgentMode`) and per-site (`SiteFeature.DESKTOP`) |
| HTTPS-Only upgrade & fallback warning | **IMPLEMENTED** | `WebViewFactory.upgrade()` + `EddyWebViewClient` loop detection (`ErrorKind.INSECURE`) + SSL error prompt/override |
| Cookie & Storage management | **IMPLEMENTED** | `CookieMode` (`ALLOW_ALL`, `BLOCK_THIRD_PARTY`, `BLOCK_ALL`), per-site `THIRD_PARTY_COOKIES`, `clearSiteData()`, isolated incognito `ProfileStore` (`Profile`) on supported WebViews |
| View Page Source & DevTools | **IMPLEMENTED** | `BrowserViewModel.viewPageSource()` -> `SourceScreen` (`HtmlPretty` + `LogEntry`); `DevTools.show/hide()` via bundled `eruda.min.js` |
| **B. Tab Management** | | |
| Multi-tab & Incognito tabs | **IMPLEMENTED** | `TabManager` (separate normal + incognito lists), `Tabs` persistence (`tabs.json` + `tabstate/*.bin`), Incognito excluded from disk & `ProfileStore` isolated |
| Create / Close / Reorder / Reopen Closed | **IMPLEMENTED** | `TabManager.add`, `select`, `close`, `closeAll`, `restoreClosed` (up to 10 closed normal tabs), `move`, `CloseTabBehavior` (`ADJACENT`/`RECENT`), LRU eviction (`MAX_LIVE_WEBVIEWS = 4`) |
| Tab thumbnails | **IMPLEMENTED** | `TabThumbnailManager` captures 480px JPEG previews from live WebView canvas |
| **C. History & Bookmarks** | | |
| History tracking & 90-day pruning | **IMPLEMENTED** | `HistoryManager.record()` skips incognito & error pages; `search()`, `suggest()`, `frequent()`, `recentSites()`, `remove()`, `clearRange()`, `clear()`, `prune()` |
| Bookmarks & hierarchical folders + HTML import/export | **IMPLEMENTED** | `BookmarkManager` backed by `Bookmark` + `BookmarkFolder` Room tables; Netscape HTML export/import |
| **D. Downloads & Documents** | | |
| HTTP(S) resumable downloads & foreground notification | **IMPLEMENTED** | `DownloadEngine` (range resume, `.part` staging, `MediaStore` export, mime/disposition naming, risky executable confirmation) + `DownloadService` (`dataSync`) |
| `blob:` & `data:` URI downloads | **IMPLEMENTED** | `BlobDownloader` (`EddyBlob` chunked `WebMessageListener`) + `DownloadEngine.importFile()` |
| File Uploads (`<input type="file">`) | **IMPLEMENTED** | `EddyChromeClient.onShowFileChooser` -> `MainActivity` `StartActivityForResult` |
| Built-in PDF Viewer & Print | **IMPLEMENTED** | `PdfScreen` via Android `PdfRenderer`; `BrowserViewModel.printPage()` via `PrintManager` |
| **E. Privacy, Security & Passwords** | | |
| Ad & Tracker blocking (Network + Cosmetic) | **IMPLEMENTED** | `ContentBlocker` + `NetworkRule` + `EddyWebViewClient.shouldInterceptRequest` + `WebViewFactory.applyCosmetic` (`COSMETIC_SCRIPT`) + `FilterUpdateService` |
| Encrypted Password Manager & Autofill | **IMPLEMENTED** | `PasswordVault` (Android Keystore AES-256-GCM) + `PasswordManager` + `AutofillScript` + `BiometricPrompt` gate + CSV import/export + 60s clipboard clear |
| Per-site permissions & DNT/GPC | **IMPLEMENTED** | `SitePermissions` (Camera, Mic, Location, JS, Popups, 3P Cookies, Desktop, Content Blocking, Text Zoom); `DNT_SCRIPT` setting `navigator.doNotTrack` & `navigator.globalPrivacyControl` |
| Clear on Exit | **IMPLEMENTED** | `ExitCleanupService` + cold-start `ExitCleanup` marker fallback |
| **F. Page Intelligence & Utilities** | | |
| Reader Mode & Offline Article Library | **IMPLEMENTED** | `ReaderMode` (`readability.js`) + `ReaderTheme` (Light/Dark CSS vars) + `SavedArticleRepository` (Room offline text + images) |
| Search Engines & Suggestions | **IMPLEMENTED** | 5 built-in engines (`DuckDuckGo`, `Google`, `Brave`, `Bing`, `Startpage`) + custom engines (`%s`), `SuggestionSource` |
| Find-in-Page | **IMPLEMENTED** | `WebView.findAllAsync` + `FindBar` (`startFind`, `findNext`, `closeFind`) |
| Read Aloud (TTS) & Voice Search | **IMPLEMENTED** | `ReadAloud` (`TextToSpeech`, sentence chunking) + `VoiceInputButton` (`SpeechRecognizer`) |
| Page Translation | **PARTIALLY IMPLEMENTED** | `Translate` routes URL through `translate.google.com` web proxy (no local/API DOM translator; fails on logged-in/private pages) |
| Share URL & Launcher Shortcuts | **IMPLEMENTED** | `BrowserViewModel.share()`, `addShortcut()`, `addToLauncher()` (`ShortcutManagerCompat`) |
| **G. Automation & Programmatic Control** | | |
| DOM Inspection / Element Automation | **NOT IMPLEMENTED** | Only human-facing `eruda.min.js` injection exists; **no structured DOM snapshot, link extraction, form/click automation, or programmatic JS eval bridge exists yet** |

---

## 6. WebView Dark Mode Audit

| Mechanism | Source Location | How It Works Today | Limitations / Gaps |
| :--- | :--- | :--- | :--- |
| **1. App Chrome Theme** | `Theme.kt:38-72`, `Models.kt:12`, `Palette.kt:33-142` | `Settings.themeMode` (`SYSTEM`, `LIGHT`, `DARK`) + `Settings.amoled` (`toAmoled()`) + `dynamicColor` (API 31+) + 6 seed palettes. Incognito forces dark `Palette.SLATE`. | Controls Compose UI & `FrostHeaderColors` only — does **not** style website DOM directly. |
| **2. WebView Surface Backdrop (Anti-Flash)** | `WebViewFactory.kt:96`, `EddyWebView.kt:44`, `BrowserScreen.kt`, `MainActivity.kt` | `BrowserHost.pageBackground` passes `MaterialTheme.colorScheme.background` (`0xFF080B0D` default dark / `#000000` AMOLED) to `EddyWebView.applyBackgroundColor()` and `MainActivity` window decor. Prevents white flashes during initial navigation/unpainted frames without touching site appearance. | Surface paint only; once HTML paints `<body>`, site colors dominate. |
| **3. WebView Algorithmic Darkening** | `WebViewFactory.kt:175-178`, `AppearanceSettings.kt:121`, `Models.kt:41` | `Settings.darkenPages: Boolean` (`darken_pages` in `SettingsStore`) calls:<br>`WebSettingsCompat.setAlgorithmicDarkeningAllowed(view.settings, s.darkenPages)` inside `applyGlobal(view, s)`. | 1. **OS Night Mode Coupling**: As noted in `WebViewFactory.kt:175`, Chromium's `ALGORITHMIC_DARKENING` only activates when Android/WebView's app theme itself resolves to dark — toggling `ThemeMode.DARK` inside the app on a Light OS can behave inconsistently depending on OS `UiMode`.<br>2. **All-or-Nothing**: Global boolean only; **no per-site exclusion/inclusion** in `SiteSettings`/`SiteFeature`.<br>3. **No Custom Palette**: Cannot enforce Eddy's charcoal aesthetic (`#121212` / `#1E1E1E`) or preserve native dark sites selectively without Chromium heuristics altering brand imagery. |
| **4. Reader Mode Theme** | `ReaderMode.kt:74-276`, `ReaderTheme.kt:50-191`, `Models.kt:13,44` | `Settings.readerTheme: ReaderThemeMode` (`DARK`, `LIGHT`) switches CSS custom properties (`--er-bg: #0E1113`, `--er-surface: #171B1E`, `--er-text: #EDF1F4`, `--er-muted: #9BA5AE`, `--er-accent`, etc.) and updates `color-scheme` metadata live via `ReaderMode.applyTheme(view, theme, reducedMotion)` without reloading. | Works **only when Reader Mode is active** (rewritten document), not on arbitrary live websites. |

---

## 7. Custom CSS Injection Analysis

### A. Existing Injection Mechanisms in the Codebase

1. **Document-Start Cosmetic & Script Injection (`WebViewFactory.kt:200-239, 326-410`)**:
   - Guarded by `WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)`.
   - Registers 6 document-start `ScriptHandler` entries on `EddyWebView` (`setOf("*")` origins):
     - `YOUTUBE_SCRIPT`, `SPEED_SCRIPT`, `DNT_SCRIPT`, `DESKTOP_VIEWPORT_SCRIPT`, `AutofillScript.SOURCE`, `ClipboardBridge.SOURCE`, plus dynamic `COSMETIC_SCRIPT` (ad/banner CSS hiding).
   - **`COSMETIC_SCRIPT` (`WebViewFactory.kt:381-390`)**: Checks `if(document.getElementById('eddy-cosmetic'))return;`, creates `<style id="eddy-cosmetic">`, sets `s.textContent = COSMETIC_CSS`, and appends to `(document.head || document.documentElement)` (also sets up `addEventListener('DOMContentLoaded', ...)` fallback).
   - Clean teardown: stores `ScriptHandler` inside `cosmeticScripts: WeakHashMap<WebView, ScriptHandler>` and calls `handler.remove()` when disabled.
2. **Post-Load Live DOM/CSS Manipulation (`ReaderMode.kt:74-184` & `DevTools.kt:27-35`)**:
   - `ReaderMode.applyTheme()` uses `WebView.evaluateJavascript(buildApplyThemeJs(...))` to mutate `:root` CSS custom properties (`root.style.setProperty(...)`), `data-eddy-theme`, `root.style.colorScheme`, and `<meta name="color-scheme">` instantaneously with CSS transitions (`0.22s`).

### B. Technical Analysis for Arbitrary Webpage CSS Dark Mode Injection

| Dimension | Technical Assessment for Eddy Browser |
| :--- | :--- |
| **FOUC Prevention** | Document-start injection via `WebViewCompat.addDocumentStartJavaScript(view, script, setOf("*"))` runs before HTML parsing begins (`document.head` may not exist yet). Setting `document.documentElement.dataset.eddyDark = "1"` + injecting `<style id="eddy-dark-css">` onto `document.documentElement` immediately (paired with `EddyWebView.applyBackgroundColor` matching `#121212`) eliminates white pre-paint flashes. |
| **CSS Specificity & Overrides** | Websites frequently use inline `style="background: #fff"`, `!important` utility classes (Tailwind/Bootstrap), or CSS custom properties (`--bg-color`, `--surface`). High-specificity selectors (`:root`, `html, body`) + targeted `!important` overrides on backgrounds (`#121212` / `#1E1E1E`) and foreground text (`#E4E6EB`), while scoping `img, video, canvas, svg` cleanly, are essential. **Avoid destructive `filter: invert(1)`** as it breaks photos, brand colors, shadows, and causes heavy GPU repaints. |
| **Already-Dark Site Detection** | Blindly forcing CSS onto native dark sites (GitHub Dark, YouTube Dark, Discord) degrades contrast. Before applying rules (or via a fast `requestAnimationFrame` / `DOMContentLoaded` check), inspect `getComputedStyle(document.documentElement).colorScheme` and luminance of `getComputedStyle(document.body).backgroundColor` — skip injection if the page already declares/renders a dark surface unless `force` mode is explicitly requested. |
| **Dynamic SPAs & Lazy Nodes** | Modern SPAs dynamically inject modals, portals, and style tags. A lightweight `MutationObserver` on `document.head`/`document.body` ensures `<style id="eddy-dark-css">` stays last in `<head>` (maintaining cascade precedence) without polling. |
| **Shadow DOM** | Document `<style>` tags **do not pierce Shadow DOM boundaries**. Open Shadow DOM (`el.shadowRoot`) can be patched by hooking `Element.prototype.attachShadow` at document start and adopting a shared `CSSStyleSheet` (`shadowRoot.adoptedStyleSheets`); **closed Shadow DOM (`mode: 'closed'`) cannot be styled via page JS**. |
| **Iframes** | `evaluateJavascript()` on `EddyWebView` executes **only in the top-level main frame** and cannot reach cross-origin `<iframe>` documents. Conversely, `WebViewCompat.addDocumentStartJavaScript(..., setOf("*"))` runs inside **both main frames and child frames** automatically (provided the origin scope permits it), allowing coherent dark styling across embedded frames — though host-based per-site exclusions must inspect `window.top` or be checked carefully to avoid breaking cross-origin embeds. |
| **Security & Page Isolation** | Do **not** inject `<style>` via string concatenation of untrusted page content, nor expose privileged Android `@JavascriptInterface` APIs when injecting CSS. Keep `eddy-dark-css` strictly declarative CSS + a `Boolean` toggle, and **skip** `chrome-error://`, `file://`, `view-source://`, and active Reader Mode documents (`window.__eddyReader === true`, handled natively by `ReaderThemeManager`). |
| **Live Toggle Without Reload** | Following the `ReaderTheme` pattern, store a dedicated `darkScripts: WeakHashMap<WebView, ScriptHandler>` for future navigations AND run `evaluateJavascript()` on active `BrowserTab.webView` instances to toggle/remove `#eddy-dark-css` immediately without losing scroll position or form state. |

---

## 8. Database & Data Models

### A. Room Database (`EddyDatabase`, `"eddy.db"`, Schema Version `5`, AutoMigrations `1->5`)

| Table Name | Entity Class (`data/database/Entities.kt`) | Columns & Keys / Indices | DAO (`Daos.kt`) | MCP Exposure Safety |
| :--- | :--- | :--- | :--- | :--- |
| `history` | `HistoryEntry` | `id` (PK), `url` (indexed), `title`, `host`, `visitedAt` (indexed) | `HistoryDao` | **SAFE (Read / Controlled Clear)** — Exclude any incognito state (not stored anyway) |
| `bookmarks` | `Bookmark` | `id` (PK), `url` (unique index), `title`, `folderId` (indexed), `createdAt` | `BookmarkDao` | **SAFE (Read / Write)** |
| `bookmark_folders` | `BookmarkFolder` | `id` (PK), `name`, `createdAt` | `BookmarkDao` | **SAFE (Read / Write)** |
| `downloads` | `DownloadEntry` | `id` (PK), `url`, `finalUrl`, `fileName`, `mimeType`, `totalBytes`, `downloadedBytes`, `status` (indexed `DownloadStatus`), `localUri`, `incognito`, `error`, `createdAt`, `updatedAt` | `DownloadDao` | **SAFE WITH FILTERING** — Mask/exclude `incognito == true` rows by default |
| `site_settings` | `SiteSettings` | `host` (PK), `location`, `camera`, `microphone`, `javascript`, `popups`, `thirdPartyCookies`, `desktop`, `contentBlocking`, `textZoom` *(Note: **no `darkMode` or `customCss` column exists yet** -> requires Room v6 migration)* | `SiteSettingsDao` | **SAFE (Read / Write)** — Gate sensitive permission overrides (`javascript`, `location`, `camera`, `mic`) |
| `logins` | `SavedLogin` | `id` (PK), `site` (indexed), `origin`, `username`, `password` (AES-256-GCM `"base64(iv):base64(ct)"`), `updatedAt` | `LoginDao` | **STRICTLY FORBIDDEN** — Never expose usernames or encrypted/decrypted passwords over MCP |
| `login_blocklist` | `LoginBlock` | `site` (PK) | `LoginDao` | **STRICTLY FORBIDDEN** — Privacy metadata |
| `saved_articles` | `SavedArticle` | `id` (PK), `url` (unique index), `title`, `siteName`, `byline`, `readMinutes`, `textContent`, `contentHtml`, `imageCount`, `savedAt` | `SavedArticleDao` | **SAFE (Read / Write)** — Ideal source for offline article summarization |
| `saved_article_images` | `SavedArticleImage` | `id` (PK), `articleId` (FK -> `saved_articles.id` ON DELETE CASCADE, indexed), `originalUrl`, `mimeType`, `imageData` (`BLOB`) | `SavedArticleDao` | **SAFE (Read metadata / Base64 if requested)** |

### B. Preferences & File-Backed Persistence

| Store | Implementation | Schema / Contents | Encryption & Retention | MCP Safety |
| :--- | :--- | :--- | :--- | :--- |
| **Settings** | `SettingsStore` (`DataStore<Preferences>` `"eddy_settings"`) | `Settings` data class (40+ fields: `searchEngineId`, `customSearchEngines`, `searchSuggestions`, `homepageMode`, `homepageUrl`, `restoreTabs`, `switchToNewTabs`, `tabLayout`, `closeTabBehavior`, `themeMode`, `amoled`, `dynamicColor`, `palette`, `motion`, `hapticsEnabled`, `textZoom`, `darkenPages`, `readerTheme`, `adBlock`, `trackerProtection`, `filterLists`, `doNotTrack`, `httpsOnly`, `alwaysIncognito`, `clearOnExit`, `cookieMode`, `javascript`, `savePasswords`, `autofillPasswords`, `userAgentMode`, `customUserAgent`, `webDebugging`, `shortcuts`, `home*`, `onboardingCompleted`, `changelogSeen`) | Unencrypted Preferences DataStore. Note `exportJson()` serializes entire `Settings` object. | **SAFE** (Restrict `savePasswords`/`autofillPasswords`/`webDebugging` mutations) |
| **Open Tabs** | `TabManager` | `filesDir/tabs.json` (metadata + `selectedId`) + `filesDir/tabstate/<id>.bin` (Parcelable `WebView` back/forward history via `AtomicFile`) | Unencrypted private app files; **incognito tabs never written to disk**; wiped if `clearOnExit` triggers | **SAFE (Non-incognito only)** |
| **Thumbnails & Favicons** | `TabThumbnailManager`, `FaviconCache` | `filesDir/thumbs/<tabId>.jpg`, `cacheDir/favicons/<sha256(host)>.png` | Ephemeral previews; incognito thumbnails in RAM LRU only | **SAFE** |
| **WebView Profile & Cookies** | `CookieManager`, `WebStorage`, `ProfileStore` | System Chromium profile `"Default"` (and `"Incognito"` via `ProfileStore` when supported) | Managed by Chromium; wiped via `ExitCleanup` / `clearBrowsingData` | **STRICTLY FORBIDDEN** — Never export cookies/localStorage/tokens to MCP |

---

## 9. MCP Tool Compatibility Matrix

> All tools are grouped into the 9 required domains. Every existing backing symbol was verified in source; gaps are marked `NEW IMPLEMENTATION REQUIRED`.

### 1. Browser Read Tools

| MCP Tool Name | Android Backing Class & Method | Parameters & Types | Response Schema | Thread & Approval Policy |
| :--- | :--- | :--- | :--- | :--- |
| `get_browser_status` | `BrowserViewModel` (`screen`, `tabs.tabs`, `tabs.selected`, `fullscreen`) + `TabManager` (`closedCount`) | `{}` | `{ screen: string, activeTabId: string?, tabCount: int, incognitoTabCount: int, closedTabsAvailable: int, themeMode: string, darkenPages: bool }` | **Main / StateFlow** · Scope: `browser.read` · **Auto-allow** |
| `get_current_page` | `TabManager.selected` (`BrowserTab`: `id`, `url`, `title`, `host`, `isLoading`, `progress`, `canGoBack`, `canGoForward`, `security`, `readerActive`, `readerAvailable`, `desktopActive`, `blockedCount`, `error`) | `{ includeIncognito: bool = false }` | `{ tabId: string, url: string, title: string, host: string, isLoading: bool, progress: int, canGoBack: bool, canGoForward: bool, security: string, readerAvailable: bool, readerActive: bool, error: string? }` | **Main** · Scope: `browser.read` · **Auto-allow** (deny/mask incognito unless enabled in user settings) |
| `list_saved_articles` / `get_saved_article` | `SavedArticleRepository` (`articles`, `findByUrl`, `dao.get`) | `{ id?: long, url?: string, includeText: bool = true }` | `{ articles: [{ id, url, title, siteName, byline, readMinutes, savedAt, textContent? }] }` | **IO** · Scope: `browser.read` · **Auto-allow** |

### 2. Browser Navigation Tools

| MCP Tool Name | Android Backing Class & Method | Parameters & Types | Response Schema | Thread & Approval Policy |
| :--- | :--- | :--- | :--- | :--- |
| `navigate_to` | `BrowserViewModel.navigate(tab, input)` (`tabId` lookup via `TabManager` + **NEW:** `awaitLoad` helper listening on `tab.isLoading`) | `{ input: string, tabId?: string, waitForLoad?: bool = false }` | `{ tabId: string, resolvedUrl: string, title: string, status: "started" \| "loaded" \| "error" }` | **Main** · Scope: `browser.navigate` · **Ask on sensitive/external schemes**; block `file://`, `javascript:`, `intent://` from MCP |
| `go_back` | `BrowserViewModel.goBack()` (or `tab.webView?.goBack()` for target `tabId`) | `{ tabId?: string }` | `{ navigated: bool, url: string, canGoBack: bool }` | **Main** · Scope: `browser.navigate` · **Auto-allow** |
| `go_forward` | `BrowserViewModel.goForward()` (or `tab.webView?.goForward()`) | `{ tabId?: string }` | `{ navigated: bool, url: string, canGoForward: bool }` | **Main** · Scope: `browser.navigate` · **Auto-allow** |
| `reload_page` | `BrowserViewModel.reload()` / `BrowserViewModel.stop()` | `{ tabId?: string, action: "reload" \| "stop" = "reload" }` | `{ tabId: string, isLoading: bool }` | **Main** · Scope: `browser.navigate` · **Auto-allow** |
| `find_in_page` | `BrowserViewModel.startFind(query)` / `findNext(forward)` / `closeFind()` (**NEW:** expose `WebView.FindListener` result callback via suspendable wrapper) | `{ query: string, forward?: bool = true, close?: bool = false }` | `{ query: string, activeMatch: int, totalMatches: int }` | **Main** · Scope: `browser.read` · **Auto-allow** |

### 3. Tab Management Tools

| MCP Tool Name | Android Backing Class & Method | Parameters & Types | Response Schema | Thread & Approval Policy |
| :--- | :--- | :--- | :--- | :--- |
| `list_tabs` | `TabManager.normalTabs` (plus `incognitoTabs` only if explicitly permitted) | `{ includeIncognito: bool = false }` | `{ selectedTabId: string?, tabs: [{ id, title, url, host, isHome, isLoading, incognito, desktopActive, readerActive, hasLiveWebView }] }` | **Main** · Scope: `browser.read` · **Auto-allow** |
| `create_tab` | `BrowserViewModel.newTab(incognito, select)` / `BrowserViewModel.openInNewTab(url, incognito, select)` | `{ url?: string, incognito: bool = false, select: bool = true }` | `{ tabId: string, url: string, selected: bool }` | **Main** · Scope: `browser.tabs.write` · **Auto-allow** (validate http/https URL) |
| `select_tab` | `BrowserViewModel.selectTab(id)` / `TabManager.select(id)` | `{ tabId: string }` | `{ selectedTabId: string, url: string, title: string }` | **Main** · Scope: `browser.tabs.write` · **Auto-allow** |
| `close_tab` | `BrowserViewModel.closeTab(id)` / `TabManager.close(id)` | `{ tabId: string }` | `{ closed: bool, nextSelectedTabId: string?, closedStackCount: int }` | **Main** · Scope: `browser.tabs.write` · **Confirm if `close_all`** |
| `restore_closed_tab` | `BrowserViewModel.restoreClosedTab()` / `TabManager.restoreClosed()` | `{}` | `{ restoredTabId: string?, url: string? }` | **Main** · Scope: `browser.tabs.write` · **Auto-allow** |

### 4. History Tools

| MCP Tool Name | Android Backing Class & Method | Parameters & Types | Response Schema | Thread & Approval Policy |
| :--- | :--- | :--- | :--- | :--- |
| `search_history` | `HistoryManager.all()` / `HistoryManager.search(query)` (`.first()` one-shot collection) | `{ query?: string, limit: int = 50 }` | `{ entries: [{ id, url, title, host, visitedAt }] }` | **IO** · Scope: `browser.history.read` · **Requires explicit scope grant** |
| `get_frequent_sites` | `HistoryManager.frequent(limit)` / `HistoryManager.recentSites(limit)` | `{ mode: "frequent" \| "recent" = "frequent", limit: int = 8 }` | `{ sites: [{ host, url, title, visits, lastVisit }] }` | **IO** · Scope: `browser.history.read` · **Requires explicit scope grant** |
| `delete_history` | `HistoryManager.remove(id)` / `clearRange(sinceMs)` / `clear()` | `{ id?: long, sinceMs?: long, clearAll: bool = false }` | `{ deleted: bool }` | **IO** · Scope: `browser.history.write` · **Always require human confirmation for range/all** |

### 5. Bookmark Tools

| MCP Tool Name | Android Backing Class & Method | Parameters & Types | Response Schema | Thread & Approval Policy |
| :--- | :--- | :--- | :--- | :--- |
| `list_bookmarks` | `BookmarkManager.observe()` (`.first()`) / `bookmarksIn(folderId)` / `suggest(query, limit)` | `{ query?: string, folderId?: long }` | `{ folders: [{ id, name, createdAt }], bookmarks: [{ id, url, title, folderId, createdAt }] }` | **IO** · Scope: `browser.bookmarks.read` · **Auto-allow (scoped)** |
| `add_bookmark` | `BookmarkManager.add(url, title, folderId)` | `{ url: string, title: string, folderId: long = 0 }` | `{ created: bool, isBookmarked: bool }` | **IO** · Scope: `browser.bookmarks.write` · **Auto-allow or confirm** |
| `remove_bookmark` | `BookmarkManager.remove(id)` or `BookmarkManager.toggle(url, title)` | `{ id?: long, url?: string }` | `{ removed: bool }` | **IO** · Scope: `browser.bookmarks.write` · **Confirm bulk/folder deletes** |
| `manage_bookmark_folder` | `BookmarkManager.createFolder(name)` / `renameFolder(id, name)` / `deleteFolder(id)` | `{ action: "create"\|"rename"\|"delete", id?: long, name?: string }` | `{ success: bool, folderId?: long }` | **IO** · Scope: `browser.bookmarks.write` · **Confirm folder delete** |

### 6. Download Tools

| MCP Tool Name | Android Backing Class & Method | Parameters & Types | Response Schema | Thread & Approval Policy |
| :--- | :--- | :--- | :--- | :--- |
| `list_downloads` | `DownloadEngine.all()` (`.first()`) filtered `!incognito` | `{ status?: string, limit: int = 50 }` | `{ downloads: [{ id, fileName, url, mimeType, totalBytes, downloadedBytes, status, localUri, createdAt }] }` | **IO** · Scope: `browser.downloads.read` · **Exclude incognito rows** |
| `start_download` | `DownloadEngine.enqueue(...)` (**NEW:** wrapper resolving `DownloadPolicy.referrerFor` & blocking executables unless confirmed) | `{ url: string, userAgent?: string, fileNameHint?: string }` | `{ downloadId: long, fileName: string, status: string }` | **Main/IO** · Scope: `browser.downloads.write` · **Mandatory user confirmation** (strictly confirm `DownloadNames.isExecutable`) |
| `control_download` | `DownloadEngine.pause(id)` / `resume(id)` / `retry(id)` / `cancel(id)` / `remove(id)` | `{ id: long, action: "pause"\|"resume"\|"retry"\|"cancel"\|"remove" }` | `{ id: long, action: string, ok: bool }` | **IO** · Scope: `browser.downloads.write` · **Confirm `cancel`/`remove`** |

### 7. Webpage Information Tools

| MCP Tool Name | Android Backing Class & Method | Parameters & Types | Response Schema | Thread & Approval Policy |
| :--- | :--- | :--- | :--- | :--- |
| `extract_readable_article` | `ReaderMode.extractArticle(context, webView, callback)` (reuses `readability.js`) | `{ tabId?: string }` | `{ url, title, siteName, byline, readMinutes, textContent, contentHtml, imageUrls }` | **Main (`WebView`)** · Scope: `browser.read` · **Require user opt-in per session/site for DOM content** |
| `extract_visible_text` | `ReaderMode.extractReadableText(webView, callback)` (or **NEW:** `document.body.innerText` script) | `{ tabId?: string, maxChars: int = 20000 }` | `{ tabId, url, title, text, truncated: bool }` | **Main (`WebView`)** · Scope: `browser.read` · **Require per-session / per-site approval** |
| `capture_tab_screenshot` | `TabThumbnailManager.capture(tab)` / `peek(tabId)` (**NEW:** high-res PNG/WebP capture method using `webView.draw(canvas)`) | `{ tabId?: string, maxWidth: int = 720 }` | `{ tabId, mimeType: "image/jpeg"\|"image/png", width, height, base64Data }` | **Main (`WebView`)** · Scope: `browser.read` · **Explicit approval** (sensitive visual data; block incognito/passwords screen) |
| `get_page_dom_snapshot` / `query_links` / `run_page_script` | **NEW IMPLEMENTATION REQUIRED** (WebView JS bridge / `evaluateJavascript` suspending wrapper returning JSON: links, headings, forms, interactive elements) | `{ tabId?: string, selector?: string }` | `{ url, title, links: [{text, href}], headings: [], interactive: [] }` | **Main (`WebView`)** · Scope: `browser.read` / `browser.dom.write` · **Arbitrary JS / form submission MUST require explicit human confirmation** |
| `get_page_source` | **NEW:** Refactor existing `BrowserViewModel.viewPageSource()` JS snippet (`document.documentElement.outerHTML`) into a suspendable `BrowserTab` helper | `{ tabId?: string, maxBytes: int = 262144 }` | `{ html: string, truncated: bool }` | **Main (`WebView`)** · Scope: `browser.read` · **Confirm / Block incognito** |

### 8. Browser Settings Tools

| MCP Tool Name | Android Backing Class & Method | Parameters & Types | Response Schema | Thread & Approval Policy |
| :--- | :--- | :--- | :--- | :--- |
| `get_settings` | `SettingsStore.settings.first()` (read-only sanitized projection) | `{}` | `{ searchEngineId, themeMode, amoled, darkenPages, readerTheme, textZoom, adBlock, trackerProtection, httpsOnly, doNotTrack, cookieMode, javascript, userAgentMode, clearOnExit }` | **IO** · Scope: `browser.settings.read` · **Auto-allow** |
| `update_setting` | `SettingsStore.update { ... }` via `BrowserViewModel.launchSettings` | `{ key: string, value: any }` | `{ updated: bool, currentValue: any }` | **IO/Main** · Scope: `browser.settings.write` · **Confirm security-downgrading changes** (disabling `adBlock`, `httpsOnly`, `trackerProtection`, enabling `webDebugging`) |
| `get_site_settings` / `set_site_settings` | `SitePermissions.peek(host)` / `SitePermissions.set(host, feature, value)` | `{ host: string, feature?: SiteFeature, value?: int? }` | `{ host, settings: { ... } }` | **Main/IO** · Scope: `browser.settings.write` · **Confirm permission grants** (`CAMERA`, `MICROPHONE`, `LOCATION`, `JAVASCRIPT`) |

### 9. Dark Mode & Theme Tools

| MCP Tool Name | Android Backing Class & Method | Parameters & Types | Response Schema | Thread & Approval Policy |
| :--- | :--- | :--- | :--- | :--- |
| `set_app_theme` | `BrowserViewModel.launchSettings { it.copy(themeMode = ..., amoled = ..., palette = ...) }` | `{ themeMode?: "SYSTEM"\|"LIGHT"\|"DARK", amoled?: bool, paletteIndex?: int }` | `{ themeMode, amoled, palette }` | **Main** · Scope: `browser.theme.write` · **Auto-allow** |
| `set_algorithmic_darkening` | `BrowserViewModel.launchSettings { it.copy(darkenPages = enabled) }` (triggers `WebViewFactory.applyGlobal` on live tabs) | `{ enabled: bool }` | `{ darkenPages: bool }` | **Main** · Scope: `browser.theme.write` · **Auto-allow** |
| `set_reader_theme` | `BrowserViewModel.setReaderTheme(...)` + `ReaderMode.applyTheme(...)` | `{ mode: "DARK"\|"LIGHT" }` | `{ readerTheme: string, appliedToActiveTab: bool }` | **Main** · Scope: `browser.theme.write` · **Auto-allow** |
| `set_adaptive_css_dark_mode` | **NEW IMPLEMENTATION REQUIRED** (`WebViewFactory` document-start CSS injector + `SiteFeature.DARK_MODE` in `SiteSettings` DB v6 + live `evaluateJavascript` toggle) | `{ scope: "global"\|"site", host?: string, mode: "auto"\|"force_dark"\|"force_light", palette?: "charcoal"\|"amoled" }` | `{ scope, host?, mode, appliedTabs: int }` | **Main (`WebView`)** · Scope: `browser.theme.write` / `browser.css.write` · **Auto-allow preset; confirm arbitrary custom CSS** |
| `inject_custom_css` / `clear_custom_css` | **NEW IMPLEMENTATION REQUIRED** (safe `<style id="eddy-mcp-css">` injector + `ScriptHandler` registry) | `{ tabId?: string, css: string, persistForHost?: string }` | `{ injected: bool, tabId: string }` | **Main (`WebView`)** · Scope: `browser.css.write` · **Explicit confirmation for raw CSS** (CSP/XSS sanitization) |

---

## 10. Android Integration Architecture

### Threading & WebView Confinement
- **Mandatory Main Thread Rule**: Android's `WebView` methods (`loadUrl`, `goBack`, `goForward`, `evaluateJavascript`, `findAllAsync`, `draw(Canvas)`, `settings`, `saveState`, `CookieManager.getInstance()`) throw `RuntimeException` if invoked off `Looper.getMainLooper()`.
- **Async Bridging Pattern**: An MCP tool router running on `Dispatchers.IO` (or a WebSocket coroutine) **must** dispatch all WebView operations via `withContext(Dispatchers.Main.immediate)` and bridge callback APIs (`evaluateJavascript(script) { ... }`, `ReaderMode.extractArticle`, `FindListener`) using `suspendCancellableCoroutine` with strict timeouts (e.g., 5–10s).
- **Tab Hydration Caveat (`TabManager.kt:23, 396-411`)**: `TabManager` enforces `MAX_LIVE_WEBVIEWS = 4` and evicts idle tabs to binary `filesDir/tabstate/<id>.bin`. Reading/acting on an evicted background tab requires calling `TabManager.requireWebView(tab)` on the main thread (which inflates `EddyWebView` and restores Bundle state) before any WebView evaluation succeeds.

### Lifecycle, Services & Process Survival
- **Headless Separation**: Currently `BrowserViewModel` holds `TabManager`, `ReadAloud`, `prompts`, and `uiEffects`, while `MainActivity` collects them in Compose. To support remote/background MCP execution cleanly:
  - Hoist `TabManager` (or a shared **Browser Session Controller**) into `AppContainer` so `BrowserViewModel` and an MCP service operate on the same live state, degrading gracefully to headless DB/Settings operations when no `Activity` is foregrounded.
- **Dedicated Foreground Service (`McpRelayService`)**:
  - Model after `DownloadService` (`foregroundServiceType="dataSync"`) and `MediaService`.
  - Runs only while the user explicitly toggles **"MCP Remote Control Active"** (mandatory ongoing notification showing connected client & one-tap **"Disconnect / Revoke"** action).
- **Intents & Deep Links (`MainActivity.kt` & `AndroidManifest.xml`)**:
  - `MainActivity` uses `launchMode="singleTask"` and handles `ACTION_VIEW` (`http`/`https`, `file`), `ACTION_WEB_SEARCH`, `ACTION_PROCESS_TEXT`, `ACTION_SEND`, plus `SearchWidget` actions.
  - Pairing URIs (scanning an MCP QR pairing token) can route cleanly through `MainActivity.onNewIntent`.

### Local vs. Remote Bridge Decision
- **Local-only IPC (`ContentProvider` / `BoundService` / `127.0.0.1` HTTP)**: Cannot be reached by cloud AI assistants (Claude, Gemini, ChatGPT, web agents) and is blocked/restricted across sandboxed mobile apps without custom client SDKs.
- **Recommended Hybrid Architecture**:
  1. **Internal `McpCommandRouter`** living in Kotlin inside the app, talking directly to `AppContainer` + shared `TabManager` / `BrowserViewModel`.
  2. **Pluggable Transport Adapters**:
     - *Optional Local Debug Transport* (`127.0.0.1` Streamable HTTP/SSE bound only in debug builds or via ADB port-forwarding).
     - *Primary Production Transport*: **Outbound WSS (WebSocket over TLS)** from `McpRelayService` to a user-owned/trusted **Cloud MCP Relay** exposing standard **MCP Streamable HTTP / SSE** to external AI clients.

---

## 11. Security Audit

### A. `AndroidManifest.xml` & Surface Review

| Surface | Current Configuration | Security Finding & Risk |
| :--- | :--- | :--- |
| **`android:usesCleartextTraffic="true"`** | `AndroidManifest.xml:46` | Required by browser to open plain `http://` pages when HTTPS-Only is off, **but creates risk if an MCP transport accidentally uses `http://`/`ws://`**. MCP transport must enforce `https://` / `wss://` via strict URL validation / `network_security_config`. |
| **`android:allowBackup="false"`** | `AndroidManifest.xml:39` | Prevents ADB/cloud backup extraction of tabs/history/settings. |
| **`MainActivity` & 11 `<activity-alias>` entries** | `exported="true"`, `launchMode="singleTask"` | Accepts `http`, `https`, `file` (HTML/XHTML), `WEB_SEARCH`, `PROCESS_TEXT`, `SEND`. Intent URI handling in `EddyWebViewClient.kt:87-91` strips `intent.selector = null; intent.component = null` to stop self-targeting intent attacks. |
| **Services (4) & Receivers (2) & FileProvider** | `DownloadService`, `MediaService`, `ExitCleanupService`, `FilterUpdateService`, `SearchWidget`, `ClipboardClearReceiver`, `FileProvider` | **All `exported="false"`** (`FilterUpdateService` protected by `BIND_JOB_SERVICE`). |
| **`REQUEST_INSTALL_PACKAGES`** | `AndroidManifest.xml:17` | High-risk Play permission justified by APK downloads + gated by `Prompt.RiskyDownload`. **MCP must never be allowed to trigger APK installation UI.** |
| **`CAMERA`, `RECORD_AUDIO`, `ACCESS_FINE_LOCATION`** | `AndroidManifest.xml:6-10` | Runtime-gated via `WebPermissionCoordinator`. **MCP must not auto-grant these or inspect permission prompts.** |
| **JS Bridges (`WebMessageListener`)** | `EddyWebView.kt:55-63` | Uses `WebViewCompat.addWebMessageListener(..., setOf("*"), ...)` with `sourceOrigin` checks in `BlobDownloader.kt:80` and `MainActivity`/`ViewModel` (`isMainFrame` check). Avoids legacy `@JavascriptInterface` on untrusted frames (`proguard-rules.pro` confirms none used). |
| **Credential Storage (`PasswordVault.kt`)** | AES-256-GCM in `AndroidKeyStore` (`eddy_login_key`), no `setUserAuthenticationRequired(true)` | Encrypted passwords sit in Room `logins` table, while `site`, `origin`, and `username` are **plaintext**. UI lock (`BiometricPrompt`) is enforced only inside `PasswordsScreen`. **Zero access to `logins` / `PasswordManager` / `PasswordVault` / `AutofillScript` / `CookieManager` must be enforced in the MCP layer.** |
| **URL Scheme Sanitization** | `UrlUtils.isWebUrl()` vs raw `loadUrl()` | Allowing `javascript:`, `file:///data/...`, `content://`, or `intent:` through MCP `navigate_to` would allow arbitrary file/token exfiltration. Enforce `http://` / `https://` strictly on MCP navigation inputs. |

### B. Proposed MCP Permission Scopes & Approval Boundaries

| Scope | Capabilities | Default Policy |
| :--- | :--- | :--- |
| `browser.read` | Active tab URL/title/status, tab list (non-incognito), saved article list | Auto-allow after initial pairing |
| `browser.read.content` | Webpage visible text, Reader extraction, HTML source, screenshots | **Prompt once per session or domain** ("Allow AI to read `example.com`?") |
| `browser.navigate` | `navigate_to` (`http/https` only), `back`, `forward`, `reload`, `find_in_page` | Auto-allow; block non-HTTP(S) schemes |
| `browser.tabs.write` | Create, select, restore, close single tab (`incognito=false` default) | Auto-allow (`closeAll` requires confirmation) |
| `browser.history.read` / `.write` | Search/read history vs delete/clear history | Opt-in scope; **`clearAll` requires explicit Android confirmation dialog** |
| `browser.bookmarks.read` / `.write` | List, add, edit, delete bookmarks & folders | Auto-allow read & add; confirm delete |
| `browser.downloads.read` / `.write` | List downloads vs start/pause/cancel download | **Start/delete download & all executables require human confirmation (`Prompt`)** |
| `browser.settings.read` / `.write` | Read non-sensitive settings; modify theme/zoom/behavior | Allow safe writes; **confirm privacy/security downgrades** |
| `browser.theme.write` / `browser.css.write` | Theme mode, AMOLED, algorithmic darkening, adaptive charcoal CSS presets, custom CSS | Auto-allow safe presets; confirm raw arbitrary CSS |
| **DENIED (Never Scoped)** | Passwords (`logins`), `AutofillScript`, `CookieManager` raw cookies, `WebStorage`, clipboard contents, incognito tabs (unless user explicitly toggles) | **Hard-blocked at MCP router** |

---

## 12. Remote MCP Requirements

1. **Why Localhost Does Not Work for Mobile AI Clients**:
   - External cloud AI assistants (Web/Desktop/Cloud agents) cannot connect to an Android phone's `127.0.0.1` over NAT/carrier CGNAT without port forwarding.
   - Same-device mobile AI apps run in restricted sandboxes, and Android kills idle localhost listening sockets when Eddy leaves the foreground unless pinned by a `ForegroundService`.
2. **Recommended Transport & Relay Topology**:
   - **Device -> Relay**: `Eddy` (`McpRelayService`, foreground service) opens a persistent **outbound WSS (WebSocket over TLS 1.3)** connection with heartbeat pings (`20–30s`) and exponential-backoff reconnection to a lightweight **MCP Cloud Relay** exposing standard **MCP Streamable HTTP / SSE** to external AI clients.
3. **Device Pairing & Identity**:
   - User initiates pairing in Eddy Settings (`Settings -> Advanced -> Remote MCP`) via QR scan or short-lived 6-digit pairing code.
   - Eddy generates an **Android Keystore EC P-256 device keypair** (device ID = SHA-256 fingerprint of public key) and exchanges a signed challenge for short-lived scoped tokens.
   - Pairing UI displays connected client name, granted scopes, and an instant **Kill Switch / Unpair** button.
4. **Protocol & Request Routing**:
   - Relay forwards JSON-RPC 2.0 `tools/list`, `tools/call`, `resources/list`, `resources/read` envelopes tagged with `requestId`, `clientId`, and `deadlineMs` over the WSS tunnel.
   - `McpRelayService` verifies scopes locally on-device (Zero-Trust: **never trust the relay alone** to enforce permissions), dispatches to `McpCommandRouter`, and returns structured JSON-RPC results or `-32xxx` errors.
5. **Multi-Client & Offline Handling**:
   - Include `tabId` preconditions; reject mutations targeting stale `tabId`s cleanly (`TAB_NOT_FOUND`).
   - If the phone enters Doze, loses connectivity, or Eddy's process is stopped, the Relay immediately returns a deterministic `MCP -32001` (`"Eddy Browser on device is offline or paused"`) rather than queuing stale mutating commands indefinitely.

---

## 13. Missing Components (MCP Gap Analysis)

| Requirement | Existing Support | Missing Work | Priority |
| :--- | :--- | :--- | :--- |
| **MCP Protocol Engine & JSON-RPC Schema** | `kotlinx.serialization-json` present | Implement JSON-RPC 2.0 dispatcher, `ToolDefinition` registry, schema validator, and `tools/call` router | **P0 (Critical)** |
| **Remote Transport & Foreground Service** | `DownloadService` / `MediaService` foreground patterns | Build `McpRelayService` (WSS client, heartbeat, ongoing notification, disconnect action) + pairing manager | **P0 (Critical)** |
| **Headless / Activity-Decoupled Browser Control** | `HistoryManager`, `BookmarkManager`, `DownloadEngine`, `SettingsStore` live in `AppContainer` (`TabManager` currently lives in `BrowserViewModel`) | Hoist `TabManager` (or a `BrowserSessionController`) into `AppContainer` so `McpRelayService` can inspect/control tabs even if `MainActivity` is backgrounded/recreated | **P0 (Critical)** |
| **MCP Security & Human Approval Gate** | `Prompt` / `PromptHost` UI modal queue exists | Add `Prompt.McpApproval(tool, host, summary, decision)` + scope settings store + hard blocklist for passwords/cookies/incognito | **P0 (Critical)** |
| **Suspendable WebView DOM / JS Extraction APIs** | `ReaderMode` has `evaluateJavascript` callback pattern; `DevTools` injects JS | Add coroutine wrappers on `EddyWebView` for `evalJson()`, `getVisibleText()`, `getLinks()`, `getForms()`, `captureScreenshotPng()`, `findInPageAsync()` | **P1 (High)** |
| **Non-Destructive Adaptive Charcoal CSS Dark Mode** | `darkenPages` (`ALGORITHMIC_DARKENING`) + `ReaderTheme` CSS custom-property live switcher | Add `WebViewFactory` document-start adaptive charcoal CSS engine, already-dark detector, live toggle JS, + Room DB v6 `SiteSettings.darkMode` column & `SiteFeature.DARK_MODE` | **P1 (High)** |
| **User Pairing & MCP Audit Log UI** | `SettingsScreen` + `SettingsPage` + `SourceScreen` log viewer | Add `SettingsPage.MCP` (pairing QR/code, active client status, scope switches, emergency kill switch, local tool-call audit log) | **P1 (High)** |
| **ProGuard / R8 Hardening** | `proguard-rules.pro` minimal | Add keep rules for `@Serializable` MCP JSON-RPC payloads & WSS client if R8 minify is enabled | **P2 (Medium)** |

---

## 14. Recommended MCP Implementation Strategy

1. **Decouple Session Ownership Without Breaking UI**:
   - Move `TabManager` (and a thin `BrowserController` facade) from `BrowserViewModel` into `AppContainer`, letting `BrowserViewModel` bind to it for Compose UI observation while `McpRelayService` invokes the exact same single-source-of-truth methods.
2. **Enforce On-Device Zero-Trust Policy Before Tool Execution**:
   - Route every `tools/call` through an `McpPolicyGuard` that verifies:
     1. Valid authenticated client & granted scope;
     2. Tab is not incognito (unless explicitly unlocked by user);
     3. URL scheme is `http`/`https` only;
     4. Target is not in restricted screens (`PASSWORDS`, `LoginPrompt`);
     5. Risky/write operations suspend on a `Prompt.McpApproval` dialog when `MainActivity` is foregrounded (or auto-deny if backgrounded & confirmation is required).
3. **Reuse Existing High-Value Subsystems**:
   - Wrap `ReaderMode.extractArticle` / `extractReadableText` directly for `extract_readable_article`.
   - Model the adaptive dark mode CSS engine directly on `WebViewFactory.applyCosmetic` (document-start `ScriptHandler`) + `ReaderMode.applyTheme` (live CSS variable toggling without reload).
4. **Preserve `SettingsStore` Backward Compatibility**:
   - Use nullable/defaulted fields in `Settings` (`@Serializable`) and safe Room `AutoMigration(from = 5, to = 6)` if adding `SiteSettings.darkMode`.

---

## 15. Development Roadmap

| Phase | Scope & Deliverables | Target Files / Modules |
| :--- | :--- | :--- |
| **Phase 1: Core Extraction & Session Decoupling** | Hoist `TabManager` into `AppContainer`; add coroutine `EddyWebView` helpers (`evalJs`, `captureBitmap`, `findInPageAsync`, `waitForLoad`); build `McpToolRegistry` + JSON-RPC 2.0 router + `McpPolicyGuard` (hard-blocking `PasswordManager`, `CookieManager`, incognito). | `AppContainer`, `TabManager`, `BrowserViewModel`, `EddyWebView`, new `mcp/` package |
| **Phase 2: Read, Navigation, Tab & Bookmark Tools + Approval UX** | Implement Categories 1–6 & 8 (`get_current_page`, `navigate_to`, tab CRUD, history, bookmarks, downloads, settings); add `Prompt.McpApproval` in `PromptDialogs` and `SettingsPage.MCP` controls. | `mcp/tools/`, `Prompts.kt`, `PromptDialogs.kt`, `SettingsPage.kt` |
| **Phase 3: Webpage Intelligence & Adaptive Charcoal Dark Mode** | Expose `ReaderMode.extractArticle`, DOM snapshot/link extraction, screenshots; implement non-destructive adaptive charcoal CSS injector (`WebViewFactory` + `SiteSettings` v6 `darkMode` + Category 9 dark-mode/CSS tools). | `ReaderMode.kt`, `WebViewFactory.kt`, `Entities.kt`, `SitePermissions.kt`, `AppearanceSettings.kt`, `SiteInfoSheet.kt` |
| **Phase 4: Remote WSS Relay, Pairing & Hardening** | Build `McpRelayService` (foreground `dataSync` notification + TLS WebSocket client), Keystore EC identity & QR pairing flow, Robolectric unit tests for `McpPolicyGuard` / `NetworkRule` / URL filters / JSON-RPC routing, and battery/Doze reconnection polish. | `mcp/transport/`, `AndroidManifest.xml`, `EddyApp.kt`, `app/src/test/` |

---

## 16. Technical Risks & Limitations

1. **Background Tab WebView Eviction (`TabManager.kt:23`)**: `MAX_LIVE_WEBVIEWS = 4` destroys WebView instances on inactive tabs (saving only binary navigation state to `tabstate/*.bin`). Inspecting DOM, taking a screenshot, or running Reader extraction on an inactive tab forces `requireWebView(tab)` — which recreates an `EddyWebView` and reloads/restores the page, causing latency, memory spikes, or missing transient JS state.
2. **Android 14/15+ Background Execution & Doze**: Long-lived idle WebSockets are killed by App Standby / Doze unless an explicit user-initiated `ForegroundService` (`McpRelayService` with visible notification) is running. If Eddy is swiped from Recents with `clearOnExit = true`, `ExitCleanupService` destroys tabs, cookies, cache, and history (`ExitCleanup.kt:61-73`).
3. **WebView Single-Thread & Cross-Origin / Shadow DOM Limits**:
   - `evaluateJavascript` executes only on the main frame (cannot read cross-origin `<iframe>` DOMs).
   - Closed Shadow DOM (`attachShadow({ mode: 'closed' })`) cannot be reached by injected CSS/JS.
   - DRM-protected (`RESOURCE_PROTECTED_MEDIA_ID`), strict-CSP, or anti-bot sites may reject automated interaction or require interactive login.
4. **Translation & Google Proxy Limits (`Translate.kt`)**: Built-in translation redirects to `translate.google.com`, which fails on authenticated pages or localhost URLs; MCP clients with LLM capabilities can instead translate extracted text from `extract_readable_article` directly.
5. **Dual Theme Interaction**: Enabling both Chromium `ALGORITHMIC_DARKENING` (`darkenPages = true`) and custom CSS dark mode simultaneously would double-darken or corrupt colors. Mutual exclusion (`if customCssDarkMode active -> disable algorithmic darkening for that WebView`) must be enforced in `WebViewFactory.prepare()`.
6. **Memory Constraints on `capture_tab_screenshot`**: Drawing full-resolution WebViews into `ARGB_8888` bitmaps on high-DPI devices can cause `OutOfMemoryError`; captures should clamp width (e.g., <= 720px–1080px, mirroring `TabThumbnailManager`) and Base64-encode off the main thread.
