package ai.instavision.sandbox.data

import ai.instavision.guardian.sdk.data.entity.Device
import ai.instavision.guardian.sdk.data.entity.Event
import ai.instavision.guardian.sdk.data.entity.Space
import ai.instavision.guardian.sdk.data.entity.User
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Process-wide cache of what the signed-in account is currently looking at, mirroring the way the
 * production app's data sources publish SDK results. Every property is Compose-observable, so a
 * screen that reads one recomposes when another screen refreshes it.
 */
object SessionStore {
  /** The signed-in account; null before sign-in and after [clear]. */
  var user: User? by mutableStateOf(null)

  /** Spaces the account can reach, as last published by a `getSpaces` call. */
  var spaces: List<Space> by mutableStateOf(emptyList())

  /** Cameras inside [selectedSpace], as last published by a `getDevices` call. */
  var devices: List<Device> by mutableStateOf(emptyList())

  /** Activity in [selectedSpace], newest first, as last published by a `getEvents` call. */
  var events: List<Event> by mutableStateOf(emptyList())

  /** The space every space-scoped SDK call is made against. */
  var selectedSpace: Space? by mutableStateOf(null)

  /** The camera every device-scoped SDK call is made against. */
  var selectedDevice: Device? by mutableStateOf(null)

  /** The event the detail screen renders; null whenever nothing has been opened. */
  var selectedEvent: Event? by mutableStateOf(null)

  /** Identifier the SDK expects for space-scoped calls; empty while nothing is selected. */
  val spaceId: String get() = selectedSpace?.id.orEmpty()

  /** Identifier the SDK expects for device-scoped calls; empty while nothing is selected. */
  val deviceId: String get() = selectedDevice?.id.orEmpty()

  /** Caches the account returned by sign-in, sign-up or a profile fetch. */
  fun putUser(user: User) {
    this.user = user
  }

  /** Publishes a fetched space list, re-selecting the first entry when the old one is gone. */
  fun putSpaces(spaces: List<Space>) {
    this.spaces = spaces
    if (spaces.none { it.id == selectedSpace?.id }) applySpaceSelection(spaces.firstOrNull())
  }

  /** Publishes a fetched device list, re-selecting the first entry when the old one is gone. */
  fun putDevices(devices: List<Device>) {
    this.devices = devices
    if (devices.none { it.id == selectedDevice?.id }) selectedDevice = devices.firstOrNull()
  }

  /**
   * Publishes the events of the selected space. The list is the whole page window the events tab
   * has loaded so far, which is also what the detail screen steps through.
   */
  fun putEvents(events: List<Event>) {
    this.events = events
  }

  /** Switches the active space, dropping the device and event caches of the previous one. */
  fun selectSpace(space: Space) {
    applySpaceSelection(space)
  }

  /** Switches the active camera; callers are expected to pass an entry of [devices]. */
  fun selectDevice(device: Device) {
    selectedDevice = device
  }

  /** Records the event the detail screen opens, or clears it when passed null. */
  fun selectEvent(event: Event?) {
    selectedEvent = event
  }

  /** Empties the store on sign-out so the next account never sees the previous one's data. */
  fun clear() {
    user = null
    spaces = emptyList()
    applySpaceSelection(null)
  }

  /** Points the store at [space] and invalidates the devices and events of the previous one. */
  private fun applySpaceSelection(space: Space?) {
    selectedSpace = space
    devices = emptyList()
    selectedDevice = null
    events = emptyList()
    selectedEvent = null
  }
}
