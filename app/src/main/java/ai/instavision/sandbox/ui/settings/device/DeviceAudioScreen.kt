package ai.instavision.sandbox.ui.settings.device

import ai.instavision.sandbox.ui.common.CircleIconButton
import ai.instavision.sandbox.ui.common.GroupCard
import ai.instavision.sandbox.ui.common.RowDivider
import ai.instavision.sandbox.ui.common.SectionHeader
import ai.instavision.sandbox.ui.common.SelectableChip
import ai.instavision.sandbox.ui.common.SettingRow
import ai.instavision.sandbox.ui.common.TextLink
import ai.instavision.sandbox.ui.common.ToggleRow
import ai.instavision.sandbox.ui.settings.DeviceSettingsUiState
import ai.instavision.sandbox.ui.settings.DeviceSettingsViewModel
import ai.instavision.sandbox.ui.settings.DeviceSubScreen
import ai.instavision.sandbox.ui.theme.AppTheme
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The camera's microphone, its speaker and — for the models that ship with them — its lullabies.
 * Lullaby lives here rather than on the live-view screen because it is playback, not picture.
 */
@Composable
fun DeviceAudioScreen(onBack: () -> Unit) {
  DeviceSubScreen(title = "Audio", onBack = onBack) { state, viewModel ->
    if (!state.hasCluster || state.controls(ClusterSection.Audio).isNotEmpty()) {
      SectionHeader(text = "Sound")
      SoundCard(state = state, viewModel = viewModel)
    }
    if (state.lullabySupported) {
      LullabySections(state = state, viewModel = viewModel)
    }
  }
}

/**
 * The camera's own sound controls. A camera with a cluster reports which of the microphone and
 * volume it actually has, and what range the volume runs over; one on the pre-cluster firmware
 * keeps the fixed trio that settings API returns.
 */
@Composable
private fun SoundCard(state: DeviceSettingsUiState, viewModel: DeviceSettingsViewModel) {
  val controls = state.controls(ClusterSection.Audio)
  if (!state.hasCluster) {
    GroupCard {
      ToggleRow(
        title = "Microphone",
        checked = state.audio.microphoneEnabled,
        onCheckedChange = viewModel::setMicrophone,
        icon = Icons.Outlined.Mic,
        description = "Records sound alongside the picture",
        enabled = !state.busy,
      )
      RowDivider()
      ToggleRow(
        title = "Speaker",
        checked = state.audio.speakerEnabled,
        onCheckedChange = viewModel::setSpeaker,
        icon = Icons.AutoMirrored.Outlined.VolumeUp,
        description = "Lets the camera talk back and play lullabies",
        enabled = !state.busy,
      )
      RowDivider()
      SettingRow(label = "Volume", value = state.audio.volumeLevel.toString())
    }
  } else {
    ClusterControlGroup(
      controls = controls,
      enabled = !state.busy,
      onChange = viewModel::setClusterValue,
    )
  }
}

/** Track picker, transport and playback preferences, shown only for models with lullabies. */
@Composable
private fun LullabySections(state: DeviceSettingsUiState, viewModel: DeviceSettingsViewModel) {
  SectionHeader(text = "Lullaby")
  GroupCard {
    SettingRow(label = "Playback", value = state.lullaby?.playbackState ?: "Stopped")
    state.lullabyTracks.forEach { track ->
      RowDivider()
      SettingRow(
        label = track.name,
        enabled = !state.busy,
        trailing = {
          RadioButton(
            selected = track.id == state.selectedTrackId,
            onClick = { viewModel.selectLullabyTrack(track.id) },
            enabled = !state.busy,
            colors = RadioButtonDefaults.colors(
              selectedColor = AppTheme.colors.accent,
              unselectedColor = AppTheme.colors.textTertiary,
            ),
          )
        },
        onClick = { viewModel.selectLullabyTrack(track.id) },
      )
    }
  }
  TransportRow(
    enabled = !state.busy,
    onPlay = viewModel::playLullaby,
    onPause = viewModel::pauseLullaby,
    onStop = viewModel::stopLullaby,
    onResume = viewModel::resumeLullaby,
  )
  if (state.lullabyModes.isNotEmpty()) {
    SectionHeader(text = "Repeat")
    ChipRow(
      options = state.lullabyModes,
      selected = state.lullaby?.playbackMode,
      enabled = !state.busy,
      onSelect = viewModel::setLullabyMode,
    )
  }
  if (state.lullabyTimers.isNotEmpty()) {
    SectionHeader(text = "Sleep timer")
    ChipRow(
      options = state.lullabyTimers.map { minutes -> timerLabel(minutes = minutes) },
      selected = state.lullaby?.let { timerLabel(minutes = it.timerDurationInMins) },
      enabled = !state.busy,
      onSelect = { choice -> viewModel.setLullabyTimer(minutesOf(label = choice)) },
    )
  }
}

/** Play, pause and stop as discs, with resume kept apart because it only follows a pause. */
@Composable
private fun TransportRow(
  enabled: Boolean,
  onPlay: () -> Unit,
  onPause: () -> Unit,
  onStop: () -> Unit,
  onResume: () -> Unit,
) {
  Row(
    horizontalArrangement = Arrangement.spacedBy(TransportSpacing),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    CircleIconButton(
      icon = Icons.Rounded.PlayArrow,
      contentDescription = "Play lullaby",
      onClick = { if (enabled) onPlay() },
      tint = transportTint(enabled = enabled),
    )
    CircleIconButton(
      icon = Icons.Rounded.Pause,
      contentDescription = "Pause lullaby",
      onClick = { if (enabled) onPause() },
      tint = transportTint(enabled = enabled),
    )
    CircleIconButton(
      icon = Icons.Rounded.Stop,
      contentDescription = "Stop lullaby",
      onClick = { if (enabled) onStop() },
      tint = transportTint(enabled = enabled),
    )
    TextLink(text = "Resume", onClick = onResume, enabled = enabled)
  }
}

/**
 * Glyph colour standing in for a disabled transport disc, since [CircleIconButton] has no enabled
 * flag of its own and the discs must dim while a write is in flight.
 */
@Composable
private fun transportTint(enabled: Boolean): Color =
  if (enabled) AppTheme.colors.textPrimary else AppTheme.colors.textTertiary

/** A single-choice strip of short options, used for the repeat modes and the sleep timers. */
@Composable
private fun ChipRow(
  options: List<String>,
  selected: String?,
  enabled: Boolean,
  onSelect: (String) -> Unit,
) {
  Row(
    modifier = Modifier.horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(ChipSpacing),
  ) {
    options.forEach { option ->
      SelectableChip(
        label = option,
        selected = option == selected,
        onClick = { onSelect(option) },
        enabled = enabled,
      )
    }
  }
}

/** Renders a sleep-timer duration as the chip label the user reads. */
private fun timerLabel(minutes: Long): String = "$minutes min"

/** Reverses [timerLabel] so a tapped chip can be sent back to the camera as a number. */
private fun minutesOf(label: String): Long = label.substringBefore(" ").toLong()

/** Gap between the transport discs. */
private val TransportSpacing = 12.dp

/** Gap between two chips of the same strip. */
private val ChipSpacing = 8.dp
