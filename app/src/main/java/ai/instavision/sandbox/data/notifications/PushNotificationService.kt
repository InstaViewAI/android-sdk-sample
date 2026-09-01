package ai.instavision.sandbox.data.notifications

import ai.instavision.guardian.sdk.InstaVision
import ai.instavision.sandbox.SampleApp
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives Guardian's push payloads.
 *
 * Both entry points are inert while nobody is signed in, matching production: the SDK cannot
 * register a token without a session, and a notification for a signed-out account would open an
 * app that has nothing to show.
 */
class PushNotificationService : FirebaseMessagingService() {
  /** Shows the payload on the channel its `type` selects. */
  override fun onMessageReceived(message: RemoteMessage) {
    super.onMessageReceived(message)
    if (!isSignedIn()) return
    showNotification(context = this, data = message.data)
  }

  /**
   * Re-registers a rotated token through the SDK's callback form, this being no coroutine scope.
   *
   * Messaging 25 deprecates this in favour of `onRegistered`, which hands over the Firebase
   * installation id instead of the FCM registration token. Guardian's backend wants the token, so
   * the deprecated callback is the correct one and the warning is suppressed rather than obeyed.
   */
  @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
  override fun onNewToken(token: String) {
    super.onNewToken(token)
    if (!isSignedIn()) return
    registerPushToken(token)
  }

  /** Whether a Guardian session exists; the SDK's services are unusable before initialisation. */
  private fun isSignedIn(): Boolean =
    SampleApp.isSdkInitialized && InstaVision.userServices.isLoggedIn().value == true
}
