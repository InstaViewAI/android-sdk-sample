package ai.instavision.sandbox.ui.settings.device

import ai.instavision.sandbox.ui.common.ConfirmDialog
import ai.instavision.sandbox.ui.common.GroupCard
import ai.instavision.sandbox.ui.common.RowDivider
import ai.instavision.sandbox.ui.common.SecondaryButton
import ai.instavision.sandbox.ui.common.SectionHeader
import ai.instavision.sandbox.ui.common.SettingRow
import ai.instavision.sandbox.ui.settings.DEVICE_UNKNOWN_VALUE
import ai.instavision.sandbox.ui.settings.DeviceSettingsUiState
import ai.instavision.sandbox.ui.settings.DeviceSubScreen
import ai.instavision.guardian.sdk.data.entity.Device
import ai.instavision.guardian.sdk.data.entity.hasBattery
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Read-only hardware, network and storage readout for the selected camera, plus the one action
 * that belongs with it: erasing the SD card.
 */
@Composable
fun DeviceInfoScreen(onBack: () -> Unit) {
  var confirmFormat by remember { mutableStateOf(false) }

  DeviceSubScreen(title = "Camera info", onBack = onBack) { state, viewModel ->
    val device = state.device ?: return@DeviceSubScreen

    SectionHeader(text = "Hardware")
    HardwareCard(device = device, firmware = state.currentFirmware)
    SectionHeader(text = "Network")
    NetworkCard(device = device)
    SectionHeader(text = "Storage")
    StorageCard(state = state)
    SecondaryButton(
      text = "Format SD card",
      onClick = { confirmFormat = true },
      enabled = !state.busy,
    )

    if (confirmFormat) {
      ConfirmDialog(
        title = "Format SD card?",
        message = "Every recording stored on the card is erased. Clips already uploaded to the " +
          "cloud are not affected.",
        confirmLabel = "Format",
        onConfirm = {
          confirmFormat = false
          viewModel.formatSdCard()
        },
        onDismiss = { confirmFormat = false },
      )
    }
  }
}

/** What the camera is and what it runs; the battery line is dropped for mains-only cameras. */
@Composable
private fun HardwareCard(device: Device, firmware: String) {
  GroupCard {
    SettingRow(label = "Model", value = device.modelName)
    RowDivider()
    SettingRow(label = "Firmware", value = firmware.ifEmpty { DEVICE_UNKNOWN_VALUE })
    if (device.hasBattery()) {
      RowDivider()
      SettingRow(label = "Battery", value = batteryLabel(device = device))
    }
  }
}

/** The network the camera is joined to and how well it hears it. */
@Composable
private fun NetworkCard(device: Device) {
  GroupCard {
    SettingRow(
      label = "Wi-Fi",
      value = device.deviceState.wifiName.ifEmpty { DEVICE_UNKNOWN_VALUE },
    )
    RowDivider()
    SettingRow(label = "Signal", value = device.deviceState.rssi ?: DEVICE_UNKNOWN_VALUE)
  }
}

/** SD card health, with the space line dropped when the camera has no card in it. */
@Composable
private fun StorageCard(state: DeviceSettingsUiState) {
  GroupCard {
    SettingRow(label = "SD card", value = state.sdCardStatus.ifEmpty { DEVICE_UNKNOWN_VALUE })
    if (state.sdCardUsage.isNotEmpty()) {
      RowDivider()
      SettingRow(label = "Space", value = state.sdCardUsage)
    }
  }
}

/** Charge level of a battery camera, noting when it is plugged in and filling back up. */
private fun batteryLabel(device: Device): String {
  val percentage = "${device.deviceState.batteryPercentage}%"
  return if (device.deviceState.charging == true) "$percentage · Charging" else percentage
}
