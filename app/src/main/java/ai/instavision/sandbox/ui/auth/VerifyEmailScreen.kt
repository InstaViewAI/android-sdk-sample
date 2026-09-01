package ai.instavision.sandbox.ui.auth

import ai.instavision.sandbox.ui.common.ErrorBanner
import ai.instavision.sandbox.ui.common.Notice
import ai.instavision.sandbox.ui.common.PrimaryButton
import ai.instavision.sandbox.ui.common.ScreenPadding
import ai.instavision.sandbox.ui.common.SecondaryButton
import ai.instavision.sandbox.ui.common.TextLink
import ai.instavision.sandbox.ui.theme.AppTheme
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Waiting room shown straight after sign-up. It watches the account in the background while the
 * visitor follows the link from their mail app, so nothing here has to be tapped to make progress.
 * Like [OnboardingScreen] it carries no scaffold and handles its own insets.
 */
@Composable
fun VerifyEmailScreen(
  onVerified: () -> Unit,
  onUseDifferentAccount: () -> Unit,
) {
  val viewModel: VerifyEmailViewModel = viewModel()
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = LocalContext.current

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(color = AppTheme.colors.ground)
      .safeDrawingPadding()
      .padding(horizontal = ScreenPadding, vertical = VerticalPadding),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Spacer(modifier = Modifier.weight(1f))
    MailMark()
    Spacer(modifier = Modifier.height(TitleGap))
    Text(
      text = "Confirm your email",
      style = MaterialTheme.typography.headlineMedium,
      color = AppTheme.colors.textPrimary,
      textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(TextGap))
    Text(
      text = "We sent a verification link to",
      style = MaterialTheme.typography.bodyLarge,
      color = AppTheme.colors.textSecondary,
      textAlign = TextAlign.Center,
    )
    Text(
      text = state.email.ifBlank { "your email address" },
      style = MaterialTheme.typography.bodyLarge,
      color = AppTheme.colors.accent,
      textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(TextGap))
    Text(
      text = "Open it on this device and we will take you straight through — no need to come " +
        "back here.",
      style = MaterialTheme.typography.bodyMedium,
      color = AppTheme.colors.textSecondary,
      textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(CardGap))
    WaitingCard()
    Spacer(modifier = Modifier.height(BannerGap))
    ErrorBanner(message = state.error)
    Notice(message = state.message)
    Spacer(modifier = Modifier.weight(1f))
    PrimaryButton(
      text = "Open Mail",
      onClick = { if (!openEmailApp(context = context)) viewModel.onEmailAppMissing() },
    )
    Spacer(modifier = Modifier.height(ButtonGap))
    SecondaryButton(
      text = state.resendLabel,
      onClick = viewModel::resend,
      enabled = state.canResend,
    )
    Spacer(modifier = Modifier.height(ButtonGap))
    TextLink(
      text = "Use a different account",
      onClick = {
        viewModel.useDifferentAccount()
        onUseDifferentAccount()
      },
    )
  }

  if (state.verified) {
    ConfirmedDialog(onContinue = onVerified)
  }
}

/** The hero mark: an unread-mail glyph on a soft accent disc. Decorative, so it has no description. */
@Composable
private fun MailMark() {
  Box(
    modifier = Modifier
      .size(MarkSize)
      .background(color = AppTheme.colors.accentSoft, shape = CircleShape),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = Icons.Outlined.MarkEmailUnread,
      contentDescription = null,
      tint = AppTheme.colors.accent,
      modifier = Modifier.size(MarkIconSize),
    )
  }
}

/** The card that says the screen is still watching the account, alongside a live spinner. */
@Composable
private fun WaitingCard() {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(shape = MaterialTheme.shapes.medium)
      .background(color = AppTheme.colors.surface)
      .border(
        width = CardBorderWidth,
        color = AppTheme.colors.outline,
        shape = MaterialTheme.shapes.medium,
      )
      .padding(all = CardPadding),
    horizontalArrangement = Arrangement.spacedBy(
      space = CardSpacing,
      alignment = Alignment.CenterHorizontally,
    ),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    CircularProgressIndicator(
      modifier = Modifier.size(SpinnerSize),
      color = AppTheme.colors.accent,
      strokeWidth = SpinnerStroke,
    )
    Text(
      text = "Waiting for confirmation…",
      style = MaterialTheme.typography.bodyMedium,
      color = AppTheme.colors.textSecondary,
    )
  }
}

/**
 * The dialog raised the moment the backend vouches for the address. It is deliberately impossible
 * to dismiss: [onContinue] is the only way on, since the account is verified and the waiting room
 * behind it has nothing left to say.
 */
@Composable
private fun ConfirmedDialog(onContinue: () -> Unit) {
  AlertDialog(
    onDismissRequest = {},
    confirmButton = {
      PrimaryButton(text = "Continue", onClick = onContinue)
    },
    title = {
      Text(
        text = "Email confirmed",
        style = MaterialTheme.typography.titleLarge,
        color = AppTheme.colors.textPrimary,
      )
    },
    text = {
      Text(
        text = "Your address is verified. Let's finish setting up.",
        style = MaterialTheme.typography.bodyMedium,
        color = AppTheme.colors.textSecondary,
      )
    },
    shape = MaterialTheme.shapes.large,
    containerColor = AppTheme.colors.surface,
    properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
  )
}

/** Opens whichever app the device registered for mail, returning false when nothing answers. */
private fun openEmailApp(context: Context): Boolean = runCatching {
  context.startActivity(
    Intent(Intent.ACTION_MAIN)
      .addCategory(Intent.CATEGORY_APP_EMAIL)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
  )
}.isSuccess

/** Breathing room above the hero mark and below the last link. */
private val VerticalPadding = 32.dp

/** Diameter of the soft accent disc behind the mail glyph. */
private val MarkSize = 120.dp

/** Size of the mail glyph centred on the disc. */
private val MarkIconSize = 56.dp

/** Gap between the hero mark and the title. */
private val TitleGap = 28.dp

/** Gap between two blocks of copy. */
private val TextGap = 12.dp

/** Gap between the copy and the status card. */
private val CardGap = 24.dp

/** Gap between the status card and any banner under it. */
private val BannerGap = 12.dp

/** Width of the hairline around the status card. */
private val CardBorderWidth = 1.dp

/** Inset between the status card's edge and its contents. */
private val CardPadding = 16.dp

/** Gap between the status card's spinner and its label. */
private val CardSpacing = 12.dp

/** Diameter of the spinner in the status card. */
private val SpinnerSize = 20.dp

/** Stroke width of the spinner in the status card. */
private val SpinnerStroke = 2.dp

/** Gap between two consecutive calls to action. */
private val ButtonGap = 12.dp
