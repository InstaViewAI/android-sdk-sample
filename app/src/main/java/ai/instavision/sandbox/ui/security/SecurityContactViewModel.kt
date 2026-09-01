package ai.instavision.sandbox.ui.security

import ai.instavision.sandbox.data.SessionStore
import ai.instavision.sandbox.ui.common.sdkCall
import ai.instavision.sandbox.ui.common.userMessage
import ai.instavision.guardian.sdk.InstaVision
import ai.instavision.guardian.sdk.data.entity.SecurityAddress
import ai.instavision.guardian.sdk.data.entity.SecurityPhoneNumber
import ai.instavision.guardian.sdk.data.entity.State
import ai.instavision.guardian.sdk.data.entity.TimeZone
import ai.instavision.guardian.sdk.data.entity.request.SecurityProfileRequest
import ai.instavision.guardian.sdk.data.entity.response.SecurityProfileResponse
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Country code the backend expects, which is the production app's own spelling rather than an ISO
 * one. Monitoring is US-only, so it is the only value this form can ever send.
 */
private const val COUNTRY_CODE = "USA"

/** How [COUNTRY_CODE] is written for the user. */
private const val COUNTRY_NAME = "United States"

/** Dialling code every monitored number carries, because monitoring is US-only. */
private const val PHONE_COUNTRY_CODE = "+1"

/** Digits in a US number, which is also the length the production app requires before it sends. */
private const val PHONE_LENGTH = 10

/** Digits in the SMS code; the backend issues exactly this many. */
private const val OTP_LENGTH = 6

/** How long the resend link stays disabled after a code goes out, matching the production timer. */
private const val RESEND_SECONDS = 60

/** One second of the resend countdown, in milliseconds. */
private const val SECOND_MILLIS = 1_000L

/** Keeps typed input numeric, so a pasted "+1 (555)" cannot reach the request. */
private val DIGITS = Regex("^\\d*$")

/**
 * Which panel of the contact step is on screen. The address has to be saved before a number can be
 * attached to the profile, so the panels run in this order and cannot be skipped.
 */
enum class ContactStage {
  /** Where the dispatcher is sent. */
  Address,

  /** The number the monitoring centre calls, before a code has been requested. */
  Phone,

  /** The code that proves the number reaches the household. */
  Verify,
}

/** Everything the contact information step renders and submits. */
data class SecurityContactUiState(
  /** Which panel is showing; the step ends when the number on the last one is verified. */
  val stage: ContactStage = ContactStage.Address,
  /** Street address, sent as the address's `line_one`, which is where the backend keeps it. */
  val street: String = "",
  /** Nearest intersection; optional, and only useful to a responder in the field. */
  val crossStreet: String = "",
  /** Town or city. */
  val city: String = "",
  /** Two-letter code of the selected state, which is what the request carries. */
  val stateCode: String = "",
  /** Postal code. */
  val zip: String = "",
  /** Code of the selected zone, which is what the request carries. */
  val timeZoneCode: String = "",
  /** The number the monitoring centre dispatches against, digits only and without the `+1`. */
  val phone: String = "",
  /** The SMS code the user is typing. */
  val otp: String = "",
  /** Seconds left before the code can be resent; zero means the link is live. */
  val resendIn: Int = 0,
  /** States offered by the dropdown, as returned by `getUsaStates`. */
  val states: List<State> = emptyList(),
  /** Zones the backend accepts; the address call is rejected without one of their codes. */
  val timeZones: List<TimeZone> = emptyList(),
  /** True when the zone list could not be fetched, which leaves the step unable to submit. */
  val timeZonesFailed: Boolean = false,
  /** Whether the home already has a profile, which decides create versus update on submit. */
  val hasProfile: Boolean = false,
  /** True until the states and the existing profile have both been read. */
  val loading: Boolean = true,
  /** True while a request is in flight. */
  val busy: Boolean = false,
  /** Set once the number has been verified, so the screen can hand back to the checklist. */
  val done: Boolean = false,
  /** Message from the last failed request; a missing profile never sets this. */
  val error: String? = null,
) {
  /** State names for the dropdown, in the order the SDK returned them. */
  val stateNames: List<String> get() = states.map { it.name }

  /** Name of the selected state, or empty so the dropdown falls back to its placeholder. */
  val stateName: String get() = states.firstOrNull { it.code == stateCode }?.name.orEmpty()

  /** Zone names for the dropdown, in the order the SDK returned them. */
  val timeZoneNames: List<String> get() = timeZones.map { it.name }

  /** Name of the selected zone, or empty so the dropdown falls back to its placeholder. */
  val timeZoneName: String
    get() = timeZones.firstOrNull { it.code == timeZoneCode }?.name.orEmpty()

  /** The country, fixed because monitoring is US-only; the dropdown exists to say so. */
  val country: String get() = COUNTRY_NAME

  /** How the phone number is shown back to the user once it is on the profile. */
  val dialCode: String get() = PHONE_COUNTRY_CODE

  /** Whether the parts a dispatcher cannot be sent without are all filled in. */
  val canSubmit: Boolean
    get() = street.isNotBlank() && city.isNotBlank() && stateCode.isNotBlank() &&
      zip.isNotBlank() && timeZoneCode.isNotBlank()

  /** Whether the number is long enough to be worth sending a code to. */
  val canSendCode: Boolean get() = phone.length == PHONE_LENGTH

  /** Whether a full code has been typed. */
  val canVerify: Boolean get() = otp.length == OTP_LENGTH

  /** Whether the resend link is live yet. */
  val canResend: Boolean get() = resendIn == 0

  /** Whether the panel's own button should be tappable. */
  val canAdvance: Boolean
    get() = when (stage) {
      ContactStage.Address -> canSubmit
      ContactStage.Phone -> canSendCode
      ContactStage.Verify -> canVerify
    }

}

/**
 * Backs the contact information step: saves the dispatch address, then verifies the number the
 * monitoring centre calls, and only marks the step done once both have landed. A home with no
 * profile yet gets one created, which is what the first save of this step means.
 */
class SecurityContactViewModel : ViewModel() {
  private val _uiState = MutableStateFlow(SecurityContactUiState())

  /** Single source of truth for [SecurityContactScreen]. */
  val uiState = _uiState.asStateFlow()

  /** The running resend countdown, cancelled whenever a fresh code goes out. */
  private var resendJob: Job? = null

  init {
    load()
  }

  /** Reads the states first, so the profile's state code can be resolved to a name on arrival. */
  fun load() {
    val spaceId = SessionStore.spaceId
    if (spaceId.isEmpty()) {
      _uiState.update { it.copy(loading = false) }
      return
    }
    _uiState.update { it.copy(loading = true, error = null, timeZonesFailed = false) }
    viewModelScope.launch {
      loadStates(spaceId)
      loadTimeZones(spaceId)
      loadProfile(spaceId)
      applyDeviceTimeZone()
      _uiState.update { it.copy(loading = false) }
    }
  }

  /**
   * Fetches the zone list again after it failed to load. It deliberately does not re-run [load],
   * which would overwrite an address the user has already typed with the saved one.
   */
  fun retryTimeZones() {
    val spaceId = SessionStore.spaceId
    if (spaceId.isEmpty()) return
    _uiState.update { it.copy(busy = true, error = null) }
    viewModelScope.launch {
      loadTimeZones(spaceId)
      applyDeviceTimeZone()
      _uiState.update { it.copy(busy = false) }
    }
  }

  /** Records what the user typed into the street address field. */
  fun onStreetChange(value: String) {
    _uiState.update { it.copy(street = value) }
  }

  /** Records what the user typed into the cross street field. */
  fun onCrossStreetChange(value: String) {
    _uiState.update { it.copy(crossStreet = value) }
  }

  /** Records what the user typed into the city field. */
  fun onCityChange(value: String) {
    _uiState.update { it.copy(city = value) }
  }

  /** Records the state picked by name, storing the code the request has to carry instead. */
  fun onStateChange(name: String) {
    val code = _uiState.value.states.firstOrNull { it.name == name }?.code.orEmpty()
    _uiState.update { it.copy(stateCode = code) }
  }

  /** Records what the user typed into the ZIP field. */
  fun onZipChange(value: String) {
    _uiState.update { it.copy(zip = value) }
  }

  /** Records the zone picked by name, storing the code the request has to carry instead. */
  fun onTimeZoneChange(name: String) {
    val code = _uiState.value.timeZones.firstOrNull { it.name == name }?.code.orEmpty()
    _uiState.update { it.copy(timeZoneCode = code) }
  }

  /** Records the number, dropping anything that is not a digit and stopping at [PHONE_LENGTH]. */
  fun onPhoneChange(value: String) {
    if (!value.matches(DIGITS) || value.length > PHONE_LENGTH) return
    _uiState.update { it.copy(phone = value) }
  }

  /** Records the code, dropping anything that is not a digit and stopping at [OTP_LENGTH]. */
  fun onOtpChange(value: String) {
    if (!value.matches(DIGITS) || value.length > OTP_LENGTH) return
    _uiState.update { it.copy(otp = value) }
  }

  /**
   * Runs whichever request the panel on screen is waiting on. [markStep] is false when the screen
   * was opened from security settings rather than from the checklist, which is the one difference
   * between editing the address afterwards and collecting it during setup.
   */
  fun onAdvance(markStep: Boolean = true) {
    when (_uiState.value.stage) {
      ContactStage.Address -> submit()
      ContactStage.Phone -> sendCode()
      ContactStage.Verify -> verifyCode(markStep = markStep)
    }
  }

  /**
   * Steps back one panel, reporting whether it consumed the gesture. A false answer means the
   * screen itself should close, which is what the address panel's back does.
   */
  fun back(): Boolean {
    val previous = when (_uiState.value.stage) {
      ContactStage.Address -> return false
      ContactStage.Phone -> ContactStage.Address
      ContactStage.Verify -> ContactStage.Phone
    }
    resendJob?.cancel()
    _uiState.update { it.copy(stage = previous, error = null) }
    return true
  }

  /**
   * Saves the address and moves on to the number. The profile is created rather than updated when
   * the home has never had one, which is how the production app opens this flow too.
   */
  fun submit() {
    val spaceId = SessionStore.spaceId
    val state = _uiState.value
    if (spaceId.isEmpty() || !state.canSubmit) return
    _uiState.update { it.copy(busy = true, error = null) }
    viewModelScope.launch {
      val request = SecurityProfileRequest(
        address = SecurityAddress(
          city = state.city.trim(),
          country = COUNTRY_CODE,
          state = state.stateCode,
          crossStreet = state.crossStreet.trim().takeIf { it.isNotEmpty() },
          lineOne = state.street.trim(),
          zipCode = state.zip.trim(),
        ),
        timezone = state.timeZoneCode,
      )
      val result = if (state.hasProfile) update(spaceId, request) else create(spaceId, request)
      result
        .onSuccess {
          _uiState.update {
            it.copy(busy = false, hasProfile = true, stage = ContactStage.Phone)
          }
        }
        .onFailure { error ->
          _uiState.update { it.copy(busy = false, error = error.userMessage()) }
        }
    }
  }

  /**
   * Texts a code to the number. A number already on the profile is texted a fresh code like any
   * other: the production app re-verifies unconditionally, and the number the monitoring centre
   * dispatches against is only worth what the last code proved.
   */
  fun sendCode() {
    val spaceId = SessionStore.spaceId
    val state = _uiState.value
    if (spaceId.isEmpty() || !state.canSendCode) return
    requestOtp(spaceId, state.phone)
  }

  /** Texts a fresh code for the same number once the countdown has run out. */
  fun resendCode() {
    val spaceId = SessionStore.spaceId
    val state = _uiState.value
    if (spaceId.isEmpty() || !state.canResend) return
    requestOtp(spaceId, state.phone)
  }

  /** Attaches the verified number to the profile, then marks the step done when [markStep]. */
  fun verifyCode(markStep: Boolean = true) {
    val spaceId = SessionStore.spaceId
    val state = _uiState.value
    if (spaceId.isEmpty() || !state.canVerify) return
    _uiState.update { it.copy(busy = true, error = null) }
    viewModelScope.launch {
      update(
        spaceId,
        SecurityProfileRequest(
          phoneNumber = SecurityPhoneNumber(
            code = PHONE_COUNTRY_CODE,
            value = state.phone,
            otp = state.otp,
          ),
        ),
      )
        .onSuccess {
          resendJob?.cancel()
          if (markStep) {
            markStepComplete(spaceId)
          } else {
            _uiState.update { it.copy(busy = false, done = true) }
          }
        }
        .onFailure { error ->
          _uiState.update { it.copy(busy = false, error = error.userMessage()) }
        }
    }
  }

  /** Texts a code to [phone] and opens the verify panel, clearing any code typed before. */
  private fun requestOtp(spaceId: String, phone: String) {
    _uiState.update { it.copy(busy = true, error = null, otp = "") }
    viewModelScope.launch {
      sdkCall<Unit> { onSuccess, onError ->
        InstaVision.securityServices.sendOtp(
          spaceId = spaceId,
          request = SecurityPhoneNumber(code = PHONE_COUNTRY_CODE, value = phone),
          onSuccess = onSuccess,
          onError = onError,
        )
      }
        .onSuccess {
          _uiState.update { it.copy(busy = false, stage = ContactStage.Verify) }
          startResendCountdown()
        }
        .onFailure { error ->
          _uiState.update { it.copy(busy = false, error = error.userMessage()) }
        }
    }
  }

  /** Fills the state dropdown; a failure only costs the dropdown its options. */
  private suspend fun loadStates(spaceId: String) {
    sdkCall<List<State>> { onSuccess, onError ->
      InstaVision.securityServices.getUsaStates(
        spaceId = spaceId,
        onSuccess = onSuccess,
        onError = onError,
      )
    }
      .onSuccess { states -> _uiState.update { it.copy(states = states) } }
      .onFailure { error -> _uiState.update { it.copy(error = error.userMessage()) } }
  }

  /**
   * Fills the zone dropdown. Losing this list blocks the step outright rather than defaulting to
   * a zone, because a wrong zone silently misdates every dispatch made against the address.
   */
  private suspend fun loadTimeZones(spaceId: String) {
    sdkCall<List<TimeZone>> { onSuccess, onError ->
      InstaVision.securityServices.getUsaTimeZones(
        spaceId = spaceId,
        onSuccess = onSuccess,
        onError = onError,
      )
    }
      .onSuccess { zones ->
        _uiState.update { it.copy(timeZones = zones, timeZonesFailed = zones.isEmpty()) }
      }
      .onFailure { error ->
        _uiState.update {
          it.copy(timeZones = emptyList(), timeZonesFailed = true, error = error.userMessage())
        }
      }
  }

  /**
   * Pre-selects the device's own zone, but only when the backend actually offers it. A device on
   * UTC or abroad is left unselected so the user has to say where the home is.
   */
  private fun applyDeviceTimeZone() {
    val state = _uiState.value
    if (state.timeZoneCode.isNotBlank() || state.timeZones.isEmpty()) return
    val deviceZone = java.util.TimeZone.getDefault().id
    val match = state.timeZones.firstOrNull { it.code.equals(deviceZone, ignoreCase = true) }
      ?: state.timeZones.firstOrNull { it.name.equals(deviceZone, ignoreCase = true) }
    if (match != null) _uiState.update { it.copy(timeZoneCode = match.code) }
  }

  /** Counts the resend link down from [RESEND_SECONDS], replacing any countdown still running. */
  private fun startResendCountdown() {
    resendJob?.cancel()
    resendJob = viewModelScope.launch {
      for (second in RESEND_SECONDS downTo 1) {
        _uiState.update { it.copy(resendIn = second) }
        delay(SECOND_MILLIS)
      }
      _uiState.update { it.copy(resendIn = 0) }
    }
  }

  /**
   * Marks the contact step done once the address and number have both been accepted. The
   * production app keeps this as its own call, and sending `setup_step` alongside the address is
   * what the backend rejects.
   */
  private suspend fun markStepComplete(spaceId: String) {
    update(spaceId, SecurityProfileRequest(setupStep = SecuritySteps.ContactInformation.apiName))
      .onSuccess { _uiState.update { it.copy(busy = false, done = true) } }
      .onFailure { error ->
        _uiState.update { it.copy(busy = false, error = error.userMessage()) }
      }
  }

  /** Pre-fills the form from an existing profile; a home without one starts on a blank form. */
  private suspend fun loadProfile(spaceId: String) {
    fetchSecurityProfile(spaceId)
      .onSuccess { profile -> if (profile != null) prefill(profile) }
      .onFailure { error -> _uiState.update { it.copy(error = error.userMessage()) } }
  }

  /**
   * Copies a saved address and number back in so the user edits them rather than retypes them.
   * A pre-filled number still has to be verified by a fresh code; it is offered to save typing,
   * never to skip the check. The number is read through a nullable binding because a profile
   * created by the address save alone comes back without one, whatever the SDK's type says.
   */
  private fun prefill(profile: SecurityProfileResponse) {
    val saved: SecurityPhoneNumber? = profile.phoneNumber
    val phone = saved?.value?.filter { it.isDigit() }?.takeLast(PHONE_LENGTH).orEmpty()
    _uiState.update {
      it.copy(
        street = profile.address.lineOne.orEmpty(),
        crossStreet = profile.address.crossStreet.orEmpty(),
        city = profile.address.city,
        stateCode = profile.address.state,
        zip = profile.address.zipCode.orEmpty(),
        timeZoneCode = profile.timezone.orEmpty(),
        phone = phone,
        hasProfile = true,
      )
    }
  }

  /** Writes the address onto a profile the home already has. */
  private suspend fun update(spaceId: String, request: SecurityProfileRequest): Result<*> =
    sdkCall<SecurityProfileResponse> { onSuccess, onError ->
      InstaVision.securityServices.updateProfile(
        spaceId = spaceId,
        request = request,
        onSuccess = onSuccess,
        onError = onError,
      )
    }

  /** Opens monitoring on a home that has no profile at all. */
  private suspend fun create(spaceId: String, request: SecurityProfileRequest): Result<*> =
    sdkCall<Unit> { onSuccess, onError ->
      InstaVision.securityServices.createProfile(
        spaceId = spaceId,
        request = request,
        onSuccess = onSuccess,
        onError = onError,
      )
    }
}
