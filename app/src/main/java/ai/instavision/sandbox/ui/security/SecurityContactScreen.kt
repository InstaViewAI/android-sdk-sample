package ai.instavision.sandbox.ui.security

import ai.instavision.sandbox.ui.common.AppDropdownField
import ai.instavision.sandbox.ui.common.AppTextField
import ai.instavision.sandbox.ui.common.DetailScaffold
import ai.instavision.sandbox.ui.common.ErrorBanner
import ai.instavision.sandbox.ui.common.FieldError
import ai.instavision.sandbox.ui.common.InfoNote
import ai.instavision.sandbox.ui.common.LoadingBox
import ai.instavision.sandbox.ui.common.PrimaryButton
import ai.instavision.sandbox.ui.common.ScreenPadding
import ai.instavision.sandbox.ui.common.TextLink
import ai.instavision.sandbox.ui.theme.AppTheme
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** Why the form is fussier than a normal address form. */
private const val HERO_BODY =
  "This is the address a dispatcher is sent to, so it has to be exact."

/** What the optional cross street is actually for, which is not obvious from the field alone. */
private const val CROSS_STREET_NOTE =
  "Cross streets help responders find you faster in rural areas and large complexes."

/** Why the number is asked for at all, and why it cannot be skipped. */
private const val PHONE_BODY =
  "The monitoring centre calls this number before it dispatches, so it has to reach someone."

/** What the zone list failing means for the user, who can otherwise only see an empty dropdown. */
private const val TIME_ZONE_FAILED =
  "Time zones could not be loaded, and the address cannot be saved without one."

/**
 * The contact information step of the monitoring checklist: the address a dispatcher is sent to,
 * then the number the monitoring centre calls. Saving the address creates the home's monitoring
 * profile when there is not one yet; the step only completes once the number is verified.
 */
@Composable
fun SecurityContactScreen(onBack: () -> Unit, onDone: () -> Unit, standalone: Boolean = false) {
  val viewModel: SecurityContactViewModel = viewModel()
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(state.done) { if (state.done) onDone() }
  BackHandler(enabled = state.stage != ContactStage.Address) { viewModel.back() }

  DetailScaffold(
    title = if (standalone) "Personal information" else "Contact information",
    onBack = { if (!viewModel.back()) onBack() },
    bottomBar = {
      Box(
        modifier = Modifier
          .navigationBarsPadding()
          .padding(horizontal = ScreenPadding, vertical = BottomBarPadding),
      ) {
        PrimaryButton(
          text = advanceLabel(state),
          onClick = { viewModel.onAdvance(markStep = !standalone) },
          enabled = state.canAdvance && !state.busy,
          loading = state.busy,
        )
      }
    },
  ) {
    ErrorBanner(message = state.error)
    if (state.loading) {
      LoadingBox()
    } else {
      when (state.stage) {
        ContactStage.Address -> {
          Hero(title = "Where are we protecting?", body = HERO_BODY)
          AddressForm(
            state = state,
            onStreetChange = viewModel::onStreetChange,
            onCrossStreetChange = viewModel::onCrossStreetChange,
            onCityChange = viewModel::onCityChange,
            onStateChange = viewModel::onStateChange,
            onZipChange = viewModel::onZipChange,
            onTimeZoneChange = viewModel::onTimeZoneChange,
            onRetryTimeZones = viewModel::retryTimeZones,
          )
          InfoNote(text = CROSS_STREET_NOTE)
        }

        ContactStage.Phone -> PhoneStage(
          state = state,
          onPhoneChange = viewModel::onPhoneChange,
        )

        ContactStage.Verify -> VerifyStage(
          state = state,
          onOtpChange = viewModel::onOtpChange,
          onResend = viewModel::resendCode,
        )
      }
    }
  }
}

/** Wording of the bottom-bar button, which runs a different request on each panel. */
private fun advanceLabel(state: SecurityContactUiState): String = when (state.stage) {
  ContactStage.Address -> "Continue"
  ContactStage.Phone -> "Send code"
  ContactStage.Verify -> "Verify"
}

/** How long the user still has to wait before another code can be asked for. */
private fun resendLabel(secondsLeft: Int): String =
  if (secondsLeft == 0) "Resend code" else "Resend code in ${secondsLeft}s"

/** The second panel: the number the monitoring centre dispatches against. */
@Composable
private fun PhoneStage(state: SecurityContactUiState, onPhoneChange: (String) -> Unit) {
  Hero(title = "Confirm your phone number", body = PHONE_BODY)
  AppTextField(
    value = state.phone,
    onValueChange = onPhoneChange,
    placeholder = "Phone number",
    enabled = !state.busy,
    keyboardType = KeyboardType.Number,
    leading = {
      Text(
        text = state.dialCode,
        style = MaterialTheme.typography.bodyLarge,
        color = AppTheme.colors.textSecondary,
        modifier = Modifier.padding(start = FieldGap),
      )
    },
  )
}

/** The third panel: the texted code, and the link that asks for another one. */
@Composable
private fun VerifyStage(
  state: SecurityContactUiState,
  onOtpChange: (String) -> Unit,
  onResend: () -> Unit,
) {
  Hero(
    title = "Verify your phone number",
    body = "We texted a 6-digit code to ${state.dialCode} ${state.phone}.",
  )
  AppTextField(
    value = state.otp,
    onValueChange = onOtpChange,
    placeholder = "6-digit code",
    enabled = !state.busy,
    keyboardType = KeyboardType.Number,
  )
  TextLink(
    text = resendLabel(state.resendIn),
    onClick = onResend,
    enabled = state.canResend && !state.busy,
  )
}

/** The headline that tells the user what the panel in front of them is for. */
@Composable
private fun Hero(title: String, body: String) {
  Column(verticalArrangement = Arrangement.spacedBy(HeroGap)) {
    Text(
      text = title,
      style = MaterialTheme.typography.headlineMedium,
      color = AppTheme.colors.textPrimary,
    )
    Text(
      text = body,
      style = MaterialTheme.typography.bodyLarge,
      color = AppTheme.colors.textSecondary,
    )
  }
}

/** The address itself. The country is a dropdown of one, because monitoring is US-only. */
@Composable
private fun AddressForm(
  state: SecurityContactUiState,
  onStreetChange: (String) -> Unit,
  onCrossStreetChange: (String) -> Unit,
  onCityChange: (String) -> Unit,
  onStateChange: (String) -> Unit,
  onZipChange: (String) -> Unit,
  onTimeZoneChange: (String) -> Unit,
  onRetryTimeZones: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(FieldGap)) {
    AppTextField(
      value = state.street,
      onValueChange = onStreetChange,
      placeholder = "Street address",
      enabled = !state.busy,
    )
    AppTextField(
      value = state.crossStreet,
      onValueChange = onCrossStreetChange,
      placeholder = "Cross street (optional)",
      enabled = !state.busy,
    )
    AppTextField(
      value = state.city,
      onValueChange = onCityChange,
      placeholder = "City",
      enabled = !state.busy,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(FieldGap)) {
      AppDropdownField(
        value = state.stateName,
        options = state.stateNames,
        onSelect = onStateChange,
        placeholder = "State",
        modifier = Modifier.weight(1f),
        enabled = !state.busy && state.stateNames.isNotEmpty(),
      )
      AppTextField(
        value = state.zip,
        onValueChange = onZipChange,
        placeholder = "ZIP",
        modifier = Modifier.weight(1f),
        enabled = !state.busy,
        keyboardType = KeyboardType.Number,
      )
    }
    AppDropdownField(
      value = state.country,
      options = listOf(state.country),
      onSelect = {},
      placeholder = "Country",
      enabled = !state.busy,
    )
    TimeZoneField(state = state, onSelect = onTimeZoneChange, onRetry = onRetryTimeZones)
  }
}

/**
 * The zone the address sits in, which the backend rejects the profile without. It is never
 * guessed: a device zone the backend does not offer leaves this empty for the user to fill.
 */
@Composable
private fun TimeZoneField(
  state: SecurityContactUiState,
  onSelect: (String) -> Unit,
  onRetry: () -> Unit,
) {
  AppDropdownField(
    value = state.timeZoneName,
    options = state.timeZoneNames,
    onSelect = onSelect,
    placeholder = "Time zone",
    enabled = !state.busy && state.timeZoneNames.isNotEmpty(),
  )
  if (state.timeZonesFailed) {
    FieldError(message = TIME_ZONE_FAILED)
    TextLink(text = "Try again", onClick = onRetry, enabled = !state.busy)
  }
}

/** Gap between the hero's headline and the line under it. */
private val HeroGap = 8.dp

/** Gap between two fields, and between the two halves of the state and ZIP row. */
private val FieldGap = 12.dp

/** Breathing room above and below the screen's bottom-bar button. */
private val BottomBarPadding = 12.dp
