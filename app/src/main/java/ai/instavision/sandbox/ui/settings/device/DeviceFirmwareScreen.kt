package ai.instavision.sandbox.ui.settings.device

import ai.instavision.sandbox.ui.common.GroupCard
import ai.instavision.sandbox.ui.common.PrimaryButton
import ai.instavision.sandbox.ui.common.RowDivider
import ai.instavision.sandbox.ui.common.SettingRow
import ai.instavision.sandbox.ui.common.TextLink
import ai.instavision.sandbox.ui.settings.DEVICE_UNKNOWN_VALUE
import ai.instavision.sandbox.ui.settings.DeviceSubScreen
import androidx.compose.runtime.Composable

/**
 * The firmware the camera runs against the newest it is entitled to, and the way to close the gap.
 * The update button doubles as the "nothing to do" readout when the two versions already match.
 */
@Composable
fun DeviceFirmwareScreen(onBack: () -> Unit) {
  DeviceSubScreen(title = "Firmware", onBack = onBack) { state, viewModel ->
    val updateAvailable = state.latestFirmware.isNotEmpty() &&
      !state.currentFirmware.contains(state.latestFirmware)

    GroupCard {
      SettingRow(
        label = "Installed",
        value = state.currentFirmware.ifEmpty { DEVICE_UNKNOWN_VALUE },
      )
      RowDivider()
      SettingRow(label = "Latest", value = state.latestFirmware.ifEmpty { DEVICE_UNKNOWN_VALUE })
      if (state.firmwareStatus != null) {
        RowDivider()
        SettingRow(label = "Update status", value = state.firmwareStatus)
      }
    }
    PrimaryButton(
      text = if (updateAvailable) "Install update" else "Up to date",
      onClick = viewModel::updateFirmware,
      enabled = updateAvailable,
      loading = state.busy,
    )
    TextLink(
      text = "Check status",
      onClick = viewModel::refreshFirmwareStatus,
      enabled = !state.busy,
    )
  }
}
