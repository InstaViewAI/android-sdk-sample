package ai.instavision.sandbox.ui.settings

import ai.instavision.sandbox.data.SessionStore
import ai.instavision.sandbox.ui.common.sdkCall
import ai.instavision.sandbox.ui.common.userMessage
import ai.instavision.sandbox.ui.settings.device.ClusterControl
import ai.instavision.sandbox.ui.settings.device.ClusterSection
import ai.instavision.sandbox.ui.settings.device.ClusterSetting
import ai.instavision.sandbox.ui.settings.device.DEFAULT_ZONE_GRID_COLUMNS
import ai.instavision.sandbox.ui.settings.device.DEFAULT_ZONE_GRID_ROWS
import ai.instavision.sandbox.ui.settings.device.controls
import ai.instavision.sandbox.ui.settings.device.minutesToTime
import ai.instavision.sandbox.ui.settings.device.toLocalZone
import ai.instavision.sandbox.ui.settings.device.toUtcMinutes
import ai.instavision.sandbox.ui.settings.device.toUtcZone
import ai.instavision.guardian.sdk.InstaVision
import ai.instavision.guardian.sdk.data.entity.AiDetection
import ai.instavision.guardian.sdk.data.entity.AudioSettings
import ai.instavision.guardian.sdk.data.entity.CloudSetting
import ai.instavision.guardian.sdk.data.entity.Device
import ai.instavision.guardian.sdk.data.entity.DeviceModel
import ai.instavision.guardian.sdk.data.entity.DeviceSetting
import ai.instavision.guardian.sdk.data.entity.EventSchedulingSetting
import ai.instavision.guardian.sdk.data.entity.LullabySetting
import ai.instavision.guardian.sdk.data.entity.LullabyTrackInfo
import ai.instavision.guardian.sdk.data.entity.Notification
import ai.instavision.guardian.sdk.data.entity.Time
import ai.instavision.guardian.sdk.data.entity.cluster.DeviceCluster
import ai.instavision.guardian.sdk.data.entity.cluster.UpdateClusterAttribute
import ai.instavision.guardian.sdk.data.entity.cluster.activityZones
import ai.instavision.guardian.sdk.data.entity.cluster.eventScheduleStartTime
import ai.instavision.guardian.sdk.data.entity.cluster.eventSchedulingEnabled
import ai.instavision.guardian.sdk.data.entity.cluster.eventSchedulingEndTime
import ai.instavision.guardian.sdk.data.entity.cluster.getActivityZoneConfig
import ai.instavision.guardian.sdk.data.entity.cluster.isActivityZoneEnabled
import ai.instavision.guardian.sdk.data.entity.cluster.supportsActivityZone
import ai.instavision.guardian.sdk.data.entity.cluster.supportsEdgeAICry
import ai.instavision.guardian.sdk.data.entity.cluster.supportsEventScheduling
import ai.instavision.guardian.sdk.data.entity.cluster.supportsHumidityAlert
import ai.instavision.guardian.sdk.data.entity.cluster.supportsPTZ as clusterSupportsPTZ
import ai.instavision.guardian.sdk.data.entity.cluster.supportsTemperatureAlert
import ai.instavision.guardian.sdk.data.entity.cluster.supportsTimeZone
import ai.instavision.guardian.sdk.data.entity.cluster.timeZone
import ai.instavision.guardian.sdk.data.entity.formattedFirmwareVersion
import ai.instavision.guardian.sdk.data.entity.isOnline
import ai.instavision.guardian.sdk.data.entity.request.AiSettingsRequest
import ai.instavision.guardian.sdk.data.entity.request.PlayLullabyRequest
import ai.instavision.guardian.sdk.data.entity.request.UpdateAudioSettings
import ai.instavision.guardian.sdk.data.entity.request.UpdateClusterAttributeRequest
import ai.instavision.guardian.sdk.data.entity.request.UpdateClusterRequest
import ai.instavision.guardian.sdk.data.entity.request.UpdateDeviceLocationRequest
import ai.instavision.guardian.sdk.data.entity.request.UpdateDeviceRequest
import ai.instavision.guardian.sdk.data.entity.request.UpdateDeviceSettingRequest
import ai.instavision.guardian.sdk.data.entity.request.UpdateFirmwareRequest
import ai.instavision.guardian.sdk.data.entity.request.UpdateLullabySettingRequest
import ai.instavision.guardian.sdk.data.entity.request.UpdateMotionZoneSettings
import ai.instavision.guardian.sdk.data.entity.request.UpdateZones
import ai.instavision.guardian.sdk.data.entity.response.ActivityZonesResponse
import ai.instavision.guardian.sdk.data.entity.response.ClusterResponse
import ai.instavision.guardian.sdk.data.entity.response.FirmwareUpdateStatusResponse
import ai.instavision.guardian.sdk.data.entity.response.LatestFirmwareVersionResponse
import ai.instavision.guardian.sdk.data.entity.supportsCluster
import ai.instavision.guardian.sdk.data.entity.supportsPTZ
import ai.instavision.guardian.sdk.data.enums.MotionSelectedZoneType
import ai.instavision.guardian.sdk.data.enums.MotionZoneType
import ai.instavision.guardian.sdk.data.enums.RotationAngle
import ai.instavision.guardian.sdk.data.enums.cluster.ClusterAttributeTypes
import ai.instavision.guardian.sdk.data.enums.cluster.DeviceClusterTypes
import ai.instavision.guardian.sdk.domain.TZ
import android.content.res.AssetManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalTime
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shown for the activity-zone row when the camera reports no zones of its own. */
private const val NO_ACTIVITY_ZONES = "Watching the whole frame"

/** Shown when the zone editor is saved with nothing selected, which the camera would reject. */
private const val EMPTY_ZONE_ERROR = "Select at least one block for the camera to watch"

/** Shown when a zone save is attempted against a camera that is not on the network. */
private const val OFFLINE_ZONE_ERROR = "This camera is offline, so its zones cannot be changed"

/** Shown when a schedule edit is attempted against a camera that is not on the network. */
private const val OFFLINE_SCHEDULE_ERROR =
  "This camera is offline, so its schedule cannot be changed"

/** Megabytes in a gigabyte, used to turn the raw SD card figures into a readable line. */
private const val MB_PER_GB = 1024.0

/**
 * Everything the camera settings screen renders. Values start empty and are replaced by the four
 * fetches [DeviceSettingsViewModel] fires on creation.
 */
data class DeviceSettingsUiState(
  /** Camera being configured; null means nothing was selected before opening the screen. */
  val device: Device? = null,
  /** Editable camera name, seeded from the camera and saved on demand. */
  val name: String = "",
  /** Editable place name for the camera, saved alongside its last known coordinates. */
  val locationName: String = "",
  /**
   * Controls the camera advertises through its cluster, already resolved to the shape they render
   * as. Empty for a camera that reports no clusters, which is what sends every screen back to the
   * settings it drove before.
   */
  val clusterControls: List<ClusterControl> = emptyList(),
  /**
   * Whether the camera answered with a cluster at all. Every screen falls back to its fixed rows
   * only when this is false, so a camera that reports a cluster gets exactly what it advertises
   * and nothing else.
   */
  val hasCluster: Boolean = false,
  /** Whether the camera has motors, which is what puts the recentre action on the live view. */
  val ptzSupported: Boolean = false,
  /** Timezone the camera keeps its clock in, empty when it does not report one. */
  val timeZone: String = "",
  /** Whether the camera reports a timezone cluster, which is what puts the row on the hub. */
  val timeZoneSupported: Boolean = false,
  /** Whether the camera recognises crying itself, which is what offers the cry alert and mute. */
  val cryDetectionSupported: Boolean = false,
  /** Whether the camera raises temperature alerts, which is what offers their mute. */
  val temperatureAlertsSupported: Boolean = false,
  /** Whether the camera raises humidity alerts, which is what offers their mute. */
  val humidityAlertsSupported: Boolean = false,
  /** Whether the camera reports events when it sees movement. */
  val motionDetectionEnabled: Boolean = false,
  /** Cloud detection flags as last returned, edited one toggle at a time. */
  val cloudAi: AiDetection = AiDetection(),
  /** Edge detection flags, resent unchanged whenever the cloud ones are updated. */
  val edgeAi: AiDetection = AiDetection(),
  /** One-line summary of the activity zones configured for this camera. */
  val activityZoneSummary: String = NO_ACTIVITY_ZONES,
  /** Whether this camera has activity zones at all; the whole section is hidden when it does not. */
  val activityZoneSupported: Boolean = false,
  /** Whether the camera is currently narrowing its attention to those zones. */
  val activityZoneEnabled: Boolean = false,
  /** Cells the camera currently watches, as `row * zoneColumns + col` indices. */
  val activityZones: List<Int> = emptyList(),
  /** Columns the camera divides its frame into, from its own schema when it publishes one. */
  val zoneColumns: Int = DEFAULT_ZONE_GRID_COLUMNS,
  /** Rows the camera divides its frame into, from its own schema when it publishes one. */
  val zoneRows: Int = DEFAULT_ZONE_GRID_ROWS,
  /** Whether this camera has an event window at all; the whole section is hidden when it has not. */
  val eventScheduleSupported: Boolean = false,
  /** Whether the camera is currently confining its events to that window. */
  val eventScheduleEnabled: Boolean = false,
  /** When the window opens, in the phone's timezone; null until the camera reports a time. */
  val eventScheduleStart: LocalTime? = null,
  /** When the window closes, in the phone's timezone; null until the camera reports a time. */
  val eventScheduleEnd: LocalTime? = null,
  /** Notification preferences as last returned, edited one toggle at a time. */
  val notifications: Notification = Notification(),
  /** Whether the small LED on the camera body is lit. */
  val statusLightOn: Boolean = false,
  /** Whether the lens is currently covered, which stops all streaming and recording. */
  val privacyModeOn: Boolean = false,
  /** Whether the picture is flipped, for cameras mounted upside down. */
  val imageRotated: Boolean = false,
  /** Microphone, speaker and volume as last returned, edited one control at a time. */
  val audio: AudioSettings = AudioSettings(),
  /** Human-readable SD card state, for example "Normal". */
  val sdCardStatus: String = "",
  /** Free and total SD card space, or empty when the camera has no card in it. */
  val sdCardUsage: String = "",
  /** Firmware the camera is running right now. */
  val currentFirmware: String = "",
  /** Newest firmware the account is entitled to, empty when the lookup failed. */
  val latestFirmware: String = "",
  /** Progress of an update that was started from this screen, null before one is checked. */
  val firmwareStatus: String? = null,
  /** Whether the camera model can play lullabies, which gates the whole lullaby section. */
  val lullabySupported: Boolean = false,
  /** Tracks the camera model ships with. */
  val lullabyTracks: List<LullabyTrackInfo> = emptyList(),
  /** Playback modes the camera model accepts, for example "Loop". */
  val lullabyModes: List<String> = emptyList(),
  /** Sleep-timer durations in minutes the camera model accepts. */
  val lullabyTimers: List<Long> = emptyList(),
  /** Track queued for the next play request; the camera picks its own when this is null. */
  val selectedTrackId: String? = null,
  /** Lullaby settings and playback state as last returned by the camera. */
  val lullaby: LullabySetting? = null,
  /** True until the initial fetches finish. */
  val loading: Boolean = true,
  /** True while a write is in flight, which disables every control on the screen. */
  val busy: Boolean = false,
  /** Message from the last failed request. */
  val error: String? = null,
  /** Confirmation of the last successful write, cleared once the user acknowledges it. */
  val notice: String? = null,
  /** Set once the camera is removed so the screen can navigate away. */
  val deleted: Boolean = false,
) {
  /** The cluster-backed controls that belong on one screen, in the order the catalogue lists them. */
  fun controls(section: ClusterSection): List<ClusterControl> =
    clusterControls.filter { control -> control.setting.section == section }

  /**
   * Whether the camera reports movement at all, which is what the cloud alert categories hang off.
   * Movement is a cloud flag rather than a cluster attribute, exactly as the production app has it.
   */
  fun motionDetectionOn(): Boolean =
    cloudAi.motion ?: edgeAi.motion ?: motionDetectionEnabled

  /**
   * Whether the event window runs past midnight, which a start at or after the end says it does.
   * An overnight window is a perfectly ordinary schedule, so this only labels the end row; nothing
   * anywhere refuses, clamps or swaps the two times because of it.
   */
  fun eventScheduleEndsNextDay(): Boolean {
    val start = eventScheduleStart ?: return false
    val end = eventScheduleEnd ?: return false
    return start >= end
  }
}

/**
 * Loads and writes the settings of [SessionStore.selectedDevice]. Reads are best-effort — a
 * failing secondary fetch leaves its section empty rather than emptying the whole screen.
 */
@Suppress("TooManyFunctions")
class DeviceSettingsViewModel : ViewModel() {
  private val _uiState = MutableStateFlow(DeviceSettingsUiState())

  /** Single source of truth for [DeviceSettingsScreen]. */
  val uiState = _uiState.asStateFlow()

  init {
    load()
  }

  /** Fetches the camera, its cluster, its settings, its cloud configuration and its firmware. */
  fun load() {
    val selected = SessionStore.selectedDevice
    if (selected == null) {
      _uiState.update { it.copy(loading = false) }
      return
    }
    _uiState.update { it.copy(loading = true, error = null) }
    viewModelScope.launch {
      val device = fetchDevice(selected) ?: selected
      applyDevice(device)
      reloadCluster(device)
      val setting = fetchSettings(device)
      applySettings(setting)
      applyMotionZones(device, setting)
      applyEventSchedule(device, setting)
      applyCloudSettings(fetchCloudSettings(device))
      applyLatestFirmware(fetchLatestFirmware(device))
      applyActivityZones(device, fetchActivityZones(device))
      applyLullabySupport(device, fetchDeviceModel(device))
      _uiState.update { it.copy(loading = false) }
    }
  }

  /** Records what the user typed into the camera name field. */
  fun onNameChange(value: String) {
    _uiState.update { it.copy(name = value) }
  }

  /** Records what the user typed into the location field. */
  fun onLocationChange(value: String) {
    _uiState.update { it.copy(locationName = value) }
  }

  /**
   * Saves the camera name and its place name together, which is how the name screen offers them.
   * Whichever of the two the user left alone is skipped, so one edit still sends one request.
   */
  fun saveDetails() {
    val device = _uiState.value.device ?: return
    submit(notice = "Camera updated") { writeDetails(device) }
  }

  /** Turns event reporting for movement on or off. */
  fun setMotionDetection(enabled: Boolean) {
    val device = _uiState.value.device ?: return
    submit(
      notice = if (enabled) "Motion detection on" else "Motion detection off",
      onDone = { it.copy(motionDetectionEnabled = enabled) },
    ) {
      sdkCall<Unit> { onSuccess, onError ->
        InstaVision.deviceServices.updateMotionDetection(
          device = device,
          isEnabled = enabled,
          onSuccess = { onSuccess(Unit) },
          onError = onError,
        )
      }
    }
  }

  /** Toggles one cloud detection category, resending the other categories untouched. */
  fun setCloudDetection(category: DetectionCategory, enabled: Boolean) {
    val device = _uiState.value.device ?: return
    val cloudAi = category.applyTo(_uiState.value.cloudAi, enabled)
    submit(notice = "${category.label} alerts updated", onDone = { it.copy(cloudAi = cloudAi) }) {
      sdkCall<Unit> { onSuccess, onError ->
        InstaVision.deviceServices.updateAiSettings(
          device = device,
          request = AiSettingsRequest(cloudAi = cloudAi, edgeAi = _uiState.value.edgeAi),
          onSuccess = { onSuccess(Unit) },
          onError = onError,
        )
      }
    }
  }

  /**
   * Turns the camera's attention to its zones on or off. The reference app puts this switch at the
   * head of the activity-zone card and reveals the editor beneath it only while it is on, which is
   * why the switch lives here rather than among the generated cluster rows.
   */
  fun setActivityZoneEnabled(enabled: Boolean) {
    val device = _uiState.value.device ?: return
    submit(
      notice = if (enabled) "Activity zones on" else "Activity zones off",
      onDone = { it.copy(activityZoneEnabled = enabled) },
    ) {
      writeActivityZoneEnabled(device = device, enabled = enabled)
    }
  }

  /**
   * Saves the cells the camera should watch, as `row * zoneColumns + col` indices. An offline
   * camera and an empty selection are both refused before anything is sent: the camera cannot
   * take the write, and clearing every cell would leave it watching nothing at all.
   */
  fun saveActivityZones(zones: List<Int>) {
    val device = _uiState.value.device ?: return
    if (refuseWhenOffline(device = device, message = OFFLINE_ZONE_ERROR)) return
    if (zones.isEmpty()) {
      _uiState.update { it.copy(error = EMPTY_ZONE_ERROR, notice = null) }
      return
    }
    submit(
      notice = "Activity zones updated",
      onDone = { it.copy(activityZones = zones, activityZoneSummary = zoneSummary(zones.size)) },
    ) {
      writeActivityZones(device = device, zones = zones)
    }
  }

  /**
   * Confines the camera's events to its window, or lets it report around the clock again. The
   * window's two ends are left as they are, so a camera switched back on keeps the hours it had.
   * A camera with a cluster is re-read afterwards for the same reason [updateEventSchedule] does.
   */
  fun setEventScheduleEnabled(enabled: Boolean) {
    val device = _uiState.value.device ?: return
    if (refuseWhenOffline(device = device, message = OFFLINE_SCHEDULE_ERROR)) return
    submit(
      notice = if (enabled) "Event schedule on" else "Event schedule off",
      onDone = { state ->
        if (device.supportsCluster()) state else state.copy(eventScheduleEnabled = enabled)
      },
    ) {
      if (device.supportsCluster()) {
        writeScheduleAttribute(
          device = device,
          attribute = ClusterAttributeTypes.EventSchedulingEnabled,
          value = enabled,
        ).also { reloadCluster(device) }
      } else {
        writeDeviceSetting(
          device = device,
          request = UpdateDeviceSettingRequest(eventSchedulingEnabled = enabled),
        )
      }
    }
  }

  /** Moves the hour the camera's event window opens; the picker hands over a local time. */
  fun setEventScheduleStart(time: LocalTime) {
    updateEventSchedule(
      notice = "Schedule start updated",
      attribute = ClusterAttributeTypes.EventSchedulingStartTime,
      edited = time,
      start = time,
      end = _uiState.value.eventScheduleEnd,
      onDone = { it.copy(eventScheduleStart = time) },
    )
  }

  /** Moves the hour the camera's event window closes; the picker hands over a local time. */
  fun setEventScheduleEnd(time: LocalTime) {
    updateEventSchedule(
      notice = "Schedule end updated",
      attribute = ClusterAttributeTypes.EventSchedulingEndTime,
      edited = time,
      start = _uiState.value.eventScheduleStart,
      end = time,
      onDone = { it.copy(eventScheduleEnd = time) },
    )
  }

  /** Mutes or unmutes every push notification this camera would raise. */
  fun setNotificationsMuted(muted: Boolean) {
    updateNotifications(notice = if (muted) "Notifications muted" else "Notifications on") {
      it.copy(mute = muted)
    }
  }

  /** Mutes or unmutes the alert raised when the camera drops off the network. */
  fun setOfflineAlertsMuted(muted: Boolean) {
    updateNotifications(notice = "Offline alerts updated") {
      it.copy(cameraOfflineNotification = muted)
    }
  }

  /** Mutes or unmutes the alert a nursery camera raises when it hears crying. */
  fun setCryAlertsMuted(muted: Boolean) {
    updateNotifications(notice = "Cry alerts updated") { it.copy(cryMute = muted) }
  }

  /** Mutes or unmutes the alert raised when the camera's own thermometer leaves its safe range. */
  fun setTemperatureAlertsMuted(muted: Boolean) {
    updateNotifications(notice = "Temperature alerts updated") { it.copy(temperatureMute = muted) }
  }

  /** Mutes or unmutes the alert raised when the camera's own hygrometer leaves its safe range. */
  fun setHumidityAlertsMuted(muted: Boolean) {
    updateNotifications(notice = "Humidity alerts updated") { it.copy(humidityMute = muted) }
  }

  /** Lights or dims the LED on the camera body. */
  fun setStatusLight(on: Boolean) {
    val device = _uiState.value.device ?: return
    submit(
      notice = if (on) "Status light on" else "Status light off",
      onDone = { it.copy(statusLightOn = on) },
    ) {
      sdkCall<DeviceSetting> { onSuccess, onError ->
        InstaVision.deviceServices.updateDeviceLightMode(
          device = device,
          isOn = on,
          onSuccess = onSuccess,
          onError = onError,
        )
      }
    }
  }

  /** Covers or uncovers the lens; while covered the camera neither streams nor records. */
  fun setPrivacyMode(on: Boolean) {
    updateSetting(
      notice = if (on) "Privacy mode on" else "Privacy mode off",
      request = UpdateDeviceSettingRequest(isInPrivacyMode = on),
      onDone = { it.copy(privacyModeOn = on) },
    )
  }

  /** Flips the picture for a camera that is mounted upside down. */
  fun setImageRotated(rotated: Boolean) {
    val angle = if (rotated) RotationAngle.Angle180.value else RotationAngle.Angle0.value
    updateSetting(
      notice = "Image rotation updated",
      request = UpdateDeviceSettingRequest(rotationAngle = angle),
      onDone = { it.copy(imageRotated = rotated) },
    )
  }

  /**
   * Writes one cluster attribute and re-reads the cluster, so the screen ends up showing what the
   * camera settled on rather than what it was asked for. [value] already carries the wire type the
   * attribute expects: a boolean, the enum string of one of its advertised options, or a number.
   */
  fun setClusterValue(setting: ClusterSetting, value: Any) {
    val device = _uiState.value.device ?: return
    submit(notice = "${setting.title} updated") {
      sdkCall<ClusterResponse> { onSuccess, onError ->
        InstaVision.deviceServices.updateClusterAttribute(
          device = device,
          clusterId = setting.cluster.id,
          attributeId = setting.attribute.id,
          request = UpdateClusterAttributeRequest(value = value),
          onSuccess = onSuccess,
          onError = onError,
        )
      }.onSuccess { reloadCluster(device) }
    }
  }

  /**
   * Puts the camera's clock in the phone's timezone. The identifier and the POSIX offset string
   * the camera runs on have to move together, which is why this is the one write that goes to the
   * whole cluster instead of a single attribute.
   */
  fun matchPhoneTimeZone(assets: AssetManager) {
    val device = _uiState.value.device ?: return
    val zoneId = TimeZone.getDefault().id
    submit(notice = "Timezone updated") {
      val offset = withContext(Dispatchers.IO) {
        TZ.getTzFormat(assetManager = assets, timeZone = zoneId)
      }
      sdkCall<ClusterResponse> { onSuccess, onError ->
        InstaVision.deviceServices.updateCluster(
          device = device,
          clusterId = DeviceClusterTypes.TimeZone.id,
          request = UpdateClusterRequest(
            attributes = listOf(
              UpdateClusterAttribute(
                id = ClusterAttributeTypes.TimeZoneIdentifier.id,
                value = zoneId,
              ),
              UpdateClusterAttribute(
                id = ClusterAttributeTypes.TimeZoneOffset.id,
                value = offset,
              ),
            ),
          ),
          onSuccess = onSuccess,
          onError = onError,
        )
      }.onSuccess { reloadCluster(device) }
    }
  }

  /** Opens or closes the microphone, which is what two-way audio and clip sound depend on. */
  fun setMicrophone(enabled: Boolean) {
    val audio = _uiState.value.audio.copy(microphoneEnabled = enabled)
    updateSetting(
      notice = if (enabled) "Microphone on" else "Microphone off",
      request = UpdateDeviceSettingRequest(
        audioSettings = UpdateAudioSettings(microphoneEnabled = enabled),
      ),
      onDone = { it.copy(audio = audio) },
    )
  }

  /** Enables or mutes the speaker the camera talks and plays lullabies through. */
  fun setSpeaker(enabled: Boolean) {
    val audio = _uiState.value.audio.copy(speakerEnabled = enabled)
    updateSetting(
      notice = if (enabled) "Speaker on" else "Speaker off",
      request = UpdateDeviceSettingRequest(
        audioSettings = UpdateAudioSettings(speakerEnabled = enabled),
      ),
      onDone = { it.copy(audio = audio) },
    )
  }

  /**
   * Drives a motorised camera back to the position it was set up in. The SDK wakes a sleeping
   * camera before it sends the command, so a battery camera that has dozed off needs nothing extra
   * here; it simply takes longer to answer.
   */
  fun resetCameraPosition() {
    val device = _uiState.value.device ?: return
    submit(notice = "Resetting camera position") {
      sdkCall<Unit> { onSuccess, onError ->
        InstaVision.deviceServices.resetPTZ(
          device = device,
          onSuccess = { onSuccess(Unit) },
          onError = onError,
        )
      }
    }
  }

  /** Erases the SD card; the caller is expected to have confirmed this with the user first. */
  fun formatSdCard() {
    val device = _uiState.value.device ?: return
    submit(notice = "Formatting started") {
      sdkCall<Unit> { onSuccess, onError ->
        InstaVision.deviceServices.formatSDCard(
          device = device,
          onSuccess = { onSuccess(Unit) },
          onError = onError,
        )
      }
    }
  }

  /** Asks the camera to install [DeviceSettingsUiState.latestFirmware]. */
  fun updateFirmware() {
    val device = _uiState.value.device ?: return
    val version = _uiState.value.latestFirmware
    if (version.isEmpty()) return
    submit(notice = "Update started", onDone = { it.copy(firmwareStatus = "Starting") }) {
      sdkCall<Unit> { onSuccess, onError ->
        InstaVision.deviceServices.updateFirmware(
          device = device,
          request = UpdateFirmwareRequest(version = version),
          onSuccess = { onSuccess(Unit) },
          onError = onError,
        )
      }
    }
  }

  /** Re-reads how far a running firmware update has got. */
  fun refreshFirmwareStatus() {
    val device = _uiState.value.device ?: return
    viewModelScope.launch {
      sdkCall<FirmwareUpdateStatusResponse> { onSuccess, onError ->
        InstaVision.deviceServices.getFirmwareUpdateStatus(
          device = device,
          onSuccess = onSuccess,
          onError = onError,
        )
      }
        .onSuccess { response -> _uiState.update { it.copy(firmwareStatus = response.status) } }
        .onFailure { error -> _uiState.update { it.copy(error = error.userMessage()) } }
    }
  }

  /** Queues [trackId] for the next play request without starting playback. */
  fun selectLullabyTrack(trackId: String) {
    _uiState.update { it.copy(selectedTrackId = trackId) }
  }

  /** Starts the queued lullaby, letting the camera choose a track when none is queued. */
  fun playLullaby() {
    val device = _uiState.value.device ?: return
    val trackId = _uiState.value.selectedTrackId
    submit(notice = "Playing lullaby") {
      sdkCall<Unit> { onSuccess, onError ->
        InstaVision.deviceServices.playLullaby(
          device = device,
          request = trackId?.let { PlayLullabyRequest(trackId = it) },
          onSuccess = { onSuccess(Unit) },
          onError = onError,
        )
      }.onSuccess { reloadLullaby(device) }
    }
  }

  /** Pauses playback, leaving the track where it is. */
  fun pauseLullaby() {
    val device = _uiState.value.device ?: return
    submit(notice = "Lullaby paused") {
      sdkCall<Unit> { onSuccess, onError ->
        InstaVision.deviceServices.pauseLullaby(
          device = device,
          onSuccess = { onSuccess(Unit) },
          onError = onError,
        )
      }.onSuccess { reloadLullaby(device) }
    }
  }

  /** Resumes a paused lullaby. */
  fun resumeLullaby() {
    val device = _uiState.value.device ?: return
    submit(notice = "Lullaby resumed") {
      sdkCall<Unit> { onSuccess, onError ->
        InstaVision.deviceServices.resumeLullaby(
          device = device,
          onSuccess = { onSuccess(Unit) },
          onError = onError,
        )
      }.onSuccess { reloadLullaby(device) }
    }
  }

  /** Stops playback and rewinds to the start of the queue. */
  fun stopLullaby() {
    val device = _uiState.value.device ?: return
    submit(notice = "Lullaby stopped") {
      sdkCall<Unit> { onSuccess, onError ->
        InstaVision.deviceServices.stopLullaby(
          device = device,
          onSuccess = { onSuccess(Unit) },
          onError = onError,
        )
      }.onSuccess { reloadLullaby(device) }
    }
  }

  /** Changes how the queue repeats, for example playing every track once or looping one. */
  fun setLullabyMode(mode: String) {
    updateLullabySettings(
      notice = "Playback mode updated",
      request = UpdateLullabySettingRequest(playbackMode = mode),
    )
  }

  /** Changes how long the camera keeps playing before it fades out. */
  fun setLullabyTimer(minutes: Long) {
    updateLullabySettings(
      notice = "Sleep timer updated",
      request = UpdateLullabySettingRequest(timerDurationInMins = minutes),
    )
  }

  /**
   * Unpairs the camera and drops it from [SessionStore]; the caller is expected to have confirmed
   * this with the user first.
   */
  fun deleteDevice() {
    val device = _uiState.value.device ?: return
    submit(notice = "Camera removed") {
      sdkCall<Unit> { onSuccess, onError ->
        InstaVision.deviceServices.deleteDevice(
          device = device,
          onSuccess = { onSuccess(Unit) },
          onError = onError,
        )
      }.onSuccess {
        SessionStore.putDevices(SessionStore.devices.filterNot { it.id == device.id })
        _uiState.update { it.copy(deleted = true) }
      }
    }
  }

  /** Clears the banner left behind by the last write. */
  fun dismissNotice() {
    _uiState.update { it.copy(notice = null, error = null) }
  }

  /** Re-reads the camera, returning null when the fetch fails so the cached copy is kept. */
  private suspend fun fetchDevice(device: Device): Device? =
    sdkCall<Device> { onSuccess, onError ->
      InstaVision.deviceServices.getDevice(
        spaceId = device.spaceId,
        deviceId = device.id,
        onSuccess = onSuccess,
        onError = onError,
      )
    }.getOrNull()

  /**
   * Reads what the camera advertises it can do. A camera still on the pre-cluster firmware reports
   * nothing here; one whose fetch fails falls back to the copy carried on the camera record, so a
   * dropped request never empties the screen.
   */
  private suspend fun fetchCluster(device: Device): DeviceCluster? {
    if (!device.supportsCluster()) return null
    val fetched = sdkCall<DeviceCluster> { onSuccess, onError ->
      InstaVision.deviceServices.getDeviceCluster(
        device = device,
        onSuccess = onSuccess,
        onError = onError,
      )
    }.getOrNull()
    return fetched
      ?: device.clusters?.let { DeviceCluster(deviceId = device.id, clusters = it) }
  }

  /**
   * Rebuilds every cluster-backed control; a camera without a cluster keeps the rows it had. The
   * flags alongside the controls are the attributes the production app reads only to decide which
   * row to show, never to render one of its own.
   */
  private suspend fun reloadCluster(device: Device) {
    val cluster = fetchCluster(device) ?: return
    val grid = cluster.getActivityZoneConfig()
    val zones = if (cluster.isActivityZoneEnabled()) cluster.activityZones() else emptyList()
    _uiState.update {
      it.copy(
        clusterControls = cluster.controls(),
        hasCluster = true,
        ptzSupported = it.ptzSupported || cluster.clusterSupportsPTZ(),
        timeZoneSupported = cluster.supportsTimeZone(),
        timeZone = if (cluster.supportsTimeZone()) cluster.timeZone() else "",
        cryDetectionSupported = cluster.supportsEdgeAICry(),
        temperatureAlertsSupported = cluster.supportsTemperatureAlert(),
        humidityAlertsSupported = cluster.supportsHumidityAlert(),
        activityZones = zones,
        activityZoneSupported = cluster.supportsActivityZone(),
        activityZoneEnabled = cluster.isActivityZoneEnabled(),
        zoneColumns = grid?.columns ?: DEFAULT_ZONE_GRID_COLUMNS,
        zoneRows = grid?.rows ?: DEFAULT_ZONE_GRID_ROWS,
        eventScheduleSupported = cluster.supportsEventScheduling(),
        eventScheduleEnabled = cluster.eventSchedulingEnabled(),
        eventScheduleStart = cluster.eventScheduleStartTime().minutesToTime().toLocalZone(),
        eventScheduleEnd = cluster.eventSchedulingEndTime().minutesToTime().toLocalZone(),
      )
    }
  }

  /** Reads the camera's own settings; the SDK only batches, so a single entry comes back. */
  private suspend fun fetchSettings(device: Device): DeviceSetting? =
    sdkCall<List<DeviceSetting>> { onSuccess, onError ->
      InstaVision.deviceServices.getDeviceSettings(
        devices = listOf(device),
        onSuccess = onSuccess,
        onError = onError,
      )
    }.getOrNull()?.firstOrNull()

  /** Reads the detection and notification settings that live in the cloud, not on the camera. */
  private suspend fun fetchCloudSettings(device: Device): CloudSetting? =
    sdkCall<CloudSetting> { onSuccess, onError ->
      InstaVision.deviceServices.getCloudSettings(
        device = device,
        onSuccess = onSuccess,
        onError = onError,
      )
    }.getOrNull()

  /** Reads the newest firmware the camera is entitled to. */
  private suspend fun fetchLatestFirmware(device: Device): LatestFirmwareVersionResponse? =
    sdkCall<LatestFirmwareVersionResponse> { onSuccess, onError ->
      InstaVision.deviceServices.getLatestFirmwareVersion(
        device = device,
        onSuccess = onSuccess,
        onError = onError,
      )
    }.getOrNull()

  /** Reads the activity zones of every camera in the space; the caller narrows them down. */
  private suspend fun fetchActivityZones(device: Device): ActivityZonesResponse? =
    sdkCall<ActivityZonesResponse> { onSuccess, onError ->
      InstaVision.deviceServices.getActivityZones(
        spaceId = device.spaceId,
        onSuccess = onSuccess,
        onError = onError,
      )
    }.getOrNull()

  /** Reads the camera model, which is the only place lullaby capability is advertised. */
  private suspend fun fetchDeviceModel(device: Device): DeviceModel? =
    sdkCall<DeviceModel> { onSuccess, onError ->
      InstaVision.deviceServices.getDeviceModel(
        spaceId = device.spaceId,
        deviceId = device.id,
        onSuccess = onSuccess,
        onError = onError,
      )
    }.getOrNull()

  /**
   * Writes the on/off state to whichever store this camera keeps its zones in, mirroring the split
   * [writeActivityZones] makes. The cells themselves are left alone: a camera switched back on
   * watches the zones it was given before.
   */
  private suspend fun writeActivityZoneEnabled(device: Device, enabled: Boolean): Result<*> =
    if (device.supportsCluster()) {
      sdkCall<ClusterResponse> { onSuccess, onError ->
        InstaVision.deviceServices.updateClusterAttribute(
          device = device,
          clusterId = DeviceClusterTypes.BlockActivityZone.id,
          attributeId = ClusterAttributeTypes.BlockActivityZoneEnabled.id,
          request = UpdateClusterAttributeRequest(enabled),
          onSuccess = onSuccess,
          onError = onError,
        )
      }
    } else {
      sdkCall<DeviceSetting> { onSuccess, onError ->
        InstaVision.deviceServices.updateDeviceSetting(
          device = device,
          deviceSetting = UpdateDeviceSettingRequest(motionZonesEnabled = enabled),
          onSuccess = onSuccess,
          onError = onError,
        )
      }
    }

  /**
   * Writes [zones] to whichever store this camera keeps them in: a cluster attribute for camera
   * firmware that advertises one, and the block-typed motion zone of the device settings for the
   * rest. Both take the same cell indices, so only the envelope differs.
   */
  private suspend fun writeActivityZones(device: Device, zones: List<Int>): Result<*> =
    if (device.supportsCluster()) {
      sdkCall<ClusterResponse> { onSuccess, onError ->
        InstaVision.deviceServices.updateClusterAttribute(
          device = device,
          clusterId = DeviceClusterTypes.BlockActivityZone.id,
          attributeId = ClusterAttributeTypes.BlockActivityZoneZones.id,
          request = UpdateClusterAttributeRequest(zones),
          onSuccess = onSuccess,
          onError = onError,
        )
      }
    } else {
      sdkCall<DeviceSetting> { onSuccess, onError ->
        InstaVision.deviceServices.updateDeviceSetting(
          device = device,
          deviceSetting = UpdateDeviceSettingRequest(
            motionZone = UpdateMotionZoneSettings(
              zones = listOf(
                UpdateZones(blocks = zones, type = MotionSelectedZoneType.Inclusion.name),
              ),
              type = MotionZoneType.Block.name,
            ),
          ),
          onSuccess = onSuccess,
          onError = onError,
        )
      }
    }

  /**
   * Writes one end of the event window to whichever store this camera keeps it in. A camera with a
   * cluster takes [edited] on its own, as minutes past midnight UTC, and is re-read whether or not
   * it accepted the write — which is also why [onDone] is skipped for one: the refetched hour is
   * what the camera settled on, and it may have clamped it. A camera without a cluster takes
   * [start] and [end] together as UTC wall times, and keeps the ends it had when the write fails,
   * which is what leaving the state alone on failure amounts to.
   */
  @Suppress("LongParameterList")
  private fun updateEventSchedule(
    notice: String,
    attribute: ClusterAttributeTypes,
    edited: LocalTime,
    start: LocalTime?,
    end: LocalTime?,
    onDone: (DeviceSettingsUiState) -> DeviceSettingsUiState,
  ) {
    val device = _uiState.value.device ?: return
    if (refuseWhenOffline(device = device, message = OFFLINE_SCHEDULE_ERROR)) return
    submit(
      notice = notice,
      onDone = { state -> if (device.supportsCluster()) state else onDone(state) },
    ) {
      if (device.supportsCluster()) {
        writeScheduleAttribute(
          device = device,
          attribute = attribute,
          value = edited.toUtcMinutes(),
        ).also { reloadCluster(device) }
      } else {
        writeDeviceSetting(
          device = device,
          request = UpdateDeviceSettingRequest(
            eventSchedulingSetting = EventSchedulingSetting(
              startTime = start.toUtcTime(),
              endTime = end.toUtcTime(),
            ),
          ),
        )
      }
    }
  }

  /** Writes one attribute of the event-scheduling cluster. */
  private suspend fun writeScheduleAttribute(
    device: Device,
    attribute: ClusterAttributeTypes,
    value: Any,
  ): Result<ClusterResponse> =
    sdkCall<ClusterResponse> { onSuccess, onError ->
      InstaVision.deviceServices.updateClusterAttribute(
        device = device,
        clusterId = DeviceClusterTypes.EventScheduling.id,
        attributeId = attribute.id,
        request = UpdateClusterAttributeRequest(value = value),
        onSuccess = onSuccess,
        onError = onError,
      )
    }

  /** Sends a partial camera-side settings patch and hands the result back to the caller. */
  private suspend fun writeDeviceSetting(
    device: Device,
    request: UpdateDeviceSettingRequest,
  ): Result<DeviceSetting> =
    sdkCall<DeviceSetting> { onSuccess, onError ->
      InstaVision.deviceServices.updateDeviceSetting(
        device = device,
        deviceSetting = request,
        onSuccess = onSuccess,
        onError = onError,
      )
    }

  /**
   * Refuses a write to a camera that is not on the network, leaving the reason on screen. The
   * reference app opens every one of these writes this way: an offline camera cannot take them.
   */
  private fun refuseWhenOffline(device: Device, message: String): Boolean {
    if (device.isOnline()) return false
    _uiState.update { it.copy(error = message, notice = null) }
    return true
  }

  /**
   * Sends the name then the place name, stopping at the first one the server rejects so the
   * failure reaches [submit] rather than being swallowed by the second write.
   */
  private suspend fun writeDetails(device: Device): Result<*> {
    val name = _uiState.value.name.trim()
    if (name.isNotEmpty() && name != device.name) {
      val renamed = renameDevice(device = device, name = name)
      renamed.onSuccess { updated -> publish(updated) }
      if (renamed.isFailure) return renamed
    }
    val location = _uiState.value.locationName.trim()
    if (location.isEmpty()) return Result.success(Unit)
    return relocateDevice(device = device, name = location)
  }

  /** Renames the camera, returning the record the server echoes back. */
  private suspend fun renameDevice(device: Device, name: String): Result<Device> =
    sdkCall<Device> { onSuccess, onError ->
      InstaVision.deviceServices.updateDeviceInfo(
        device = device,
        request = UpdateDeviceRequest(name = name),
        onSuccess = onSuccess,
        onError = onError,
      )
    }

  /**
   * Renames the spot the camera watches. The coordinates are carried over from the camera's last
   * known fix because the screen only lets the user name the spot, not move it.
   */
  private suspend fun relocateDevice(device: Device, name: String): Result<Unit> =
    sdkCall<Unit> { onSuccess, onError ->
      InstaVision.deviceServices.updateDeviceLocation(
        spaceId = device.spaceId,
        deviceId = device.id,
        request = UpdateDeviceLocationRequest(
          latitude = device.gpsLocation?.latitude?.toString().orEmpty(),
          longitude = device.gpsLocation?.longitude?.toString().orEmpty(),
          name = name,
        ),
        onSuccess = onSuccess,
        onError = onError,
      )
    }

  /** Seeds the identity fields and the read-only hardware lines from the fetched camera. */
  private fun applyDevice(device: Device) {
    SessionStore.selectDevice(device)
    val sdCard = device.deviceState.sdCard
    _uiState.update {
      it.copy(
        device = device,
        name = device.name,
        ptzSupported = device.supportsPTZ() == true,
        locationName = device.gpsLocation?.name ?: device.location.orEmpty(),
        privacyModeOn = device.deviceState.isInPrivacyMode,
        statusLightOn = device.deviceState.isLightOn,
        currentFirmware = device.formattedFirmwareVersion(),
        sdCardStatus = sdCard.status,
        sdCardUsage = formatStorage(sdCard.memoryAvailable, sdCard.totalMemory),
      )
    }
  }

  /**
   * Seeds the zone editor for a camera on the pre-cluster firmware, whose zones live in its motion
   * settings instead. A camera with a cluster is skipped, since [reloadCluster] already read its
   * zones and its grid; a coordinate-based motion zone has no blocks to draw and is skipped too.
   */
  private fun applyMotionZones(device: Device, setting: DeviceSetting?) {
    if (device.supportsCluster() || setting == null) return
    _uiState.update {
      it.copy(activityZoneSupported = true, activityZoneEnabled = setting.motionZonesEnabled)
    }
    if (!setting.motionZonesEnabled || setting.motionZone.type != MotionZoneType.Block.name) return
    val zones = setting.motionZone.zones.flatMap { zone -> zone.blocks.orEmpty() }
    _uiState.update { it.copy(activityZones = zones.distinct().sorted()) }
  }

  /**
   * Seeds the event window for a camera on the pre-cluster firmware, which keeps both ends in its
   * device settings as UTC wall times. A camera with a cluster is skipped, since [reloadCluster]
   * already read the window it advertises.
   */
  private fun applyEventSchedule(device: Device, setting: DeviceSetting?) {
    if (device.supportsCluster() || setting == null) return
    val schedule: EventSchedulingSetting? = setting.eventSchedulingSetting
    _uiState.update {
      it.copy(
        eventScheduleSupported = true,
        eventScheduleEnabled = setting.eventSchedulingEnabled,
        eventScheduleStart = schedule?.startTime?.toLocalTime(),
        eventScheduleEnd = schedule?.endTime?.toLocalTime(),
      )
    }
  }

  /** Overlays the camera-side settings, which are more current than the cached camera record. */
  private fun applySettings(setting: DeviceSetting?) {
    if (setting == null) return
    _uiState.update { state ->
      state.copy(
        privacyModeOn = setting.isInPrivacyMode,
        statusLightOn = setting.statusLightEnabled,
        imageRotated = setting.rotationAngle == RotationAngle.Angle180.value,
        motionDetectionEnabled = setting.eventDetectionSetting.motion ?: false,
        audio = setting.audioSettings,
        currentFirmware = setting.fwVersion.ifEmpty { state.currentFirmware },
      )
    }
  }

  /** Seeds the detection and notification toggles from the cloud configuration. */
  private fun applyCloudSettings(cloudSetting: CloudSetting?) {
    if (cloudSetting == null) return
    _uiState.update {
      it.copy(
        cloudAi = cloudSetting.cloudAiDetections,
        edgeAi = cloudSetting.edgeAiDetections,
        notifications = cloudSetting.notifications,
      )
    }
  }

  /** Records the newest available firmware so the section can offer the update. */
  private fun applyLatestFirmware(response: LatestFirmwareVersionResponse?) {
    if (response == null) return
    _uiState.update { it.copy(latestFirmware = response.version) }
  }

  /** Reduces this camera's zones, cluster-based or not, to the one line the row shows. */
  private fun applyActivityZones(device: Device, response: ActivityZonesResponse?) {
    if (response == null) return
    val cluster = response.clusterZones.firstOrNull { it.deviceId == device.id }
    val nonCluster = response.nonClusterZones.firstOrNull { it.deviceId == device.id }
    val summary = when {
      cluster != null && cluster.isActivityZoneEnabled() ->
        zoneSummary(cluster.activityZones().size)

      nonCluster != null && nonCluster.motionZoneEnabled ->
        zoneSummary(nonCluster.motionZones.size)

      else -> NO_ACTIVITY_ZONES
    }
    _uiState.update { it.copy(activityZoneSummary = summary) }
  }

  /** Opens the lullaby section when the model advertises it, then reads its current state. */
  private fun applyLullabySupport(device: Device, model: DeviceModel?) {
    val properties = model?.modelDetail?.properties?.lullabyProperties ?: return
    _uiState.update {
      it.copy(
        lullabySupported = true,
        lullabyTracks = properties.supportedTracks,
        lullabyModes = properties.supportedModes,
        lullabyTimers = properties.supportedTimers,
      )
    }
    viewModelScope.launch { reloadLullaby(device) }
  }

  /** Re-reads playback state after a transport command so the section stays honest. */
  private suspend fun reloadLullaby(device: Device) {
    sdkCall<LullabySetting> { onSuccess, onError ->
      InstaVision.deviceServices.getLullabySettings(
        spaceId = device.spaceId,
        deviceId = device.id,
        onSuccess = onSuccess,
        onError = onError,
      )
    }.onSuccess { setting ->
      _uiState.update { it.copy(lullaby = setting, selectedTrackId = setting.trackId) }
    }
  }

  /** Sends one edited lullaby preference and folds the returned settings back into the state. */
  private fun updateLullabySettings(notice: String, request: UpdateLullabySettingRequest) {
    val device = _uiState.value.device ?: return
    submit(notice = notice) {
      sdkCall<LullabySetting> { onSuccess, onError ->
        InstaVision.deviceServices.updateLullabySettings(
          device = device,
          request = request,
          onSuccess = onSuccess,
          onError = onError,
        )
      }.onSuccess { setting -> _uiState.update { it.copy(lullaby = setting) } }
    }
  }

  /** Sends one edited notification preference, resending the rest of the block untouched. */
  private fun updateNotifications(notice: String, edit: (Notification) -> Notification) {
    val device = _uiState.value.device ?: return
    val notifications = edit(_uiState.value.notifications)
    submit(notice = notice, onDone = { it.copy(notifications = notifications) }) {
      sdkCall<Unit> { onSuccess, onError ->
        InstaVision.deviceServices.updateNotificationSettings(
          device = device,
          request = notifications,
          onSuccess = { onSuccess(Unit) },
          onError = onError,
        )
      }
    }
  }

  /** Sends a partial camera-side settings patch; unset fields are left as they are. */
  private fun updateSetting(
    notice: String,
    request: UpdateDeviceSettingRequest,
    onDone: (DeviceSettingsUiState) -> DeviceSettingsUiState,
  ) {
    val device = _uiState.value.device ?: return
    submit(notice = notice, onDone = onDone) {
      writeDeviceSetting(device = device, request = request)
    }
  }

  /** Replaces the camera everywhere it is cached after the server accepts an edit. */
  private fun publish(device: Device) {
    SessionStore.putDevices(
      SessionStore.devices.map { if (it.id == device.id) device else it }
    )
    SessionStore.selectDevice(device)
    _uiState.update { it.copy(device = device, name = device.name) }
  }

  /**
   * Runs one write with the busy flag raised, applying [onDone] and showing [notice] when the
   * server accepts it. Failures leave the state untouched so the controls keep the server's view.
   */
  private fun submit(
    notice: String,
    onDone: (DeviceSettingsUiState) -> DeviceSettingsUiState = { it },
    block: suspend () -> Result<*>,
  ) {
    _uiState.update { it.copy(busy = true, error = null, notice = null) }
    viewModelScope.launch {
      block()
        .onSuccess {
          _uiState.update { state -> onDone(state).copy(busy = false, notice = notice) }
        }
        .onFailure { error ->
          _uiState.update { it.copy(busy = false, error = error.userMessage()) }
        }
    }
  }

  /** Turns a zone count into the sentence the activity zone row shows. */
  private fun zoneSummary(count: Int): String =
    if (count == 0) NO_ACTIVITY_ZONES else "$count zone${if (count == 1) "" else "s"} configured"

  /** Renders the SD card figures as "x.x GB free of y.y GB", or empty when there is no card. */
  private fun formatStorage(availableMb: Double, totalMb: Double): String =
    if (totalMb <= 0) {
      ""
    } else {
      "%.1f GB free of %.1f GB".format(availableMb / MB_PER_GB, totalMb / MB_PER_GB)
    }
}

/** A UTC wall time as a camera's device settings carry it, read as the phone's own time of day. */
private fun Time.toLocalTime(): LocalTime = LocalTime.of(hour, minute).toLocalZone()

/**
 * The inverse of [Time.toLocalTime]. An end the camera has never reported is sent as midnight,
 * because a device-settings write always carries both ends of the window together.
 */
private fun LocalTime?.toUtcTime(): Time {
  val utc = this?.toUtcZone() ?: return Time()
  return Time(hour = utc.hour, minute = utc.minute)
}

/**
 * The detection categories this screen lets the user switch on and off. These are account
 * settings written through the AI settings API, not cluster attributes — the camera's own edge-AI
 * cluster only decides which of them are offered, which is how the production app has it too.
 */
enum class DetectionCategory(
  /** Label shown next to the toggle. */
  val label: String,
) {
  /** Alerts when a person is recognised in frame. */
  Person(label = "People"),

  /** Alerts when a vehicle is recognised in frame. */
  Vehicle(label = "Vehicles"),

  /** Alerts when a household pet is recognised in frame. */
  Pet(label = "Pets"),

  /** Alerts when any other animal is recognised in frame. */
  Animal(label = "Animals"),

  /** Alerts when a nursery camera hears a baby cry; only offered when the camera can hear one. */
  Cry(label = "Crying"),
  ;

  /** Reads this category's flag out of [detection], treating a missing flag as off. */
  fun readFrom(detection: AiDetection): Boolean = when (this) {
    Person -> detection.person
    Vehicle -> detection.vehicle
    Pet -> detection.pet
    Animal -> detection.animal
    Cry -> detection.cry
  } ?: false

  /** Returns a copy of [detection] with only this category's flag replaced by [enabled]. */
  fun applyTo(detection: AiDetection, enabled: Boolean): AiDetection = when (this) {
    Person -> detection.copy(person = enabled)
    Vehicle -> detection.copy(vehicle = enabled)
    Pet -> detection.copy(pet = enabled)
    Animal -> detection.copy(animal = enabled)
    Cry -> detection.copy(cry = enabled)
  }

  /** Whether this category is on offer for a camera described by [state]. */
  fun availableIn(state: DeviceSettingsUiState): Boolean =
    this != Cry || state.cryDetectionSupported
}
