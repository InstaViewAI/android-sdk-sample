package ai.instavision.sandbox.ui.security

import ai.instavision.sandbox.ui.common.AppDropdownField
import ai.instavision.sandbox.ui.common.DetailScaffold
import ai.instavision.sandbox.ui.common.ErrorBanner
import ai.instavision.sandbox.ui.common.InfoNote
import ai.instavision.sandbox.ui.common.LoadingBox
import ai.instavision.sandbox.ui.common.PrimaryButton
import ai.instavision.sandbox.ui.common.ScreenPadding
import ai.instavision.sandbox.ui.common.SectionHeader
import ai.instavision.sandbox.ui.theme.AppTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** What armed mode is, in the production step's own words. */
private const val ARM_BODY_ONE =
  "Armed mode monitors your home, protecting it while you are away."

/** How the cameras chosen on the previous step relate to armed mode. */
private const val ARM_BODY_TWO =
  "The cameras and zones you set up are the ones armed mode watches."

/** What the exit delay is for, which is not obvious from a bare number of seconds. */
private const val EXIT_DELAY_NOTE =
  "The exit delay is how long you have to leave after arming before the alarm goes live."

/** Reassurance that this is not the last chance to change any of it. */
private const val ARM_FOOTNOTE =
  "You can change these in security settings once setup is complete."

/**
 * The arm settings step: what armed mode does, and the grace period the household gets to leave
 * before the alarm goes live.
 */
@Composable
fun SecurityArmScreen(onBack: () -> Unit, onDone: () -> Unit) {
  val viewModel: SecurityArmViewModel = viewModel()
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(state.done) { if (state.done) onDone() }

  DetailScaffold(
    title = SecuritySteps.ArmSettings.title,
    onBack = onBack,
    bottomBar = {
      Box(
        modifier = Modifier
          .navigationBarsPadding()
          .padding(horizontal = ScreenPadding, vertical = BottomBarPadding),
      ) {
        PrimaryButton(
          text = "Done",
          onClick = viewModel::submit,
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
      SectionHeader(text = "Exit delay")
      AppDropdownField(
        value = state.delayLabel,
        options = state.delayLabels,
        onSelect = viewModel::onDelayChange,
        placeholder = "Exit delay",
        enabled = !state.busy,
      )
      InfoNote(text = EXIT_DELAY_NOTE)
      Text(
        text = ARM_FOOTNOTE,
        style = MaterialTheme.typography.bodyMedium,
        color = AppTheme.colors.textSecondary,
      )
    }
  }
}

/** The headline and the two lines that explain armed mode before anything is asked of the user. */
@Composable
private fun Hero() {
  Column(verticalArrangement = Arrangement.spacedBy(HeroGap)) {
    Text(
      text = "Arm mode",
      style = MaterialTheme.typography.headlineMedium,
      color = AppTheme.colors.textPrimary,
    )
    Text(
      text = ARM_BODY_ONE,
      style = MaterialTheme.typography.bodyLarge,
      color = AppTheme.colors.textSecondary,
    )
    Text(
      text = ARM_BODY_TWO,
      style = MaterialTheme.typography.bodyLarge,
      color = AppTheme.colors.textSecondary,
    )
  }
}

/** Gap between the hero's headline and the lines under it. */
private val HeroGap = 8.dp

/** Breathing room above and below the screen's bottom-bar button. */
private val BottomBarPadding = 12.dp
