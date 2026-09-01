package ai.instavision.sandbox.data.notifications

import ai.instavision.guardian.sdk.InstaVision
import ai.instavision.guardian.sdk.data.entity.request.NotificationRegistrationRequest
import ai.instavision.sandbox.BuildConfig
import ai.instavision.sandbox.SampleApp
import ai.instavision.sandbox.ui.common.sdkCall
import com.google.firebase.messaging.FirebaseMessaging
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Value the backend expects in `provider`, taken from production's `NotificationProvider.FIREBASE`.
 * Its sibling `"Pushy"` covers devices without Play Services, which the sample does not support.
 */
private const val FIREBASE_PROVIDER = "Firebase"

/**
 * Fetches the current FCM token and binds it to the signed-in account, so pushes for that account
 * reach this install. Only meaningful with a session: the SDK authenticates the call.
 *
 * Failure is silent by design — a missing `google-services.json`, an unreachable token service or
 * a rejected call all leave the app working without push, and the launch path this runs on has no
 * error surface to report on.
 */
suspend fun registerCurrentPushToken() {
  val token = currentPushToken() ?: return
  sdkCall<Unit> { onSuccess, onError ->
    InstaVision.userServices.registerForPushNotifications(
      request = registrationRequest(token),
      onSuccess = { onSuccess(Unit) },
      onError = onError,
    )
  }
}

/**
 * Registers [token] through the SDK's own callback pair, for callers that are not a coroutine
 * scope — `FirebaseMessagingService.onNewToken` being the only one.
 */
internal fun registerPushToken(token: String) {
  InstaVision.userServices.registerForPushNotifications(
    request = registrationRequest(token),
    onSuccess = {},
    onError = {},
  )
}

/**
 * Builds the registration body. `deviceId` is serialised as `app_id` and must be the same
 * installation id the SDK itself was configured with, which is what production passes too.
 */
private fun registrationRequest(token: String): NotificationRegistrationRequest =
  NotificationRegistrationRequest(
    token = token,
    provider = FIREBASE_PROVIDER,
    deviceId = SampleApp.installationId,
  )

/**
 * Returns the current FCM token, or null when the app was built without `google-services.json`,
 * when Firebase failed to start, or when the token service refused.
 *
 * Messaging 25 deprecates `token` in favour of `register()`, which resolves to the Firebase
 * installation id rather than the registration token Guardian's backend expects, so the deprecated
 * property stays and its warning is suppressed.
 */
@Suppress("DEPRECATION")
private suspend fun currentPushToken(): String? {
  if (!BuildConfig.HAS_FIREBASE_CONFIG) return null
  return suspendCancellableCoroutine { continuation ->
    runCatching {
      FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
        continuation.resume(if (task.isSuccessful) task.result else null)
      }
    }.onFailure { continuation.resume(null) }
  }
}
