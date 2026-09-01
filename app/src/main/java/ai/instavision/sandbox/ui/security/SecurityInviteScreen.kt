package ai.instavision.sandbox.ui.security

import ai.instavision.sandbox.ui.common.AppTextField
import ai.instavision.sandbox.ui.common.Avatar
import ai.instavision.sandbox.ui.common.DetailScaffold
import ai.instavision.sandbox.ui.common.ErrorBanner
import ai.instavision.sandbox.ui.common.GroupCard
import ai.instavision.sandbox.ui.common.LoadingBox
import ai.instavision.sandbox.ui.common.PrimaryButton
import ai.instavision.sandbox.ui.common.RowDivider
import ai.instavision.sandbox.ui.common.ScreenPadding
import ai.instavision.sandbox.ui.common.SecondaryButton
import ai.instavision.sandbox.ui.common.SectionHeader
import ai.instavision.sandbox.ui.theme.AppTheme
import ai.instavision.guardian.sdk.data.entity.InvitedSpace
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** What a household member can do once they accept, which is a good deal more than view cameras. */
private const val HERO_BODY =
  "Household members can arm and disarm the system, see your cameras and answer an alarm."

/** Said in place of the list while nobody has been invited; the step is optional. */
private const val EMPTY_BODY =
  "Nobody invited yet. You can finish this step without inviting anyone — it is optional."

/**
 * The optional invite household step: the people who can answer for the alarm besides the owner.
 * Every invite goes out as a space invite flagged `is_security`, which is what puts the member in
 * the monitoring group rather than only on the cameras.
 */
@Composable
fun SecurityInviteScreen(onBack: () -> Unit, onDone: () -> Unit) {
  val viewModel: SecurityInviteViewModel = viewModel()
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(state.done) { if (state.done) onDone() }

  DetailScaffold(
    title = SecuritySteps.InviteHouseholds.title,
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
    AppTextField(
      value = state.email,
      onValueChange = viewModel::onEmailChange,
      placeholder = "Email address",
      enabled = !state.busy && state.hasRoom,
      keyboardType = KeyboardType.Email,
    )
    Text(
      text = state.limitMessage,
      style = MaterialTheme.typography.bodySmall,
      color = if (state.hasRoom) AppTheme.colors.textTertiary else AppTheme.colors.warning,
    )
    SecondaryButton(
      text = "Send invite",
      onClick = viewModel::invite,
      enabled = state.canInvite && !state.busy,
    )
    if (state.loading) {
      LoadingBox()
    } else {
      MemberList(state = state, onRemove = viewModel::remove)
    }
  }
}

/** The headline that says what an invite grants before the field asks for an address. */
@Composable
private fun Hero() {
  Column(verticalArrangement = Arrangement.spacedBy(HeroGap)) {
    Text(
      text = "Invite your household",
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

/** Everyone monitoring is shared with, each row carrying the way to withdraw them. */
@Composable
private fun MemberList(state: SecurityInviteUiState, onRemove: (InvitedSpace) -> Unit) {
  SectionHeader(text = "Household")
  if (state.members.isEmpty()) {
    Text(
      text = EMPTY_BODY,
      style = MaterialTheme.typography.bodyMedium,
      color = AppTheme.colors.textSecondary,
    )
    return
  }
  GroupCard {
    state.members.forEachIndexed { index, member ->
      MemberRow(
        member = member,
        status = state.statusLabel(member),
        enabled = !state.busy,
        onRemove = { onRemove(member) },
      )
      if (index != state.members.lastIndex) RowDivider()
    }
  }
}

/** One household member: their initial, who they are, how their invite stands, and a way out. */
@Composable
private fun MemberRow(
  member: InvitedSpace,
  status: String,
  enabled: Boolean,
  onRemove: () -> Unit,
) {
  val title = member.name.ifEmpty { member.email }
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = RowPadding, vertical = RowVerticalPadding),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(RowSpacing),
  ) {
    Avatar(initial = title.take(1), size = AvatarSize)
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(TitleGap),
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = AppTheme.colors.textPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = status,
        style = MaterialTheme.typography.bodyMedium,
        color = AppTheme.colors.textSecondary,
      )
    }
    IconButton(onClick = onRemove, enabled = enabled) {
      Icon(
        imageVector = Icons.Outlined.Close,
        contentDescription = "Remove $title",
        tint = AppTheme.colors.textSecondary,
      )
    }
  }
}

/** Gap between the hero's headline and the line under it. */
private val HeroGap = 8.dp

/** Horizontal padding inside a member row, matching the design system's own rows. */
private val RowPadding = 16.dp

/** Vertical padding inside a member row. */
private val RowVerticalPadding = 12.dp

/** Gap between the parts of a member row. */
private val RowSpacing = 12.dp

/** Gap between a member's name and their status line. */
private val TitleGap = 4.dp

/** Diameter of a member's avatar, smaller than the design system's default for a list row. */
private val AvatarSize = 40.dp

/** Breathing room above and below the screen's bottom-bar button. */
private val BottomBarPadding = 12.dp
