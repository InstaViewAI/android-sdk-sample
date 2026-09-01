package ai.instavision.sandbox.ui.security

import ai.instavision.sandbox.data.SessionStore
import ai.instavision.sandbox.ui.common.sdkCall
import ai.instavision.sandbox.ui.common.userMessage
import ai.instavision.guardian.sdk.InstaVision
import ai.instavision.guardian.sdk.data.entity.request.SecurityProfileRequest
import ai.instavision.guardian.sdk.data.entity.response.SecurityProfileResponse
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Exit delays the production app offers, in seconds. The backend takes any integer, but keeping to
 * this set is what makes a delay the monitoring centre's agents recognise.
 */
private val ExitDelayOptions = listOf(30, 45, 60, 90, 120)

/** The delay a home falls back to when its profile has never had one set. */
private const val DEFAULT_EXIT_DELAY = 60

/** Everything the arm settings step renders and submits. */
data class SecurityArmUiState(
  /** Seconds between arming and the alarm going live. */
  val exitDelay: Int = DEFAULT_EXIT_DELAY,
  /** True until the profile's current delay has been read. */
  val loading: Boolean = true,
  /** True while the submit is in flight. */
  val busy: Boolean = false,
  /** Set once the step has been recorded, so the screen can hand back to the checklist. */
  val done: Boolean = false,
  /** Message from the last failed request; a missing profile never sets this. */
  val error: String? = null,
) {
  /** The delays the dropdown offers, written the way the user reads them. */
  val delayLabels: List<String> get() = ExitDelayOptions.map { label(it) }

  /** The currently selected delay, written the same way. */
  val delayLabel: String get() = label(exitDelay)

  /** Turns a delay in seconds into its dropdown entry. */
  private fun label(seconds: Int): String = "$seconds seconds"

  /** Resolves a dropdown entry back to the number of seconds the request carries. */
  fun secondsFor(label: String): Int = ExitDelayOptions.firstOrNull { label(it) == label } ?: exitDelay
}

/**
 * Backs the arm settings step. Production explains arm mode and nothing else here, leaving the exit
 * delay to a settings screen the sample does not have, so the delay is collected on this step and
 * travels with the step completion in one `updateProfile` call, exactly as the disarm step does.
 */
class SecurityArmViewModel : ViewModel() {
  private val _uiState = MutableStateFlow(SecurityArmUiState())

  /** Single source of truth for [SecurityArmScreen]. */
  val uiState = _uiState.asStateFlow()

  init {
    load()
  }

  /** Pre-fills the delay from the home's profile; a home without one keeps the default. */
  fun load() {
    val spaceId = SessionStore.spaceId
    if (spaceId.isEmpty()) {
      _uiState.update { it.copy(loading = false) }
      return
    }
    _uiState.update { it.copy(loading = true, error = null) }
    viewModelScope.launch {
      fetchSecurityProfile(spaceId)
        .onSuccess { profile ->
          _uiState.update {
            it.copy(
              exitDelay = profile?.exitDelay?.takeIf { delay -> delay > 0 } ?: DEFAULT_EXIT_DELAY,
              loading = false,
            )
          }
        }
        .onFailure { error ->
          _uiState.update { it.copy(loading = false, error = error.userMessage()) }
        }
    }
  }

  /** Records the delay picked from the dropdown, which is offered by label rather than by value. */
  fun onDelayChange(label: String) {
    _uiState.update { it.copy(exitDelay = it.secondsFor(label)) }
  }

  /** Saves the delay and marks the step done in the same request; a failure keeps the user here. */
  fun submit() {
    val spaceId = SessionStore.spaceId
    val state = _uiState.value
    if (spaceId.isEmpty()) return
    _uiState.update { it.copy(busy = true, error = null) }
    viewModelScope.launch {
      sdkCall<SecurityProfileResponse> { onSuccess, onError ->
        InstaVision.securityServices.updateProfile(
          spaceId = spaceId,
          request = SecurityProfileRequest(
            setupStep = SecuritySteps.ArmSettings.apiName,
            exitDelay = state.exitDelay,
          ),
          onSuccess = onSuccess,
          onError = onError,
        )
      }
        .onSuccess { _uiState.update { it.copy(busy = false, done = true) } }
        .onFailure { error ->
          _uiState.update { it.copy(busy = false, error = error.userMessage()) }
        }
    }
  }
}
