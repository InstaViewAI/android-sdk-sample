package ai.instavision.sandbox.ui.settings

import ai.instavision.sandbox.ui.common.AppDropdownField
import ai.instavision.sandbox.ui.common.AppTextField
import ai.instavision.sandbox.ui.common.ConfirmDialog
import ai.instavision.sandbox.ui.common.DestructiveButton
import ai.instavision.sandbox.ui.common.DetailScaffold
import ai.instavision.sandbox.ui.common.ErrorBanner
import ai.instavision.sandbox.ui.common.GroupCard
import ai.instavision.sandbox.ui.common.LoadingBox
import ai.instavision.sandbox.ui.common.Notice
import ai.instavision.sandbox.ui.common.PrimaryButton
import ai.instavision.sandbox.ui.common.SecondaryButton
import ai.instavision.sandbox.ui.common.SectionHeader
import ai.instavision.sandbox.ui.common.SettingRow
import ai.instavision.sandbox.ui.common.StatusPill
import ai.instavision.sandbox.ui.theme.AppTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

/** How long a "saved" confirmation stays on screen before it clears itself. */
private const val ACCOUNT_NOTICE_DURATION_MS = 2500L

/** Width of the country calling code dropdown, sized to hold the widest code plus its chevron. */
private val CodeFieldWidth = 110.dp

/** Gap between two fields sharing a row. */
private val FieldSpacing = 12.dp

/**
 * The signed-in account's own details: the sign-in email and its verification state, the name and
 * phone number, and deleting the account. [onSignedOut] fires once the account is actually gone.
 */
@Composable
fun AccountSettingsScreen(onBack: () -> Unit, onSignedOut: () -> Unit) {
  val viewModel: AccountSettingsViewModel = viewModel()
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var confirmDelete by remember { mutableStateOf(false) }

  LaunchedEffect(state.signedOut) { if (state.signedOut) onSignedOut() }
  LaunchedEffect(state.notice) {
    if (state.notice != null) {
      delay(ACCOUNT_NOTICE_DURATION_MS)
      viewModel.dismissNotice()
    }
  }

  DetailScaffold(title = "My account", onBack = onBack) {
    ErrorBanner(message = state.error)
    Notice(message = state.notice)
    if (state.loading) {
      LoadingBox()
    } else {
      EmailSection(
        email = state.email,
        verified = state.emailVerified,
        enabled = !state.busy,
        onResendVerification = viewModel::resendVerification,
      )
      NameFields(
        state = state,
        onFirstNameChange = viewModel::onFirstNameChange,
        onLastNameChange = viewModel::onLastNameChange,
      )
      PhoneFields(
        state = state,
        onCountryCodeChange = viewModel::onCountryCodeChange,
        onPhoneChange = viewModel::onPhoneChange,
      )
      PrimaryButton(
        text = "Save changes",
        onClick = viewModel::saveChanges,
        loading = state.busy,
      )
      DestructiveButton(
        text = "Delete my account",
        onClick = { confirmDelete = true },
        enabled = !state.busy,
      )
    }
  }

  if (confirmDelete) {
    ConfirmDialog(
      title = "Delete your account?",
      message = "Your homes, cameras and recordings are deleted along with the account. There " +
        "is no way to get them back.",
      confirmLabel = "Delete",
      onConfirm = {
        confirmDelete = false
        viewModel.deleteAccount()
      },
      onDismiss = { confirmDelete = false },
    )
  }
}

/** The sign-in address and whether it has been confirmed, with the way to re-send the email. */
@Composable
private fun EmailSection(
  email: String,
  verified: Boolean,
  enabled: Boolean,
  onResendVerification: () -> Unit,
) {
  SectionHeader(text = "Sign-in email")
  GroupCard {
    SettingRow(
      label = email,
      trailing = {
        if (verified) {
          StatusPill(
            text = "Verified",
            containerColor = AppTheme.colors.successContainer,
            contentColor = AppTheme.colors.success,
          )
        } else {
          StatusPill(
            text = "Not verified",
            containerColor = AppTheme.colors.warningContainer,
            contentColor = AppTheme.colors.warning,
          )
        }
      },
    )
  }
  if (!verified) {
    SecondaryButton(
      text = "Resend verification email",
      onClick = onResendVerification,
      enabled = enabled,
    )
  }
}

/** Given and family name, which the service takes together as one value. */
@Composable
private fun NameFields(
  state: AccountSettingsUiState,
  onFirstNameChange: (String) -> Unit,
  onLastNameChange: (String) -> Unit,
) {
  Row(horizontalArrangement = Arrangement.spacedBy(FieldSpacing)) {
    AppTextField(
      value = state.firstName,
      onValueChange = onFirstNameChange,
      placeholder = "First name",
      enabled = !state.busy,
      modifier = Modifier.weight(1f),
    )
    AppTextField(
      value = state.lastName,
      onValueChange = onLastNameChange,
      placeholder = "Last name",
      enabled = !state.busy,
      modifier = Modifier.weight(1f),
    )
  }
}

/** Country calling code beside the national part, sent to the service as one phone number. */
@Composable
private fun PhoneFields(
  state: AccountSettingsUiState,
  onCountryCodeChange: (String) -> Unit,
  onPhoneChange: (String) -> Unit,
) {
  Row(horizontalArrangement = Arrangement.spacedBy(FieldSpacing)) {
    AppDropdownField(
      value = state.countryCode,
      options = state.countryCodes,
      onSelect = onCountryCodeChange,
      placeholder = "Code",
      enabled = !state.busy,
      modifier = Modifier.width(CodeFieldWidth),
    )
    AppTextField(
      value = state.phone,
      onValueChange = onPhoneChange,
      placeholder = "Phone number",
      enabled = !state.busy,
      keyboardType = KeyboardType.Phone,
      modifier = Modifier.weight(1f),
    )
  }
}
