package ai.instavision.sandbox.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ai.instavision.sandbox.ui.theme.AppTheme

/**
 * The call to action of a screen, filled with the brand gradient. [loading] swaps the label for a
 * spinner and blocks taps; [enabled] flattens the fill to a disabled grey instead. Clearing
 * [fillWidth] lets the button hug its label rather than span the screen.
 */
@Composable
fun PrimaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  loading: Boolean = false,
  fillWidth: Boolean = true,
) {
  val fill = if (enabled) {
    Modifier.background(
      brush = Brush.horizontalGradient(
        listOf(AppTheme.colors.accentStart, AppTheme.colors.accentEnd),
      ),
    )
  } else {
    Modifier.background(color = AppTheme.colors.surfaceHigh)
  }
  val width = if (fillWidth) {
    Modifier.fillMaxWidth()
  } else {
    Modifier.padding(horizontal = HugPadding)
  }
  Box(
    modifier = modifier
      .height(ButtonHeight)
      .clip(ButtonShape)
      .then(fill)
      .clickable(enabled = enabled && !loading, onClick = onClick)
      .then(width),
    contentAlignment = Alignment.Center,
  ) {
    if (loading) {
      CircularProgressIndicator(
        modifier = Modifier.size(SpinnerSize),
        color = AppTheme.colors.textPrimary,
        strokeWidth = SpinnerStroke,
      )
    } else {
      Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (enabled) AppTheme.colors.textPrimary else AppTheme.colors.textTertiary,
      )
    }
  }
}

/**
 * The quieter sibling of [PrimaryButton] for the second choice on a screen. [icon] is decorative
 * and drawn ahead of the label, so [text] still has to carry the whole meaning.
 */
@Composable
fun SecondaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  icon: ImageVector? = null,
) {
  SolidButton(
    text = text,
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    container = AppTheme.colors.surfaceHigh,
    content = AppTheme.colors.textPrimary,
    icon = icon,
  )
}

/** The button for an action that removes something: sign out, delete, remove. */
@Composable
fun DestructiveButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  SolidButton(
    text = text,
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    container = AppTheme.colors.dangerContainer,
    content = AppTheme.colors.danger,
    icon = null,
  )
}

/**
 * An inline accent-coloured action such as "See all", "Select" or "Done". Pass [color] to make the
 * link destructive; a disabled link greys out and stops responding rather than disappearing.
 */
@Composable
fun TextLink(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  color: Color = AppTheme.colors.accent,
) {
  TextButton(onClick = onClick, modifier = modifier, enabled = enabled) {
    Text(
      text = text,
      style = MaterialTheme.typography.titleMedium,
      color = if (enabled) color else AppTheme.colors.textTertiary,
    )
  }
}

/**
 * The circular icon affordance used for back chips, the header "+" and carousel chevrons.
 * Its [size] drives the glyph too, so a larger chip keeps the same proportions.
 */
@Composable
fun CircleIconButton(
  icon: ImageVector,
  contentDescription: String?,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  size: Dp = 40.dp,
  background: Color = AppTheme.colors.surfaceHigh,
  tint: Color = AppTheme.colors.textPrimary,
) {
  Box(
    modifier = modifier
      .size(size)
      .clip(CircleShape)
      .background(color = background)
      .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      tint = tint,
      modifier = Modifier.size(size * IconRatio),
    )
  }
}

/**
 * A pill that turns one choice of a set on: accent-filled while [selected], outlined while not.
 * [icon] is drawn ahead of the label and is decorative, so [label] has to carry the whole meaning.
 */
@Composable
fun SelectableChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  enabled: Boolean = true,
) {
  FilterChip(
    selected = selected,
    onClick = onClick,
    label = {
      Text(text = label, style = MaterialTheme.typography.labelMedium)
    },
    modifier = modifier,
    enabled = enabled,
    leadingIcon = if (icon == null) {
      null
    } else {
      {
        Icon(
          imageVector = icon,
          contentDescription = null,
          modifier = Modifier.size(ChipIconSize),
        )
      }
    },
    shape = MaterialTheme.shapes.small,
    colors = FilterChipDefaults.filterChipColors(
      containerColor = Color.Transparent,
      labelColor = AppTheme.colors.textSecondary,
      iconColor = AppTheme.colors.textSecondary,
      disabledContainerColor = Color.Transparent,
      disabledLabelColor = AppTheme.colors.textTertiary,
      disabledLeadingIconColor = AppTheme.colors.textTertiary,
      selectedContainerColor = AppTheme.colors.accent,
      selectedLabelColor = AppTheme.colors.textPrimary,
      selectedLeadingIconColor = AppTheme.colors.textPrimary,
      disabledSelectedContainerColor = AppTheme.colors.surfaceHigh,
    ),
    border = FilterChipDefaults.filterChipBorder(
      enabled = enabled,
      selected = selected,
      borderColor = AppTheme.colors.outline,
      disabledBorderColor = AppTheme.colors.outline,
      borderWidth = ChipBorderWidth,
    ),
  )
}

/** Shared body of the flat-filled buttons, so they cannot drift apart in metrics. */
@Composable
private fun SolidButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier,
  enabled: Boolean,
  container: Color,
  content: Color,
  icon: ImageVector?,
) {
  val tint = if (enabled) content else AppTheme.colors.textTertiary
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(ButtonHeight)
      .clip(ButtonShape)
      .background(color = if (enabled) container else AppTheme.colors.surfaceHigh)
      .clickable(enabled = enabled, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(ButtonIconSpacing),
    ) {
      if (icon != null) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = tint,
          modifier = Modifier.size(ButtonIconSize),
        )
      }
      Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = tint,
      )
    }
  }
}

/** Touch-target height every button in the app shares. */
private val ButtonHeight = 52.dp

/** Corner radius every button in the app shares. */
private val ButtonShape = RoundedCornerShape(16.dp)

/** Padding either side of a content-width [PrimaryButton]'s label. */
private val HugPadding = 28.dp

/** Size of the optional leading glyph a [SecondaryButton] carries. */
private val ButtonIconSize = 20.dp

/** Gap between a solid button's leading glyph and its label. */
private val ButtonIconSpacing = 12.dp

/** Diameter of the spinner [PrimaryButton] shows while loading. */
private val SpinnerSize = 20.dp

/** Stroke width of the spinner [PrimaryButton] shows while loading. */
private val SpinnerStroke = 2.dp

/** Share of a [CircleIconButton]'s diameter taken by its glyph. */
private const val IconRatio = 0.55f

/** Size of the leading glyph a [SelectableChip] may carry. */
private val ChipIconSize = 18.dp

/** Width of an unselected [SelectableChip]'s outline. */
private val ChipBorderWidth = 1.dp
