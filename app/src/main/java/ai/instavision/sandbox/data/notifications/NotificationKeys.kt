package ai.instavision.sandbox.data.notifications

/**
 * Keys the Guardian backend puts in the FCM data payload. The spellings are fixed by the server,
 * so they are copied from production rather than chosen here.
 */
internal object NotificationKeys {
  /** Payload type, resolved through [NotificationType.of]. */
  const val TYPE = "type"

  /** Headline supplied by the backend; blank on payloads that expect the client to word it. */
  const val TITLE = "title"

  /** Body text supplied by the backend. */
  const val BODY = "body"

  /** Name of the camera that raised the payload, used when the backend sent no body. */
  const val DEVICE_NAME = "device_name"

  /** Server-side id that de-duplicates ordinary event notifications. */
  const val MESSAGE_ID = "message_id"
}
