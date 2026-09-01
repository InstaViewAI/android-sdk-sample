package ai.instavision.sandbox.data.notifications

import ai.instavision.sandbox.MainActivity
import ai.instavision.sandbox.R
import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Request code every content intent shares; the intent carries no extras to tell them apart. */
private const val CONTENT_REQUEST_CODE = 0

/**
 * Posts an FCM data payload as a notification on the channel its `type` selects, opening
 * [MainActivity] when tapped.
 *
 * The headline and the body are the backend's own `title` and `body`, used verbatim and never
 * substituted for. The backend words every notification for the account that receives it, so a
 * local fallback would only ever say something less true than saying nothing; a payload carrying
 * neither is dropped, which is what the reference app does with it.
 *
 * A no-op while the notification permission is denied: posting anyway would throw on Android 13+,
 * and there is no user-facing surface here to explain the failure on.
 *
 * [canPostNotifications] is that guard, but lint cannot follow the check through a helper, so the
 * `MissingPermission` warning it raises on the `notify` call below is suppressed rather than obeyed.
 */
@SuppressLint("MissingPermission")
internal fun showNotification(
  context: Context,
  data: Map<String, String>,
) {
  if (!canPostNotifications(context)) return
  val title = data[NotificationKeys.TITLE].orEmpty()
  val body = data[NotificationKeys.BODY].orEmpty()
  if (title.isEmpty() && body.isEmpty()) return
  val type = NotificationType.of(data[NotificationKeys.TYPE])
  val config = NotificationChannels.configFor(type)
  val notification = NotificationCompat.Builder(context, config.id)
    .setSmallIcon(R.drawable.ic_notification)
    .setContentTitle(title)
    .setContentText(body)
    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
    .setCategory(category(type))
    .setPriority(priority(type))
    .setContentIntent(contentIntent(context))
    .setOnlyAlertOnce(true)
    .setAutoCancel(true)
    .build()
  NotificationManagerCompat.from(context)
    .notify(resolveNotificationId(type = type, data = data), notification)
}

/** Whether the runtime permission Android 13+ requires before posting has been granted. */
private fun canPostNotifications(context: Context): Boolean =
  Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
    PackageManager.PERMISSION_GRANTED

/** The system category, which drives Do Not Disturb and lock-screen treatment. */
private fun category(type: NotificationType): String = when (type) {
  NotificationType.AlarmEvent, NotificationType.AlarmNotified -> NotificationCompat.CATEGORY_ALARM
  NotificationType.DoorbellRing, NotificationType.Event -> NotificationCompat.CATEGORY_EVENT
}

/** Ranking within the channel; the channel's own importance still decides whether it interrupts. */
private fun priority(type: NotificationType): Int = when (type) {
  NotificationType.Event -> NotificationCompat.PRIORITY_DEFAULT
  NotificationType.DoorbellRing,
  NotificationType.AlarmEvent,
  NotificationType.AlarmNotified,
  -> NotificationCompat.PRIORITY_HIGH
}

/**
 * Reopens the app on tap. The intent reuses the running activity rather than stacking a second
 * copy of it, and is immutable so no other app can rewrite it.
 */
private fun contentIntent(context: Context): PendingIntent = PendingIntent.getActivity(
  context,
  CONTENT_REQUEST_CODE,
  Intent(context, MainActivity::class.java).apply {
    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
  },
  PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
)
