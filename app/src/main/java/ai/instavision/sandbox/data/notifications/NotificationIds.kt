package ai.instavision.sandbox.data.notifications

/** Milliseconds in a second, used to shrink the clock into an id that fits an `Int`. */
private const val MILLIS_PER_SECOND = 1000L

/** Id every doorbell ring is posted under, so a second ring replaces the first. */
private const val DOORBELL_NOTIFICATION_ID = 2001

/** Id every alarm is posted under. */
private const val ALARM_NOTIFICATION_ID = 2002

/** Id every monitoring-centre follow-up is posted under. */
private const val ALARM_NOTIFIED_NOTIFICATION_ID = 2003

/**
 * The id a payload is posted under.
 *
 * Doorbell and alarm each get one fixed id so a repeat of the same situation replaces the visible
 * notification instead of stacking a second copy on top of it. Ordinary events keep the backend's
 * `message_id` — the same choice production makes — and fall back to the current second, which
 * lets several unrelated events coexist.
 */
internal fun resolveNotificationId(
  type: NotificationType,
  data: Map<String, String>,
): Int = when (type) {
  NotificationType.DoorbellRing -> DOORBELL_NOTIFICATION_ID
  NotificationType.AlarmEvent -> ALARM_NOTIFICATION_ID
  NotificationType.AlarmNotified -> ALARM_NOTIFIED_NOTIFICATION_ID
  NotificationType.Event -> data[NotificationKeys.MESSAGE_ID]?.toLongOrNull()?.toInt()
    ?: (System.currentTimeMillis() / MILLIS_PER_SECOND).toInt()
}
