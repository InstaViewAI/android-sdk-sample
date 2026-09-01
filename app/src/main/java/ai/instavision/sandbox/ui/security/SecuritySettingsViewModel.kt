package ai.instavision.sandbox.ui.security

import ai.instavision.sandbox.data.SessionStore
import ai.instavision.sandbox.ui.common.sdkCall
import ai.instavision.sandbox.ui.common.userMessage
import ai.instavision.guardian.sdk.InstaVision
import ai.instavision.guardian.sdk.data.entity.request.SecurityProfileRequest
import ai.instavision.guardian.sdk.data.entity.response.SecurityProfileResponse
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Exit delays production's own picker offers, in seconds; its `TimeDelay` enum in full. */
private val SettingsExitDelays = listOf(30, 45, 60, 90, 120)

/** Windows production's picker offers for cancelling an alarm; the same enum minus 45 seconds. */
private val SettingsDismissalWindows = listOf(30, 60, 90, 120)

/** Shown in place of a delay the home has never had written to it, which reads back as zero. */
private const val NOT_SET = "Not set"

/** Which of the two delay rows has its picker open, since they share one dialog. */
enum class DelayPicker {
  /** The grace period between arming and the alarm going live. */
  ExitDelay,

  /** The window the household has to cancel an alarm before the monitoring centre acts. */
  DismissalWindow,
}

/** Everything the security settings screen renders. */
data class SecuritySettingsUiState(
  /** The home's monitoring profile; null when the home has never started setup. */
  val profile: SecurityProfileResponse? = null,
  /** Whether alarms are being kept away from the monitoring centre, from `test_mode`. */
  val testMode: Boolean = false,
  /** The delay row whose picker is open, or null while neither is. */
  val picker: DelayPicker? = null,
  /** True until the first profile read settles. */
  val loading: Boolean = true,
  /** True while a settings write is in flight, which freezes every row. */
  val busy: Boolean = false,
  /** Message from the last failed request; a missing profile never sets this. */
  val error: String? = null,
) {
  /** Whether the checklist has been walked to its end, which is what unlocks these settings. */
  val setupComplete: Boolean get() = profile?.setupStep == SecuritySteps.Completed.apiName

  /** The home's city, which is what the personal information row shows in place of the address. */
  val city: String get() = profile?.address?.city.orEmpty()

  /**
   * Whether a safe word exists. The profile response never returns the word itself, so the only
   * signal available is the disarm step having been completed, which is where it is set.
   */
  val safeWordSet: Boolean
    get() = SecuritySteps.DisarmSettings.apiName in profile?.completedSteps.orEmpty()

  /** How many people the monitoring centre calls, from the profile's `responding_parties`. */
  val callListCount: Int get() = profile?.respondingParties.orEmpty().size

  /** How many cameras monitoring arms, which is the length of the profile's device list. */
  val cameraCount: Int get() = profile?.deviceList.orEmpty().size

  /** The exit delay written the way the row shows it. */
  val exitDelayLabel: String get() = secondsLabel(profile?.exitDelay ?: 0)

  /** The cancellation window written the same way. */
  val dismissalLabel: String get() = secondsLabel(profile?.dismissalWindow ?: 0)

  /** The seconds the open picker offers, and the one of them currently in force. */
  val pickerOptions: List<Int>
    get() = when (picker) {
      DelayPicker.ExitDelay -> SettingsExitDelays
      DelayPicker.DismissalWindow -> SettingsDismissalWindows
      null -> emptyList()
    }

  /** The value the open picker should show as chosen. */
  val pickerSelection: Int
    get() = when (picker) {
      DelayPicker.ExitDelay -> profile?.exitDelay ?: 0
      DelayPicker.DismissalWindow -> profile?.dismissalWindow ?: 0
      null -> 0
    }

  /** Turns a delay in seconds into its row value; a delay of zero has never been written. */
  private fun secondsLabel(seconds: Int): String =
    if (seconds > 0) "$seconds seconds" else NOT_SET
}

/**
 * Backs the security settings screen: the post-setup home for everything the checklist collected.
 *
 * Every write here goes through `updateSettings`, the `PATCH …/security/settings` endpoint, rather
 * than `updateProfile` — the setup steps use the latter because they carry `setup_step` with them,
 * and the two are not interchangeable.
 */
class SecuritySettingsViewModel : ViewModel() {
  private val _uiState = MutableStateFlow(SecuritySettingsUiState())

  /** Single source of truth for [SecuritySettingsScreen]. */
  val uiState = _uiState.asStateFlow()

  init {
    load()
  }

  /**
   * Rereads the profile every setting on the screen is drawn from, leaving what is already on
   * screen in place while it runs so returning from a sub-screen does not blank the rows.
   */
  fun load() {
    val spaceId = SessionStore.spaceId
    if (spaceId.isEmpty()) {
      _uiState.update { it.copy(loading = false) }
      return
    }
    _uiState.update { it.copy(loading = it.profile == null, error = null) }
    viewModelScope.launch {
      fetchSecurityProfile(spaceId)
        .onSuccess { profile -> publish(profile) }
        .onFailure { error ->
          _uiState.update { it.copy(loading = false, error = error.userMessage()) }
        }
    }
  }

  /** Opens the picker for one of the two delay rows, or closes whichever is open with null. */
  fun showPicker(picker: DelayPicker?) {
    _uiState.update { it.copy(picker = picker, error = null) }
  }

  /** Writes the [seconds] chosen in the open picker to whichever delay that picker belongs to. */
  fun pickDelay(seconds: Int) {
    val request = when (_uiState.value.picker) {
      DelayPicker.ExitDelay -> SecurityProfileRequest(exitDelay = seconds)
      DelayPicker.DismissalWindow -> SecurityProfileRequest(dismissalWindow = seconds)
      null -> return
    }
    _uiState.update { it.copy(picker = null) }
    updateSettings(request)
  }

  /**
   * Turns test mode on or off. This is the one write on the screen that is not a settings patch:
   * the SDK gives test mode an endpoint of its own, shared with the checklist's test step.
   */
  fun setTestMode(enabled: Boolean) {
    val spaceId = SessionStore.spaceId
    if (spaceId.isEmpty()) return
    _uiState.update { it.copy(busy = true, error = null) }
    viewModelScope.launch {
      enableTestMode(spaceId = spaceId, enable = enabled)
        .onSuccess { _uiState.update { it.copy(busy = false, testMode = enabled) } }
        .onFailure { error ->
          _uiState.update { it.copy(busy = false, error = error.userMessage()) }
        }
    }
  }

  /**
   * Sends one settings patch and republishes the profile the backend answers with, so a row only
   * ever shows a value the monitoring service has actually accepted.
   */
  private fun updateSettings(request: SecurityProfileRequest) {
    val spaceId = SessionStore.spaceId
    if (spaceId.isEmpty()) return
    _uiState.update { it.copy(busy = true, error = null) }
    viewModelScope.launch {
      sdkCall<SecurityProfileResponse> { onSuccess, onError ->
        InstaVision.securityServices.updateSettings(
          spaceId = spaceId,
          request = request,
          onSuccess = onSuccess,
          onError = onError,
        )
      }
        .onSuccess { profile ->
          publish(profile)
          _uiState.update { it.copy(busy = false) }
        }
        .onFailure { error ->
          _uiState.update { it.copy(busy = false, error = error.userMessage()) }
        }
    }
  }

  /** Folds a freshly read profile into the state; a null profile means setup was never started. */
  private fun publish(profile: SecurityProfileResponse?) {
    _uiState.update {
      it.copy(profile = profile, testMode = profile?.testMode == true, loading = false)
    }
  }
}
