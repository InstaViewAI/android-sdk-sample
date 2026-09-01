package ai.instavision.sandbox.ui.events

import ai.instavision.guardian.sdk.data.entity.Event
import ai.instavision.sandbox.ui.common.CircleIconButton
import ai.instavision.sandbox.ui.common.ConfirmDialog
import ai.instavision.sandbox.ui.common.DestructiveButton
import ai.instavision.sandbox.ui.common.DetailScaffold
import ai.instavision.sandbox.ui.common.EmptyState
import ai.instavision.sandbox.ui.common.ErrorBanner
import ai.instavision.sandbox.ui.common.GroupCard
import ai.instavision.sandbox.ui.common.LoadingBox
import ai.instavision.sandbox.ui.common.Notice
import ai.instavision.sandbox.ui.common.RowDivider
import ai.instavision.sandbox.ui.common.ScreenPadding
import ai.instavision.sandbox.ui.common.SecondaryButton
import ai.instavision.sandbox.ui.common.SectionHeader
import ai.instavision.sandbox.ui.common.SettingRow
import ai.instavision.sandbox.ui.common.StatusPill
import ai.instavision.sandbox.ui.theme.AppTheme
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/**
 * A single captured event: its clip, what the backend made of it, and the two things the user can
 * do about it — correct the detection, or delete it. The event comes from `SessionStore`, so the
 * screen takes no arguments beyond its way back.
 */
@Composable
fun EventDetailScreen(onBack: () -> Unit) {
  val viewModel: EventDetailViewModel = viewModel()
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  var confirmingDelete by remember { mutableStateOf(false) }
  val event = state.event
  val style = event?.let { eventTagStyle(it.tags) }
  val clip = event?.video?.takeIf { it.isNotBlank() }

  LaunchedEffect(state.deleted) {
    if (state.deleted) onBack()
  }

  LaunchedEffect(state.message) {
    if (state.message != null) {
      delay(timeMillis = MessageVisibleMillis)
      viewModel.consumeMessage()
    }
  }

  DetailScaffold(
    title = style?.label ?: "Event",
    subtitle = event?.deviceName,
    onBack = onBack,
    actions = {
      StepButton(
        icon = Icons.Rounded.ExpandLess,
        contentDescription = "Newer event",
        enabled = state.canGoPrevious,
        onClick = viewModel::showPrevious,
      )
      StepButton(
        icon = Icons.Rounded.ExpandMore,
        contentDescription = "Older event",
        enabled = state.canGoNext,
        onClick = viewModel::showNext,
      )
    },
  ) {
    if (event == null || style == null) {
      EmptyState(
        title = "No event selected",
        body = "Open an event from the events tab to see its clip here.",
        icon = Icons.Outlined.Videocam,
      )
      return@DetailScaffold
    }
    when {
      !state.mediaReady -> EventMediaPlaceholder()
      clip == null -> EventSnapshot(snapshot = event.snapShot)
      else -> EventPlayer(video = clip)
    }
    ErrorBanner(message = state.error)
    Notice(message = state.message)
    EventContext(contextBody = event.contextBody)
    EventSummary(event = event, style = style)
    FeedbackSection(
      accurate = event.accurate,
      enabled = !state.busy,
      onFeedback = viewModel::sendFeedback,
    )
    SecondaryButton(
      text = if (clip == null) "Save snapshot" else "Save video",
      onClick = {
        if (clip == null) viewModel.saveSnapshot(context) else viewModel.saveVideo(context)
      },
      enabled = !state.busy && (clip != null || event.snapShot != null),
    )
    DestructiveButton(
      text = "Delete event",
      onClick = { confirmingDelete = true },
      enabled = !state.busy,
    )
  }

  if (confirmingDelete) {
    ConfirmDialog(
      title = "Delete event",
      message = "This clip and its snapshot will be removed for everyone in the home.",
      confirmLabel = "Delete",
      onConfirm = {
        confirmingDelete = false
        viewModel.deleteEvent()
      },
      onDismiss = { confirmingDelete = false },
    )
  }
}

/**
 * The clip stage while the event's media URLs are being signed with a fresh token, framed exactly
 * like the player's surface so resolving one does not shift the layout.
 */
@Composable
private fun EventMediaPlaceholder() {
  Box(
    modifier = Modifier
      .fullBleed(inset = ScreenPadding)
      .aspectRatio(ClipAspectRatio)
      .background(color = AppTheme.colors.surfaceHigh),
    contentAlignment = Alignment.Center,
  ) {
    LoadingBox()
  }
}

/**
 * The still frame of an event that carries no clip, framed exactly like the player's surface so
 * stepping between events never shifts the layout. Falls back to a struck-through camera when the
 * event has no snapshot either.
 */
@Composable
private fun EventSnapshot(snapshot: String?) {
  Box(
    modifier = Modifier
      .fullBleed(inset = ScreenPadding)
      .aspectRatio(ClipAspectRatio)
      .background(color = AppTheme.colors.surfaceHigh),
    contentAlignment = Alignment.Center,
  ) {
    if (snapshot.isNullOrBlank()) {
      Icon(
        imageVector = Icons.Outlined.VideocamOff,
        contentDescription = null,
        tint = AppTheme.colors.textTertiary,
        modifier = Modifier.size(PlaceholderIconSize),
      )
    } else {
      AsyncImage(
        model = snapshot,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
      )
    }
  }
}

/**
 * The clip and the transport the sample draws over it. `PlayerView` is given no controller of its
 * own so the platform transport bar can never appear; position and duration are polled instead,
 * because an `ExoPlayer` is not observable from composition. The player is tied to [video] rather
 * than to the event id, so stepping to a neighbour and re-signing the same clip both release this
 * player and build another around the URL that is now current.
 *
 * The transport sits over the video and hides itself after [ControlsIdleMillis] of untouched
 * playback; a tap anywhere on the frame brings it back, and it stays up whenever the clip is not
 * running so a paused frame always shows a way to resume.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun EventPlayer(video: String) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val exoPlayer = remember(video) {
    ExoPlayer.Builder(context).build().apply {
      setMediaSource(eventMediaSource(video = video))
      prepare()
      playWhenReady = true
    }
  }
  var position by remember(video) { mutableLongStateOf(0L) }
  var duration by remember(video) { mutableLongStateOf(0L) }
  var buffering by remember(video) { mutableStateOf(true) }
  var playing by remember(video) { mutableStateOf(true) }
  var muted by remember(video) { mutableStateOf(false) }
  var ended by remember(video) { mutableStateOf(false) }
  var controlsShown by remember(video) { mutableStateOf(true) }
  var interactions by remember(video) { mutableIntStateOf(0) }
  val progress = if (duration > 0L) position.toFloat() / duration else 0f

  DisposableEffect(video) {
    val observer = LifecycleEventObserver { _, lifecycleEvent ->
      if (lifecycleEvent == Lifecycle.Event.ON_STOP) exoPlayer.pause()
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      exoPlayer.release()
    }
  }

  LaunchedEffect(exoPlayer) {
    while (true) {
      position = exoPlayer.currentPosition.coerceAtLeast(0L)
      duration = exoPlayer.duration.takeIf { it > 0L } ?: 0L
      buffering = exoPlayer.playbackState == Player.STATE_BUFFERING
      ended = exoPlayer.playbackState == Player.STATE_ENDED
      playing = exoPlayer.playWhenReady && !ended
      delay(timeMillis = PollIntervalMillis)
    }
  }

  LaunchedEffect(playing) {
    if (!playing) controlsShown = true
  }

  LaunchedEffect(controlsShown, playing, interactions) {
    if (!controlsShown || !playing) return@LaunchedEffect
    delay(timeMillis = ControlsIdleMillis)
    controlsShown = false
  }

  Box(
    modifier = Modifier
      .fullBleed(inset = ScreenPadding)
      .aspectRatio(ClipAspectRatio),
    contentAlignment = Alignment.Center,
  ) {
    AndroidView(
      factory = { viewContext ->
        PlayerView(viewContext).apply {
          useController = false
          resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
      },
      update = { view -> view.player = exoPlayer },
      modifier = Modifier.fillMaxSize(),
    )
    Box(
      modifier = Modifier
        .fillMaxSize()
        .pointerInput(exoPlayer) {
          detectTapGestures(
            onTap = { controlsShown = !controlsShown },
            onDoubleTap = { offset ->
              interactions += 1
              val step = if (offset.x < size.width / HalvesOfTheFrame) -StepMillis else StepMillis
              exoPlayer.seekTo((exoPlayer.currentPosition + step).coerceAtLeast(0L))
            },
          )
        },
    )
    if (buffering) LoadingBox()
    AnimatedVisibility(
      visible = controlsShown,
      modifier = Modifier.align(Alignment.Center),
      enter = fadeIn(),
      exit = fadeOut(),
    ) {
      PlayerCenterControls(
        playing = playing,
        ended = ended,
        onInteraction = { interactions += 1 },
        onPlayToggle = {
          when {
            exoPlayer.playbackState == Player.STATE_ENDED -> {
              exoPlayer.seekTo(0L)
              exoPlayer.play()
            }

            exoPlayer.playWhenReady -> exoPlayer.pause()
            else -> exoPlayer.play()
          }
          ended = exoPlayer.playbackState == Player.STATE_ENDED
          playing = exoPlayer.playWhenReady && !ended
        },
        onStep = { offset ->
          exoPlayer.seekTo((exoPlayer.currentPosition + offset).coerceAtLeast(0L))
        },
      )
    }
    AnimatedVisibility(
      visible = controlsShown,
      modifier = Modifier.align(Alignment.BottomCenter),
      enter = fadeIn(),
      exit = fadeOut(),
    ) {
      PlayerBottomBar(
        position = position,
        duration = duration,
        muted = muted,
        onInteraction = { interactions += 1 },
        onSeek = { millis -> exoPlayer.seekTo(millis) },
        onMuteToggle = {
          muted = !muted
          exoPlayer.volume = if (muted) SilentVolume else FullVolume
        },
      )
    }
    AnimatedVisibility(
      visible = !controlsShown,
      modifier = Modifier.align(Alignment.BottomCenter),
      enter = fadeIn(),
      exit = fadeOut(),
    ) {
      SeekTrack(progress = progress)
    }
  }
}

/**
 * The three transport buttons over the middle of the frame: skip back, play or pause, skip forward.
 * The skip glyphs carry no circle so the frame stays visible around them, leaving the violet
 * play/pause circle as the only filled shape and so the obvious target. A finished clip swaps that
 * middle glyph for a replay arrow, because [onPlayToggle] restarts rather than resumes there.
 */
@Composable
private fun PlayerCenterControls(
  playing: Boolean,
  ended: Boolean,
  onInteraction: () -> Unit,
  onPlayToggle: () -> Unit,
  onStep: (Long) -> Unit,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(CenterControlSpacing),
  ) {
    PlainIconButton(
      icon = Icons.Outlined.Replay10,
      contentDescription = "Back ten seconds",
      iconSize = SkipIconSize,
      onClick = {
        onInteraction()
        onStep(-StepMillis)
      },
    )
    CircleIconButton(
      icon = when {
        ended -> Icons.Outlined.Replay
        playing -> Icons.Filled.Pause
        else -> Icons.Filled.PlayArrow
      },
      contentDescription = when {
        ended -> "Replay"
        playing -> "Pause"
        else -> "Play"
      },
      onClick = {
        onInteraction()
        onPlayToggle()
      },
      size = PlayButtonSize,
      background = AppTheme.colors.accent,
      tint = AppTheme.colors.textPrimary,
    )
    PlainIconButton(
      icon = Icons.Outlined.Forward10,
      contentDescription = "Forward ten seconds",
      iconSize = SkipIconSize,
      onClick = {
        onInteraction()
        onStep(StepMillis)
      },
    )
  }
}

/**
 * The bottom edge of the frame: the `m:ss / m:ss` readout on the left, the mute toggle on the
 * right, and the seek bar flush beneath them, all over a scrim that darkens towards the edge so
 * they stay readable against a bright frame. Dragging the bar is held locally until the finger
 * lifts, so the polled [position] cannot fight the thumb under it.
 *
 * The opt-in is for `Slider`'s thumb and track slots, which are the only way to shrink it to a
 * video seek bar without fighting the Material defaults.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerBottomBar(
  position: Long,
  duration: Long,
  muted: Boolean,
  onInteraction: () -> Unit,
  onSeek: (Long) -> Unit,
  onMuteToggle: () -> Unit,
) {
  var scrubbed by remember { mutableStateOf<Float?>(null) }
  val total = duration.coerceAtLeast(1L)
  val shown = (scrubbed?.toLong() ?: position).coerceIn(0L, total)
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(
        brush = Brush.verticalGradient(colors = listOf(Color.Transparent, OverlayScrim)),
      ),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = OverlayHorizontalPadding, top = OverlayVerticalPadding),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "${clockTime(shown)} / ${clockTime(duration)}",
        style = MaterialTheme.typography.bodyMedium,
        color = AppTheme.colors.textPrimary,
        modifier = Modifier.weight(1f),
      )
      PlainIconButton(
        icon = if (muted) {
          Icons.AutoMirrored.Outlined.VolumeOff
        } else {
          Icons.AutoMirrored.Outlined.VolumeUp
        },
        contentDescription = if (muted) "Unmute" else "Mute",
        iconSize = MuteIconSize,
        onClick = {
          onInteraction()
          onMuteToggle()
        },
      )
    }
    Slider(
      value = shown.toFloat(),
      onValueChange = { value -> scrubbed = value },
      modifier = Modifier.fillMaxWidth(),
      valueRange = 0f..total.toFloat(),
      onValueChangeFinished = {
        onInteraction()
        scrubbed?.let { value -> onSeek(value.toLong()) }
        scrubbed = null
      },
      colors = SliderDefaults.colors(
        thumbColor = AppTheme.colors.accent,
        activeTrackColor = AppTheme.colors.accent,
        inactiveTrackColor = AppTheme.colors.textPrimary.copy(alpha = InactiveTrackAlpha),
      ),
      thumb = { SeekThumb() },
      track = { SeekTrack(progress = shown.toFloat() / total) },
    )
  }
}

/**
 * The seek bar's line: a hairline of white at low alpha with the played portion in accent. It is
 * both the slider's own track and, on its own, the residual progress line left behind when the
 * transport hides — which is why it draws no thumb and sits in a band of a fixed [SeekBarHeight],
 * so the two states line up rather than jumping as one fades into the other.
 */
@Composable
private fun SeekTrack(progress: Float) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(SeekBarHeight),
    contentAlignment = Alignment.CenterStart,
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(SeekTrackHeight)
        .clip(CircleShape)
        .background(color = AppTheme.colors.textPrimary.copy(alpha = InactiveTrackAlpha)),
    )
    Box(
      modifier = Modifier
        .fillMaxWidth(fraction = progress.coerceIn(0f, 1f))
        .height(SeekTrackHeight)
        .clip(CircleShape)
        .background(color = AppTheme.colors.accent),
    )
  }
}

/** The seek bar's handle, shrunk to a dot so the bar reads as a video scrubber rather than a form. */
@Composable
private fun SeekThumb() {
  Box(
    modifier = Modifier
      .size(SeekThumbSize)
      .clip(CircleShape)
      .background(color = AppTheme.colors.accent),
  )
}

/**
 * A transport glyph with nothing behind it, drawn in white over whatever the frame happens to show.
 * The tappable area stays the full button size even though [iconSize] is smaller, so a small glyph
 * is still comfortable to hit.
 */
@Composable
private fun PlainIconButton(
  icon: ImageVector,
  contentDescription: String,
  iconSize: Dp,
  onClick: () -> Unit,
) {
  IconButton(onClick = onClick, modifier = Modifier.size(PlainButtonSize)) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      tint = AppTheme.colors.textPrimary,
      modifier = Modifier.size(iconSize),
    )
  }
}

/**
 * Builds the source [video] is played from. An event clip is an HLS playlist whose segment lines
 * are relative and so carry none of the playlist URL's `?Authorization=` query parameter; asking
 * for them over a plain data source is what 401s. The token is therefore lifted out of the URL and
 * sent as an `Authorization` header on every request the player makes, exactly as the reference
 * app does, which covers the playlist and its segments alike. [PlaylistDataSourceFactory] then
 * terminates the playlist so the clip is seekable. The media source is inferred from the URL, so
 * an event that carries a plain MP4 instead still plays.
 */
@androidx.annotation.OptIn(UnstableApi::class)
private fun eventMediaSource(video: String): MediaSource {
  val uri = video.toUri()
  val token = uri.getQueryParameter(AuthorizationParameter).orEmpty()
  val httpFactory = DefaultHttpDataSource.Factory()
  if (token.isNotBlank()) {
    httpFactory.setDefaultRequestProperties(mapOf(AuthorizationParameter to token))
  }
  return DefaultMediaSourceFactory(PlaylistDataSourceFactory(httpFactory))
    .createMediaSource(MediaItem.fromUri(uri))
}

/**
 * The backend's plain-language account of the clip. Most events carry none, and an empty card
 * would read worse than no card, so the whole thing disappears when [contextBody] is blank.
 */
@Composable
private fun EventContext(contextBody: String?) {
  if (contextBody.isNullOrBlank()) return
  GroupCard {
    Column(
      modifier = Modifier.padding(ContextPadding),
      verticalArrangement = Arrangement.spacedBy(ContextSpacing),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ContextHeaderSpacing),
      ) {
        Icon(
          imageVector = Icons.Outlined.AutoAwesome,
          contentDescription = null,
          tint = AppTheme.colors.success,
          modifier = Modifier.size(ContextIconSize),
        )
        Text(
          text = ContextHeading.uppercase(),
          style = MaterialTheme.typography.labelSmall,
          color = AppTheme.colors.textTertiary,
        )
      }
      Text(
        text = contextBody,
        style = MaterialTheme.typography.bodyLarge,
        color = AppTheme.colors.textPrimary,
      )
    }
  }
}

/** The read-only facts about the event: when it happened, how long it ran and what was detected. */
@Composable
private fun EventSummary(event: Event, style: EventTagStyle) {
  Column {
    SectionHeader(text = "Event")
    GroupCard {
      SettingRow(label = "When", value = absoluteTime(event.startTime))
      RowDivider()
      SettingRow(label = "Length", value = eventDuration(event))
      RowDivider()
      SettingRow(label = "Camera", value = event.deviceName.orEmpty().ifBlank { "Camera" })
      RowDivider()
      SettingRow(
        label = "Detected",
        trailing = {
          StatusPill(
            text = style.label,
            containerColor = AppTheme.colors.accentSoft,
            contentColor = AppTheme.colors.accent,
          )
        },
      )
    }
  }
}

/** Asks whether the detection was right; the answered side stays filled once a verdict is in. */
@Composable
private fun FeedbackSection(accurate: Boolean?, enabled: Boolean, onFeedback: (Boolean) -> Unit) {
  Column {
    SectionHeader(text = "Was this right?")
    GroupCard {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(FeedbackPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FeedbackSpacing),
      ) {
        Text(
          text = "Telling us helps improve detection for this space.",
          style = MaterialTheme.typography.bodyMedium,
          color = AppTheme.colors.textSecondary,
          modifier = Modifier.weight(1f),
        )
        VerdictButton(
          icon = Icons.Outlined.ThumbUp,
          contentDescription = "Detection was right",
          chosen = accurate == true,
          tint = AppTheme.colors.success,
          chosenBackground = AppTheme.colors.successContainer,
          enabled = enabled,
          onClick = { onFeedback(true) },
        )
        VerdictButton(
          icon = Icons.Outlined.ThumbDown,
          contentDescription = "Detection was wrong",
          chosen = accurate == false,
          tint = AppTheme.colors.danger,
          chosenBackground = AppTheme.colors.dangerContainer,
          enabled = enabled,
          onClick = { onFeedback(false) },
        )
      }
    }
  }
}

/** One side of the feedback pair, filled with its own colour once it is the answer given. */
@Composable
private fun VerdictButton(
  icon: ImageVector,
  contentDescription: String,
  chosen: Boolean,
  tint: Color,
  chosenBackground: Color,
  enabled: Boolean,
  onClick: () -> Unit,
) {
  CircleIconButton(
    icon = icon,
    contentDescription = contentDescription,
    onClick = { if (enabled) onClick() },
    size = FeedbackButtonSize,
    background = if (chosen) chosenBackground else AppTheme.colors.surfaceHigh,
    tint = if (enabled) tint else AppTheme.colors.textTertiary,
  )
}

/**
 * A header chevron that walks the loaded list. `CircleIconButton` has no disabled state, so the
 * end of the list is shown by greying the glyph and swallowing the tap.
 */
@Composable
private fun StepButton(
  icon: ImageVector,
  contentDescription: String,
  enabled: Boolean,
  onClick: () -> Unit,
) {
  CircleIconButton(
    icon = icon,
    contentDescription = contentDescription,
    onClick = { if (enabled) onClick() },
    tint = if (enabled) AppTheme.colors.textPrimary else AppTheme.colors.textTertiary,
  )
}

/**
 * Lets the clip escape the horizontal [inset] the scaffold padded it with, so the video runs to
 * both screen edges while everything around it stays in the padded column. The node still reports
 * the padded width, which is what keeps its siblings where they were.
 */
private fun Modifier.fullBleed(inset: Dp): Modifier = layout { measurable, constraints ->
  val bleed = if (constraints.hasBoundedWidth) inset.roundToPx() else 0
  val widened = constraints.copy(
    minWidth = constraints.minWidth + bleed * 2,
    maxWidth = constraints.maxWidth + bleed * 2,
  )
  val placeable = measurable.measure(widened)
  layout(width = placeable.width - bleed * 2, height = placeable.height) {
    placeable.place(x = -bleed, y = 0)
  }
}

/** The event's start as a calendar date and a clock time, such as `"Aug 31, 2026 at 22:01"`. */
private fun absoluteTime(startTime: Long): String = Instant.ofEpochMilli(startTime)
  .atZone(ZoneId.systemDefault())
  .format(TimestampFormatter)

/** A playback offset as `m:ss`, which is as long as any clip the cameras record ever gets. */
private fun clockTime(millis: Long): String {
  val seconds = (millis / MillisInSecond).coerceAtLeast(0L)
  val remainder = (seconds % SecondsInMinute).toString().padStart(SecondDigits, '0')
  return "${seconds / SecondsInMinute}:$remainder"
}

/** Pattern behind [absoluteTime]; the literal "at" is quoted so it is not read as a field. */
private val TimestampFormatter: DateTimeFormatter =
  DateTimeFormatter.ofPattern("MMM d, yyyy 'at' HH:mm")

/** Aspect ratio of the clip stage, matching the cameras' own framing. */
private const val ClipAspectRatio = 16f / 9f

/** How long a transient confirmation stays on screen before it clears itself. */
private const val MessageVisibleMillis = 3_000L

/** How often the transport re-reads the player's position, duration and state. */
private const val PollIntervalMillis = 250L

/** How far the skip buttons and a double tap move the playhead. */
private const val StepMillis = 10_000L

/** Divisor splitting the frame into the two halves a double tap seeks backwards or forwards in. */
private const val HalvesOfTheFrame = 2f

/** How long the overlaid transport stays up during playback once the user stops touching it. */
private const val ControlsIdleMillis = 3_000L

/**
 * Query parameter the clip URL is signed with, and the header the same token is resent under so
 * the playlist's relative segments are authorised too.
 */
private const val AuthorizationParameter = "Authorization"

/** Player volume while the clip is muted. */
private const val SilentVolume = 0f

/** Player volume while the clip is not muted. */
private const val FullVolume = 1f

/** Milliseconds in one second, used to turn a playback offset into a clock reading. */
private const val MillisInSecond = 1_000L

/** Seconds in one minute, used to turn a playback offset into a clock reading. */
private const val SecondsInMinute = 60L

/** Digits the seconds half of a clock reading is padded to. */
private const val SecondDigits = 2

/** Heading of the card describing what the backend saw; uppercased like a section header. */
private const val ContextHeading = "What happened"

/** Darkest stop of the scrim the transport sits on, at the very bottom of the video. */
private val OverlayScrim = Color.Black.copy(alpha = 0.65f)

/** How faint the unplayed part of the seek bar is drawn over the video. */
private const val InactiveTrackAlpha = 0.35f

/** Inset holding the time readout clear of the video's left edge. */
private val OverlayHorizontalPadding = 16.dp

/** Gap above the time readout, separating it from the frame it sits over. */
private val OverlayVerticalPadding = 8.dp

/** Gap between the three central transport buttons, wide enough to read as separate targets. */
private val CenterControlSpacing = 28.dp

/** Height of the band the seek bar occupies, shared with the residual line so the two align. */
private val SeekBarHeight = 16.dp

/** Thickness of the seek bar's line. */
private val SeekTrackHeight = 3.dp

/** Diameter of the seek bar's handle. */
private val SeekThumbSize = 12.dp

/** Tappable size of a transport glyph that has no circle behind it. */
private val PlainButtonSize = 48.dp

/** Size of a skip glyph, drawn large because it carries no circle to anchor it. */
private val SkipIconSize = 34.dp

/** Size of the mute glyph, which sits in a row of text rather than beside the play button. */
private val MuteIconSize = 22.dp

/** Diameter of the play/pause button, the largest control on the screen. */
private val PlayButtonSize = 72.dp

/** Size of the glyph shown in place of a missing still frame. */
private val PlaceholderIconSize = 32.dp

/** Padding inside the "what happened" card. */
private val ContextPadding = 16.dp

/** Gap between the "what happened" heading and its body. */
private val ContextSpacing = 8.dp

/** Gap between the "what happened" glyph and its heading. */
private val ContextHeaderSpacing = 8.dp

/** Size of the glyph beside the "what happened" heading. */
private val ContextIconSize = 20.dp

/** Padding inside the feedback row. */
private val FeedbackPadding = 16.dp

/** Gap between the feedback copy and the two verdict buttons. */
private val FeedbackSpacing = 12.dp

/** Diameter of a verdict button. */
private val FeedbackButtonSize = 48.dp
