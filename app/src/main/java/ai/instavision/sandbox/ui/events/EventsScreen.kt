package ai.instavision.sandbox.ui.events

import ai.instavision.sandbox.ui.common.ConfirmDialog
import ai.instavision.sandbox.ui.common.EmptyState
import ai.instavision.sandbox.ui.common.ErrorBanner
import ai.instavision.sandbox.ui.common.GroupCard
import ai.instavision.sandbox.ui.common.LoadingBox
import ai.instavision.sandbox.ui.common.RootScaffold
import ai.instavision.sandbox.ui.common.RowDivider
import ai.instavision.sandbox.ui.common.ScreenPadding
import ai.instavision.sandbox.ui.common.SelectableChip
import ai.instavision.sandbox.ui.common.TextLink
import ai.instavision.sandbox.ui.theme.AppTheme
import ai.instavision.guardian.sdk.data.entity.Event
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Root of the Events tab: the selected space's activity, grouped into days and filtered by what
 * was detected. The header doubles as the multi-select bar, which is why the title and the action
 * both depend on whether rows are being ticked.
 */
@Composable
fun EventsScreen(onEvent: (Event) -> Unit) {
  val viewModel: EventsViewModel = viewModel()
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val listState = rememberLazyListState()
  var confirmingDelete by remember { mutableStateOf(false) }
  val days = remember(state.events) { groupByDay(state.events) }

  LifecycleResumeEffect(Unit) {
    viewModel.refreshOnResume()
    onPauseOrDispose {}
  }

  PaginationEffect(
    canLoadMore = state.canLoadMore,
    listState = listState,
    onLoadMore = viewModel::loadMore,
  )

  RootScaffold(
    title = if (state.selecting) "${state.selectedCount} selected" else "Events",
    action = {
      EventsHeaderAction(
        state = state,
        onDelete = { confirmingDelete = true },
        onEnterSelection = viewModel::enterSelection,
        onExitSelection = viewModel::exitSelection,
      )
    },
    scrollable = false,
  ) {
    Box(modifier = Modifier.padding(horizontal = ScreenPadding)) {
      ErrorBanner(message = state.error)
    }
    EventFilterRow(selected = state.filter, onSelect = viewModel::selectFilter)
    when {
      state.loading && state.events.isEmpty() -> LoadingBox()
      state.isEmpty -> EmptyState(
        title = "No events yet",
        body = "Activity your cameras capture will show up here.",
        icon = Icons.Outlined.Videocam,
      )
      else -> EventDayList(
        days = days,
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        listState = listState,
        selecting = state.selecting,
        selectedIds = state.selectedIds,
        loadingMore = state.loadingMore,
        onEvent = { event ->
          if (state.selecting) {
            viewModel.toggleSelection(event)
          } else {
            viewModel.select(event)
            onEvent(event)
          }
        },
      )
    }
  }

  if (confirmingDelete) {
    ConfirmDialog(
      title = "Delete events",
      message = "Delete ${state.selectedCount} event(s)? This cannot be undone.",
      confirmLabel = "Delete",
      onConfirm = {
        confirmingDelete = false
        viewModel.deleteSelected()
      },
      onDismiss = { confirmingDelete = false },
    )
  }
}

/** "Select" on its own, or the delete-and-done pair once the list is being edited. */
@Composable
private fun EventsHeaderAction(
  state: EventsUiState,
  onDelete: () -> Unit,
  onEnterSelection: () -> Unit,
  onExitSelection: () -> Unit,
) {
  if (!state.selecting) {
    TextLink(text = "Select", onClick = onEnterSelection)
    return
  }
  Row(verticalAlignment = Alignment.CenterVertically) {
    TextLink(
      text = "Delete",
      onClick = onDelete,
      enabled = state.selectedCount > 0 && !state.busy,
      color = AppTheme.colors.danger,
    )
    TextLink(text = "Done", onClick = onExitSelection)
  }
}

/**
 * The horizontally scrolling chip row that narrows the list to one kind of detection. The applied
 * chip drops its glyph, so its accent fill rather than an icon is what reads as the current filter.
 */
@Composable
private fun EventFilterRow(selected: EventFilter, onSelect: (EventFilter) -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .horizontalScroll(rememberScrollState())
      .padding(horizontal = ScreenPadding, vertical = ChipRowVerticalPadding),
    horizontalArrangement = Arrangement.spacedBy(ChipSpacing),
  ) {
    EventFilter.entries.forEach { filter ->
      SelectableChip(
        label = filter.label,
        selected = filter == selected,
        onClick = { onSelect(filter) },
        icon = filterIcon(filter).takeIf { filter != selected },
      )
    }
  }
}

/**
 * The list itself: a pinned band per calendar day over the card of that day's rows. It paints its
 * own flat `AppTheme.colors.ground` backdrop because the scaffold's background is still graduating
 * out of `groundTop` this far up the screen, and a pinned band filled with flat ground would
 * otherwise read as a darker strip against it.
 */
@Composable
private fun EventDayList(
  days: List<EventDay>,
  modifier: Modifier = Modifier,
  listState: LazyListState,
  selecting: Boolean,
  selectedIds: Set<String>,
  loadingMore: Boolean,
  onEvent: (Event) -> Unit,
) {
  LazyColumn(
    state = listState,
    modifier = modifier.background(color = AppTheme.colors.ground),
    contentPadding = PaddingValues(bottom = ListBottomPadding),
  ) {
    days.forEach { day ->
      stickyHeader(key = "band-${day.label}") { DayBand(label = day.label) }
      item(key = "card-${day.label}") {
        GroupCard(modifier = Modifier.padding(horizontal = ScreenPadding)) {
          day.events.forEachIndexed { index, event ->
            if (index > 0) RowDivider()
            EventRow(
              event = event,
              selected = if (selecting) event.id in selectedIds else null,
              onClick = { onEvent(event) },
            )
          }
        }
      }
    }
    if (loadingMore) {
      item(key = "loading-more") { LoadingBox() }
    }
  }
}

/** The pinned day caption; it paints the ground colour so rows scroll out of sight behind it. */
@Composable
private fun DayBand(label: String) {
  Text(
    text = label,
    style = MaterialTheme.typography.bodyMedium,
    color = AppTheme.colors.textSecondary,
    modifier = Modifier
      .fillMaxWidth()
      .background(color = AppTheme.colors.ground)
      .padding(start = ScreenPadding, top = BandVerticalPadding, bottom = BandVerticalPadding),
  )
}

/**
 * Asks for the next page once the list is scrolled to within [PrefetchDistance] of its end. Shared
 * with the security log, so both paged lists reach for a page at the same distance from the end.
 */
@Composable
internal fun PaginationEffect(
  canLoadMore: Boolean,
  listState: LazyListState,
  onLoadMore: () -> Unit,
) {
  LaunchedEffect(listState, canLoadMore) {
    if (!canLoadMore) return@LaunchedEffect
    snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
      .distinctUntilChanged()
      .collect { lastVisible ->
        val total = listState.layoutInfo.totalItemsCount
        if (total > 0 && lastVisible >= total - PrefetchDistance) onLoadMore()
      }
  }
}

/** One calendar day of activity, as the list renders it. */
private data class EventDay(
  /** Band caption, reading "Today", "Yesterday" or a formatted date. */
  val label: String,
  /** That day's events, newest first. */
  val events: List<Event>,
)

/** Splits [events] into calendar days in the phone's zone, keeping the order they arrived in. */
private fun groupByDay(events: List<Event>): List<EventDay> {
  val zone = ZoneId.systemDefault()
  val today = LocalDate.now(zone)
  return events
    .groupBy { Instant.ofEpochMilli(it.startTime).atZone(zone).toLocalDate() }
    .map { (date, dayEvents) -> EventDay(label = dayLabel(date, today), events = dayEvents) }
}

/** Names a day relative to [today], falling back to a date once it is older than yesterday. */
private fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
  today -> "Today"
  today.minusDays(1) -> "Yesterday"
  else -> date.format(DayFormatter)
}

/** The glyph of a filter chip; the chip that asks for everything deliberately has none. */
private fun filterIcon(filter: EventFilter): ImageVector? = when (filter) {
  EventFilter.All -> null
  EventFilter.Person -> Icons.Outlined.Person
  EventFilter.Vehicle -> Icons.Outlined.DirectionsCar
  EventFilter.Animal -> Icons.Outlined.Pets
  EventFilter.Doorbell -> Icons.Outlined.Notifications
}

/** How a day older than yesterday is captioned. */
private val DayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

/**
 * How close to the end of the list a scroll gets before the next page is asked for. Counted in
 * list items rather than events, because every day contributes a band and a single card.
 */
private const val PrefetchDistance = 3

/** Vertical padding around the filter chip row. */
private val ChipRowVerticalPadding = 8.dp

/** Gap between two filter chips. */
private val ChipSpacing = 8.dp

/** Vertical padding of a pinned day band. */
private val BandVerticalPadding = 10.dp

/** Breathing room under the last card, so it clears the tab bar. */
private val ListBottomPadding = 16.dp
