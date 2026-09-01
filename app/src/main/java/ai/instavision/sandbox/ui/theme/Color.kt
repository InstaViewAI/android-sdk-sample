package ai.instavision.sandbox.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Window and scaffold background; the darkest surface in the app. */
val Ground = Color(0xFF0B0E1A)

/** Top stop of the subtle vertical wash every scaffold paints over [Ground]. */
val GroundTop = Color(0xFF12162A)

/** Fill of grouped cards and input fields. */
val Surface = Color(0xFF171B2C)

/** Fill one step above [Surface]: secondary buttons, avatar circles, disabled primaries, thumbnails. */
val SurfaceHigh = Color(0xFF1E2235)

/** 1dp card borders and the inset dividers between rows inside a card. */
val Outline = Color(0xFF262B3F)

/** Primary violet used for icons, the selected tab and links. */
val Accent = Color(0xFF7C5CFC)

/** Start stop of the primary button's horizontal gradient. */
val AccentStart = Color(0xFF6D4AF5)

/** End stop of the primary button's horizontal gradient. */
val AccentEnd = Color(0xFF9B85FF)

/** Tinted backing for circular accent icons; too dark to carry text. */
val AccentSoft = Color(0xFF221E3D)

/** Titles and any text that must read as the primary content of its row. */
val TextPrimary = Color(0xFFFFFFFF)

/** Row values, subtitles and unselected tabs. */
val TextSecondary = Color(0xFF9AA0B4)

/** Section headers and disabled text; the faintest readable tier. */
val TextTertiary = Color(0xFF6B7186)

/** Positive state: the online dot and granted permission checks. */
val Success = Color(0xFF32D74B)

/** Fill of the green "Verified" pill, paired with [Success]. */
val SuccessContainer = Color(0xFF123024)

/** Caution state, such as the "setup not finished" shield. */
val Warning = Color(0xFFFF9F0A)

/** Fill behind [Warning] content. */
val WarningContainer = Color(0xFF33280F)

/** Destructive state: sign out, delete and remove. */
val Danger = Color(0xFFFF453A)

/** Fill of destructive buttons and error banners, paired with [Danger]. */
val DangerContainer = Color(0xFF2A161C)

/** Teal used by informational note icons. */
val Info = Color(0xFF2DD4BF)

/**
 * The app's full brand palette, carried alongside M3's `ColorScheme` because that scheme has no
 * slot for success, warning, a third text tier or the primary button's gradient stops.
 */
@Immutable
data class AppColors(
  /** Window and scaffold background. */
  val ground: Color,
  /** Top stop of the scaffold's background gradient. */
  val groundTop: Color,
  /** Grouped card and input field fill. */
  val surface: Color,
  /** Fill one step above [surface] for secondary controls and placeholders. */
  val surfaceHigh: Color,
  /** Card borders and inset row dividers. */
  val outline: Color,
  /** Primary violet for icons, selection and links. */
  val accent: Color,
  /** Start stop of the primary button gradient. */
  val accentStart: Color,
  /** End stop of the primary button gradient. */
  val accentEnd: Color,
  /** Tinted backing for circular accent icons. */
  val accentSoft: Color,
  /** Titles and primary row content. */
  val textPrimary: Color,
  /** Row values, subtitles and unselected tabs. */
  val textSecondary: Color,
  /** Section headers and disabled text. */
  val textTertiary: Color,
  /** Positive state colour. */
  val success: Color,
  /** Fill paired with [success]. */
  val successContainer: Color,
  /** Caution state colour. */
  val warning: Color,
  /** Fill paired with [warning]. */
  val warningContainer: Color,
  /** Destructive state colour. */
  val danger: Color,
  /** Fill paired with [danger]. */
  val dangerContainer: Color,
  /** Informational note colour. */
  val info: Color,
)

/** The one palette the app ships; there is no light variant. */
val DarkAppColors = AppColors(
  ground = Ground,
  groundTop = GroundTop,
  surface = Surface,
  surfaceHigh = SurfaceHigh,
  outline = Outline,
  accent = Accent,
  accentStart = AccentStart,
  accentEnd = AccentEnd,
  accentSoft = AccentSoft,
  textPrimary = TextPrimary,
  textSecondary = TextSecondary,
  textTertiary = TextTertiary,
  success = Success,
  successContainer = SuccessContainer,
  warning = Warning,
  warningContainer = WarningContainer,
  danger = Danger,
  dangerContainer = DangerContainer,
  info = Info,
)

/** Supplies [AppColors] to the tree; read it through `AppTheme.colors` rather than directly. */
val LocalAppColors = staticCompositionLocalOf { DarkAppColors }
