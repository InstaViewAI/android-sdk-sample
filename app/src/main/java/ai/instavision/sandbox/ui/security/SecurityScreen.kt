package ai.instavision.sandbox.ui.security

import ai.instavision.sandbox.data.SessionStore
import ai.instavision.sandbox.ui.common.CircleIconButton
import ai.instavision.sandbox.ui.common.EmptyState
import ai.instavision.sandbox.ui.common.ErrorBanner
import ai.instavision.sandbox.ui.common.GroupCard
import ai.instavision.sandbox.ui.common.InfoNote
import ai.instavision.sandbox.ui.common.LoadingBox
import ai.instavision.sandbox.ui.common.Notice
import ai.instavision.sandbox.ui.common.PrimaryButton
import ai.instavision.sandbox.ui.common.RootScaffold
import ai.instavision.sandbox.ui.common.RowDivider
import ai.instavision.sandbox.ui.common.SectionHeader
import ai.instavision.sandbox.ui.common.SettingRow
import ai.instavision.sandbox.ui.common.StatusPill
import ai.instavision.sandbox.ui.common.TextLink
import ai.instavision.sandbox.ui.events.relativeTime
import ai.instavision.sandbox.ui.theme.AppTheme
import ai.instavision.guardian.sdk.data.entity.DisarmedLog
import ai.instavision.guardian.sdk.data.entity.SecurityAddress
import ai.instavision.guardian.sdk.data.entity.SecurityDevice
import ai.instavision.guardian.sdk.data.entity.SecurityLog
import ai.instavision.guardian.sdk.data.entity.isOnline
import ai.instavision.guardian.sdk.data.entity.primarySnapshotUrl
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.google.gson.Gson

/** Said to the owner of a home whose plan does not cover monitoring, in place of the setup entry. */
private const val UPSELL_BODY =
  "Professional monitoring is not on this home's plan yet. Add it to your subscription to set it up."

/** Said in place of the setup button when the home has no camera monitoring could arm. */
private const val NO_CAMERA_BODY =
  "Monitoring needs a home security camera. Pair one with this home before starting setup."

/** Stands in for the activity summary of a home the system has never been armed in. */
private const val NO_ACTIVITY = "No activity yet"

/** Said to everyone but the owner while the home's checklist is unfinished; only the owner can. */
private const val NOT_OWNER_BODY =
  "The owner of this home has not finished setting monitoring up. Only they can complete it."

/** The caption under the dial while nothing is armed, which is the whole of what disarmed means. */
private const val NOT_MONITORED = "Nothing is being monitored right now."

/** Stand-in name for a camera the profile lists but the device cache has not resolved. */
private const val UNKNOWN_CAMERA = "Camera"

/** What test mode changes, spelled out because the word alone reads like a harmless setting. */
private const val TEST_MODE_BODY = "Alarms are not passed to the monitoring centre."

/** How many arm and disarm lines the tab previews before handing over to the full log. */
private const val MaxLogPreviewRows = 4

/**
 * Security tab root: the state of the alarm, the slide control that flips it, the cameras
 * monitoring covers and the newest arm and disarm lines of the log. Setup is only ever offered to
 * the owner of a home whose plan covers monitoring; everyone else is told why the tab is not
 * theirs.
 *
 * [onLog] opens the full log, [onEditCameras] the camera step of setup, and [onSettings] the
 * monitoring settings behind the header's gear.
 */
@Composable
fun SecurityScreen(
  onSetup: () -> Unit,
  onLog: () -> Unit,
  onSettings: () -> Unit,
  onEditCameras: () -> Unit,
) {
  val viewModel: SecurityViewModel = viewModel()
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  LifecycleResumeEffect(Unit) {
    viewModel.refreshOnResume()
    onPauseOrDispose {}
  }

  RootScaffold(
    title = "Security",
    subtitle = state.profile?.address.dispatchLine(),
    action = {
      CircleIconButton(
        icon = Icons.Outlined.Settings,
        contentDescription = "Monitoring settings",
        onClick = onSettings,
      )
    },
  ) {
    ErrorBanner(message = state.error)
    when {
      state.loading -> LoadingBox()
      !state.entitled && state.isOwner -> UpsellPrompt()
      state.isOwner && !state.setupComplete -> SetupPrompt(
        started = state.started,
        canSetup = state.canSetup,
        onSetup = onSetup,
      )

      !state.setupComplete -> EmptyState(
        title = "Setup is not finished",
        body = NOT_OWNER_BODY,
        icon = Icons.Outlined.Shield,
      )

      else -> {
        StatusDial(
          status = state.status,
          modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .padding(vertical = DialPadding),
        )
        MonitoringCaption(status = state.status, cameras = state.cameras.size)
        SlideToArm(
          armed = state.armed,
          busy = state.busy,
          onComplete = { if (state.armed) viewModel.disarm() else viewModel.arm() },
        )
        if (state.profile?.testMode == true) TestModeBanner()
        CameraSection(cameras = state.cameras, onEditCameras = onEditCameras)
        LogSection(logs = state.logs, loading = state.logsLoading, onLog = onLog)
      }
    }
  }
}

/**
 * The state of the alarm as one large dial: a ring, a wash of the same hue inside it and a padlock
 * over the status word. While the backend is still moving between two states the ring becomes a
 * spinner, so a system mid-arm never looks like one that has settled.
 */
@Composable
private fun StatusDial(status: String, modifier: Modifier = Modifier) {
  val live = status.isArmedLike()
  val tint = if (live) AppTheme.colors.danger else AppTheme.colors.success
  Box(
    modifier = modifier.size(DialSize),
    contentAlignment = Alignment.Center,
  ) {
    if (status.isSettling()) {
      CircularProgressIndicator(
        modifier = Modifier.fillMaxSize(),
        color = tint,
        strokeWidth = DialRing,
      )
    } else {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .border(width = DialRing, color = tint, shape = CircleShape),
      )
    }
    Box(
      modifier = Modifier
        .size(DialDiscSize)
        .clip(CircleShape)
        .background(color = tint.copy(alpha = DialDiscAlpha)),
    )
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(DialGap),
    ) {
      Icon(
        imageVector = if (live) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(DialIconSize),
      )
      Text(
        text = status.dialLabel(),
        style = MaterialTheme.typography.headlineSmall,
        color = tint,
      )
    }
  }
}

/** The one grey line under the dial saying what the state means for the home's cameras. */
@Composable
private fun MonitoringCaption(status: String, cameras: Int) {
  Text(
    text = if (status.isArmedLike()) monitoredCaption(cameras) else NOT_MONITORED,
    modifier = Modifier.fillMaxWidth(),
    style = MaterialTheme.typography.bodyMedium,
    color = AppTheme.colors.textSecondary,
    textAlign = TextAlign.Center,
  )
}

/** The amber warning that this home's alarms stop at the app rather than reaching anyone. */
@Composable
private fun TestModeBanner() {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(MaterialTheme.shapes.medium)
      .background(color = AppTheme.colors.warningContainer)
      .border(
        width = BannerBorder,
        color = AppTheme.colors.warning,
        shape = MaterialTheme.shapes.medium,
      )
      .padding(BannerPadding),
    verticalAlignment = Alignment.Top,
    horizontalArrangement = Arrangement.spacedBy(BannerSpacing),
  ) {
    Icon(
      imageVector = Icons.Outlined.WarningAmber,
      contentDescription = null,
      tint = AppTheme.colors.warning,
      modifier = Modifier.size(BannerIconSize),
    )
    Column(verticalArrangement = Arrangement.spacedBy(TitleGap)) {
      Text(
        text = "Test mode is on",
        style = MaterialTheme.typography.titleMedium,
        color = AppTheme.colors.warning,
      )
      Text(
        text = TEST_MODE_BODY,
        style = MaterialTheme.typography.bodyMedium,
        color = AppTheme.colors.textSecondary,
      )
    }
  }
}

/** The cameras the profile arms, with the way back into the setup step that picks them. */
@Composable
private fun CameraSection(cameras: List<SecurityDevice>, onEditCameras: () -> Unit) {
  Column {
    SectionHeader(
      text = "Security cameras",
      action = { TextLink(text = "Edit", onClick = onEditCameras) },
    )
    GroupCard {
      if (cameras.isEmpty()) {
        SettingRow(label = "No cameras selected", enabled = false)
      } else {
        cameras.forEachIndexed { index, camera ->
          if (index > 0) RowDivider()
          CameraRow(camera = camera)
        }
      }
    }
  }
}

/**
 * One camera of the profile: its snapshot and name from the device cache, and the state the
 * profile itself reports for it, which is what says whether this camera followed the last arm.
 */
@Composable
private fun CameraRow(camera: SecurityDevice) {
  val device = SessionStore.devices.firstOrNull { it.id == camera.id }
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = RowPadding, vertical = RowVerticalPadding),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(RowSpacing),
  ) {
    CameraThumbnail(url = device?.primarySnapshotUrl())
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = device?.name.orEmpty().ifBlank { UNKNOWN_CAMERA },
        style = MaterialTheme.typography.titleMedium,
        color = AppTheme.colors.textPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = if (device?.isOnline() == true) "Online" else "Offline",
        style = MaterialTheme.typography.bodyMedium,
        color = AppTheme.colors.textSecondary,
      )
    }
    StatusPill(
      text = camera.state.orEmpty().ifBlank { SecurityStatus.DISARMED },
      dotColor = cameraStateColor(camera.state.orEmpty()),
      containerColor = AppTheme.colors.surfaceHigh,
      contentColor = AppTheme.colors.textSecondary,
    )
  }
}

/** The camera's still frame, or a camera glyph while the device cache has none to show. */
@Composable
private fun CameraThumbnail(url: String?) {
  Box(
    modifier = Modifier
      .size(ThumbnailSize)
      .clip(MaterialTheme.shapes.small)
      .background(color = AppTheme.colors.surfaceHigh),
    contentAlignment = Alignment.Center,
  ) {
    if (url.isNullOrBlank()) {
      Icon(
        imageVector = Icons.Outlined.Videocam,
        contentDescription = null,
        tint = AppTheme.colors.textTertiary,
        modifier = Modifier.size(ThumbnailIconSize),
      )
    } else {
      AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
      )
    }
  }
}

/**
 * The newest arm and disarm lines of the log as flat rows. Detections are deliberately left out:
 * this is a record of who put the system into which state, and the full log is where what the
 * cameras saw while it was armed belongs.
 */
@Composable
private fun LogSection(logs: List<SecurityLog>, loading: Boolean, onLog: () -> Unit) {
  val rows = remember(logs) { logs.armingRows(MaxLogPreviewRows) }
  Column {
    SectionHeader(
      text = "Security log",
      action = { TextLink(text = "See all", onClick = onLog) },
    )
    when {
      loading -> LoadingBox()
      rows.isEmpty() -> GroupCard { SettingRow(label = NO_ACTIVITY, enabled = false) }
      else -> Column { rows.forEach { row -> ArmingLogRow(row = row) } }
    }
  }
}

/** One arm or disarm of the log: a padlock badge, what happened, who did it and how long ago. */
@Composable
private fun ArmingLogRow(row: ArmingRow) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = RowVerticalPadding),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(RowSpacing),
  ) {
    Box(
      modifier = Modifier
        .size(BadgeSize)
        .clip(CircleShape)
        .background(
          color = if (row.armed) {
            AppTheme.colors.dangerContainer
          } else {
            AppTheme.colors.successContainer
          },
        ),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = if (row.armed) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
        contentDescription = null,
        tint = if (row.armed) AppTheme.colors.danger else AppTheme.colors.success,
        modifier = Modifier.size(BadgeIconSize),
      )
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = if (row.armed) "System armed" else "System disarmed",
        style = MaterialTheme.typography.titleMedium,
        color = AppTheme.colors.textPrimary,
      )
      if (row.actor.isNotBlank()) {
        Text(
          text = "by ${row.actor}",
          style = MaterialTheme.typography.bodyMedium,
          color = AppTheme.colors.textSecondary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    Text(
      text = relativeTime(row.createdAt),
      style = MaterialTheme.typography.bodyMedium,
      color = AppTheme.colors.textSecondary,
    )
  }
}

/**
 * Stands in for the whole tab until the checklist reaches its terminal step: the case for
 * monitoring, then the button that opens the checklist. The button is dead until the home has a
 * camera monitoring can arm, because the checklist cannot be finished without one.
 */
@Composable
private fun SetupPrompt(started: Boolean, canSetup: Boolean, onSetup: () -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(PromptGap)) {
    MonitoringIntroContent()
    if (!canSetup) InfoNote(text = NO_CAMERA_BODY)
    PrimaryButton(
      text = if (started) "Continue setup" else "Start setup",
      onClick = onSetup,
      enabled = canSetup,
    )
  }
}

/** The case for monitoring with no way in, shown to an owner whose plan does not include it. */
@Composable
private fun UpsellPrompt() {
  Column(verticalArrangement = Arrangement.spacedBy(PromptGap)) {
    MonitoringIntroContent()
    Notice(message = UPSELL_BODY)
  }
}

/** One arm or disarm of the log, with its payload already read for the name behind it. */
private data class ArmingRow(
  /** Identifier of the log entry, which is what keys the row. */
  val id: String,
  /** When the entry was written, which the row renders as an age. */
  val createdAt: Long,
  /** True for an `Armed` entry, false for a `Disarmed` one; nothing else becomes a row. */
  val armed: Boolean,
  /** Who the payload named, or blank when it named nobody. */
  val actor: String,
)

/**
 * Picks the newest [limit] arm and disarm entries out of a raw log window and reads the name off
 * each one. Only a disarm carries a user in its payload — the SDK's `ArmedLog` has no such field —
 * so an arm row never gets a "by" line.
 */
private fun List<SecurityLog>.armingRows(limit: Int): List<ArmingRow> =
  asSequence()
    .filter { it.type == SecurityLogTypes.ARMED || it.type == SecurityLogTypes.DISARMED }
    .take(limit)
    .map { log ->
      val armed = log.type == SecurityLogTypes.ARMED
      ArmingRow(
        id = log.id,
        createdAt = log.createdAt,
        armed = armed,
        actor = if (armed) "" else log.properties.readAs<DisarmedLog>()?.user?.name.orEmpty(),
      )
    }
    .toList()

/**
 * Reads a log entry's `properties` as [T], the way the full log's own cards do. The SDK types the
 * field as `Any` and Gson hands it over as a map, so it is re-serialised and read back; a payload
 * of any other shape yields null rather than throwing.
 */
private inline fun <reified T> Any?.readAs(): T? =
  runCatching { RowGson.fromJson(RowGson.toJson(this), T::class.java) }.getOrNull()

/** The single-line dispatch address the header carries, or null when the profile holds none. */
private fun SecurityAddress?.dispatchLine(): String? =
  listOfNotNull(this?.lineOne, this?.city, this?.state, this?.zipCode)
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .joinToString(", ")
    .ifEmpty { null }

/** Whether the status word means the alarm is live or on its way to being, which is one dial. */
private fun String.isArmedLike(): Boolean =
  this == SecurityStatus.ARMED || this == SecurityStatus.ARMING

/** Whether the backend is still moving between two states, which is what the spinner reports. */
private fun String.isSettling(): Boolean =
  this == SecurityStatus.ARMING || this == SecurityStatus.DISARMING

/** The word inside the dial; an unrecognised status reads as stood down rather than as itself. */
private fun String.dialLabel(): String = when (this) {
  SecurityStatus.ARMED -> "Armed"
  SecurityStatus.ARMING -> "Arming"
  SecurityStatus.DISARMING -> "Disarming"
  else -> "Disarmed"
}

/** The caption for an armed home, counting the cameras its profile put under monitoring. */
private fun monitoredCaption(cameras: Int): String = when (cameras) {
  0 -> "The system is armed, but no camera is monitoring."
  1 -> "1 camera is being monitored."
  else -> "$cameras cameras are being monitored."
}

/** The dot beside a camera's own state: green once it followed the arm, amber while it has not. */
@Composable
private fun cameraStateColor(state: String): Color = when (state) {
  SecurityStatus.ARMED -> AppTheme.colors.success
  SecurityStatus.FAILED -> AppTheme.colors.danger
  SecurityStatus.DISARMED -> AppTheme.colors.textTertiary
  else -> AppTheme.colors.warning
}

/** Turns a log's `properties` map back into JSON so it can be read as the payload type it is. */
private val RowGson = Gson()

/** Diameter of the status dial, which is the screen's focal point. */
private val DialSize = 250.dp

/** Width of the dial's outer ring, and of the spinner that replaces it mid-transition. */
private val DialRing = 3.dp

/** Diameter of the tinted disc inside the dial's ring. */
private val DialDiscSize = 200.dp

/** Opacity of that disc, low enough for the padlock over it to stay legible. */
private const val DialDiscAlpha = 0.12f

/** Size of the padlock at the centre of the dial. */
private val DialIconSize = 64.dp

/** Gap between the dial's padlock and its status word. */
private val DialGap = 8.dp

/** Breathing room above and below the dial. */
private val DialPadding = 8.dp

/** Width of the test mode banner's outline. */
private val BannerBorder = 1.dp

/** Padding inside the test mode banner. */
private val BannerPadding = 16.dp

/** Gap between the banner's warning glyph and its text. */
private val BannerSpacing = 12.dp

/** Size of the banner's warning glyph. */
private val BannerIconSize = 22.dp

/** Diameter of the padlock badge that opens a log row. */
private val BadgeSize = 36.dp

/** Size of the padlock inside that badge. */
private val BadgeIconSize = 18.dp

/** Edge length of a camera row's snapshot. */
private val ThumbnailSize = 44.dp

/** Size of the glyph standing in for a snapshot that has not been fetched. */
private val ThumbnailIconSize = 20.dp

/** Horizontal padding of a camera row, matching the rows a `GroupCard` normally holds. */
private val RowPadding = 16.dp

/** Vertical padding of a camera or log row. */
private val RowVerticalPadding = 12.dp

/** Gap between the parts of a camera or log row. */
private val RowSpacing = 12.dp

/** Gap between a block's title and the line under it. */
private val TitleGap = 4.dp

/** Gap the setup prompt leaves between the monitoring pitch and the button under it. */
private val PromptGap = 24.dp
