package ai.instavision.sandbox.ui.settings

import ai.instavision.sandbox.ui.common.DetailScaffold
import ai.instavision.sandbox.ui.common.ErrorBanner
import ai.instavision.sandbox.ui.common.FieldError
import ai.instavision.sandbox.ui.common.PasswordField
import ai.instavision.sandbox.ui.common.PrimaryButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Replaces the account password. [onDone] fires once the service has accepted the new one, which
 * is the only signal available — the SDK does not report anything else back.
 */
@Composable
fun ChangePasswordScreen(onBack: () -> Unit, onDone: () -> Unit) {
  val viewModel: ChangePasswordViewModel = viewModel()
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(state.changed) { if (state.changed) onDone() }

  DetailScaffold(title = "Change password", onBack = onBack) {
    ErrorBanner(message = state.error)
    PasswordField(
      value = state.currentPassword,
      onValueChange = viewModel::onCurrentPasswordChange,
      placeholder = "Current password",
      enabled = !state.busy,
    )
    PasswordField(
      value = state.newPassword,
      onValueChange = viewModel::onNewPasswordChange,
      placeholder = "New password",
      enabled = !state.busy,
    )
    PasswordField(
      value = state.confirmPassword,
      onValueChange = viewModel::onConfirmPasswordChange,
      placeholder = "Confirm new password",
      enabled = !state.busy,
    )
    FieldError(message = "Passwords do not match".takeIf { state.mismatch })
    PrimaryButton(
      text = "Update password",
      onClick = viewModel::submit,
      enabled = state.canSubmit,
      loading = state.busy,
    )
  }
}
