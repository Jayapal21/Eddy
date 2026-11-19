package app.eddy.browser.browser

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.eddy.browser.R
import app.eddy.browser.data.database.SavedArticle
import app.eddy.browser.ui.components.ConfirmDialog
import app.eddy.browser.ui.components.EddyIconButton
import app.eddy.browser.ui.components.EmptyState
import app.eddy.browser.ui.components.ScreenScaffold
import app.eddy.browser.ui.components.SearchField
import app.eddy.browser.ui.sakuga.SakugaAnimationController
import app.eddy.browser.ui.sakuga.SakugaParticlePreset
import app.eddy.browser.ui.sakuga.sakugaPressEffect
import app.eddy.browser.ui.theme.Dimens
import app.eddy.browser.util.UrlUtils
import app.eddy.browser.util.plural
import app.eddy.browser.util.str

/** Offline library of Reader Mode articles (extracted text and cached images) stored in Room. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SavedArticlesScreen(vm: BrowserViewModel) {
    val articles by vm.savedArticlesList.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<SavedArticle?>(null) }
    var clearingAll by remember { mutableStateOf(false) }

    val searching = query.isNotBlank()
    val visible = remember(articles, query) {
        if (!searching) articles
        else articles.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.siteName.contains(query, ignoreCase = true) ||
                it.url.contains(query, ignoreCase = true)
        }
    }

    ScreenScaffold(
        title = str(R.string.saved_articles),
        onBack = { vm.screen = Screen.BROWSER },
        actions = {
            if (articles.isNotEmpty()) {
                EddyIconButton(Icons.Rounded.DeleteSweep, str(R.string.clear_all), { clearingAll = true })
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchField(query, { query = it }, str(R.string.search_saved_articles))
            if (visible.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.LibraryBooks,
                    title = if (searching) str(R.string.no_matches) else str(R.string.no_saved_articles),
                    message = if (searching) str(R.string.try_a_different_search) else str(R.string.tap_offline_save_in_reader_mode),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).navigationBarsPadding(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    items(visible, key = { it.id }) { article ->
                        SavedArticleRow(
                            article = article,
                            onOpen = { vm.openSavedArticle(article) },
                            onDelete = { deleting = article },
                        )
                    }
                }
            }
        }
    }

    deleting?.let { article ->
        ConfirmDialog(
            title = str(R.string.delete_saved_article),
            message = article.title,
            confirmLabel = str(R.string.delete_4),
            onDismiss = { deleting = null },
        ) {
            vm.deleteSavedArticle(article)
            deleting = null
        }
    }
    if (clearingAll) {
        ConfirmDialog(
            title = str(R.string.clear_saved_articles),
            message = str(R.string.tap_offline_save_in_reader_mode),
            confirmLabel = str(R.string.clear_all),
            onDismiss = { clearingAll = false },
        ) {
            vm.clearSavedArticles()
            clearingAll = false
        }
    }
}

@Composable
private fun SavedArticleRow(
    article: SavedArticle,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val host = article.siteName.ifBlank { UrlUtils.displayHost(article.url) }
    val relTime = remember(article.savedAt) {
        DateUtils.getRelativeTimeSpanString(
            article.savedAt,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        ).toString()
    }
    val meta = buildList {
        if (host.isNotBlank()) add(host)
        add("${article.readMinutes} min")
        if (article.imageCount > 0) add(plural(R.plurals.cached_image_count, article.imageCount))
        add(relTime)
    }.joinToString(" · ")

    val source = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .sakugaPressEffect(
                interactionSource = source,
                role = SakugaAnimationController.ComponentRole.CARD,
                enableParticles = false,
            )
            .clickable(interactionSource = source, indication = LocalIndication.current, role = Role.Button, onClickLabel = str(R.string.open), onClick = onOpen)
            .padding(start = Dimens.gutter, end = 4.dp, top = 10.dp, bottom = 10.dp)
            .testTag("saved_article_row_${article.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(Dimens.faviconLarge)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.OfflinePin,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(
                text = article.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        EddyIconButton(
            icon = Icons.Rounded.Delete,
            contentDescription = str(R.string.delete_4),
            onClick = onDelete,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            particlePreset = SakugaParticlePreset.DELETE,
        )
    }
}
