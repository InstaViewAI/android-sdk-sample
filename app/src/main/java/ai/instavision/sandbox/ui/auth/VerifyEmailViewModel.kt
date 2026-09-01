package ai.instavision.sandbox.ui.auth

import ai.instavision.guardian.sdk.InstaVision
import ai.instavision.guardian.sdk.data.entity.User
import ai.instavision.sandbox.data.SessionStore
import ai.instavision.sandbox.ui.common.sdkCall
import ai.instavision.sandbox.ui.common.userMessage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How long the screen waits between two `getUser` polls while the link is still unfollowed. */
private const val POLL_INTERVAL_MILLIS = 3_000L

/** How long the visitor has to wait before another verification mail may be requested. */
private const val RESEND_DELAY_SECONDS = 60

/** One tick of the resend countdown. */
private const val COUNTDOWN_TICK_MILLIS = 1_000L

/** Confirmation shown after the SDK accepts a request for a fresh verification mail. */
private const val RESENT_MESSAGE = "Verification email sent. Check your inbox and spam folder."

/** Shown when the device has nothing registered to handle mail, so "Open Mail" resolves to nobody. */
private const val NO_EMAIL_APP_MESSAGE = "No email app is set up on this device."

/** Everything [VerifyEmailScreen] draws. */
data class VerifyEmailUiState(
  /** Address the verification link was sent to, taken from the cached session user. */
  val email: String = "",
  /** Seconds left before another verification mail may be requested; zero once it may. */
  val resendSeconds: Int = RESEND_DELAY_SECONDS,
  /** True while a resend request is in flight, which locks the secondary button. */
  val resending: Boolean = false,
  /** Informational feedback such as "email sent", or null when there is nothing to say. */
  val message: String? = null,
  /** Banner-level failure, or null when there is nothing to report. */
  val error: String? = null,
  /** True once the backend vouches for the address, which raises the confirmation dialog. */
  val verified: Boolean = false,
) {
  /** Whether the secondary button is live, which it only is once the countdown has run out. */
  val canResend: Boolean get() = resendSeconds == 0 && !resending

  /** Label on the secondary button: the countdown while it runs, the action once it ends. */
  val resendLabel: String
    get() = if (resendSeconds > 0) "Resend in ${resendSeconds}s" else "Resend email"
}

/**
 * Drives the "confirm your email" waiting room. It polls the account every [POLL_INTERVAL_MILLIS]
 * until the backend reports the address as verified, and runs the countdown that holds the resend
 * button shut. Both loops live in `viewModelScope`, so leaving the screen ends them.
 */
class VerifyEmailViewModel : ViewModel() {
  /** Mutable backing state, only ever updated from this ViewModel. */
  private val _state = MutableStateFlow(
    VerifyEmailUiState(email = SessionStore.user?.email.orEmpty()),
  )

  /** State the screen collects with `collectAsStateWithLifecycle`. */
  val state: StateFlow<VerifyEmailUiState> = _state.asStateFlow()

  /** The running countdown, cancelled and replaced every time a fresh mail goes out. */
  private var countdownJob: Job? = null

  init {
    pollUntilVerified()
    restartCountdown()
  }

  /**
   * Asks the backend to send another verification mail and restarts the countdown once it agrees.
   * A refusal leaves the countdown at zero, so the visitor can immediately try again.
   */
  fun resend() {
    if (!_state.value.canResend) return
    viewModelScope.launch {
      _state.update { it.copy(resending = true, error = null, message = null) }
      sdkCall<Unit> { onSuccess, onError ->
        InstaVision.userServices.sendVerificationEmail(
          onSuccess = { onSuccess(Unit) },
          onError = onError,
        )
      }
        .onSuccess {
          _state.update { current -> current.copy(resending = false, message = RESENT_MESSAGE) }
          restartCountdown()
        }
        .onFailure { failure ->
          _state.update { it.copy(resending = false, error = failure.userMessage()) }
        }
    }
  }

  /** Reports that no email app answered the "Open Mail" intent, rather than letting it throw. */
  fun onEmailAppMissing() {
    _state.update { it.copy(error = NO_EMAIL_APP_MESSAGE) }
  }

  /**
   * Ends the half-finished session so the visitor can start again with a different address. The
   * SDK's logout is synchronous and reports nothing, so the caller may navigate straight after.
   */
  fun useDifferentAccount() {
    InstaVision.userServices.logout(pushToken = "")
    SessionStore.clear()
  }

  /**
   * Re-reads the account on a fixed interval until the address comes back verified. A failed poll
   * is swallowed on purpose: a flaky network must not strand the visitor on a screen that has
   * stopped watching for the link they are about to follow.
   */
  private fun pollUntilVerified() {
    viewModelScope.launch {
      while (!_state.value.verified) {
        delay(POLL_INTERVAL_MILLIS)
        sdkCall<User> { onSuccess, onError ->
          InstaVision.userServices.getUser(onSuccess = onSuccess, onError = onError)
        }.onSuccess { user ->
          SessionStore.putUser(user)
          _state.update {
            it.copy(email = user.email, verified = user.emailVerified == true)
          }
        }
      }
    }
  }

  /** Puts the resend countdown back to [RESEND_DELAY_SECONDS] and ticks it down to zero. */
  private fun restartCountdown() {
    countdownJob?.cancel()
    countdownJob = viewModelScope.launch {
      _state.update { it.copy(resendSeconds = RESEND_DELAY_SECONDS) }
      while (_state.value.resendSeconds > 0) {
        delay(COUNTDOWN_TICK_MILLIS)
        _state.update { it.copy(resendSeconds = it.resendSeconds - 1) }
      }
    }
  }
}
