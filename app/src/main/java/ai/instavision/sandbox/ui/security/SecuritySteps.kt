package ai.instavision.sandbox.ui.security

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One entry of the professional monitoring checklist, ported from the production app. [apiName] is
 * the string the backend stores in `setup_step` and lists in `completed_steps`, so it — not
 * [title] — is the identity of a step and the only part of it the SDK ever sees.
 */
sealed class SecuritySteps(
  /** The backend's identifier for the step, stored in `setup_step` and `completed_steps`. */
  val apiName: String,
  /** Heading of the step's card and of its own screen. */
  val title: String,
  /** Second line of the step's card; null for a step the production app gives no strapline. */
  val subtitle: String?,
  /** Glyph shown in the step's accent circle. */
  val icon: ImageVector,
  /** Whether monitoring can start with this step still undone. */
  val optional: Boolean = false,
) {
  /** Where the alarm is and who a dispatcher reaches; the one step this sample fully designs. */
  data object ContactInformation : SecuritySteps(
    apiName = "ContactInformation",
    title = "Contact information",
    subtitle = "Address, phone number, alarm permit",
    icon = Icons.Outlined.LocationOn,
  )

  /** Which cameras take part in monitoring and which parts of their view they watch. */
  data object CameraSetup : SecuritySteps(
    apiName = "CameraSetup",
    title = "Camera setup",
    subtitle = "Choose cameras and set security zones",
    icon = Icons.Outlined.Videocam,
  )

  /** The grace period between arming and the alarm going live. */
  data object ArmSettings : SecuritySteps(
    apiName = "ArmSettings",
    title = "Arm settings",
    subtitle = "How long you get to leave",
    icon = Icons.Outlined.Lock,
  )

  /** How the household stands the alarm down, including the spoken safe word. */
  data object DisarmSettings : SecuritySteps(
    apiName = "DisarmSettings",
    title = "Disarm settings",
    subtitle = "Disarm methods and your safe word",
    icon = Icons.Outlined.Key,
  )

  /** Unattended arming and disarming; its [apiName] is `ScheduleSystem`, not `Schedule`. */
  data object ScheduleSystem : SecuritySteps(
    apiName = "ScheduleSystem",
    title = "Schedule",
    subtitle = "Arm and disarm automatically",
    icon = Icons.Outlined.CalendarMonth,
    optional = true,
  )

  /** A rehearsal of the whole alarm path with the monitoring centre left out of it. */
  data object TestSystem : SecuritySteps(
    apiName = "TestSystem",
    title = "Test the system",
    subtitle = "A live run without calling anyone",
    icon = Icons.Outlined.VerifiedUser,
    optional = true,
  )

  /** Bringing the rest of the household in; the production app gives it no second line. */
  data object InviteHouseholds : SecuritySteps(
    apiName = "InviteHouseholds",
    title = "Invite your household",
    subtitle = null,
    icon = Icons.Outlined.GroupAdd,
    optional = true,
  )

  /**
   * Terminal value of `setup_step`, reached once the checklist is done. It is not part of [entries]
   * because there is no screen behind it; its [title] restates its [apiName] for the same reason.
   */
  data object Completed : SecuritySteps(
    apiName = "Completed",
    title = "Completed",
    subtitle = null,
    icon = Icons.Outlined.VerifiedUser,
  )

  /** The checklist itself, since a sealed class has no `entries` the way an enum does. */
  companion object {
    /** The seven user-facing steps, in the order the checklist lists them. */
    val entries: List<SecuritySteps> = listOf(
      ContactInformation,
      CameraSetup,
      ArmSettings,
      DisarmSettings,
      ScheduleSystem,
      TestSystem,
      InviteHouseholds,
    )

    /** The steps that have to be done before monitoring can start. */
    val required: List<SecuritySteps> = entries.filterNot { it.optional }

    /** Resolves a backend string, including [Completed]; null for a value this build cannot map. */
    fun fromApiName(apiName: String): SecuritySteps? =
      (entries + Completed).firstOrNull { it.apiName == apiName }
  }
}
