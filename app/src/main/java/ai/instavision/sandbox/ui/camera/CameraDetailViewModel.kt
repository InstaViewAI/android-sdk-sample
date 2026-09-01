package ai.instavision.sandbox.ui.camera

import ai.instavision.sandbox.data.SessionStore
import ai.instavision.sandbox.ui.common.sdkCall
import ai.instavision.sandbox.ui.common.userMessage
import ai.instavision.guardian.sdk.InstaVision
import ai.instavision.guardian.sdk.data.entity.Device
import ai.instavision.guardian.sdk.data.entity.aws.StreamStatus
import ai.instavision.guardian.sdk.data.entity.request.MoveRequest
import ai.instavision.guardian.sdk.data.entity.supportsManualSiren
import ai.instavision.guardian.sdk.data.entity.supportsPTZ
import ai.instavision.guardian.sdk.data.enums.MoveValue
import ai.instavision.guardian.sdk.data.enums.StreamType
import ai.instavision.guardian.sdk.webrtc.TextureViewRenderer
import ai.instavision.guardian.sdk.webrtc.live.LiveStreamClient
import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Format snapshots are written in; JPEG keeps a full-resolution still a sensible size. */
private const val SNAPSHOT_MIME_TYPE = "image/jpeg"

/** Gallery folder snapshots land in, relative to the shared external volume. */
private const val SNAPSHOT_DIRECTORY = "Pictures/InstaVision"

/** Compression quality snapshots are encoded at. */
private const val SNAPSHOT_QUALITY = 95

/** Fallback name for a snapshot taken from a camera whose name is blank or all punctuation. */
private const val SNAPSHOT_FALLBACK_NAME = "Camera"

/** Confirmation shown once a still has reached the gallery. */
private const val SNAPSHOT_SAVED = "Snapshot saved to Photos"

/** Shown when the still could not be written, or when there was no frame to take one from. */
private const val SNAPSHOT_FAILED = "Could not save the snapshot"

/**
 * The four axes the pan/tilt pad drives, each carrying the pan and tilt the camera expects. The
 * magnitudes are the SDK's own [MoveValue] units, which are directions rather than step sizes:
 * how far the camera travels is decided by the firmware, not by the caller.
 */
enum class PtzDirection(val pan: Int, val tilt: Int) {
  /** Tilts the lens upwards. */
  Up(pan = MoveValue.NONE.value, tilt = MoveValue.UP.value),

  /** Tilts the lens downwards. */
  Down(pan = MoveValue.NONE.value, tilt = MoveValue.DOWN.value),

  /** Pans the lens anticlockwise. */
  Left(pan = MoveValue.LEFT.value, tilt = MoveValue.NONE.value),

  /** Pans the lens clockwise. */
  Right(pan = MoveValue.RIGHT.value, tilt = MoveValue.NONE.value),
}

/** Everything the camera detail screen renders except the video surface itself. */
data class CameraDetailUiState(
  /** Camera being streamed; empty when no camera was selected before navigating here. */
  val deviceName: String = "",
  /** False when [SessionStore.selectedDevice] was null, in which case nothing is streamed. */
  val hasDevice: Boolean = false,
  /** Latest WebRTC status; `FAILED` is recoverable and offers a retry. */
  val status: StreamStatus = StreamStatus.CONNECTING,
  /** True once the first frame has been drawn, so controls can be enabled. */
  val hasLoadedStream: Boolean = false,
  /** Two-way audio state; only ever turned on after `RECORD_AUDIO` is granted. */
  val micEnabled: Boolean = false,
  /** Whether the camera's audio is routed to the phone speaker. */
  val speakerEnabled: Boolean = false,
  /** True while a clip is being written to the gallery. */
  val recording: Boolean = false,
  /** True when the stream is pinned to HD instead of adapting its bitrate. */
  val hdOnly: Boolean = false,
  /** Local mirror of the siren, which the client exposes as commands rather than as a flow. */
  val sirenOn: Boolean = false,
  /** Wi-Fi RSSI reported over the data channel, null until the camera sends one. */
  val signalStrength: Int? = null,
  /** Ambient temperature in degrees Celsius, null on cameras without the sensor. */
  val temperature: Float? = null,
  /** Ambient relative humidity, null on cameras without the sensor. */
  val humidity: Float? = null,
  /** Whether the pan/tilt controls apply to this model. */
  val supportsPtz: Boolean = false,
  /** Whether the siren control applies to this model. */
  val supportsSiren: Boolean = false,
  /** Transient confirmation such as a saved recording, cleared by the screen once shown. */
  val message: String? = null,
  /** Message from the last failed control call, independent of the stream status. */
  val error: String? = null,
)

/**
 * Owns the [LiveStreamClient] for the selected camera. The client is a connection plus an audio
 * session, so it is closed in [onCleared] and on every retry — skipping that leaks both.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CameraDetailViewModel : ViewModel() {
  private val device: Device? = SessionStore.selectedDevice
  private val _client = MutableStateFlow<LiveStreamClient?>(null)
  private val _uiState = MutableStateFlow(
    CameraDetailUiState(
      deviceName = device?.name.orEmpty(),
      hasDevice = device != null,
      supportsPtz = device?.supportsPTZ() == true,
      supportsSiren = device?.supportsManualSiren() == true,
    ),
  )
  private var mirrorJob: Job? = null
  private var moveJob: Job? = null
  private var moveRunning = false

  /** Single source of truth for [CameraDetailScreen]. */
  val uiState = _uiState.asStateFlow()

  /**
   * The SDK-built renderers, exposed as-is rather than copied into [uiState]: these are live
   * `View` instances that must reach the composable so it can attach them.
   */
  val players: StateFlow<List<TextureViewRenderer?>> = _client
    .flatMapLatest { client -> client?.players ?: flowOf(emptyList()) }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.Eagerly,
      initialValue = emptyList(),
    )

  init {
    if (device != null) startStream()
  }

  /**
   * Tears the current connection down and starts a fresh one. A closed client cannot reconnect,
   * so recovering from `FAILED` always means building a new [LiveStreamClient].
   */
  fun retry() {
    closeClient()
    _uiState.update {
      it.copy(
        status = StreamStatus.CONNECTING,
        hasLoadedStream = false,
        micEnabled = false,
        speakerEnabled = false,
        recording = false,
        sirenOn = false,
        error = null,
      )
    }
    startStream()
  }

  /** Turns two-way audio on or off; the caller must have `RECORD_AUDIO` before enabling it. */
  fun setMicEnabled(enabled: Boolean) {
    _client.value?.enableMic(enabled)
  }

  /** Routes the camera's audio to the phone speaker, or mutes it. */
  fun setSpeakerEnabled(enabled: Boolean) {
    _client.value?.enableSpeaker(enabled)
  }

  /** Starts or stops writing the live stream to the device gallery. */
  fun toggleRecording() {
    val client = _client.value ?: return
    if (_uiState.value.recording) {
      client.stopRecording { saved ->
        _uiState.update {
          it.copy(message = if (saved > 0) "Saved $saved clip(s)" else "Nothing was recorded")
        }
      }
    } else {
      client.startRecording(
        shouldInterpolate = null,
        minMp = null,
        minWidth = null,
        minHeight = null,
        fileName = _uiState.value.deviceName.ifBlank { "camera" },
      )
    }
  }

  /** Sounds or silences the camera's siren, mirroring the state locally. */
  fun toggleSiren() {
    val client = _client.value ?: return
    val next = !_uiState.value.sirenOn
    if (next) client.startSiren() else client.stopSiren()
    _uiState.update { it.copy(sirenOn = next) }
  }

  /** Switches between adaptive bitrate and a pinned HD stream. */
  fun toggleHdOnly() {
    _client.value?.toggleHdOnly()
  }

  /**
   * Drives the pan/tilt motors, which live on the device service rather than on the stream client.
   * A [continuous] move runs until [stopMove] halts it and is what a held key sends; a tap sends a
   * single non-continuous command, which the camera completes by itself and must not be stopped.
   *
   * One command covers a whole gesture, so holding a key never floods the service.
   */
  fun move(direction: PtzDirection, continuous: Boolean) {
    val target = device ?: return
    if (continuous) moveRunning = true
    moveJob = viewModelScope.launch {
      sdkCall<Unit> { onSuccess, onError ->
        InstaVision.deviceServices.ptzMove(
          device = target,
          request = MoveRequest(pan = direction.pan, tilt = direction.tilt, nonStop = continuous),
          onSuccess = { onSuccess(Unit) },
          onError = onError,
        )
      }.onFailure { error -> _uiState.update { it.copy(error = error.userMessage()) } }
    }
  }

  /**
   * Halts a continuous move, and does nothing when none is running. The stop waits on the move it
   * terminates: a gesture released just after the hold threshold would otherwise let the two
   * requests overtake each other and leave the camera turning.
   */
  fun stopMove() {
    val target = device ?: return
    if (!moveRunning) return
    moveRunning = false
    val pending = moveJob
    viewModelScope.launch {
      pending?.join()
      sdkCall<Unit> { onSuccess, onError ->
        InstaVision.deviceServices.stopPTZ(
          device = target,
          onSuccess = { onSuccess(Unit) },
          onError = onError,
        )
      }.onFailure { error -> _uiState.update { it.copy(error = error.userMessage()) } }
    }
  }

  /**
   * Writes a still to the gallery. [LiveStreamClient] has no capture API, so the frame is read off
   * the renderer by the screen and handed here; on API 29+ the `MediaStore` insert needs no
   * storage grant, which is why the whole path is a plain content-resolver write.
   */
  fun saveSnapshot(bitmap: Bitmap?, resolver: ContentResolver) {
    if (bitmap == null) {
      _uiState.update { it.copy(error = SNAPSHOT_FAILED) }
      return
    }
    val name = snapshotFileName(deviceName = _uiState.value.deviceName)
    viewModelScope.launch {
      val saved = withContext(Dispatchers.IO) {
        runCatching { writeSnapshot(bitmap = bitmap, name = name, resolver = resolver) }
          .getOrDefault(false)
      }
      _uiState.update {
        if (saved) it.copy(message = SNAPSHOT_SAVED) else it.copy(error = SNAPSHOT_FAILED)
      }
    }
  }

  /** Drops a transient confirmation once the screen has displayed it. */
  fun consumeMessage() {
    _uiState.update { it.copy(message = null) }
  }

  /** Dismisses the last control error. */
  fun clearError() {
    _uiState.update { it.copy(error = null) }
  }

  override fun onCleared() {
    abandonMove()
    closeClient()
    super.onCleared()
  }

  /**
   * Stops a move left running by a screen that went away mid-gesture. [viewModelScope] is already
   * cancelled by the time this runs, so the stop goes straight to the service instead of through
   * [sdkCall]; a coroutine started here would never get to send it.
   */
  private fun abandonMove() {
    val target = device ?: return
    if (!moveRunning) return
    moveRunning = false
    InstaVision.deviceServices.stopPTZ(device = target, onSuccess = {}, onError = {})
  }

  /** Builds the client, which connects on construction, and mirrors its flows into [uiState]. */
  private fun startStream() {
    val target = device ?: return
    val client = LiveStreamClient(
      streamType = StreamType.MAINSTREAM,
      device = target,
      autoDisconnectAfter = 0L,
      prewarmEnabled = false,
    )
    _client.value = client
    mirrorJob?.cancel()
    mirrorJob = viewModelScope.launch {
      launch { client.status.collect { value -> _uiState.update { it.copy(status = value) } } }
      launch {
        client.hasLoadedStream.collect { value ->
          _uiState.update { it.copy(hasLoadedStream = value) }
        }
      }
      launch {
        client.isMicEnabled.collect { value -> _uiState.update { it.copy(micEnabled = value) } }
      }
      launch {
        client.isSpeakerEnabled.collect { value ->
          _uiState.update { it.copy(speakerEnabled = value) }
        }
      }
      launch {
        client.isRecording.collect { value -> _uiState.update { it.copy(recording = value) } }
      }
      launch { client.isHdOnly.collect { value -> _uiState.update { it.copy(hdOnly = value) } } }
      launch {
        client.signalStrength.collect { value ->
          _uiState.update { it.copy(signalStrength = value) }
        }
      }
      launch {
        client.temperatureInfo.collect { value -> _uiState.update { it.copy(temperature = value) } }
      }
      launch {
        client.humidityInfo.collect { value -> _uiState.update { it.copy(humidity = value) } }
      }
    }
  }

  /** Stops mirroring and releases the connection, the renderers and the audio session. */
  private fun closeClient() {
    mirrorJob?.cancel()
    mirrorJob = null
    _client.value?.close()
    _client.value = null
  }
}

/**
 * Inserts [bitmap] into the shared image collection, holding it pending until the bytes are all
 * written so no half-encoded still ever appears in the gallery.
 */
private fun writeSnapshot(bitmap: Bitmap, name: String, resolver: ContentResolver): Boolean {
  val values = ContentValues().apply {
    put(MediaStore.Images.Media.DISPLAY_NAME, name)
    put(MediaStore.Images.Media.MIME_TYPE, SNAPSHOT_MIME_TYPE)
    put(MediaStore.Images.Media.RELATIVE_PATH, SNAPSHOT_DIRECTORY)
    put(MediaStore.Images.Media.IS_PENDING, 1)
  }
  val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
  val written = resolver.openOutputStream(uri)?.use { stream ->
    bitmap.compress(Bitmap.CompressFormat.JPEG, SNAPSHOT_QUALITY, stream)
  } == true
  if (!written) {
    resolver.delete(uri, null, null)
    return false
  }
  values.clear()
  values.put(MediaStore.Images.Media.IS_PENDING, 0)
  resolver.update(uri, values, null, null)
  return true
}

/** Names a snapshot after its camera and the instant it was taken, dropping anything a
 *  `MediaStore` display name may not contain. */
private fun snapshotFileName(deviceName: String): String {
  val label = deviceName.filter { it.isLetterOrDigit() || it == ' ' }.trim()
  return "${label.ifBlank { SNAPSHOT_FALLBACK_NAME }} ${System.currentTimeMillis()}.jpg"
}
