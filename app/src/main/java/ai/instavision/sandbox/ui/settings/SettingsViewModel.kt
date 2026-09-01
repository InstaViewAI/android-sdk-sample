package ai.instavision.sandbox.ui.settings

import ai.instavision.sandbox.data.SessionStore
import ai.instavision.guardian.sdk.InstaVision
import ai.instavision.guardian.sdk.data.entity.Device
import ai.instavision.guardian.sdk.data.entity.Space
import ai.instavision.guardian.sdk.data.entity.User
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Initial shown in the profile avatar when neither a name nor an email is cached yet. */
private const val FALLBACK_INITIAL = "?"

/** Everything the settings tab renders. */
data class SettingsUiState(
  /** The signed-in account, mirrored from [SessionStore.user]. */
  val user: User? = null,
  /** The active home, mirrored from [SessionStore.selectedSpace]. */
  val space: Space? = null,
  /** Cameras in [space], mirrored from [SessionStore.devices]. */
  val devices: List<Device> = emptyList(),
  /** Whether the signed-in account has a password to change; false for a Google-only account. */
  val passwordAuthEnabled: Boolean = false,
  /** Set once the session has been dropped so the screen can leave the tabs. */
  val signedOut: Boolean = false,
) {
  /** Name to head the profile card with, falling back to the email when no name is set. */
  val displayName: String
    get() = user?.name?.let { "${it.first} ${it.last}".trim() }?.takeIf { it.isNotBlank() }
      ?: user?.email.orEmpty()

  /** Email under the profile name; blank until the account is cached. */
  val email: String get() = user?.email.orEmpty()

  /** Single letter for the profile avatar, taken from the name and then from the email. */
  val avatarInitial: String
    get() = displayName.firstOrNull()?.toString() ?: FALLBACK_INITIAL

  /** Name of the active home, shown as the value of the "Edit space" row. */
  val spaceName: String? get() = space?.name
}

/**
 * Drives the settings tab off [SessionStore], so a home renamed or a camera paired elsewhere shows
 * up here without this screen refetching anything. It also owns signing out, the one account-wide
 * action that has no screen of its own.
 */
class SettingsViewModel : ViewModel() {
  /**
   * Mutable backing state. `isPasswordAuthEnabled` answers for the account signed in right now, so
   * it is read once here rather than during composition.
   */
  private val _uiState = MutableStateFlow(
    SettingsUiState(passwordAuthEnabled = InstaVision.userServices.isPasswordAuthEnabled()),
  )

  /** Single source of truth for [SettingsScreen]. */
  val uiState = _uiState.asStateFlow()

  init {
    mirrorStore()
  }

  /** Records the camera the user tapped so the device settings screen knows what to edit. */
  fun selectDevice(device: Device) {
    SessionStore.selectDevice(device)
  }

  /**
   * Ends the session, drops the push registration and empties the cached account data. The SDK's
   * logout is synchronous and reports nothing, so the screen leaves as soon as this returns.
   */
  fun signOut() {
    InstaVision.userServices.logout(pushToken = "")
    SessionStore.clear()
    _uiState.update { it.copy(signedOut = true) }
  }

  /** Mirrors the store's Compose state into the UI state, one collector per observed property. */
  private fun mirrorStore() {
    viewModelScope.launch {
      snapshotFlow { SessionStore.user }
        .collect { user -> _uiState.update { it.copy(user = user) } }
    }
    viewModelScope.launch {
      snapshotFlow { SessionStore.selectedSpace }
        .collect { space -> _uiState.update { it.copy(space = space) } }
    }
    viewModelScope.launch {
      snapshotFlow { SessionStore.devices }
        .collect { devices -> _uiState.update { it.copy(devices = devices) } }
    }
  }
}
