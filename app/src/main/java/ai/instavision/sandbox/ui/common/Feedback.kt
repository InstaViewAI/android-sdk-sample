package ai.instavision.sandbox.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ai.instavision.sandbox.ui.theme.AppTheme

/** Shows why an SDK call failed; renders nothing at all while [message] is null. */
@Composable
fun ErrorBanner(message: String?) {
  if (message == null) return
  Banner(
    message = message,
    container = AppTheme.colors.dangerContainer,
    content = AppTheme.colors.danger,
  )
}

/** The transient confirmation counterpart of [ErrorBanner], such as "Saved" or "Email sent". */
@Composable
fun Notice(message: String?) {
  if (message == null) return
  Banner(
    message = message,
    container = AppTheme.colors.accentSoft,
    content = AppTheme.colors.accent,
  )
}

/** A standing explanation of how a feature behaves, sat beside a tinted icon. */
@Composable
fun InfoNote(
  text: String,
  icon: ImageVector = Icons.Outlined.Info,
  tint: Color = AppTheme.colors.info,
) {
  Surface(
    color = AppTheme.colors.surface,
    shape = BannerShape,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(BannerPadding),
      verticalAlignment = Alignment.Top,
      horizontalArrangement = Arrangement.spacedBy(NoteSpacing),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(NoteIconSize),
      )
      Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = AppTheme.colors.textSecondary,
      )
    }
  }
}

/** Placeholder shown in place of content that is still being fetched. */
@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .padding(vertical = PlaceholderPadding),
    contentAlignment = Alignment.Center,
  ) {
    CircularProgressIndicator(color = AppTheme.colors.accent)
  }
}

/**
 * The centred illustration, headline and call to action shown when a screen has nothing to list
 * yet, such as "Setup is not finished". Everything but [title] is optional. A null
 * [iconBackground] drops the circle behind the glyph and draws it larger and on its own.
 */
@Composable
fun EmptyState(
  title: String,
  modifier: Modifier = Modifier,
  body: String? = null,
  icon: ImageVector? = null,
  iconTint: Color = AppTheme.colors.accent,
  iconBackground: Color? = AppTheme.colors.accentSoft,
  action: @Composable (() -> Unit)? = null,
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(vertical = PlaceholderPadding),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(EmptyStateSpacing),
  ) {
    if (icon != null) {
      if (iconBackground == null) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = iconTint,
          modifier = Modifier.size(BareIconSize),
        )
      } else {
        Box(
          modifier = Modifier
            .size(EmblemSize)
            .clip(CircleShape)
            .background(color = iconBackground),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(EmblemIconSize),
          )
        }
      }
    }
    Text(
      text = title,
      style = MaterialTheme.typography.headlineMedium,
      color = AppTheme.colors.textPrimary,
      textAlign = TextAlign.Center,
    )
    if (body != null) {
      Text(
        text = body,
        style = MaterialTheme.typography.bodyLarge,
        color = AppTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
      )
    }
    action?.invoke()
  }
}

/**
 * The app's single confirmation dialog. [destructive] paints the confirm action red, which is the
 * default because every confirmation in the app removes something.
 */
@Composable
fun ConfirmDialog(
  title: String,
  message: String,
  confirmLabel: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
  destructive: Boolean = true,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(text = title, style = MaterialTheme.typography.titleLarge)
    },
    text = {
      Text(text = message, style = MaterialTheme.typography.bodyMedium)
    },
    confirmButton = {
      TextButton(onClick = onConfirm) {
        Text(
          text = confirmLabel,
          style = MaterialTheme.typography.titleMedium,
          color = if (destructive) AppTheme.colors.danger else AppTheme.colors.accent,
        )
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(
          text = "Cancel",
          style = MaterialTheme.typography.titleMedium,
          color = AppTheme.colors.textSecondary,
        )
      }
    },
    containerColor = AppTheme.colors.surface,
    titleContentColor = AppTheme.colors.textPrimary,
    textContentColor = AppTheme.colors.textSecondary,
  )
}

/** Shared body of [ErrorBanner] and [Notice], which differ only in their two colours. */
@Composable
private fun Banner(message: String, container: Color, content: Color) {
  Surface(
    color = container,
    shape = BannerShape,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Text(
      text = message,
      style = MaterialTheme.typography.bodyMedium,
      color = content,
      modifier = Modifier.padding(BannerPadding),
    )
  }
}

/** Corner radius of the banners and notes. */
private val BannerShape = RoundedCornerShape(16.dp)

/** Padding inside a banner or note. */
private val BannerPadding = 14.dp

/** Gap between an [InfoNote]'s icon and its text. */
private val NoteSpacing = 12.dp

/** Size of an [InfoNote]'s icon. */
private val NoteIconSize = 20.dp

/** Vertical breathing room around [LoadingBox] and [EmptyState]. */
private val PlaceholderPadding = 32.dp

/** Gap between the stacked parts of an [EmptyState]. */
private val EmptyStateSpacing = 16.dp

/** Diameter of the circle behind an [EmptyState]'s icon. */
private val EmblemSize = 132.dp

/** Size of the icon inside an [EmptyState]'s circle. */
private val EmblemIconSize = 64.dp

/** Size of an [EmptyState]'s icon when it stands alone, with no circle behind it. */
private val BareIconSize = 96.dp
