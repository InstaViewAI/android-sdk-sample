package ai.instavision.sandbox.ui.security

import ai.instavision.sandbox.data.SessionStore
import ai.instavision.sandbox.ui.common.sdkCall
import ai.instavision.sandbox.ui.common.userMessage
import ai.instavision.guardian.sdk.InstaVision
import ai.instavision.guardian.sdk.data.entity.SecuritySchedule
import ai.instavision.guardian.sdk.data.entity.Time
import ai.instavision.guardian.sdk.data.entity.request.DeleteSecurityScheduleRequest
import ai.instavision.guardian.sdk.data.entity.request.UpdateSecurityScheduleRequest
import ai.instavision.guardian.sdk.data.enums.SecurityScheduleType
import ai.instavision.guardian.sdk.data.enums.WeekDay
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Said when a schedule write is refused because the system is not settled and disarmed. */
internal const val SYSTEM_ARMED_MESSAGE =
  "Disarm the system before adding, changing or deleting a schedule."

/** Hours the hour dropdown runs through, on the 24-hour clock the schedules are written in. */
private const val HOURS_PER_DAY = 24

/** Minutes in the hour the minute dropdown runs through. */
private const val MINUTES_PER_HOUR = 60

/** Granularity the minute dropdown offers; the backend accepts any minute, the picker offers five. */
private const val MINUTE_STEP = 5

/** Writes an hour and a minute as the 24-hour clock time both the list and the editor show. */
internal fun formatTimeOfDay(hour: Int, minute: Int): String =
  String.format(Locale.US, "%02d:%02d", hour, minute)

/** Everything the schedule step renders: the saved schedules and the editor sat under them. */
data class SecurityScheduleUiState(
  /** Schedules the home already has, as last returned by `getSchedules`. */
  val schedules: List<SecuritySchedule> = emptyList(),
  /** Identifier of the schedule being edited; null while the editor is composing a new one. */
  val editingId: String? = null,
  /** Whether the schedule being edited arms the system or disarms it. */
  val type: String = SecurityScheduleType.ARM.value,
  /** Days of the week the schedule fires on. */
  val days: Set<WeekDay> = emptySet(),
  /** Hour of the day the schedule fires at, on the 24-hour clock. */
  val hour: Int = 0,
  /** Minute of the hour the schedule fires at. */
  val minute: Int = 0,
  /** The profile's `status`, read alongside the schedules; empty when the home has no profile. */
  val status: String = "",
  /** True until the first `getSchedules` settles. */
  val loading: Boolean = true,
  /** True while a schedule write or the step completion is in flight. */
  val busy: Boolean = false,
  /** Set once the step has been recorded, so the screen can hand back to the checklist. */
  val done: Boolean = false,
  /** Message from the last failed request. */
  val error: String? = null,
) {
  /** Hours the dropdown offers, written the way the list shows them. */
  val hourOptions: List<String> get() = (0 until HOURS_PER_DAY).map { pad(it) }

  /** Minutes the dropdown offers, in [MINUTE_STEP] increments. */
  val minuteOptions: List<String> get() = (0 until MINUTES_PER_HOUR step MINUTE_STEP).map { pad(it) }

  /** The selected hour as its dropdown entry. */
  val hourLabel: String get() = pad(hour)

  /** The selected minute as its dropdown entry. */
  val minuteLabel: String get() = pad(minute)

  /** Whether the editor holds enough to send: a time is always set, days are not. */
  val canSave: Boolean get() = days.isNotEmpty()

  /**
   * Whether the system is armed or mid-transition, which is when the schedules are left alone.
   * The production app refuses the same three statuses in its own client; only it enforces this.
   */
  val locked: Boolean get() = status in SecurityStatus.LOCKED

  /** Whether the editor is changing a saved schedule rather than composing a new one. */
  val isEditing: Boolean get() = editingId != null

  /** Two digits, which is how both dropdowns and the schedule rows write a clock field. */
  private fun pad(value: Int): String = String.format(Locale.US, "%02d", value)
}

/**
 * Backs the optional schedule step: the arm and disarm times the system runs unattended on.
 * Production spreads the list, the editor and the delete confirmation across three screens; this
 * keeps all three on one, because the sample has nothing to gain from the extra navigation.
 */
class SecurityScheduleViewModel : ViewModel() {
  private val _uiState = MutableStateFlow(SecurityScheduleUiState())

  /** Single source of truth for [SecurityScheduleScreen]. */
  val uiState = _uiState.asStateFlow()

  init {
    load()
  }

  /**
   * Refetches the home's schedules and the profile status they can only be changed in, leaving
   * whatever is on screen in place while it runs. A failed status read leaves the last one intact.
   */
  fun load() {
    val spaceId = SessionStore.spaceId
    if (spaceId.isEmpty()) {
      _uiState.update { it.copy(loading = false) }
      return
    }
    _uiState.update { it.copy(error = null) }
    viewModelScope.launch {
      fetchSecurityProfile(spaceId).onSuccess { profile ->
        _uiState.update { it.copy(status = profile?.status.orEmpty()) }
      }
      sdkCall<List<SecuritySchedule>> { onSuccess, onError ->
        InstaVision.securityServices.getSchedules(
          spaceId = spaceId,
          profileId = spaceId,
          onSuccess = onSuccess,
          onError = onError,
        )
      }
        .onSuccess { schedules ->
          _uiState.update { it.copy(schedules = schedules, loading = false) }
        }
        .onFailure { error ->
          _uiState.update { it.copy(loading = false, error = error.userMessage()) }
        }
    }
  }

  /** Points the editor at [schedule], so saving updates it rather than adding another. */
  fun edit(schedule: SecuritySchedule) {
    _uiState.update {
      it.copy(
        editingId = schedule.id,
        type = schedule.type,
        days = schedule.selectedDays.toSet(),
        hour = schedule.timeSlot.hour,
        minute = schedule.timeSlot.minute,
      )
    }
  }

  /** Empties the editor back to a new schedule. */
  fun clearEditor() {
    _uiState.update {
      it.copy(
        editingId = null,
        type = SecurityScheduleType.ARM.value,
        days = emptySet(),
        hour = 0,
        minute = 0,
      )
    }
  }

  /** Switches the editor between arming and disarming. */
  fun onTypeChange(type: String) {
    _uiState.update { it.copy(type = type) }
  }

  /** Adds or removes one weekday from the schedule being edited. */
  fun onDayToggle(day: WeekDay) {
    _uiState.update { state ->
      state.copy(days = if (day in state.days) state.days - day else state.days + day)
    }
  }

  /** Records the hour picked from the dropdown, which offers it as a two-digit label. */
  fun onHourChange(label: String) {
    label.toIntOrNull()?.let { hour -> _uiState.update { it.copy(hour = hour) } }
  }

  /** Records the minute picked from the dropdown, which offers it as a two-digit label. */
  fun onMinuteChange(label: String) {
    label.toIntOrNull()?.let { minute -> _uiState.update { it.copy(minute = minute) } }
  }

  /**
   * Sends the editor's contents, adding a schedule or updating the one being edited. An armed or
   * transitioning system is refused here without reaching the SDK, as the production app does.
   */
  fun save() {
    val spaceId = SessionStore.spaceId
    val state = _uiState.value
    if (spaceId.isEmpty() || !state.canSave) return
    if (state.locked) {
      _uiState.update { it.copy(error = SYSTEM_ARMED_MESSAGE) }
      return
    }
    _uiState.update { it.copy(busy = true, error = null) }
    viewModelScope.launch {
      val request = UpdateSecurityScheduleRequest(
        selectedDays = state.days.sortedBy { it.value },
        timeSlot = Time(hour = state.hour, minute = state.minute),
        timezone = TimeZone.getDefault().id,
        type = state.type,
      )
      val editingId = state.editingId
      val result = if (editingId == null) {
        add(spaceId = spaceId, request = request)
      } else {
        update(spaceId = spaceId, scheduleId = editingId, request = request)
      }
      result
        .onSuccess {
          clearEditor()
          _uiState.update { it.copy(busy = false) }
          load()
        }
        .onFailure { error ->
          _uiState.update { it.copy(busy = false, error = error.userMessage()) }
        }
    }
  }

  /**
   * Removes one saved schedule, clearing the editor when it was the one being edited. Refused on
   * an armed or transitioning system, the same three statuses [save] refuses.
   */
  fun delete(scheduleId: String) {
    val spaceId = SessionStore.spaceId
    if (spaceId.isEmpty()) return
    if (_uiState.value.locked) {
      _uiState.update { it.copy(error = SYSTEM_ARMED_MESSAGE) }
      return
    }
    _uiState.update { it.copy(busy = true, error = null) }
    viewModelScope.launch {
      sdkCall<Unit> { onSuccess, onError ->
        InstaVision.securityServices.deleteSchedules(
          spaceId = spaceId,
          profileId = spaceId,
          request = DeleteSecurityScheduleRequest(scheduleIds = listOf(scheduleId)),
          onSuccess = onSuccess,
          onError = onError,
        )
      }
        .onSuccess {
          if (_uiState.value.editingId == scheduleId) clearEditor()
          _uiState.update { it.copy(busy = false) }
          load()
        }
        .onFailure { error ->
          _uiState.update { it.copy(busy = false, error = error.userMessage()) }
        }
    }
  }

  /** Records the step as done. The step is optional, so it never requires a saved schedule. */
  fun finish() {
    val spaceId = SessionStore.spaceId
    if (spaceId.isEmpty()) return
    _uiState.update { it.copy(busy = true, error = null) }
    viewModelScope.launch {
      markSetupStep(spaceId = spaceId, apiName = SecuritySteps.ScheduleSystem.apiName)
        .onSuccess { _uiState.update { it.copy(busy = false, done = true) } }
        .onFailure { error ->
          _uiState.update { it.copy(busy = false, error = error.userMessage()) }
        }
    }
  }

  /** Creates a schedule the home does not have yet. */
  private suspend fun add(spaceId: String, request: UpdateSecurityScheduleRequest): Result<*> =
    sdkCall<SecuritySchedule> { onSuccess, onError ->
      InstaVision.securityServices.addSchedule(
        spaceId = spaceId,
        profileId = spaceId,
        request = request,
        onSuccess = onSuccess,
        onError = onError,
      )
    }

  /** Rewrites a schedule the home already has. */
  private suspend fun update(
    spaceId: String,
    scheduleId: String,
    request: UpdateSecurityScheduleRequest,
  ): Result<*> = sdkCall<SecuritySchedule> { onSuccess, onError ->
    InstaVision.securityServices.updateSchedule(
      spaceId = spaceId,
      profileId = spaceId,
      scheduleId = scheduleId,
      request = request,
      onSuccess = onSuccess,
      onError = onError,
    )
  }
}
