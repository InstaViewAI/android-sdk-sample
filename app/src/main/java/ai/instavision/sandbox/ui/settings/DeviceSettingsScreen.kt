package ai.instavision.sandbox.ui.settings

import ai.instavision.sandbox.ui.common.ConfirmDialog
import ai.instavision.sandbox.ui.common.DestructiveButton
import ai.instavision.sandbox.ui.common.DetailScaffold
import ai.instavision.sandbox.ui.common.EmptyState
import ai.instavision.sandbox.ui.common.ErrorBanner
import ai.instavision.sandbox.ui.common.GroupCard
import ai.instavision.sandbox.ui.common.LoadingBox
import ai.instavision.sandbox.ui.common.Notice
import ai.instavision.sandbox.ui.common.RowDivider
import ai.instavision.sandbox.ui.common.SectionHeader
import ai.instavision.sandbox.ui.common.SettingRow
import ai.instavision.sandbox.ui.common.StatusPill
import ai.instavision.sandbox.ui.common.ToggleRow
import ai.instavision.sandbox.ui.nav.Screen
import ai.instavision.sandbox.ui.settings.device.ClusterControlGroup
import ai.instavision.sandbox.ui.settings.device.ClusterSection
import ai.instavision.sandbox.ui.settings.device.ClusterSetting
import ai.instavision.sandbox.ui.theme.AppTheme
import ai.instavision.guardian.sdk.data.entity.Device
import ai.instavision.guardian.sdk.data.entity.isOnline
import ai.instavision.guardian.sdk.data.entity.primarySnapshotUrl
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Wifi
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

/** How long a "saved" confirmation stays on screen before it clears itself. */
private const val NOTICE_DURATION_MS = 2500L

/** Headline of the placeholder shown when the user arrived without picking a camera. */
private const val NO_DEVICE_TITLE = "No camera selected"

/** Body of the placeholder shown when the user arrived without picking a camera. */
private const val NO_DEVICE_MESSAGE = "Pick a camera on the home screen to change its settings."

/** Stand-in for a hardware or firmware value the camera has not reported yet. */
internal const val DEVICE_UNKNOWN_VALUE = "Unknown"

/**
 * Hub of the camera settings tree: a snapshot of
 * [ai.instavision.sandbox.data.SessionStore.selectedDevice], grouped rows that push into the focused
 * sub-screens through [onSubScreen], the two general toggles that stay inline, and the removal
 * action. The screen pops itself once the camera has been removed.
 */
@Composable
fun DeviceSettingsScreen(onBack: () -> Unit, onSubScreen: (Screen) -> Unit) {
  var confirmRemove by remember { mutableStateOf(false) }
  val assets = LocalContext.current.assets

  DeviceSubScreen(title = "Camera settings", onBack = onBack) { state, viewModel ->
    val device = state.device ?: return@DeviceSubScreen
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }

    DeviceHeaderCard(device = device)
    CameraSection(device = device, firmware = state.currentFirmware, onSubScreen = onSubScreen)
    DetectionSection(onSubScreen = onSubScreen)
    VideoAndAudioSection(onSubScreen = onSubScreen)
    GeneralSection(
      state = state,
      onClusterChange = viewModel::setClusterValue,
      onPrivacyChange = viewModel::setPrivacyMode,
      onStatusLightChange = viewModel::setStatusLight,
    )
    ClusterSectionCard(
      state = state,
      section = ClusterSection.Light,
      header = "Lights",
      onClusterChange = viewModel::setClusterValue,
    )
    ClusterSectionCard(
      state = state,
      section = ClusterSection.Sensors,
      header = "Sensors",
      onClusterChange = viewModel::setClusterValue,
    )
    if (state.timeZoneSupported) {
      TimeZoneSection(state = state, onMatchTimeZone = { viewModel.matchPhoneTimeZone(assets) })
    }
    DestructiveButton(
      text = "Remove this camera",
      onClick = { confirmRemove = true },
      enabled = !state.busy,
    )

    if (confirmRemove) {
      ConfirmDialog(
        title = "Remove ${device.name}?",
        message = "The camera is unpaired from this home and stops recording. You can pair it " +
          "again later.",
        confirmLabel = "Remove",
        onConfirm = {
          confirmRemove = false
          viewModel.deleteDevice()
        },
        onDismiss = { confirmRemove = false },
      )
    }
  }
}

/**
 * Frame shared by the hub and every camera sub-screen: one [DeviceSettingsViewModel] resolved from
 * the host activity so they all read and write the same state, the detail scaffold, the empty and
 * loading placeholders, and the banners a write leaves behind. [content] only runs once a camera
 * is loaded, so it may read `state.device` as present.
 */
@Composable
internal fun DeviceSubScreen(
  title: String,
  onBack: () -> Unit,
  content: @Composable ColumnScope.(DeviceSettingsUiState, DeviceSettingsViewModel) -> Unit,
) {
  val viewModel: DeviceSettingsViewModel = viewModel()
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(state.notice) {
    if (state.notice != null) {
      delay(NOTICE_DURATION_MS)
      viewModel.dismissNotice()
    }
  }

  DetailScaffold(title = title, onBack = onBack) {
    when {
      state.loading -> LoadingBox()

      state.device == null -> EmptyState(
        title = NO_DEVICE_TITLE,
        body = NO_DEVICE_MESSAGE,
        icon = Icons.Outlined.Videocam,
      )

      else -> {
        ErrorBanner(message = state.error)
        Notice(message = state.notice)
        content(state, viewModel)
      }
    }
  }
}

/** The camera's latest snapshot over its connection state and the network it is joined to. */
@Composable
private fun DeviceHeaderCard(device: Device) {
  GroupCard {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(SnapshotAspectRatio)
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
    }
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(HeaderRowPadding),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(HeaderRowSpacing),
    ) {
      StatusPill(
        text = if (device.isOnline()) "Online" else "Offline",
        dotColor = if (device.isOnline()) AppTheme.colors.success else AppTheme.colors.textTertiary,
      )
      Spacer(modifier = Modifier.weight(1f))
      Icon(
        imageVector = Icons.Outlined.Wifi,
        contentDescription = null,
        tint = AppTheme.colors.textSecondary,
        modifier = Modifier.size(NetworkIconSize),
      )
      Text(
        text = device.deviceState.wifiName.ifEmpty { DEVICE_UNKNOWN_VALUE },
        style = MaterialTheme.typography.bodyMedium,
        color = AppTheme.colors.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/** Identity of the camera: what it is called, what it is, and what it is running. */
@Composable
private fun CameraSection(device: Device, firmware: String, onSubScreen: (Screen) -> Unit) {
  SectionHeader(text = "Camera")
  GroupCard {
    SettingRow(
      label = "Name",
      icon = Icons.Outlined.TextFields,
      value = device.name,
      showChevron = true,
      onClick = { onSubScreen(Screen.DeviceName) },
    )
    RowDivider()
    SettingRow(
      label = "Camera info",
      icon = Icons.Outlined.Info,
      value = device.modelName,
      showChevron = true,
      onClick = { onSubScreen(Screen.DeviceInfo) },
    )
    RowDivider()
    SettingRow(
      label = "Firmware",
      icon = Icons.Outlined.Download,
      value = firmware.ifEmpty { DEVICE_UNKNOWN_VALUE },
      showChevron = true,
      onClick = { onSubScreen(Screen.DeviceFirmware) },
    )
  }
}

/** What the camera looks for, and who hears about it. */
@Composable
private fun DetectionSection(onSubScreen: (Screen) -> Unit) {
  SectionHeader(text = "Detection")
  GroupCard {
    SettingRow(
      label = "Events and detection",
      icon = Icons.Outlined.Sensors,
      showChevron = true,
      onClick = { onSubScreen(Screen.DeviceDetection) },
    )
    RowDivider()
    SettingRow(
      label = "Notifications",
      icon = Icons.Outlined.Notifications,
      showChevron = true,
      onClick = { onSubScreen(Screen.DeviceNotifications) },
    )
  }
}

/** How the picture is presented and what the camera plays through its speaker. */
@Composable
private fun VideoAndAudioSection(onSubScreen: (Screen) -> Unit) {
  SectionHeader(text = "Video and audio")
  GroupCard {
    SettingRow(
      label = "Live view",
      icon = Icons.Outlined.Videocam,
      showChevron = true,
      onClick = { onSubScreen(Screen.DeviceLiveView) },
    )
    RowDivider()
    SettingRow(
      label = "Audio",
      icon = Icons.AutoMirrored.Outlined.VolumeUp,
      showChevron = true,
      onClick = { onSubScreen(Screen.DeviceAudio) },
    )
  }
}

/**
 * The controls that act on the camera body itself, kept on the hub rather than pushed. A camera
 * that reports a cluster gets exactly the switches and pickers it advertises and nothing else;
 * only a camera on the pre-cluster firmware keeps the two switches every Guardian camera has
 * always had.
 */
@Composable
private fun GeneralSection(
  state: DeviceSettingsUiState,
  onClusterChange: (ClusterSetting, Any) -> Unit,
  onPrivacyChange: (Boolean) -> Unit,
  onStatusLightChange: (Boolean) -> Unit,
) {
  val controls = state.controls(ClusterSection.General)
  if (!state.hasCluster) {
    SectionHeader(text = "General")
    GroupCard {
      ToggleRow(
        title = "Privacy mode",
        checked = state.privacyModeOn,
        onCheckedChange = onPrivacyChange,
        icon = Icons.Outlined.VisibilityOff,
        description = "Stops recording and disables the lens",
        enabled = !state.busy,
      )
      RowDivider()
      ToggleRow(
        title = "Status light",
        checked = state.statusLightOn,
        onCheckedChange = onStatusLightChange,
        icon = Icons.Outlined.Lightbulb,
        description = "The small LED on the camera body",
        enabled = !state.busy,
      )
    }
    return
  }
  if (controls.isEmpty()) return
  SectionHeader(text = "General")
  ClusterControlGroup(controls = controls, enabled = !state.busy, onChange = onClusterChange)
}

/**
 * One group of cluster-backed rows with its own heading, drawn only when the camera advertises at
 * least one attribute in [section]. This is how the lamp and sensor groups the production app puts
 * on their own sub-screens are reached without adding routes.
 */
@Composable
private fun ClusterSectionCard(
  state: DeviceSettingsUiState,
  section: ClusterSection,
  header: String,
  onClusterChange: (ClusterSetting, Any) -> Unit,
) {
  val controls = state.controls(section)
  if (controls.isEmpty()) return
  SectionHeader(text = header)
  ClusterControlGroup(controls = controls, enabled = !state.busy, onChange = onClusterChange)
}

/**
 * The clock the camera keeps. Only cameras that report a timezone cluster get this, and the one
 * action writes the identifier and its offset together through the whole cluster.
 */
@Composable
private fun TimeZoneSection(state: DeviceSettingsUiState, onMatchTimeZone: () -> Unit) {
  SectionHeader(text = "Time")
  GroupCard {
    SettingRow(
      label = "Timezone",
      icon = Icons.Outlined.Public,
      value = state.timeZone.ifEmpty { DEVICE_UNKNOWN_VALUE },
    )
    RowDivider()
    SettingRow(
      label = "Match phone timezone",
      enabled = !state.busy,
      onClick = onMatchTimeZone,
    )
  }
}

/** Shape of the snapshot that heads the hub, matching the camera's own sensor. */
private const val SnapshotAspectRatio = 16f / 9f

/** Size of the camera glyph standing in for a snapshot the camera has not uploaded yet. */
private val PlaceholderIconSize = 36.dp

/** Inset of the status line beneath the snapshot. */
private val HeaderRowPadding = 16.dp

/** Gap between the status pill and the network readout beside it. */
private val HeaderRowSpacing = 8.dp

/** Size of the Wi-Fi glyph in the status line. */
private val NetworkIconSize = 18.dp
