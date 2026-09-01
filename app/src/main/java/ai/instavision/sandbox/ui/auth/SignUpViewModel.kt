package ai.instavision.sandbox.ui.auth

import ai.instavision.sandbox.data.SessionStore
import ai.instavision.sandbox.ui.common.sdkCall
import ai.instavision.sandbox.ui.common.userMessage
import ai.instavision.guardian.sdk.InstaVision
import ai.instavision.guardian.sdk.data.entity.Name
import ai.instavision.guardian.sdk.data.entity.PhoneNumber
import ai.instavision.guardian.sdk.data.entity.User
import ai.instavision.guardian.sdk.data.entity.request.CreateUserRequest
import ai.instavision.guardian.sdk.data.entity.request.UpdateUserRequest
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Inline message for a password that fails one of [PasswordChecklist]'s rules, which lists them. */
private const val WEAK_PASSWORD_MESSAGE = "Password does not meet the requirements"

/** Loose shape check for e-mail input; the backend stays the authority on deliverability. */
private val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")

/** Language every account is created with; the sample deliberately offers no choice of language. */
private const val ACCOUNT_LANGUAGE = "en"

/** Dialling code every account is created with; the sample serves the US only. */
private const val PHONE_COUNTRY_CODE = "+1"

/** Everything [SignUpScreen] draws, including per-field validation messages. */
data class SignUpUiState(
  /** Given name the account is created under; required. */
  val firstName: String = "",
  /** Family name the account is created under; required. */
  val lastName: String = "",
  /** E-mail the new account will be created with. */
  val email: String = "",
  /** Optional phone number, sent with the profile update when it is not blank. */
  val phone: String = "",
  /** Chosen password, kept in memory only for the duration of the screen. */
  val password: String = "",
  /** Repeat of [password]; sign-up is refused while the two differ. */
  val confirmPassword: String = "",
  /** Inline message under the e-mail field, or null when the value looks valid. */
  val emailError: String? = null,
  /** Inline message under the password field, or null when the value looks valid. */
  val passwordError: String? = null,
  /** Inline message under the confirmation field, or null when the two passwords match. */
  val confirmPasswordError: String? = null,
  /** True while the account-creation request is in flight; drives the primary button's spinner. */
  val loading: Boolean = false,
  /** True while Credential Manager's sheet and the Google exchange behind it are up. */
  val googlePending: Boolean = false,
  /** Banner-level failure from the SDK, or null when there is nothing to report. */
  val error: String? = null,
  /** Terminal flag the screen consumes once to navigate on to e-mail verification. */
  val signedUp: Boolean = false,
) {
  /** True while either path is busy, which is when every control on the screen locks. */
  val busy: Boolean get() = loading || googlePending

  /**
   * True once every required field is filled in and the password meets every rule the checklist
   * lists, which is what the primary button is gated on. The phone number is deliberately absent —
   * it is optional and must not hold the form back — and the flag stays true while [loading] so the
   * button can keep showing its spinner.
   */
  val canSubmit: Boolean
    get() = !googlePending &&
      firstName.isNotBlank() &&
      lastName.isNotBlank() &&
      email.isNotBlank() &&
      passwordSatisfiesRules(password = password) &&
      confirmPassword.isNotBlank()

  /** The phone number to send with the profile update, or null while the optional field is blank. */
  fun phoneNumber(): PhoneNumber? = phone.trim()
    .takeIf { it.isNotBlank() }
    ?.let { PhoneNumber(code = PHONE_COUNTRY_CODE, value = it) }
}

/**
 * Validates the sign-up form locally and creates the account through
 * `InstaVision.userServices.signup`. On success the new [User] is cached in [SessionStore] so the
 * verification screen can address the visitor by e-mail.
 */
class SignUpViewModel : ViewModel() {
  /** Mutable backing state, only ever updated from this ViewModel. */
  private val _state = MutableStateFlow(SignUpUiState())

  /** State the screen collects with `collectAsStateWithLifecycle`. */
  val state: StateFlow<SignUpUiState> = _state.asStateFlow()

  /** Records given-name edits and clears the previous failure. */
  fun onFirstNameChange(value: String) {
    _state.update { it.copy(firstName = value, error = null) }
  }

  /** Records family-name edits and clears the previous failure. */
  fun onLastNameChange(value: String) {
    _state.update { it.copy(lastName = value, error = null) }
  }

  /** Records e-mail edits and clears any stale validation message for that field. */
  fun onEmailChange(value: String) {
    _state.update { it.copy(email = value, emailError = null, error = null) }
  }

  /** Records phone-number edits; the field is optional, so there is nothing to validate. */
  fun onPhoneChange(value: String) {
    _state.update { it.copy(phone = value, error = null) }
  }

  /** Records password edits and clears any stale validation message for that field. */
  fun onPasswordChange(value: String) {
    _state.update { it.copy(password = value, passwordError = null, error = null) }
  }

  /** Records confirmation edits and clears the mismatch message. */
  fun onConfirmPasswordChange(value: String) {
    _state.update { it.copy(confirmPassword = value, confirmPasswordError = null, error = null) }
  }

  /**
   * Validates the form and, only when every field passes, creates the account and then attaches the
   * name and phone number to it. Invalid input never reaches the SDK — it is reported inline.
   */
  fun signUp() {
    if (!validate()) return
    viewModelScope.launch {
      _state.update { it.copy(loading = true, error = null) }
      val form = _state.value
      sdkCall<User> { onSuccess, onError ->
        InstaVision.userServices.signup(
          request = CreateUserRequest(
            email = form.email.trim(),
            language = ACCOUNT_LANGUAGE,
            password = form.password,
          ),
          onSuccess = onSuccess,
          onError = onError,
        )
      }
        .onSuccess { user ->
          SessionStore.putUser(user)
          attachProfile(form = form)
          _state.update { it.copy(loading = false, signedUp = true) }
        }
        .onFailure { failure ->
          _state.update { it.copy(loading = false, error = failure.userMessage()) }
        }
    }
  }

  /**
   * Asks Credential Manager for a Google ID token and exchanges it for a session, which creates the
   * account on first use. [context] has to be the Activity, since the account sheet is drawn over
   * it. Google returns a verified address, so this path lands straight in the signed-in app.
   */
  fun signUpWithGoogle(context: Context) {
    viewModelScope.launch {
      _state.update { it.copy(googlePending = true, error = null) }
      requestGoogleIdToken(context = context)
        .onSuccess { idToken -> exchangeGoogleToken(idToken = idToken) }
        .onFailure { failure -> releaseGoogle(failure = failure) }
    }
  }

  /** Clears the terminal flag after the screen has navigated away, so it cannot fire twice. */
  fun onNavigationHandled() {
    _state.update { it.copy(signedUp = false) }
  }

  /**
   * Adds the name and optional phone number that `CreateUserRequest` has no room for. A failure
   * here is deliberately not surfaced and never blocks navigation: the account already exists at
   * this point, and My Account can set both later, so refusing to move on would strand the visitor
   * on a form that can no longer be submitted.
   */
  private suspend fun attachProfile(form: SignUpUiState) {
    sdkCall<User> { onSuccess, onError ->
      InstaVision.userServices.updateUser(
        request = UpdateUserRequest(
          name = Name(first = form.firstName.trim(), last = form.lastName.trim()),
          phone = form.phoneNumber(),
        ),
        onSuccess = onSuccess,
        onError = onError,
      )
    }.onSuccess { updated -> SessionStore.putUser(updated) }
  }

  /** Trades a Google ID token for a session and caches the resulting [User]. */
  private suspend fun exchangeGoogleToken(idToken: String) {
    sdkCall<User> { onSuccess, onError ->
      InstaVision.userServices.loginWithGoogle(
        idToken = idToken,
        onSuccess = onSuccess,
        onError = onError,
      )
    }
      .onSuccess { user ->
        SessionStore.putUser(user)
        _state.update { it.copy(googlePending = false, signedUp = true) }
      }
      .onFailure { failure ->
        _state.update { it.copy(googlePending = false, error = failure.userMessage()) }
      }
  }

  /**
   * Unlocks the screen after Credential Manager declined to produce a token, reporting everything
   * except a [GoogleSignInCancelled] — backing out of the sheet is a choice, not a fault.
   */
  private fun releaseGoogle(failure: Throwable) {
    _state.update {
      it.copy(
        googlePending = false,
        error = if (failure is GoogleSignInCancelled) null else failure.userMessage(),
      )
    }
  }

  /**
   * Writes an inline message for every field that fails, returning true only when the whole form
   * is acceptable.
   */
  private fun validate(): Boolean {
    val current = _state.value
    val emailError = when {
      current.email.isBlank() -> "Enter your email address"
      !EMAIL_PATTERN.matches(current.email.trim()) -> "That does not look like an email address"
      else -> null
    }
    val passwordError = when {
      !passwordSatisfiesRules(password = current.password) -> WEAK_PASSWORD_MESSAGE
      else -> null
    }
    val confirmPasswordError = when {
      current.confirmPassword != current.password -> "Passwords do not match"
      else -> null
    }
    _state.update {
      it.copy(
        emailError = emailError,
        passwordError = passwordError,
        confirmPasswordError = confirmPasswordError,
      )
    }
    return emailError == null && passwordError == null && confirmPasswordError == null
  }
}
