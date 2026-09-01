package ai.instavision.sandbox.ui.settings.device

import ai.instavision.sandbox.ui.common.GroupCard
import ai.instavision.sandbox.ui.common.RowDivider
import ai.instavision.sandbox.ui.common.ToggleRow
import ai.instavision.sandbox.ui.settings.DeviceSubScreen
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.runtime.Composable

/**
 * Push preferences for the selected camera. Every switch mutes rather than disables — events keep
 * being recorded either way, which is what the supporting copy has to make clear. The cry,
 * temperature and humidity rows only appear for a camera whose cluster advertises that alert,
 * which is exactly how the production app gates them.
 */
@Composable
fun DeviceNotificationsScreen(onBack: () -> Unit) {
  DeviceSubScreen(title = "Notifications", onBack = onBack) { state, viewModel ->
    GroupCard {
      ToggleRow(
        title = "Mute all notifications",
        checked = state.notifications.mute == true,
        onCheckedChange = viewModel::setNotificationsMuted,
        icon = Icons.Outlined.NotificationsOff,
        description = "Events are still recorded, you just will not be told about them",
        enabled = !state.busy,
      )
      RowDivider()
      ToggleRow(
        title = "Mute offline alerts",
        checked = state.notifications.cameraOfflineNotification == true,
        onCheckedChange = viewModel::setOfflineAlertsMuted,
        icon = Icons.Outlined.CloudOff,
        description = "Stop being told when this camera loses its connection",
        enabled = !state.busy,
      )
      if (state.cryDetectionSupported) {
        RowDivider()
        ToggleRow(
          title = "Mute cry alerts",
          checked = state.notifications.cryMute == true,
          onCheckedChange = viewModel::setCryAlertsMuted,
          icon = Icons.Outlined.ChildCare,
          description = "Stop being told when the camera hears a baby cry",
          enabled = !state.busy,
        )
      }
      if (state.temperatureAlertsSupported) {
        RowDivider()
        ToggleRow(
          title = "Mute temperature alerts",
          checked = state.notifications.temperatureMute == true,
          onCheckedChange = viewModel::setTemperatureAlertsMuted,
          icon = Icons.Outlined.Thermostat,
          description = "Stop being told when the room leaves its safe range",
          enabled = !state.busy,
        )
      }
      if (state.humidityAlertsSupported) {
        RowDivider()
        ToggleRow(
          title = "Mute humidity alerts",
          checked = state.notifications.humidityMute == true,
          onCheckedChange = viewModel::setHumidityAlertsMuted,
          icon = Icons.Outlined.WaterDrop,
          description = "Stop being told when the air gets too damp or too dry",
          enabled = !state.busy,
        )
      }
    }
  }
}
