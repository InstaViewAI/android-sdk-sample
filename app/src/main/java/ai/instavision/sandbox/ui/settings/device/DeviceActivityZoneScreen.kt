package ai.instavision.sandbox.ui.settings.device

import ai.instavision.sandbox.ui.common.ErrorBanner
import ai.instavision.sandbox.ui.common.GroupCard
import ai.instavision.sandbox.ui.common.InfoNote
import ai.instavision.sandbox.ui.common.PrimaryButton
import ai.instavision.sandbox.ui.common.SecondaryButton
import ai.instavision.sandbox.ui.settings.DeviceSubScreen
import ai.instavision.sandbox.ui.theme.AppTheme
import ai.instavision.guardian.sdk.data.entity.Device
import ai.instavision.guardian.sdk.data.entity.isOnline
import ai.instavision.guardian.sdk.data.entity.primarySnapshotUrl
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/** Shape of the backdrop the grid is drawn over, matching the camera's own sensor. */
private const val ZONE_ASPECT_RATIO = 16f / 9f

/** Size of the camera glyph standing in for a snapshot the camera has not uploaded yet. */
private val PlaceholderIconSize = 36.dp

/** Explanation of what the grid does, which is not obvious from the grid alone. */
private const val ZONE_HELP =
  "Drag across the picture to choose the blocks the camera watches. Movement anywhere else is " +
    "ignored, so it stops reporting the road or a neighbour's garden."

/** Shown in place of the editor when the camera is not on the network. */
private const val ZONE_OFFLINE =
  "This camera is offline. Its zones are shown as last saved and cannot be changed until it is " +
    "back on the network."

/**
 * The activity-zone editor: the camera's latest still with the block grid it divides its frame
 * into drawn over it, and the save that writes the chosen blocks back. The grid comes from the
 * camera's own schema, so a camera that advertises a different one gets that instead of the
 * default eight by four.
 */
@Composable
fun DeviceActivityZoneScreen(onBack: () -> Unit) {
  DeviceSubScreen(title = "Activity zones", onBack = onBack) { state, viewModel ->
    val device = state.device ?: return@DeviceSubScreen
    val editable = device.isOnline() && !state.busy
    var selection by remember { mutableStateOf(state.activityZones) }
    LaunchedEffect(state.activityZones) { selection = state.activityZones }

    if (!device.isOnline()) ErrorBanner(message = ZONE_OFFLINE)
    ZoneStage(
      device = device,
      selection = selection,
      rows = state.zoneRows,
      columns = state.zoneColumns,
      editable = editable,
      onSelectionChange = { selection = it },
    )
    Text(
      text = "${selection.size} of ${state.zoneRows * state.zoneColumns} blocks watched",
      style = MaterialTheme.typography.bodyMedium,
      color = AppTheme.colors.textSecondary,
    )
    InfoNote(text = ZONE_HELP)
    PrimaryButton(
      text = "Save zones",
      onClick = { viewModel.saveActivityZones(selection) },
      enabled = editable,
      loading = state.busy,
    )
    SecondaryButton(
      text = "Reset",
      onClick = { selection = state.activityZones },
      enabled = editable && selection != state.activityZones,
    )
  }
}

/** The camera's still with the grid painted over it, or a placeholder when it has no still yet. */
@Composable
private fun ZoneStage(
  device: Device,
  selection: List<Int>,
  rows: Int,
  columns: Int,
  editable: Boolean,
  onSelectionChange: (List<Int>) -> Unit,
) {
  GroupCard {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(ZONE_ASPECT_RATIO)
        .background(color = AppTheme.colors.surfaceHigh),
      contentAlignment = Alignment.Center,
    ) {
      val snapshotUrl = device.primarySnapshotUrl()
      if (snapshotUrl.isEmpty()) {
        Icon(
          imageVector = Icons.Outlined.Videocam,
          contentDescription = null,
          tint = AppTheme.colors.textTertiary,
          modifier = Modifier.size(PlaceholderIconSize),
        )
      } else {
        AsyncImage(
          model = snapshotUrl,
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
      }
      ActivityZoneGrid(
        selected = selection,
        onSelectedChange = onSelectionChange,
        modifier = Modifier.fillMaxSize(),
        rows = rows,
        columns = columns,
        enabled = editable,
      )
    }
  }
}
