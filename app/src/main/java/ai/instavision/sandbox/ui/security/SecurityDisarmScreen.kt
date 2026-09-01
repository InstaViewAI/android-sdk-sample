package ai.instavision.sandbox.ui.security

import ai.instavision.sandbox.ui.common.AppTextField
import ai.instavision.sandbox.ui.common.DetailScaffold
import ai.instavision.sandbox.ui.common.ErrorBanner
import ai.instavision.sandbox.ui.common.InfoNote
import ai.instavision.sandbox.ui.common.PrimaryButton
import ai.instavision.sandbox.ui.common.ScreenPadding
import ai.instavision.sandbox.ui.common.SectionHeader
import ai.instavision.sandbox.ui.theme.AppTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** How the household stands the alarm down, which is the whole of production's first disarm page. */
private const val DISARM_METHOD_BODY =
  "You can disarm the system from the Security tab, which only takes one tap."

/** What a monitoring agent does with the safe word, and why it has to be sayable under stress. */
private const val SAFE_WORD_BODY =
  "A safe word is a unique word our monitoring agent uses to confirm it is really you. Pick a " +
    "word you can say clearly in a high-stress situation."

/** The consequence of losing the safe word, which the field alone does not convey. */
private const val SAFE_WORD_NOTE =
  "An agent who does not hear the safe word treats the alarm as unconfirmed and escalates it."

/**
 * The disarm settings step: how the alarm is stood down, and the spoken safe word the monitoring
 * centre confirms with. Production splits these across two pages of a pager; the sample keeps them
 * on one screen because only the second half asks anything of the user.
 */
@Composable
fun SecurityDisarmScreen(onBack: () -> Unit, onDone: () -> Unit, standalone: Boolean = false) {
  val viewModel: SecurityDisarmViewModel = viewModel()
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(state.done) { if (state.done) onDone() }

  DetailScaffold(
    title = if (standalone) "Safe word" else SecuritySteps.DisarmSettings.title,
    onBack = onBack,
    bottomBar = {
      Box(
        modifier = Modifier
          .navigationBarsPadding()
          .padding(horizontal = ScreenPadding, vertical = BottomBarPadding),
      ) {
        PrimaryButton(
          text = "Done",
          onClick = { viewModel.submit(markStep = !standalone) },
          enabled = state.canSubmit,
          loading = state.busy,
        )
      }
    },
  ) {
    ErrorBanner(message = state.error)
    Hero()
    SectionHeader(text = "Safe word")
    Text(
      text = SAFE_WORD_BODY,
      style = MaterialTheme.typography.bodyMedium,
      color = AppTheme.colors.textSecondary,
    )
    AppTextField(
      value = state.safeWord,
      onValueChange = viewModel::onSafeWordChange,
      placeholder = "Your safe word",
      enabled = !state.busy,
    )
    Text(
      text = "${state.remaining} characters left",
      style = MaterialTheme.typography.bodySmall,
      color = AppTheme.colors.textTertiary,
      textAlign = TextAlign.End,
      modifier = Modifier.fillMaxWidth(),
    )
    InfoNote(text = SAFE_WORD_NOTE)
  }
}

/** The headline and the line that explain how disarming works before the safe word is asked for. */
@Composable
private fun Hero() {
  Column(verticalArrangement = Arrangement.spacedBy(HeroGap)) {
    Text(
      text = "Disarm methods",
      style = MaterialTheme.typography.headlineMedium,
      color = AppTheme.colors.textPrimary,
    )
    Text(
      text = DISARM_METHOD_BODY,
      style = MaterialTheme.typography.bodyLarge,
      color = AppTheme.colors.textSecondary,
    )
  }
}

/** Gap between the hero's headline and the line under it. */
private val HeroGap = 8.dp

/** Breathing room above and below the screen's bottom-bar button. */
private val BottomBarPadding = 12.dp
