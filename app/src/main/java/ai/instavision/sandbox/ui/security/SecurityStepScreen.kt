package ai.instavision.sandbox.ui.security

import ai.instavision.sandbox.ui.common.DetailScaffold
import ai.instavision.sandbox.ui.common.EmptyState
import ai.instavision.sandbox.ui.common.PrimaryButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.runtime.Composable

/** Title used when the backend hands over a step this build does not know about. */
private const val UNKNOWN_STEP_TITLE = "Security setup"

/** Why an unrecognised step has no screen: the checklist has moved on without this build. */
private const val UNKNOWN_STEP_BODY =
  "The monitoring service asked for a step this build of the sample does not know about. Update " +
    "the app, or carry on with the steps it does recognise."

/**
 * Routes a checklist step's `apiName` to its screen. Contact information never arrives here — the
 * checklist opens it directly — so every value this handles is one of the six later steps, and an
 * unrecognised one is a backend the sample has fallen behind rather than a step left unbuilt.
 */
@Composable
fun SecurityStepScreen(apiName: String, onBack: () -> Unit, onDone: () -> Unit) {
  when (SecuritySteps.fromApiName(apiName)) {
    SecuritySteps.CameraSetup -> SecurityCameraScreen(onBack = onBack, onDone = onDone)
    SecuritySteps.ArmSettings -> SecurityArmScreen(onBack = onBack, onDone = onDone)
    SecuritySteps.DisarmSettings -> SecurityDisarmScreen(onBack = onBack, onDone = onDone)
    SecuritySteps.ScheduleSystem -> SecurityScheduleScreen(onBack = onBack, onDone = onDone)
    SecuritySteps.TestSystem -> SecuritySystemTestScreen(onBack = onBack, onDone = onDone)
    SecuritySteps.InviteHouseholds -> SecurityInviteScreen(onBack = onBack, onDone = onDone)
    SecuritySteps.ContactInformation -> SecurityContactScreen(onBack = onBack, onDone = onDone)
    else -> UnknownStep(onBack = onBack)
  }
}

/** Shown for a step name this build cannot map, which leaves the user a way back rather than stuck. */
@Composable
private fun UnknownStep(onBack: () -> Unit) {
  DetailScaffold(title = UNKNOWN_STEP_TITLE, onBack = onBack) {
    EmptyState(
      title = "Step not recognised",
      body = UNKNOWN_STEP_BODY,
      icon = Icons.AutoMirrored.Outlined.HelpOutline,
      action = { PrimaryButton(text = "Back to checklist", onClick = onBack) },
    )
  }
}
