package ai.instavision.sandbox.ui.security

/**
 * The `type` vocabulary a security log entry reports.
 *
 * The values belong to the production app rather than the SDK, which types the field as a bare
 * string and ships no enum for it, so they are restated here the way [SecurityStatus] restates the
 * profile's own vocabulary.
 */
internal object SecurityLogTypes {
  /** The system was armed; this is the entry that closes an arming session. */
  const val ARMED = "Armed"

  /** The system stood down, ending the session the next `Armed` entry opened. */
  const val DISARMED = "Disarmed"

  /** A camera detected something while the system was armed. */
  const val EVENT = "Event"

  /** A text message went out to a responding party; never grouped into a session. */
  const val SMS = "SMS"

  /** The monitoring centre phoned someone; never grouped into a session. */
  const val AGENT_CALL = "AgentCall"

  /** The authorities were dispatched; never grouped into a session. */
  const val DISPATCH = "Dispatch"

  /** The user asked for help from the app; never grouped into a session. */
  const val SEND_HELP = "SendHelp"
}

/** The per-camera outcome recorded inside an `Armed` log's payload; production's `ArmStatus`. */
internal object ArmStatus {
  /** The camera reached the armed state. */
  const val SUCCESS = "Success"

  /** The camera never reached it. */
  const val FAILURE = "Failure"

  /** The camera was still arming when the log was written. */
  const val ARMING = "Arming"

  /** Value of a device's `reason` field rather than its `arm_status`; why [FAILURE] happened. */
  const val LOW_BATTERY = "LowBattery"
}
