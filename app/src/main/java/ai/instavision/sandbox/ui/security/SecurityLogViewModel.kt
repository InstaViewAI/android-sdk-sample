package ai.instavision.sandbox.ui.security

import ai.instavision.sandbox.data.EventTokenStore
import ai.instavision.sandbox.data.SessionStore
import ai.instavision.sandbox.ui.common.sdkCall
import ai.instavision.sandbox.ui.common.userMessage
import ai.instavision.guardian.sdk.InstaVision
import ai.instavision.guardian.sdk.data.entity.Event
import ai.instavision.guardian.sdk.data.entity.SecurityLog
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the full security log renders. */
data class SecurityLogUiState(
  /** Arming sessions folded out of every page loaded so far, newest first. */
  val sessions: List<SecuritySession> = emptyList(),
  /** True while the first page is in flight. */
  val loading: Boolean = true,
  /** True while a further page is in flight, which shows a spinner under the list. */
  val loadingMore: Boolean = false,
  /** True while the backend still holds entries beyond the ones already loaded. */
  val canLoadMore: Boolean = false,
  /** True while a tapped detection is being fetched, which holds off a second tap. */
  val opening: Boolean = false,
  /** True once that fetch has landed and been stored, which is the cue to open the detail. */
  val eventReady: Boolean = false,
  /** Message from the last failed request. */
  val error: String? = null,
) {
  /** True once a completed fetch has produced no session, which is when the empty state shows. */
  val isEmpty: Boolean get() = sessions.isEmpty() && !loading
}

/**
 * Pages the selected space's security log and folds every page into arming sessions. The raw
 * entries are kept because grouping is not page-local: a session opened by an `Armed` entry on one
 * page may hold detections that only arrive on the next, so each page regroups the whole window.
 */
class SecurityLogViewModel : ViewModel() {
  private val logs = mutableListOf<SecurityLog>()
  private val _uiState = MutableStateFlow(SecurityLogUiState())
  private var totalCount = 0

  /** Single source of truth for [SecurityLogScreen]. */
  val uiState = _uiState.asStateFlow()

  init {
    observeSelectedSpace()
  }

  /** Fetches the page after the loaded window; a no-op while a fetch is already running. */
  fun loadMore() {
    val state = _uiState.value
    if (state.loading || state.loadingMore || !state.canLoadMore) return
    val spaceId = SessionStore.spaceId
    if (spaceId.isEmpty()) return
    _uiState.update { it.copy(loadingMore = true) }
    viewModelScope.launch { fetchPage(spaceId = spaceId, skip = logs.size.toLong()) }
  }

  /**
   * Fetches the event a detection row stands for and hands it to [SessionStore], which is how the
   * detail screen is opened without this screen knowing anything about it. The event arrives with
   * no siblings, so the detail screen's step chevrons are dead there — the log is not a window
   * into the events list and has none to offer.
   */
  fun openEvent(eventId: String) {
    val spaceId = SessionStore.spaceId
    if (spaceId.isEmpty() || _uiState.value.opening) return
    _uiState.update { it.copy(opening = true, error = null) }
    viewModelScope.launch {
      sdkCall<Event> { onSuccess, onError ->
        InstaVision.eventServices.getEvent(
          spaceId = spaceId,
          eventId = eventId,
          onSuccess = onSuccess,
          onError = onError,
        )
      }
        .onSuccess { event ->
          SessionStore.selectEvent(event)
          _uiState.update { it.copy(opening = false, eventReady = true) }
        }
        .onFailure { error ->
          _uiState.update { it.copy(opening = false, error = error.userMessage()) }
        }
    }
  }

  /** Clears the open cue once the screen has acted on it, so a return here does not re-navigate. */
  fun consumeEventReady() {
    _uiState.update { it.copy(eventReady = false) }
  }

  /**
   * Keys the log on the selected space, the way the events list does, so reopening this screen
   * after a space switch reloads rather than showing the previous home's sessions. `collectLatest`
   * abandons a page whose space is already stale.
   */
  private fun observeSelectedSpace() {
    viewModelScope.launch {
      snapshotFlow { SessionStore.spaceId }
        .distinctUntilChanged()
        .collectLatest { spaceId -> loadFirstPage(spaceId) }
    }
  }

  /** Discards the loaded window and fetches the newest page of [spaceId] into it. */
  private suspend fun loadFirstPage(spaceId: String) {
    logs.clear()
    totalCount = 0
    if (spaceId.isEmpty()) {
      _uiState.update { it.copy(sessions = emptyList(), loading = false, canLoadMore = false) }
      return
    }
    _uiState.update { it.copy(loading = true, error = null) }
    fetchPage(spaceId = spaceId, skip = 0L)
  }

  /**
   * Fetches one page and republishes the whole window regrouped, or reports why it could not. A
   * page that comes back empty ends the paging whatever the reported total says, so a total the
   * backend counts differently from the entries it serves cannot spin the list forever.
   */
  private suspend fun fetchPage(spaceId: String, skip: Long) {
    fetchSecurityLogs(spaceId = spaceId, skip = skip)
      .onSuccess { response ->
        logs += response.logs
        totalCount = if (response.logs.isEmpty()) logs.size else response.totalCount
        publish(spaceId)
      }
      .onFailure { error ->
        _uiState.update {
          it.copy(loading = false, loadingMore = false, error = error.userMessage())
        }
      }
  }

  /**
   * Regroups every entry loaded so far and publishes it signed. The tokens are refreshed before
   * the grouping rather than after it, so a detection's snapshot is never rendered with the token
   * the page happened to arrive carrying, which may already have expired.
   */
  private suspend fun publish(spaceId: String) {
    EventTokenStore.refresh(spaceId)
    val sessions = groupSecuritySessions(logs).signed(spaceId)
    _uiState.update {
      it.copy(
        sessions = sessions,
        loading = false,
        loadingMore = false,
        canLoadMore = logs.size < totalCount,
      )
    }
  }
}
