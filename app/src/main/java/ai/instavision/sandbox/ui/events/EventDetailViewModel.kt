package ai.instavision.sandbox.ui.events

import ai.instavision.sandbox.data.EventTokenStore
import ai.instavision.sandbox.data.SessionStore
import ai.instavision.sandbox.ui.common.sdkCall
import ai.instavision.sandbox.ui.common.userMessage
import ai.instavision.guardian.sdk.InstaVision
import ai.instavision.guardian.sdk.data.entity.Event
import ai.instavision.guardian.sdk.data.entity.request.DeleteEventsRequest
import ai.instavision.guardian.sdk.domain.util.M3U8ToMP4Converter
import ai.instavision.network.data.entity.ApiError
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Everything the detail screen renders, including where the event sits among its siblings. */
data class EventDetailUiState(
  /** Event being shown; null when the screen was opened without one selected. */
  val event: Event? = null,
  /** True while there is a newer event to step to. */
  val canGoPrevious: Boolean = false,
  /** True while there is an older event to step to. */
  val canGoNext: Boolean = false,
  /** True while feedback, a delete or a save of the still or the clip is in flight. */
  val busy: Boolean = false,
  /** True once the event has been deleted, which is the screen's cue to go back. */
  val deleted: Boolean = false,
  /**
   * True once the snapshot and clip URLs have been signed with a fresh token, or once that fetch
   * has failed and the cached URLs stand. The screen holds the media stage empty until then, so
   * neither the player nor the image loader is ever handed a URL whose token has expired.
   */
  val mediaReady: Boolean = false,
  /** Transient confirmation such as "Snapshot saved", cleared by the screen once shown. */
  val message: String? = null,
  /** Message from the last failed action. */
  val error: String? = null,
)

/**
 * Drives the event detail screen from [SessionStore]: the event to show and the loaded window it
 * belongs to are both read once at construction, which is what the previous/next chevrons walk.
 */
class EventDetailViewModel : ViewModel() {
  private val siblings: List<Event> = SessionStore.events
  private val _uiState = MutableStateFlow(EventDetailUiState())
  private var index: Int = siblings.indexOfFirst { it.id == SessionStore.selectedEvent?.id }

  /** Token fetch for the event on screen, cancelled when the chevrons step to a neighbour. */
  private var tokenJob: Job? = null

  /** Single source of truth for [EventDetailScreen]. */
  val uiState = _uiState.asStateFlow()

  init {
    show(SessionStore.selectedEvent)
  }

  /** Steps to the newer neighbour of the current event. */
  fun showPrevious() {
    if (index <= 0) return
    index -= 1
    show(siblings[index])
  }

  /** Steps to the older neighbour of the current event. */
  fun showNext() {
    if (index < 0 || index >= siblings.lastIndex) return
    index += 1
    show(siblings[index])
  }

  /**
   * Tells the backend whether the detection was right. The answer is mirrored onto the local copy
   * so the buttons stay filled without refetching the event.
   */
  fun sendFeedback(isAccurate: Boolean) {
    val event = _uiState.value.event ?: return
    _uiState.update { it.copy(busy = true, error = null) }
    viewModelScope.launch {
      sdkCall<Unit> { onSuccess, onError ->
        InstaVision.eventServices.feedback(
          spaceId = event.spaceId,
          eventId = event.id,
          isAccurate = isAccurate,
          onSuccess = onSuccess,
          onError = onError,
        )
      }
        .onSuccess { markAccurate(event = event, isAccurate = isAccurate) }
        .onFailure { error ->
          _uiState.update { it.copy(busy = false, error = error.userMessage()) }
        }
    }
  }

  /** Deletes the event and drops it from the shared cache, then reports it through [deleted]. */
  fun deleteEvent() {
    val event = _uiState.value.event ?: return
    _uiState.update { it.copy(busy = true, error = null) }
    viewModelScope.launch {
      sdkCall<Unit> { onSuccess, onError ->
        InstaVision.eventServices.deleteEvents(
          deleteEventsRequest = DeleteEventsRequest(eventIds = listOf(event.id)),
          spaceId = event.spaceId,
          onSuccess = { onSuccess(Unit) },
          onError = onError,
        )
      }
        .onSuccess {
          SessionStore.putEvents(SessionStore.events.filterNot { it.id == event.id })
          SessionStore.selectEvent(null)
          _uiState.update { it.copy(busy = false, deleted = true) }
        }
        .onFailure { error ->
          _uiState.update { it.copy(busy = false, error = error.userMessage()) }
        }
    }
  }

  /**
   * Writes the event's still frame into the phone's gallery. The snapshot URL is pre-signed, so it
   * is fetched through the shared image loader rather than the SDK, and `MediaStore` needs no
   * permission for a file this app owns on the versions the sample supports.
   */
  fun saveSnapshot(context: Context) {
    val snapshot = _uiState.value.event?.snapShot
    val name = _uiState.value.event?.id ?: return
    if (snapshot == null) {
      _uiState.update { it.copy(error = "This event has no snapshot to save") }
      return
    }
    val appContext = context.applicationContext
    _uiState.update { it.copy(busy = true, error = null) }
    viewModelScope.launch {
      val bitmap = loadSnapshot(context = appContext, url = snapshot)
      val saved = bitmap != null &&
        writeToGallery(context = appContext, bitmap = bitmap, name = name)
      _uiState.update {
        it.copy(
          busy = false,
          message = if (saved) "Snapshot saved to your gallery" else null,
          error = if (saved) null else "Could not save the snapshot",
        )
      }
    }
  }

  /**
   * Writes the event's clip into the phone's gallery. The clip is an HLS playlist, so it cannot be
   * downloaded as a file: the SDK's converter pulls every segment and muxes them into MP4s, which
   * is slow enough that `busy` is held for the whole conversion and not only for the insert. A
   * multi-lens camera yields one file per lens, and all of them are written.
   */
  fun saveVideo(context: Context) {
    val event = _uiState.value.event ?: return
    val playlist = event.video
    if (playlist.isNullOrBlank()) {
      _uiState.update { it.copy(error = "This event has no clip to save") }
      return
    }
    val appContext = context.applicationContext
    _uiState.update { it.copy(busy = true, error = null) }
    viewModelScope.launch {
      val converted = convertClip(context = appContext, event = event, playlist = playlist)
      val files = converted.getOrDefault(emptyList())
      val saved = writeVideosToGallery(context = appContext, files = files, event = event)
      _uiState.update {
        it.copy(
          busy = false,
          message = if (saved) savedVideoMessage(files.size) else null,
          error = if (saved) {
            null
          } else {
            converted.exceptionOrNull()?.userMessage() ?: "Could not save the video"
          },
        )
      }
    }
  }

  /** Drops a transient confirmation once the screen has displayed it. */
  fun consumeMessage() {
    _uiState.update { it.copy(message = null) }
  }

  /** Dismisses the last failure. */
  fun clearError() {
    _uiState.update { it.copy(error = null) }
  }

  /**
   * Publishes [event] as the one on screen, recomputes which chevrons are still live and starts
   * the token refresh its media URLs need before anything can play them.
   */
  private fun show(event: Event?) {
    SessionStore.selectEvent(event)
    _uiState.update {
      it.copy(
        event = event,
        canGoPrevious = index > 0,
        canGoNext = index >= 0 && index < siblings.lastIndex,
        mediaReady = event == null,
        message = null,
        error = null,
      )
    }
    refreshMediaTokens(event)
  }

  /**
   * Refreshes the space's tokens and re-signs [event] with the one issued now. The URLs came from
   * the list's page fetch and their token expires within minutes, so playing what was cached 401s.
   * A failed fetch leaves [EventTokenStore] holding what it had, because that token may still be
   * good and an empty stage would read worse than a clip that might not load.
   */
  private fun refreshMediaTokens(event: Event?) {
    tokenJob?.cancel()
    if (event == null) return
    tokenJob = viewModelScope.launch {
      EventTokenStore.refresh(event.spaceId)
      applyMediaTokens(event)
    }
  }

  /**
   * Publishes [event] signed and releases the media stage. The loaded window is re-signed with it,
   * so stepping to a neighbour finds a URL that is already current. A token that arrives after the
   * user has stepped on is dropped, so a slow fetch can never put the previous event back on
   * screen.
   */
  private fun applyMediaTokens(event: Event) {
    if (_uiState.value.event?.id != event.id) return
    val signed = EventTokenStore.sign(event)
    _uiState.update { it.copy(event = signed, mediaReady = true) }
    SessionStore.selectEvent(signed)
    SessionStore.putEvents(EventTokenStore.sign(SessionStore.events))
  }

  /** Records the user's verdict on [event] locally and in the shared cache. */
  private fun markAccurate(event: Event, isAccurate: Boolean) {
    val updated = event.copy(accurate = isAccurate)
    SessionStore.putEvents(
      SessionStore.events.map { if (it.id == updated.id) updated else it },
    )
    SessionStore.selectEvent(updated)
    _uiState.update { it.copy(event = updated, busy = false) }
  }

  /** Decodes the pre-signed snapshot through Coil's shared loader; null when the fetch fails. */
  private suspend fun loadSnapshot(context: Context, url: String): Bitmap? {
    val request = ImageRequest.Builder(context).data(url).build()
    val result = SingletonImageLoader.get(context).execute(request)
    return (result as? SuccessResult)?.image?.toBitmap()
  }

  /** Inserts [bitmap] into the device's pictures collection, returning whether it landed. */
  private suspend fun writeToGallery(context: Context, bitmap: Bitmap, name: String): Boolean =
    withContext(Dispatchers.IO) {
      val resolver = context.contentResolver
      val pending = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "$SavedFilePrefix$name.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
        put(MediaStore.Images.Media.IS_PENDING, 1)
      }
      val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, pending)
        ?: return@withContext false
      val written = runCatching {
        resolver.openOutputStream(uri)?.use { stream ->
          bitmap.compress(Bitmap.CompressFormat.JPEG, JpegQuality, stream)
        } ?: false
      }.getOrDefault(false)
      val published = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
      resolver.update(uri, published, null, null)
      if (!written) resolver.delete(uri, null, null)
      written
    }

  /**
   * Turns [playlist] into playable MP4s in the cache directory, one per lens of a stitched camera.
   * The event's own [Event.hardwareProperties] is handed over because it is what tells the
   * converter where each lens sits in the stitched frame; without it every camera would yield a
   * single undivided file. The interpolation settings are left at their defaults, which is exactly
   * what the converter's short overload passes, because the sample has no capability lookup to
   * source them from.
   *
   * The URL goes over untouched: the reference app passes it through `NetworkManager
   * .getResolvedB2Url` first, but that only swaps the B2 host when an alternative-endpoint flag is
   * set and returns the URL unchanged otherwise, and it lives in a module the SDK does not ship.
   */
  private suspend fun convertClip(
    context: Context,
    event: Event,
    playlist: String,
  ): Result<List<File>> = sdkCall { onSuccess, onError ->
    M3U8ToMP4Converter.convert(
      context = context,
      m3u8Url = playlist,
      onSuccess = onSuccess,
      onError = { message -> onError(ApiError(code = ConversionErrorCode, message = message)) },
      hardwareConfig = event.hardwareProperties,
    )
  }

  /**
   * Writes every converted file into the device's movies collection, returning whether all of them
   * landed. The cache copies are deleted either way, because a clip runs to several megabytes and
   * the converter leaves one behind per lens.
   */
  private suspend fun writeVideosToGallery(
    context: Context,
    files: List<File>,
    event: Event,
  ): Boolean = withContext(Dispatchers.IO) {
    val written = files.isNotEmpty() &&
      files.withIndex().all { (index, file) ->
        writeVideo(
          context = context,
          file = file,
          name = videoFileName(event = event, index = index, total = files.size),
        )
      }
    files.forEach { it.delete() }
    written
  }

  /**
   * Copies one converted file into a pending row of the movies collection, returning whether it
   * landed. Any earlier row of the same name is dropped first so re-saving an event replaces its
   * clip rather than filling the gallery with copies, and the row is published only once the last
   * byte is written, so a failed copy leaves nothing behind instead of a truncated video.
   */
  private fun writeVideo(context: Context, file: File, name: String): Boolean {
    val resolver = context.contentResolver
    val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    runCatching {
      resolver.delete(
        collection,
        "${MediaStore.Video.Media.DISPLAY_NAME} = ?" +
          " AND ${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?",
        arrayOf(name, "%${Environment.DIRECTORY_MOVIES}%"),
      )
    }
    val pending = ContentValues().apply {
      put(MediaStore.Video.Media.DISPLAY_NAME, name)
      put(MediaStore.Video.Media.MIME_TYPE, VideoMimeType)
      put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES)
      put(MediaStore.Video.Media.IS_PENDING, 1)
    }
    val uri: Uri = resolver.insert(collection, pending) ?: return false
    val written = runCatching {
      val stream = resolver.openOutputStream(uri) ?: return@runCatching false
      stream.use { output -> file.inputStream().use { input -> input.copyTo(output) } }
      true
    }.getOrDefault(false)
    if (written) {
      val published = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
      resolver.update(uri, published, null, null)
    } else {
      resolver.delete(uri, null, null)
    }
    return written
  }
}

/**
 * Names a saved clip after its camera, the moment it was captured and the event it belongs to, so
 * the files of one event sort together in a gallery. A stitched camera converts to one file per
 * lens, which is what [total] distinguishes: those get a trailing lens number, a single-lens
 * camera gets none.
 */
private fun videoFileName(event: Event, index: Int, total: Int): String {
  val camera = event.deviceName.orEmpty().filterNot { it.isWhitespace() }.ifBlank { UnnamedCamera }
  val captured = SavedFileTimestamp.format(Instant.ofEpochMilli(event.startTime))
  val lens = if (total > 1) "-${index + 1}" else ""
  return "$SavedFilePrefix$camera-$captured-${event.id}$lens.mp4"
}

/** The confirmation shown once [count] files have landed, which reads oddly in the plural. */
private fun savedVideoMessage(count: Int): String =
  if (count > 1) "$count videos saved to your gallery" else "Video saved to your gallery"

/** Prefix of a saved file's name, so the sample's snapshots and clips stand out in the gallery. */
private const val SavedFilePrefix = "instavision_"

/** Quality a saved snapshot is re-encoded at. */
private const val JpegQuality = 95

/** Type a saved clip is filed in the gallery under; the converter always produces MP4s. */
private const val VideoMimeType = "video/mp4"

/** Stands in for a camera that was never named, so a saved file always has a readable prefix. */
private const val UnnamedCamera = "Camera"

/**
 * Code given to the clip converter's failures on their way into an `ApiError`. The converter
 * reports a bare string rather than the `ApiError` the rest of the SDK uses, and this is what lets
 * its message travel the same `sdkCall` path as every other failure the screen shows.
 */
private const val ConversionErrorCode = "CLIP_CONVERSION_FAILED"

/** Stamp in a saved clip's name, compact enough to sit inside a file name and still sort. */
private val SavedFileTimestamp: DateTimeFormatter =
  DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault())
