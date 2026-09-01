package ai.instavision.sandbox.ui.security

import ai.instavision.sandbox.ui.common.DetailScaffold
import ai.instavision.sandbox.ui.common.EmptyState
import ai.instavision.sandbox.ui.common.ErrorBanner
import ai.instavision.sandbox.ui.common.LoadingBox
import ai.instavision.sandbox.ui.common.ScreenPadding
import ai.instavision.sandbox.ui.events.PaginationEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** Said when the space has been armed at no point in the window the log covers. */
private const val EMPTY_BODY =
  "Every time the system is armed, what happened while it was will show up here."

/**
 * The whole security log: one card per arming session, paged as the list is scrolled. Tapping a
 * detection fetches the event behind it and opens the same detail screen the Events tab does,
 * which is why [onEvent] takes nothing — the event travels through `SessionStore`.
 */
@Composable
fun SecurityLogScreen(onBack: () -> Unit, onEvent: () -> Unit) {
  val viewModel: SecurityLogViewModel = viewModel()
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val listState = rememberLazyListState()

  LaunchedEffect(state.eventReady) {
    if (state.eventReady) {
      viewModel.consumeEventReady()
      onEvent()
    }
  }

  PaginationEffect(
    canLoadMore = state.canLoadMore,
    listState = listState,
    onLoadMore = viewModel::loadMore,
  )

  DetailScaffold(title = "Security log", onBack = onBack, scrollable = false) {
    Box(modifier = Modifier.padding(horizontal = ScreenPadding)) {
      ErrorBanner(message = state.error)
    }
    when {
      state.loading -> LoadingBox()
      state.isEmpty -> EmptyState(
        title = "No activity yet",
        body = EMPTY_BODY,
        icon = Icons.Outlined.Shield,
      )

      else -> LazyColumn(
        state = listState,
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        contentPadding = PaddingValues(
          start = ScreenPadding,
          end = ScreenPadding,
          top = ListVerticalPadding,
          bottom = ListVerticalPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(CardSpacing),
      ) {
        items(items = state.sessions, key = { it.id }) { session ->
          SecurityLogCard(session = session, onEvent = viewModel::openEvent)
        }
        if (state.loadingMore) {
          item(key = "loading-more") { LoadingBox() }
        }
      }
    }
  }
}

/** Gap between two session cards. */
private val CardSpacing = 12.dp

/** Breathing room above the first card and below the last one. */
private val ListVerticalPadding = 8.dp
