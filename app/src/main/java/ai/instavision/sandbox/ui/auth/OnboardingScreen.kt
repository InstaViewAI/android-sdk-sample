package ai.instavision.sandbox.ui.auth

import ai.instavision.sandbox.ui.common.PrimaryButton
import ai.instavision.sandbox.ui.common.ScreenPadding
import ai.instavision.sandbox.ui.common.SecondaryButton
import ai.instavision.sandbox.ui.theme.AppTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Launch screen for signed-out visitors. Purely presentational — it makes no SDK calls and has no
 * back destination, so it is the natural start of the navigation graph. It is the one screen
 * without a scaffold, so it handles its own window insets with `safeDrawingPadding`.
 */
@Composable
fun OnboardingScreen(
  onCreateAccount: () -> Unit,
  onSignIn: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(color = AppTheme.colors.ground)
      .safeDrawingPadding()
      .padding(horizontal = ScreenPadding, vertical = VerticalPadding),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Spacer(modifier = Modifier.weight(1f))
    BadgedCameraMark()
    Spacer(modifier = Modifier.height(TitleGap))
    Text(
      text = "Sandbox",
      style = MaterialTheme.typography.displaySmall,
      color = AppTheme.colors.textPrimary,
    )
    Spacer(modifier = Modifier.height(SubtitleGap))
    Text(
      text = "Set up your cameras, watch what matters, and keep an eye on every space.",
      style = MaterialTheme.typography.bodyLarge,
      color = AppTheme.colors.textSecondary,
      textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.weight(1f))
    PrimaryButton(text = "Create account", onClick = onCreateAccount)
    Spacer(modifier = Modifier.height(ButtonGap))
    SecondaryButton(text = "I already have an account", onClick = onSignIn)
  }
}

/**
 * The hero mark: a camera glyph on a soft accent disc with a tick badge tucked into its lower-left.
 * The badge offsets from the disc's centre rather than its edge, so it stays pinned to the glyph if
 * the disc is ever resized. Decorative — the title carries its meaning, so it has no description.
 */
@Composable
private fun BadgedCameraMark() {
  Box(
    modifier = Modifier
      .size(MarkSize)
      .background(color = AppTheme.colors.accentSoft, shape = CircleShape),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = Icons.Outlined.Videocam,
      contentDescription = null,
      tint = AppTheme.colors.accent,
      modifier = Modifier.size(MarkIconSize),
    )
    Box(
      modifier = Modifier
        .align(Alignment.Center)
        .offset(x = BadgeOffsetX, y = BadgeOffsetY)
        .size(BadgeSize)
        .background(color = AppTheme.colors.accent, shape = CircleShape),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = Icons.Rounded.Check,
        contentDescription = null,
        tint = AppTheme.colors.ground,
        modifier = Modifier.size(BadgeIconSize),
      )
    }
  }
}

/** Breathing room above the hero mark and below the sign-in button. */
private val VerticalPadding = 32.dp

/** Diameter of the soft accent disc behind the camera glyph. */
private val MarkSize = 120.dp

/** Size of the camera glyph centred on the disc. */
private val MarkIconSize = 56.dp

/** Diameter of the tick badge overlaid on the disc. */
private val BadgeSize = 24.dp

/** Size of the tick inside the badge. */
private val BadgeIconSize = 14.dp

/** Horizontal shift of the badge from the disc's centre, onto the camera glyph's left edge. */
private val BadgeOffsetX = (-26).dp

/** Vertical shift of the badge from the disc's centre, onto the camera glyph's lower corner. */
private val BadgeOffsetY = 12.dp

/** Gap between the hero mark and the title. */
private val TitleGap = 28.dp

/** Gap between the title and the subtitle. */
private val SubtitleGap = 12.dp

/** Gap between the two calls to action. */
private val ButtonGap = 12.dp
