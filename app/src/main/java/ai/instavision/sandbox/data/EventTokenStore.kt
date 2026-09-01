package ai.instavision.sandbox.data

import ai.instavision.guardian.sdk.InstaVision
import ai.instavision.guardian.sdk.data.entity.Event
import ai.instavision.guardian.sdk.data.entity.EventMetaData
import ai.instavision.sandbox.ui.common.sdkCall

/**
 * The sample's stand-in for the production app's `EventsDao.updateB2Tokens`, which rewrites the
 * `Authorization=` tail of every stored event's snapshot and clip URL whenever `getEventTokens`
 * hands back a fresh set. There is no database here, so the tokens are held in memory and applied
 * to events on their way through [sign] instead of to rows on their way into a table.
 *
 * A token is issued per bucket, so [EventMetaData.deviceId] plays no part in the lookup and is
 * ignored outright — every camera writing into a bucket shares that bucket's token.
 *
 * This is the only place event media URLs are signed. Anything that publishes events — the list,
 * pagination, the detail screen — signs through it, so a token can never be applied by one path
 * and missed by another.
 */
object EventTokenStore {
  /** Token per bucket from the last successful fetch, or empty until one has landed. */
  @Volatile
  private var tokens: Map<String, String> = emptyMap()

  /** Space [tokens] were issued for; signing an event from any other space is a no-op. */
  @Volatile
  private var spaceId: String = ""

  /**
   * Fetches the tokens for [spaceId] and replaces whatever was held. A failure is swallowed and
   * the previous tokens stand, because the URLs already on screen are signed with a token that may
   * still be good and blanking them would be worse than serving one that is about to expire.
   */
  suspend fun refresh(spaceId: String) {
    if (spaceId.isEmpty()) return
    sdkCall<List<EventMetaData>> { onSuccess, onError ->
      InstaVision.eventServices.getEventTokens(
        spaceId = spaceId,
        onSuccess = onSuccess,
        onError = onError,
      )
    }.onSuccess { fetched ->
      tokens = fetched.associate { it.bucketName to it.authToken.trim() }
      this.spaceId = spaceId
    }
  }

  /** Returns [event] with its snapshot and clip signed, or unchanged when no token covers it. */
  fun sign(event: Event): Event {
    val token = tokenFor(event) ?: return event
    return event.copy(
      snapShot = event.snapShot.reSigned(token),
      video = event.video.reSigned(token),
    )
  }

  /** Signs a whole page in one pass, which is what the list and [SessionStore] are handed. */
  fun sign(events: List<Event>): List<Event> = events.map(::sign)

  /**
   * Signs a media URL that reaches the app outside an [Event] — a security log entry's snapshot,
   * which carries its own bucket rather than a whole event. Returns [url] unchanged when no token
   * covers [bucketName].
   */
  fun sign(url: String?, spaceId: String, bucketName: String?): String? {
    val token = tokenFor(space = spaceId, bucket = bucketName) ?: return url
    return url.reSigned(token)
  }

  /** The token covering [event], looked up on the space and bucket it belongs to. */
  private fun tokenFor(event: Event): String? =
    tokenFor(space = event.spaceId, bucket = event.bucketName)

  /**
   * The token covering [bucket] of [space], found on the bucket alone. Media carrying no bucket
   * has nothing to look up and is deliberately given no fallback, so if such media exists it stays
   * visibly unsigned rather than being papered over with another bucket's token.
   */
  private fun tokenFor(space: String, bucket: String?): String? {
    if (space != spaceId) return null
    if (bucket.isNullOrBlank()) return null
    return tokens[bucket]?.takeIf { it.isNotEmpty() }
  }
}

/**
 * Signs a media URL with [token]: the value after an existing `Authorization=` is replaced, and a
 * URL carrying no token at all has the parameter appended rather than being left unsigned. The
 * replace half truncates at the *first* marker so it stays byte-identical to the reference app's
 * `updateB2Tokens`, whose SQL matches with `instr`.
 */
private fun String?.reSigned(token: String): String? {
  val url = this ?: return null
  if (url.isBlank()) return url
  val marker = url.indexOf(AuthorizationMarker)
  if (marker >= 0) return url.substring(0, marker + AuthorizationMarker.length) + token
  val separator = if (url.contains(QuerySeparator)) ParameterSeparator else QuerySeparator
  return "$url$separator$AuthorizationMarker$token"
}

/** Query parameter the backend signs event media with; everything after it is the token. */
private const val AuthorizationMarker = "Authorization="

/** Character that opens a URL's query string, and so tells a signed URL from an unsigned one. */
private const val QuerySeparator = '?'

/** Character that joins the token onto a URL that already carries a query string. */
private const val ParameterSeparator = '&'
