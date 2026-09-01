package ai.instavision.sandbox.ui.security

import ai.instavision.sandbox.ui.common.sdkCall
import ai.instavision.guardian.sdk.InstaVision
import ai.instavision.guardian.sdk.data.entity.request.SecurityProfileRequest
import ai.instavision.guardian.sdk.data.entity.request.TestModeRequest
import ai.instavision.guardian.sdk.data.entity.response.SecurityProfileResponse

/**
 * Records [apiName] in the profile's `setup_step`. Every checklist step ends with this one write,
 * because it is what moves the profile's `completed_steps` — and so the checklist — forward.
 */
internal suspend fun markSetupStep(
  spaceId: String,
  apiName: String,
): Result<SecurityProfileResponse> = sdkCall { onSuccess, onError ->
  InstaVision.securityServices.updateProfile(
    spaceId = spaceId,
    request = SecurityProfileRequest(setupStep = apiName),
    onSuccess = onSuccess,
    onError = onError,
  )
}

/**
 * Puts the home in or out of test mode, which is what keeps a system's alarms away from a real
 * dispatcher. The test step and the checklist's Finish only ever switch it on, so [enable]
 * defaults to that; security settings is the one caller that also switches it back off.
 */
internal suspend fun enableTestMode(
  spaceId: String,
  enable: Boolean = true,
): Result<Unit> = sdkCall { onSuccess, onError ->
  InstaVision.securityServices.enableTestMode(
    spaceId = spaceId,
    request = TestModeRequest(enable = enable),
    onSuccess = onSuccess,
    onError = onError,
  )
}
