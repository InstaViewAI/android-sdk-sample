package ai.instavision.sandbox.ui.events

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Hands the player a playlist that is terminated. The backend serves a finished event's HLS
 * playlist without the `#EXT-X-ENDLIST` tag, which the player reads as "still recording": it then
 * treats the clip as a live stream, reports no duration and refuses to seek, so the scrubber and
 * the `0:00 / 0:40` readout have nothing to show. Adding the tag on the way through is what the
 * reference app does for any event that has already ended.
 *
 * Only `.m3u8` responses are rewritten; segments and plain MP4s pass straight through.
 */
@UnstableApi
class PlaylistDataSourceFactory(
  private val upstream: DataSource.Factory,
) : DataSource.Factory {
  /** Wraps one upstream source per request, as the player opens a new one for every download. */
  override fun createDataSource(): DataSource = PlaylistDataSource(upstream.createDataSource())
}

/** The wrapper [PlaylistDataSourceFactory] builds; buffers a playlist so it can be terminated. */
@UnstableApi
private class PlaylistDataSource(private val upstream: DataSource) : DataSource {
  /** The rewritten playlist being served instead of [upstream]; null for anything else. */
  private var playlist: InputStream? = null

  /** Passes the player's bandwidth meter down to the source actually doing the transfer. */
  override fun addTransferListener(transferListener: TransferListener) {
    upstream.addTransferListener(transferListener)
  }

  /** The URI in flight, which stays the upstream's because nothing here redirects. */
  override fun getUri(): Uri? = upstream.uri

  /**
   * Opens [dataSpec] and, when it names a playlist, drains it up front so the terminated copy can
   * be measured — the player needs an exact length back, not the upstream's.
   */
  override fun open(dataSpec: DataSpec): Long {
    val length = upstream.open(dataSpec)
    if (dataSpec.uri.path?.endsWith(PlaylistExtension) != true) return length
    val terminated = terminate(drainUpstream()).toByteArray()
    playlist = ByteArrayInputStream(terminated)
    return terminated.size.toLong()
  }

  /** Serves the rewritten playlist when there is one, and the untouched response otherwise. */
  override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
    playlist?.read(buffer, offset, length) ?: upstream.read(buffer, offset, length)

  /** Releases the buffered playlist along with the upstream connection. */
  override fun close() {
    playlist?.close()
    playlist = null
    upstream.close()
  }

  /** Reads the whole upstream response into a string, which a playlist is always small enough for. */
  private fun drainUpstream(): String {
    val text = StringBuilder()
    val chunk = ByteArray(BufferSize)
    while (true) {
      val read = upstream.read(chunk, 0, chunk.size)
      if (read == C.RESULT_END_OF_INPUT) break
      text.append(String(chunk, 0, read, Charsets.UTF_8))
    }
    return text.toString()
  }

  /** Appends the end tag unless the playlist already carries one. */
  private fun terminate(playlist: String): String =
    if (playlist.contains(EndListTag)) playlist else "${playlist.trim()}\n$EndListTag"
}

/** Extension identifying a response as an HLS playlist rather than one of its segments. */
private const val PlaylistExtension = ".m3u8"

/** Tag telling the player the playlist is complete, which is what makes the clip seekable. */
private const val EndListTag = "#EXT-X-ENDLIST"

/** Size of the chunk a playlist is drained in. */
private const val BufferSize = 4096
