package ai.instavision.sandbox.ui.security

import ai.instavision.sandbox.ui.common.DetailScaffold
import ai.instavision.sandbox.ui.common.EmptyState
import ai.instavision.sandbox.ui.common.ErrorBanner
import ai.instavision.sandbox.ui.common.GroupCard
import ai.instavision.sandbox.ui.common.InfoNote
import ai.instavision.sandbox.ui.common.LoadingBox
import ai.instavision.sandbox.ui.common.PrimaryButton
import ai.instavision.sandbox.ui.common.RowDivider
import ai.instavision.sandbox.ui.common.ScreenPadding
import ai.instavision.sandbox.ui.common.SectionHeader
import ai.instavision.sandbox.ui.common.TextLink
import ai.instavision.sandbox.ui.common.ToggleRow
import ai.instavision.sandbox.ui.theme.AppTheme
import ai.instavision.guardian.sdk.data.entity.Device
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** Why the user is picking cameras at all, taken from the production step's own strapline. */
private const val HERO_BODY =
  "Select the cameras you want to use with the monitoring service."

/** What an activity zone buys, shown above the per-camera toggles. */
private const val ZONE_BODY =
  "Activity zones put security priority on specific spots of a camera's view."

/** Where the zone grid itself is edited, since this step only switches zones on and off. */
private const val ZONE_NOTE =
  "Switching a zone on for the first time applies the default centre-of-frame zone. Drawing the " +
    "zone itself is done in the camera's own detection settings."

/** Said in place of a model name for a camera monitoring cannot arm. */
private const val INCOMPATIBLE_CAMERA = "Incompatible camera"

/** Said instead for a camera monitoring could arm but the home's plan does not cover. */
private const val NO_SUBSCRIPTION = "No monitoring subscription"

/** The line under a camera's name: its model, or the reason it cannot be picked. */
private fun Device.selectionReason(): String = when {
  !supportsProSecurity() -> INCOMPATIBLE_CAMERA
  !hasMonitoringSubscription() -> NO_SUBSCRIPTION
  else -> modelName
}

/**
 * The camera setup step: which cameras take part in monitoring, then whether each of them limits
 * detection to an activity zone. Production splits this across a pager with a walkthrough between
 * the two halves; the sample drops the walkthrough and lets both halves share one scrolling screen.
 */
@Composable
fun SecurityCameraScreen(onBack: () -> Unit, onDone: () -> Unit, standalone: Boolean = false) {
  val viewModel: SecurityCameraViewModel = viewModel()
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(state.done) { if (state.done) onDone() }

  DetailScaffold(
    title = if (standalone) "Security cameras" else SecuritySteps.CameraSetup.title,
    onBack = onBack,
    bottomBar = {
      Box(
        modifier = Modifier
          .navigationBarsPadding()
          .padding(horizontal = ScreenPadding, vertical = BottomBarPadding),
      ) {
        PrimaryButton(
          text = if (state.selectionSaved) "Done" else "Save cameras",
          onClick = {
            when {
              !state.selectionSaved -> viewModel.saveSelection()
              standalone -> onDone()
              else -> viewModel.finish()
            }
          },
          enabled = state.selectedIds.isNotEmpty(),
          loading = state.busy,
        )
      }
    },
  ) {
    ErrorBanner(message = state.error)
    Hero()
    when {
      state.loading -> LoadingBox()
      state.devices.isEmpty() -> EmptyState(
        title = "No cameras yet",
        body = "Pair a camera with this home before setting monitoring up.",
        icon = Icons.Outlined.Videocam,
      )

      else -> {
        CameraSection(
          state = state,
          onToggleDevice = viewModel::toggleDevice,
          onToggleAll = viewModel::toggleAll,
        )
        ZoneSection(state = state, onToggleZone = viewModel::setZoneEnabled)
      }
    }
  }
}

/** The headline that says what the selection is for before the list of cameras starts. */
@Composable
private fun Hero() {
  Column(verticalArrangement = Arrangement.spacedBy(HeroGap)) {
    Text(
      text = "Which cameras take part?",
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

/** Every camera of the home, with a tick on the ones monitoring will arm. */
@Composable
private fun CameraSection(
  state: SecurityCameraUiState,
  onToggleDevice: (String) -> Unit,
  onToggleAll: () -> Unit,
) {
  SectionHeader(
    text = "Cameras",
    action = {
      if (state.eligible.isNotEmpty()) {
        TextLink(
          text = if (state.allSelected) "Clear all" else "Select all",
          onClick = onToggleAll,
          enabled = !state.busy,
        )
      }
    },
  )
  GroupCard {
    state.devices.forEachIndexed { index, device ->
      CameraRow(
        device = device,
        selected = device.id in state.selectedIds,
        enabled = device.isSelectableForSecurity() && !state.busy,
        onClick = { onToggleDevice(device.id) },
      )
      if (index != state.devices.lastIndex) RowDivider()
    }
  }
}

/**
 * One camera of the selection list. A camera monitoring cannot take is still listed, greyed and
 * untappable, with the reason in place of its model, rather than silently dropped the way
 * production drops it.
 */
@Composable
private fun CameraRow(
  device: Device,
  selected: Boolean,
  enabled: Boolean,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(enabled = enabled, onClick = onClick)
      .padding(horizontal = RowPadding, vertical = RowVerticalPadding),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(RowSpacing),
  ) {
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(TitleGap),
    ) {
      Text(
        text = device.name,
        style = MaterialTheme.typography.titleMedium,
        color = if (enabled) AppTheme.colors.textPrimary else AppTheme.colors.textTertiary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = device.selectionReason(),
        style = MaterialTheme.typography.bodyMedium,
        color = AppTheme.colors.textSecondary,
      )
    }
    if (device.isSelectableForSecurity()) {
      Icon(
        imageVector = if (selected) {
          Icons.Outlined.CheckCircle
        } else {
          Icons.Outlined.RadioButtonUnchecked
        },
        contentDescription = null,
        tint = if (selected) AppTheme.colors.accent else AppTheme.colors.textTertiary,
        modifier = Modifier.size(TickSize),
      )
    }
  }
}

/**
 * The activity-zone half of the step. It only appears once a selection has been written, because
 * the backend reports zones per home rather than per camera and the sample has nothing to show
 * until monitoring knows which cameras it covers.
 */
@Composable
private fun ZoneSection(
  state: SecurityCameraUiState,
  onToggleZone: (Device, Boolean) -> Unit,
) {
  val zoneDevices = state.zoneDevices.filter { state.supportsZones(it) }
  if (state.savedIds.isEmpty() || zoneDevices.isEmpty()) return
  SectionHeader(text = "Activity zones")
  Text(
    text = ZONE_BODY,
    style = MaterialTheme.typography.bodyMedium,
    color = AppTheme.colors.textSecondary,
  )
  GroupCard {
    zoneDevices.forEachIndexed { index, device ->
      ToggleRow(
        title = device.name,
        checked = state.zoneEnabled(device),
        onCheckedChange = { enabled -> onToggleZone(device, enabled) },
        description = if (device.isOnline()) device.modelName else "Camera offline",
        enabled = device.isOnline() && state.zoneBusyId == null,
      )
      if (index != zoneDevices.lastIndex) RowDivider()
    }
  }
  InfoNote(text = ZONE_NOTE)
}

/** Gap between the hero's headline and the line under it. */
private val HeroGap = 8.dp

/** Horizontal padding inside a camera row, matching the design system's own rows. */
private val RowPadding = 16.dp

/** Vertical padding inside a camera row. */
private val RowVerticalPadding = 12.dp

/** Gap between a camera row's text block and its tick. */
private val RowSpacing = 12.dp

/** Gap between a camera's name and its model line. */
private val TitleGap = 4.dp

/** Size of the tick that marks a selected camera. */
private val TickSize = 22.dp

/** Breathing room above and below the screen's bottom-bar button. */
private val BottomBarPadding = 12.dp
