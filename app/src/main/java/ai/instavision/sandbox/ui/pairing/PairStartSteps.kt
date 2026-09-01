package ai.instavision.sandbox.ui.pairing

import ai.instavision.sandbox.ui.common.ErrorBanner
import ai.instavision.sandbox.ui.common.GroupCard
import ai.instavision.sandbox.ui.common.PrimaryButton
import ai.instavision.sandbox.ui.common.RowDivider
import ai.instavision.sandbox.ui.common.SecondaryButton
import ai.instavision.sandbox.ui.common.TextLink
import ai.instavision.sandbox.ui.permissions.BluetoothPermissions
import ai.instavision.sandbox.ui.theme.AppTheme
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BluetoothDisabled
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/** What a camera that has just been plugged in should be doing before setup can go on. */
@Composable
internal fun PowerOnPage(
  onBack: () -> Unit,
  onExit: () -> Unit,
  onBlinking: () -> Unit,
  onNotBlinking: () -> Unit,
) {
  WizardPage(
    step = PairPage.PowerOn.wizardStep,
    title = "Power on your camera",
    subtitle = "Plug the camera in and wait for it to finish starting up.",
    onBack = onBack,
    onExit = onExit,
    bottom = {
      PrimaryButton(text = "The light is blinking blue", onClick = onBlinking)
      SecondaryButton(text = "The light is off or a different colour", onClick = onNotBlinking)
    },
  ) {
    WizardEmblem(icon = Icons.Outlined.Power)
    WizardTips(
      tips = listOf(
        "Connect the power adapter, or hold the power button for three seconds on a battery " +
          "model.",
        "Wait for the status light to blink blue — that can take up to a minute.",
        "Keep the camera within arm's reach of your phone for the rest of setup.",
      ),
    )
  }
}

/** The pinhole reset, which is what a camera that was set up before needs before it can be again. */
@Composable
internal fun ResetCameraPage(
  onBack: () -> Unit,
  onExit: () -> Unit,
  onDone: () -> Unit,
  onStillStuck: () -> Unit,
) {
  WizardPage(
    step = PairPage.ResetCamera.wizardStep,
    title = "Reset the camera",
    subtitle = "A reset clears the previous network so the camera can be set up again.",
    onBack = onBack,
    onExit = onExit,
    bottom = {
      PrimaryButton(text = "Done — the light is blinking", onClick = onDone)
      SecondaryButton(text = "Still not working", onClick = onStillStuck)
    },
  ) {
    WizardEmblem(icon = Icons.Outlined.RestartAlt)
    WizardTips(
      tips = listOf(
        "Find the pinhole marked RESET on the back or underside of the camera.",
        "Press and hold it with the reset pin for ten seconds.",
        "Let go when you hear the chime and the light starts blinking blue.",
      ),
    )
  }
}

/**
 * The Bluetooth scan, counting what it has turned up as it goes. The grants the scan needs are
 * asked for on arrival, and a refusal or a switched-off radio replaces the ring with the reason,
 * because neither is something the scan can work around.
 */
@Composable
internal fun SearchingPage(
  state: PairCameraUiState,
  onBack: () -> Unit,
  onExit: () -> Unit,
  onPermissionsResult: (Boolean, Boolean) -> Unit,
  onChoose: () -> Unit,
  onGenerateCode: () -> Unit,
) {
  val context = LocalContext.current
  var asked by remember { mutableStateOf(false) }
  val launcher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions(),
  ) {
    asked = true
    onPermissionsResult(context.hasBluetoothPermissions(), context.isBluetoothOn())
  }

  LaunchedEffect(Unit) {
    if (context.hasBluetoothPermissions()) {
      asked = true
      onPermissionsResult(true, context.isBluetoothOn())
    } else {
      launcher.launch(BluetoothPermissions.toTypedArray())
    }
  }

  val blocked = asked && (!state.permissionsGranted || !state.bluetoothEnabled)
  WizardPage(
    step = PairPage.Searching.wizardStep,
    title = "Looking for your camera",
    subtitle = "Keep your phone close to the camera while we search.",
    onBack = onBack,
    onExit = onExit,
    bottom = {
      if (blocked) {
        PrimaryButton(
          text = "Try again",
          onClick = {
            if (context.hasBluetoothPermissions()) {
              onPermissionsResult(true, context.isBluetoothOn())
            } else {
              launcher.launch(BluetoothPermissions.toTypedArray())
            }
          },
        )
      } else {
        PrimaryButton(
          text = "Choose a camera",
          onClick = onChoose,
          enabled = state.cameras.isNotEmpty(),
        )
      }
      SecondaryButton(text = "Set up with a QR code instead", onClick = onGenerateCode)
    },
  ) {
    ErrorBanner(message = state.error)
    if (blocked) {
      WizardEmblem(icon = Icons.Outlined.BluetoothDisabled, warning = true)
      CentredNote(text = blockedReason(state = state))
    } else {
      Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        ScanRing(count = state.cameras.size, scanning = state.step != PairingStep.Failed)
      }
      CentredNote(text = "This usually takes about 20 seconds.")
    }
  }
}

/** The cameras the scan turned up, strongest signal first, one of which is about to be paired. */
@Composable
internal fun PickCameraPage(
  state: PairCameraUiState,
  onBack: () -> Unit,
  onExit: () -> Unit,
  onSelect: (DiscoveredCamera) -> Unit,
  onNotListed: () -> Unit,
) {
  WizardPage(
    step = PairPage.PickCamera.wizardStep,
    title = "Pick your camera",
    subtitle = "The one closest to your phone is listed first.",
    onBack = onBack,
    onExit = onExit,
  ) {
    ErrorBanner(message = state.error)
    GroupCard {
      state.cameras.forEachIndexed { index, camera ->
        if (index > 0) RowDivider()
        CameraRow(camera = camera, onClick = { onSelect(camera) })
      }
    }
    TextLink(text = "My camera is not listed", onClick = onNotListed)
  }
}

/** What to check when the scan found nothing, with both ways of trying again underneath. */
@Composable
internal fun NoCameraFoundPage(
  onBack: () -> Unit,
  onExit: () -> Unit,
  onSearchAgain: () -> Unit,
  onGenerateCode: () -> Unit,
) {
  WizardPage(
    step = PairPage.NoCameraFound.wizardStep,
    title = "No camera found",
    subtitle = "We could not pick up a camera in setup mode nearby.",
    onBack = onBack,
    onExit = onExit,
    bottom = {
      PrimaryButton(text = "Search again", onClick = onSearchAgain)
      SecondaryButton(text = "Set up with a QR code instead", onClick = onGenerateCode)
    },
  ) {
    WizardEmblem(icon = Icons.Outlined.WifiTethering, warning = true)
    WizardTips(
      tips = listOf(
        "Check the status light is blinking blue. If it is not, reset the camera.",
        "Move the phone within a metre of the camera.",
        "Make sure nobody else is setting up the same camera right now.",
      ),
    )
  }
}

/** One camera of the picker: its name, how well the phone hears it, and a way into pairing it. */
@Composable
private fun CameraRow(
  camera: DiscoveredCamera,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(all = RowPadding),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(space = RowSpacing),
  ) {
    Box(
      modifier = Modifier
        .size(RowEmblemSize)
        .clip(CircleShape)
        .background(color = AppTheme.colors.accentSoft),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = Icons.Outlined.Videocam,
        contentDescription = null,
        tint = AppTheme.colors.accent,
        modifier = Modifier.size(RowIconSize),
      )
    }
    Text(
      text = camera.label,
      style = MaterialTheme.typography.bodyLarge,
      color = AppTheme.colors.textPrimary,
      modifier = Modifier.weight(1f),
    )
    SignalBars(rssi = camera.result.rssi)
    Icon(
      imageVector = Icons.Rounded.ChevronRight,
      contentDescription = null,
      tint = AppTheme.colors.textTertiary,
      modifier = Modifier.size(RowIconSize),
    )
  }
}

/**
 * The scan's own illustration: a track with an accent arc travelling round it while the radio is
 * listening, and the running count of what it has heard in the middle.
 */
@Composable
private fun ScanRing(
  count: Int,
  scanning: Boolean,
) {
  val transition = rememberInfiniteTransition(label = "scan")
  val sweepStart by transition.animateFloat(
    initialValue = 0f,
    targetValue = FullTurn,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = RingSpinMillis, easing = LinearEasing),
    ),
    label = "sweep",
  )
  val track = AppTheme.colors.surfaceHigh
  val arc = AppTheme.colors.accent
  Box(modifier = Modifier.size(RingSize), contentAlignment = Alignment.Center) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val stroke = Stroke(width = RingStroke.toPx(), cap = StrokeCap.Round)
      val inset = stroke.width / 2
      val arcSize = Size(width = size.width - stroke.width, height = size.height - stroke.width)
      drawArc(
        color = track,
        startAngle = 0f,
        sweepAngle = FullTurn,
        useCenter = false,
        topLeft = Offset(x = inset, y = inset),
        size = arcSize,
        style = stroke,
      )
      if (scanning) {
        drawArc(
          color = arc,
          startAngle = sweepStart,
          sweepAngle = RingSweep,
          useCenter = false,
          topLeft = Offset(x = inset, y = inset),
          size = arcSize,
          style = stroke,
        )
      }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        text = "$count",
        style = MaterialTheme.typography.displaySmall,
        color = AppTheme.colors.textPrimary,
      )
      Text(
        text = "cameras found",
        style = MaterialTheme.typography.bodyMedium,
        color = AppTheme.colors.textSecondary,
      )
    }
  }
}

/** A line of quiet centred copy under an illustration. */
@Composable
private fun CentredNote(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodyMedium,
    color = AppTheme.colors.textSecondary,
    textAlign = TextAlign.Center,
    modifier = Modifier.fillMaxWidth(),
  )
}

/** Which of the two things the scan cannot work around is currently in the way. */
private fun blockedReason(state: PairCameraUiState): String = if (!state.permissionsGranted) {
  "Finding cameras needs the Bluetooth permission. Allow it to carry on with setup."
} else {
  "Bluetooth is switched off. Turn it on in Settings, then try again."
}

/** Whether every entry of [BluetoothPermissions] has already been granted to the app. */
private fun Context.hasBluetoothPermissions(): Boolean = BluetoothPermissions.all { permission ->
  ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

/** Whether the radio is switched on; the SDK's scan quietly finds nothing while it is off. */
private fun Context.isBluetoothOn(): Boolean =
  getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true

/** A whole turn in degrees, which the ring's track covers and its arc travels. */
private const val FullTurn = 360f

/** Portion of the ring the travelling arc covers. */
private const val RingSweep = 90f

/** Time the arc takes to travel once round the ring. */
private const val RingSpinMillis = 1_400

/** Diameter of the scan ring. */
private val RingSize = 156.dp

/** Thickness of both the scan ring's track and its arc. */
private val RingStroke = 6.dp

/** Padding inside a camera row. */
private val RowPadding = 16.dp

/** Gap between the parts of a camera row. */
private val RowSpacing = 12.dp

/** Diameter of the circle carrying a camera row's glyph. */
private val RowEmblemSize = 44.dp

/** Size of a camera row's leading glyph and its trailing chevron. */
private val RowIconSize = 22.dp
