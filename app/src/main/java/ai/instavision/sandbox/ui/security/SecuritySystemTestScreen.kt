package ai.instavision.sandbox.ui.security

import ai.instavision.sandbox.data.SessionStore
import ai.instavision.sandbox.ui.common.DetailScaffold
import ai.instavision.sandbox.ui.common.ErrorBanner
import ai.instavision.sandbox.ui.common.GroupCard
import ai.instavision.sandbox.ui.common.InfoNote
import ai.instavision.sandbox.ui.common.LoadingBox
import ai.instavision.sandbox.ui.common.PrimaryButton
import ai.instavision.sandbox.ui.common.RowDivider
import ai.instavision.sandbox.ui.common.ScreenPadding
import ai.instavision.sandbox.ui.common.SecondaryButton
import ai.instavision.sandbox.ui.common.SectionHeader
import ai.instavision.sandbox.ui.common.SettingRow
import ai.instavision.sandbox.ui.common.StatusPill
import ai.instavision.sandbox.ui.common.TextLink
import ai.instavision.sandbox.ui.theme.AppTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** What the test run is, and the one precaution it asks for. */
private const val HERO_BODY =
  "Arm and disarm the system for real. Make sure you are indoors so you do not set the alarm off."

/** What test mode does to an alarm raised while it is on. */
private const val TEST_MODE_NOTE =
  "In test mode the alarm reaches nobody: no agent is called and no responder is dispatched."

/** Warning shown until test mode is confirmed on, since arming before that is a real alarm. */
private const val NOT_IN_TEST_MODE_NOTE =
  "This home is not in test mode yet. Starting the test run turns it on before arming anything."

/**
 * The optional system test step: a full arm and disarm with the monitoring centre left out of it.
 * Production splits the start button and the live test onto two pages of a pager; the sample keeps
 * them on one screen, because the first page carries nothing but that button.
 */
@Composable
fun SecuritySystemTestScreen(onBack: () -> Unit, onDone: () -> Unit) {
  val viewModel: SecuritySystemTestViewModel = viewModel()
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(state.done) { if (state.done) onDone() }

  DetailScaffold(
    title = SecuritySteps.TestSystem.title,
    onBack = onBack,
    bottomBar = {
      Box(
        modifier = Modifier
          .navigationBarsPadding()
          .padding(horizontal = ScreenPadding, vertical = BottomBarPadding),
      ) {
        PrimaryButton(
          text = "Done",
          onClick = viewModel::finish,
          enabled = !state.loading,
          loading = state.busy,
        )
      }
    },
  ) {
    ErrorBanner(message = state.error)
    Hero()
    if (state.loading) {
      LoadingBox()
    } else {
      InfoNote(
        text = if (state.testMode) TEST_MODE_NOTE else NOT_IN_TEST_MODE_NOTE,
        icon = if (state.testMode) Icons.Outlined.Info else Icons.Outlined.WarningAmber,
        tint = if (state.testMode) AppTheme.colors.info else AppTheme.colors.warning,
      )
      TestControls(
        state = state,
        onStart = viewModel::startTestRun,
        onArm = viewModel::armSystem,
        onDisarm = viewModel::disarmSystem,
      )
      CameraStates(state = state, onRefresh = viewModel::load)
    }
  }
}

/** The headline and the precaution that go above the test controls. */
@Composable
private fun Hero() {
  Column(verticalArrangement = Arrangement.spacedBy(HeroGap)) {
    Text(
      text = "Test run the system",
      style = MaterialTheme.typography.headlineMedium,
      color = AppTheme.colors.textPrimary,
    )
    Text(
      text = HERO_BODY,
      style = MaterialTheme.typography.bodyLarge,
      color = AppTheme.colors.textSecondary,
    )
  }
}

/** The buttons that drive the test: one to start it, then arm and disarm for as long as it lasts. */
@Composable
private fun TestControls(
  state: SecuritySystemTestUiState,
  onStart: () -> Unit,
  onArm: () -> Unit,
  onDisarm: () -> Unit,
) {
  if (!state.testMode) {
    SecondaryButton(
      text = "Start test run",
      onClick = onStart,
      enabled = !state.busy,
    )
    return
  }
  if (state.armed) {
    SecondaryButton(text = "Disarm", onClick = onDisarm, enabled = !state.busy)
  } else {
    SecondaryButton(text = "Arm", onClick = onArm, enabled = !state.busy)
  }
}

/** How each armed camera is getting on, read back from the profile rather than guessed at. */
@Composable
private fun CameraStates(state: SecuritySystemTestUiState, onRefresh: () -> Unit) {
  if (state.deviceStates.isEmpty()) return
  SectionHeader(
    text = "Cameras",
    action = { TextLink(text = "Refresh", onClick = onRefresh, enabled = !state.busy) },
  )
  GroupCard {
    state.deviceStates.forEachIndexed { index, deviceState ->
      val name = SessionStore.devices
        .firstOrNull { it.id == deviceState.id }
        ?.name
        .orEmpty()
        .ifEmpty { deviceState.id }
      SettingRow(
        label = name,
        trailing = {
          StatusPill(
            text = deviceState.state,
            containerColor = AppTheme.colors.surfaceHigh,
            contentColor = AppTheme.colors.textSecondary,
          )
        },
      )
      if (index != state.deviceStates.lastIndex) RowDivider()
    }
  }
  Box(modifier = Modifier.padding(top = StatusGap), contentAlignment = Alignment.CenterStart) {
    Text(
      text = "System status: ${state.status.ifEmpty { "unknown" }}",
      style = MaterialTheme.typography.bodyMedium,
      color = AppTheme.colors.textSecondary,
    )
  }
}

/** Gap between the hero's headline and the line under it. */
private val HeroGap = 8.dp

/** Breathing room above the system status readout. */
private val StatusGap = 4.dp

/** Breathing room above and below the screen's bottom-bar button. */
private val BottomBarPadding = 12.dp
