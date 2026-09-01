package ai.instavision.sandbox.ui.settings.device

import ai.instavision.sandbox.ui.common.GroupCard
import ai.instavision.sandbox.ui.common.RowDivider
import ai.instavision.sandbox.ui.common.SectionHeader
import ai.instavision.sandbox.ui.common.SettingRow
import ai.instavision.sandbox.ui.common.ToggleRow
import ai.instavision.sandbox.ui.nav.Screen
import ai.instavision.sandbox.ui.settings.DetectionCategory
import ai.instavision.sandbox.ui.settings.DeviceSettingsUiState
import ai.instavision.sandbox.ui.settings.DeviceSettingsViewModel
import ai.instavision.sandbox.ui.settings.DeviceSubScreen
import ai.instavision.sandbox.ui.theme.AppTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.time.LocalTime

/**
 * What the camera watches for. Movement itself and the alert categories are account settings, so
 * they are always shown; the sensitivity, cooldown and clip length beneath them come from the
 * camera's own cluster and only appear when it advertises them, and the zone and event-window
 * sections likewise appear only for a camera that has them. The zone row pushes into the editor
 * through [onSubScreen].
 */
@Composable
fun DeviceDetectionScreen(onBack: () -> Unit, onSubScreen: (Screen) -> Unit) {
  DeviceSubScreen(title = "Events and detection", onBack = onBack) { state, viewModel ->
    SectionHeader(text = "Detection")
    GroupCard {
      ToggleRow(
        title = "Motion events",
        checked = state.motionDetectionOn(),
        onCheckedChange = viewModel::setMotionDetection,
        icon = Icons.Outlined.Sensors,
        description = "Record and alert whenever the camera sees movement",
        enabled = !state.busy,
      )
    }
    CameraDetectionCard(state = state, viewModel = viewModel)
    SectionHeader(text = "Alert me about")
    GroupCard {
      val categories = DetectionCategory.entries.filter { it.availableIn(state) }
      categories.forEachIndexed { index, category ->
        if (index > 0) RowDivider()
        ToggleRow(
          title = category.label,
          checked = category.readFrom(state.cloudAi),
          onCheckedChange = { enabled ->
            viewModel.setCloudDetection(category = category, enabled = enabled)
          },
          enabled = !state.busy && state.motionDetectionOn(),
        )
      }
    }
    ActivityZoneCard(state = state, viewModel = viewModel, onSubScreen = onSubScreen)
    EventScheduleCard(state = state, viewModel = viewModel)
  }
}

/**
 * The activity-zone card, shown the way the reference app shows it: the whole section is absent
 * unless the camera advertises zones, the switch heads the card, and the way into the editor
 * appears beneath it only while zones are switched on.
 */
@Composable
private fun ActivityZoneCard(
  state: DeviceSettingsUiState,
  viewModel: DeviceSettingsViewModel,
  onSubScreen: (Screen) -> Unit,
) {
  if (!state.activityZoneSupported) return
  SectionHeader(text = "Activity zones")
  GroupCard {
    ToggleRow(
      title = "Activity zones",
      checked = state.activityZoneEnabled,
      onCheckedChange = viewModel::setActivityZoneEnabled,
      icon = Icons.Outlined.CropFree,
      description = "Only watch the parts of the frame you picked",
      enabled = !state.busy,
    )
    if (state.activityZoneEnabled) {
      RowDivider()
      SettingRow(
        label = "Zones",
        value = state.activityZoneSummary,
        showChevron = true,
        onClick = { onSubScreen(Screen.DeviceActivityZone) },
      )
    }
  }
}

/** The detection controls the camera itself reports, drawn only when it advertises any. */
@Composable
private fun CameraDetectionCard(
  state: DeviceSettingsUiState,
  viewModel: DeviceSettingsViewModel,
) {
  ClusterControlGroup(
    controls = state.controls(ClusterSection.Detection),
    enabled = !state.busy,
    onChange = viewModel::setClusterValue,
  )
}

/**
 * The event-window card, built to the same shape as [ActivityZoneCard]: absent unless the camera
 * has a window at all, the switch heads it, and the two hours appear beneath only while it is on.
 * Both are shown in the phone's timezone; the camera is written to in UTC.
 */
@Composable
private fun EventScheduleCard(
  state: DeviceSettingsUiState,
  viewModel: DeviceSettingsViewModel,
) {
  if (!state.eventScheduleSupported) return
  var editing by remember { mutableStateOf<ScheduleEdge?>(null) }
  SectionHeader(text = "Event schedule")
  GroupCard {
    ToggleRow(
      title = "Event schedule",
      checked = state.eventScheduleEnabled,
      onCheckedChange = viewModel::setEventScheduleEnabled,
      icon = Icons.Outlined.Schedule,
      description = "Only record between the hours you set",
      enabled = !state.busy,
    )
    if (state.eventScheduleEnabled) {
      RowDivider()
      SettingRow(
        label = ScheduleEdge.Start.title,
        value = state.eventScheduleStart.clockLabel(),
        showChevron = true,
        enabled = !state.busy,
        onClick = { editing = ScheduleEdge.Start },
      )
      RowDivider()
      SettingRow(
        label = ScheduleEdge.End.title,
        value = state.endLabel(),
        showChevron = true,
        enabled = !state.busy,
        onClick = { editing = ScheduleEdge.End },
      )
    }
  }
  editing?.let { edge ->
    ScheduleTimeDialog(
      edge = edge,
      time = edge.timeIn(state),
      onConfirm = { picked ->
        editing = null
        edge.write(viewModel = viewModel, time = picked)
      },
      onDismiss = { editing = null },
    )
  }
}

/**
 * The one time picker both schedule rows open, seeded with the hour of whichever row was tapped.
 * Nothing is sent until Apply, so dismissing it leaves the camera exactly as it was.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleTimeDialog(
  edge: ScheduleEdge,
  time: LocalTime?,
  onConfirm: (LocalTime) -> Unit,
  onDismiss: () -> Unit,
) {
  val pickerState = rememberTimePickerState(
    initialHour = time?.hour ?: 0,
    initialMinute = time?.minute ?: 0,
  )
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(text = edge.title, style = MaterialTheme.typography.titleLarge) },
    text = { TimePicker(state = pickerState) },
    confirmButton = {
      TextButton(onClick = { onConfirm(LocalTime.of(pickerState.hour, pickerState.minute)) }) {
        Text(
          text = "Apply",
          style = MaterialTheme.typography.titleMedium,
          color = AppTheme.colors.accent,
        )
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(
          text = "Cancel",
          style = MaterialTheme.typography.titleMedium,
          color = AppTheme.colors.textSecondary,
        )
      }
    },
    containerColor = AppTheme.colors.surface,
    titleContentColor = AppTheme.colors.textPrimary,
    textContentColor = AppTheme.colors.textSecondary,
  )
}

/**
 * The closing hour as its row shows it. A window whose start is at or after its end runs overnight
 * — a common enough schedule that it is labelled rather than refused.
 */
private fun DeviceSettingsUiState.endLabel(): String {
  val label = eventScheduleEnd.clockLabel()
  return if (eventScheduleEndsNextDay()) "$label next day" else label
}

/** Which end of the event window a row opens the time picker for. */
private enum class ScheduleEdge(
  /** Label of the row, reused as the title of the picker it opens. */
  val title: String,
) {
  /** The hour the window opens. */
  Start(title = "Start time"),

  /** The hour the window closes. */
  End(title = "End time"),
  ;

  /** The hour this end currently sits at, in the phone's timezone. */
  fun timeIn(state: DeviceSettingsUiState): LocalTime? = when (this) {
    Start -> state.eventScheduleStart
    End -> state.eventScheduleEnd
  }

  /** Sends the picked hour for this end of the window. */
  fun write(viewModel: DeviceSettingsViewModel, time: LocalTime) {
    when (this) {
      Start -> viewModel.setEventScheduleStart(time)
      End -> viewModel.setEventScheduleEnd(time)
    }
  }
}
