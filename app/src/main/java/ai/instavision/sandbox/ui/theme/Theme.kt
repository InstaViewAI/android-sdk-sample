package ai.instavision.sandbox.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The brand palette mapped onto M3 roles so stock Material components pick it up. Anything M3 has
 * no role for lives in [AppColors] instead.
 */
private val DarkColorScheme = darkColorScheme(
  primary = Accent,
  onPrimary = TextPrimary,
  background = Ground,
  onBackground = TextPrimary,
  surface = Surface,
  onSurface = TextPrimary,
  surfaceVariant = SurfaceHigh,
  onSurfaceVariant = TextSecondary,
  outline = Outline,
  outlineVariant = Outline,
  error = Danger,
  onError = TextPrimary,
  errorContainer = DangerContainer,
  onErrorContainer = Danger,
  secondaryContainer = AccentSoft,
  onSecondaryContainer = Accent,
  surfaceContainer = SurfaceHigh,
  scrim = Color.Black,
)

/** Corner radii of the app: pills and chips, cards and fields, then hero containers. */
private val AppShapes = Shapes(
  small = RoundedCornerShape(12.dp),
  medium = RoundedCornerShape(16.dp),
  large = RoundedCornerShape(20.dp),
)

/**
 * Wraps the app in its dark-only theme. There is deliberately no light scheme and no dynamic
 * colour, since Monet on Android 12+ would replace the brand palette with the user's wallpaper.
 */
@Composable
fun InstaVisionSDKSampleTheme(content: @Composable () -> Unit) {
  CompositionLocalProvider(LocalAppColors provides DarkAppColors) {
    MaterialTheme(
      colorScheme = DarkColorScheme,
      typography = Typography,
      shapes = AppShapes,
      content = content,
    )
  }
}

/** Terse access to the brand tokens from anywhere under [InstaVisionSDKSampleTheme]. */
object AppTheme {
  /** The palette in scope; the app provides one and never swaps it at runtime. */
  val colors: AppColors
    @Composable @ReadOnlyComposable get() = LocalAppColors.current
}
