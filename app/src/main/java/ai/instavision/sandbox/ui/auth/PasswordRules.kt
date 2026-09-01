package ai.instavision.sandbox.ui.auth

import ai.instavision.sandbox.ui.theme.AppTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** One requirement a password has to meet, paired with the pattern that decides whether it does. */
private data class PasswordRule(
  /** The wording shown beside the rule's indicator. */
  val label: String,
  /** Pattern the whole password is matched against; a full match means the rule is met. */
  val pattern: Regex,
)

/**
 * The four requirements the backend enforces, in the order they are listed to the user. The length
 * rule's lookahead also forbids whitespace anywhere in the password, which is deliberate: the
 * backend rejects spaces outright, and production shows no separate row for it, so the length label
 * carries both halves of that rule.
 */
private val PasswordRules: List<PasswordRule> = listOf(
  PasswordRule(label = "At least 8 characters", pattern = Regex("^(?=\\S+$).{8,}$")),
  PasswordRule(label = "One uppercase letter", pattern = Regex(".*[A-Z].*")),
  PasswordRule(label = "One lowercase letter", pattern = Regex(".*[a-z].*")),
  PasswordRule(label = "One number", pattern = Regex(".*\\d.*")),
)

/** True once [password] satisfies every rule [PasswordChecklist] draws. */
fun passwordSatisfiesRules(password: String): Boolean =
  PasswordRules.all { it.pattern.matches(password) }

/**
 * The live checklist under the password fields: one row per requirement, ticked as the typed
 * [password] starts to meet it. Purely a read-out of [passwordSatisfiesRules] — it never gates
 * anything itself.
 */
@Composable
fun PasswordChecklist(password: String, modifier: Modifier = Modifier) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(shape = MaterialTheme.shapes.medium)
      .background(color = AppTheme.colors.surface)
      .border(
        width = CardBorderWidth,
        color = AppTheme.colors.outline,
        shape = MaterialTheme.shapes.medium,
      )
      .padding(all = CardPadding),
    verticalArrangement = Arrangement.spacedBy(space = RowSpacing),
  ) {
    PasswordRules.forEach { rule ->
      PasswordRuleRow(label = rule.label, met = rule.pattern.matches(password))
    }
  }
}

/** A single requirement: its indicator, then its [label] brightened once the rule is [met]. */
@Composable
private fun PasswordRuleRow(label: String, met: Boolean) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(space = IndicatorSpacing),
  ) {
    RuleIndicator(met = met)
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      color = if (met) AppTheme.colors.textPrimary else AppTheme.colors.textSecondary,
    )
  }
}

/**
 * The dot at the head of a rule row: an empty outline while the rule is unmet, a filled tick once
 * it is. Decorative, since the row's label already says what the state means.
 */
@Composable
private fun RuleIndicator(met: Boolean) {
  if (!met) {
    Box(
      modifier = Modifier
        .size(IndicatorSize)
        .border(
          width = IndicatorBorderWidth,
          color = AppTheme.colors.textTertiary,
          shape = CircleShape,
        ),
    )
    return
  }
  Box(
    modifier = Modifier
      .size(IndicatorSize)
      .background(color = AppTheme.colors.success, shape = CircleShape),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = Icons.Rounded.Check,
      contentDescription = null,
      tint = AppTheme.colors.ground,
      modifier = Modifier.size(IndicatorIconSize),
    )
  }
}

/** Width of the hairline around the checklist card. */
private val CardBorderWidth = 1.dp

/** Inset between the checklist card's edge and its rows. */
private val CardPadding = 16.dp

/** Gap between two consecutive rules. */
private val RowSpacing = 10.dp

/** Gap between a rule's indicator and its label. */
private val IndicatorSpacing = 12.dp

/** Diameter of a rule's indicator, met or not. */
private val IndicatorSize = 22.dp

/** Width of the outline drawn for an unmet rule. */
private val IndicatorBorderWidth = 2.dp

/** Size of the tick inside a met rule's indicator. */
private val IndicatorIconSize = 14.dp
