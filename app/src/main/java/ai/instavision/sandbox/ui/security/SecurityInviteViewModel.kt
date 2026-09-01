package ai.instavision.sandbox.ui.security

import ai.instavision.sandbox.data.SessionStore
import ai.instavision.sandbox.ui.common.sdkCall
import ai.instavision.sandbox.ui.common.userMessage
import ai.instavision.guardian.sdk.InstaVision
import ai.instavision.guardian.sdk.data.entity.InvitedSpace
import ai.instavision.guardian.sdk.data.entity.request.SpaceInviteRequest
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How many people monitoring may be shared with, copied from production's `SECURITY_INVITE_LIMIT`. */
private const val INVITE_LIMIT = 3

/** The sharing type a monitoring invite carries, which is how `getUsers` tells them apart. */
private const val SECURITY_SHARING_TYPE = "Security"

/** Role a household member is invited under; monitoring never grants ownership. */
private const val VIEWER_ROLE = "Viewer"

/** Value of an invite's `status` once the invitee has turned it down. */
private const val STATUS_REJECTED = "Rejected"

/** Value of `status` once the invitee has joined, which decides remove-user versus remove-invite. */
private const val STATUS_ACCEPTED = "Accepted"

/** Value of `status` while the invite is still out. */
private const val STATUS_PENDING = "Pending"

/** Everything the invite household step renders. */
data class SecurityInviteUiState(
  /** Address being typed into the invite field. */
  val email: String = "",
  /** People monitoring is already shared with, including invites nobody has answered. */
  val members: List<InvitedSpace> = emptyList(),
  /** True until the first `getUsers` settles. */
  val loading: Boolean = true,
  /** True while an invite, a removal or the step completion is in flight. */
  val busy: Boolean = false,
  /** Set once the step has been recorded, so the screen can hand back to the checklist. */
  val done: Boolean = false,
  /** Message from the last failed request. */
  val error: String? = null,
) {
  /** Invites that still count against the limit; a declined one does not. */
  val active: List<InvitedSpace> get() = members.filter { it.status != STATUS_REJECTED }

  /** Whether there is room for another invite. */
  val hasRoom: Boolean get() = active.size < INVITE_LIMIT

  /** Whether the typed address is worth sending. */
  val canInvite: Boolean
    get() = hasRoom && Patterns.EMAIL_ADDRESS.matcher(email).matches()

  /** The line under the field: how much room is left, or that there is none. */
  val limitMessage: String
    get() = if (hasRoom) "Up to $INVITE_LIMIT people can be invited." else "Invite limit reached."

  /** How [member]'s standing reads in their row. */
  fun statusLabel(member: InvitedSpace): String = when (member.status) {
    STATUS_PENDING -> "Invite pending"
    STATUS_REJECTED -> "Invite declined"
    STATUS_ACCEPTED -> "Full access"
    else -> member.status
  }
}

/**
 * Backs the optional invite household step: the people who can arm, disarm and answer for the
 * alarm besides the owner. The invite is a space invite flagged `is_security`, which is what puts
 * the new member in the monitoring group rather than merely on the cameras.
 */
class SecurityInviteViewModel : ViewModel() {
  private val _uiState = MutableStateFlow(SecurityInviteUiState())

  /** Single source of truth for [SecurityInviteScreen]. */
  val uiState = _uiState.asStateFlow()

  init {
    load()
  }

  /** Refetches the home's members and keeps only the ones shared for monitoring. */
  fun load() {
    val spaceId = SessionStore.spaceId
    if (spaceId.isEmpty()) {
      _uiState.update { it.copy(loading = false) }
      return
    }
    _uiState.update { it.copy(error = null) }
    viewModelScope.launch {
      sdkCall<List<InvitedSpace>> { onSuccess, onError ->
        InstaVision.spaceServices.getUsers(
          spaceId = spaceId,
          onSuccess = onSuccess,
          onError = onError,
        )
      }
        .onSuccess { users ->
          _uiState.update {
            it.copy(
              members = users.filter { user -> SECURITY_SHARING_TYPE in user.types },
              loading = false,
            )
          }
        }
        .onFailure { error ->
          _uiState.update { it.copy(loading = false, error = error.userMessage()) }
        }
    }
  }

  /** Records what the user typed into the invite field. */
  fun onEmailChange(value: String) {
    _uiState.update { it.copy(email = value.trim()) }
  }

  /** Sends a monitoring invite to the typed address and clears the field on acceptance. */
  fun invite() {
    val spaceId = SessionStore.spaceId
    val state = _uiState.value
    if (spaceId.isEmpty() || !state.canInvite) return
    _uiState.update { it.copy(busy = true, error = null) }
    viewModelScope.launch {
      sdkCall<Unit> { onSuccess, onError ->
        InstaVision.spaceServices.createInvite(
          spaceId = spaceId,
          request = SpaceInviteRequest(
            inviteeEmail = state.email,
            deviceIds = emptyList(),
            isSecurity = true,
            role = VIEWER_ROLE,
          ),
          onSuccess = onSuccess,
          onError = onError,
        )
      }
        .onSuccess {
          _uiState.update { it.copy(busy = false, email = "") }
          load()
        }
        .onFailure { error ->
          _uiState.update { it.copy(busy = false, error = error.userMessage()) }
        }
    }
  }

  /**
   * Withdraws [member]. Someone who has joined is removed as a user of the monitoring share, while
   * an unanswered invite is deleted instead — two different endpoints for what reads as one action.
   */
  fun remove(member: InvitedSpace) {
    val spaceId = SessionStore.spaceId
    if (spaceId.isEmpty()) return
    _uiState.update { it.copy(busy = true, error = null) }
    viewModelScope.launch {
      val result = if (member.status == STATUS_ACCEPTED) {
        removeUser(spaceId = spaceId, spaceUserId = member.id)
      } else {
        removeInvite(spaceId = spaceId, invitationId = member.id)
      }
      result
        .onSuccess {
          _uiState.update { it.copy(busy = false) }
          load()
        }
        .onFailure { error ->
          _uiState.update { it.copy(busy = false, error = error.userMessage()) }
        }
    }
  }

  /** Records the step as done. The step is optional, so it never requires an invite to have gone. */
  fun finish() {
    val spaceId = SessionStore.spaceId
    if (spaceId.isEmpty()) return
    _uiState.update { it.copy(busy = true, error = null) }
    viewModelScope.launch {
      markSetupStep(spaceId = spaceId, apiName = SecuritySteps.InviteHouseholds.apiName)
        .onSuccess { _uiState.update { it.copy(busy = false, done = true) } }
        .onFailure { error ->
          _uiState.update { it.copy(busy = false, error = error.userMessage()) }
        }
    }
  }

  /** Takes someone who has already joined off the monitoring share. */
  private suspend fun removeUser(spaceId: String, spaceUserId: String): Result<*> =
    sdkCall<Unit> { onSuccess, onError ->
      InstaVision.spaceServices.deleteUser(
        spaceId = spaceId,
        spaceUserId = spaceUserId,
        type = SECURITY_SHARING_TYPE,
        onSuccess = onSuccess,
        onError = onError,
      )
    }

  /** Withdraws an invite nobody has answered yet. */
  private suspend fun removeInvite(spaceId: String, invitationId: String): Result<*> =
    sdkCall<Unit> { onSuccess, onError ->
      InstaVision.spaceServices.deleteInvite(
        spaceId = spaceId,
        invitationId = invitationId,
        onSuccess = onSuccess,
        onError = onError,
      )
    }
}
