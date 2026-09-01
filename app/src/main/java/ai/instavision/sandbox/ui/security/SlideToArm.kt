package ai.instavision.sandbox.ui.security

import ai.instavision.sandbox.ui.theme.AppTheme
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * The primary arm control: a pill the user drags a thumb across rather than a button they can hit
 * by accident. The gesture only counts once the thumb has crossed [CompletionFraction] of the
 * track; anything shorter springs back and nothing is sent. [busy] both blocks the drag and turns
 * the thumb into a spinner, because an arm request is not finished until the backend settles it.
 *
 * [armed] picks the wording and the thumb colour, so the control always says what the *next* slide
 * will do rather than what the system currently is.
 */
@Composable
internal fun SlideToArm(
  armed: Boolean,
  busy: Boolean,
  onComplete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val density = LocalDensity.current
  val scope = rememberCoroutineScope()
  val offset = remember { Animatable(0f) }
  var travel by remember { mutableFloatStateOf(0f) }
  val label = if (armed) "Slide to disarm" else "Slide to arm"
  val thumbColor = if (armed) AppTheme.colors.success else AppTheme.colors.danger

  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(TrackHeight)
      .clip(CircleShape)
      .background(color = AppTheme.colors.surface)
      .border(width = TrackBorder, color = AppTheme.colors.outline, shape = CircleShape)
      .onSizeChanged { size ->
        val occupied = with(density) { (ThumbSize + TrackInset * 2).toPx() }
        travel = (size.width - occupied).coerceAtLeast(0f)
      },
    contentAlignment = Alignment.CenterStart,
  ) {
    Text(
      text = label,
      modifier = Modifier.align(Alignment.Center),
      style = MaterialTheme.typography.labelLarge,
      color = if (busy) AppTheme.colors.textTertiary else AppTheme.colors.textPrimary,
    )
    Box(
      modifier = Modifier
        .padding(horizontal = TrackInset)
        .offset { IntOffset(x = offset.value.roundToInt(), y = 0) }
        .size(ThumbSize)
        .clip(CircleShape)
        .background(color = thumbColor)
        .draggable(
          state = rememberDraggableState { delta ->
            scope.launch { offset.snapTo((offset.value + delta).coerceIn(0f, travel)) }
          },
          orientation = Orientation.Horizontal,
          enabled = !busy,
          onDragStopped = {
            if (travel > 0f && offset.value >= travel * CompletionFraction) onComplete()
            offset.animateTo(targetValue = 0f, animationSpec = ReturnSpec)
          },
        ),
      contentAlignment = Alignment.Center,
    ) {
      if (busy) {
        CircularProgressIndicator(
          modifier = Modifier.size(ThumbProgressSize),
          color = AppTheme.colors.textPrimary,
          strokeWidth = ThumbProgressStroke,
        )
      } else {
        Icon(
          imageVector = Icons.Rounded.KeyboardDoubleArrowRight,
          contentDescription = label,
          tint = AppTheme.colors.textPrimary,
          modifier = Modifier.size(ThumbIconSize),
        )
      }
    }
  }
}

/** How far along the track the thumb has to be released for the slide to count as a request. */
private const val CompletionFraction = 0.9f

/** How the thumb returns to rest, whether the slide completed or was let go of short of it. */
private val ReturnSpec = tween<Float>(durationMillis = 220)

/** Height of the slide track, which is what makes it read as the screen's primary control. */
private val TrackHeight = 64.dp

/** Width of the track's outline. */
private val TrackBorder = 1.dp

/** Clear space between the thumb and either end of the track. */
private val TrackInset = 4.dp

/** Diameter of the draggable thumb. */
private val ThumbSize = 56.dp

/** Size of the double chevron the thumb carries at rest. */
private val ThumbIconSize = 28.dp

/** Diameter of the spinner that replaces the chevron while a request is in flight. */
private val ThumbProgressSize = 24.dp

/** Stroke of that spinner, thinned so it does not fill the thumb. */
private val ThumbProgressStroke = 2.dp
