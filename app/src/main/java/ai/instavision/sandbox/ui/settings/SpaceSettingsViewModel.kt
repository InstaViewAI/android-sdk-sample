package ai.instavision.sandbox.ui.settings

import ai.instavision.sandbox.data.SessionStore
import ai.instavision.sandbox.ui.common.sdkCall
import ai.instavision.sandbox.ui.common.userMessage
import ai.instavision.sandbox.ui.space.COUNTRY_OPTIONS
import ai.instavision.guardian.sdk.InstaVision
import ai.instavision.guardian.sdk.data.entity.AutoTopUp
import ai.instavision.guardian.sdk.data.entity.CellularData
import ai.instavision.guardian.sdk.data.entity.DataPass
import ai.instavision.guardian.sdk.data.entity.Invitations
import ai.instavision.guardian.sdk.data.entity.InvitedSpace
import ai.instavision.guardian.sdk.data.entity.Space
import ai.instavision.guardian.sdk.data.entity.is4GDevice
import ai.instavision.guardian.sdk.data.entity.request.SpaceInviteRequest
import ai.instavision.guardian.sdk.data.entity.request.UpdateAddressRequest
import ai.instavision.guardian.sdk.data.entity.request.UpdateFeaturesRequest
import ai.instavision.guardian.sdk.data.entity.request.UpdateSpaceRequest
import ai.instavision.guardian.sdk.data.enums.TemperatureUnit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Member type for someone who was given access to the home itself. The SDK takes this as a bare
 * string; the vocabulary lives in the production app's `core/tattva` module, not in the SDK, so
 * the sample restates it here.
 */
private const val MEMBER_TYPE_SPACE = "Space"

/**
 * Member type for someone who was given access to the professional monitoring side of the home.
 * Same story as [MEMBER_TYPE_SPACE] — the SDK never declares these values.
 */
private const val MEMBER_TYPE_SECURITY = "Security"

/**
 * Invite role with full control over the home. Like the member types, this vocabulary is the
 * app's rather than the SDK's, which types `SpaceInviteRequest.role` as a nullable string.
 */
private const val ROLE_OWNER = "Owner"

/** Invite role that can watch the cameras but not change anything. See [ROLE_OWNER]. */
private const val ROLE_VIEWER = "Viewer"

/** The roles the invite form offers, in the order it lists them. */
private val INVITE_ROLES = listOf(ROLE_VIEWER, ROLE_OWNER)

/** Everything the home settings screen renders for the selected space. */
data class SpaceSettingsUiState(
  /** Home being configured; null means nothing was selected before opening the screen. */
  val space: Space? = null,
  /** Editable home name. */
  val name: String = "",
  /** Editable street line of the home's address. */
  val street: String = "",
  /** Editable city of the home's address. */
  val city: String = "",
  /** Editable state or province of the home's address; sent as `Address.state`. */
  val region: String = "",
  /** Editable postal or ZIP code, kept as text so non-numeric codes survive. */
  val postalCode: String = "",
  /** Editable country of the home's address, picked from [COUNTRY_OPTIONS]. */
  val country: String = "",
  /** Countries the address form offers. */
  val countryOptions: List<String> = COUNTRY_OPTIONS,
  /** Whether familiar faces are recognised and named in events across this home. */
  val faceRecognitionEnabled: Boolean = false,
  /** Unit every temperature reading in this home is shown in. */
  val temperatureUnit: TemperatureUnit = TemperatureUnit.FAHRENHEIT,
  /** People who already have access to this home. */
  val members: List<InvitedSpace> = emptyList(),
  /** Invitations to this home that are still waiting on the signed-in account. */
  val invites: List<Invitations> = emptyList(),
  /** Address typed into the invite form. */
  val inviteEmail: String = "",
  /** Role selected in the invite form. */
  val inviteRole: String = ROLE_VIEWER,
  /** Roles the invite form offers. */
  val inviteRoles: List<String> = INVITE_ROLES,
  /** Whether any camera in this home is on a mobile network, which gates the data section. */
  val cellularAvailable: Boolean = false,
  /** Mobile data allowance left this cycle. */
  val cellularData: CellularData? = null,
  /** Extra data bundles bought for this home. */
  val dataPasses: List<DataPass> = emptyList(),
  /** Whether the home tops itself up when the allowance runs out. */
  val autoTopUp: AutoTopUp? = null,
  /** True until the member and invitation reads finish. */
  val loading: Boolean = true,
  /** True while a write is in flight, which disables every control on the screen. */
  val busy: Boolean = false,
  /** Message from the last failed request. */
  val error: String? = null,
  /** Confirmation of the last successful write. */
  val notice: String? = null,
  /** Set once the account has left the home so the screen can navigate away. */
  val left: Boolean = false,
)

/**
 * Reads and writes the settings of [SessionStore.selectedSpace]: its name, its shared features,
 * who can see it, the invitations out on it and — for homes with a mobile camera — the data
 * allowance behind it.
 */
@Suppress("TooManyFunctions")
class SpaceSettingsViewModel : ViewModel() {
  private val _uiState = MutableStateFlow(SpaceSettingsUiState())

  /** Single source of truth for [SpaceSettingsScreen]. */
  val uiState = _uiState.asStateFlow()

  init {
    load()
  }

  /** Seeds the form from the selected home, then fetches its members and invitations. */
  fun load() {
    val space = SessionStore.selectedSpace
    if (space == null) {
      _uiState.update { it.copy(loading = false) }
      return
    }
    val cellularAvailable = SessionStore.devices.any { it.is4GDevice() }
    _uiState.update {
      it.copy(
        space = space,
        name = space.name,
        street = space.address.street,
        city = space.address.city,
        region = space.address.state,
        postalCode = space.address.postalCode,
        country = space.address.country,
        countryOptions = withCountry(space.address.country),
        faceRecognitionEnabled = space.settings?.face == true,
        temperatureUnit = TemperatureUnit.fromValue(
          space.settings?.temperatureUnit ?: TemperatureUnit.FAHRENHEIT.value
        ),
        cellularAvailable = cellularAvailable,
        loading = true,
        error = null,
      )
    }
    viewModelScope.launch {
      loadMembers(space)
      loadInvites(space)
      if (cellularAvailable) loadCellular(space)
      _uiState.update { it.copy(loading = false) }
    }
  }

  /** Records what the user typed into the home name field. */
  fun onNameChange(value: String) {
    _uiState.update { it.copy(name = value) }
  }

  /** Records what the user typed into the street field. */
  fun onStreetChange(value: String) {
    _uiState.update { it.copy(street = value) }
  }

  /** Records what the user typed into the city field. */
  fun onCityChange(value: String) {
    _uiState.update { it.copy(city = value) }
  }

  /** Records what the user typed into the state field. */
  fun onRegionChange(value: String) {
    _uiState.update { it.copy(region = value) }
  }

  /** Records what the user typed into the postal code field. */
  fun onPostalCodeChange(value: String) {
    _uiState.update { it.copy(postalCode = value) }
  }

  /** Records the country picked from the dropdown. */
  fun onCountryChange(value: String) {
    _uiState.update { it.copy(country = value) }
  }

  /**
   * Saves the home's name and address together. The request carries both regardless of which the
   * user touched, because the SDK takes the address as a whole rather than as a patch.
   */
  fun saveDetails() {
    val space = _uiState.value.space ?: return
    val current = _uiState.value
    val name = current.name.trim()
    if (name.isEmpty()) return
    submit(notice = "Home updated") {
      sdkCall<Space> { onSuccess, onError ->
        InstaVision.spaceServices.updateSpace(
          spaceId = space.id,
          updateSpaceRequest = UpdateSpaceRequest(
            address = UpdateAddressRequest(
              city = current.city.trim(),
              country = current.country.trim(),
              postalCode = current.postalCode.trim(),
              state = current.region.trim(),
              street = current.street.trim(),
            ),
            name = name,
          ),
          onSuccess = onSuccess,
          onError = onError,
        )
      }.onSuccess { updated -> publish(updated) }
    }
  }

  /** Turns naming of familiar faces on or off for every camera in the home. */
  fun setFaceRecognition(enabled: Boolean) {
    updateFeatures(
      notice = if (enabled) "Face recognition on" else "Face recognition off",
      request = UpdateFeaturesRequest(faceRecognition = enabled),
      onDone = { it.copy(faceRecognitionEnabled = enabled) },
    )
  }

  /** Switches every temperature reading in the home between Celsius and Fahrenheit. */
  fun setTemperatureUnit(unit: TemperatureUnit) {
    updateFeatures(
      notice = "Temperature unit updated",
      request = UpdateFeaturesRequest(temperatureUnit = unit.value),
      onDone = { it.copy(temperatureUnit = unit) },
    )
  }

  /** Removes someone's access to the home and refreshes the member list. */
  fun removeMember(member: InvitedSpace) {
    val space = _uiState.value.space ?: return
    val type = if (member.securityEnabled == true) MEMBER_TYPE_SECURITY else MEMBER_TYPE_SPACE
    submit(notice = "${member.email} removed") {
      sdkCall<Unit> { onSuccess, onError ->
        InstaVision.spaceServices.deleteUser(
          spaceId = space.id,
          spaceUserId = member.id,
          type = type,
          onSuccess = onSuccess,
          onError = onError,
        )
      }.onSuccess { loadMembers(space) }
    }
  }

  /** Records what the user typed into the invite email field. */
  fun onInviteEmailChange(value: String) {
    _uiState.update { it.copy(inviteEmail = value) }
  }

  /** Records the role picked in the invite form. */
  fun onInviteRoleChange(role: String) {
    _uiState.update { it.copy(inviteRole = role) }
  }

  /**
   * Invites the typed address to the home. Every camera currently in the home is shared, which
   * is what an owner or viewer invite means here; per-camera sharing is out of scope.
   */
  fun sendInvite() {
    val space = _uiState.value.space ?: return
    val email = _uiState.value.inviteEmail.trim()
    if (email.isEmpty()) return
    val role = _uiState.value.inviteRole
    submit(notice = "Invite sent to $email", onDone = { it.copy(inviteEmail = "") }) {
      sdkCall<Unit> { onSuccess, onError ->
        InstaVision.spaceServices.createInvite(
          spaceId = space.id,
          request = SpaceInviteRequest(
            inviteeEmail = email,
            deviceIds = SessionStore.devices.map { it.id },
            isSecurity = false,
            role = role,
          ),
          onSuccess = onSuccess,
          onError = onError,
        )
      }.onSuccess { loadInvites(space) }
    }
  }

  /**
   * Changes the role attached to a pending invitation. `getInvites` returns the invitations
   * addressed to the signed-in account, so that account is the invitee being resent.
   */
  fun changeInviteRole(invite: Invitations, role: String) {
    val space = _uiState.value.space ?: return
    val email = SessionStore.user?.email ?: return
    submit(notice = "Invitation updated") {
      sdkCall<Unit> { onSuccess, onError ->
        InstaVision.spaceServices.updateInvite(
          spaceId = space.id,
          invitationId = invite.id,
          request = SpaceInviteRequest(
            inviteeEmail = email,
            deviceIds = invite.deviceIds,
            role = role,
          ),
          onSuccess = onSuccess,
          onError = onError,
        )
      }.onSuccess { loadInvites(space) }
    }
  }

  /** Withdraws a pending invitation. */
  fun cancelInvite(invite: Invitations) {
    val space = _uiState.value.space ?: return
    submit(notice = "Invitation cancelled") {
      sdkCall<Unit> { onSuccess, onError ->
        InstaVision.spaceServices.deleteInvite(
          spaceId = space.id,
          invitationId = invite.id,
          onSuccess = onSuccess,
          onError = onError,
        )
      }.onSuccess { loadInvites(space) }
    }
  }

  /**
   * Gives up access to the home and refreshes the account's remaining homes; the caller is
   * expected to have confirmed this with the user first.
   */
  fun leaveSpace() {
    val space = _uiState.value.space ?: return
    submit(notice = "You left ${space.name}") {
      sdkCall<Unit> { onSuccess, onError ->
        InstaVision.spaceServices.leaveSpace(
          spaceId = space.id,
          onSuccess = onSuccess,
          onError = onError,
        )
      }.onSuccess {
        refreshSpaces()
        _uiState.update { it.copy(left = true) }
      }
    }
  }

  /** Clears the banner left behind by the last write. */
  fun dismissNotice() {
    _uiState.update { it.copy(notice = null, error = null) }
  }

  /** The offered countries with the home's own folded in, so a stored value is never dropped. */
  private fun withCountry(country: String): List<String> =
    if (country.isEmpty() || country in COUNTRY_OPTIONS) {
      COUNTRY_OPTIONS
    } else {
      COUNTRY_OPTIONS + country
    }

  /** Reads who has access to the home; a failure leaves the previous list in place. */
  private suspend fun loadMembers(space: Space) {
    sdkCall<List<InvitedSpace>> { onSuccess, onError ->
      InstaVision.spaceServices.getUsers(
        spaceId = space.id,
        onSuccess = onSuccess,
        onError = onError,
      )
    }
      .onSuccess { members -> _uiState.update { it.copy(members = members) } }
      .onFailure { error -> _uiState.update { it.copy(error = error.userMessage()) } }
  }

  /** Reads the account's invitations and keeps only the ones pointing at this home. */
  private suspend fun loadInvites(space: Space) {
    sdkCall<List<Invitations>> { onSuccess, onError ->
      InstaVision.spaceServices.getInvites(onSuccess = onSuccess, onError = onError)
    }
      .onSuccess { invites ->
        val forSpace = invites.filter { it.spaceDetails.id == space.id }
        _uiState.update { it.copy(invites = forSpace) }
      }
      .onFailure { error -> _uiState.update { it.copy(error = error.userMessage()) } }
  }

  /** Reads the mobile data figures; every one of the three is best-effort. */
  private suspend fun loadCellular(space: Space) {
    sdkCall<CellularData> { onSuccess, onError ->
      InstaVision.spaceServices.getCellularData(
        spaceId = space.id,
        onSuccess = onSuccess,
        onError = onError,
      )
    }.onSuccess { data -> _uiState.update { it.copy(cellularData = data) } }

    sdkCall<List<DataPass>> { onSuccess, onError ->
      InstaVision.spaceServices.getDataPass(
        spaceId = space.id,
        onSuccess = { passes -> onSuccess(passes.orEmpty()) },
        onError = onError,
      )
    }.onSuccess { passes -> _uiState.update { it.copy(dataPasses = passes) } }

    sdkCall<AutoTopUp> { onSuccess, onError ->
      InstaVision.spaceServices.getAutoTopUpInfo(
        spaceId = space.id,
        onSuccess = onSuccess,
        onError = onError,
      )
    }.onSuccess { topUp -> _uiState.update { it.copy(autoTopUp = topUp) } }
  }

  /** Re-reads the account's homes after leaving one so the rest of the app stays in step. */
  private suspend fun refreshSpaces() {
    sdkCall<List<Space>> { onSuccess, onError ->
      InstaVision.spaceServices.getSpaces(onSuccess = onSuccess, onError = onError)
    }.onSuccess { spaces -> SessionStore.putSpaces(spaces) }
  }

  /** Sends one edited home feature; unset fields of the request are left as they are. */
  private fun updateFeatures(
    notice: String,
    request: UpdateFeaturesRequest,
    onDone: (SpaceSettingsUiState) -> SpaceSettingsUiState,
  ) {
    val space = _uiState.value.space ?: return
    submit(notice = notice, onDone = onDone) {
      sdkCall<Unit> { onSuccess, onError ->
        InstaVision.spaceServices.updateSettings(
          spaceId = space.id,
          request = request,
          onSuccess = onSuccess,
          onError = onError,
        )
      }
    }
  }

  /**
   * Replaces the home everywhere it is cached after the server accepts an edit. The selection is
   * assigned rather than re-selected, because re-selecting would throw away the loaded cameras.
   */
  private fun publish(space: Space) {
    SessionStore.putSpaces(
      SessionStore.spaces.map { if (it.id == space.id) space else it }
    )
    SessionStore.selectedSpace = space
    _uiState.update { it.copy(space = space, name = space.name) }
  }

  /**
   * Runs one write with the busy flag raised, applying [onDone] and showing [notice] when the
   * server accepts it.
   */
  private fun submit(
    notice: String,
    onDone: (SpaceSettingsUiState) -> SpaceSettingsUiState = { it },
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
