package ai.instavision.sandbox.data.notifications

/**
 * The push payload types this sample acts on, spelled exactly as the backend sends them in the
 * payload's `type` field.
 *
 * Production's enum carries a further twenty entries that trigger data refreshes — subscription,
 * pet, baby and face syncs — which this sample has no caches to refresh, so they are left out and
 * arrive here as [Event].
 */
enum class NotificationType {
  /** Ordinary camera activity: motion, a person, a vehicle. The low-urgency default. */
  Event,

  /** The security system has gone into alarm. */
  AlarmEvent,

  /** The monitoring centre was alerted about a running alarm. */
  AlarmNotified,

  /** Someone pressed a doorbell. */
  DoorbellRing,
  ;

  companion object {
    /**
     * Maps a raw payload `type` onto an entry.
     *
     * Anything unrecognised — including the types this sample deliberately does not model —
     * becomes [Event] so the notification is still shown rather than dropped.
     */
    fun of(value: String?): NotificationType = entries.find { it.name == value } ?: Event
  }
}
