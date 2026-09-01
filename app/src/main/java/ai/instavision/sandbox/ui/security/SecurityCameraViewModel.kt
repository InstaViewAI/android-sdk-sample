package ai.instavision.sandbox.ui.security

import ai.instavision.sandbox.data.SessionStore
import ai.instavision.sandbox.ui.common.SdkException
import ai.instavision.sandbox.ui.common.sdkCall
import ai.instavision.sandbox.ui.common.userMessage
import ai.instavision.guardian.sdk.InstaVision
import ai.instavision.guardian.sdk.data.entity.Device
import ai.instavision.guardian.sdk.data.entity.DeviceSetting
import ai.instavision.guardian.sdk.data.entity.SecurityDevice
import ai.instavision.guardian.sdk.data.entity.cluster.isActivityZoneEnabled
import ai.instavision.guardian.sdk.data.entity.isDoorbell
import ai.instavision.guardian.sdk.data.entity.request.SecurityDeviceRequest
import ai.instavision.guardian.sdk.data.entity.request.UpdateClusterAttributeRequest
import ai.instavision.guardian.sdk.data.entity.request.UpdateDeviceSettingRequest
import ai.instavision.guardian.sdk.data.entity.request.UpdateMotionZoneSettings
import ai.instavision.guardian.sdk.data.entity.request.UpdateZones
import ai.instavision.guardian.sdk.data.entity.response.ActivityZonesResponse
import ai.instavision.guardian.sdk.data.entity.response.ClusterResponse
import ai.instavision.guardian.sdk.data.entity.supportsCluster
import ai.instavision.guardian.sdk.data.enums.DeviceProductType
import ai.instavision.guardian.sdk.data.enums.DeviceStatus
import ai.instavision.guardian.sdk.data.enums.MotionSelectedZoneType
import ai.instavision.guardian.sdk.data.enums.MotionZoneType
import ai.instavision.guardian.sdk.data.enums.PairingStatus
import ai.instavision.guardian.sdk.data.enums.cluster.ClusterAttributeTypes
import ai.instavision.guardian.sdk.data.enums.cluster.DeviceClusterTypes
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Logcat tag the whole `getDevices` payload is printed under. Filter on it to read every field the
 * backend sends for a camera, which no screen in the sample renders in full.
 */
private const val DEVICE_LOG_TAG = "GuardianDevices"

/**
 * Blocks of the motion grid a camera with no zone of its own is given when zones are switched on,
 * copied from the production app's `DEFAULT_INCLUSION_BLOCK`. It is the centre of the frame.
 */
private val DefaultZoneBlocks = listOf(10, 11, 12, 13, 18, 19, 20, 21)

/** Error code `updateDevices` returns while the alarm is live, which blocks any camera change. */
private const val ERROR_PROFILE_ARMED = "SecurityProfile_Armed"

/** Error code `updateDevices` returns when not one camera of the selection is reachable. */
private const val ERROR_ALL_DEVICES_OFFLINE = "AllDevicesOffline"

/** What to do about [ERROR_PROFILE_ARMED], which the backend's own wording does not say. */
private const val ARMED_MESSAGE =
  "Disarm the system before changing which cameras monitoring covers."

/** The counterpart of [ARMED_MESSAGE] for [ERROR_ALL_DEVICES_OFFLINE]. */
private const val OFFLINE_MESSAGE =
  "At least one of the cameras you pick has to be online. Check their power and Wi-Fi, then retry."

/**
 * Mirrors the production app's `supportProSecurity`: monitoring can only arm a home-security
 * camera, and never a doorbell, whatever else the home has paired.
 */
internal fun Device.supportsProSecurity(): Boolean =
  modelProductType == DeviceProductType.HOME_SECURITY.value &&
    !isDoorbell() &&
    pairingStatus == PairingStatus.ACTIVATED.type

/**
 * Mirrors the production app's `hasSubscription`: a plan names the cameras it covers, and
 * `updateDevices` rejects a selection holding one the home has not paid for.
 */
internal fun Device.hasMonitoringSubscription(): Boolean =
  SessionStore.selectedSpace?.subscriptionsMetaData.orEmpty().any { it.devices.contains(id) }

/** Whether the camera can be ticked at all: compatible with monitoring and covered by the plan. */
internal fun Device.isSelectableForSecurity(): Boolean =
  supportsProSecurity() && hasMonitoringSubscription()

/**
 * Turns an `updateDevices` failure into something the user can act on, since the two states the
 * backend rejects a selection for are both fixable and neither says so in its own message.
 */
private fun Throwable.selectionMessage(): String =
  when ((this as? SdkException)?.error?.code) {
    ERROR_PROFILE_ARMED -> ARMED_MESSAGE
    ERROR_ALL_DEVICES_OFFLINE -> OFFLINE_MESSAGE
    else -> userMessage()
  }

/** Whether the camera is reachable right now, which is what a zone toggle needs to be sent. */
internal fun Device.isOnline(): Boolean = deviceState.status == DeviceStatus.ONLINE.value

/** Everything the camera setup step renders, in both of its phases. */
data class SecurityCameraUiState(
  /** Every camera of the home, incompatible ones included, in the order `getDevices` returned. */
  val devices: List<Device> = emptyList(),
  /** Cameras the user has ticked; the payload of the next `updateDevices` call. */
  val selectedIds: Set<String> = emptySet(),
  /** Cameras monitoring is already armed against, as last accepted by the backend. */
  val savedIds: Set<String> = emptySet(),
  /** Zone configuration of the whole home; null until a selection has been saved. */
  val zones: ActivityZonesResponse? = null,
  /** True until the cameras, the profile and the zones have all been read. */
  val loading: Boolean = true,
  /** True while the selection or the step completion is in flight. */
  val busy: Boolean = false,
  /** Camera whose zone toggle is mid-flight, so only that row is frozen. */
  val zoneBusyId: String? = null,
  /** Set once the step has been recorded, so the screen can hand back to the checklist. */
  val done: Boolean = false,
  /** Message from the last failed request; a missing profile never sets this. */
  val error: String? = null,
) {
  /** The cameras that can be ticked; the rest are listed only to say why they cannot. */
  val eligible: List<Device> get() = devices.filter { it.isSelectableForSecurity() }

  /** Whether the "select all" link should offer to clear the selection instead. */
  val allSelected: Boolean
    get() = eligible.isNotEmpty() && eligible.all { it.id in selectedIds }

  /** Whether what is ticked is exactly what the backend last accepted. */
  val selectionSaved: Boolean get() = savedIds.isNotEmpty() && savedIds == selectedIds

  /** The cameras the zone section covers, which is the saved selection rather than the ticked one. */
  val zoneDevices: List<Device> get() = devices.filter { it.id in savedIds }

  /** Whether [device] reports a zone configuration this build can switch on and off. */
  fun supportsZones(device: Device): Boolean =
    clusterZone(device) != null || nonClusterZone(device) != null

  /** Whether [device] currently has its activity zone switched on. */
  fun zoneEnabled(device: Device): Boolean = if (device.supportsCluster()) {
    clusterZone(device)?.isActivityZoneEnabled() == true
  } else {
    nonClusterZone(device)?.motionZoneEnabled == true
  }

  /** The zone blocks [device] already has, which decides whether switching on needs a default. */
  internal fun hasZoneBlocks(device: Device): Boolean =
    nonClusterZone(device)?.motionZones.orEmpty().isNotEmpty()

  /** The cluster-shaped zone record of [device], for the newer cameras. */
  private fun clusterZone(device: Device) =
    zones?.clusterZones?.firstOrNull { it.deviceId == device.id }

  /** The flat zone record of [device], for cameras that predate clusters. */
  private fun nonClusterZone(device: Device) =
    zones?.nonClusterZones?.firstOrNull { it.deviceId == device.id }
}

/**
 * Backs the camera setup step: which cameras take part in monitoring, and whether each of them
 * restricts detection to an activity zone. The selection is written first, because the backend
 * only reports zones for cameras monitoring already knows about.
 */
class SecurityCameraViewModel : ViewModel() {
  private val _uiState = MutableStateFlow(SecurityCameraUiState())

  /** Single source of truth for [SecurityCameraScreen]. */
  val uiState = _uiState.asStateFlow()

  init {
    load()
  }

  /** Reads the cameras, the saved selection and the zones, in that order of dependency. */
  fun load() {
    val spaceId = SessionStore.spaceId
    if (spaceId.isEmpty()) {
      _uiState.update { it.copy(loading = false) }
      return
    }
    _uiState.update { it.copy(loading = true, error = null) }
    viewModelScope.launch {
      loadDevices(spaceId)
      loadSelection(spaceId)
      if (_uiState.value.savedIds.isNotEmpty()) loadZones(spaceId)
      _uiState.update { it.copy(loading = false) }
    }
  }

  /** Ticks or unticks one camera, which also invalidates the saved selection until it is written. */
  fun toggleDevice(deviceId: String) {
    _uiState.update { state ->
      val selected = state.selectedIds
      state.copy(
        selectedIds = if (deviceId in selected) selected - deviceId else selected + deviceId,
      )
    }
  }

  /** Ticks every eligible camera, or clears the lot when they are already all ticked. */
  fun toggleAll() {
    _uiState.update { state ->
      state.copy(
        selectedIds = if (state.allSelected) emptySet() else state.eligible.map { it.id }.toSet(),
      )
    }
  }

  /** Writes the ticked cameras to the monitoring profile and reads their zones back. */
  fun saveSelection() {
    val spaceId = SessionStore.spaceId
    val selected = _uiState.value.selectedIds
    if (spaceId.isEmpty() || selected.isEmpty()) return
    _uiState.update { it.copy(busy = true, error = null) }
    viewModelScope.launch {
      sdkCall<List<SecurityDevice>> { onSuccess, onError ->
        InstaVision.securityServices.updateDevices(
          spaceId = spaceId,
          request = SecurityDeviceRequest(deviceIds = selected.toList()),
          onSuccess = onSuccess,
          onError = onError,
        )
      }
        .onSuccess { securityDevices ->
          _uiState.update { it.copy(savedIds = securityDevices.map { device -> device.id }.toSet()) }
          loadZones(spaceId)
          _uiState.update { it.copy(busy = false) }
        }
        .onFailure { error ->
          _uiState.update { it.copy(busy = false, error = error.selectionMessage()) }
        }
    }
  }

  /**
   * Switches [device]'s activity zone on or off. Cluster cameras carry the flag as an attribute
   * and everything older carries it in its device settings, which is the split production makes.
   */
  fun setZoneEnabled(device: Device, enabled: Boolean) {
    val spaceId = SessionStore.spaceId
    if (spaceId.isEmpty()) return
    _uiState.update { it.copy(zoneBusyId = device.id, error = null) }
    viewModelScope.launch {
      val result = if (device.supportsCluster()) {
        updateClusterZone(device = device, enabled = enabled)
      } else {
        updateSettingZone(device = device, enabled = enabled)
      }
      result
        .onSuccess {
          loadZones(spaceId)
          _uiState.update { it.copy(zoneBusyId = null) }
        }
        .onFailure { error ->
          _uiState.update { it.copy(zoneBusyId = null, error = error.userMessage()) }
        }
    }
  }

  /** Records the step as done once the cameras are saved, which hands back to the checklist. */
  fun finish() {
    val spaceId = SessionStore.spaceId
    if (spaceId.isEmpty()) return
    _uiState.update { it.copy(busy = true, error = null) }
    viewModelScope.launch {
      markSetupStep(spaceId = spaceId, apiName = SecuritySteps.CameraSetup.apiName)
        .onSuccess { _uiState.update { it.copy(busy = false, done = true) } }
        .onFailure { error ->
          _uiState.update { it.copy(busy = false, error = error.userMessage()) }
        }
    }
  }

  /**
   * Fetches the home's cameras and prints each one whole to logcat under [DEVICE_LOG_TAG]. The log
   * lives here rather than in the screen so it fires once per visit and never on a recomposition.
   */
  private suspend fun loadDevices(spaceId: String) {
    sdkCall<List<Device>> { onSuccess, onError ->
      InstaVision.deviceServices.getDevices(
        spaceId = spaceId,
        onSuccess = onSuccess,
        onError = onError,
      )
    }
      .onSuccess { devices ->
        Log.d(DEVICE_LOG_TAG, "getDevices(spaceId=$spaceId) returned ${devices.size} device(s)")
        devices.forEachIndexed { index, device ->
          Log.d(DEVICE_LOG_TAG, "device[$index] = $device")
        }
        SessionStore.putDevices(devices)
        _uiState.update { it.copy(devices = devices) }
      }
      .onFailure { error -> _uiState.update { it.copy(error = error.userMessage()) } }
  }

  /**
   * Pre-ticks the cameras monitoring already covers. A home that has never saved a selection gets
   * every eligible camera ticked, which is the default the production app's checkbox list opens on.
   */
  private suspend fun loadSelection(spaceId: String) {
    fetchSecurityProfile(spaceId)
      .onSuccess { profile ->
        val saved = profile?.deviceList.orEmpty().map { it.id }.toSet()
        _uiState.update { state ->
          state.copy(
            savedIds = saved,
            selectedIds = saved.ifEmpty { state.eligible.map { it.id }.toSet() },
          )
        }
      }
      .onFailure { error -> _uiState.update { it.copy(error = error.userMessage()) } }
  }

  /** Reads the zone configuration of the whole home; a failure only costs the zone section. */
  private suspend fun loadZones(spaceId: String) {
    sdkCall<ActivityZonesResponse> { onSuccess, onError ->
      InstaVision.deviceServices.getActivityZones(
        spaceId = spaceId,
        onSuccess = onSuccess,
        onError = onError,
      )
    }
      .onSuccess { zones -> _uiState.update { it.copy(zones = zones) } }
      .onFailure { error -> _uiState.update { it.copy(error = error.userMessage()) } }
  }

  /** Flips the activity-zone attribute of a cluster camera. */
  private suspend fun updateClusterZone(device: Device, enabled: Boolean): Result<*> =
    sdkCall<ClusterResponse> { onSuccess, onError ->
      InstaVision.deviceServices.updateClusterAttribute(
        device = device,
        clusterId = DeviceClusterTypes.BlockActivityZone.id,
        attributeId = ClusterAttributeTypes.BlockActivityZoneEnabled.id,
        request = UpdateClusterAttributeRequest(value = enabled),
        onSuccess = onSuccess,
        onError = onError,
      )
    }

  /**
   * Flips the motion-zone flag of a camera that predates clusters, seeding [DefaultZoneBlocks] when
   * it is being switched on for the first time — the backend rejects an enabled zone with no blocks.
   */
  private suspend fun updateSettingZone(device: Device, enabled: Boolean): Result<*> {
    val needsSeed = enabled && !_uiState.value.hasZoneBlocks(device)
    val zone = if (needsSeed) {
      UpdateMotionZoneSettings(
        type = MotionZoneType.Block.name,
        zones = listOf(
          UpdateZones(
            type = MotionSelectedZoneType.Inclusion.name,
            blocks = DefaultZoneBlocks,
          ),
        ),
      )
    } else {
      null
    }
    return sdkCall<DeviceSetting> { onSuccess, onError ->
      InstaVision.deviceServices.updateDeviceSetting(
        device = device,
        deviceSetting = UpdateDeviceSettingRequest(
          motionZonesEnabled = enabled,
          motionZone = zone,
        ),
        onSuccess = onSuccess,
        onError = onError,
      )
    }
  }
}
