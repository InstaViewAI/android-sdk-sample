package ai.instavision.sandbox.ui.settings.device

import ai.instavision.sandbox.ui.common.GroupCard
import ai.instavision.sandbox.ui.common.RowDivider
import ai.instavision.sandbox.ui.common.SelectableChip
import ai.instavision.sandbox.ui.common.SettingRow
import ai.instavision.sandbox.ui.common.ToggleRow
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Renders the controls a camera advertises for one section as a single grouped card. Nothing is
 * drawn when [controls] is empty, which is how a camera that reports no clusters — or a cluster
 * fetch that failed — leaves the caller's own rows in place.
 *
 * [onChange] is handed the wire value the camera expects, so callers pass it straight to the
 * cluster write without translating anything.
 */
@Composable
fun ClusterControlGroup(
  controls: List<ClusterControl>,
  enabled: Boolean,
  onChange: (ClusterSetting, Any) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (controls.isEmpty()) return
  GroupCard(modifier = modifier) {
    controls.forEachIndexed { index, control ->
      if (index > 0) RowDivider()
      when (control) {
        is ClusterControl.Switch -> SwitchControl(
          control = control,
          enabled = enabled,
          onChange = onChange,
        )

        is ClusterControl.Choice -> ChoiceControl(
          control = control,
          enabled = enabled,
          onChange = onChange,
        )

        is ClusterControl.Level -> LevelControl(
          control = control,
          enabled = enabled,
          onChange = onChange,
        )

        is ClusterControl.Readout -> SettingRow(
          label = control.setting.title,
          icon = control.setting.icon,
          value = control.value + control.setting.unit,
        )
      }
    }
  }
}

/** An on-or-off attribute, which is the shape most of the cluster catalogue takes. */
@Composable
private fun SwitchControl(
  control: ClusterControl.Switch,
  enabled: Boolean,
  onChange: (ClusterSetting, Any) -> Unit,
) {
  ToggleRow(
    title = control.setting.title,
    checked = control.checked,
    onCheckedChange = { checked ->
      onChange(control.setting, control.setting.switchValue(checked = checked))
    },
    icon = control.setting.icon,
    description = control.setting.description,
    enabled = enabled,
  )
}

/** An attribute that advertises its own values, shown as the current one over a strip of chips. */
@Composable
private fun ChoiceControl(
  control: ClusterControl.Choice,
  enabled: Boolean,
  onChange: (ClusterSetting, Any) -> Unit,
) {
  SettingRow(
    label = control.setting.title,
    icon = control.setting.icon,
    value = control.selectedLabel(),
    enabled = enabled,
  )
  ChipStrip {
    control.options.forEach { option ->
      SelectableChip(
        label = option.label,
        selected = option.key == control.selected,
        onClick = { onChange(control.setting, option.value) },
        enabled = enabled,
      )
    }
  }
}

/**
 * A bounded number. The design system has no slider, so the values on offer are laid out as a
 * short strip of chips instead.
 */
@Composable
private fun LevelControl(
  control: ClusterControl.Level,
  enabled: Boolean,
  onChange: (ClusterSetting, Any) -> Unit,
) {
  SettingRow(
    label = control.setting.title,
    icon = control.setting.icon,
    value = "${control.value}${control.setting.unit}",
    enabled = enabled,
  )
  ChipStrip {
    control.choices.forEach { choice ->
      SelectableChip(
        label = "$choice${control.setting.unit}",
        selected = choice == control.value,
        onClick = { onChange(control.setting, choice) },
        enabled = enabled,
      )
    }
  }
}

/** The scrolling strip a picker's chips sit in, inset to line up with the row above it. */
@Composable
private fun ChipStrip(content: @Composable () -> Unit) {
  Row(
    modifier = Modifier
      .horizontalScroll(rememberScrollState())
      .padding(start = StripStartPadding, end = StripEndPadding, bottom = StripBottomPadding),
    horizontalArrangement = Arrangement.spacedBy(ChipSpacing),
  ) {
    content()
  }
}

/** Inset that lines a chip strip up with the label of the row above it. */
private val StripStartPadding = 16.dp

/** Clear space after the last chip of a strip. */
private val StripEndPadding = 16.dp

/** Clear space between a chip strip and whatever follows it in the card. */
private val StripBottomPadding = 12.dp

/** Gap between two chips of the same strip. */
private val ChipSpacing = 8.dp
