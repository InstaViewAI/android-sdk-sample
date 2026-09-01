package ai.instavision.sandbox.ui.settings

import ai.instavision.sandbox.ui.common.AppDropdownField
import ai.instavision.sandbox.ui.common.AppTextField
import ai.instavision.sandbox.ui.common.ConfirmDialog
import ai.instavision.sandbox.ui.common.DestructiveButton
import ai.instavision.sandbox.ui.common.DetailScaffold
import ai.instavision.sandbox.ui.common.EmptyState
import ai.instavision.sandbox.ui.common.ErrorBanner
import ai.instavision.sandbox.ui.common.GroupCard
import ai.instavision.sandbox.ui.common.LoadingBox
import ai.instavision.sandbox.ui.common.Notice
import ai.instavision.sandbox.ui.common.PrimaryButton
import ai.instavision.sandbox.ui.common.RowDivider
import ai.instavision.sandbox.ui.common.SectionHeader
import ai.instavision.sandbox.ui.common.SelectableChip
import ai.instavision.sandbox.ui.common.SettingRow
import ai.instavision.sandbox.ui.common.TextLink
import ai.instavision.sandbox.ui.common.ToggleRow
import ai.instavision.sandbox.ui.theme.AppTheme
import ai.instavision.guardian.sdk.data.entity.Invitations
import ai.instavision.guardian.sdk.data.entity.InvitedSpace
import ai.instavision.guardian.sdk.data.enums.TemperatureUnit
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Face
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

/** How long a "saved" confirmation stays on screen before it clears itself. */
private const val SPACE_NOTICE_DURATION_MS = 2500L

/** Headline of the placeholder shown when the user arrived without picking a home. */
private const val NO_SPACE_TITLE = "No home selected"

/** Body of the placeholder shown when the user arrived without picking a home. */
private const val NO_SPACE_MESSAGE = "Pick a home on the home screen to change its settings."

/**
 * Editor for [ai.instavision.sandbox.data.SessionStore.selectedSpace]: its name and address, the
 * features every camera in it shares, who can see it, the invitations out on it, its mobile data
 * allowance and the way out of it. The screen pops itself once the account has left the home.
 */
@Composable
fun SpaceSettingsScreen(onBack: () -> Unit) {
  val viewModel: SpaceSettingsViewModel = viewModel()
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var memberToRemove by remember { mutableStateOf<InvitedSpace?>(null) }
  var confirmLeave by remember { mutableStateOf(false) }

  LaunchedEffect(state.left) { if (state.left) onBack() }
  LaunchedEffect(state.notice) {
    if (state.notice != null) {
      delay(SPACE_NOTICE_DURATION_MS)
      viewModel.dismissNotice()
    }
  }

  DetailScaffold(title = "Edit space", onBack = onBack) {
    when {
      state.loading -> LoadingBox()
      state.space == null -> EmptyState(title = NO_SPACE_TITLE, body = NO_SPACE_MESSAGE)
      else -> {
        ErrorBanner(message = state.error)
        Notice(message = state.notice)
        DetailsForm(state = state, viewModel = viewModel)
        FeaturesSection(
          state = state,
          onFaceRecognitionChange = viewModel::setFaceRecognition,
          onTemperatureUnitChange = viewModel::setTemperatureUnit,
        )
        MembersSection(state = state, onRemove = { memberToRemove = it })
        InvitationsSection(
          state = state,
          onInviteEmailChange = viewModel::onInviteEmailChange,
          onInviteRoleChange = viewModel::onInviteRoleChange,
          onSendInvite = viewModel::sendInvite,
          onChangeRole = viewModel::changeInviteRole,
          onCancelInvite = viewModel::cancelInvite,
        )
        if (state.cellularAvailable) {
          CellularSection(state = state)
        }
        DestructiveButton(
          text = "Leave home",
          onClick = { confirmLeave = true },
          enabled = !state.busy,
        )
      }
    }
  }

  val member = memberToRemove
  if (member != null) {
    ConfirmDialog(
      title = "Remove ${member.email}?",
      message = "They lose access to every camera in this home straight away.",
      confirmLabel = "Remove",
      onConfirm = {
        memberToRemove = null
        viewModel.removeMember(member)
      },
      onDismiss = { memberToRemove = null },
    )
  }
  if (confirmLeave) {
    ConfirmDialog(
      title = "Leave ${state.space?.name.orEmpty()}?",
      message = "You lose access to its cameras and recordings. Someone with access can " +
        "invite you back.",
      confirmLabel = "Leave",
      onConfirm = {
        confirmLeave = false
        viewModel.leaveSpace()
      },
      onDismiss = { confirmLeave = false },
    )
  }
}

/** The home's name and address, saved as one, since the SDK takes the address whole. */
@Composable
private fun DetailsForm(state: SpaceSettingsUiState, viewModel: SpaceSettingsViewModel) {
  AppTextField(
    value = state.name,
    onValueChange = viewModel::onNameChange,
    placeholder = "Space name",
    enabled = !state.busy,
  )
  SectionHeader(text = "Address (optional)")
  Column(verticalArrangement = Arrangement.spacedBy(FieldGap)) {
    AppTextField(
      value = state.street,
      onValueChange = viewModel::onStreetChange,
      placeholder = "Street",
      enabled = !state.busy,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(FieldGap)) {
      AppTextField(
        value = state.city,
        onValueChange = viewModel::onCityChange,
        placeholder = "City",
        modifier = Modifier.weight(1f),
        enabled = !state.busy,
      )
      AppTextField(
        value = state.region,
        onValueChange = viewModel::onRegionChange,
        placeholder = "State",
        modifier = Modifier.weight(1f),
        enabled = !state.busy,
      )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(FieldGap)) {
      AppTextField(
        value = state.postalCode,
        onValueChange = viewModel::onPostalCodeChange,
        placeholder = "ZIP",
        modifier = Modifier.weight(1f),
        enabled = !state.busy,
      )
      AppDropdownField(
        value = state.country,
        options = state.countryOptions,
        onSelect = viewModel::onCountryChange,
        placeholder = "Country",
        modifier = Modifier.weight(1f),
        enabled = !state.busy,
      )
    }
  }
  PrimaryButton(
    text = "Save changes",
    onClick = viewModel::saveDetails,
    enabled = state.name.isNotBlank(),
    loading = state.busy,
  )
}

/** The settings every camera in the home shares. */
@Composable
private fun FeaturesSection(
  state: SpaceSettingsUiState,
  onFaceRecognitionChange: (Boolean) -> Unit,
  onTemperatureUnitChange: (TemperatureUnit) -> Unit,
) {
  SectionHeader(text = "Features")
  GroupCard {
    ToggleRow(
      title = "Face recognition",
      checked = state.faceRecognitionEnabled,
      onCheckedChange = onFaceRecognitionChange,
      icon = Icons.Outlined.Face,
      description = "Name the people your cameras already know in the event timeline",
      enabled = !state.busy,
    )
  }
  SectionHeader(text = "Temperature unit")
  Row(horizontalArrangement = Arrangement.spacedBy(ChipGap)) {
    TemperatureUnit.entries.forEach { unit ->
      SelectableChip(
        label = "°${unit.value}",
        selected = unit == state.temperatureUnit,
        onClick = { onTemperatureUnitChange(unit) },
        enabled = !state.busy,
      )
    }
  }
}

/** Everyone who can already see this home, each with the way to take that access back. */
@Composable
private fun MembersSection(state: SpaceSettingsUiState, onRemove: (InvitedSpace) -> Unit) {
  SectionHeader(text = "Members")
  GroupCard {
    if (state.members.isEmpty()) {
      SettingRow(label = "Nobody else has access to this home yet", enabled = false)
      return@GroupCard
    }
    state.members.forEachIndexed { index, member ->
      if (index > 0) RowDivider()
      SettingRow(
        label = member.name.ifEmpty { member.email },
        value = "${member.role} · ${member.status}",
        trailing = {
          TextLink(
            text = "Remove",
            onClick = { onRemove(member) },
            enabled = !state.busy,
            color = AppTheme.colors.danger,
          )
        },
      )
    }
  }
}

/** The invite form and the invitations that are still outstanding for this home. */
@Composable
private fun InvitationsSection(
  state: SpaceSettingsUiState,
  onInviteEmailChange: (String) -> Unit,
  onInviteRoleChange: (String) -> Unit,
  onSendInvite: () -> Unit,
  onChangeRole: (Invitations, String) -> Unit,
  onCancelInvite: (Invitations) -> Unit,
) {
  SectionHeader(text = "Invitations")
  Column(verticalArrangement = Arrangement.spacedBy(FieldGap)) {
    AppTextField(
      value = state.inviteEmail,
      onValueChange = onInviteEmailChange,
      placeholder = "Email address",
      enabled = !state.busy,
      keyboardType = KeyboardType.Email,
    )
    Row(
      modifier = Modifier.horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(ChipGap),
    ) {
      state.inviteRoles.forEach { role ->
        SelectableChip(
          label = role,
          selected = role == state.inviteRole,
          onClick = { onInviteRoleChange(role) },
          enabled = !state.busy,
        )
      }
    }
    PrimaryButton(
      text = "Send invite",
      onClick = onSendInvite,
      enabled = state.inviteEmail.isNotBlank(),
      loading = state.busy,
    )
  }
  state.invites.forEach { invite ->
    InviteCard(
      invite = invite,
      roles = state.inviteRoles,
      enabled = !state.busy,
      onChangeRole = onChangeRole,
      onCancelInvite = onCancelInvite,
    )
  }
}

/** One outstanding invitation, with the roles it can be reissued as and the way to withdraw it. */
@Composable
private fun InviteCard(
  invite: Invitations,
  roles: List<String>,
  enabled: Boolean,
  onChangeRole: (Invitations, String) -> Unit,
  onCancelInvite: (Invitations) -> Unit,
) {
  GroupCard {
    SettingRow(
      label = "From ${invite.inviter.name.ifEmpty { invite.inviter.email }}",
      value = "${invite.type} · ${invite.status}",
      trailing = {
        TextLink(
          text = "Cancel",
          onClick = { onCancelInvite(invite) },
          enabled = enabled,
          color = AppTheme.colors.danger,
        )
      },
    )
    RowDivider()
    SettingRow(
      label = "Role",
      trailing = {
        Row(horizontalArrangement = Arrangement.spacedBy(ChipGap)) {
          roles.forEach { role ->
            TextLink(
              text = role,
              onClick = { onChangeRole(invite, role) },
              enabled = enabled,
            )
          }
        }
      },
    )
  }
}

/** Read-only mobile data figures, shown only for homes with a camera on a mobile network. */
@Composable
private fun CellularSection(state: SpaceSettingsUiState) {
  SectionHeader(text = "Mobile data")
  val data = state.cellularData
  GroupCard {
    SettingRow(
      label = "Allowance",
      value = if (data == null) {
        "Unavailable"
      } else {
        "${data.remainingData} MB left of ${data.totalData} MB"
      },
    )
    RowDivider()
    SettingRow(
      label = "Auto top-up",
      value = state.autoTopUp?.let { if (it.enable) "On · ${it.skuName}" else "Off" } ?: "Off",
    )
    state.dataPasses.forEach { pass ->
      RowDivider()
      SettingRow(label = pass.skuName, value = "${pass.totalData} MB · ${pass.status}")
    }
  }
}

/** Gap between two fields of the same address block. */
private val FieldGap = 12.dp

/** Gap between two chips of the same strip. */
private val ChipGap = 8.dp
