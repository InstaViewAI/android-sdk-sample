package ai.instavision.sandbox.ui.auth

import ai.instavision.sandbox.ui.common.TextLink
import ai.instavision.sandbox.ui.theme.AppTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * The centred line that closes an auth screen by pointing at the other one: a plain [prompt]
 * followed by a tappable [linkText]. [prompt] carries its own trailing space, since the two halves
 * are separate composables rather than one styled string.
 */
@Composable
fun AuthFooter(
  prompt: String,
  linkText: String,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = prompt,
      style = MaterialTheme.typography.bodyLarge,
      color = AppTheme.colors.textSecondary,
    )
    TextLink(text = linkText, onClick = onClick)
  }
}
