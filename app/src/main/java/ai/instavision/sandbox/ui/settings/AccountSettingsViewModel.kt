package ai.instavision.sandbox.ui.settings

import ai.instavision.sandbox.data.SessionStore
import ai.instavision.sandbox.ui.common.sdkCall
import ai.instavision.sandbox.ui.common.userMessage
import ai.instavision.guardian.sdk.InstaVision
import ai.instavision.guardian.sdk.data.entity.Name
import ai.instavision.guardian.sdk.data.entity.PhoneNumber
import ai.instavision.guardian.sdk.data.entity.User
import ai.instavision.guardian.sdk.data.entity.request.UpdateUserRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Country calling codes the phone field offers, in the order the dropdown lists them. */
private val COUNTRY_CODES: List<String> = listOf("+1", "+44", "+91")

/** Everything the "My account" screen renders for the signed-in user. */
data class AccountSettingsUiState(
  /** The signed-in account as last fetched; null until the first read lands. */
  val user: User? = null,
  /** Editable given name. */
  val firstName: String = "",
  /** Editable family name. */
  val lastName: String = "",
  /** The sign-in address, shown read-only because changing it restarts verification. */
  val email: String = "",
  /** Whether the address on the account has been confirmed. */
  val emailVerified: Boolean = false,
  /** Country calling codes the dropdown offers. */
  val countryCodes: List<String> = COUNTRY_CODES,
  /** Selected country calling code, stored with its leading plus. */
  val countryCode: String = COUNTRY_CODES.first(),
  /** Editable national part of the phone number. */
  val phone: String = "",
  /** True until the profile read finishes. */
  val loading: Boolean = true,
  /** True while a write is in flight, which disables every control on the screen. */
  val busy: Boolean = false,
  /** Message from the last failed request. */
  val error: String? = null,
  /** Confirmation of the last successful write. */
  val notice: String? = null,
  /** Set once the account has been deleted so the screen can leave the tabs. */
  val signedOut: Boolean = false,
)

/**
 * Reads and writes the signed-in account's own details: name, phone number, email verification,
 * and deleting the account outright. Signing out, the region picker and desktop access live on the
 * settings root instead.
 */
class AccountSettingsViewModel : ViewModel() {
  private val _uiState = MutableStateFlow(AccountSettingsUiState())

  /** Single source of truth for [AccountSettingsScreen]. */
  val uiState = _uiState.asStateFlow()

  init {
    load()
  }

  /** Fetches the profile and republishes it into [SessionStore]. */
  fun load() {
    _uiState.update { it.copy(loading = true, error = null) }
    viewModelScope.launch {
      sdkCall<User> { onSuccess, onError ->
        InstaVision.userServices.getUser(onSuccess = onSuccess, onError = onError)
      }
        .onSuccess { user ->
          SessionStore.putUser(user)
          _uiState.update { it.applyUser(user).copy(loading = false) }
        }
        .onFailure { error ->
          _uiState.update { it.copy(loading = false, error = error.userMessage()) }
        }
    }
  }

  /** Records what the user typed into the given name field. */
  fun onFirstNameChange(value: String) {
    _uiState.update { it.copy(firstName = value) }
  }

  /** Records what the user typed into the family name field. */
  fun onLastNameChange(value: String) {
    _uiState.update { it.copy(lastName = value) }
  }

  /** Records the country calling code picked from the dropdown. */
  fun onCountryCodeChange(value: String) {
    _uiState.update { it.copy(countryCode = value) }
  }

  /** Records what the user typed into the phone number field. */
  fun onPhoneChange(value: String) {
    _uiState.update { it.copy(phone = value) }
  }

  /**
   * Saves the edited name and phone number. Only the fields that actually changed are sent; the
   * request treats every property as optional and a null means "leave this one alone".
   */
  fun saveChanges() {
    val state = _uiState.value
    val user = state.user ?: return
    val name = buildName(first = state.firstName, last = state.lastName)
      ?.takeIf { it != user.name }
    val phone = buildPhone(code = state.countryCode, value = state.phone)
      ?.takeIf { it.code != user.phone.code || it.value != user.phone.value }
    if (name == null && phone == null) return
    submit(notice = "Changes saved") {
      sdkCall<User> { onSuccess, onError ->
        InstaVision.userServices.updateUser(
          request = UpdateUserRequest(name = name, phone = phone),
          onSuccess = onSuccess,
          onError = onError,
        )
      }.onSuccess { updated ->
        SessionStore.putUser(updated)
        _uiState.update { it.applyUser(updated) }
      }
    }
  }

  /** Sends the confirmation email again for an account whose address is still unverified. */
  fun resendVerification() {
    submit(notice = "Verification email sent") {
      sdkCall<Unit> { onSuccess, onError ->
        InstaVision.userServices.sendVerificationEmail(
          onSuccess = { onSuccess(Unit) },
          onError = onError,
        )
      }
    }
  }

  /**
   * Deletes the account for good; the caller is expected to have confirmed this with the user
   * first. Signing out afterwards is what clears the SDK's stored token: without it the app would
   * still see a logged-in session on the next launch and open the tabs for an account that is gone.
   */
  fun deleteAccount() {
    submit(notice = "Account deleted") {
      sdkCall<Unit> { onSuccess, onError ->
        InstaVision.userServices.deleteAccount(
          onSuccess = { onSuccess(Unit) },
          onError = onError,
        )
      }.onSuccess {
        InstaVision.userServices.logout(pushToken = "")
        SessionStore.clear()
        _uiState.update { it.copy(signedOut = true) }
      }
    }
  }

  /** Clears the banner left behind by the last request. */
  fun dismissNotice() {
    _uiState.update { it.copy(notice = null, error = null) }
  }

  /** Builds the name to send, or null when both halves are blank and nothing should change. */
  private fun buildName(first: String, last: String): Name? =
    if (first.isBlank() && last.isBlank()) {
      null
    } else {
      Name(first = first.trim(), last = last.trim())
    }

  /** Builds the phone number to send, or null when the national part is blank. */
  private fun buildPhone(code: String, value: String): PhoneNumber? =
    value.trim().takeIf { it.isNotBlank() }?.let { PhoneNumber(code = code, value = it) }

  /**
   * Runs one write with the busy flag raised, applying [onDone] and showing [notice] when the
   * server accepts it.
   */
  private fun submit(
    notice: String,
    onDone: (AccountSettingsUiState) -> AccountSettingsUiState = { it },
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
}

/** Refills the editable fields from a freshly fetched or freshly saved account. */
private fun AccountSettingsUiState.applyUser(user: User): AccountSettingsUiState = copy(
  user = user,
  firstName = user.name.first,
  lastName = user.name.last,
  email = user.email,
  emailVerified = user.emailVerified == true,
  countryCode = user.phone.code.takeIf { it in countryCodes } ?: countryCode,
  phone = user.phone.value,
)
