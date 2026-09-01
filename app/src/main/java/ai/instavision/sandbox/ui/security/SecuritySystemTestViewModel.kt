package ai.instavision.sandbox.ui.security

import ai.instavision.sandbox.data.SessionStore
import ai.instavision.sandbox.ui.common.sdkCall
import ai.instavision.sandbox.ui.common.userMessage
import ai.instavision.guardian.sdk.InstaVision
import ai.instavision.guardian.sdk.data.entity.SecurityDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * How long the backend is given to settle after an arm or a disarm before the profile is read
 * again, copied from the production app's `POLLING_DURATION`. Arming is not instantaneous.
 */
private val SettleDelay = 3000L.milliseconds

/** Everything the system test step renders. */
data class SecuritySystemTestUiState(
  /** Per-camera arming state, straight off the profile's `device_list`. */
  val deviceStates: List<SecurityDevice> = emptyList(),
  /** The profile's `status`, which is the whole system's arming state. */
  val status: String = "",
  /** Whether the backend has the home in test mode, so no alarm reaches a dispatcher. */
  val testMode: Boolean = false,
  /** True until the first profile read settles. */
  val loading: Boolean = true,
  /** True while a test-mode, arm, disarm or step write is in flight. */
  val busy: Boolean = false,
  /** Set once the step has been recorded, so the screen can hand back to the checklist. */
  val done: Boolean = false,
  /** Message from the last failed request; a missing profile never sets this. */
  val error: String? = null,
) {
  /** Whether the system counts as armed, which is what the arm and disarm buttons key off. */
  val armed: Boolean get() = status == SecurityStatus.ARMED || status == SecurityStatus.ARMING
}

/**
 * Backs the optional system test step: puts the home into test mode, then arms and disarms it for
 * real without any of it reaching the monitoring centre. Production runs this across two pages of a
 * pager; the sample keeps it on one, since the first page is only the button that starts the test.
 */
class SecuritySystemTestViewModel : ViewModel() {
  private val _uiState = MutableStateFlow(SecuritySystemTestUiState())

  /** Single source of truth for [SecuritySystemTestScreen]. */
  val uiState = _uiState.asStateFlow()

  init {
    load()
  }

  /** Refetches the profile, which carries both the system status and the per-camera states. */
  fun load() {
    val spaceId = SessionStore.spaceId
    if (spaceId.isEmpty()) {
      _uiState.update { it.copy(loading = false) }
      return
    }
    _uiState.update { it.copy(error = null) }
    viewModelScope.launch { refresh(spaceId) }
  }

  /** Enters test mode and arms the system, which is the test run itself. */
  fun startTestRun() {
    runStep { spaceId ->
      enableTestMode(spaceId)
        .onSuccess {
          arm(spaceId)
            .onFailure { error -> _uiState.update { it.copy(error = error.userMessage()) } }
          settleAndRefresh(spaceId)
        }
        .onFailure { error -> _uiState.update { it.copy(error = error.userMessage()) } }
    }
  }

  /** Arms the system again after a disarm, without leaving test mode. */
  fun armSystem() {
    runStep { spaceId ->
      arm(spaceId)
        .onSuccess { settleAndRefresh(spaceId) }
        .onFailure { error -> _uiState.update { it.copy(error = error.userMessage()) } }
    }
  }

  /** Stands the test alarm back down. */
  fun disarmSystem() {
    runStep { spaceId ->
      sdkCall<Unit> { onSuccess, onError ->
        InstaVision.securityServices.disarmSystem(
          spaceId = spaceId,
          onSuccess = onSuccess,
          onError = onError,
        )
      }
        .onSuccess { settleAndRefresh(spaceId) }
        .onFailure { error -> _uiState.update { it.copy(error = error.userMessage()) } }
    }
  }

  /**
   * Records the step as done. Skipping still enters test mode first, exactly as the production app
   * does, so a home that never ran the test is not left able to raise a real alarm by accident.
   */
  fun finish() {
    runStep { spaceId ->
      enableTestMode(spaceId)
        .onSuccess {
          markSetupStep(spaceId = spaceId, apiName = SecuritySteps.TestSystem.apiName)
            .onSuccess { _uiState.update { it.copy(done = true) } }
            .onFailure { error -> _uiState.update { it.copy(error = error.userMessage()) } }
        }
        .onFailure { error -> _uiState.update { it.copy(error = error.userMessage()) } }
    }
  }

  /** Runs [block] against the selected home with the busy flag raised for its whole duration. */
  private fun runStep(block: suspend (String) -> Unit) {
    val spaceId = SessionStore.spaceId
    if (spaceId.isEmpty()) return
    _uiState.update { it.copy(busy = true, error = null) }
    viewModelScope.launch {
      block(spaceId)
      _uiState.update { it.copy(busy = false) }
    }
  }

  /** Turns test mode on, which is what keeps an alarm raised here away from a dispatcher. */
  /** Makes the alarm live. */
  private suspend fun arm(spaceId: String): Result<Unit> = sdkCall { onSuccess, onError ->
    InstaVision.securityServices.armSystem(
      spaceId = spaceId,
      onSuccess = onSuccess,
      onError = onError,
    )
  }

  /** Waits out [SettleDelay] before rereading, because arming reports its progress in stages. */
  private suspend fun settleAndRefresh(spaceId: String) {
    delay(SettleDelay)
    refresh(spaceId)
  }

  /** Reads the profile into the state; a home with no profile leaves the screen empty, not failed. */
  private suspend fun refresh(spaceId: String) {
    fetchSecurityProfile(spaceId)
      .onSuccess { profile ->
        _uiState.update {
          it.copy(
            deviceStates = profile?.deviceList.orEmpty(),
            status = profile?.status.orEmpty(),
            testMode = profile?.testMode == true,
            loading = false,
          )
        }
      }
      .onFailure { error ->
        _uiState.update { it.copy(loading = false, error = error.userMessage()) }
      }
  }
}
