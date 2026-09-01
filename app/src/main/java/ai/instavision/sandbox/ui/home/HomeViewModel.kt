package ai.instavision.sandbox.ui.home

import ai.instavision.sandbox.data.SessionStore
import ai.instavision.sandbox.ui.common.sdkCall
import ai.instavision.sandbox.ui.common.userMessage
import ai.instavision.guardian.sdk.InstaVision
import ai.instavision.guardian.sdk.data.entity.Device
import ai.instavision.guardian.sdk.data.entity.Space
import ai.instavision.guardian.sdk.data.entity.isOnline
import ai.instavision.guardian.sdk.data.entity.request.UpdateDeviceRequest
import ai.instavision.guardian.sdk.data.enums.PairingStatus
import android.util.Log
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Everything the home screen renders: the homes the account can see and the cameras in the
 * currently selected one.
 */
data class HomeUiState(
  /** Homes returned by the account; the switcher is only offered when there is more than one. */
  val spaces: List<Space> = emptyList(),
  /** Home whose cameras are listed, mirrored from [SessionStore.selectedSpace]. */
  val selectedSpace: Space? = null,
  /** Cameras paired to [selectedSpace], mirrored from [SessionStore.devices]. */
  val devices: List<Device> = emptyList(),
  /** True while the home list is being fetched and there is nothing yet to show. */
  val loadingSpaces: Boolean = true,
  /** True while the camera list is being fetched and there is nothing yet to show. */
  val loadingDevices: Boolean = true,
  /** Message from the last failed home or camera request, cleared when a refresh starts. */
  val error: String? = null,
) {
  /** True while either request is still in flight; the screen shows one spinner for both. */
  val loading: Boolean get() = loadingSpaces || loadingDevices

  /** Cameras in the selected home, which is the denominator of the header subtitle. */
  val cameraCount: Int get() = devices.size

  /** Cameras currently reachable, which is the numerator of the header subtitle. */
  val onlineCount: Int get() = devices.count { it.isOnline() }
}

/**
 * Drives the home screen off [SessionStore] rather than off screen entry: the store is the single
 * source of truth for the lists, and the cameras are refetched whenever the selected home changes.
 * That is what makes a home created elsewhere arrive with its cameras rather than looking empty.
 */
class HomeViewModel : ViewModel() {
  private val _uiState = MutableStateFlow(HomeUiState())
  private val deviceReloads = MutableStateFlow(0)
  private var hasResumed = false

  /** Single source of truth for [HomeScreen]. */
  val uiState = _uiState.asStateFlow()

  init {
    mirrorStore()
    observeSelectedSpace()
    load()
  }

  /**
   * Refetches the homes and the cameras of the selected one. Nothing here shows a spinner over a
   * list that already has content, so a refresh never blanks the screen.
   */
  fun load() {
    _uiState.update { it.copy(loadingSpaces = it.spaces.isEmpty(), error = null) }
    deviceReloads.update { it + 1 }
    viewModelScope.launch {
      sdkCall<List<Space>> { onSuccess, onError ->
        InstaVision.spaceServices.getSpaces(onSuccess = onSuccess, onError = onError)
      }
        .onSuccess { spaces ->
          SessionStore.putSpaces(spaces)
          _uiState.update { it.copy(loadingSpaces = false) }
        }
        .onFailure { error ->
          _uiState.update { it.copy(loadingSpaces = false, error = error.userMessage()) }
        }
    }
  }

  /**
   * Switches the active home. Only the store is written; the camera list reloads because
   * [observeSelectedSpace] is watching [SessionStore.spaceId].
   */
  fun selectSpace(space: Space) {
    if (space.id == SessionStore.spaceId) return
    SessionStore.selectSpace(space)
  }

  /** Records the camera the user tapped so the detail screen knows what to stream. */
  fun selectDevice(device: Device) {
    SessionStore.selectDevice(device)
  }

  /**
   * Refetches when the screen comes back to the foreground, skipping the first resume because
   * [init] has already loaded. Correctness does not depend on this: the store observers already
   * surface what another screen published, so this only keeps a backgrounded list fresh.
   */
  fun refreshOnResume() {
    if (!hasResumed) {
      hasResumed = true
      return
    }
    load()
  }

  /**
   * Mirrors the store's Compose state into the UI state, so a home or camera published by the
   * create-home or pairing flow appears here without this screen refetching anything. Only reads
   * the store, so nothing here feeds back into the fetches.
   */
  private fun mirrorStore() {
    viewModelScope.launch {
      snapshotFlow { SessionStore.spaces }
        .collect { spaces -> _uiState.update { it.copy(spaces = spaces) } }
    }
    viewModelScope.launch {
      snapshotFlow { SessionStore.selectedSpace }
        .collect { space -> _uiState.update { it.copy(selectedSpace = space) } }
    }
    viewModelScope.launch {
      snapshotFlow { SessionStore.devices }
        .collect { devices -> _uiState.update { it.copy(devices = devices) } }
    }
  }

  /**
   * Keys the camera fetch on [SessionStore.spaceId] so every route into a new home reloads — the
   * switcher, the create-home flow, or anything else that writes the store. [deviceReloads] is the
   * manual-refresh path, which has to work even when the home has not changed, and `collectLatest`
   * abandons a fetch whose home is already stale.
   */
  private fun observeSelectedSpace() {
    viewModelScope.launch {
      combine(
        snapshotFlow { SessionStore.spaceId }.distinctUntilChanged(),
        deviceReloads,
      ) { spaceId, reload -> spaceId to reload }
        .distinctUntilChanged()
        .collectLatest { (spaceId, _) -> loadDevices(spaceId) }
    }
  }

  /**
   * Fetches the cameras of [spaceId] into the store; an empty id means no home is selected. Every
   * camera the backend returns is printed whole to logcat under [DEVICE_LOG_TAG], so the fields the
   * tiles read can be checked against what actually arrived.
   */
  private suspend fun loadDevices(spaceId: String) {
    if (spaceId.isEmpty()) {
      _uiState.update { it.copy(loadingDevices = false) }
      return
    }
    _uiState.update { it.copy(loadingDevices = it.devices.isEmpty()) }
    sdkCall<List<Device>> { onSuccess, onError ->
      InstaVision.deviceServices.getDevices(
        spaceId = spaceId,
        onSuccess = onSuccess,
        onError = onError,
      )
    }
      .onSuccess { devices ->
        Log.d(DEVICE_LOG_TAG, "getDevices(spaceId=$spaceId) returned ${devices.size} device(s)")
        devices.forEachIndexed { index, device -> Log.d(DEVICE_LOG_TAG, "device[$index] = $device") }
        SessionStore.putDevices(devices)
        _uiState.update { it.copy(loadingDevices = false) }
        activateStragglers(devices = devices)
      }
      .onFailure { error ->
        Log.d(DEVICE_LOG_TAG, "getDevices(spaceId=$spaceId) failed: ${error.userMessage()}")
        _uiState.update { it.copy(loadingDevices = false, error = error.userMessage()) }
      }
  }

  /**
   * Finishes off any camera the pairing wizard left short of [PairingStatus.ACTIVATED]. Activation
   * is the last step of pairing and the backend will not treat a camera as a live member of the
   * space without it, so a wizard that was killed, lost the network or timed out on the SDK's
   * wake-up leaves a camera stranded with no other way back to it.
   *
   * A camera still on [PairingStatus.INITIALIZED] is mid-pairing rather than stranded and is left
   * alone. Failures are only logged: the list on screen is already correct, and nothing the user
   * asked for has failed.
   */
  private suspend fun activateStragglers(devices: List<Device>) {
    val stranded = devices.filter { device -> device.pairingStatus in STRANDED_STATUSES }
    if (stranded.isEmpty()) return
    Log.d(DEVICE_LOG_TAG, "${stranded.size} camera(s) never finished activating; activating now")
    val activated = stranded.mapNotNull { device -> activate(device = device) }
    if (activated.isEmpty()) return
    SessionStore.putDevices(
      SessionStore.devices.map { device ->
        activated.firstOrNull { updated -> updated.id == device.id } ?: device
      },
    )
  }

  /**
   * Moves one camera on to [PairingStatus.ACTIVATED], returning the updated camera or null when
   * the call fails. The call is bounded because the SDK wakes battery cameras first and neither of
   * its callbacks fires when that wake-up never lands.
   */
  private suspend fun activate(device: Device): Device? {
    Log.d(DEVICE_LOG_TAG, "activating device=${device.id} status=${device.pairingStatus}")
    val result = withTimeoutOrNull(ACTIVATE_TIMEOUT_MS) {
      sdkCall<Device> { onSuccess, onError ->
        InstaVision.deviceServices.updateDeviceInfo(
          device = device,
          request = UpdateDeviceRequest(pairingStatus = PairingStatus.ACTIVATED.type),
          onSuccess = onSuccess,
          onError = onError,
        )
      }
    }
    if (result == null) {
      Log.d(DEVICE_LOG_TAG, "activation for device=${device.id} timed out with no SDK callback")
      return null
    }
    return result
      .onSuccess { updated ->
        Log.d(DEVICE_LOG_TAG, "activated device=${updated.id} status=${updated.pairingStatus}")
      }
      .onFailure { error ->
        Log.d(DEVICE_LOG_TAG, "activation failed for device=${device.id}: ${error.userMessage()}")
      }
      .getOrNull()
  }
}

/**
 * Pairing statuses a camera can be left on when activation never ran. `Initialized` is deliberately
 * absent: a camera on it is still being paired by a wizard that has not finished.
 */
private val STRANDED_STATUSES = setOf(
  PairingStatus.PROCESSED.type,
  PairingStatus.PAIRED.type,
)

/** How long one activation is given before the home gives up waiting on the SDK. */
private const val ACTIVATE_TIMEOUT_MS = 20_000L

/** Logcat tag the home's camera list is printed under; shared with the security camera picker. */
private const val DEVICE_LOG_TAG = "GuardianDevices"
