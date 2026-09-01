package ai.instavision.sandbox.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ai.instavision.sandbox.ui.theme.AppTheme

/**
 * The grouped rounded container every list of rows sits in. It is clipped, so children cannot
 * escape the corners, and it adds no padding of its own — rows bring their own.
 */
@Composable
fun GroupCard(
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(CardShape)
      .background(color = AppTheme.colors.surface)
      .border(width = HairlineWidth, color = AppTheme.colors.outline, shape = CardShape),
    content = content,
  )
}

/** The inset hairline callers place between two rows of a [GroupCard]. */
@Composable
fun RowDivider() {
  HorizontalDivider(
    modifier = Modifier.padding(start = RowPadding),
    thickness = HairlineWidth,
    color = AppTheme.colors.outline,
  )
}

/**
 * A horizontal rule broken by a centred [label], used to separate two ways of doing the same
 * thing — "or" between a form and a social sign-in button.
 */
@Composable
fun LabelledDivider(
  label: String,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    HorizontalDivider(
      modifier = Modifier.weight(1f),
      thickness = HairlineWidth,
      color = AppTheme.colors.outline,
    )
    Text(
      text = label,
      modifier = Modifier.padding(horizontal = DividerLabelPadding),
      style = MaterialTheme.typography.bodyMedium,
      color = AppTheme.colors.textSecondary,
    )
    HorizontalDivider(
      modifier = Modifier.weight(1f),
      thickness = HairlineWidth,
      color = AppTheme.colors.outline,
    )
  }
}

/**
 * One line of a grouped list: an optional accent [icon], a [label], an optional current [value]
 * and an optional [trailing] slot. Without [onClick] the row is a read-only readout, not a control.
 */
@Composable
fun SettingRow(
  label: String,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  value: String? = null,
  showChevron: Boolean = false,
  enabled: Boolean = true,
  trailing: @Composable (() -> Unit)? = null,
  onClick: (() -> Unit)? = null,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .then(
        if (onClick == null) Modifier else Modifier.clickable(enabled = enabled, onClick = onClick),
      )
      .padding(horizontal = RowPadding, vertical = RowVerticalPadding),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(RowSpacing),
  ) {
    if (icon != null) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (enabled) AppTheme.colors.accent else AppTheme.colors.textTertiary,
        modifier = Modifier.size(RowIconSize),
      )
    }
    Text(
      text = label,
      style = MaterialTheme.typography.bodyLarge,
      color = if (enabled) AppTheme.colors.textPrimary else AppTheme.colors.textTertiary,
      modifier = Modifier.weight(1f),
    )
    if (value != null) {
      Text(
        text = value,
        style = MaterialTheme.typography.bodyLarge,
        color = AppTheme.colors.textSecondary,
        textAlign = TextAlign.End,
      )
    }
    trailing?.invoke()
    if (showChevron) {
      Icon(
        imageVector = Icons.Rounded.ChevronRight,
        contentDescription = null,
        tint = AppTheme.colors.textTertiary,
        modifier = Modifier.size(RowIconSize),
      )
    }
  }
}

/**
 * A [SettingRow] whose control is a switch. [description] explains the effect of the toggle and
 * sits beneath the title, which is where the longer settings copy belongs.
 */
@Composable
fun ToggleRow(
  title: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  description: String? = null,
  enabled: Boolean = true,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = RowPadding, vertical = RowVerticalPadding),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(RowSpacing),
  ) {
    if (icon != null) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (enabled) AppTheme.colors.accent else AppTheme.colors.textTertiary,
        modifier = Modifier.size(RowIconSize),
      )
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        color = if (enabled) AppTheme.colors.textPrimary else AppTheme.colors.textTertiary,
      )
      if (description != null) {
        Text(
          text = description,
          style = MaterialTheme.typography.bodyMedium,
          color = AppTheme.colors.textSecondary,
        )
      }
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      enabled = enabled,
      colors = SwitchDefaults.colors(
        checkedThumbColor = AppTheme.colors.textPrimary,
        checkedTrackColor = AppTheme.colors.accent,
        checkedBorderColor = AppTheme.colors.accent,
        uncheckedThumbColor = AppTheme.colors.textSecondary,
        uncheckedTrackColor = AppTheme.colors.surfaceHigh,
        uncheckedBorderColor = AppTheme.colors.outline,
      ),
    )
  }
}

/**
 * A small capsule label: the "Online" overlay, the green "Verified" badge, the violet "Person" tag.
 * A non-null [dotColor] prefixes the text with a filled status dot.
 */
@Composable
fun StatusPill(
  text: String,
  modifier: Modifier = Modifier,
  dotColor: Color? = null,
  containerColor: Color = Color.Black.copy(alpha = ScrimAlpha),
  contentColor: Color = AppTheme.colors.textPrimary,
) {
  Row(
    modifier = modifier
      .clip(CircleShape)
      .background(color = containerColor)
      .padding(horizontal = PillHorizontalPadding, vertical = PillVerticalPadding),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(PillSpacing),
  ) {
    if (dotColor != null) {
      Box(
        modifier = Modifier
          .size(DotSize)
          .clip(CircleShape)
          .background(color = dotColor),
      )
    }
    Text(
      text = text,
      style = MaterialTheme.typography.labelMedium,
      color = contentColor,
    )
  }
}

/** The circular stand-in for a person's photo, showing the first letter of their name. */
@Composable
fun Avatar(
  initial: String,
  modifier: Modifier = Modifier,
  size: Dp = 56.dp,
) {
  Box(
    modifier = modifier
      .size(size)
      .clip(CircleShape)
      .background(color = AppTheme.colors.accentSoft),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = initial.uppercase(),
      style = MaterialTheme.typography.titleLarge,
      color = AppTheme.colors.accent,
    )
  }
}

/** Corner radius of a [GroupCard]. */
private val CardShape = RoundedCornerShape(16.dp)

/** Width of card borders and row dividers. */
private val HairlineWidth = 1.dp

/** Clear space either side of a [LabelledDivider]'s label. */
private val DividerLabelPadding = 16.dp

/** Horizontal padding inside a row, and the distance a [RowDivider] is inset from the start edge. */
private val RowPadding = 16.dp

/** Vertical padding inside a row, which is what sets the row's height around its tallest part. */
private val RowVerticalPadding = 12.dp

/** Gap between the parts of a row. */
private val RowSpacing = 12.dp

/** Size of a row's leading icon and trailing chevron. */
private val RowIconSize = 22.dp

/** Diameter of the status dot inside a [StatusPill]. */
private val DotSize = 8.dp

/** Horizontal padding of a [StatusPill]. */
private val PillHorizontalPadding = 10.dp

/** Vertical padding of a [StatusPill]. */
private val PillVerticalPadding = 5.dp

/** Gap between a [StatusPill]'s dot and its text. */
private val PillSpacing = 6.dp

/** Opacity of the default pill fill, which sits over camera thumbnails. */
private const val ScrimAlpha = 0.55f
