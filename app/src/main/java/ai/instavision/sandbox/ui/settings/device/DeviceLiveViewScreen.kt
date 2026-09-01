package ai.instavision.sandbox.ui.settings.device

import ai.instavision.sandbox.ui.common.GroupCard
import ai.instavision.sandbox.ui.common.InfoNote
import ai.instavision.sandbox.ui.common.SectionHeader
import ai.instavision.sandbox.ui.common.SettingRow
import ai.instavision.sandbox.ui.common.ToggleRow
import ai.instavision.sandbox.ui.settings.DeviceSubScreen
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ControlCamera
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.runtime.Composable

/**
 * How the picture is presented while watching live: whatever the camera reports about its own
 * framing, overlays and exposure, or the rotation switch alone for a camera on the pre-cluster
 * firmware, plus the recentre action for a camera with motors. Lullaby playback deliberately lives
 * on [DeviceAudioScreen] instead, since it is a speaker feature rather than a video one.
 */
@Composable
fun DeviceLiveViewScreen(onBack: () -> Unit) {
  DeviceSubScreen(title = "Live view", onBack = onBack) { state, viewModel ->
    val controls = state.controls(ClusterSection.LiveView)
    val rotatable = !state.hasCluster ||
      controls.any { control -> control.setting == ClusterSetting.ImageRotation }
    if (!state.hasCluster) {
      GroupCard {
        ToggleRow(
          title = "Rotate image 180°",
          checked = state.imageRotated,
          onCheckedChange = viewModel::setImageRotated,
          icon = Icons.Outlined.ScreenRotation,
          description = "For a camera mounted upside down",
          enabled = !state.busy,
        )
      }
    } else {
      ClusterControlGroup(
        controls = controls,
        enabled = !state.busy,
        onChange = viewModel::setClusterValue,
      )
    }
    if (rotatable) {
      InfoNote(
        text = "Rotation applies to the live stream, to recorded clips and to the snapshot on " +
          "the camera card.",
      )
    }
    if (state.ptzSupported) {
      SectionHeader(text = "Position")
      GroupCard {
        SettingRow(
          label = "Reset camera position",
          icon = Icons.Outlined.ControlCamera,
          enabled = !state.busy,
          onClick = viewModel::resetCameraPosition,
        )
      }
      InfoNote(
        text = "The camera turns back to the position it was set up in. A battery camera is " +
          "woken first, so it may take a moment to move.",
      )
    }
  }
}
