package ai.instavision.sandbox.ui.auth

import ai.instavision.sandbox.ui.common.AppDropdownField
import ai.instavision.sandbox.ui.common.AppTextField
import ai.instavision.sandbox.ui.common.DetailScaffold
import ai.instavision.sandbox.ui.common.ErrorBanner
import ai.instavision.sandbox.ui.common.FieldError
import ai.instavision.sandbox.ui.common.LabelledDivider
import ai.instavision.sandbox.ui.common.PasswordField
import ai.instavision.sandbox.ui.common.PrimaryButton
import ai.instavision.sandbox.ui.common.SecondaryButton
import ai.instavision.sandbox.ui.theme.AppTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
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
 * Account creation form. Validates the e-mail, the password against [PasswordChecklist]'s rules and
 * the confirmation locally before handing anything to the SDK, then moves on to e-mail
 * verification. The Google half runs through Credential Manager, so it needs the Activity that
 * `LocalContext` provides here.
 */
@Composable
fun SignUpScreen(
  onBack: () -> Unit,
  onSignedUp: () -> Unit,
  onSignIn: () -> Unit,
) {
  val viewModel: SignUpViewModel = viewModel()
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = LocalContext.current

  LaunchedEffect(state.signedUp) {
    if (state.signedUp) {
      viewModel.onNavigationHandled()
      onSignedUp()
    }
  }

  DetailScaffold(title = "", onBack = onBack) {
    Column(verticalArrangement = Arrangement.spacedBy(space = HeroSpacing)) {
      Text(
        text = "Create your account",
        style = MaterialTheme.typography.headlineMedium,
        color = AppTheme.colors.textPrimary,
      )
      Text(
        text = "You will confirm your email address on the next step.",
        style = MaterialTheme.typography.bodyLarge,
        color = AppTheme.colors.textSecondary,
      )
    }
    ErrorBanner(message = state.error)
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(space = FieldSpacing),
    ) {
      AppTextField(
        value = state.firstName,
        onValueChange = viewModel::onFirstNameChange,
        placeholder = "First name",
        modifier = Modifier.weight(1f),
        enabled = !state.busy,
      )
      AppTextField(
        value = state.lastName,
        onValueChange = viewModel::onLastNameChange,
        placeholder = "Last name",
        modifier = Modifier.weight(1f),
        enabled = !state.busy,
      )
    }
    AppTextField(
      value = state.email,
      onValueChange = viewModel::onEmailChange,
      placeholder = "Email",
      keyboardType = KeyboardType.Email,
      enabled = !state.busy,
    )
    FieldError(message = state.emailError)
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(space = FieldSpacing),
    ) {
      AppDropdownField(
        value = state.countryCode,
        options = CountryCallingCodes,
        onSelect = viewModel::onCountryCodeChange,
        placeholder = "Code",
        modifier = Modifier.width(CountryCodeWidth),
        enabled = !state.busy,
      )
      AppTextField(
        value = state.phone,
        onValueChange = viewModel::onPhoneChange,
        placeholder = "Phone number",
        modifier = Modifier.weight(1f),
        keyboardType = KeyboardType.Phone,
        enabled = !state.busy,
      )
    }
    PasswordField(
      value = state.password,
      onValueChange = viewModel::onPasswordChange,
      placeholder = "Password",
      enabled = !state.busy,
    )
    FieldError(message = state.passwordError)
    PasswordField(
      value = state.confirmPassword,
      onValueChange = viewModel::onConfirmPasswordChange,
      placeholder = "Confirm password",
      enabled = !state.busy,
    )
    FieldError(message = state.confirmPasswordError)
    if (state.password.isNotEmpty()) {
      PasswordChecklist(password = state.password)
    }
    PrimaryButton(
      text = "Create account",
      onClick = viewModel::signUp,
      enabled = state.canSubmit,
      loading = state.loading,
    )
    LabelledDivider(label = "or")
    SecondaryButton(
      text = "Sign up with Google",
      onClick = { viewModel.signUpWithGoogle(context = context) },
      enabled = !state.busy,
      icon = Icons.Outlined.Language,
    )
    AuthFooter(
      prompt = "Already have an account? ",
      linkText = "Sign in",
      onClick = onSignIn,
    )
  }
}

/** Gap between the hero title and the line underneath it. */
private val HeroSpacing = 8.dp

/** Gap between two fields sharing a row. */
private val FieldSpacing = 12.dp

/** Width of the calling-code dropdown, sized for the longest code on offer. */
private val CountryCodeWidth = 112.dp
