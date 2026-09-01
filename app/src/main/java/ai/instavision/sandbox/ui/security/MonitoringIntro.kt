package ai.instavision.sandbox.ui.security

import ai.instavision.sandbox.ui.common.GroupCard
import ai.instavision.sandbox.ui.theme.AppTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhoneInTalk
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** The pitch under the intro headline: what monitoring adds to the alerts cameras already send. */
private const val INTRO_BODY =
  "Your cameras already alert you. Monitoring adds people who act when you cannot."

/**
 * The case for professional monitoring: what a monitoring centre does that a phone notification
 * cannot. Content only, with no scaffold and no call to action, so whichever screen currently owns
 * the pitch supplies its own frame and button.
 */
@Composable
internal fun MonitoringIntroContent(modifier: Modifier = Modifier) {
  Column(
    modifier = modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
      modifier = Modifier
        .size(IntroEmblemSize)
        .clip(CircleShape)
        .background(color = AppTheme.colors.accentSoft),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = Icons.Outlined.Shield,
        contentDescription = null,
        tint = AppTheme.colors.accent,
        modifier = Modifier.size(IntroEmblemIconSize),
      )
    }
    Spacer(modifier = Modifier.height(IntroSectionGap))
    Text(
      text = "Professional monitoring",
      style = MaterialTheme.typography.headlineMedium,
      color = AppTheme.colors.textPrimary,
      textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(IntroBodyGap))
    Text(
      text = INTRO_BODY,
      style = MaterialTheme.typography.bodyLarge,
      color = AppTheme.colors.textSecondary,
      textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(IntroSectionGap))
    Column(verticalArrangement = Arrangement.spacedBy(PerkCardSpacing)) {
      PerkCard(
        icon = Icons.Outlined.SupportAgent,
        heading = "Agents on watch 24/7",
        body = "Every alarm is reviewed by a person, not just a phone.",
      )
      PerkCard(
        icon = Icons.Outlined.PhoneInTalk,
        heading = "They call you first",
        body = "An agent confirms with your safe word before doing anything else.",
      )
      PerkCard(
        icon = Icons.Outlined.Shield,
        heading = "Dispatch when it counts",
        body = "If you cannot be reached, they can send police or fire.",
      )
    }
  }
}

/** One of the three promises of the monitoring intro: a tinted glyph beside a heading and a line. */
@Composable
private fun PerkCard(
  icon: ImageVector,
  heading: String,
  body: String,
) {
  GroupCard {
    Row(
      modifier = Modifier.padding(CardPadding),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(PerkSpacing),
    ) {
      Box(
        modifier = Modifier
          .size(PerkEmblemSize)
          .clip(CircleShape)
          .background(color = AppTheme.colors.accentSoft),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = AppTheme.colors.accent,
          modifier = Modifier.size(PerkEmblemIconSize),
        )
      }
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(TitleGap),
      ) {
        Text(
          text = heading,
          style = MaterialTheme.typography.titleMedium,
          color = AppTheme.colors.textPrimary,
        )
        Text(
          text = body,
          style = MaterialTheme.typography.bodyMedium,
          color = AppTheme.colors.textSecondary,
        )
      }
    }
  }
}

/** Diameter of the circle behind the monitoring intro's shield. */
private val IntroEmblemSize = 140.dp

/** Size of the shield inside the monitoring intro's circle. */
private val IntroEmblemIconSize = 64.dp

/** Gap the monitoring intro leaves above its headline and its cards. */
private val IntroSectionGap = 24.dp

/** Gap between the monitoring intro's headline and the line under it. */
private val IntroBodyGap = 12.dp

/** Padding inside a perk card. */
private val CardPadding = 16.dp

/** Gap between a perk's emblem and its text. */
private val PerkSpacing = 16.dp

/** Gap between a perk card's heading and the line under it. */
private val TitleGap = 4.dp

/** Gap between two stacked perk cards. */
private val PerkCardSpacing = 12.dp

/** Diameter of the circle behind a perk card's icon. */
private val PerkEmblemSize = 56.dp

/** Size of the icon inside a perk card's circle. */
private val PerkEmblemIconSize = 28.dp
