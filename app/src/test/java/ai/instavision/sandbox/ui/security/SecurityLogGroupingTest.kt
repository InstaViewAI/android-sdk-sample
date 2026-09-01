package ai.instavision.sandbox.ui.security

import ai.instavision.guardian.sdk.data.entity.SecurityLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the fold that turns a flat log into arming sessions. Getting the walk wrong is what
 * attaches a detection to the session before or after the one it actually happened in.
 */
class SecurityLogGroupingTest {

  /** An `Armed` entry closes a session with the disarm and the detections seen since. */
  @Test
  fun armedEntryClosesASessionWithWhatCameBeforeIt() {
    val sessions = groupSecuritySessions(
      listOf(
        log(id = "d1", type = SecurityLogTypes.DISARMED, createdAt = 500),
        log(id = "e1", type = SecurityLogTypes.EVENT, createdAt = 400),
        log(id = "a1", type = SecurityLogTypes.ARMED, createdAt = 300),
      ),
    )

    assertEquals(1, sessions.size)
    assertEquals("a1", sessions.single().id)
    assertEquals(300L, sessions.single().armedAt)
    assertEquals(500L, sessions.single().disarmedAt)
    assertEquals(listOf("e1"), sessions.single().events.map { it.id })
  }

  /** Detections belong to the session that armed after them, never to an earlier one. */
  @Test
  fun detectionsJoinTheSessionThatArmedAfterThem() {
    val sessions = groupSecuritySessions(
      listOf(
        log(id = "e2", type = SecurityLogTypes.EVENT, createdAt = 900),
        log(id = "a2", type = SecurityLogTypes.ARMED, createdAt = 800),
        log(id = "e1", type = SecurityLogTypes.EVENT, createdAt = 400),
        log(id = "a1", type = SecurityLogTypes.ARMED, createdAt = 300),
      ),
    )

    assertEquals(listOf("a2", "a1"), sessions.map { it.id })
    assertEquals(listOf("e2"), sessions.first().events.map { it.id })
    assertEquals(listOf("e1"), sessions.last().events.map { it.id })
    assertNull(sessions.first().disarmedAt)
  }

  /**
   * A disarm with no arm after it never closes a session, and the entry types the production
   * grouper ignores stay ignored here — the item renderer has branches for them that the fold
   * never feeds.
   */
  @Test
  fun unclosedDisarmsAndUngroupedTypesAreDropped() {
    val sessions = groupSecuritySessions(
      listOf(
        log(id = "s1", type = SecurityLogTypes.SMS, createdAt = 700),
        log(id = "c1", type = SecurityLogTypes.AGENT_CALL, createdAt = 650),
        log(id = "p1", type = SecurityLogTypes.DISPATCH, createdAt = 600),
        log(id = "h1", type = SecurityLogTypes.SEND_HELP, createdAt = 550),
        log(id = "e1", type = SecurityLogTypes.EVENT, createdAt = 400),
        log(id = "a1", type = SecurityLogTypes.ARMED, createdAt = 300),
        log(id = "d1", type = SecurityLogTypes.DISARMED, createdAt = 100),
      ),
    )

    assertEquals(1, sessions.size)
    assertEquals(listOf("e1"), sessions.single().events.map { it.id })
    assertTrue(sessions.none { it.id == "d1" })
  }

  /** A log entry with the payload shape its type implies, which the fold parses per type. */
  private fun log(id: String, type: String, createdAt: Long): SecurityLog = SecurityLog(
    id = id,
    type = type,
    createdAt = createdAt,
    spaceId = "space",
    properties = payload(type = type, id = id),
  )

  /** The `properties` map the backend sends for [type], reduced to the fields the fold reads. */
  private fun payload(type: String, id: String): Map<String, Any> = when (type) {
    SecurityLogTypes.ARMED -> mapOf(
      "time" to 1L,
      "devices" to listOf(mapOf("id" to "cam", "name" to "Front door", "arm_status" to "Success")),
    )

    SecurityLogTypes.DISARMED -> mapOf("time" to 1L, "disarm_method" to "App")

    else -> mapOf("id" to id, "device_name" to "Front door", "tags" to listOf("Person"))
  }
}
