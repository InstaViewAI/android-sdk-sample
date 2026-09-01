package ai.instavision.sandbox.ui.events

import ai.instavision.sandbox.ui.theme.AppTheme
import ai.instavision.guardian.sdk.data.entity.Event
import ai.instavision.guardian.sdk.data.enums.EventTag
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.SensorsOff
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * One captured event as a list row: what was detected, which camera saw it, how long ago and for
 * how long. Shared by the events tab and the home screen's recent-activity card, so it brings no
 * card, divider or background of its own.
 *
 * A non-null [selected] puts the row in selection mode and prefixes it with a radio circle; pass
 * null whenever the list is not being edited. [showThumbnail] is what the compact home variant
 * turns off.
 */
@Composable
fun EventRow(
  event: Event,
  modifier: Modifier = Modifier,
  showThumbnail: Boolean = true,
  selected: Boolean? = null,
  onClick: (() -> Unit)? = null,
) {
  val style = eventTagStyle(event.tags)
  Row(
    modifier = modifier
      .fillMaxWidth()
      .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
      .padding(horizontal = RowHorizontalPadding, vertical = RowVerticalPadding),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(RowSpacing),
  ) {
    if (selected != null) {
      Icon(
        imageVector = if (selected) {
          Icons.Rounded.CheckCircle
        } else {
          Icons.Rounded.RadioButtonUnchecked
        },
        contentDescription = null,
        tint = if (selected) AppTheme.colors.accent else AppTheme.colors.textTertiary,
        modifier = Modifier.size(SelectionIconSize),
      )
    }
    EventThumbnail(snapshot = if (showThumbnail) event.snapShot else null)
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
        text = event.deviceName.orEmpty().ifBlank { UnknownCamera },
        style = MaterialTheme.typography.bodyMedium,
        color = AppTheme.colors.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    Column(horizontalAlignment = Alignment.End) {
      Text(
        text = relativeTime(event.startTime),
        style = MaterialTheme.typography.bodyMedium,
        color = AppTheme.colors.textSecondary,
      )
      Text(
        text = eventDuration(event),
        style = MaterialTheme.typography.bodySmall,
        color = AppTheme.colors.textTertiary,
      )
    }
  }
}

/** How an event's leading tag is presented, so every screen labels the same event identically. */
internal data class EventTagStyle(
  /** Human-readable name of the detection, such as "Person". */
  val label: String,
  /** Glyph that stands in for the detection. */
  val icon: ImageVector,
  /** Tint shared by the glyph and the label. */
  val color: Color,
)

/**
 * Resolves the presentation of the most specific entry of [tags]. Anything unrecognised falls back
 * to motion, which is what the backend sends alongside every other detection anyway. It takes the
 * tags rather than an event so that a security log entry, which carries tags without an event, is
 * labelled identically.
 */
@Composable
internal fun eventTagStyle(tags: List<String>): EventTagStyle {
  val colors = AppTheme.colors
  return when (StyledTags.firstOrNull { it.value in tags }) {
    EventTag.PERSON -> EventTagStyle(
      label = "Person",
      icon = Icons.Outlined.Person,
      color = colors.accent,
    )
    EventTag.VEHICLE -> EventTagStyle(
      label = "Vehicle",
      icon = Icons.Outlined.DirectionsCar,
      color = colors.accent,
    )
    EventTag.ANIMAL -> EventTagStyle(
      label = "Animal",
      icon = Icons.Outlined.Pets,
      color = colors.accent,
    )
    EventTag.PET -> EventTagStyle(
      label = "Pet",
      icon = Icons.Outlined.Pets,
      color = colors.accent,
    )
    EventTag.DOORBELL -> EventTagStyle(
      label = "Doorbell",
      icon = Icons.Outlined.Notifications,
      color = colors.accent,
    )
    EventTag.ALARM -> EventTagStyle(
      label = "Alarm",
      icon = Icons.Outlined.Warning,
      color = colors.danger,
    )
    else -> EventTagStyle(
      label = "Motion",
      icon = Icons.Outlined.SensorsOff,
      color = colors.textSecondary,
    )
  }
}

/** How long the clip ran, in whole seconds; an event still being recorded has no end yet. */
internal fun eventDuration(event: Event): String {
  val end = event.endTime ?: return ZeroDuration
  val seconds = ((end - event.startTime) / MillisPerSecond).coerceAtLeast(0L)
  return "${seconds}s"
}

/** Age of [startTime] as the coarsest unit that still reads naturally, such as `"16h ago"`. */
internal fun relativeTime(startTime: Long): String {
  val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(0L)
  val minutes = elapsed / MillisPerMinute
  val hours = elapsed / MillisPerHour
  val days = elapsed / MillisPerDay
  return when {
    minutes < 1 -> "Just now"
    hours < 1 -> "${minutes}m ago"
    days < 1 -> "${hours}h ago"
    else -> "${days}d ago"
  }
}

/** The clip's still frame, or a struck-through camera when there is none to show. */
@Composable
internal fun EventThumbnail(snapshot: String?) {
  Box(
    modifier = Modifier
      .width(ThumbnailWidth)
      .height(ThumbnailHeight)
      .clip(MaterialTheme.shapes.small)
      .background(color = AppTheme.colors.surfaceHigh),
    contentAlignment = Alignment.Center,
  ) {
    if (snapshot == null) {
      Icon(
        imageVector = Icons.Outlined.VideocamOff,
        contentDescription = null,
        tint = AppTheme.colors.textTertiary,
        modifier = Modifier.size(PlaceholderIconSize),
      )
    } else {
      AsyncImage(
        model = snapshot,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
      )
    }
  }
}

/** Tags with a dedicated treatment, ordered so a specific detection outranks bare motion. */
private val StyledTags = listOf(
  EventTag.PERSON,
  EventTag.VEHICLE,
  EventTag.ANIMAL,
  EventTag.PET,
  EventTag.DOORBELL,
  EventTag.ALARM,
  EventTag.MOTION,
)

/** Duration shown for a clip whose recording has not finished. */
private const val ZeroDuration = "0s"

/** Stand-in name for an event whose camera has since been removed or was never named. */
private const val UnknownCamera = "Camera"

/** Milliseconds in one second, used to turn a clip's span into a duration label. */
private const val MillisPerSecond = 1_000L

/** Milliseconds in one minute. */
private const val MillisPerMinute = 60_000L

/** Milliseconds in one hour. */
private const val MillisPerHour = 3_600_000L

/** Milliseconds in one day. */
private const val MillisPerDay = 86_400_000L

/** Width of the row's still frame. */
private val ThumbnailWidth = 72.dp

/** Height of the row's still frame. */
private val ThumbnailHeight = 56.dp

/** Size of the glyph shown in place of a missing still frame. */
private val PlaceholderIconSize = 20.dp

/** Horizontal padding of the row, matching the rows a `GroupCard` normally holds. */
private val RowHorizontalPadding = 16.dp

/** Vertical padding of the row, tightened so the thumbnail sets the row height. */
private val RowVerticalPadding = 12.dp

/** Gap between the row's thumbnail, text block and timestamps. */
private val RowSpacing = 12.dp

/** Gap between the tag glyph and its label. */
private val TagSpacing = 6.dp

/** Size of the tag glyph. */
private val TagIconSize = 16.dp

/** Size of the leading selection circle. */
private val SelectionIconSize = 22.dp
