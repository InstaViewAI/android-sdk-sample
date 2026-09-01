package ai.instavision.sandbox.ui.auth

import ai.instavision.sandbox.ui.common.sdkCall
import ai.instavision.sandbox.ui.common.userMessage
import ai.instavision.guardian.sdk.InstaVision
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Refused locally so an empty form never costs a network round trip. */
private const val EMPTY_EMAIL_MESSAGE = "Enter the email address on your account"

/** Everything [ForgotPasswordScreen] draws. */
data class ForgotPasswordUiState(
  /** Address the reset link should be sent to. */
  val email: String = "",
  /** True while the reset request is in flight. */
  val loading: Boolean = false,
  /** Banner-level failure from the SDK, or null when there is nothing to report. */
  val error: String? = null,
  /** True once the backend has accepted the request; the screen then shows a confirmation. */
  val sent: Boolean = false,
)

/**
 * Requests a password reset mail through `UserServices.resetPassword`. Note that the SDK declares
 * that call with `onError` before `onSuccess`, which is why every argument here is named.
 */
class ForgotPasswordViewModel : ViewModel() {
  /** Mutable backing state, only ever updated from this ViewModel. */
  private val _state = MutableStateFlow(ForgotPasswordUiState())

  /** State the screen collects with `collectAsStateWithLifecycle`. */
  val state: StateFlow<ForgotPasswordUiState> = _state.asStateFlow()

  /** Records e-mail edits and clears the previous failure. */
  fun onEmailChange(value: String) {
    _state.update { it.copy(email = value, error = null) }
  }

  /** Sends the reset link, refusing an empty address without calling the SDK. */
  fun sendResetLink() {
    val email = _state.value.email.trim()
    if (email.isEmpty()) {
      _state.update { it.copy(error = EMPTY_EMAIL_MESSAGE) }
      return
    }
    viewModelScope.launch {
      _state.update { it.copy(loading = true, error = null) }
      sdkCall<Unit> { onSuccess, onError ->
        InstaVision.userServices.resetPassword(
          email = email,
          onError = onError,
          onSuccess = { onSuccess(Unit) },
        )
      }
        .onSuccess {
          _state.update { current -> current.copy(loading = false, sent = true) }
        }
        .onFailure { failure ->
          _state.update { current -> current.copy(loading = false, error = failure.userMessage()) }
        }
    }
  }
}
