package ai.instavision.sandbox.ui.security

/**
 * The `status` vocabulary a security profile and each of its cameras report.
 *
 * The values belong to the production app rather than the SDK, which types both fields as bare
 * strings and ships no enum for them, so they are restated here — once, for the whole package,
 * because three screens were carrying their own copies.
 */
internal object SecurityStatus {
  /** The alarm is live. */
  const val ARMED = "Armed"

  /** Between an arm request and the alarm going live; counted as armed everywhere it is read. */
  const val ARMING = "Arming"

  /** The system has stood down. */
  const val DISARMED = "Disarmed"

  /** Between a disarm request and the system standing down. */
  const val DISARMING = "Disarming"

  /** A camera could not reach the state that was asked of it; ends a settle poll like a success. */
  const val FAILED = "Failed"

  /** Statuses during which the backend's schedules must not be edited, added to or deleted. */
  val LOCKED = setOf(ARMED, ARMING, DISARMING)
}
