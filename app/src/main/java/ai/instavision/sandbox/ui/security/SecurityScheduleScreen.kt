package ai.instavision.sandbox.ui.security

import ai.instavision.sandbox.ui.common.AppDropdownField
import ai.instavision.sandbox.ui.common.DetailScaffold
import ai.instavision.sandbox.ui.common.ErrorBanner
import ai.instavision.sandbox.ui.common.GroupCard
import ai.instavision.sandbox.ui.common.LoadingBox
import ai.instavision.sandbox.ui.common.Notice
import ai.instavision.sandbox.ui.common.PrimaryButton
import ai.instavision.sandbox.ui.common.RowDivider
import ai.instavision.sandbox.ui.common.ScreenPadding
import ai.instavision.sandbox.ui.common.SecondaryButton
import ai.instavision.sandbox.ui.common.SectionHeader
import ai.instavision.sandbox.ui.common.SelectableChip
import ai.instavision.sandbox.ui.common.SettingRow
import ai.instavision.sandbox.ui.common.TextLink
import ai.instavision.sandbox.ui.theme.AppTheme
import ai.instavision.guardian.sdk.data.entity.SecuritySchedule
import ai.instavision.guardian.sdk.data.enums.SecurityScheduleType
import ai.instavision.guardian.sdk.data.enums.WeekDay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** What a schedule saves the household from having to remember. */
private const val HERO_BODY =
  "Arm and disarm the system on a repeating weekly schedule, so nobody has to remember to."

/** Said in place of the list when the home has no schedule yet; this step is optional. */
private const val EMPTY_BODY =
  "No schedules yet. Add one below, or finish this step without any — it is optional."

/**
 * The optional schedule step: the arm and disarm times the system runs unattended on. Production
 * gives the list, the editor and the delete confirmation a screen each; the sample keeps the three
 * on one scrolling screen, so the whole SDK surface is visible at once.
 */
@Composable
fun SecurityScheduleScreen(onBack: () -> Unit, onDone: () -> Unit, standalone: Boolean = false) {
  val viewModel: SecurityScheduleViewModel = viewModel()
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(state.done) { if (state.done) onDone() }

  DetailScaffold(
    title = SecuritySteps.ScheduleSystem.title,
    onBack = onBack,
    bottomBar = {
      Box(
        modifier = Modifier
          .navigationBarsPadding()
          .padding(horizontal = ScreenPadding, vertical = BottomBarPadding),
      ) {
        PrimaryButton(
          text = "Done",
          onClick = { if (standalone) onDone() else viewModel.finish() },
          enabled = !state.loading,
          loading = state.busy,
        )
      }
    },
  ) {
    ErrorBanner(message = state.error)
    Notice(message = SYSTEM_ARMED_MESSAGE.takeIf { state.locked })
    Hero()
    if (state.loading) {
      LoadingBox()
    } else {
      ScheduleList(
        schedules = state.schedules,
        enabled = !state.busy,
        onEdit = viewModel::edit,
        onDelete = viewModel::delete,
      )
      ScheduleEditor(
        state = state,
        onTypeChange = viewModel::onTypeChange,
        onDayToggle = viewModel::onDayToggle,
        onHourChange = viewModel::onHourChange,
        onMinuteChange = viewModel::onMinuteChange,
        onSave = viewModel::save,
        onCancel = viewModel::clearEditor,
      )
    }
  }
}

/** The headline that says what a schedule does before the list of them starts. */
@Composable
private fun Hero() {
  Column(verticalArrangement = Arrangement.spacedBy(HeroGap)) {
    Text(
      text = "Run it on a schedule",
      style = MaterialTheme.typography.headlineMedium,
      color = AppTheme.colors.textPrimary,
    )
    Text(
      text = HERO_BODY,
      style = MaterialTheme.typography.bodyLarge,
      color = AppTheme.colors.textSecondary,
    )
  }
}

/** The schedules the home already has, each opening the editor and carrying its own delete. */
@Composable
private fun ScheduleList(
  schedules: List<SecuritySchedule>,
  enabled: Boolean,
  onEdit: (SecuritySchedule) -> Unit,
  onDelete: (String) -> Unit,
) {
  SectionHeader(text = "Schedules")
  if (schedules.isEmpty()) {
    Text(
      text = EMPTY_BODY,
      style = MaterialTheme.typography.bodyMedium,
      color = AppTheme.colors.textSecondary,
    )
    return
  }
  GroupCard {
    schedules.forEachIndexed { index, schedule ->
      SettingRow(
        label = "${schedule.type} at ${formatTimeOfDay(
          hour = schedule.timeSlot.hour,
          minute = schedule.timeSlot.minute,
        )}",
        value = schedule.selectedDays.joinToString(separator = " ") { it.shortName },
        enabled = enabled,
        onClick = { onEdit(schedule) },
        trailing = {
          IconButton(onClick = { onDelete(schedule.id) }, enabled = enabled) {
            Icon(
              imageVector = Icons.Outlined.Delete,
              contentDescription = "Delete schedule",
              tint = AppTheme.colors.danger,
            )
          }
        },
      )
      if (index != schedules.lastIndex) RowDivider()
    }
  }
}

/** The one editor that both adds a schedule and rewrites the one a list row opened. */
@Composable
private fun ScheduleEditor(
  state: SecurityScheduleUiState,
  onTypeChange: (String) -> Unit,
  onDayToggle: (WeekDay) -> Unit,
  onHourChange: (String) -> Unit,
  onMinuteChange: (String) -> Unit,
  onSave: () -> Unit,
  onCancel: () -> Unit,
) {
  SectionHeader(
    text = if (state.isEditing) "Edit schedule" else "New schedule",
    action = {
      if (state.isEditing) {
        TextLink(text = "Cancel", onClick = onCancel, enabled = !state.busy)
      }
    },
  )
  FlowRow(horizontalArrangement = Arrangement.spacedBy(ChipSpacing)) {
    SecurityScheduleType.entries.forEach { type ->
      SelectableChip(
        label = type.value,
        selected = state.type == type.value,
        onClick = { onTypeChange(type.value) },
        enabled = !state.busy,
      )
    }
  }
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(ChipSpacing),
    verticalArrangement = Arrangement.spacedBy(ChipSpacing),
  ) {
    WeekDay.entries.forEach { day ->
      SelectableChip(
        label = day.shortName,
        selected = day in state.days,
        onClick = { onDayToggle(day) },
        enabled = !state.busy,
      )
    }
  }
  Row(horizontalArrangement = Arrangement.spacedBy(FieldGap)) {
    AppDropdownField(
      value = state.hourLabel,
      options = state.hourOptions,
      onSelect = onHourChange,
      placeholder = "Hour",
      modifier = Modifier.weight(1f),
      enabled = !state.busy,
    )
    AppDropdownField(
      value = state.minuteLabel,
      options = state.minuteOptions,
      onSelect = onMinuteChange,
      placeholder = "Minute",
      modifier = Modifier.weight(1f),
      enabled = !state.busy,
    )
  }
  SecondaryButton(
    text = if (state.isEditing) "Save schedule" else "Add schedule",
    onClick = onSave,
    enabled = state.canSave && !state.busy,
  )
}

/** Gap between the hero's headline and the line under it. */
private val HeroGap = 8.dp

/** Gap between two chips of the type row and the weekday row. */
private val ChipSpacing = 8.dp

/** Gap between the hour and minute dropdowns. */
private val FieldGap = 12.dp

/** Breathing room above and below the screen's bottom-bar button. */
private val BottomBarPadding = 12.dp
