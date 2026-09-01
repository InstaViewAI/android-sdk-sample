package ai.instavision.sandbox.ui.security

import ai.instavision.sandbox.ui.common.EmptyState
import ai.instavision.sandbox.ui.common.ErrorBanner
import ai.instavision.sandbox.ui.common.GroupCard
import ai.instavision.sandbox.ui.common.InfoNote
import ai.instavision.sandbox.ui.common.LoadingBox
import ai.instavision.sandbox.ui.common.Notice
import ai.instavision.sandbox.ui.common.PrimaryButton
import ai.instavision.sandbox.ui.common.RootScaffold
import ai.instavision.sandbox.ui.common.SecondaryButton
import ai.instavision.sandbox.ui.common.SectionHeader
import ai.instavision.sandbox.ui.common.SettingRow
import ai.instavision.sandbox.ui.common.TextLink
import ai.instavision.sandbox.ui.theme.AppTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** What being armed means, spelled out so the card is not just a state word. */
private const val ARMED_BODY =
  "Your cameras are watching. An alarm goes straight to the monitoring centre."

/** The counterpart of [ARMED_BODY] for a system that is standing by. */
private const val DISARMED_BODY =
  "Monitoring is paused. Arm the system once everyone has left."

/** Said to the owner of a home whose plan does not cover monitoring, in place of the setup entry. */
private const val UPSELL_BODY =
  "Professional monitoring is not on this home's plan yet. Add it to your subscription to set it up."

/** Said in place of the setup button when the home has no camera monitoring could arm. */
private const val NO_CAMERA_BODY =
  "Monitoring needs a home security camera. Pair one with this home before starting setup."

/** Stands in for the activity summary of a home the system has never been armed in. */
private const val NO_ACTIVITY = "No activity yet"

/** Said to everyone but the owner while the home's checklist is unfinished; only the owner can. */
private const val NOT_OWNER_BODY =
  "The owner of this home has not finished setting monitoring up. Only they can complete it."

/**
 * Security tab root: the arm and disarm control for a home whose monitoring setup is finished, and
 * the way into that setup for every home that is not there yet. Setup is only ever offered to the
 * owner of a home whose plan covers monitoring; everyone else is told why the tab is not theirs.
 *
 * [onLog] opens the full activity log, which is the only place the summary beneath the control
 * leads to.
 */
@Composable
fun SecurityScreen(onSetup: () -> Unit, onLog: () -> Unit) {
  val viewModel: SecurityViewModel = viewModel()
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  LifecycleResumeEffect(Unit) {
    viewModel.refreshOnResume()
    onPauseOrDispose {}
  }

  RootScaffold(title = "Security") {
    ErrorBanner(message = state.error)
    when {
      state.loading -> LoadingBox()
      !state.entitled && state.isOwner -> UpsellPrompt()
      state.isOwner && !state.setupComplete -> SetupPrompt(
        started = state.started,
        canSetup = state.canSetup,
        onSetup = onSetup,
      )

      !state.setupComplete -> EmptyState(
        title = "Setup is not finished",
        body = NOT_OWNER_BODY,
        icon = Icons.Outlined.Shield,
      )

      else -> {
        ArmingSection(
          armed = state.armed,
          busy = state.busy,
          onArm = viewModel::arm,
          onDisarm = viewModel::disarm,
        )
        RecentActivitySection(
          sessions = state.sessions,
          loading = state.logsLoading,
          onLog = onLog,
        )
      }
    }
  }
}

/**
 * The newest arming sessions under the arm control, with the way into the full log. The cards are
 * a summary and their detection rows are deliberately inert; the log itself is where a detection
 * opens.
 */
@Composable
private fun RecentActivitySection(
  sessions: List<SecuritySession>,
  loading: Boolean,
  onLog: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(SectionGap)) {
    SectionHeader(
      text = "Recent activity",
      action = { TextLink(text = "See all", onClick = onLog) },
    )
    when {
      loading -> LoadingBox()
      sessions.isEmpty() -> GroupCard { SettingRow(label = NO_ACTIVITY, enabled = false) }
      else -> sessions.forEach { session -> SecurityLogCard(session = session) }
    }
  }
}

/** The armed-or-not readout and the single control that flips it. */
@Composable
private fun ArmingSection(
  armed: Boolean,
  busy: Boolean,
  onArm: () -> Unit,
  onDisarm: () -> Unit,
) {
  GroupCard {
    Row(
      modifier = Modifier.padding(CardPadding),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(CardSpacing),
    ) {
      Box(
        modifier = Modifier
          .size(EmblemSize)
          .clip(CircleShape)
          .background(
            color = if (armed) {
              AppTheme.colors.successContainer
            } else {
              AppTheme.colors.accentSoft
            },
          ),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = if (armed) Icons.Outlined.Shield else Icons.Outlined.LockOpen,
          contentDescription = null,
          tint = if (armed) AppTheme.colors.success else AppTheme.colors.accent,
          modifier = Modifier.size(EmblemIconSize),
        )
      }
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(TitleGap),
      ) {
        Text(
          text = if (armed) "System armed" else "System disarmed",
          style = MaterialTheme.typography.titleMedium,
          color = AppTheme.colors.textPrimary,
        )
        Text(
          text = if (armed) ARMED_BODY else DISARMED_BODY,
          style = MaterialTheme.typography.bodyMedium,
          color = AppTheme.colors.textSecondary,
        )
      }
    }
  }
  if (armed) {
    SecondaryButton(text = "Disarm", onClick = onDisarm, enabled = !busy)
  } else {
    PrimaryButton(text = "Arm", onClick = onArm, loading = busy)
  }
}

/**
 * Stands in for the whole tab until the checklist reaches its terminal step: the case for
 * monitoring, then the button that opens the checklist. The button is dead until the home has a
 * camera monitoring can arm, because the checklist cannot be finished without one.
 */
@Composable
private fun SetupPrompt(started: Boolean, canSetup: Boolean, onSetup: () -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(PromptGap)) {
    MonitoringIntroContent()
    if (!canSetup) InfoNote(text = NO_CAMERA_BODY)
    PrimaryButton(
      text = if (started) "Continue setup" else "Start setup",
      onClick = onSetup,
      enabled = canSetup,
    )
  }
}

/** The case for monitoring with no way in, shown to an owner whose plan does not include it. */
@Composable
private fun UpsellPrompt() {
  Column(verticalArrangement = Arrangement.spacedBy(PromptGap)) {
    MonitoringIntroContent()
    Notice(message = UPSELL_BODY)
  }
}

/** Padding inside the arming card. */
private val CardPadding = 16.dp

/** Gap between the arming card's emblem and its text. */
private val CardSpacing = 14.dp

/** Diameter of the circle behind the arming card's icon. */
private val EmblemSize = 48.dp

/** Size of the icon inside the arming card's circle. */
private val EmblemIconSize = 24.dp

/** Gap between a card's title and the line under it. */
private val TitleGap = 4.dp

/** Gap the setup prompt leaves between the monitoring pitch and the button under it. */
private val PromptGap = 24.dp

/** Gap between the activity header and its cards, and between two of those cards. */
private val SectionGap = 12.dp
