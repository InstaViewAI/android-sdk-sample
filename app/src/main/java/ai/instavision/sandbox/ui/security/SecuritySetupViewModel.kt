package ai.instavision.sandbox.ui.security

import ai.instavision.sandbox.data.SessionStore
import ai.instavision.sandbox.ui.common.sdkCall
import ai.instavision.sandbox.ui.common.userMessage
import ai.instavision.guardian.sdk.InstaVision
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Progress through the monitoring checklist, and everything the screen derives from it. */
data class SecuritySetupUiState(
  /** `apiName`s the backend has recorded as done; it may hold values this build cannot map. */
  val completedSteps: List<String> = emptyList(),
  /** True until the first profile read settles. */
  val loading: Boolean = true,
  /** True while the terminal `Completed` write is in flight. */
  val busy: Boolean = false,
  /** Set once the profile has been marked complete, so the screen can pop itself. */
  val finished: Boolean = false,
  /** Message from the last failed request; a missing profile never sets this. */
  val error: String? = null,
) {
  /** How many of the seven checklist steps are done, which is the numerator of the ring. */
  val completedCount: Int get() = SecuritySteps.entries.count { isComplete(it) }

  /** The step the checklist is waiting on; null once every step, optional included, is done. */
  val currentStep: SecuritySteps? get() = SecuritySteps.entries.firstOrNull { !isComplete(it) }

  /** Whether every step monitoring depends on is done, which is what unlocks finishing. */
  val requiredDone: Boolean get() = SecuritySteps.required.all { isComplete(it) }

  /** Whether [step] is recorded as done. */
  fun isComplete(step: SecuritySteps): Boolean = step.apiName in completedSteps

  /** Whether [step] can be opened yet; every required step ahead of it has to be done first. */
  fun isReachable(step: SecuritySteps): Boolean =
    SecuritySteps.entries.takeWhile { it != step }.none { !it.optional && !isComplete(it) }
}

/**
 * Backs the monitoring checklist: reads which steps the backend has recorded for
 * [SessionStore.selectedSpace] and writes the terminal `Completed` step once the required ones
 * are done. The individual steps write their own progress; this only reads it back.
 */
class SecuritySetupViewModel : ViewModel() {
  private val _uiState = MutableStateFlow(SecuritySetupUiState())
  private var hasResumed = false

  /** Single source of truth for [SecuritySetupScreen]. */
  val uiState = _uiState.asStateFlow()

  init {
    load()
  }

  /** Refetches the profile, leaving the checklist on screen while it runs. */
  fun load() {
    val spaceId = SessionStore.spaceId
    if (spaceId.isEmpty()) {
      _uiState.update { it.copy(loading = false) }
      return
    }
    _uiState.update { it.copy(error = null) }
    viewModelScope.launch {
      fetchSecurityProfile(spaceId)
        .onSuccess { profile ->
          _uiState.update {
            it.copy(completedSteps = profile?.completedSteps.orEmpty(), loading = false)
          }
        }
        .onFailure { error ->
          _uiState.update { it.copy(loading = false, error = error.userMessage()) }
        }
    }
  }

  /**
   * Refetches when the screen comes back to the foreground, skipping the first resume because
   * [init] has already read the profile. This is what ticks off a step the user just finished.
   */
  fun refreshOnResume() {
    if (!hasResumed) {
      hasResumed = true
      return
    }
    load()
  }

  /**
   * Marks the profile complete, which is what lets the Security tab leave its setup prompt. The
   * per-step screens never write this value, so the checklist has to close itself out. A home that
   * skipped the optional test step is put into test mode first, exactly as the production app does
   * when that step is skipped, so no home goes live having never been in test mode.
   */
  fun finish() {
    val spaceId = SessionStore.spaceId
    if (spaceId.isEmpty()) return
    val needsTestMode = !_uiState.value.isComplete(SecuritySteps.TestSystem)
    _uiState.update { it.copy(busy = true, error = null) }
    viewModelScope.launch {
      val prepared = if (needsTestMode) enableTestMode(spaceId) else Result.success(Unit)
      prepared
        .onSuccess { markCompleted(spaceId) }
        .onFailure { error ->
          _uiState.update { it.copy(busy = false, error = error.userMessage()) }
        }
    }
  }

  /** Writes the terminal `Completed` step, which is the last request setup ever makes. */
  private suspend fun markCompleted(spaceId: String) {
    markSetupStep(spaceId = spaceId, apiName = SecuritySteps.Completed.apiName)
      .onSuccess { _uiState.update { it.copy(busy = false, finished = true) } }
      .onFailure { error ->
        _uiState.update { it.copy(busy = false, error = error.userMessage()) }
      }
  }
}
