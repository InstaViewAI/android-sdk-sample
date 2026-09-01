package ai.instavision.sandbox.ui.settings

import ai.instavision.sandbox.BuildConfig
import ai.instavision.sandbox.SampleApp
import ai.instavision.sandbox.ui.common.Avatar
import ai.instavision.sandbox.ui.common.ConfirmDialog
import ai.instavision.sandbox.ui.common.DestructiveButton
import ai.instavision.sandbox.ui.common.GroupCard
import ai.instavision.sandbox.ui.common.RootScaffold
import ai.instavision.sandbox.ui.common.RowDivider
import ai.instavision.sandbox.ui.common.SectionHeader
import ai.instavision.sandbox.ui.common.SettingRow
import ai.instavision.sandbox.ui.theme.AppTheme
import ai.instavision.guardian.sdk.data.entity.Device
import ai.instavision.guardian.sdk.data.entity.isOnline
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** Padding inside the profile header card, which has no rows of its own to bring it. */
private val ProfilePadding = 16.dp

/** Gap between the profile avatar and the name block beside it. */
private val ProfileSpacing = 14.dp

/**
 * Root of the settings tab: the signed-in profile, the ways into the account, home and camera
 * screens, and signing out. [onSignedOut] fires once the session has actually been dropped.
 */
@Suppress("LongParameterList")
@Composable
fun SettingsScreen(
  onAccount: () -> Unit,
  onChangePassword: () -> Unit,
  onEditSpace: () -> Unit,
  onAddCamera: () -> Unit,
  onSecuritySetup: () -> Unit,
  onCamera: (Device) -> Unit,
  onSignedOut: () -> Unit,
) {
  val viewModel: SettingsViewModel = viewModel()
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var confirmSignOut by remember { mutableStateOf(false) }

  LaunchedEffect(state.signedOut) { if (state.signedOut) onSignedOut() }

  RootScaffold(title = "Settings") {
    ProfileHeader(name = state.displayName, email = state.email, initial = state.avatarInitial)
    AccountSection(
      onAccount = onAccount,
      onChangePassword = onChangePassword,
      showChangePassword = state.passwordAuthEnabled,
    )
    SpaceSection(
      spaceName = state.spaceName,
      onEditSpace = onEditSpace,
      onAddCamera = onAddCamera,
      onSecuritySetup = onSecuritySetup,
    )
    CameraSection(
      devices = state.devices,
      onCamera = { device ->
        viewModel.selectDevice(device)
        onCamera(device)
      },
    )
    AboutSection()
    DestructiveButton(text = "Sign out", onClick = { confirmSignOut = true })
  }

  if (confirmSignOut) {
    ConfirmDialog(
      title = "Sign out?",
      message = "You will need your password to sign back in on this phone.",
      confirmLabel = "Sign out",
      onConfirm = {
        confirmSignOut = false
        viewModel.signOut()
      },
      onDismiss = { confirmSignOut = false },
    )
  }
}

/** Who is signed in, as an initial beside the account's name and email. */
@Composable
private fun ProfileHeader(name: String, email: String, initial: String) {
  GroupCard {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(all = ProfilePadding),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(ProfileSpacing),
    ) {
      Avatar(initial = initial)
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = name,
          style = MaterialTheme.typography.titleMedium,
          color = AppTheme.colors.textPrimary,
        )
        Text(
          text = email,
          style = MaterialTheme.typography.bodyMedium,
          color = AppTheme.colors.textSecondary,
        )
      }
    }
  }
}

/**
 * The account-scoped screens: the profile itself and, only when the account actually has a
 * password provider, the way to change it. A Google-only account never gets the row.
 */
@Composable
private fun AccountSection(
  onAccount: () -> Unit,
  onChangePassword: () -> Unit,
  showChangePassword: Boolean,
) {
  SectionHeader(text = "Account")
  GroupCard {
    SettingRow(
      label = "My account",
      icon = Icons.Outlined.AccountCircle,
      showChevron = true,
      onClick = onAccount,
    )
    if (showChangePassword) {
      RowDivider()
      SettingRow(
        label = "Change password",
        icon = Icons.Outlined.Key,
        showChevron = true,
        onClick = onChangePassword,
      )
    }
  }
}

/** Everything scoped to the active home, including the two ways of growing it. */
@Composable
private fun SpaceSection(
  spaceName: String?,
  onEditSpace: () -> Unit,
  onAddCamera: () -> Unit,
  onSecuritySetup: () -> Unit,
) {
  SectionHeader(text = "Space")
  GroupCard {
    SettingRow(
      label = "Edit space",
      icon = Icons.Outlined.Home,
      value = spaceName,
      showChevron = true,
      onClick = onEditSpace,
    )
    RowDivider()
    SettingRow(
      label = "Add a camera",
      icon = Icons.Outlined.AddAPhoto,
      showChevron = true,
      onClick = onAddCamera,
    )
    RowDivider()
    SettingRow(
      label = "Professional monitoring",
      icon = Icons.Outlined.Shield,
      showChevron = true,
      onClick = onSecuritySetup,
    )
  }
}

/** One row per paired camera; the whole section is dropped while the home has none. */
@Composable
private fun CameraSection(devices: List<Device>, onCamera: (Device) -> Unit) {
  if (devices.isEmpty()) return
  SectionHeader(text = "Cameras")
  GroupCard {
    devices.forEachIndexed { index, device ->
      if (index > 0) RowDivider()
      SettingRow(
        label = device.name,
        icon = Icons.Outlined.Videocam,
        value = if (device.isOnline()) "Online" else "Offline",
        showChevron = true,
        onClick = { onCamera(device) },
      )
    }
  }
}

/** Read-only build and SDK configuration, which is the first thing a support ticket asks for. */
@Composable
private fun AboutSection() {
  SectionHeader(text = "About")
  GroupCard {
    SettingRow(
      label = "Version",
      value = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
    )
    RowDivider()
    SettingRow(label = "Environment", value = SampleApp.environment.name)
    RowDivider()
    SettingRow(label = "Partner", value = BuildConfig.PARTNER_ID)
  }
}
