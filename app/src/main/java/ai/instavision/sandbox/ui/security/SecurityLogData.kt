package ai.instavision.sandbox.ui.security

import ai.instavision.sandbox.data.EventTokenStore
import ai.instavision.sandbox.ui.common.sdkCall
import ai.instavision.guardian.sdk.InstaVision
import ai.instavision.guardian.sdk.data.entity.ArmedLog
import ai.instavision.guardian.sdk.data.entity.DisarmedLog
import ai.instavision.guardian.sdk.data.entity.EventLog
import ai.instavision.guardian.sdk.data.entity.SecurityLog
import ai.instavision.guardian.sdk.data.entity.response.SecurityLogResponse
import com.google.gson.Gson
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** How many sessions the Security tab shows before handing over to the full log; `MAX_LOG_LIMIT`. */
internal const val MaxRecentSessions = 3

/**
 * Pages of log the tab root reads at most while looking for [MaxRecentSessions] sessions. A home
 * that has not been armed in a long time stops here rather than reading its whole window.
 */
internal const val MaxRecentPages = 5

/** How far back the log window reaches, in days; production's `MAX_PLAN_LIMIT`. */
private const val MaxPlanDays = 180L

/** Turns a log's `properties` map back into JSON so it can be read as the payload type it is. */
private val LogGson = Gson()

/** One `Event` entry of an arming session, with its payload already parsed out of `properties`. */
data class SecurityLogEvent(
  /** Identifier of the log entry itself, which is what keys the row. */
  val id: String,
  /** When the entry was written, which is what the row timestamps. */
  val createdAt: Long,
  /** Snapshot of the detection, re-signed by [signed]; null when the payload carried none. */
  val snapshotUrl: String?,
  /** The detection itself, including the event id the detail screen is opened with. */
  val event: EventLog,
)

/**
 * One arming session: when the system went live, what the cameras saw while it was, and when it
 * stood down. A session is only ever closed by an `Armed` entry, so [armedAt] is always known
 * while [disarmedAt] is null for a session the system has not been stood down from yet.
 */
data class SecuritySession(
  /** Identifier of the `Armed` entry that closed the session, which is what keys the card. */
  val id: String,
  /** When the system was armed. */
  val armedAt: Long,
  /** The arm payload, or null when it did not parse as one. */
  val armed: ArmedLog?,
  /** When the system stood down; null while the session is still open. */
  val disarmedAt: Long?,
  /** The disarm payload, or null when the session is still open or it did not parse. */
  val disarmed: DisarmedLog?,
  /** What the cameras detected during the session, newest first. */
  val events: List<SecurityLogEvent>,
)

/**
 * Walks [logs] newest first and folds them into arming sessions, exactly as the production app's
 * `groupEvents` does: a `Disarmed` entry is held aside, `Event` entries accumulate, and an `Armed`
 * entry closes a session with both and resets them.
 *
 * Two consequences of that walk are deliberate rather than oversights. Only `Event` entries join a
 * session, so `SMS`, `AgentCall`, `Dispatch` and `SendHelp` are dropped outright; and a `Disarmed`
 * entry with no `Armed` entry after it never closes a session and is dropped with them.
 */
internal fun groupSecuritySessions(logs: List<SecurityLog>): List<SecuritySession> {
  val sessions = mutableListOf<SecuritySession>()
  val events = mutableListOf<SecurityLogEvent>()
  var disarm: SecurityLog? = null
  logs.forEach { log ->
    when (log.type) {
      SecurityLogTypes.ARMED -> {
        sessions += SecuritySession(
          id = log.id,
          armedAt = log.createdAt,
          armed = log.properties.parseAs<ArmedLog>(),
          disarmedAt = disarm?.createdAt,
          disarmed = disarm?.properties.parseAs<DisarmedLog>(),
          events = events.toList(),
        )
        events.clear()
        disarm = null
      }

      SecurityLogTypes.DISARMED -> disarm = log

      SecurityLogTypes.EVENT -> log.properties.parseAs<EventLog>()?.let { event ->
        events += SecurityLogEvent(
          id = log.id,
          createdAt = log.createdAt,
          snapshotUrl = event.snapshotUrl,
          event = event,
        )
      }
    }
  }
  return sessions
}

/**
 * Re-signs every session's snapshots with the tokens [EventTokenStore] is currently holding, the
 * same way a page of events is signed on its way into the events list. Call it after a
 * [EventTokenStore.refresh], because the token a log's `snapshot_url` arrived with may already
 * have expired.
 */
internal fun List<SecuritySession>.signed(spaceId: String): List<SecuritySession> = map { session ->
  session.copy(
    events = session.events.map { entry ->
      entry.copy(
        snapshotUrl = EventTokenStore.sign(
          url = entry.snapshotUrl,
          spaceId = spaceId,
          bucketName = entry.event.bucketName,
        ),
      )
    },
  )
}

/**
 * Fetches one page of [spaceId]'s security log, starting [skip] entries in.
 *
 * The window is always the last [MaxPlanDays] days, which is as far back as any plan retains logs.
 * Production narrows it to the newest entry it has already stored, less a ten-minute overlap; the
 * sample keeps no local store, so there is no sync point to resume from and the whole window is
 * asked for every time.
 */
internal suspend fun fetchSecurityLogs(spaceId: String, skip: Long): Result<SecurityLogResponse> {
  val zone = ZoneId.systemDefault()
  val today = LocalDate.now(zone)
  return sdkCall { onSuccess, onError ->
    InstaVision.securityServices.getLogs(
      spaceId = spaceId,
      from = today.minusDays(MaxPlanDays).atStartOfDay(zone).toInstant().toEpochMilli(),
      to = today.atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli(),
      skip = skip,
      onSuccess = onSuccess,
      onError = onError,
    )
  }
}

/**
 * Reads a log's `properties` as [T]. The SDK types the field as `Any` and Gson hands it over as a
 * map, so it is re-serialised and read back into the payload type the entry's `type` implies. A
 * payload of any other shape yields null rather than throwing, which drops that one entry instead
 * of failing the whole page.
 */
private inline fun <reified T> Any?.parseAs(): T? =
  runCatching { LogGson.fromJson(LogGson.toJson(this), T::class.java) }.getOrNull()
