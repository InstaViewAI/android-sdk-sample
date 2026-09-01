package ai.instavision.sandbox.ui.security

import ai.instavision.sandbox.ui.common.DetailScaffold
import ai.instavision.sandbox.ui.common.ErrorBanner
import ai.instavision.sandbox.ui.common.GroupCard
import ai.instavision.sandbox.ui.common.LoadingBox
import ai.instavision.sandbox.ui.common.PrimaryButton
import ai.instavision.sandbox.ui.common.ScreenPadding
import ai.instavision.sandbox.ui.common.StatusPill
import ai.instavision.sandbox.ui.theme.AppTheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** What the monitoring centre actually does, which is the one thing the header has to explain. */
private const val MONITORING_BODY =
  "A monitoring centre watches your alarms around the clock and can dispatch help."

/**
 * The monitoring checklist: how far the home has got, every step in the order the backend expects
 * them, and one button that opens whatever comes next. Steps write their own progress, so the
 * screen refetches on resume rather than being told what changed. The pitch for monitoring lives
 * on the Security tab root, so this screen opens straight on the checklist.
 */
@Composable
fun SecuritySetupScreen(
  onBack: () -> Unit,
  onStep: (String) -> Unit,
  onContact: () -> Unit,
) {
  val viewModel: SecuritySetupViewModel = viewModel()
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  LifecycleResumeEffect(Unit) {
    viewModel.refreshOnResume()
    onPauseOrDispose {}
  }
  LaunchedEffect(state.finished) { if (state.finished) onBack() }

  val open: (SecuritySteps) -> Unit = { step ->
    if (step == SecuritySteps.ContactInformation) onContact() else onStep(step.apiName)
  }

  DetailScaffold(
    title = "Set up security",
    onBack = onBack,
    bottomBar = {
      Box(
        modifier = Modifier
          .navigationBarsPadding()
          .padding(horizontal = ScreenPadding, vertical = BottomBarPadding),
      ) {
        PrimaryButton(
          text = bottomBarLabel(state = state),
          onClick = {
            if (state.requiredDone) viewModel.finish() else state.currentStep?.let(open)
          },
          enabled = state.requiredDone || state.currentStep != null,
          loading = state.busy,
        )
      }
    },
  ) {
    ErrorBanner(message = state.error)
    if (state.loading) {
      LoadingBox()
    } else {
      MonitoringHeader(completed = state.completedCount)
      SecuritySteps.entries.forEach { step ->
        StepCard(
          step = step,
          complete = state.isComplete(step),
          current = step == state.currentStep,
          reachable = state.isReachable(step),
          onClick = { open(step) },
        )
      }
    }
  }
}

/** The label of the one button that drives the checklist forward. */
private fun bottomBarLabel(state: SecuritySetupUiState): String = when {
  state.requiredDone -> "Finish"
  state.completedCount == 0 -> "Start setup"
  else -> "Continue setup"
}

/** What professional monitoring is, sat beside how far through the checklist the home is. */
@Composable
private fun MonitoringHeader(completed: Int) {
  GroupCard {
    Row(
      modifier = Modifier.padding(CardPadding),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(RowSpacing),
    ) {
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(TitleGap),
      ) {
        Text(
          text = "Professional monitoring",
          style = MaterialTheme.typography.titleMedium,
          color = AppTheme.colors.textPrimary,
        )
        Text(
          text = MONITORING_BODY,
          style = MaterialTheme.typography.bodyMedium,
          color = AppTheme.colors.textSecondary,
        )
      }
      ProgressRing(completed = completed, total = SecuritySteps.entries.size)
    }
  }
}

/**
 * The "N/7" counter drawn as a ring, the one place in the app that reaches for a [Canvas] because
 * no design-system component draws an arc.
 */
@Composable
private fun ProgressRing(completed: Int, total: Int) {
  val track = AppTheme.colors.surfaceHigh
  val fill = AppTheme.colors.accent
  Box(modifier = Modifier.size(RingSize), contentAlignment = Alignment.Center) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val stroke = Stroke(width = RingStroke.toPx(), cap = StrokeCap.Round)
      val inset = stroke.width / 2
      val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
      drawArc(
        color = track,
        startAngle = RingStart,
        sweepAngle = FullTurn,
        useCenter = false,
        topLeft = Offset(inset, inset),
        size = arcSize,
        style = stroke,
      )
      drawArc(
        color = fill,
        startAngle = RingStart,
        sweepAngle = FullTurn * completed / total,
        useCenter = false,
        topLeft = Offset(inset, inset),
        size = arcSize,
        style = stroke,
      )
    }
    Text(
      text = "$completed/$total",
      style = MaterialTheme.typography.titleMedium,
      color = AppTheme.colors.textPrimary,
    )
  }
}

/**
 * One step of the checklist. [current] rings the card in the accent colour and is the only state
 * that opens: a step the backend has recorded is finished and cannot be walked back into, and one
 * still out of reach is dimmed behind a padlock. This is the production app's rule, where only the
 * current step carries a tap target and a chevron.
 */
@Composable
private fun StepCard(
  step: SecuritySteps,
  complete: Boolean,
  current: Boolean,
  reachable: Boolean,
  onClick: () -> Unit,
) {
  GroupCard {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .then(
          if (current) {
            Modifier.border(
              width = BorderWidth,
              color = AppTheme.colors.accent,
              shape = MaterialTheme.shapes.medium,
            )
          } else {
            Modifier
          },
        ),
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .alpha(if (reachable) 1f else DimmedAlpha)
          .clickable(enabled = current, onClick = onClick)
          .padding(CardPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowSpacing),
      ) {
        Box(
          modifier = Modifier
            .size(StepEmblemSize)
            .clip(CircleShape)
            .background(color = AppTheme.colors.accentSoft),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = step.icon,
            contentDescription = null,
            tint = AppTheme.colors.accent,
            modifier = Modifier.size(StepIconSize),
          )
        }
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(TitleGap),
        ) {
          Text(
            text = step.title,
            style = MaterialTheme.typography.titleMedium,
            color = AppTheme.colors.textPrimary,
          )
          if (step.subtitle != null) {
            Text(
              text = step.subtitle,
              style = MaterialTheme.typography.bodyMedium,
              color = AppTheme.colors.textSecondary,
            )
          }
        }
        if (step.optional) {
          StatusPill(
            text = "Optional",
            containerColor = AppTheme.colors.surfaceHigh,
            contentColor = AppTheme.colors.textSecondary,
          )
        }
        StepAffordance(complete = complete, current = current)
      }
    }
  }
}

/**
 * The trailing glyph of a [StepCard]. Only the current step gets a chevron, because only it can be
 * opened; a finished step shows a tick and everything else a padlock.
 */
@Composable
private fun StepAffordance(complete: Boolean, current: Boolean) {
  when {
    complete -> Icon(
      imageVector = Icons.Outlined.CheckCircle,
      contentDescription = null,
      tint = AppTheme.colors.success,
      modifier = Modifier.size(TrailingIconSize),
    )

    current -> Icon(
      imageVector = Icons.Rounded.ChevronRight,
      contentDescription = null,
      tint = AppTheme.colors.textTertiary,
      modifier = Modifier.size(TrailingIconSize),
    )

    else -> Icon(
      imageVector = Icons.Outlined.Lock,
      contentDescription = null,
      tint = AppTheme.colors.textTertiary,
      modifier = Modifier.size(TrailingIconSize),
    )
  }
}

/** Padding inside the header and step cards. */
private val CardPadding = 16.dp

/** Gap between the parts of a card's row. */
private val RowSpacing = 12.dp

/** Gap between a card's title and the line under it. */
private val TitleGap = 4.dp

/** Diameter of the circle behind a step's icon. */
private val StepEmblemSize = 44.dp

/** Size of the icon inside a step's circle. */
private val StepIconSize = 22.dp

/** Size of a step's trailing glyph. */
private val TrailingIconSize = 22.dp

/** Diameter of the progress ring. */
private val RingSize = 76.dp

/** Thickness of both the ring's track and its filled arc. */
private val RingStroke = 6.dp

/** Angle the ring starts from, which puts zero progress at twelve o'clock. */
private const val RingStart = -90f

/** A whole revolution, the sweep of the ring's track. */
private const val FullTurn = 360f

/** Width of the accent ring around the step the user is on. */
private val BorderWidth = 1.dp

/** Opacity of a step that cannot be opened yet. */
private const val DimmedAlpha = 0.4f

/** Breathing room above and below the screen's bottom-bar button. */
private val BottomBarPadding = 12.dp
