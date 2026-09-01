package ai.instavision.sandbox.ui.security

import ai.instavision.sandbox.data.SessionStore
import ai.instavision.sandbox.ui.common.SdkException
import ai.instavision.sandbox.ui.common.sdkCall
import ai.instavision.sandbox.ui.common.userMessage
import ai.instavision.guardian.sdk.InstaVision
import ai.instavision.guardian.sdk.data.entity.SecurityLog
import ai.instavision.guardian.sdk.data.entity.Device
import ai.instavision.guardian.sdk.data.entity.SecurityDevice
import ai.instavision.guardian.sdk.data.entity.Space
import ai.instavision.guardian.sdk.data.entity.response.SecurityProfileResponse
import ai.instavision.network.data.enums.ErrorCode
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** The `role` a space carries for the account that owns it; production's `Role.OWNER`. */
private const val ROLE_OWNER = "Owner"

/** Feature id every monitoring plan carries, which is what makes a plan a security plan. */
private const val MONITORING_FEATURE = "monitoring"

/** Delay the settle poll assumes when the profile has never had an exit delay written to it. */
private const val DEFAULT_EXIT_DELAY_SECONDS = 60

/** Gap between two profile reads while an arm or disarm settles; production's `POLLING_DURATION`. */
private val PollInterval = 3.seconds

/** Slack past the home's exit delay before a settle is called failed, worth two more reads. */
private val PollGrace = PollInterval * 2

/** Said when the backend never reported the alarm live within the home's own exit delay. */
private const val ARM_FAILED =
  "The system did not finish arming. Check the cameras are online, then try again."

/** The counterpart of [ARM_FAILED] for a stand-down that never landed. */
private const val DISARM_FAILED = "The system did not finish disarming. Try again."

/** Whether the home's plan covers professional monitoring; production's `hasSecurityPlan`. */
internal fun Space.hasMonitoringPlan(): Boolean =
  subscriptionsMetaData.orEmpty()
    .any { plan -> plan.supportedFeatures.any { it.id == MONITORING_FEATURE } }

/** Whether the signed-in account owns this home, which is who setup and the upsell are for. */
internal fun Space.isOwner(): Boolean = role == ROLE_OWNER

/**
 * Whether the backend has finished moving the whole system to [status]. A camera that reports
 * `Failed` counts as settled, because it will never move again without a fresh request; this is
 * production's `shouldStopPolling` written for one profile rather than a pair of state flows.
 */
private fun SecurityProfileResponse.hasSettledOn(status: String): Boolean =
  this.status == status && deviceList.all { it.state == status || it.state == SecurityStatus.FAILED }

/**
 * Reads the monitoring profile of [spaceId], turning the SDK's `SecurityProfile_NotFound` failure
 * into a successful null. A home that has never started setup has no profile at all, which is a
 * state to render rather than an error to report; every other failure is passed straight through.
 */
internal suspend fun fetchSecurityProfile(spaceId: String): Result<SecurityProfileResponse?> {
  val result = sdkCall<SecurityProfileResponse> { onSuccess, onError ->
    InstaVision.securityServices.getProfile(
      spaceId = spaceId,
      onSuccess = onSuccess,
      onError = onError,
    )
  }
  val failure = result.exceptionOrNull() ?: return Result.success(result.getOrNull())
  val notFound = failure is SdkException &&
    failure.error.code == ErrorCode.SECURITY_PROFILE_NOT_FOUND.value
  return if (notFound) Result.success(null) else Result.failure(failure)
}

/** Everything the Security tab renders for the selected home. */
data class SecurityUiState(
  /** The home's monitoring profile; null when the home has never started setup. */
  val profile: SecurityProfileResponse? = null,
  /** Whether the alarm is live, as last read from the profile's `status`. */
  val armed: Boolean = false,
  /** True once the checklist has been walked to its terminal `Completed` step. */
  val setupComplete: Boolean = false,
  /** Whether any checklist step has been finished, which picks the setup prompt's wording. */
  val started: Boolean = false,
  /** Whether the home's plan covers monitoring at all; assumed until the space has been read. */
  val entitled: Boolean = true,
  /** Whether the account owns this home; assumed until the space has been read. */
  val isOwner: Boolean = true,
  /** Whether the home has a camera monitoring can arm, which is what setup needs to begin. */
  val canSetup: Boolean = false,
  /** True until the first profile read settles. */
  val loading: Boolean = true,
  /** True while an arm or disarm is in flight, including the wait for it to settle. */
  val busy: Boolean = false,
  /** The newest log entries, read until [MaxRecentSessions] arming sessions close among them. */
  val logs: List<SecurityLog> = emptyList(),
  /** True while the first page of the log is in flight, which is only ever read once. */
  val logsLoading: Boolean = false,
  /** Message from the last failed request; a missing profile never sets this. */
  val error: String? = null,
) {
  /** The profile's own status word, which is what the dial reads; a home with none is stood down. */
  val status: String get() = profile?.status ?: SecurityStatus.DISARMED

  /** The cameras monitoring covers, as the profile lists them; empty for a home without one. */
  val cameras: List<SecurityDevice> get() = profile?.deviceList.orEmpty()
}

/**
 * Backs the Security tab: reads the monitoring profile of [SessionStore.selectedSpace] and, once
 * setup is finished, arms and disarms it. Armed-ness comes from the profile's `status` string, the
 * only field the production app reads for it.
 */
class SecurityViewModel : ViewModel() {
  private val _uiState = MutableStateFlow(SecurityUiState())
  private var hasResumed = false

  /** Single source of truth for [SecurityScreen]. */
  val uiState = _uiState.asStateFlow()

  init {
    load()
  }

  /**
   * Refetches what the tab renders, leaving whatever is already on screen in place while it runs.
   * The profile is only asked for when the home is entitled to monitoring and the account is
   * allowed to see it, which is the same gate the production app's data source applies.
   */
  fun load() {
    val space = SessionStore.selectedSpace
    if (space == null) {
      _uiState.update { it.copy(loading = false) }
      return
    }
    val entitled = space.hasMonitoringPlan()
    val isOwner = space.isOwner()
    _uiState.update {
      it.copy(
        entitled = entitled,
        isOwner = isOwner,
        loading = it.profile == null,
        error = null,
      )
    }
    viewModelScope.launch {
      if (!entitled || (space.monitoring == null && !isOwner)) {
        publish(null)
        return@launch
      }
      if (isOwner) loadCameras(space.id)
      fetchSecurityProfile(space.id)
        .onSuccess { profile ->
          publish(profile)
          if (_uiState.value.setupComplete) loadRecentLogs(space.id)
        }
        .onFailure { error ->
          _uiState.update { it.copy(loading = false, error = error.userMessage()) }
        }
    }
  }

  /**
   * Refetches when the tab comes back to the foreground, skipping the first resume because [init]
   * has already read the profile. This is what brings a step finished in setup back to the tab.
   */
  fun refreshOnResume() {
    if (!hasResumed) {
      hasResumed = true
      return
    }
    load()
  }

  /** Makes the alarm live, then waits for the backend to report every camera armed. */
  fun arm() {
    submit(status = SecurityStatus.ARMED) { spaceId ->
      sdkCall<Unit> { onSuccess, onError ->
        InstaVision.securityServices.armSystem(
          spaceId = spaceId,
          onSuccess = onSuccess,
          onError = onError,
        )
      }
    }
  }

  /** Stands the alarm down; the counterpart of [arm], settled the same way. */
  fun disarm() {
    submit(status = SecurityStatus.DISARMED) { spaceId ->
      sdkCall<Unit> { onSuccess, onError ->
        InstaVision.securityServices.disarmSystem(
          spaceId = spaceId,
          onSuccess = onSuccess,
          onError = onError,
        )
      }
    }
  }

  /**
   * Records whether the home has a camera monitoring can arm, fetching the device list when the
   * tab is the first screen of the session to need it. A failed fetch only costs the setup gate.
   */
  private suspend fun loadCameras(spaceId: String) {
    val cached = SessionStore.devices
    val devices = if (cached.isNotEmpty()) {
      cached
    } else {
      sdkCall<List<Device>> { onSuccess, onError ->
        InstaVision.deviceServices.getDevices(
          spaceId = spaceId,
          onSuccess = onSuccess,
          onError = onError,
        )
      }
        .onSuccess { fetched -> SessionStore.putDevices(fetched) }
        .getOrDefault(emptyList())
    }
    _uiState.update { state ->
      state.copy(canSetup = devices.any { it.supportsProSecurity() })
    }
  }

  /**
   * Reads the space's security log until it holds enough entries for the [MaxRecentSessions]
   * sessions the tab previews.
   *
   * A session only closes on an `Armed` entry, so a page can hold none at all — a home armed once
   * a fortnight puts a whole page between two of them. Reading a single page would leave the tab
   * emptier than the log really is, so pages are read until enough sessions close, the log runs
   * out, or [MaxRecentPages] have been read. That cap is what stops a quiet home walking its whole
   * retention window to fill three cards.
   *
   * Grouping runs over the accumulated entries rather than page by page, because a session that
   * straddles a page boundary is only whole once both halves are in hand. The sessions themselves
   * are never published: they only bound the read, and the tab renders the raw entries flat.
   */
  private suspend fun loadRecentLogs(spaceId: String) {
    _uiState.update { it.copy(logsLoading = it.logs.isEmpty()) }
    val entries = mutableListOf<SecurityLog>()
    var sessions = emptyList<SecuritySession>()
    var page = 0
    while (page < MaxRecentPages && sessions.size < MaxRecentSessions) {
      val response = fetchSecurityLogs(spaceId = spaceId, skip = entries.size.toLong())
        .getOrElse { error ->
          _uiState.update { it.copy(logsLoading = false, error = error.userMessage()) }
          return
        }
      if (response.logs.isEmpty()) break
      entries += response.logs
      sessions = groupSecuritySessions(entries)
      page += 1
    }
    _uiState.update { it.copy(logs = entries.toList(), logsLoading = false) }
  }

  /** Folds a freshly read profile into the state; a null profile means setup was never started. */
  private fun publish(profile: SecurityProfileResponse?) {
    _uiState.update {
      it.copy(
        profile = profile,
        armed = profile?.status == SecurityStatus.ARMED || profile?.status == SecurityStatus.ARMING,
        setupComplete = profile?.setupStep == SecuritySteps.Completed.apiName,
        started = profile?.completedSteps.orEmpty().isNotEmpty(),
        loading = false,
      )
    }
  }

  /**
   * Runs one arm or disarm and holds the controls until the backend agrees it happened. Nothing is
   * shown optimistically: the card only changes once a profile read reports [status], and a wait
   * that runs out says so rather than leaving the tab claiming a state the system never reached.
   */
  private fun submit(status: String, block: suspend (String) -> Result<*>) {
    val spaceId = SessionStore.spaceId
    if (spaceId.isEmpty()) return
    _uiState.update { it.copy(busy = true, error = null) }
    viewModelScope.launch {
      block(spaceId)
        .onSuccess {
          val settled = awaitSettle(spaceId = spaceId, status = status)
          _uiState.update {
            it.copy(busy = false, error = if (settled) null else settleFailure(status))
          }
        }
        .onFailure { error ->
          _uiState.update { it.copy(busy = false, error = error.userMessage()) }
        }
    }
  }

  /**
   * Rereads the profile every [PollInterval] until the system and every camera report [status],
   * publishing each read so the tab tracks the transition. Gives up after the home's own exit
   * delay plus [PollGrace] and returns whether the state was reached.
   */
  private suspend fun awaitSettle(spaceId: String, status: String): Boolean {
    val exitDelay = _uiState.value.profile?.exitDelay?.takeIf { it > 0 }
      ?: DEFAULT_EXIT_DELAY_SECONDS
    return withTimeoutOrNull(exitDelay.seconds + PollGrace) {
      var settled = false
      while (!settled) {
        delay(PollInterval)
        val profile = fetchSecurityProfile(spaceId).getOrNull() ?: break
        publish(profile)
        settled = profile.hasSettledOn(status)
      }
      settled
    } == true
  }

  /** What to tell the user when the system never reached the state they asked for. */
  private fun settleFailure(status: String): String =
    if (status == SecurityStatus.ARMED) ARM_FAILED else DISARM_FAILED
}
