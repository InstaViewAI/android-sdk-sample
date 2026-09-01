package ai.instavision.sandbox.ui.space

import ai.instavision.guardian.sdk.InstaVision
import ai.instavision.guardian.sdk.data.entity.Address
import ai.instavision.guardian.sdk.data.entity.Space
import ai.instavision.guardian.sdk.data.entity.request.CreateSpaceRequest
import ai.instavision.sandbox.data.SessionStore
import ai.instavision.sandbox.ui.common.sdkCall
import ai.instavision.sandbox.ui.common.userMessage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** ISO code every space address is created with, since the sample is US-only. */
private const val COUNTRY_CODE = "US"

/**
 * Everything [CreateSpaceScreen] draws. Only the name is required: the SDK's [Address] declares its
 * five fields non-null, so an address the visitor skipped is still sent, just with empty strings.
 */
data class CreateSpaceUiState(
  /** Name the space is created with, such as "Beach House". */
  val name: String = "",
  /** Optional street line of the space's address. */
  val street: String = "",
  /** Optional city of the space's address. */
  val city: String = "",
  /** Optional state or province of the space's address; sent to the SDK as `Address.state`. */
  val region: String = "",
  /** Optional postal or ZIP code, kept as text so non-numeric codes survive. */
  val postalCode: String = "",
  /** True while the create request is in flight. */
  val loading: Boolean = false,
  /** Banner-level failure from the SDK, or null when there is nothing to report. */
  val error: String? = null,
  /** Terminal flag the screen consumes once to leave the form after the space is created. */
  val created: Boolean = false,
) {
  /** Whether the form is idle and named, which is the whole of what enables the continue button. */
  val canSubmit: Boolean get() = !loading && name.isNotBlank()
}

/**
 * Collects a space's name and optional address and creates it through `InstaVision.spaceServices`.
 * The new [Space] is appended to [SessionStore] and then selected, so every space-scoped screen
 * that follows operates on it.
 */
class CreateSpaceViewModel : ViewModel() {
  /** Mutable backing state, only ever updated from this ViewModel. */
  private val _state = MutableStateFlow(CreateSpaceUiState())

  /** State the screen collects with `collectAsStateWithLifecycle`. */
  val state: StateFlow<CreateSpaceUiState> = _state.asStateFlow()

  /** Records name edits and clears the previous failure. */
  fun onNameChange(value: String) {
    _state.update { it.copy(name = value, error = null) }
  }

  /** Records street edits and clears the previous failure. */
  fun onStreetChange(value: String) {
    _state.update { it.copy(street = value, error = null) }
  }

  /** Records city edits and clears the previous failure. */
  fun onCityChange(value: String) {
    _state.update { it.copy(city = value, error = null) }
  }

  /** Records state or province edits and clears the previous failure. */
  fun onRegionChange(value: String) {
    _state.update { it.copy(region = value, error = null) }
  }

  /** Records postal code edits and clears the previous failure. */
  fun onPostalCodeChange(value: String) {
    _state.update { it.copy(postalCode = value, error = null) }
  }

  /**
   * Creates the space and makes it the selected one. Whatever the visitor left out of the address
   * goes up as an empty string, which is the only shape the SDK's non-null [Address] allows.
   */
  fun createSpace() {
    if (!_state.value.canSubmit) return
    viewModelScope.launch {
      _state.update { it.copy(loading = true, error = null) }
      val current = _state.value
      sdkCall<Space> { onSuccess, onError ->
        InstaVision.spaceServices.createSpace(
          request = CreateSpaceRequest(
            address = Address(
              city = current.city.trim(),
              country = COUNTRY_CODE,
              postalCode = current.postalCode.trim(),
              state = current.region.trim(),
              street = current.street.trim(),
            ),
            name = current.name.trim(),
          ),
          onSuccess = onSuccess,
          onError = onError,
        )
      }
        .onSuccess { space ->
          SessionStore.putSpaces(SessionStore.spaces + space)
          SessionStore.selectSpace(space)
          _state.update { it.copy(loading = false, created = true) }
        }
        .onFailure { failure ->
          _state.update { it.copy(loading = false, error = failure.userMessage()) }
        }
    }
  }

  /** Clears the terminal flag after the screen has navigated away, so it cannot fire twice. */
  fun onNavigationHandled() {
    _state.update { it.copy(created = false) }
  }
}
