package ai.instavision.sandbox.ui.events

import ai.instavision.sandbox.data.EventTokenStore
import ai.instavision.sandbox.data.SessionStore
import ai.instavision.sandbox.ui.common.sdkCall
import ai.instavision.sandbox.ui.common.userMessage
import ai.instavision.guardian.sdk.InstaVision
import ai.instavision.guardian.sdk.data.entity.Event
import ai.instavision.guardian.sdk.data.entity.request.DeleteEventsRequest
import ai.instavision.guardian.sdk.data.entity.response.PaginatedResponse
import ai.instavision.guardian.sdk.data.enums.EventTag
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The chips above the events list. [tag] is the value the SDK's `tags` argument expects, and is
 * null for the chip that asks for everything.
 */
enum class EventFilter(val label: String, val tag: String?) {
  /** Every detection the space recorded. */
  All("All", null),

  /** People seen by any camera in the space. */
  Person("Person", EventTag.PERSON.value),

  /** Vehicles seen by any camera in the space. */
  Vehicle("Vehicle", EventTag.VEHICLE.value),

  /** Animals seen by any camera in the space. */
  Animal("Animal", EventTag.ANIMAL.value),

  /** Presses of a paired doorbell. */
  Doorbell("Doorbell", EventTag.DOORBELL.value),
}

/** Everything the events tab renders: the loaded window of activity and the editing state. */
data class EventsUiState(
  /** Events loaded so far for [filter], newest first. */
  val events: List<Event> = emptyList(),
  /** Chip currently applied; changing it discards [events] and refetches. */
  val filter: EventFilter = EventFilter.All,
  /** How many events the backend holds for this window, which is what bounds pagination. */
  val totalCount: Int = 0,
  /** True while the first page is in flight. */
  val loading: Boolean = true,
  /** True while a further page is in flight, which shows a spinner under the list. */
  val loadingMore: Boolean = false,
  /** True while a delete is in flight, which disables the header actions. */
  val busy: Boolean = false,
  /** True when rows carry checkboxes and taps toggle instead of opening. */
  val selecting: Boolean = false,
  /** Ids ticked in selection mode. */
  val selectedIds: Set<String> = emptySet(),
  /** Message from the last failed request; cleared when the next one starts. */
  val error: String? = null,
) {
  /** How many events are ticked, which the header counts. */
  val selectedCount: Int get() = selectedIds.size

  /** True while the backend still holds events beyond the ones already loaded. */
  val canLoadMore: Boolean get() = events.size < totalCount

  /** True once a completed fetch has produced nothing, which is when the empty state shows. */
  val isEmpty: Boolean get() = events.isEmpty() && !loading
}

/**
 * Pages the selected space's activity, keyed on [SessionStore.spaceId] so switching space reloads
 * the list rather than showing another space's events. Every published page is mirrored into
 * [SessionStore.putEvents], which is what lets the detail screen step to the next event.
 */
class EventsViewModel : ViewModel() {
  private val _uiState = MutableStateFlow(EventsUiState())
  private val reloads = MutableStateFlow(0)
  private var hasResumed = false

  /** Single source of truth for [EventsScreen]. */
  val uiState = _uiState.asStateFlow()

  init {
    observeSelectedSpace()
  }

  /** Refetches the first page of the current filter, keeping what is on screen until it lands. */
  fun refresh() {
    reloads.update { it + 1 }
  }

  /**
   * Refetches when the tab comes back to the foreground, skipping the first resume because
   * [observeSelectedSpace] has already loaded. This is what picks up an event deleted on the
   * detail screen without the two screens knowing about each other.
   */
  fun refreshOnResume() {
    if (!hasResumed) {
      hasResumed = true
      return
    }
    refresh()
  }

  /** Applies a chip, dropping the loaded window because it belonged to the previous filter. */
  fun selectFilter(filter: EventFilter) {
    if (filter == _uiState.value.filter) return
    _uiState.update {
      it.copy(
        filter = filter,
        events = emptyList(),
        totalCount = 0,
        loading = true,
        selectedIds = emptySet(),
        error = null,
      )
    }
    refresh()
  }

  /** Fetches the page after the loaded window; a no-op while a fetch is already running. */
  fun loadMore() {
    val state = _uiState.value
    if (state.loading || state.loadingMore || !state.canLoadMore) return
    val spaceId = SessionStore.spaceId
    if (spaceId.isEmpty()) return
    _uiState.update { it.copy(loadingMore = true) }
    viewModelScope.launch {
      fetchPage(spaceId = spaceId, skip = state.events.size.toLong())
        .onSuccess { page -> publish(spaceId = spaceId, page = page, replace = false) }
        .onFailure { error ->
          _uiState.update { it.copy(loadingMore = false, error = error.userMessage()) }
        }
    }
  }

  /** Turns the list into a multi-select, where a tap ticks a row instead of opening it. */
  fun enterSelection() {
    _uiState.update { it.copy(selecting = true, selectedIds = emptySet()) }
  }

  /** Leaves multi-select, discarding whatever was ticked. */
  fun exitSelection() {
    _uiState.update { it.copy(selecting = false, selectedIds = emptySet()) }
  }

  /** Ticks or unticks one row of the multi-select. */
  fun toggleSelection(event: Event) {
    _uiState.update { state ->
      val next = if (event.id in state.selectedIds) {
        state.selectedIds - event.id
      } else {
        state.selectedIds + event.id
      }
      state.copy(selectedIds = next)
    }
  }

  /** Records the event the detail screen opens; the caller navigates afterwards. */
  fun select(event: Event) {
    SessionStore.selectEvent(event)
  }

  /** Deletes every ticked event, then drops them from the list and leaves multi-select. */
  fun deleteSelected() {
    val ids = _uiState.value.selectedIds.toList()
    if (ids.isEmpty()) return
    val spaceId = SessionStore.spaceId
    _uiState.update { it.copy(busy = true, error = null) }
    viewModelScope.launch {
      sdkCall<Unit> { onSuccess, onError ->
        InstaVision.eventServices.deleteEvents(
          deleteEventsRequest = DeleteEventsRequest(eventIds = ids),
          spaceId = spaceId,
          onSuccess = { onSuccess(Unit) },
          onError = onError,
        )
      }
        .onSuccess { dropDeleted(ids) }
        .onFailure { error ->
          _uiState.update { it.copy(busy = false, error = error.userMessage()) }
        }
    }
  }

  /** Dismisses the last failure so a retry starts from a clean banner. */
  fun clearError() {
    _uiState.update { it.copy(error = null) }
  }

  /**
   * Keys the fetch on the selected space and on [reloads], so both a space switch and an explicit
   * refresh reload. `collectLatest` abandons a page whose space or filter is already stale.
   */
  private fun observeSelectedSpace() {
    viewModelScope.launch {
      combine(
        snapshotFlow { SessionStore.spaceId }.distinctUntilChanged(),
        reloads,
      ) { spaceId, reload -> spaceId to reload }
        .distinctUntilChanged()
        .collectLatest { (spaceId, _) -> loadFirstPage(spaceId) }
    }
  }

  /** Fetches the newest page for [spaceId]; an empty id means no space is selected yet. */
  private suspend fun loadFirstPage(spaceId: String) {
    if (spaceId.isEmpty()) {
      _uiState.update { it.copy(events = emptyList(), totalCount = 0, loading = false) }
      SessionStore.putEvents(emptyList())
      return
    }
    _uiState.update { it.copy(loading = true, error = null) }
    fetchPage(spaceId = spaceId, skip = 0L)
      .onSuccess { page -> publish(spaceId = spaceId, page = page, replace = true) }
      .onFailure { error ->
        _uiState.update { it.copy(loading = false, error = error.userMessage()) }
      }
  }

  /** One `getEvents` call over the default window, narrowed to the chip currently applied. */
  private suspend fun fetchPage(spaceId: String, skip: Long): Result<PaginatedResponse<Event>> {
    val now = System.currentTimeMillis()
    val tags = _uiState.value.filter.tag?.let { listOf(it) }
    return sdkCall { onSuccess, onError ->
      InstaVision.eventServices.getEvents(
        spaceId = spaceId,
        from = now - EventWindowMillis,
        to = now,
        tags = tags,
        skip = skip,
        onSuccess = onSuccess,
        onError = onError,
      )
    }
  }

  /**
   * Merges a page into the loaded window and publishes it signed. The tokens are refreshed before
   * the merge rather than after it, so the list never renders a URL carrying the token the page
   * happened to arrive with — that token expires within minutes and is what the clips 401 on.
   */
  private suspend fun publish(spaceId: String, page: PaginatedResponse<Event>, replace: Boolean) {
    EventTokenStore.refresh(spaceId)
    val merged = EventTokenStore.sign(
      (if (replace) page.items else _uiState.value.events + page.items)
        .distinctBy { it.id }
        .sortedByDescending { it.startTime },
    )
    _uiState.update {
      it.copy(
        events = merged,
        totalCount = page.totalCount,
        loading = false,
        loadingMore = false,
      )
    }
    SessionStore.putEvents(merged)
  }

  /** Removes locally what the backend has just deleted, so nothing is refetched to find out. */
  private fun dropDeleted(ids: List<String>) {
    val remaining = _uiState.value.events.filterNot { it.id in ids }
    _uiState.update {
      it.copy(
        events = remaining,
        totalCount = (it.totalCount - ids.size).coerceAtLeast(0),
        busy = false,
        selecting = false,
        selectedIds = emptySet(),
      )
    }
    SessionStore.putEvents(remaining)
  }
}

/**
 * How far back the events list looks, matching the reference app's thirty-day window. It is a
 * constant so that anything else in this package fetching activity asks for the same window as the
 * list that publishes into [SessionStore.events].
 */
internal const val EventWindowMillis = 30L * 24 * 60 * 60 * 1000
