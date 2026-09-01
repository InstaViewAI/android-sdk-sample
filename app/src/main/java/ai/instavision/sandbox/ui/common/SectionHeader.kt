package ai.instavision.sandbox.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ai.instavision.sandbox.ui.theme.AppTheme

/**
 * Labels the card that follows it, sitting outside the card as a small uppercase grey caption.
 * [action] is a trailing [TextLink] such as "See all".
 */
@Composable
fun SectionHeader(
  text: String,
  modifier: Modifier = Modifier,
  action: @Composable (() -> Unit)? = null,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(bottom = HeaderGap),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = text.uppercase(),
      style = MaterialTheme.typography.labelSmall,
      color = AppTheme.colors.textTertiary,
      modifier = Modifier.weight(1f),
    )
    action?.invoke()
  }
}

/** Gap that ties a header to the card beneath it. */
private val HeaderGap = 8.dp
