package ai.instavision.sandbox.ui.pairing

import ai.instavision.sandbox.ui.common.AppTextField
import ai.instavision.sandbox.ui.common.ErrorBanner
import ai.instavision.sandbox.ui.common.GroupCard
import ai.instavision.sandbox.ui.common.LoadingBox
import ai.instavision.sandbox.ui.common.PasswordField
import ai.instavision.sandbox.ui.common.PrimaryButton
import ai.instavision.sandbox.ui.common.RowDivider
import ai.instavision.sandbox.ui.common.TextLink
import ai.instavision.sandbox.ui.theme.AppTheme
import ai.instavision.guardian.sdk.data.entity.ble.WifiNetwork
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.SimCardAlert
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** The Bluetooth handshake, which runs itself and only reports which part of it is underway. */
@Composable
internal fun ConnectingPage(
  state: PairCameraUiState,
  onBack: () -> Unit,
  onExit: () -> Unit,
) {
  WizardPage(
    step = PairPage.Connecting.wizardStep,
    title = "Connecting",
    subtitle = "Hold still — we are talking to ${state.selectedCamera}.",
    onBack = onBack,
    onExit = onExit,
  ) {
    ErrorBanner(message = state.error)
    WizardWait(status = handshakeStatus(step = state.step))
  }
}

/**
 * The networks the camera can reach. Only a camera on the other end of a Bluetooth link ever
 * reports a list, so the QR path opens on the manual field rather than waiting for one that is
 * never coming, and waits there while its pairing session is opened.
 */
@Composable
internal fun ChooseNetworkPage(
  state: PairCameraUiState,
  onBack: () -> Unit,
  onExit: () -> Unit,
  onSelect: (WifiNetwork) -> Unit,
  onManual: (String) -> Unit,
) {
  val overBluetooth = state.step == PairingStep.WifiScan
  var manual by remember { mutableStateOf(!overBluetooth) }
  var ssid by remember { mutableStateOf("") }
  WizardPage(
    step = PairPage.ChooseNetwork.wizardStep,
    title = "Choose a network",
    subtitle = "These are the networks your camera can reach. Cameras join 2.4 GHz networks only.",
    onBack = onBack,
    onExit = onExit,
    bottom = {
      if (manual) {
        PrimaryButton(
          text = "Continue",
          onClick = { onManual(ssid.trim()) },
          enabled = ssid.isNotBlank() && !state.busy,
        )
      }
    },
  ) {
    ErrorBanner(message = state.error)
    if (state.networks.isNotEmpty()) {
      GroupCard {
        state.networks.forEachIndexed { index, network ->
          if (index > 0) RowDivider()
          NetworkRow(network = network, onClick = { onSelect(network) })
        }
      }
    } else if (overBluetooth || state.busy) {
      LoadingBox()
    }
    if (manual) {
      AppTextField(
        value = ssid,
        onValueChange = { value -> ssid = value },
        placeholder = "Network name",
      )
    } else {
      TextLink(text = "Enter a network manually", onClick = { manual = true })
    }
  }
}

/** The password for the chosen network, and whether to offer it again for the next camera. */
@Composable
internal fun WifiDetailsPage(
  state: PairCameraUiState,
  onBack: () -> Unit,
  onExit: () -> Unit,
  onSsidChange: (String) -> Unit,
  onPasswordChange: (String) -> Unit,
  onRememberChange: (Boolean) -> Unit,
  onContinue: () -> Unit,
) {
  WizardPage(
    step = PairPage.WifiDetails.wizardStep,
    title = "Wi-Fi details",
    subtitle = "Your camera will use this network. It stays on your device and is sent straight " +
      "to the camera.",
    onBack = onBack,
    onExit = onExit,
    bottom = {
      PrimaryButton(
        text = "Continue",
        onClick = onContinue,
        enabled = state.selectedSsid.isNotBlank(),
        loading = state.busy,
      )
    },
  ) {
    ErrorBanner(message = state.error)
    AppTextField(
      value = state.selectedSsid,
      onValueChange = onSsidChange,
      placeholder = "Network name",
    )
    PasswordField(
      value = state.password,
      onValueChange = onPasswordChange,
      placeholder = "Password",
    )
    RememberRow(checked = state.rememberPassword, onCheckedChange = onRememberChange)
  }
}

/** What a mobile-data camera needs instead of a network to join, before its SIM is asked for. */
@Composable
internal fun SimIntroPage(
  onBack: () -> Unit,
  onExit: () -> Unit,
  onContinue: () -> Unit,
) {
  WizardPage(
    step = PairPage.SimIntro.wizardStep,
    title = "This camera uses mobile data",
    subtitle = "It joins the network through its own SIM, so there is no Wi-Fi to choose.",
    onBack = onBack,
    onExit = onExit,
    bottom = { PrimaryButton(text = "Continue", onClick = onContinue) },
  ) {
    WizardEmblem(icon = Icons.Outlined.SignalCellularAlt)
    WizardTips(
      tips = listOf(
        "Slide the SIM that came with the camera into the tray on its underside.",
        "Keep the card itself to hand — its number is what you enter next.",
        "Leave the camera powered on while the SIM is activated.",
      ),
    )
  }
}

/** The SIM number, which the backend checks before the camera is allowed onto mobile data. */
@Composable
internal fun SimNumberPage(
  state: PairCameraUiState,
  onBack: () -> Unit,
  onExit: () -> Unit,
  onSimNumberChange: (String) -> Unit,
  onContinue: () -> Unit,
) {
  WizardPage(
    step = PairPage.SimNumber.wizardStep,
    title = "Enter the SIM number",
    subtitle = "It is the long number printed on the SIM card, sometimes labelled ICCID.",
    onBack = onBack,
    onExit = onExit,
    bottom = {
      PrimaryButton(
        text = "Continue",
        onClick = onContinue,
        enabled = state.simNumber.isNotBlank(),
        loading = state.busy,
      )
    },
  ) {
    ErrorBanner(message = state.error)
    AppTextField(
      value = state.simNumber,
      onValueChange = onSimNumberChange,
      placeholder = "SIM number",
      keyboardType = KeyboardType.Number,
    )
  }
}

/** The backend refused the SIM number, with its own reason and a way back to the field. */
@Composable
internal fun SimInvalidPage(
  state: PairCameraUiState,
  onBack: () -> Unit,
  onExit: () -> Unit,
  onRetry: () -> Unit,
) {
  WizardPage(
    step = PairPage.SimInvalid.wizardStep,
    title = "That SIM was not accepted",
    subtitle = "The number could not be used for this camera.",
    onBack = onBack,
    onExit = onExit,
    bottom = { PrimaryButton(text = "Enter it again", onClick = onRetry) },
  ) {
    WizardEmblem(icon = Icons.Outlined.SimCardAlert, warning = true)
    ErrorBanner(message = state.error)
  }
}

/** The wait while the SIM is activated for this home and the pairing session is opened. */
@Composable
internal fun SimActivatingPage(
  state: PairCameraUiState,
  onBack: () -> Unit,
  onExit: () -> Unit,
) {
  WizardPage(
    step = PairPage.SimActivating.wizardStep,
    title = "Activating the SIM",
    subtitle = "Hold still — we are setting ${state.selectedCamera} up on mobile data.",
    onBack = onBack,
    onExit = onExit,
  ) {
    ErrorBanner(message = state.error)
    WizardWait(status = "Activating the SIM…")
  }
}

/** One network of the list: its name, whether it is locked, and a way into the password step. */
@Composable
private fun NetworkRow(
  network: WifiNetwork,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(all = RowPadding),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(space = RowSpacing),
  ) {
    Icon(
      imageVector = Icons.Outlined.Wifi,
      contentDescription = null,
      tint = AppTheme.colors.accent,
      modifier = Modifier.size(RowIconSize),
    )
    Text(
      text = network.ssid,
      style = MaterialTheme.typography.bodyLarge,
      color = AppTheme.colors.textPrimary,
      modifier = Modifier.weight(1f),
    )
    if (network.isSecured()) {
      Icon(
        imageVector = Icons.Outlined.Lock,
        contentDescription = "Secured",
        tint = AppTheme.colors.textTertiary,
        modifier = Modifier.size(LockIconSize),
      )
    }
    Icon(
      imageVector = Icons.Rounded.ChevronRight,
      contentDescription = null,
      tint = AppTheme.colors.textTertiary,
      modifier = Modifier.size(RowIconSize),
    )
  }
}

/** The offer to keep this password for the next camera, which nothing writes to disk. */
@Composable
private fun RememberRow(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(space = CheckboxSpacing),
  ) {
    Checkbox(
      checked = checked,
      onCheckedChange = onCheckedChange,
      colors = CheckboxDefaults.colors(
        checkedColor = AppTheme.colors.accent,
        uncheckedColor = AppTheme.colors.outline,
        checkmarkColor = AppTheme.colors.textPrimary,
      ),
    )
    Text(
      text = "Remember this password for the next camera",
      style = MaterialTheme.typography.bodyLarge,
      color = AppTheme.colors.textSecondary,
    )
  }
}

/** Which part of the Bluetooth handshake the camera is currently being asked for. */
private fun handshakeStatus(step: PairingStep): String = when (step) {
  PairingStep.Discovering -> "Asking the camera to scan for networks…"
  PairingStep.SendingWifi -> "Sending the network details…"
  else -> "Connecting to the camera…"
}

/**
 * Whether a network needs a password. The camera reports one protocol byte per network and uses
 * zero for an open one, so anything else is treated as secured.
 */
private fun WifiNetwork.isSecured(): Boolean = protocol != 0

/** Padding inside a network row. */
private val RowPadding = 16.dp

/** Gap between the parts of a network row. */
private val RowSpacing = 12.dp

/** Size of a network row's leading glyph and its trailing chevron. */
private val RowIconSize = 22.dp

/** Size of the padlock that marks a secured network. */
private val LockIconSize = 16.dp

/** Gap between the remember checkbox and its label. */
private val CheckboxSpacing = 4.dp
