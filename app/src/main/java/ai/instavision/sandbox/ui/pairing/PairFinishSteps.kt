package ai.instavision.sandbox.ui.pairing

import ai.instavision.sandbox.ui.common.AppTextField
import ai.instavision.sandbox.ui.common.ErrorBanner
import ai.instavision.sandbox.ui.common.GroupCard
import ai.instavision.sandbox.ui.common.PrimaryButton
import ai.instavision.sandbox.ui.common.RowDivider
import ai.instavision.sandbox.ui.common.SectionHeader
import ai.instavision.sandbox.ui.common.SelectableChip
import ai.instavision.sandbox.ui.common.SettingRow
import ai.instavision.sandbox.ui.theme.AppTheme
import ai.instavision.guardian.sdk.data.entity.Device
import ai.instavision.guardian.sdk.data.entity.formattedFirmwareVersion
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Names offered on the last step, which are the places a first camera usually ends up. */
private val NameSuggestions = listOf(
  "Front door",
  "Living room",
  "Back garden",
  "Driveway",
  "Garage",
  "Nursery",
)

/** Shown while the backend is polled for the camera it was told about, which can take minutes. */
@Composable
internal fun AddingPage(state: PairCameraUiState) {
  PlainPage {
    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(space = WaitSpacing),
    ) {
      WizardSpinner()
      Text(
        text = "Adding camera\nto your space",
        style = MaterialTheme.typography.headlineMedium,
        color = AppTheme.colors.textPrimary,
        textAlign = TextAlign.Center,
      )
      Text(
        text = "Keep the camera powered on. This can take up to two minutes.",
        style = MaterialTheme.typography.bodyMedium,
        color = AppTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
      )
      ErrorBanner(message = state.error)
    }
  }
}

/**
 * The camera is on the account. Naming it is the last thing setup asks for, and the readouts
 * underneath are what the backend now knows about it.
 */
@Composable
internal fun ConnectedPage(
  state: PairCameraUiState,
  onNameChange: (String) -> Unit,
  onFinish: () -> Unit,
) {
  PlainPage(
    bottom = {
      PrimaryButton(
        text = "Finish setup",
        onClick = onFinish,
        enabled = state.cameraName.isNotBlank(),
        loading = state.busy,
      )
    },
  ) {
    Box(
      modifier = Modifier
        .size(TickSize)
        .clip(CircleShape)
        .background(color = AppTheme.colors.success),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = Icons.Rounded.Check,
        contentDescription = null,
        tint = AppTheme.colors.ground,
        modifier = Modifier.size(TickIconSize),
      )
    }
    Column(verticalArrangement = Arrangement.spacedBy(space = TitleSpacing)) {
      Text(
        text = "Camera connected",
        style = MaterialTheme.typography.headlineMedium,
        color = AppTheme.colors.textPrimary,
      )
      Text(
        text = "Give it a name so you can tell it apart from your other cameras.",
        style = MaterialTheme.typography.bodyLarge,
        color = AppTheme.colors.textSecondary,
      )
    }
    ErrorBanner(message = state.error)
    AppTextField(
      value = state.cameraName,
      onValueChange = onNameChange,
      placeholder = "Camera name",
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(space = ChipSpacing)) {
      NameSuggestions.forEach { suggestion ->
        SelectableChip(
          label = suggestion,
          selected = state.cameraName == suggestion,
          onClick = { onNameChange(suggestion) },
        )
      }
    }
    Column {
      SectionHeader(text = "Camera")
      GroupCard {
        SettingRow(label = "Model", value = state.pairedDevice.modelLabel(state = state))
        RowDivider()
        SettingRow(label = "Firmware", value = state.pairedDevice.firmwareLabel())
        RowDivider()
        SettingRow(label = "Network", value = state.pairedDevice.networkLabel())
      }
    }
  }
}

/** The flow stopped before the camera was added, with the reason it stopped and a way to retry. */
@Composable
internal fun StoppedPage(
  state: PairCameraUiState,
  onExit: () -> Unit,
  onStartOver: () -> Unit,
) {
  WizardPage(
    step = state.stoppedStep,
    title = "Setup stopped",
    subtitle = "Nothing was added, so running through setup again is safe.",
    onBack = onStartOver,
    onExit = onExit,
    bottom = { PrimaryButton(text = "Start over", onClick = onStartOver) },
  ) {
    WizardEmblem(icon = Icons.Outlined.ErrorOutline, warning = true)
    ErrorBanner(message = state.error)
  }
}

/** The camera's model, falling back to the model a scanned code identified. */
private fun Device?.modelLabel(state: PairCameraUiState): String =
  this?.modelName ?: state.deviceModel?.modelName ?: NOT_REPORTED

/** The firmware the camera reported when it registered itself. */
private fun Device?.firmwareLabel(): String = this?.formattedFirmwareVersion() ?: NOT_REPORTED

/** The network the camera joined, which a mobile-data camera never reports. */
private fun Device?.networkLabel(): String =
  this?.deviceState?.wifiName?.takeIf { name -> name.isNotBlank() } ?: NOT_REPORTED

/** Stand-in for a readout the camera has not published yet. */
private const val NOT_REPORTED = "Not reported"

/** Diameter of the tick that opens the last step. */
private val TickSize = 72.dp

/** Size of the tick glyph inside its circle. */
private val TickIconSize = 40.dp

/** Gap between the last step's title and the line under it. */
private val TitleSpacing = 8.dp

/** Gap between two suggestion chips. */
private val ChipSpacing = 8.dp

/** Gap between the spinner and the lines under it while the camera is being added. */
private val WaitSpacing = 24.dp
