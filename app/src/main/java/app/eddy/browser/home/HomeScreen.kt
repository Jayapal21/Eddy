package app.eddy.browser.home

import app.eddy.browser.R
import app.eddy.browser.util.str
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.eddy.browser.browser.BrowserViewModel
import app.eddy.browser.browser.Screen
import app.eddy.browser.browser.SearchEngineIcon
import app.eddy.browser.browser.Suggestion
import app.eddy.browser.browser.SuggestionRow
import app.eddy.browser.browser.TabCountButton
import app.eddy.browser.browser.VoiceInputButton
import app.eddy.browser.browser.rememberVoiceState
import app.eddy.browser.data.database.SiteVisit
import app.eddy.browser.data.models.HomeDensity
import app.eddy.browser.data.models.Settings
import app.eddy.browser.data.models.Shortcut
import app.eddy.browser.settings.SettingsPage
import app.eddy.browser.ui.animation.entrance
import app.eddy.browser.ui.components.EddyIconButton
import app.eddy.browser.ui.components.ShortcutIcons
import app.eddy.browser.ui.components.SiteIcon
import app.eddy.browser.ui.sakuga.SakugaCard
import app.eddy.browser.ui.sakuga.SakugaEffectType
import app.eddy.browser.ui.sakuga.SakugaPhysics
import app.eddy.browser.ui.sakuga.rememberSakugaColors
import app.eddy.browser.ui.sakuga.rememberSakugaController
import app.eddy.browser.ui.sakuga.sakugaOverlay
import app.eddy.browser.ui.sakuga.sakugaPressSquash
import app.eddy.browser.ui.theme.Dimens
import app.eddy.browser.ui.theme.LocalReducedMotion
import app.eddy.browser.ui.theme.UrlTextStyle
import app.eddy.browser.util.UrlUtils
import app.eddy.browser.browser.voiceTrace
import kotlinx.coroutines.flow.flowOf
import java.util.UUID

/** New-tab page: clean adaptive search capsule, integrated shortcuts, and visited history. */
@Composable
fun HomeScreen(
    vm: BrowserViewModel,
    settings: Settings,
    incognito: Boolean,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<Shortcut?>(null) }
    val compact = settings.homeDensity == HomeDensity.COMPACT
    val reduced = LocalReducedMotion.current
    val searching = vm.editing
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()

    val siteStatsFlow = remember(vm, incognito, settings.homeShowShortcuts) {
        if (!incognito && settings.homeShowShortcuts) vm.history.siteStats(150) else flowOf(emptyList())
    }
    val siteStats by siteStatsFlow.collectAsStateWithLifecycle(emptyList())
    val recommendedShortcuts = remember(siteStats, settings.shortcuts) {
        WebsiteRecommender.recommend(
            historySites = siteStats,
            excludedHosts = settings.shortcuts.map { UrlUtils.displayHost(it.url).ifEmpty { it.url } }.toSet(),
        ).mapIndexed { index, site ->
            val styleIndex = settings.shortcuts.size + index
            Shortcut(
                id = "recommended_${site.host}",
                title = site.title,
                url = site.url,
                shape = styleIndex,
                color = styleIndex,
                icon = ShortcutIcons.AUTO,
            )
        }
    }

    val animDuration = if (reduced) 0 else 280
    val lowerAlpha by animateFloatAsState(
        if (searching) 0.54f else 1f,
        tween(animDuration, easing = FastOutSlowInEasing),
        label = "lowerAlpha",
    )
    val lowerShift by animateDpAsState(
        if (searching) 6.dp else 0.dp,
        tween(animDuration, easing = FastOutSlowInEasing),
        label = "lowerShift",
    )
    val topSpacer by animateDpAsState(
        if (searching) 56.dp else if (compact) 52.dp else 62.dp,
        tween(animDuration, easing = FastOutSlowInEasing),
        label = "topSpacer",
    )

    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      Box(
          Modifier.fillMaxSize()
              .then(
                  if (searching) {
                      Modifier.clickable(
                          interactionSource = remember { MutableInteractionSource() },
                          indication = null,
                          onClickLabel = str(R.string.close_address_bar),
                          onClick = vm::stopEditing,
                      )
                  } else {
                      Modifier
                  },
              ),
      ) {
        if (settings.homeShowBackground) AmbientShapes()
        BoxWithConstraints(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.TopCenter) {
            val columns = settings.homeColumns.takeIf { it > 0 } ?: if (maxWidth >= 600.dp) 6 else 4
            val maxSuggestionsHeight = (maxHeight - topSpacer - 92.dp).coerceIn(140.dp, 360.dp)
            Column(
                Modifier.widthIn(max = Dimens.screenMaxWidth).fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.gutterLarge),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(topSpacer))
                if (settings.homeShowSearch) {
                    HomeSearchBar(
                        settings = settings,
                        incognito = incognito,
                        expanded = searching,
                        queryText = vm.omniboxText,
                        suggestions = suggestions,
                        maxSuggestionsHeight = maxSuggestionsHeight,
                        dictate = vm.dictateOnOpen,
                        onExpand = vm::startEditing,
                        onTextChange = vm::onOmniboxText,
                        onSubmit = vm::submit,
                        onPickSuggestion = { vm.submit(it.text) },
                        onPickEngine = { id -> vm.launchSettings { it.copy(searchEngineId = id) } },
                        onDictateStarted = vm::dictateStarted,
                        modifier = Modifier.entrance(0),
                    )
                    Spacer(Modifier.height(if (compact) 24.dp else 32.dp))
                }

                Column(
                    Modifier.fillMaxWidth().graphicsLayer {
                        alpha = lowerAlpha
                        translationY = lowerShift.toPx()
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (!incognito) {
                        if (settings.homeShowShortcuts) ShortcutGrid(
                            shortcuts = settings.shortcuts,
                            style = settings.shortcutStyle,
                            columns = columns,
                            tileSize = if (compact) Dimens.shortcutSizeCompact else Dimens.shortcutSize,
                            onOpen = { vm.navigate(it.url) },
                            onEdit = { editing = it },
                            onRemove = { s -> vm.launchSettings { it.copy(shortcuts = it.shortcuts.filterNot { x -> x.id == s.id }) } },
                            onReorder = { list -> vm.launchSettings { it.copy(shortcuts = list) } },
                            onAdd = { editing = Shortcut(UUID.randomUUID().toString(), "", "https://", settings.shortcuts.size, settings.shortcuts.size) },
                            onHide = {
                                vm.launchSettings { it.copy(homeShowShortcuts = false) }
                                vm.snackbar(str(R.string.shortcuts_hidden_show_them_again_in_sett), str(R.string.undo)) {
                                    vm.launchSettings { it.copy(homeShowShortcuts = true) }
                                }
                            },
                            modifier = Modifier.entrance(1),
                            recommended = recommendedShortcuts,
                            onOpenNewTab = { vm.openInNewTab(it.url) },
                            onPinRecommended = { rec ->
                                val recHost = UrlUtils.displayHost(rec.url)
                                vm.launchSettings { s ->
                                    if (s.shortcuts.any { UrlUtils.displayHost(it.url) == recHost }) s
                                    else s.copy(
                                        shortcuts = s.shortcuts + Shortcut(
                                            id = UUID.randomUUID().toString(),
                                            title = rec.title,
                                            url = rec.url,
                                            shape = s.shortcuts.size,
                                            color = s.shortcuts.size,
                                        ),
                                    )
                                }
                                vm.snackbar(str(R.string.added_to_home_shortcuts))
                            },
                        )
                        Spacer(Modifier.height(32.dp))
                        VisitedSections(vm, settings.homeShowFrequent, settings.homeShowRecent)
                    } else {
                        IncognitoNote(vm.incognitoIsolated, Modifier.entrance(1))
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
        Row(
            Modifier.align(Alignment.TopEnd).padding(contentPadding).padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabCountButton(
                count = vm.tabs.tabs.count { it.incognito == incognito },
                incognito = incognito,
                onClick = vm::openTabSwitcher,
                modifier = Modifier.testTag("home_tabs_button"),
            )
            if (!incognito) {
                TopActionIcon(
                    icon = Icons.Outlined.Tune,
                    contentDescription = str(R.string.customize),
                    pressRotation = 45f,
                    onClick = {
                        vm.settingsStack.clear()
                        vm.settingsStack.addAll(listOf(SettingsPage.APPEARANCE, SettingsPage.START_PAGE))
                        vm.screen = Screen.SETTINGS
                    },
                    modifier = Modifier.testTag("home_customize_button"),
                )
            }
            TopActionIcon(
                icon = Icons.Outlined.MoreVert,
                contentDescription = str(R.string.menu),
                pressRotation = 0f,
                onClick = { vm.menuVisible = true },
                modifier = Modifier.testTag("home_menu_button"),
            )
        }
      }
    }

    editing?.let { shortcut ->
        ShortcutEditor(
            initial = shortcut,
            isNew = settings.shortcuts.none { it.id == shortcut.id },
            onDismiss = { editing = null },
            onSave = { saved ->
                vm.launchSettings { s ->
                    val exists = s.shortcuts.any { it.id == saved.id }
                    s.copy(shortcuts = if (exists) s.shortcuts.map { if (it.id == saved.id) saved else it } else s.shortcuts + saved)
                }
                editing = null
            },
        )
    }
}

@Composable
private fun TopActionIcon(
    icon: ImageVector,
    contentDescription: String,
    pressRotation: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    val scheme = MaterialTheme.colorScheme
    val sakugaColors = rememberSakugaColors()
    val controller = rememberSakugaController()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val rotation by animateFloatAsState(
        targetValue = if (pressed) pressRotation else 0f,
        animationSpec = SakugaPhysics.overshootSpring(reduced),
        label = "topActionRotation",
    )
    val wash by animateColorAsState(
        targetValue = if (pressed) scheme.surfaceContainerHighest.copy(alpha = 0.72f) else Color.Transparent,
        animationSpec = tween(if (reduced) 0 else 160, easing = FastOutSlowInEasing),
        label = "topActionWash",
    )

    Box(
        modifier
            .size(Dimens.touchTarget)
            .sakugaPressSquash(
                interactionSource = source,
                pressX = SakugaPhysics.PRESS_ICON_SCALE_X,
                pressY = SakugaPhysics.PRESS_ICON_SCALE_Y,
            )
            .sakugaOverlay(controller)
            .clip(CircleShape)
            .background(wash)
            .clickable(
                interactionSource = source,
                indication = androidx.compose.material3.ripple(bounded = true),
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = {
                    controller.emitCenter(SakugaEffectType.SUBTLE_POP, sakugaColors, intensity = 0.8f)
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier
                .size(21.dp)
                .graphicsLayer { rotationZ = rotation },
            tint = scheme.onSurface.copy(alpha = 0.88f),
        )
    }
}

/**
 * Single unified adaptive search surface: handles idle capsule, elevated focused state,
 * live single-field input with the active search engine favicon, and internal recommendations.
 */
@Composable
private fun HomeSearchBar(
    settings: Settings,
    incognito: Boolean,
    expanded: Boolean,
    queryText: String,
    suggestions: List<Suggestion>,
    maxSuggestionsHeight: Dp,
    dictate: Boolean,
    onExpand: () -> Unit,
    onTextChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onPickSuggestion: (Suggestion) -> Unit,
    onPickEngine: (String) -> Unit,
    onDictateStarted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var picking by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf(TextFieldValue(queryText, TextRange(queryText.length))) }
    LaunchedEffect(queryText) {
        if (queryText != value.text) {
            value = TextFieldValue(queryText, TextRange(queryText.length))
        }
    }
    LaunchedEffect(expanded) {
        if (!expanded && value.text.isNotEmpty()) {
            value = TextFieldValue("")
        }
    }

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(expanded) {
        if (expanded) {
            focusRequester.requestFocus()
            keyboard?.show()
        } else {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }

    val voice = rememberVoiceState()
    val frozenSuggestions = remember(voice.listening) { suggestions }
    val shownSuggestions = if (voice.listening) frozenSuggestions else suggestions
    val reduced = LocalReducedMotion.current
    val animMs = if (reduced) 0 else 280
    val expandMs = if (reduced) 0 else 300
    val scheme = MaterialTheme.colorScheme
    val sakugaColors = rememberSakugaColors()
    val searchController = rememberSakugaController()
    val barPressSource = remember { MutableInteractionSource() }
    val typingPulse = remember { Animatable(1f) }
    var prevTextLength by remember { mutableIntStateOf(value.text.length) }
    LaunchedEffect(value.text.length, expanded) {
        if (!reduced && expanded && value.text.length > prevTextLength) {
            typingPulse.snapTo(1.012f)
            typingPulse.animateTo(1f, SakugaPhysics.pressSpring(false))
        }
        prevTextLength = value.text.length
    }
    var wasExpanded by remember { mutableStateOf(expanded) }
    LaunchedEffect(expanded) {
        if (expanded && !wasExpanded && searchController.lastSize.width > 0f) {
            searchController.emit(
                SakugaEffectType.SUBTLE_POP,
                androidx.compose.ui.geometry.Offset(30f, 30f),
                sakugaColors,
                intensity = 0.72f,
            )
        }
        wasExpanded = expanded
    }
    val engineHost = remember(settings.searchEngine.searchUrl) {
        UrlUtils.displayHost(settings.searchEngine.searchUrl)
    }

    val cornerRadius by animateDpAsState(
        if (expanded) 24.dp else 30.dp,
        tween(expandMs, easing = FastOutSlowInEasing),
        label = "searchCorner",
    )
    val scale by animateFloatAsState(
        if (expanded) 1.015f else 1f,
        SakugaPhysics.overshootSpring(reduced),
        label = "searchScale",
    )
    val shadow by animateDpAsState(
        if (expanded) 8.dp else 1.dp,
        tween(animMs, easing = FastOutSlowInEasing),
        label = "searchShadow",
    )
    val tonal by animateDpAsState(
        if (expanded) 4.dp else 1.dp,
        tween(animMs, easing = FastOutSlowInEasing),
        label = "searchTonal",
    )
    val bgColor by animateColorAsState(
        if (expanded) scheme.surfaceContainerHighest else scheme.surfaceContainerHigh,
        tween(animMs, easing = FastOutSlowInEasing),
        label = "searchBg",
    )
    val borderColor by animateColorAsState(
        if (expanded) sakugaColors.primaryAccent.copy(alpha = 0.62f) else scheme.outlineVariant.copy(alpha = 0.25f),
        tween(animMs, easing = FastOutSlowInEasing),
        label = "searchBorder",
    )
    val borderWidth by animateDpAsState(
        if (expanded) 1.5.dp else 1.dp,
        tween(animMs, easing = FastOutSlowInEasing),
        label = "searchBorderWidth",
    )
    val shape = RoundedCornerShape(cornerRadius)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                val combined = scale * typingPulse.value
                scaleX = combined
                scaleY = combined
            }
            .sakugaPressSquash(
                interactionSource = barPressSource,
                pressX = 1.015f,
                pressY = 0.96f,
                enabled = !expanded,
            )
            .sakugaOverlay(searchController)
            .animateContentSize(tween(expandMs, easing = FastOutSlowInEasing))
            .testTag("home_search_bar"),
        shape = shape,
        color = bgColor,
        tonalElevation = tonal,
        shadowElevation = shadow,
        border = BorderStroke(borderWidth, borderColor),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 58.dp)
                    .clickable(
                        interactionSource = barPressSource,
                        indication = LocalIndication.current,
                        enabled = !expanded,
                        role = Role.Button,
                        onClickLabel = str(R.string.search_or_type_a_url),
                        onClick = onExpand,
                    )
                    .padding(start = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchEngineIcon(
                    engineHost = engineHost,
                    incognito = incognito,
                    size = 28.dp,
                    badgeBackground = bgColor,
                    modifier = Modifier.testTag("home_search_engine_icon"),
                )

                BasicTextField(
                    value = value,
                    onValueChange = {
                        value = it
                        onTextChange(it.text)
                    },
                    enabled = expanded,
                    singleLine = true,
                    textStyle = UrlTextStyle.copy(color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.primary),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go,
                        autoCorrectEnabled = false,
                    ),
                    keyboardActions = KeyboardActions(onGo = { onSubmit(value.text) }),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp, vertical = 14.dp)
                        .voiceTrace(voice)
                        .focusRequester(focusRequester)
                        .testTag("home_search_input"),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (value.text.isEmpty()) {
                                Text(
                                    if (voice.listening) str(R.string.listening) else str(R.string.search_or_type_a_url),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (voice.listening) scheme.primary else scheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            inner()
                        }
                    },
                )

                if (!expanded) {
                    // With big system fonts the placeholder needs the room more than the engine name does.
                    if (LocalDensity.current.fontScale <= 1.3f) Box {
                        val engineBtnSource = remember { MutableInteractionSource() }
                        Text(
                            settings.searchEngine.name,
                            Modifier
                                .sakugaPressSquash(engineBtnSource, pressX = 1.05f, pressY = 0.91f)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = engineBtnSource,
                                    indication = LocalIndication.current,
                                    onClickLabel = str(R.string.default_search_engine),
                                    role = Role.Button,
                                ) { picking = true }
                                .heightIn(min = Dimens.touchTarget).wrapContentHeight().padding(horizontal = 10.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.outline,
                        )
                        DropdownMenu(expanded = picking, onDismissRequest = { picking = false }) {
                            settings.allEngines.forEach { engine ->
                                val itemHost = remember(engine.searchUrl) { UrlUtils.displayHost(engine.searchUrl) }
                                DropdownMenuItem(
                                    text = { Text(engine.name) },
                                    leadingIcon = if (itemHost.isNotEmpty()) {
                                        { SiteIcon(itemHost, size = 20.dp, fetch = true) }
                                    } else null,
                                    trailingIcon = { if (engine.id == settings.searchEngineId) Icon(Icons.Rounded.Check, null) },
                                    onClick = { picking = false; onPickEngine(engine.id) },
                                )
                            }
                        }
                    }
                } else {
                    VoiceInputButton(
                        onText = { spoken ->
                            value = TextFieldValue(spoken, TextRange(spoken.length))
                            onTextChange(spoken)
                        },
                        state = voice,
                        autoStart = dictate,
                        onAutoStarted = onDictateStarted,
                    )
                    AnimatedVisibility(
                        visible = value.text.isNotEmpty() || voice.listening,
                        enter = fadeIn(tween(if (reduced) 0 else 160)) +
                            scaleIn(tween(if (reduced) 0 else 160), initialScale = 0.84f),
                        exit = fadeOut(tween(if (reduced) 0 else 140)) +
                            scaleOut(tween(if (reduced) 0 else 140), targetScale = 0.84f),
                    ) {
                        EddyIconButton(
                            Icons.Rounded.Close,
                            str(R.string.clear_text),
                            {
                                value = TextFieldValue("")
                                onTextChange("")
                            },
                            Modifier.testTag("home_search_clear"),
                            iconSize = 20.dp,
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded && shownSuggestions.isNotEmpty(),
                enter = fadeIn(tween(if (reduced) 0 else 250, easing = FastOutSlowInEasing)) +
                    expandVertically(tween(expandMs, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(if (reduced) 0 else 180)) +
                    shrinkVertically(tween(animMs, easing = FastOutSlowInEasing)),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    HorizontalDivider(
                        Modifier.padding(horizontal = 16.dp),
                        color = scheme.outlineVariant.copy(alpha = 0.28f),
                    )
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxSuggestionsHeight)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 6.dp)
                            .testTag("home_search_suggestions"),
                    ) {
                        shownSuggestions.distinctBy { it.kind to it.text }.forEach { s ->
                            key(s.kind, s.text) {
                                SuggestionRow(
                                    s = s,
                                    onPick = { onPickSuggestion(s) },
                                    onFill = {
                                        value = TextFieldValue(s.text, TextRange(s.text.length))
                                        onTextChange(s.text)
                                    },
                                    engineHost = engineHost,
                                    incognito = incognito,
                                    badgeBorderColor = bgColor,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VisitedSections(vm: BrowserViewModel, showFrequent: Boolean, showRecent: Boolean) {
    // Nothing is queried for a section the user turned off.
    val frequentFlow = remember(vm, showFrequent) {
        if (showFrequent) vm.history.frequentSites(8) else flowOf(emptyList())
    }
    val recentFlow = remember(vm, showRecent) {
        if (showRecent) vm.history.recentSites(5) else flowOf(emptyList())
    }
    val frequent by frequentFlow.collectAsStateWithLifecycle(emptyList())
    val recent by recentFlow.collectAsStateWithLifecycle(emptyList())
    if (frequent.isNotEmpty()) {
        SectionTitle(str(R.string.frequently_visited), Modifier.entrance(2))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.entrance(2).fillMaxWidth(),
        ) {
            items(frequent, key = { it.host }) { site ->
                SakugaCard(
                    onClick = { vm.navigate(site.url) },
                    burstOnClick = true,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.heightIn(min = Dimens.touchTarget),
                ) {
                    Row(Modifier.padding(start = 8.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        SiteIcon(site.host, size = 28.dp, fetch = true)
                        Text(site.host, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    val recentShown = recent.filter { r -> frequent.take(3).none { it.host == r.host } }.take(4)
    if (recentShown.isNotEmpty()) {
        SectionTitle(str(R.string.recently_visited), Modifier.entrance(3))
        Column(Modifier.entrance(3).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)) {
            recentShown.forEach { RecentRow(it) { vm.navigate(it.url) } }
        }
    }
}

@Composable
private fun RecentRow(site: SiteVisit, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .sakugaPressSquash(source, pressX = SakugaPhysics.PRESS_CARD_SCALE_X, pressY = SakugaPhysics.PRESS_CARD_SCALE_Y)
            .clickable(interactionSource = source, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SiteIcon(site.host, size = Dimens.faviconLarge, fetch = true)
        Column(Modifier.padding(start = 14.dp)) {
            Text(site.title.ifBlank { site.host }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(site.host, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.fillMaxWidth().padding(start = 4.dp, bottom = 10.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun IncognitoNote(isolated: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(str(R.string.private_browsing), style = MaterialTheme.typography.titleMedium)
        Text(
            str(R.string.eddy_does_not_save_the_pages_you_open_he) +
                (if (isolated) str(R.string.it_erases_cookies_and_site_data_when_you)
                else str(R.string.this_device_s_webview_shares_cookies_and)) +
                str(R.string.websites_and_your_network_can_still_see),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Slow drifting silhouettes behind the content. Cheap: two graphicsLayer transforms, no recomposition. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AmbientShapes() {
    val reduced = LocalReducedMotion.current
    val transition = rememberInfiniteTransition(label = "ambient")
    val spin by transition.animateFloat(
        0f, 360f, infiniteRepeatable(tween(90_000, easing = LinearEasing), RepeatMode.Restart), label = "spin",
    )
    val drift by transition.animateFloat(
        -1f, 1f, infiniteRepeatable(tween(14_000), RepeatMode.Reverse), label = "drift",
    )
    val first = MaterialTheme.colorScheme.primaryContainer
    val second = MaterialTheme.colorScheme.tertiaryContainer
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.align(Alignment.TopEnd).size(280.dp)
                .graphicsLayer {
                    translationX = 90.dp.toPx() + if (reduced) 0f else drift * 16.dp.toPx()
                    translationY = -70.dp.toPx()
                    rotationZ = if (reduced) 0f else spin
                    alpha = 0.55f
                }
                .background(first, MaterialShapes.Cookie9Sided.toShape()),
        )
        Box(
            Modifier.align(Alignment.BottomStart).size(220.dp)
                .graphicsLayer {
                    translationX = -80.dp.toPx()
                    translationY = 60.dp.toPx() + if (reduced) 0f else drift * 12.dp.toPx()
                    rotationZ = if (reduced) 0f else -spin * 0.8f
                    alpha = 0.4f
                }
                .background(second, MaterialShapes.Clover4Leaf.toShape()),
        )
    }
}
