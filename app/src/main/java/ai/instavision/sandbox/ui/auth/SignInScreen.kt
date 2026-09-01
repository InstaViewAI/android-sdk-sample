package ai.instavision.sandbox.ui.auth

import ai.instavision.sandbox.ui.common.AppTextField
import ai.instavision.sandbox.ui.common.DetailScaffold
import ai.instavision.sandbox.ui.common.ErrorBanner
import ai.instavision.sandbox.ui.common.LabelledDivider
import ai.instavision.sandbox.ui.common.PasswordField
import ai.instavision.sandbox.ui.common.PrimaryButton
import ai.instavision.sandbox.ui.common.SecondaryButton
import ai.instavision.sandbox.ui.common.TextLink
import ai.instavision.sandbox.ui.theme.AppTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Sign-in form for returning visitors, offering e-mail and password alongside Google. The Google
 * half runs through Credential Manager, so it needs the Activity that `LocalContext` provides here.
 */
@Composable
fun SignInScreen(
  onBack: () -> Unit,
  onSignedIn: () -> Unit,
  onSignUp: () -> Unit,
  onForgotPassword: () -> Unit,
) {
  val viewModel: SignInViewModel = viewModel()
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = LocalContext.current

  LaunchedEffect(state.signedIn) {
    if (state.signedIn) {
      viewModel.onNavigationHandled()
      onSignedIn()
    }
  }

  DetailScaffold(title = "", onBack = onBack) {
    Column(verticalArrangement = Arrangement.spacedBy(space = HeroSpacing)) {
      Text(
        text = "Welcome back",
        style = MaterialTheme.typography.headlineMedium,
        color = AppTheme.colors.textPrimary,
      )
      Text(
        text = "Sign in to reach your spaces and cameras.",
        style = MaterialTheme.typography.bodyLarge,
        color = AppTheme.colors.textSecondary,
      )
    }
    ErrorBanner(message = state.error)
    AppTextField(
      value = state.email,
      onValueChange = viewModel::onEmailChange,
      placeholder = "Email",
      keyboardType = KeyboardType.Email,
      enabled = !state.loading,
    )
    PasswordField(
      value = state.password,
      onValueChange = viewModel::onPasswordChange,
      placeholder = "Password",
      enabled = !state.loading,
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.End,
    ) {
      TextLink(
        text = "Forgot password?",
        onClick = onForgotPassword,
        enabled = !state.loading,
      )
    }
    PrimaryButton(
      text = "Sign in",
      onClick = viewModel::signIn,
      enabled = state.canSubmit,
      loading = state.pending == SignInMethod.Password,
    )
    LabelledDivider(label = "or")
    SecondaryButton(
      text = "Continue with Google",
      onClick = { viewModel.signInWithGoogle(context = context) },
      enabled = !state.loading,
      icon = Icons.Outlined.Language,
    )
    AuthFooter(
      prompt = "New here? ",
      linkText = "Create an account",
      onClick = onSignUp,
    )
  }
}

/** Gap between the hero title and the line underneath it. */
private val HeroSpacing = 8.dp
