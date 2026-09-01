package ai.instavision.sandbox.ui.pairing

import ai.instavision.sandbox.ui.common.ErrorBanner
import ai.instavision.sandbox.ui.common.LoadingBox
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The step that hands the camera its credentials without ever talking to it: they are drawn as a
 * code for the camera's own lens to read. There is nothing to press, because the camera never
 * answers this phone — the backend poll running behind this page is what notices it has read them,
 * and swaps this page for the waiting one as soon as it does.
 */
@Composable
internal fun ShowCodePage(
  state: PairCameraUiState,
  onBack: () -> Unit,
  onExit: () -> Unit,
) {
  WizardPage(
    step = PairPage.ShowCode.wizardStep,
    title = "Show this code\nto your camera",
    subtitle = "The camera reads your network details straight off the screen, so hold it up to " +
      "the lens.",
    onBack = onBack,
    onExit = onExit,
  ) {
    ErrorBanner(message = state.error)
    QrCard(payload = state.qrPayload)
    WizardTips(
      tips = listOf(
        "Turn your screen brightness up, and keep the phone out of glare.",
        "Hold the code square on to the lens, about 20 cm away.",
        "Move slowly nearer and further until the camera chimes.",
      ),
    )
  }
}

/**
 * The code itself, on the white field it has to be read off. Encoding runs off the main thread
 * because a code this size takes long enough to drop a frame, and a payload the ViewModel has
 * already cleared draws nothing rather than an empty box.
 */
@Composable
private fun QrCard(payload: String) {
  val code by produceState<ImageBitmap?>(initialValue = null, payload) {
    value = if (payload.isBlank()) {
      null
    } else {
      withContext(Dispatchers.Default) { payload.toQrBitmap(size = QrPixelSize) }
    }
  }
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .aspectRatio(ratio = 1f)
      .clip(MaterialTheme.shapes.large)
      .background(color = Color.White)
      .padding(all = QrPadding),
    contentAlignment = Alignment.Center,
  ) {
    code?.let { bitmap ->
      Image(
        bitmap = bitmap,
        contentDescription = "Pairing code",
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize(),
      )
    } ?: LoadingBox()
  }
}

/**
 * Encodes this payload as a square code of [size] pixels, black on white. The hints are the ones
 * the production app encodes with: the lowest error correction, which keeps the modules as large
 * as the payload allows, and the narrowest quiet zone the format permits.
 */
private fun String.toQrBitmap(size: Int): ImageBitmap {
  val hints = mapOf<EncodeHintType, Any>(
    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
    EncodeHintType.MARGIN to QrMargin,
  )
  val matrix = MultiFormatWriter().encode(this, BarcodeFormat.QR_CODE, size, size, hints)
  val dark = Color.Black.toArgb()
  val light = Color.White.toArgb()
  val pixels = IntArray(matrix.width * matrix.height)
  for (y in 0 until matrix.height) {
    val offset = y * matrix.width
    for (x in 0 until matrix.width) {
      pixels[offset + x] = if (matrix.get(x, y)) dark else light
    }
  }
  val bitmap = createBitmap(width = matrix.width, height = matrix.height)
  bitmap.setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
  return bitmap.asImageBitmap()
}

/** Side of the encoded code in pixels, which is what the production app encodes at. */
private const val QrPixelSize = 1_200

/** Modules of quiet zone the encoder leaves around the code, in the format's own units. */
private const val QrMargin = 1

/** White border kept around the code, so the card's rounded corners cannot crop its quiet zone. */
private val QrPadding = 16.dp
