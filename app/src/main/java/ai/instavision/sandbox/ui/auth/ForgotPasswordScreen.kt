package ai.instavision.sandbox.ui.auth

import ai.instavision.sandbox.ui.common.AppTextField
import ai.instavision.sandbox.ui.common.DetailScaffold
import ai.instavision.sandbox.ui.common.EmptyState
import ai.instavision.sandbox.ui.common.ErrorBanner
import ai.instavision.sandbox.ui.common.PrimaryButton
import ai.instavision.sandbox.ui.theme.AppTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Password recovery form. Once the backend accepts the request the form is replaced by a
 * confirmation rather than navigating straight away, so the visitor can actually read it before
 * tapping through to [onDone].
 */
@Composable
fun ForgotPasswordScreen(
  onBack: () -> Unit,
  onDone: () -> Unit,
) {
  val viewModel: ForgotPasswordViewModel = viewModel()
  val state by viewModel.state.collectAsStateWithLifecycle()

  DetailScaffold(title = "Reset your password", onBack = onBack) {
    if (state.sent) {
      SentConfirmation(email = state.email.trim(), onDone = onDone)
    } else {
      Text(
        text = "Enter the email on your account and we will send you a link to choose a new " +
          "password.",
        style = MaterialTheme.typography.bodyMedium,
        color = AppTheme.colors.textSecondary,
      )
      ErrorBanner(message = state.error)
      AppTextField(
        value = state.email,
        onValueChange = viewModel::onEmailChange,
        placeholder = "Email",
        keyboardType = KeyboardType.Email,
        enabled = !state.loading,
      )
      PrimaryButton(
        text = "Send reset link",
        onClick = viewModel::sendResetLink,
        loading = state.loading,
      )
    }
  }
}

/** Success state shown in place of the form once the reset mail has been requested. */
@Composable
private fun SentConfirmation(
  email: String,
  onDone: () -> Unit,
) {
  EmptyState(
    title = "Check your inbox",
    body = "We sent a password reset link to $email. Follow it to choose a new password, then " +
      "sign in again.",
    icon = Icons.Outlined.MarkEmailRead,
    action = {
      PrimaryButton(text = "Back to sign in", onClick = onDone)
    },
  )
}
