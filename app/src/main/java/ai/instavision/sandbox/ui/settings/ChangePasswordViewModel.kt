package ai.instavision.sandbox.ui.settings

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

/** Shortest password the screen will submit, matching what the service accepts. */
private const val MIN_PASSWORD_LENGTH = 8

/** The three password fields and what the screen may do with them. */
data class ChangePasswordUiState(
  /** Current password, which the service re-authenticates with before accepting a new one. */
  val currentPassword: String = "",
  /** Replacement password. */
  val newPassword: String = "",
  /** Repeat of [newPassword], compared locally before anything is sent. */
  val confirmPassword: String = "",
  /** True while the change is in flight, which disables every control on the screen. */
  val busy: Boolean = false,
  /** Message from the last failed request. */
  val error: String? = null,
  /** Set once the service has accepted the new password so the screen can leave. */
  val changed: Boolean = false,
) {
  /** Whether the form is filled in well enough to be worth sending. */
  val canSubmit: Boolean
    get() = currentPassword.isNotBlank() &&
      newPassword.length >= MIN_PASSWORD_LENGTH &&
      newPassword == confirmPassword

  /** Whether both new-password fields are filled in and disagree, which the screen calls out. */
  val mismatch: Boolean
    get() = newPassword.isNotBlank() && confirmPassword.isNotBlank() &&
      newPassword != confirmPassword
}

/**
 * Replaces the signed-in account's password. The service re-authenticates with the current
 * password itself, so a wrong one comes back as an error rather than being caught here.
 */
class ChangePasswordViewModel : ViewModel() {
  private val _uiState = MutableStateFlow(ChangePasswordUiState())

  /** Single source of truth for [ChangePasswordScreen]. */
  val uiState = _uiState.asStateFlow()

  /** Records what the user typed into the current password field. */
  fun onCurrentPasswordChange(value: String) {
    _uiState.update { it.copy(currentPassword = value) }
  }

  /** Records what the user typed into the new password field. */
  fun onNewPasswordChange(value: String) {
    _uiState.update { it.copy(newPassword = value) }
  }

  /** Records what the user typed into the repeated password field. */
  fun onConfirmPasswordChange(value: String) {
    _uiState.update { it.copy(confirmPassword = value) }
  }

  /**
   * Submits the change, keyed by the cached account's email. The service re-authenticates with
   * that email, so there is nothing to send while no account is cached.
   */
  fun submit() {
    val state = _uiState.value
    if (!state.canSubmit) return
    val email = SessionStore.user?.email
    if (email == null) {
      _uiState.update { it.copy(error = "Sign in again to change your password") }
      return
    }
    _uiState.update { it.copy(busy = true, error = null) }
    viewModelScope.launch {
      sdkCall<Unit> { onSuccess, onError ->
        InstaVision.userServices.updatePassword(
          email = email,
          oldPassword = state.currentPassword,
          newPassword = state.newPassword,
          onSuccess = { onSuccess(Unit) },
          onError = onError,
        )
      }
        .onSuccess { _uiState.update { it.copy(busy = false, changed = true) } }
        .onFailure { error ->
          _uiState.update { it.copy(busy = false, error = error.userMessage()) }
        }
    }
  }
}
