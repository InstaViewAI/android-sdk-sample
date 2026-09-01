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
 * Lengths the backend accepts for a safe word, copied from the production app's `SAFE_WORD_RANGE`.
 * It has to be long enough to be unambiguous and short enough to say under stress.
 */
private val SafeWordRange = 3..10

/** Everything the disarm settings step renders and submits. */
data class SecurityDisarmUiState(
  /** The word the monitoring agent asks for; never read back, so it starts empty every visit. */
  val safeWord: String = "",
  /** True while the submit is in flight. */
  val busy: Boolean = false,
  /** Set once the step has been recorded, so the screen can hand back to the checklist. */
  val done: Boolean = false,
  /** Message from the last failed request. */
  val error: String? = null,
) {
  /** How many more characters the safe word may take, for the counter under the field. */
  val remaining: Int get() = SafeWordRange.last - safeWord.length

  /** Whether the safe word is long enough to send. */
  val canSubmit: Boolean get() = safeWord.length in SafeWordRange
}

/**
 * Backs the disarm settings step. The safe word and the step completion travel in one
 * `updateProfile` call, which is exactly what the production app does here.
 */
class SecurityDisarmViewModel : ViewModel() {
  private val _uiState = MutableStateFlow(SecurityDisarmUiState())

  /** Single source of truth for [SecurityDisarmScreen]. */
  val uiState = _uiState.asStateFlow()

  /** Records the safe word, trimmed and clipped to the length the backend accepts. */
  fun onSafeWordChange(value: String) {
    val trimmed = value.trim()
    if (trimmed.length > SafeWordRange.last) return
    _uiState.update { it.copy(safeWord = trimmed) }
  }

  /** Saves the safe word and marks the step done in the same request. */
  fun submit() {
    val spaceId = SessionStore.spaceId
    val state = _uiState.value
    if (spaceId.isEmpty() || !state.canSubmit) return
    _uiState.update { it.copy(busy = true, error = null) }
    viewModelScope.launch {
      sdkCall<SecurityProfileResponse> { onSuccess, onError ->
        InstaVision.securityServices.updateProfile(
          spaceId = spaceId,
          request = SecurityProfileRequest(
            safeWord = state.safeWord,
            setupStep = SecuritySteps.DisarmSettings.apiName,
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
