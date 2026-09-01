package ai.instavision.sandbox.ui.pairing

import ai.instavision.sandbox.SampleApp
import ai.instavision.sandbox.data.SessionStore
import ai.instavision.sandbox.ui.common.sdkCall
import ai.instavision.sandbox.ui.common.userMessage
import ai.instavision.guardian.sdk.InstaVision
import ai.instavision.guardian.sdk.data.entity.Device
import ai.instavision.guardian.sdk.data.entity.DeviceModel
import ai.instavision.guardian.sdk.data.entity.PairingSession
import ai.instavision.guardian.sdk.data.entity.PairingStatusResponse
import ai.instavision.guardian.sdk.data.entity.TimezoneSettings
import ai.instavision.guardian.sdk.data.entity.ble.BleConfig
import ai.instavision.guardian.sdk.data.entity.ble.WifiNetwork
import ai.instavision.guardian.sdk.data.entity.isLoginFailed
import ai.instavision.guardian.sdk.data.entity.isPairingFailed
import ai.instavision.guardian.sdk.data.entity.request.PairingSessionRequest
import ai.instavision.guardian.sdk.data.entity.request.UpdateDeviceRequest
import ai.instavision.guardian.sdk.data.entity.request.ValidateSimId
import ai.instavision.guardian.sdk.data.enums.PairingStatus
import ai.instavision.guardian.sdk.data.enums.SessionType
import ai.instavision.guardian.sdk.domain.ble.BleCallback
import android.annotation.SuppressLint
import android.bluetooth.le.ScanResult
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.TimeZone
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Gap between two backend polls while the wizard waits for the camera to come online. */
private const val POLL_INTERVAL_MS = 3_000L

/** Overall budget for the backend confirmation, after which the wizard reports a timeout. */
private const val POLL_TIMEOUT_MS = 120_000L

/**
 * Budget for the same confirmation on the QR path, which is longer because the wait starts the
 * moment the code appears: lining the phone up with the lens is part of what is being waited for.
 */
private const val QR_POLL_TIMEOUT_MS = 300_000L

/** Budget for one BLE handshake step, matching the SDK's own 90 second BLE timeouts. */
private const val BLE_STEP_TIMEOUT_MS = 90_000L

/** Clock format sent with the pairing session's timezone settings. */
/**
 * Pause before the one retry of the camera list. The backend can report a camera paired a moment
 * before it appears in the space, and activation needs the device object the list carries.
 */
private const val DEVICE_APPEAR_RETRY_MS = 2_000L

/** Clock format sent with the pairing session's timezone settings. */
private const val TZ_FORMAT_24_HOUR = "24"

/** Told to the user when no home is selected, which every pairing call is scoped to. */
private const val NO_SPACE =
  "Pick a home on the cameras screen before adding a camera to it."

/** Told to the user when the SDK's scan window closed without finding a single camera. */
private const val NO_CAMERAS_FOUND =
  "No cameras found. Put the camera into pairing mode and start over."

/** Told to the user when the scan window closed but cameras had already been found. */
private const val SCAN_WINDOW_CLOSED =
  "Scanning has stopped. Pick a camera below, or start over to look again."

/** Told to the user when the camera dropped the Bluetooth link mid-handshake. */
private const val BLE_CONNECTION_LOST =
  "Lost the Bluetooth connection to the camera."

/** Told to the user when a picked camera advertises no name, which the SDK needs to connect. */
private const val CAMERA_HAS_NO_NAME =
  "That camera is not advertising a name yet, and the SDK needs one to connect."

/** Told to the user when the camera never accepted the connection. */
private const val CONNECT_TIMED_OUT =
  "The camera did not accept the Bluetooth connection in time."

/** Told to the user when the camera connected but never finished service discovery. */
private const val DISCOVERY_TIMED_OUT =
  "The camera connected but never became ready to talk to."

/** Told to the user when the camera reported no Wi-Fi networks at all. */
private const val WIFI_SCAN_TIMED_OUT =
  "The camera did not report any Wi-Fi networks. Move it closer to the router and start over."

/** Told to the user when the camera never acknowledged the credentials it was sent. */
private const val WIFI_CONFIG_TIMED_OUT =
  "The camera never acknowledged the Wi-Fi credentials."

/** Told to the user when the backend never reported the camera as paired. */
private const val CONFIRM_TIMED_OUT =
  "The camera did not come online in time. Check the Wi-Fi password and start over."

/** Told to the user when the backend session ran out before the camera reported in. */
private const val SESSION_EXPIRED =
  "The pairing session expired before the camera came online."

/** Fallback reason for a backend-reported login failure that carried no reason of its own. */
private const val LOGIN_FAILED = "The camera could not sign in to the backend."

/** Fallback reason for a backend-reported pairing failure that carried no reason of its own. */
private const val PAIRING_FAILED = "The backend rejected this camera."

/** Told to the user when the identified camera was lost before its pairing session was opened. */
private const val MODEL_MISSING =
  "We lost track of which camera this is. Start over and connect to it again."

/** Told to the user when the QR path was reached without a backend session to put in the code. */
private const val SESSION_MISSING =
  "The pairing session is not ready yet. Start over and try again."

/** One stage of the pairing wizard, rendered one at a time by `PairCameraScreen`. */
enum class PairingStep {
  /** Bluetooth permissions and the radio itself, checked before any other work starts. */
  Permissions,

  /** The backend pairing session, whose key is an input to the Wi-Fi handshake later on. */
  CreatingSession,

  /** The BLE scan, with cameras appearing in the list as they advertise. */
  Scanning,

  /** Opening a GATT connection to the camera the user picked. */
  Connecting,

  /** Discovering the camera's GATT services and waiting for it to report itself ready. */
  Discovering,

  /** Listing the Wi-Fi networks the camera can see, and collecting the password for one. */
  WifiScan,

  /** Handing the credentials to the camera over BLE. */
  SendingWifi,

  /**
   * Bluetooth has been given back and the camera reaches the backend on its own: either by reading
   * the credentials off the code this phone displays, or over its own SIM.
   */
  Handover,

  /** BLE is finished; the backend is being polled for what actually happened. */
  Confirming,

  /** The camera is online and the cached camera list has been refreshed. */
  Success,

  /** The flow stopped early; the state carries the reason it stopped. */
  Failed,
}

/**
 * One screen of the five-step wizard. [PairingStep] stays the SDK's own state machine; this is
 * what the user is looking at while that machine runs, which is why several pages share a step.
 */
enum class PairPage(val wizardStep: Int) {
  /** Step 1: what a camera that has just been powered on should be doing. */
  PowerOn(wizardStep = 1),

  /** Step 1: the pinhole reset, for a camera whose light says it is not in setup mode. */
  ResetCamera(wizardStep = 1),

  /** Step 2: the BLE scan, counting the cameras it has turned up so far. */
  Searching(wizardStep = 2),

  /** Step 2: the cameras the scan found, strongest signal first. */
  PickCamera(wizardStep = 2),

  /** Step 2: the scan turned up nothing, with the three things worth checking. */
  NoCameraFound(wizardStep = 2),

  /** Step 3: the BLE handshake, from connecting through to the credentials being sent. */
  Connecting(wizardStep = 3),

  /** Step 3: the networks the camera reported, plus manual entry for a hidden one. */
  ChooseNetwork(wizardStep = 3),

  /** Step 3: the password for the chosen network. */
  WifiDetails(wizardStep = 3),

  /** Step 3: the credentials as a code on screen, for the camera to read with its own lens. */
  ShowCode(wizardStep = 3),

  /** Step 3: what a mobile-data camera needs instead of a Wi-Fi network. */
  SimIntro(wizardStep = 3),

  /** Step 3: the SIM number, which the backend validates before pairing continues. */
  SimNumber(wizardStep = 3),

  /** Step 3: the backend rejected the SIM number that was entered. */
  SimInvalid(wizardStep = 3),

  /** Step 3: the SIM is being activated for this home. */
  SimActivating(wizardStep = 3),

  /** Step 4: the backend poll that waits for the camera to come online. */
  Adding(wizardStep = 4),

  /** Step 5: naming the camera that was added. */
  Connected(wizardStep = 5),

  /** The flow stopped early; `PairCameraUiState.stoppedStep` says where it stopped. */
  Stopped(wizardStep = 1),
}

/** A camera the BLE scan turned up, together with the label the list shows for it. */
data class DiscoveredCamera(
  /** The SDK's own scan result; its `device` is what `connectToDevice` is handed. */
  val result: ScanResult,
  /** Advertised name, falling back to the hardware address when the camera has not sent one. */
  val label: String,
)

/** Everything the pairing wizard renders, for whichever page it is currently showing. */
data class PairCameraUiState(
  /** The screen on show; every other property belongs to one or two of these. */
  val page: PairPage = PairPage.PowerOn,
  /** The SDK stage behind [page], which several pages share. */
  val step: PairingStep = PairingStep.Permissions,
  /** Whether the Bluetooth permissions this Android version needs have been granted. */
  val permissionsGranted: Boolean = false,
  /** Whether the radio is on; the SDK's scan quietly finds nothing while Bluetooth is off. */
  val bluetoothEnabled: Boolean = true,
  /** Cameras found so far, mirrored from `bleService.scanResults`, strongest signal first. */
  val cameras: List<DiscoveredCamera> = emptyList(),
  /** Label of the camera being paired, shown on every step after the scan. */
  val selectedCamera: String = "",
  /** Networks the camera reported, mirrored from `bleService.wifiNetworks`. */
  val networks: List<WifiNetwork> = emptyList(),
  /** Network the user picked to put the camera on; blank until one is tapped or typed. */
  val selectedSsid: String = "",
  /** Password typed for [selectedSsid]; an open network is paired with an empty one. */
  val password: String = "",
  /** Whether [password] should be offered again for the next camera of this session. */
  val rememberPassword: Boolean = false,
  /** What the code on screen encodes; empty on every page that is not showing one. */
  val qrPayload: String = "",
  /** The model the connected camera resolved to, which decides the Wi-Fi or SIM branch. */
  val deviceModel: DeviceModel? = null,
  /** SIM number typed on the mobile-data branch, validated before pairing continues. */
  val simNumber: String = "",
  /** Latest status word from the backend poll, shown while the wizard is confirming. */
  val backendStatus: String = "",
  /** The camera that was added, which the last step renames and reports on. */
  val pairedDevice: Device? = null,
  /** Name typed on the last step; the rename only runs once it is non-blank. */
  val cameraName: String = "",
  /** Whether a one-shot SDK call is in flight, which the button it belongs to shows. */
  val busy: Boolean = false,
  /** Wizard number the flow stopped on, so the counter on the stop screen stays honest. */
  val stoppedStep: Int = 1,
  /** Set once the wizard is finished with, which the screen turns into a navigation. */
  val finished: Boolean = false,
  /** Why the flow stopped, or a non-fatal note about a step that is still usable. */
  val error: String? = null,
)

/** How the backend confirmation poll ended. */
private sealed interface PollOutcome {
  /** The backend reported the camera as paired; [deviceId] identifies the new camera. */
  data class Paired(val deviceId: String) : PollOutcome

  /** The session settled without the camera coming online, with [reason] for the user. */
  data class Failed(val reason: String) : PollOutcome
}

/**
 * The Wi-Fi password the user asked to reuse for the next camera. It is held in memory for the
 * life of the process and nothing else: it is never written to disk, never sent anywhere but the
 * camera itself, and gone the moment the app is killed.
 */
private object RememberedWifi {
  /** Network the remembered password belongs to; a different network gets nothing back. */
  private var ssid: String = ""

  /** The password itself, kept only in this field. */
  private var password: String = ""

  /** Keeps [password] for the next camera put on [ssid] during this run of the app. */
  fun remember(ssid: String, password: String) {
    this.ssid = ssid
    this.password = password
  }

  /** Drops whatever was remembered, which unticking the checkbox does. */
  fun forget() {
    ssid = ""
    password = ""
  }

  /** The remembered password for [ssid], or an empty one when nothing matches it. */
  fun passwordFor(ssid: String): String = if (ssid == this.ssid) password else ""
}

/**
 * Drives the whole pairing sequence: backend session, BLE scan, GATT connect, service discovery,
 * Wi-Fi handshake and the backend poll that confirms the camera actually came online, plus the
 * two branches that hand the camera over without BLE carrying the credentials — the code this
 * phone displays for the camera to read, and a mobile-data camera whose SIM is validated instead
 * of a network being chosen.
 *
 * The SDK holds a GATT connection and a scan for the duration, so `release()` runs in
 * [onCleared] as well as on both terminal steps; skipping it leaks the radio to the next screen.
 * Reading `ScanResult.device.name` needs `BLUETOOTH_CONNECT`, which the screen has already
 * secured before any of this runs, hence the class-wide lint suppression.
 */
@SuppressLint("MissingPermission")
class PairCameraViewModel : ViewModel() {
  private val _uiState = MutableStateFlow(PairCameraUiState())
  private var sessionKey: String = ""
  private var bleDeviceId: String = ""
  private var callbackRegistered: Boolean = false
  private var mirrorJob: Job? = null
  private var timeoutJob: Job? = null
  private var pollJob: Job? = null

  /** Single source of truth for `PairCameraScreen`. */
  val uiState = _uiState.asStateFlow()

  /**
   * Bridges the SDK's BLE handshake into [uiState]. Every override here is invoked on a GATT
   * binder thread, so each one hops to the main dispatcher before touching state or the SDK.
   */
  private val bleCallback = object : BleCallback() {
    /** The GATT link is up; service discovery still has to be asked for explicitly. */
    override fun onConnected() {
      onMain {
        if (_uiState.value.step != PairingStep.Connecting) return@onMain
        _uiState.update { it.copy(step = PairingStep.Discovering, page = PairPage.Connecting) }
        armTimeout(step = PairingStep.Discovering, reason = DISCOVERY_TIMED_OUT)
        InstaVision.bleService.startServiceDiscovery()
      }
    }

    /**
     * Carries the camera's own identifier. It arrives during service discovery rather than
     * right after connecting, and cameras without the characteristic never send it at all.
     */
    override fun onDeviceIdReceived(deviceId: String) {
      onMain { bleDeviceId = deviceId }
    }

    /** The camera is ready for commands, so find out which of the two branches its model takes. */
    override fun onDeviceReady() {
      onMain {
        if (_uiState.value.step != PairingStep.Discovering) return@onMain
        identifyConnectedCamera()
      }
    }

    /** The SDK's 90 second scan window closed; whether that is fatal depends on the results. */
    override fun onScanStopped() {
      onMain {
        if (_uiState.value.step != PairingStep.Scanning) return@onMain
        if (_uiState.value.cameras.isEmpty()) {
          fail(NO_CAMERAS_FOUND)
        } else {
          _uiState.update { it.copy(error = SCAN_WINDOW_CLOSED) }
        }
      }
    }

    /**
     * Only confirms that the credentials reached the camera. Whether the camera then joined the
     * network and registered itself is what the backend poll is for.
     */
    override fun onWifiConfigSent() {
      onMain {
        if (_uiState.value.step != PairingStep.SendingWifi) return@onMain
        startConfirming()
      }
    }

    /**
     * The camera hangs up on BLE once it has the credentials, so a drop after the handshake is
     * expected rather than a failure.
     */
    override fun onDeviceConnectionFailed() {
      onMain {
        if (_uiState.value.step in SETTLED_STEPS) return@onMain
        fail(BLE_CONNECTION_LOST)
      }
    }
  }

  /** Offers the pinhole reset to someone whose status light is not blinking blue yet. */
  fun showReset() {
    _uiState.update { it.copy(page = PairPage.ResetCamera, error = null) }
  }

  /** Returns to the power-on instructions, which the reset screen's second choice does. */
  fun showPowerOn() {
    _uiState.update { it.copy(page = PairPage.PowerOn, error = null) }
  }

  /**
   * Opens step 2 and drops anything a previous attempt had taken, so "Search again" and the
   * first arrival from step 1 behave identically. The screen reports permissions from here,
   * which is what actually starts the backend session and the scan.
   */
  fun startSearch() {
    teardown()
    sessionKey = ""
    bleDeviceId = ""
    val state = _uiState.value
    _uiState.value = PairCameraUiState(
      page = PairPage.Searching,
      permissionsGranted = state.permissionsGranted,
      bluetoothEnabled = state.bluetoothEnabled,
      rememberPassword = state.rememberPassword,
    )
  }

  /** Shows the cameras the scan has turned up, which the scan screen's button opens. */
  fun showPicker() {
    if (_uiState.value.cameras.isEmpty()) return
    _uiState.update { it.copy(page = PairPage.PickCamera) }
  }

  /** Explains what to check when the camera the user expected is not in the list. */
  fun showNoCameraFound() {
    _uiState.update { it.copy(page = PairPage.NoCameraFound, error = null) }
  }

  /**
   * Opens the path that gives the camera its credentials through a code on this screen rather than
   * over Bluetooth. The radio is handed back on the way in, exactly as the production app does, and
   * the backend session the code has to carry is opened here when the abandoned Bluetooth attempt
   * never got as far as creating one.
   */
  fun startQrPairing() {
    teardown()
    _uiState.update {
      it.copy(
        page = PairPage.ChooseNetwork,
        step = PairingStep.Handover,
        cameras = emptyList(),
        networks = emptyList(),
        busy = sessionKey.isBlank(),
        error = null,
      )
    }
    if (sessionKey.isBlank()) {
      createSession(onReady = { _uiState.update { state -> state.copy(busy = false) } })
    }
  }

  /**
   * Reports what the screen found out about permissions and the radio, and starts the flow once
   * both are in place. Ignored outside the permissions step so a late result cannot rewind it.
   */
  fun onPermissionsResult(granted: Boolean, bluetoothEnabled: Boolean) {
    if (_uiState.value.step != PairingStep.Permissions) return
    if (_uiState.value.page != PairPage.Searching) return
    _uiState.update {
      it.copy(permissionsGranted = granted, bluetoothEnabled = bluetoothEnabled, error = null)
    }
    if (!granted || !bluetoothEnabled) return
    _uiState.update { it.copy(step = PairingStep.CreatingSession) }
    createSession(onReady = ::startScan)
  }

  /**
   * Connects to the camera the user tapped. The scan is stopped first because the SDK cannot
   * open a GATT connection while its scanner is still running.
   */
  fun selectCamera(camera: DiscoveredCamera) {
    if (_uiState.value.step != PairingStep.Scanning) return
    val device = camera.result.device
    if (device.name == null) {
      _uiState.update { it.copy(error = CAMERA_HAS_NO_NAME) }
      return
    }
    InstaVision.bleService.stopScan()
    _uiState.update {
      it.copy(
        step = PairingStep.Connecting,
        page = PairPage.Connecting,
        selectedCamera = camera.label,
        error = null,
      )
    }
    armTimeout(step = PairingStep.Connecting, reason = CONNECT_TIMED_OUT)
    InstaVision.bleService.connectToDevice(device)
  }

  /** Picks the network the camera should join, carrying its remembered password over. */
  fun selectNetwork(network: WifiNetwork) {
    useNetwork(ssid = network.ssid)
  }

  /** Takes the network name typed by hand, for a camera that reported a hidden network. */
  fun useNetwork(ssid: String) {
    if (ssid.isBlank()) return
    _uiState.update {
      it.copy(
        page = PairPage.WifiDetails,
        selectedSsid = ssid,
        password = RememberedWifi.passwordFor(ssid),
        error = null,
      )
    }
  }

  /** Records an edit to the network name, which the details step starts out pre-filled with. */
  fun setSsid(ssid: String) {
    _uiState.update { it.copy(selectedSsid = ssid) }
  }

  /** Records the password typed for the selected network. */
  fun setPassword(password: String) {
    _uiState.update { it.copy(password = password) }
  }

  /** Records whether the password should be offered again for the next camera. */
  fun setRememberPassword(remember: Boolean) {
    _uiState.update { it.copy(rememberPassword = remember) }
  }

  /**
   * Hands the credentials to the camera. A camera reached over Bluetooth is sent them directly;
   * on the QR path there is no BLE link to send them over, so they go on screen as a code for the
   * camera to read instead.
   */
  fun submitWifi() {
    val state = _uiState.value
    if (state.selectedSsid.isBlank() || state.busy) return
    if (state.rememberPassword) {
      RememberedWifi.remember(ssid = state.selectedSsid, password = state.password)
    } else {
      RememberedWifi.forget()
    }
    if (state.step == PairingStep.WifiScan) sendWifiConfig() else showQrCode()
  }

  /** Opens the SIM number field, which the mobile-data introduction leads on to. */
  fun showSimNumber() {
    _uiState.update { it.copy(page = PairPage.SimNumber, error = null) }
  }

  /** Records the SIM number typed on the mobile-data branch. */
  fun setSimNumber(simNumber: String) {
    _uiState.update { it.copy(simNumber = simNumber) }
  }

  /** Asks the backend whether the SIM number can be used, and activates it when it can. */
  fun validateSim() {
    val state = _uiState.value
    val simNumber = state.simNumber.trim()
    if (simNumber.isBlank() || state.busy) return
    _uiState.update { it.copy(busy = true, error = null) }
    viewModelScope.launch {
      sdkCall<Unit> { onSuccess, onError ->
        InstaVision.deviceServices.validateSimId(
          spaceId = SessionStore.spaceId,
          request = ValidateSimId(simNumber = simNumber),
          onSuccess = { onSuccess(Unit) },
          onError = onError,
        )
      }
        .onSuccess { activateSim() }
        .onFailure { error ->
          _uiState.update {
            it.copy(page = PairPage.SimInvalid, busy = false, error = error.userMessage())
          }
        }
    }
  }

  /** Records the name typed for the camera on the last step. */
  fun setCameraName(name: String) {
    _uiState.update { it.copy(cameraName = name) }
  }

  /**
   * Renames the camera that was added and finishes the wizard. A camera the device refresh never
   * returned cannot be renamed, so the wizard simply closes rather than trapping the user.
   */
  fun finishSetup() {
    val state = _uiState.value
    val device = state.pairedDevice
    val name = state.cameraName.trim()
    if (state.busy) return
    if (device == null || name.isBlank()) {
      _uiState.update { it.copy(finished = true) }
      return
    }
    _uiState.update { it.copy(busy = true, error = null) }
    viewModelScope.launch {
      sdkCall<Device> { onSuccess, onError ->
        InstaVision.deviceServices.updateDeviceInfo(
          device = device,
          request = UpdateDeviceRequest(name = name),
          onSuccess = onSuccess,
          onError = onError,
        )
      }
        .onSuccess { updated ->
          SessionStore.putDevices(
            SessionStore.devices.map { known -> if (known.id == updated.id) updated else known },
          )
          SessionStore.selectDevice(updated)
          _uiState.update { it.copy(busy = false, finished = true) }
        }
        .onFailure { error ->
          _uiState.update { it.copy(busy = false, error = error.userMessage()) }
        }
    }
  }

  /**
   * Steps the wizard back one page, reporting false when there is nowhere left to go and the
   * screen should leave instead. Backing out of a live Bluetooth handshake abandons it, which is
   * why those pages return to a fresh scan rather than to the page that opened them.
   */
  fun goBack(): Boolean {
    when (_uiState.value.page) {
      PairPage.PowerOn -> return false
      PairPage.ResetCamera -> showPowerOn()
      PairPage.Searching -> restart()

      PairPage.PickCamera -> _uiState.update { it.copy(page = PairPage.Searching, error = null) }

      PairPage.NoCameraFound,
      PairPage.Connecting,
      PairPage.ChooseNetwork -> startSearch()

      PairPage.WifiDetails -> _uiState.update {
        it.copy(page = PairPage.ChooseNetwork, error = null)
      }

      PairPage.ShowCode -> abandonCode()

      PairPage.SimIntro -> startSearch()
      PairPage.SimNumber -> _uiState.update { it.copy(page = PairPage.SimIntro, error = null) }
      PairPage.SimInvalid,
      PairPage.SimActivating -> _uiState.update {
        it.copy(page = PairPage.SimNumber, error = null)
      }

      PairPage.Adding,
      PairPage.Connected,
      PairPage.Stopped -> return false
    }
    return true
  }

  /**
   * Drops every SDK resource and returns to the first step. A fresh session key is minted on the
   * way through, because the one from the abandoned attempt is no longer usable.
   */
  fun restart() {
    teardown()
    sessionKey = ""
    bleDeviceId = ""
    val state = _uiState.value
    _uiState.value = PairCameraUiState(
      permissionsGranted = state.permissionsGranted,
      bluetoothEnabled = state.bluetoothEnabled,
      rememberPassword = state.rememberPassword,
    )
  }

  /**
   * Gives up on the flow altogether: the Bluetooth link is dropped and every trace of the attempt
   * is thrown away, so the next visit to the wizard opens on the first step instead of resuming.
   *
   * Nothing scopes this ViewModel to the wizard's destination — it belongs to the activity and
   * outlives the screen — so leaving without this is exactly what made an abandoned attempt come
   * back. It is the same teardown the production app's `quitPairingFlow` performs.
   */
  fun quitPairingFlow() {
    teardown()
    sessionKey = ""
    bleDeviceId = ""
    _uiState.value = PairCameraUiState()
  }

  /** Gives the radio back when the screen goes away, which nothing else in the SDK does. */
  override fun onCleared() {
    teardown()
    super.onCleared()
  }

  /**
   * Looks up the model of the camera that has just become ready, which is the only thing that says
   * whether it joins a Wi-Fi network or its own SIM. It is the lookup the production app runs
   * against the identifier the camera reports over GATT; a camera that reported none, and a lookup
   * the backend refuses, both fall through to the Wi-Fi branch rather than stranding setup.
   */
  private fun identifyConnectedCamera() {
    val deviceId = bleDeviceId
    if (deviceId.isBlank() || SessionStore.spaceId.isBlank()) {
      startWifiScan()
      return
    }
    viewModelScope.launch {
      val model = sdkCall<DeviceModel> { onSuccess, onError ->
        InstaVision.deviceServices.getDeviceModel(
          spaceId = SessionStore.spaceId,
          deviceId = deviceId,
          onSuccess = onSuccess,
          onError = onError,
        )
      }.getOrNull()
      if (_uiState.value.step != PairingStep.Discovering) return@launch
      if (model == null) startWifiScan() else identify(model)
    }
  }

  /** Sends the camera down the SIM branch or the Wi-Fi branch, whichever its model calls for. */
  private fun identify(model: DeviceModel) {
    _uiState.update { it.copy(deviceModel = model, busy = false, error = null) }
    if (model.isCellular()) showSimIntro() else startWifiScan()
  }

  /** Asks the camera which networks it can see, which is the branch every Wi-Fi camera takes. */
  private fun startWifiScan() {
    _uiState.update {
      it.copy(step = PairingStep.WifiScan, page = PairPage.ChooseNetwork, error = null)
    }
    armTimeout(step = PairingStep.WifiScan, reason = WIFI_SCAN_TIMED_OUT)
    InstaVision.bleService.sendWifiScanCommand()
  }

  /**
   * Opens the mobile-data branch. Bluetooth is given back on the way in: a SIM camera is paired
   * entirely through the backend, and a link left open would only report itself as lost later on.
   */
  private fun showSimIntro() {
    teardown()
    _uiState.update {
      it.copy(page = PairPage.SimIntro, step = PairingStep.Handover, error = null)
    }
  }

  /** Shows the activation wait and opens the mobile-data pairing session behind it. */
  private fun activateSim() {
    _uiState.update { it.copy(page = PairPage.SimActivating, busy = false, error = null) }
    startBackendPairing(sessionType = SessionType.FOUR_G)
  }

  /**
   * Creates the backend session both handshakes need — its key is what the camera is sent over
   * Bluetooth, and what the code on screen carries — then hands over to [onReady].
   */
  private fun createSession(onReady: () -> Unit) {
    if (SessionStore.spaceId.isBlank()) {
      fail(NO_SPACE)
      return
    }
    viewModelScope.launch {
      sdkCall<PairingSession> { onSuccess, onError ->
        InstaVision.deviceServices.createPairingSession(
          spaceId = SessionStore.spaceId,
          request = PairingSessionRequest(
            timezoneSettings = TimezoneSettings(
              id = TimeZone.getDefault().id,
              tzFormat = TZ_FORMAT_24_HOUR,
            ),
            sessionType = SessionType.OTHER.value,
          ),
          onSuccess = onSuccess,
          onError = onError,
        )
      }
        .onSuccess { session ->
          sessionKey = session.sessionKey
          onReady()
        }
        .onFailure { error -> fail(error.userMessage()) }
    }
  }

  /**
   * Opens the pairing session for a camera the backend already knows about, which is how the
   * mobile-data branch pairs: the session names the camera and [sessionType] says how it joins.
   * There is no code to show and no Wi-Fi to send, so the poll starts as soon as it is open.
   */
  private fun startBackendPairing(sessionType: SessionType) {
    if (SessionStore.spaceId.isBlank()) {
      fail(NO_SPACE)
      return
    }
    val model = _uiState.value.deviceModel
    if (model == null) {
      fail(MODEL_MISSING)
      return
    }
    _uiState.update { it.copy(busy = true, error = null) }
    viewModelScope.launch {
      sdkCall<PairingSession> { onSuccess, onError ->
        InstaVision.deviceServices.createPairingSession(
          spaceId = SessionStore.spaceId,
          request = PairingSessionRequest(
            timezoneSettings = TimezoneSettings(
              id = TimeZone.getDefault().id,
              tzFormat = TZ_FORMAT_24_HOUR,
            ),
            sessionType = sessionType.value,
            deviceId = model.did,
          ),
          onSuccess = onSuccess,
          onError = onError,
        )
      }
        .onSuccess { session ->
          sessionKey = session.sessionKey
          bleDeviceId = model.did
          _uiState.update { it.copy(busy = false) }
          startConfirming()
        }
        .onFailure { error ->
          _uiState.update { it.copy(busy = false) }
          fail(error.userMessage())
        }
    }
  }

  /** Registers for BLE events and opens the scan the camera list is built from. */
  private fun startScan() {
    registerCallback()
    _uiState.update {
      it.copy(
        step = PairingStep.Scanning,
        page = PairPage.Searching,
        cameras = emptyList(),
        error = null,
      )
    }
    InstaVision.bleService.startScan(bleConfig = BleConfig())
  }

  /**
   * Registers the BLE callback and starts mirroring the SDK's flows. `release()` clears the
   * SDK's callback list, so this has to run again after every teardown.
   */
  private fun registerCallback() {
    if (callbackRegistered) return
    InstaVision.bleService.registerCallback(bleCallback)
    callbackRegistered = true
    startMirroring()
  }

  /** Copies the SDK's scan and Wi-Fi flows into [uiState] for as long as the flow is running. */
  private fun startMirroring() {
    mirrorJob?.cancel()
    mirrorJob = viewModelScope.launch {
      launch {
        InstaVision.bleService.scanResults.collect { results ->
          val cameras = results
            .map { result -> result.toDiscoveredCamera() }
            .filter { camera -> camera.isPairableCamera() }
            .sortedByDescending { camera -> camera.result.rssi }
          _uiState.update { state -> state.copy(cameras = cameras) }
        }
      }
      launch {
        InstaVision.bleService.wifiNetworks.collect { networks ->
          _uiState.update { state -> state.copy(networks = networks) }
          if (networks.isNotEmpty() && _uiState.value.step == PairingStep.WifiScan) cancelTimeout()
        }
      }
    }
  }

  /** Hands the credentials and the session key to the camera over BLE. */
  private fun sendWifiConfig() {
    val state = _uiState.value
    if (state.step != PairingStep.WifiScan || state.selectedSsid.isBlank()) return
    _uiState.update {
      it.copy(step = PairingStep.SendingWifi, page = PairPage.Connecting, error = null)
    }
    armTimeout(step = PairingStep.SendingWifi, reason = WIFI_CONFIG_TIMED_OUT)
    InstaVision.bleService.sendWifiConfig(
      ssid = state.selectedSsid,
      password = state.password,
      sessionKey = sessionKey,
      region = SampleApp.region,
      env = SampleApp.environment,
    )
  }

  /**
   * Puts the credentials on screen as the code the camera reads with its own lens, and starts the
   * backend poll behind it: the camera says nothing to this phone, so the session's status is the
   * only sign that it has taken them in. [pollUntilSettled] swaps this page for the waiting one as
   * soon as the backend has heard from the camera.
   */
  private fun showQrCode() {
    val state = _uiState.value
    if (sessionKey.isBlank()) {
      fail(SESSION_MISSING)
      return
    }
    cancelTimeout()
    _uiState.update {
      it.copy(
        page = PairPage.ShowCode,
        step = PairingStep.Confirming,
        qrPayload = qrPayload(ssid = state.selectedSsid, password = state.password),
        backendStatus = "",
        busy = false,
        error = null,
      )
    }
    poll(budgetMs = QR_POLL_TIMEOUT_MS)
  }

  /**
   * What the camera reads off the screen, in the order the firmware expects it: the network and its
   * password, the session the camera reports itself against, and the region and build of the
   * backend it should report to. A camera being paired without a network is sent the last three on
   * their own, which is the same shortened form the production app falls back to.
   */
  private fun qrPayload(ssid: String, password: String): String {
    val region = SampleApp.region.id
    val variant = SampleApp.environment.value
    return if (ssid.isNotEmpty()) {
      "$ssid\n$password\n$sessionKey\n$region\n$variant"
    } else {
      "$sessionKey\n$region\n$variant"
    }
  }

  /** Takes the code back off the screen and stops the poll, which backing out of it does. */
  private fun abandonCode() {
    pollJob?.cancel()
    pollJob = null
    _uiState.update {
      it.copy(
        page = PairPage.WifiDetails,
        step = PairingStep.Handover,
        qrPayload = "",
        backendStatus = "",
        error = null,
      )
    }
  }

  /** Polls the backend until it settles, because BLE only proves the credentials were delivered. */
  private fun startConfirming() {
    cancelTimeout()
    _uiState.update { it.copy(step = PairingStep.Confirming, page = PairPage.Adding, error = null) }
    poll(budgetMs = POLL_TIMEOUT_MS)
  }

  /** Runs the backend poll for at most [budgetMs] and acts on however the session settles. */
  private fun poll(budgetMs: Long) {
    pollJob?.cancel()
    pollJob = viewModelScope.launch {
      val outcome = withTimeoutOrNull(budgetMs) { pollUntilSettled() }
        ?: PollOutcome.Failed(CONFIRM_TIMED_OUT)
      pollJob = null
      when (outcome) {
        is PollOutcome.Paired -> finishPairing(outcome.deviceId)
        is PollOutcome.Failed -> fail(outcome.reason)
      }
    }
  }

  /** Asks the backend for the session's outcome every few seconds until one of them is final. */
  private suspend fun pollUntilSettled(): PollOutcome {
    while (true) {
      delay(POLL_INTERVAL_MS)
      val result = sdkCall<PairingStatusResponse> { onSuccess, onError ->
        InstaVision.deviceServices.getPairingSessionStatus(
          spaceId = SessionStore.spaceId,
          sessionKey = sessionKey,
          onSuccess = onSuccess,
          onError = onError,
        )
      }
      val response = result.getOrElse { error ->
        Log.d(PAIRING_LOG_TAG, "poll failed: ${error.userMessage()}")
        return PollOutcome.Failed(error.userMessage())
      }
      Log.d(
        PAIRING_LOG_TAG,
        "poll status=${response.status} deviceId=${response.deviceId} expired=${response.expired}",
      )
      _uiState.update { state ->
        state.copy(
          backendStatus = response.status,
          page = if (state.page == PairPage.ShowCode && response.hasStarted()) {
            PairPage.Adding
          } else {
            state.page
          },
          qrPayload = if (response.hasStarted()) "" else state.qrPayload,
        )
      }
      val settled = when {
        response.expired -> PollOutcome.Failed(SESSION_EXPIRED)
        response.isLoginFailed() -> PollOutcome.Failed(response.login?.reason ?: LOGIN_FAILED)
        response.isPairingFailed() -> PollOutcome.Failed(response.pairing?.reason ?: PAIRING_FAILED)
        response.status in SETTLED_STATUSES ->
          PollOutcome.Paired(response.deviceId.ifBlank { bleDeviceId })

        else -> null
      }
      if (settled != null) return settled
    }
  }

  /** Releases BLE, republishes the space's cameras and selects the one that was just added. */
  private suspend fun finishPairing(deviceId: String) {
    Log.d(PAIRING_LOG_TAG, "pairing settled for device=$deviceId; refreshing the space's cameras")
    teardown()
    var paired = republishDevices(deviceId = deviceId)
    if (paired == null) {
      delay(DEVICE_APPEAR_RETRY_MS)
      paired = republishDevices(deviceId = deviceId)
    }
    if (paired == null) {
      Log.d(PAIRING_LOG_TAG, "paired device=$deviceId never appeared in the list; not activating")
    }
    paired?.let { device -> activate(device = device) }
    _uiState.update {
      it.copy(step = PairingStep.Success, page = PairPage.Connected, pairedDevice = paired)
    }
  }

  /**
   * Moves a freshly paired camera on to [PairingStatus.ACTIVATED], which is what the backend waits
   * for before it treats the camera as a live member of the space. The poll only ever reports
   * `Processed`, so without this the camera stays half-registered until something else activates it.
   *
   * A failure here is deliberately not fatal: the camera exists and is named on the next screen, so
   * the flow carries on and the message is surfaced rather than sending the user back to the start.
   *
   */
  private suspend fun activate(device: Device) {
    Log.d(
      PAIRING_LOG_TAG,
      "activating device=${device.id} current pairingStatus=${device.pairingStatus}",
    )
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
      Log.d(PAIRING_LOG_TAG, "activation for device=${device.id} timed out with no SDK callback")
      return
    }
    result
      .onSuccess { updated ->
        Log.d(
          PAIRING_LOG_TAG,
          "activated device=${updated.id} pairingStatus=${updated.pairingStatus}",
        )
        SessionStore.selectDevice(updated)
      }
      .onFailure { error ->
        Log.d(PAIRING_LOG_TAG, "activation failed for device=${device.id}: ${error.userMessage()}")
        _uiState.update { it.copy(error = error.userMessage()) }
      }
  }

  /**
   * Refetches the space's cameras, republishes them and returns the one just paired. Returns null
   * when the backend's list has not caught up with the camera the poll already reported.
   */
  private suspend fun republishDevices(deviceId: String): Device? {
    var found: Device? = null
    sdkCall<List<Device>> { onSuccess, onError ->
      InstaVision.deviceServices.getDevices(
        spaceId = SessionStore.spaceId,
        onSuccess = onSuccess,
        onError = onError,
      )
    }
      .onSuccess { devices ->
        SessionStore.putDevices(devices)
        found = devices.firstOrNull { device -> device.id == deviceId }
        found?.let(SessionStore::selectDevice)
      }
      .onFailure { error -> _uiState.update { it.copy(error = error.userMessage()) } }
    return found
  }

  /** Ends the flow on [reason], releasing every SDK resource it had taken. */
  private fun fail(reason: String) {
    teardown()
    _uiState.update {
      it.copy(
        step = PairingStep.Failed,
        page = if (reason == NO_CAMERAS_FOUND) PairPage.NoCameraFound else PairPage.Stopped,
        stoppedStep = it.page.wizardStep,
        busy = false,
        error = reason,
      )
    }
  }

  /** Fails the flow if [step] is still on screen once the SDK's own window has elapsed. */
  private fun armTimeout(step: PairingStep, reason: String) {
    timeoutJob?.cancel()
    timeoutJob = viewModelScope.launch {
      delay(BLE_STEP_TIMEOUT_MS)
      if (_uiState.value.step != step) return@launch
      timeoutJob = null
      fail(reason)
    }
  }

  /** Disarms the pending step timeout once the step it guards has completed. */
  private fun cancelTimeout() {
    timeoutJob?.cancel()
    timeoutJob = null
  }

  /** Stops every job and gives the radio back; safe to call more than once. */
  private fun teardown() {
    cancelTimeout()
    pollJob?.cancel()
    pollJob = null
    mirrorJob?.cancel()
    mirrorJob = null
    if (callbackRegistered) InstaVision.bleService.unregisterCallback(bleCallback)
    callbackRegistered = false
    InstaVision.bleService.release()
  }

  /** Runs [block] on the main dispatcher, which the GATT binder threads are never on. */
  private fun onMain(block: () -> Unit) {
    viewModelScope.launch { block() }
  }

  /** Constants shared by the BLE callback and the step machine. */
  private companion object {
    /** Steps after which a dropped BLE link is expected rather than a failure. */
    val SETTLED_STEPS = setOf(PairingStep.Confirming, PairingStep.Success, PairingStep.Failed)
  }
}

/** Labels a scan result for the list, preferring the GATT name the SDK connects by. */
@SuppressLint("MissingPermission")
private fun ScanResult.toDiscoveredCamera(): DiscoveredCamera = DiscoveredCamera(
  result = this,
  label = device.name ?: scanRecord?.deviceName ?: device.address,
)

/** Advertised-name prefix every InstaVision camera in setup mode announces itself with. */
private const val CAMERA_NAME_PREFIX = "IV-"

/**
 * Whether this scan result is one of our cameras rather than any other BLE device in range. A
 * camera that advertised no name at all is labelled with its hardware address, which cannot carry
 * the prefix, so it is correctly excluded.
 */
private fun DiscoveredCamera.isPairableCamera(): Boolean =
  label.startsWith(prefix = CAMERA_NAME_PREFIX, ignoreCase = true)

/**
 * Whether this is a mobile-data camera, which the backend marks with the model's
 * `four_g_props.enabled` flag. It is the same signal the production app's `is4GModel` reads, and
 * a model whose properties omit the block is treated as a Wi-Fi camera.
 */
private fun DeviceModel.isCellular(): Boolean =
  modelDetail.properties?.fourGProperties?.enabled == true

/**
 * Whether the backend has heard from the camera itself yet. Until it has, the session sits on the
 * status it was created with, and on the QR path that is what keeps the code on screen.
 */
private fun PairingStatusResponse.hasStarted(): Boolean =
  status.isNotBlank() && status != PairingStatus.INITIALIZED.type

/** Logcat tag the pairing flow's poll and activation steps are printed under. */
private const val PAIRING_LOG_TAG = "GuardianPairing"

/**
 * Backend statuses that mean the camera has joined the space. The backend usually reports
 * `Processed` and is then moved on by [PairCameraViewModel]'s own activation, but a camera that is
 * already past that point reports `Paired` or `Activated` and must settle the poll just the same,
 * or the wizard waits out its whole budget on a camera that is already there.
 */
private val SETTLED_STATUSES = setOf(
  PairingStatus.PROCESSED.type,
  PairingStatus.PAIRED.type,
  PairingStatus.ACTIVATED.type,
)

/** How long the activation call is given before the flow gives up waiting on the SDK. */
private const val ACTIVATE_TIMEOUT_MS = 20_000L
