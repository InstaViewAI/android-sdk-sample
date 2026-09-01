package ai.instavision.sandbox.ui.camera

import ai.instavision.sandbox.ui.common.CircleIconButton
import ai.instavision.sandbox.ui.common.EmptyState
import ai.instavision.sandbox.ui.common.ErrorBanner
import ai.instavision.sandbox.ui.common.GroupCard
import ai.instavision.sandbox.ui.common.Notice
import ai.instavision.sandbox.ui.common.ScreenPadding
import ai.instavision.sandbox.ui.common.SecondaryButton
import ai.instavision.sandbox.ui.common.ToggleRow
import ai.instavision.sandbox.ui.theme.AppTheme
import ai.instavision.guardian.sdk.data.entity.aws.StreamStatus
import ai.instavision.guardian.sdk.webrtc.TextureViewRenderer
import android.Manifest
import android.content.pm.PackageManager
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** How long a transient confirmation stays on screen before it clears itself. */
private const val MESSAGE_VISIBLE_MS = 3_000L

/** Shape of the video, which every camera in the range streams in. */
private const val VIDEO_ASPECT_RATIO = 16f / 9f

/** Opacity of the translucent white the player's chrome is drawn on. */
private const val CHROME_SCRIM_ALPHA = 0.12f

/** Opacity of the fill behind the floating pan/tilt pad, which sits directly over the picture. */
private const val PAD_SCRIM_ALPHA = 0.32f

/**
 * How long a pan/tilt key must be held before the press becomes a continuous move. Anything
 * shorter is a tap, which nudges the camera one step instead.
 */
private const val PTZ_HOLD_MS = 400L

/**
 * Full-bleed live view for the camera selected on the home screen, with two-way audio, snapshots,
 * recording, siren and pan/tilt. `MainActivity` reports foreground state through
 * `InstaVision.setAppInForeground`, which is what keeps the microphone gate open for two-way audio
 * while this screen is visible. The chrome carries the window insets so the video itself can stay
 * edge to edge behind them.
 */
@Composable
fun CameraDetailScreen(
  onBack: () -> Unit,
  onSettings: () -> Unit,
) {
  val viewModel: CameraDetailViewModel = viewModel()
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val players by viewModel.players.collectAsStateWithLifecycle()
  val renderer = players.firstOrNull()
  val context = LocalContext.current
  var micGranted by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED,
    )
  }
  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions(),
  ) { results ->
    micGranted = results[Manifest.permission.RECORD_AUDIO] == true
    if (micGranted) viewModel.setMicEnabled(true)
  }

  LaunchedEffect(state.message) {
    if (state.message != null) {
      delay(timeMillis = MESSAGE_VISIBLE_MS)
      viewModel.consumeMessage()
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(color = Color.Black),
  ) {
    if (state.hasDevice) {
      VideoStage(
        state = state,
        renderer = renderer,
        onRetry = viewModel::retry,
        onMove = viewModel::move,
        onStopMove = viewModel::stopMove,
        modifier = Modifier.align(Alignment.Center),
      )
    } else {
      EmptyState(
        title = "No camera selected",
        body = "Pick a camera on the home screen to watch it live",
        icon = Icons.Outlined.VideocamOff,
        modifier = Modifier
          .align(Alignment.Center)
          .padding(horizontal = ScreenPadding),
      )
    }
    PlayerHeader(
      state = state,
      onBack = onBack,
      onSettings = onSettings,
      modifier = Modifier
        .align(Alignment.TopCenter)
        .safeDrawingPadding(),
    )
    if (state.hasDevice) {
      PlayerFooter(
        state = state,
        onSpeaker = viewModel::setSpeakerEnabled,
        onSnapshot = {
          viewModel.saveSnapshot(bitmap = renderer?.bitmap, resolver = context.contentResolver)
        },
        onRecord = viewModel::toggleRecording,
        onMic = { enabled ->
          if (!enabled || micGranted) {
            viewModel.setMicEnabled(enabled)
          } else {
            permissionLauncher.launch(
              arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA),
            )
          }
        },
        onSiren = viewModel::toggleSiren,
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .safeDrawingPadding(),
      )
    }
  }
}

/**
 * The video and everything drawn directly on it, including the floating pan/tilt pad. The renderer
 * the SDK created may still be attached to a previous host after a recomposition, so it is
 * detached from its old parent before being added — skipping that throws "child already has a
 * parent".
 */
@Composable
private fun VideoStage(
  state: CameraDetailUiState,
  renderer: TextureViewRenderer?,
  onRetry: () -> Unit,
  onMove: (PtzDirection, Boolean) -> Unit,
  onStopMove: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val stalled = state.status.isStalled()
  Box(
    modifier = modifier
      .fillMaxWidth()
      .aspectRatio(ratio = VIDEO_ASPECT_RATIO),
    contentAlignment = Alignment.Center,
  ) {
    AndroidView(
      modifier = Modifier.fillMaxSize(),
      factory = { context -> FrameLayout(context) },
      update = { host ->
        host.removeAllViews()
        renderer?.let { view ->
          (view.parent as? ViewGroup)?.removeView(view)
          host.addView(view)
        }
      },
      onRelease = { host -> host.removeAllViews() },
    )
    if (!state.hasLoadedStream && !stalled) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(space = StageSpacing),
      ) {
        CircularProgressIndicator(color = AppTheme.colors.accent)
        Text(
          text = "Connecting to ${state.deviceName.ifBlank { "camera" }}…",
          style = MaterialTheme.typography.bodyMedium,
          color = Color.White,
        )
      }
    }
    if (stalled) {
      SecondaryButton(
        text = "Reconnect",
        onClick = onRetry,
        modifier = Modifier.padding(horizontal = ScreenPadding),
      )
    }
    if (state.supportsPtz && state.hasLoadedStream) {
      PtzPad(onMove = onMove, onStop = onStopMove)
    }
  }
}

/** The chrome over the top of the player: back, the camera and its live status, then settings. */
@Composable
private fun PlayerHeader(
  state: CameraDetailUiState,
  onBack: () -> Unit,
  onSettings: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = ScreenPadding, vertical = ChromePadding),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(space = ChromeSpacing),
  ) {
    CircleIconButton(
      icon = Icons.Rounded.ChevronLeft,
      contentDescription = "Back",
      onClick = onBack,
      background = ChromeScrim,
      tint = Color.White,
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = state.deviceName.ifBlank { "Live view" },
        style = MaterialTheme.typography.headlineSmall,
        color = Color.White,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = StatusSpacing),
      ) {
        Box(
          modifier = Modifier
            .size(size = StatusDotSize)
            .clip(CircleShape)
            .background(color = state.statusColor()),
        )
        Text(
          text = state.statusLabel(),
          style = MaterialTheme.typography.bodyMedium,
          color = AppTheme.colors.textSecondary,
        )
      }
    }
    CircleIconButton(
      icon = Icons.Outlined.Settings,
      contentDescription = "Camera settings",
      onClick = onSettings,
      background = ChromeScrim,
      tint = Color.White,
    )
  }
}

/**
 * Everything pinned under the player: the transient banners, the four controls every camera has,
 * and the siren of the models that carry one. Pan/tilt is not here — it rides on the picture.
 */
@Composable
private fun PlayerFooter(
  state: CameraDetailUiState,
  onSpeaker: (Boolean) -> Unit,
  onSnapshot: () -> Unit,
  onRecord: () -> Unit,
  onMic: (Boolean) -> Unit,
  onSiren: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = ScreenPadding, vertical = ChromePadding),
    verticalArrangement = Arrangement.spacedBy(space = ChromeSpacing),
  ) {
    Notice(message = state.message)
    ErrorBanner(message = state.error)
    ControlRow(
      state = state,
      onSpeaker = onSpeaker,
      onSnapshot = onSnapshot,
      onRecord = onRecord,
      onMic = onMic,
    )
    if (state.supportsSiren) {
      GroupCard {
        ToggleRow(
          title = "Siren",
          checked = state.sirenOn,
          onCheckedChange = { onSiren() },
          icon = Icons.Outlined.Campaign,
          description = "Sound the camera's alarm",
          enabled = state.hasLoadedStream,
        )
      }
    }
  }
}

/** The four controls every camera has, all inert until the first frame has been drawn. */
@Composable
private fun ControlRow(
  state: CameraDetailUiState,
  onSpeaker: (Boolean) -> Unit,
  onSnapshot: () -> Unit,
  onRecord: () -> Unit,
  onMic: (Boolean) -> Unit,
) {
  val enabled = state.hasLoadedStream
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceEvenly,
  ) {
    ControlButton(
      icon = if (state.speakerEnabled) {
        Icons.AutoMirrored.Outlined.VolumeUp
      } else {
        Icons.AutoMirrored.Outlined.VolumeOff
      },
      contentDescription = if (state.speakerEnabled) "Mute the camera" else "Listen to the camera",
      enabled = enabled,
      onClick = { onSpeaker(!state.speakerEnabled) },
    )
    ControlButton(
      icon = Icons.Outlined.PhotoCamera,
      contentDescription = "Save a snapshot",
      enabled = enabled,
      onClick = onSnapshot,
    )
    ControlButton(
      icon = Icons.Outlined.RadioButtonChecked,
      contentDescription = if (state.recording) "Stop recording" else "Start recording",
      enabled = enabled,
      onClick = onRecord,
      tint = if (state.recording) AppTheme.colors.danger else Color.White,
    )
    ControlButton(
      icon = if (state.micEnabled) Icons.Outlined.Mic else Icons.Outlined.MicOff,
      contentDescription = if (state.micEnabled) "Stop talking" else "Talk to the camera",
      enabled = enabled,
      onClick = { onMic(!state.micEnabled) },
    )
  }
}

/**
 * One button of [ControlRow]. The design system's chip has no disabled state of its own, so a
 * control that is not ready yet is greyed out and swallows its taps instead.
 */
@Composable
private fun ControlButton(
  icon: ImageVector,
  contentDescription: String,
  enabled: Boolean,
  onClick: () -> Unit,
  tint: Color = Color.White,
) {
  CircleIconButton(
    icon = icon,
    contentDescription = contentDescription,
    onClick = { if (enabled) onClick() },
    size = ControlButtonSize,
    background = ChromeScrim,
    tint = if (enabled) tint else AppTheme.colors.textTertiary,
  )
}

/**
 * The floating pan/tilt pad, laid over the picture where a camera that can turn is being watched.
 * It starts against the right edge and is dragged anywhere over the video by its middle key, so it
 * can be moved off whatever the viewer is trying to look at; the travel is clamped to the picture
 * so it can never be pushed out of reach.
 */
@Composable
private fun PtzPad(
  onMove: (PtzDirection, Boolean) -> Unit,
  onStop: () -> Unit,
) {
  var stageSize by remember { mutableStateOf(IntSize.Zero) }
  var padSize by remember { mutableStateOf(IntSize.Zero) }
  var offsetX by remember { mutableFloatStateOf(0f) }
  var offsetY by remember { mutableFloatStateOf(0f) }
  val slackX = (stageSize.width - padSize.width).coerceAtLeast(minimumValue = 0).toFloat()
  val slackY = (stageSize.height - padSize.height).coerceAtLeast(minimumValue = 0).toFloat() / 2f
  Box(
    modifier = Modifier
      .fillMaxSize()
      .onSizeChanged { size -> stageSize = size },
  ) {
    Column(
      modifier = Modifier
        .align(Alignment.CenterEnd)
        .offset { IntOffset(x = offsetX.roundToInt(), y = offsetY.roundToInt()) }
        .onSizeChanged { size -> padSize = size }
        .padding(all = PadMargin)
        .clip(MaterialTheme.shapes.large)
        .background(color = PadScrim)
        .padding(all = PadInset),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(space = PadSpacing),
    ) {
      PadKey(
        icon = Icons.Rounded.KeyboardArrowUp,
        contentDescription = "Tilt up",
        direction = PtzDirection.Up,
        onMove = onMove,
        onStop = onStop,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(space = PadSpacing)) {
        PadKey(
          icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
          contentDescription = "Pan left",
          direction = PtzDirection.Left,
          onMove = onMove,
          onStop = onStop,
        )
        PadHandle(
          onDrag = { deltaX, deltaY ->
            offsetX = (offsetX + deltaX).coerceIn(minimumValue = -slackX, maximumValue = 0f)
            offsetY = (offsetY + deltaY).coerceIn(minimumValue = -slackY, maximumValue = slackY)
          },
        )
        PadKey(
          icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
          contentDescription = "Pan right",
          direction = PtzDirection.Right,
          onMove = onMove,
          onStop = onStop,
        )
      }
      PadKey(
        icon = Icons.Rounded.KeyboardArrowDown,
        contentDescription = "Tilt down",
        direction = PtzDirection.Down,
        onMove = onMove,
        onStop = onStop,
      )
    }
  }
}

/**
 * One direction key of [PtzPad]. A tap sends a single step, while a press held past [PTZ_HOLD_MS]
 * turns into one continuous move that runs until the finger lifts — so a long press costs the
 * service two calls however long it lasts.
 *
 * The terminating stop is sent from `finally`, which also covers the pad leaving composition
 * mid-press: the gesture coroutine is cancelled there, and without it the camera would keep
 * turning with nothing left on screen to stop it.
 */
@Composable
private fun PadKey(
  icon: ImageVector,
  contentDescription: String,
  direction: PtzDirection,
  onMove: (PtzDirection, Boolean) -> Unit,
  onStop: () -> Unit,
) {
  val interactions = remember { MutableInteractionSource() }
  val pressed by interactions.collectIsPressedAsState()
  val currentMove by rememberUpdatedState(newValue = onMove)
  val currentStop by rememberUpdatedState(newValue = onStop)
  var pressSeen by remember { mutableStateOf(false) }
  var holding by remember { mutableStateOf(false) }

  LaunchedEffect(pressed) {
    if (pressed) {
      pressSeen = true
      delay(timeMillis = PTZ_HOLD_MS)
      holding = true
      currentMove(direction, true)
    } else if (pressSeen) {
      pressSeen = false
      if (holding) {
        holding = false
        currentStop()
      } else {
        currentMove(direction, false)
      }
    }
  }

  DisposableEffect(Unit) {
    onDispose { if (holding) currentStop() }
  }

  Box(
    modifier = Modifier
      .size(size = PadKeySize)
      .clickable(
        interactionSource = interactions,
        indication = null,
        onClick = {},
      ),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      tint = Color.White,
    )
  }
}

/** The middle key of [PtzPad], which drives nothing and only drags the pad around the picture. */
@Composable
private fun PadHandle(onDrag: (deltaX: Float, deltaY: Float) -> Unit) {
  val currentDrag by rememberUpdatedState(newValue = onDrag)
  Box(
    modifier = Modifier
      .size(size = PadKeySize)
      .pointerInput(Unit) {
        detectDragGestures { change, dragAmount ->
          change.consume()
          currentDrag(dragAmount.x, dragAmount.y)
        }
      },
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = Icons.Outlined.OpenWith,
      contentDescription = "Move the pan and tilt pad",
      tint = AppTheme.colors.textSecondary,
      modifier = Modifier.size(size = PadHandleIconSize),
    )
  }
}

/** Whether the stream has stopped in a way only a fresh connection recovers from. */
private fun StreamStatus.isStalled(): Boolean =
  this is StreamStatus.FAILED || this is StreamStatus.AUTO_DISCONNECT

/** Colour of the status dot: green once frames arrive, amber while connecting, red once stopped. */
@Composable
private fun CameraDetailUiState.statusColor(): Color = when (status) {
  is StreamStatus.CONNECTING -> AppTheme.colors.warning
  is StreamStatus.STARTED ->
    if (hasLoadedStream) AppTheme.colors.success else AppTheme.colors.warning
  is StreamStatus.AUTO_DISCONNECT, is StreamStatus.FAILED -> AppTheme.colors.danger
}

/** Human-readable connection state, preferring the reason the SDK gave for a failure. */
private fun CameraDetailUiState.statusLabel(): String = when (val current = status) {
  is StreamStatus.CONNECTING -> "Connecting to camera"
  is StreamStatus.STARTED -> if (hasLoadedStream) "Live" else "Starting stream"
  is StreamStatus.AUTO_DISCONNECT -> "Disconnected to save battery"
  is StreamStatus.FAILED -> current.message ?: "Stream failed"
}

/** The translucent white fill every chip and button of the player chrome sits on. */
private val ChromeScrim = Color.White.copy(alpha = CHROME_SCRIM_ALPHA)

/** Vertical breathing room between the chrome and the edge of the safe area. */
private val ChromePadding = 12.dp

/** Gap between the parts of the top chrome, and between the stacked parts of the bottom chrome. */
private val ChromeSpacing = 12.dp

/** Gap between the connecting spinner and the line under it. */
private val StageSpacing = 12.dp

/** Gap between the status dot and its label. */
private val StatusSpacing = 8.dp

/** Diameter of the status dot beside the camera name. */
private val StatusDotSize = 8.dp

/** Diameter of a control in the player's bottom row. */
private val ControlButtonSize = 56.dp

/** Fill the floating pan/tilt pad is drawn on, darker than the chrome so keys stay legible. */
private val PadScrim = Color.Black.copy(alpha = PAD_SCRIM_ALPHA)

/** Gap between the keys of the pan/tilt pad. */
private val PadSpacing = 4.dp

/** Distance the pan/tilt pad keeps from the edge of the picture before it is dragged. */
private val PadMargin = 12.dp

/** Breathing room between the pad's keys and its own edge. */
private val PadInset = 4.dp

/** Touch target of one key of the pan/tilt pad. */
private val PadKeySize = 44.dp

/** Size of the grab icon in the middle of the pan/tilt pad, kept smaller than the arrows. */
private val PadHandleIconSize = 18.dp
