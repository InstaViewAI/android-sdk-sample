package ai.instavision.sandbox.ui.security

import ai.instavision.sandbox.ui.common.GroupCard
import ai.instavision.sandbox.ui.common.RowDivider
import ai.instavision.sandbox.ui.common.SettingRow
import ai.instavision.sandbox.ui.common.StatusPill
import ai.instavision.sandbox.ui.events.EventThumbnail
import ai.instavision.sandbox.ui.events.eventTagStyle
import ai.instavision.sandbox.ui.theme.AppTheme
import ai.instavision.guardian.sdk.data.entity.ArmedDevices
import ai.instavision.guardian.sdk.data.entity.ArmedLog
import ai.instavision.guardian.sdk.data.entity.DisarmedLog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Said in place of the event rows for a session the cameras saw nothing during. */
private const val SAFE_AND_SECURE = "Safe and secure, nothing was detected"

/** Stand-in name for a camera the log recorded without one. */
private const val UNKNOWN_CAMERA = "Camera"

/**
 * One arming session as a card: when the system stood down, what the cameras saw while it was
 * live, and when it was armed — newest first, so the card reads top to bottom the way the list
 * around it does.
 *
 * Passing [onEvent] makes the detection rows tappable and hands back the event id to open; leave
 * it null wherever the card is a summary rather than a way in.
 */
@Composable
internal fun SecurityLogCard(
  session: SecuritySession,
  modifier: Modifier = Modifier,
  onEvent: ((String) -> Unit)? = null,
) {
  var showDevices by remember(session.id) { mutableStateOf(false) }
  GroupCard(modifier = modifier) {
    if (session.disarmedAt != null) {
      DisarmedRow(disarmedAt = session.disarmedAt, log = session.disarmed)
      RowDivider()
    }
    if (session.events.isEmpty()) {
      SettingRow(label = SAFE_AND_SECURE, icon = Icons.Outlined.VerifiedUser)
    } else {
      session.events.forEachIndexed { index, entry ->
        if (index > 0) RowDivider()
        SecurityLogEventRow(
          entry = entry,
          onClick = onEvent?.let { open -> { open(entry.event.id) } },
        )
      }
    }
    RowDivider()
    ArmedRow(
      armedAt = session.armedAt,
      log = session.armed,
      expanded = showDevices,
      onToggle = { showDevices = !showDevices },
    )
    if (showDevices) {
      session.armed?.devices.orEmpty().forEach { device ->
        RowDivider()
        SettingRow(
          label = device.name.orEmpty().ifBlank { UNKNOWN_CAMERA },
          value = armStatusLabel(device),
        )
      }
    }
  }
}

/** The line that closes a session: when the system stood down, by which method and for whom. */
@Composable
private fun DisarmedRow(disarmedAt: Long, log: DisarmedLog?) {
  val method = log?.disarmMethod.orEmpty()
  val name = log?.user?.name.orEmpty()
  val trailing: @Composable (() -> Unit)? = if (method.isBlank()) {
    null
  } else {
    { MutedPill(text = method) }
  }
  SettingRow(
    label = if (name.isBlank()) "Disarmed" else "Disarmed by $name",
    icon = Icons.Outlined.LockOpen,
    value = logTimestamp(disarmedAt),
    trailing = trailing,
  )
}

/**
 * The line that opens a session, counting the cameras the arm request covered. The row only
 * becomes a control once there are camera names behind the count to expand to.
 */
@Composable
private fun ArmedRow(armedAt: Long, log: ArmedLog?, expanded: Boolean, onToggle: () -> Unit) {
  val devices = log?.devices.orEmpty()
  val trailing: @Composable (() -> Unit)? = if (devices.isEmpty()) {
    null
  } else {
    { ArmedDevicesPill(devices = devices, expanded = expanded) }
  }
  SettingRow(
    label = "Armed",
    icon = Icons.Outlined.Shield,
    value = logTimestamp(armedAt),
    trailing = trailing,
    onClick = if (devices.isEmpty()) null else onToggle,
  )
}

/** The camera count of an arm request, dotted green unless one of them failed to arm. */
@Composable
private fun ArmedDevicesPill(devices: List<ArmedDevices>, expanded: Boolean) {
  val failed = devices.any { it.armStatus != ArmStatus.SUCCESS && it.armStatus != ArmStatus.ARMING }
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(PillSpacing),
  ) {
    StatusPill(
      text = if (devices.size == 1) "1 camera" else "${devices.size} cameras",
      dotColor = if (failed) AppTheme.colors.warning else AppTheme.colors.success,
      containerColor = AppTheme.colors.surfaceHigh,
      contentColor = AppTheme.colors.textSecondary,
    )
    Icon(
      imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
      contentDescription = null,
      tint = AppTheme.colors.textTertiary,
      modifier = Modifier.size(ChevronSize),
    )
  }
}

/** A pill for a value that is context rather than status, such as how a system was disarmed. */
@Composable
private fun MutedPill(text: String) {
  StatusPill(
    text = text,
    containerColor = AppTheme.colors.surfaceHigh,
    contentColor = AppTheme.colors.textSecondary,
  )
}

/**
 * One detection inside a session, laid out like the row the events tab uses so the same detection
 * reads the same in both places. It is only a control when [onClick] is given.
 */
@Composable
private fun SecurityLogEventRow(entry: SecurityLogEvent, onClick: (() -> Unit)?) {
  val style = eventTagStyle(entry.event.tags.orEmpty())
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
      .padding(horizontal = RowHorizontalPadding, vertical = RowVerticalPadding),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(RowSpacing),
  ) {
    EventThumbnail(snapshot = entry.snapshotUrl)
    Column(modifier = Modifier.weight(1f)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TagSpacing),
      ) {
        Icon(
          imageVector = style.icon,
          contentDescription = null,
          tint = style.color,
          modifier = Modifier.size(TagIconSize),
        )
        Text(
          text = style.label,
          style = MaterialTheme.typography.titleMedium,
          color = style.color,
        )
      }
      Text(
        text = entry.event.deviceName.orEmpty().ifBlank { UNKNOWN_CAMERA },
        style = MaterialTheme.typography.bodyMedium,
        color = AppTheme.colors.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    Text(
      text = logTimestamp(entry.createdAt),
      style = MaterialTheme.typography.bodyMedium,
      color = AppTheme.colors.textSecondary,
    )
  }
}

/** How one camera answered the arm request it was part of. */
private fun armStatusLabel(device: ArmedDevices): String = when {
  device.armStatus == ArmStatus.SUCCESS -> "Armed"
  device.armStatus == ArmStatus.ARMING -> "Arming"
  device.lowBattery == ArmStatus.LOW_BATTERY -> "Low battery"
  device.armStatus == ArmStatus.FAILURE -> "Failed to arm"
  else -> "Unknown"
}

/**
 * Stamps a log entry with its clock time, prefixed by the day once it is older than today. The
 * time is what distinguishes entries inside a session, so it is never the part that is dropped.
 */
internal fun logTimestamp(millis: Long): String {
  val zone = ZoneId.systemDefault()
  val moment = Instant.ofEpochMilli(millis).atZone(zone)
  val today = LocalDate.now(zone)
  val time = moment.format(TimeFormatter)
  return when (moment.toLocalDate()) {
    today -> time
    today.minusDays(1) -> "Yesterday $time"
    else -> "${moment.format(DateFormatter)} $time"
  }
}

/** How the clock time of a log entry is printed. */
private val TimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

/** How the day of a log entry older than yesterday is printed. */
private val DateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")

/** Gap between the camera count and the chevron that expands it. */
private val PillSpacing = 6.dp

/** Size of the chevron that expands an arm request into its cameras. */
private val ChevronSize = 20.dp

/** Horizontal padding of a detection row, matching the rows a `GroupCard` normally holds. */
private val RowHorizontalPadding = 16.dp

/** Vertical padding of a detection row, tightened so the thumbnail sets the row height. */
private val RowVerticalPadding = 12.dp

/** Gap between a detection row's thumbnail, text block and timestamp. */
private val RowSpacing = 12.dp

/** Gap between a detection's tag glyph and its label. */
private val TagSpacing = 6.dp

/** Size of a detection's tag glyph. */
private val TagIconSize = 16.dp
