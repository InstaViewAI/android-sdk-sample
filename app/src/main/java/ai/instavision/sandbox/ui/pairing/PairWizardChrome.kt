package ai.instavision.sandbox.ui.pairing

import ai.instavision.sandbox.ui.common.CircleIconButton
import ai.instavision.sandbox.ui.common.GroupCard
import ai.instavision.sandbox.ui.common.ScreenPadding
import ai.instavision.sandbox.ui.common.TextLink
import ai.instavision.sandbox.ui.theme.AppTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** How many steps the wizard's counter reports, which every page's caption ends with. */
private const val WIZARD_STEPS = 5

/**
 * The frame every step of the wizard shares: the back chip and the way out on one row, the step
 * counter, the title and the sentence under it, then whatever the step itself shows. [bottom] is
 * pinned above the navigation bar and is where a step's buttons go.
 */
@Composable
internal fun WizardPage(
  step: Int,
  title: String,
  subtitle: String,
  onBack: () -> Unit,
  onExit: () -> Unit,
  bottom: @Composable ColumnScope.() -> Unit = {},
  content: @Composable ColumnScope.() -> Unit,
) {
  PageFrame(
    bottom = bottom,
    header = {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = ScreenPadding, vertical = HeaderPadding),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        CircleIconButton(
          icon = Icons.Rounded.ChevronLeft,
          contentDescription = "Back",
          onClick = onBack,
        )
        Box(modifier = Modifier.weight(1f))
        TextLink(text = "Exit", onClick = onExit)
      }
    },
    content = {
      Column(verticalArrangement = Arrangement.spacedBy(space = TitleSpacing)) {
        Text(
          text = "STEP $step OF $WIZARD_STEPS",
          style = MaterialTheme.typography.labelSmall,
          color = AppTheme.colors.accent,
        )
        Text(
          text = title,
          style = MaterialTheme.typography.headlineMedium,
          color = AppTheme.colors.textPrimary,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodyLarge,
          color = AppTheme.colors.textSecondary,
        )
      }
      content()
    },
  )
}

/**
 * The frame of the two steps that carry no chrome at all, because there is nothing to go back to
 * while the camera is being added and nothing to abandon once it is on the account.
 */
@Composable
internal fun PlainPage(
  bottom: @Composable ColumnScope.() -> Unit = {},
  content: @Composable ColumnScope.() -> Unit,
) {
  PageFrame(bottom = bottom, header = {}, content = content)
}

/**
 * The circle-in-a-card illustration that opens most steps. [warning] repaints it for the states
 * where something needs the user's attention rather than their agreement.
 */
@Composable
internal fun WizardEmblem(
  icon: ImageVector,
  warning: Boolean = false,
) {
  GroupCard {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = EmblemPadding),
      contentAlignment = Alignment.Center,
    ) {
      Box(
        modifier = Modifier
          .size(EmblemSize)
          .clip(CircleShape)
          .background(
            color = if (warning) AppTheme.colors.warningContainer else AppTheme.colors.accentSoft,
          ),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = if (warning) AppTheme.colors.warning else AppTheme.colors.accent,
          modifier = Modifier.size(EmblemIconSize),
        )
      }
    }
  }
}

/** The numbered things to do on a step, counted from one in the order they are given. */
@Composable
internal fun WizardTips(tips: List<String>) {
  Column(verticalArrangement = Arrangement.spacedBy(space = TipSpacing)) {
    tips.forEachIndexed { index, tip ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(space = TipSpacing),
      ) {
        Box(
          modifier = Modifier
            .size(TipNumberSize)
            .clip(CircleShape)
            .background(color = AppTheme.colors.accentSoft),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.labelMedium,
            color = AppTheme.colors.accent,
          )
        }
        Text(
          text = tip,
          style = MaterialTheme.typography.bodyLarge,
          color = AppTheme.colors.textSecondary,
        )
      }
    }
  }
}

/**
 * The spinner-in-a-circle every waiting step shows, with [status] naming what is being waited on.
 * The wait itself is bounded by the ViewModel's own timeouts, not by anything here.
 */
@Composable
internal fun WizardWait(status: String) {
  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(space = WaitSpacing),
  ) {
    WizardSpinner()
    Text(
      text = status,
      style = MaterialTheme.typography.bodyLarge,
      color = AppTheme.colors.textSecondary,
      textAlign = TextAlign.Center,
    )
  }
}

/** The tinted circle holding a spinner that every step with nothing to do but wait shows. */
@Composable
internal fun WizardSpinner() {
  Box(
    modifier = Modifier
      .size(WaitSize)
      .clip(CircleShape)
      .background(color = AppTheme.colors.accentSoft),
    contentAlignment = Alignment.Center,
  ) {
    CircularProgressIndicator(
      modifier = Modifier.size(WaitSpinnerSize),
      color = AppTheme.colors.accent,
    )
  }
}

/**
 * Four bars that read a raw [rssi] as a strength: how many are filled and what colour they are
 * both come from the reading, and the rest of the meter stays as an unfilled outline.
 */
@Composable
internal fun SignalBars(rssi: Int) {
  val filled = when {
    rssi > STRONG_RSSI -> 4
    rssi > GOOD_RSSI -> 3
    rssi > WEAK_RSSI -> 2
    else -> 1
  }
  val color = when {
    rssi > GOOD_RSSI -> AppTheme.colors.success
    rssi > WEAK_RSSI -> AppTheme.colors.warning
    else -> AppTheme.colors.danger
  }
  Row(
    verticalAlignment = Alignment.Bottom,
    horizontalArrangement = Arrangement.spacedBy(space = BarSpacing),
  ) {
    BarHeights.forEachIndexed { index, height ->
      SignalBar(
        height = height,
        color = if (index < filled) color else AppTheme.colors.surfaceHigh,
      )
    }
  }
}

/** One bar of [SignalBars], drawn as a rounded column of its own [height]. */
@Composable
private fun SignalBar(height: Dp, color: Color) {
  Box(
    modifier = Modifier
      .width(BarWidth)
      .height(height)
      .clip(CircleShape)
      .background(color = color),
  )
}

/**
 * Shared body of both page frames: the app's background wash, an optional [header] row and the
 * scrolling content, with [bottom] held above the navigation bar.
 */
@Composable
private fun PageFrame(
  header: @Composable () -> Unit,
  bottom: @Composable ColumnScope.() -> Unit,
  content: @Composable ColumnScope.() -> Unit,
) {
  Scaffold(
    containerColor = AppTheme.colors.ground,
    bottomBar = {
      Column(
        modifier = Modifier
          .navigationBarsPadding()
          .imePadding()
          .padding(horizontal = ScreenPadding, vertical = BottomPadding),
        verticalArrangement = Arrangement.spacedBy(space = ButtonSpacing),
        content = bottom,
      )
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(brush = groundBrush())
        .padding(innerPadding)
        .consumeWindowInsets(innerPadding)
        .imePadding(),
    ) {
      header()
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = ScreenPadding)
          .padding(bottom = ContentSpacing),
        verticalArrangement = Arrangement.spacedBy(space = ContentSpacing),
        content = content,
      )
    }
  }
}

/** The same vertical wash the app's scaffolds paint, so the wizard sits on the usual background. */
@Composable
private fun groundBrush(): Brush = Brush.verticalGradient(
  0f to AppTheme.colors.groundTop,
  GradientEnd to AppTheme.colors.ground,
  1f to AppTheme.colors.ground,
)

/** Fraction of the screen height over which the background gradient has fully resolved. */
private const val GradientEnd = 0.35f

/** Reading above which a signal counts as full strength. */
private const val STRONG_RSSI = -55

/** Reading above which a signal is still comfortably usable. */
private const val GOOD_RSSI = -67

/** Reading above which a signal is workable but worth moving closer for. */
private const val WEAK_RSSI = -80

/** Heights of the four bars of a [SignalBars] meter, shortest first. */
private val BarHeights = listOf(6.dp, 10.dp, 14.dp, 18.dp)

/** Width of one bar of a [SignalBars] meter. */
private val BarWidth = 4.dp

/** Gap between two bars of a [SignalBars] meter. */
private val BarSpacing = 3.dp

/** Gap between two consecutive pieces of a page's content. */
private val ContentSpacing = 16.dp

/** Gap between the step counter, the title and the sentence under it. */
private val TitleSpacing = 8.dp

/** Vertical breathing room around a page's header row. */
private val HeaderPadding = 8.dp

/** Vertical breathing room around a page's pinned buttons. */
private val BottomPadding = 12.dp

/** Gap between two stacked buttons. */
private val ButtonSpacing = 12.dp

/** Padding above and below the circle inside an emblem card. */
private val EmblemPadding = 24.dp

/** Diameter of the circle an emblem card centres. */
private val EmblemSize = 128.dp

/** Size of the glyph inside an emblem circle. */
private val EmblemIconSize = 48.dp

/** Gap between a numbered tip's marker and its text, and between two tips. */
private val TipSpacing = 16.dp

/** Diameter of the circle carrying a tip's number. */
private val TipNumberSize = 28.dp

/** Diameter of the circle a waiting step centres its spinner in. */
private val WaitSize = 140.dp

/** Diameter of the spinner inside a waiting step's circle. */
private val WaitSpinnerSize = 32.dp

/** Gap between a waiting step's circle and the line under it. */
private val WaitSpacing = 24.dp
