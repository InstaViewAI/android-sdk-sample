package ai.instavision.sandbox.ui.auth

import ai.instavision.sandbox.data.SessionStore
import ai.instavision.sandbox.ui.common.sdkCall
import ai.instavision.sandbox.ui.common.userMessage
import ai.instavision.guardian.sdk.InstaVision
import ai.instavision.guardian.sdk.data.entity.User
import ai.instavision.network.data.entity.ApiError
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Refused locally so an obviously empty form never costs a network round trip. */
private const val EMPTY_CREDENTIALS_MESSAGE = "Enter your email and password"

/** The two ways into an account, kept apart so the screen can mark the button being waited on. */
enum class SignInMethod {
  /** E-mail and password typed into the form. */
  Password,

  /** Google ID token taken from Credential Manager. */
  Google,
}

/** Everything [SignInScreen] draws. */
data class SignInUiState(
  /** E-mail typed into the password form. */
  val email: String = "",
  /** Password typed into the password form. */
  val password: String = "",
  /** Which sign-in is in flight, or null while the screen is idle. */
  val pending: SignInMethod? = null,
  /** Banner-level failure from the SDK, or null when there is nothing to report. */
  val error: String? = null,
  /** Terminal flag the screen consumes once to move into the signed-in app. */
  val signedIn: Boolean = false,
) {
  /** True while either path is busy, which is when every control on the screen locks. */
  val loading: Boolean get() = pending != null

  /**
   * True once both credentials are filled in, which is what the primary button is gated on. It
   * stays true while the password sign-in is in flight so the button can keep showing its spinner.
   */
  val canSubmit: Boolean
    get() = email.isNotBlank() && password.isNotBlank() && pending != SignInMethod.Google
}

/**
 * Signs an existing account in, either with e-mail and password or with a Google ID token obtained
 * from Credential Manager. The resulting [User] is cached in [SessionStore] for the rest of the app.
 */
class SignInViewModel : ViewModel() {
  /** Mutable backing state, only ever updated from this ViewModel. */
  private val _state = MutableStateFlow(SignInUiState())

  /** State the screen collects with `collectAsStateWithLifecycle`. */
  val state: StateFlow<SignInUiState> = _state.asStateFlow()

  /** Records e-mail edits and clears the previous failure. */
  fun onEmailChange(value: String) {
    _state.update { it.copy(email = value, error = null) }
  }

  /** Records password edits and clears the previous failure. */
  fun onPasswordChange(value: String) {
    _state.update { it.copy(password = value, error = null) }
  }

  /** Signs in with the e-mail and password currently in the form. */
  fun signIn() {
    val current = _state.value
    if (current.email.isBlank() || current.password.isBlank()) {
      _state.update { it.copy(error = EMPTY_CREDENTIALS_MESSAGE) }
      return
    }
    authenticate(method = SignInMethod.Password) { onSuccess, onError ->
      InstaVision.userServices.login(
        email = current.email.trim(),
        password = current.password,
        onSuccess = onSuccess,
        onError = onError,
      )
    }
  }

  /**
   * Asks Credential Manager for a Google ID token and exchanges it for a session. [context] has to
   * be the Activity, since the account sheet is drawn over it.
   */
  fun signInWithGoogle(context: Context) {
    viewModelScope.launch {
      _state.update { it.copy(pending = SignInMethod.Google, error = null) }
      requestGoogleIdToken(context = context)
        .onSuccess { idToken ->
          authenticate(method = SignInMethod.Google) { onSuccess, onError ->
            InstaVision.userServices.loginWithGoogle(
              idToken = idToken,
              onSuccess = onSuccess,
              onError = onError,
            )
          }
        }
        .onFailure { failure -> releaseGoogle(failure = failure) }
    }
  }

  /** Clears the terminal flag after the screen has navigated away, so it cannot fire twice. */
  fun onNavigationHandled() {
    _state.update { it.copy(signedIn = false) }
  }

  /** Shared loading, caching and error handling around the two ways of signing in. */
  private fun authenticate(
    method: SignInMethod,
    block: (onSuccess: (User) -> Unit, onError: (ApiError) -> Unit) -> Unit,
  ) {
    viewModelScope.launch {
      _state.update { it.copy(pending = method, error = null) }
      sdkCall<User>(block)
        .onSuccess { user ->
          SessionStore.putUser(user)
          _state.update { it.copy(pending = null, signedIn = true) }
        }
        .onFailure { failure ->
          _state.update { it.copy(pending = null, error = failure.userMessage()) }
        }
    }
  }

  /**
   * Unlocks the screen after Credential Manager declined to produce a token, reporting everything
   * except a [GoogleSignInCancelled] — backing out of the sheet is a choice, not a fault.
   */
  private fun releaseGoogle(failure: Throwable) {
    _state.update {
      it.copy(
        pending = null,
        error = if (failure is GoogleSignInCancelled) null else failure.userMessage(),
      )
    }
  }
}
