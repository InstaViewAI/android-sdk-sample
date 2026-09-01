package ai.instavision.sandbox.ui.common

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import ai.instavision.sandbox.ui.theme.AppTheme

/**
 * A single-line input drawn as a filled dark box with a placeholder rather than a floating label,
 * matching the iOS forms. The border picks up the accent colour while the field holds focus.
 */
@Composable
fun AppTextField(
  value: String,
  onValueChange: (String) -> Unit,
  placeholder: String,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  keyboardType: KeyboardType = KeyboardType.Text,
  singleLine: Boolean = true,
  leading: @Composable (() -> Unit)? = null,
  trailing: @Composable (() -> Unit)? = null,
) {
  BaseField(
    value = value,
    onValueChange = onValueChange,
    placeholder = placeholder,
    modifier = modifier,
    enabled = enabled,
    keyboardType = keyboardType,
    singleLine = singleLine,
    leading = leading,
    trailing = trailing,
  )
}

/** An [AppTextField] that masks its contents, with an eye button that reveals them while tapped on. */
@Composable
fun PasswordField(
  value: String,
  onValueChange: (String) -> Unit,
  placeholder: String,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  var visible by rememberSaveable { mutableStateOf(false) }
  BaseField(
    value = value,
    onValueChange = onValueChange,
    placeholder = placeholder,
    modifier = modifier,
    enabled = enabled,
    keyboardType = KeyboardType.Password,
    singleLine = true,
    visualTransformation =
      if (visible) VisualTransformation.None else PasswordVisualTransformation(),
    trailing = {
      IconButton(onClick = { visible = !visible }) {
        Icon(
          imageVector = if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
          contentDescription = if (visible) "Hide password" else "Show password",
          tint = AppTheme.colors.textSecondary,
        )
      }
    },
  )
}

/**
 * A field that looks like [AppTextField] but only offers [options], used for country, state and
 * the phone country code. The text is never editable; tapping anywhere opens the menu.
 */
@Composable
fun AppDropdownField(
  value: String,
  options: List<String>,
  onSelect: (String) -> Unit,
  placeholder: String,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  var expanded by remember { mutableStateOf(false) }
  Box(modifier = modifier.fillMaxWidth()) {
    BaseField(
      value = value,
      onValueChange = {},
      placeholder = placeholder,
      modifier = Modifier,
      enabled = enabled,
      readOnly = true,
      trailing = {
        Icon(
          imageVector = Icons.Rounded.ExpandMore,
          contentDescription = null,
          tint = AppTheme.colors.textSecondary,
        )
      },
    )
    Box(
      modifier = Modifier
        .matchParentSize()
        .clip(FieldShape)
        .clickable(enabled = enabled) { expanded = true },
    )
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      options.forEach { option ->
        DropdownMenuItem(
          text = {
            Text(
              text = option,
              style = MaterialTheme.typography.bodyLarge,
              color = AppTheme.colors.textPrimary,
            )
          },
          onClick = {
            onSelect(option)
            expanded = false
          },
        )
      }
    }
  }
}

/** The validation message under a field; renders nothing at all while [message] is null. */
@Composable
fun FieldError(message: String?) {
  if (message == null) return
  Text(
    text = message,
    style = MaterialTheme.typography.bodySmall,
    color = AppTheme.colors.danger,
    modifier = Modifier.padding(start = ErrorInset, top = ErrorInset),
  )
}

/** Shared body of every field variant, so they cannot drift apart in fill, border or metrics. */
@Composable
private fun BaseField(
  value: String,
  onValueChange: (String) -> Unit,
  placeholder: String,
  modifier: Modifier,
  enabled: Boolean,
  keyboardType: KeyboardType = KeyboardType.Text,
  singleLine: Boolean = true,
  readOnly: Boolean = false,
  visualTransformation: VisualTransformation = VisualTransformation.None,
  leading: @Composable (() -> Unit)? = null,
  trailing: @Composable (() -> Unit)? = null,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val focused by interactionSource.collectIsFocusedAsState()
  TextField(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier
      .fillMaxWidth()
      .border(
        width = BorderWidth,
        color = if (focused) AppTheme.colors.accent else AppTheme.colors.outline,
        shape = FieldShape,
      ),
    enabled = enabled,
    readOnly = readOnly,
    textStyle = MaterialTheme.typography.bodyLarge,
    placeholder = {
      Text(
        text = placeholder,
        style = MaterialTheme.typography.bodyLarge,
        color = AppTheme.colors.textTertiary,
      )
    },
    leadingIcon = leading,
    trailingIcon = trailing,
    visualTransformation = visualTransformation,
    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    singleLine = singleLine,
    interactionSource = interactionSource,
    shape = FieldShape,
    colors = fieldColors(),
  )
}

/** Strips the Material underline and repaints the container in the app's own field colours. */
@Composable
private fun fieldColors() = TextFieldDefaults.colors(
  focusedContainerColor = AppTheme.colors.surface,
  unfocusedContainerColor = AppTheme.colors.surface,
  disabledContainerColor = AppTheme.colors.surface,
  errorContainerColor = AppTheme.colors.surface,
  cursorColor = AppTheme.colors.accent,
  focusedTextColor = AppTheme.colors.textPrimary,
  unfocusedTextColor = AppTheme.colors.textPrimary,
  disabledTextColor = AppTheme.colors.textTertiary,
  focusedPlaceholderColor = AppTheme.colors.textTertiary,
  unfocusedPlaceholderColor = AppTheme.colors.textTertiary,
  disabledPlaceholderColor = AppTheme.colors.textTertiary,
  focusedIndicatorColor = Color.Transparent,
  unfocusedIndicatorColor = Color.Transparent,
  disabledIndicatorColor = Color.Transparent,
  errorIndicatorColor = Color.Transparent,
)

/** Corner radius every field shares. */
private val FieldShape = RoundedCornerShape(16.dp)

/** Width of a field's border. */
private val BorderWidth = 1.dp

/** Inset that lines a [FieldError] up under the field's text. */
private val ErrorInset = 4.dp
