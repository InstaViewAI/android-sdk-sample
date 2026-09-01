package ai.instavision.sandbox.ui.security

import ai.instavision.sandbox.ui.common.DetailScaffold
import ai.instavision.sandbox.ui.common.EmptyState
import ai.instavision.sandbox.ui.common.ErrorBanner
import ai.instavision.sandbox.ui.common.GroupCard
import ai.instavision.sandbox.ui.common.InfoNote
import ai.instavision.sandbox.ui.common.LoadingBox
import ai.instavision.sandbox.ui.common.RowDivider
import ai.instavision.sandbox.ui.common.SectionHeader
import ai.instavision.sandbox.ui.common.SettingRow
import ai.instavision.sandbox.ui.common.ToggleRow
import ai.instavision.sandbox.ui.theme.AppTheme
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HistoryToggleOff
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** Why test mode exists, shown as the toggle's own second line. */
private const val TEST_MODE_DESCRIPTION = "Run alarms without alerting the monitoring centre"

/** The standing warning that test mode is not a state to leave a real home sitting in. */
private const val TEST_MODE_NOTE =
  "While test mode is on, nothing you trigger reaches the monitoring centre. Remember to switch " +
    "it back off."

/** Said when the home has no finished setup behind these settings to change. */
private const val NOT_READY_BODY =
  "Finish setting monitoring up before changing how it behaves."

/** Title of the picker shared by the two delay rows, which names the row it was opened from. */
private fun DelayPicker.dialogTitle(): String = when (this) {
  DelayPicker.ExitDelay -> "Exit delay"
  DelayPicker.DismissalWindow -> "Time to cancel an alarm"
}

/**
 * Everything about the home's monitoring that can be changed once setup is done: the details a
 * dispatcher is given, the timings the system runs on, its log, and test mode.
 *
 * Completed checklist steps are locked, so this screen is the only way back into a safe word or
 * the armed camera list. Each row that edits a step's data opens that step's own screen with its
 * `setup_step` write suppressed, which is what keeps an edit from rewriting the checklist.
 */
@Composable
fun SecuritySettingsScreen(
  onBack: () -> Unit,
  onPersonalInfo: () -> Unit,
  onSafeWord: () -> Unit,
  onCallList: () -> Unit,
  onCameras: () -> Unit,
  onSchedule: () -> Unit,
  onLog: () -> Unit,
) {
  val viewModel: SecuritySettingsViewModel = viewModel()
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  DetailScaffold(title = "Security settings", onBack = onBack) {
    ErrorBanner(message = state.error)
    when {
      state.loading -> LoadingBox()
      !state.setupComplete -> EmptyState(
        title = "Setup is not finished",
        body = NOT_READY_BODY,
        icon = Icons.Outlined.WarningAmber,
      )

      else -> {
        MonitoringSection(
          state = state,
          onPersonalInfo = onPersonalInfo,
          onSafeWord = onSafeWord,
          onCallList = onCallList,
        )
        SystemSection(
          state = state,
          onCameras = onCameras,
          onSchedule = onSchedule,
          onPicker = viewModel::showPicker,
        )
        ActivitySection(enabled = !state.busy, onLog = onLog)
        TestingSection(state = state, onTestMode = viewModel::setTestMode)
      }
    }
  }

  state.picker?.let { picker ->
    DelayPickerDialog(
      title = picker.dialogTitle(),
      options = state.pickerOptions,
      selected = state.pickerSelection,
      onSelect = viewModel::pickDelay,
      onDismiss = { viewModel.showPicker(null) },
    )
  }
}

/** The details the monitoring centre works from: where the home is, and who it speaks to. */
@Composable
private fun MonitoringSection(
  state: SecuritySettingsUiState,
  onPersonalInfo: () -> Unit,
  onSafeWord: () -> Unit,
  onCallList: () -> Unit,
) {
  SectionHeader(text = "Monitoring")
  GroupCard {
    SettingRow(
      label = "Personal information",
      icon = Icons.Outlined.LocationOn,
      value = state.city,
      showChevron = true,
      enabled = !state.busy,
      onClick = onPersonalInfo,
    )
    RowDivider()
    SettingRow(
      label = "Safe word",
      icon = Icons.Outlined.Key,
      value = if (state.safeWordSet) "Set" else "Not set",
      showChevron = true,
      enabled = !state.busy,
      onClick = onSafeWord,
    )
    RowDivider()
    SettingRow(
      label = "Call list",
      icon = Icons.Outlined.Groups,
      value = "${state.callListCount} contacts",
      showChevron = true,
      enabled = !state.busy,
      onClick = onCallList,
    )
  }
}

/** What the system arms, and the two timings it runs on either side of an alarm. */
@Composable
private fun SystemSection(
  state: SecuritySettingsUiState,
  onCameras: () -> Unit,
  onSchedule: () -> Unit,
  onPicker: (DelayPicker) -> Unit,
) {
  SectionHeader(text = "System")
  GroupCard {
    SettingRow(
      label = "Security cameras",
      icon = Icons.Outlined.Videocam,
      value = state.cameraCount.toString(),
      showChevron = true,
      enabled = !state.busy,
      onClick = onCameras,
    )
    RowDivider()
    SettingRow(
      label = "Exit delay",
      icon = Icons.Outlined.Timer,
      value = state.exitDelayLabel,
      showChevron = true,
      enabled = !state.busy,
      onClick = { onPicker(DelayPicker.ExitDelay) },
    )
    RowDivider()
    SettingRow(
      label = "Time to cancel an alarm",
      icon = Icons.Outlined.HistoryToggleOff,
      value = state.dismissalLabel,
      showChevron = true,
      enabled = !state.busy,
      onClick = { onPicker(DelayPicker.DismissalWindow) },
    )
    RowDivider()
    SettingRow(
      label = "Schedule",
      icon = Icons.Outlined.CalendarMonth,
      showChevron = true,
      enabled = !state.busy,
      onClick = onSchedule,
    )
  }
}

/** The way back to everything the system has already done. */
@Composable
private fun ActivitySection(enabled: Boolean, onLog: () -> Unit) {
  SectionHeader(text = "Activity")
  GroupCard {
    SettingRow(
      label = "Security log",
      icon = Icons.Outlined.ListAlt,
      showChevron = true,
      enabled = enabled,
      onClick = onLog,
    )
  }
}

/** Test mode and the warning that goes with leaving it on. */
@Composable
private fun TestingSection(state: SecuritySettingsUiState, onTestMode: (Boolean) -> Unit) {
  SectionHeader(text = "Testing")
  GroupCard {
    ToggleRow(
      title = "Test mode",
      checked = state.testMode,
      onCheckedChange = onTestMode,
      icon = Icons.Outlined.Science,
      description = TEST_MODE_DESCRIPTION,
      enabled = !state.busy,
    )
  }
  InfoNote(
    text = TEST_MODE_NOTE,
    icon = Icons.Outlined.WarningAmber,
    tint = AppTheme.colors.warning,
  )
}

/**
 * The fixed list of seconds behind both delay rows. Picking writes straight away and closes, which
 * is what production's bottom sheets do; there is nothing here to confirm.
 */
@Composable
private fun DelayPickerDialog(
  title: String,
  options: List<Int>,
  selected: Int,
  onSelect: (Int) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(text = title, style = MaterialTheme.typography.titleLarge) },
    text = {
      GroupCard {
        options.forEachIndexed { index, seconds ->
          SettingRow(
            label = "$seconds seconds",
            trailing = {
              if (seconds == selected) {
                Icon(
                  imageVector = Icons.Outlined.Check,
                  contentDescription = null,
                  tint = AppTheme.colors.accent,
                  modifier = Modifier.size(TickSize),
                )
              }
            },
            onClick = { onSelect(seconds) },
          )
          if (index != options.lastIndex) RowDivider()
        }
      }
    },
    confirmButton = {
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

/** Size of the tick that marks the delay currently in force. */
private val TickSize = 20.dp
