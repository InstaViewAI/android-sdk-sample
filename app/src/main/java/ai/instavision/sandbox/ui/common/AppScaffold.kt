package ai.instavision.sandbox.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import ai.instavision.sandbox.ui.theme.AppTheme

/** Horizontal inset the content of every screen sits inside; exposed for full-bleed exceptions. */
val ScreenPadding = 20.dp

/**
 * The tab bar a [RootScaffold] picks up when nothing is passed to its `bottomBar`, letting the
 * shell supply it once for every tab root. Deliberately not a static local: the shell swaps it as
 * the user navigates away from a tab root, and the scaffolds reading it must recompose.
 */
val LocalBottomBar = compositionLocalOf<@Composable (() -> Unit)?> { null }

/**
 * The frame of a tab-root screen. Deliberately uses a plain header row rather than a Material
 * `TopAppBar` so the oversized title scrolls with nothing and matches the iOS app.
 * Passing [onTitleClick] turns the title into a tap target and appends a chevron to it. Pass
 * `scrollable = false` when the screen hosts its own `LazyColumn`, which leaves the content area
 * full-bleed and unpadded so the list can own its insets. [bottomBar] falls back to the shell's
 * [LocalBottomBar], so a tab root gets the tab bar without naming it.
 */
@Composable
fun RootScaffold(
  title: String,
  subtitle: String? = null,
  onTitleClick: (() -> Unit)? = null,
  action: @Composable (() -> Unit)? = null,
  bottomBar: @Composable (() -> Unit)? = LocalBottomBar.current,
  scrollable: Boolean = true,
  content: @Composable ColumnScope.() -> Unit,
) {
  Scaffold(
    containerColor = AppTheme.colors.ground,
    bottomBar = { bottomBar?.invoke() },
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(brush = groundBrush())
        .padding(innerPadding)
        .consumeWindowInsets(innerPadding)
        .imePadding(),
    ) {
      RootHeader(
        title = title,
        subtitle = subtitle,
        onTitleClick = onTitleClick,
        action = action,
      )
      ScaffoldContent(scrollable = scrollable, content = content)
    }
  }
}

/**
 * The frame of a pushed screen: a circular back chip beside a smaller title, with [subtitle] on a
 * second line when the screen has context to add, such as a camera name or a connection state.
 * Pass `scrollable = false` when the screen hosts its own `LazyColumn`, which leaves the content
 * area full-bleed and unpadded so the list can own its insets. Unlike [RootScaffold] this never
 * picks up [LocalBottomBar]; a pushed screen's [bottomBar] is its own call to action or nothing.
 */
@Composable
fun DetailScaffold(
  title: String,
  subtitle: String? = null,
  subtitleIcon: @Composable (() -> Unit)? = null,
  onBack: () -> Unit,
  actions: @Composable RowScope.() -> Unit = {},
  bottomBar: @Composable (() -> Unit)? = null,
  scrollable: Boolean = true,
  content: @Composable ColumnScope.() -> Unit,
) {
  Scaffold(
    containerColor = AppTheme.colors.ground,
    bottomBar = { bottomBar?.invoke() },
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(brush = groundBrush())
        .padding(innerPadding)
        .consumeWindowInsets(innerPadding)
        .imePadding(),
    ) {
      DetailHeader(
        title = title,
        subtitle = subtitle,
        subtitleIcon = subtitleIcon,
        onBack = onBack,
        actions = actions,
      )
      ScaffoldContent(scrollable = scrollable, content = content)
    }
  }
}

/** Oversized title block of [RootScaffold], with the optional trailing [action] pinned to the end. */
@Composable
private fun RootHeader(
  title: String,
  subtitle: String?,
  onTitleClick: (() -> Unit)?,
  action: @Composable (() -> Unit)?,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = ScreenPadding, vertical = HeaderPadding),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(HeaderSpacing),
  ) {
    Column(
      modifier = Modifier
        .weight(1f)
        .then(if (onTitleClick == null) Modifier else Modifier.clickable(onClick = onTitleClick)),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = title,
          style = MaterialTheme.typography.displaySmall,
          color = AppTheme.colors.textPrimary,
        )
        if (onTitleClick != null) {
          Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = AppTheme.colors.textSecondary,
            modifier = Modifier.size(TitleChevronSize),
          )
        }
      }
      if (subtitle != null) {
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = AppTheme.colors.textSecondary,
        )
      }
    }
    action?.invoke()
  }
}

/**
 * Back chip, title block and trailing [actions] of [DetailScaffold], all on one row. The chip and
 * the actions stay centred against the title block whether or not it carries a second line.
 */
@Composable
private fun DetailHeader(
  title: String,
  subtitle: String?,
  subtitleIcon: @Composable (() -> Unit)?,
  onBack: () -> Unit,
  actions: @Composable RowScope.() -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = ScreenPadding, vertical = HeaderPadding),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(HeaderSpacing),
  ) {
    CircleIconButton(
      icon = Icons.Rounded.ChevronLeft,
      contentDescription = "Back",
      onClick = onBack,
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        color = AppTheme.colors.textPrimary,
      )
      if (subtitle != null) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(SubtitleSpacing),
        ) {
          subtitleIcon?.invoke()
          Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
          )
        }
      }
    }
    actions()
  }
}

/**
 * The content area both scaffolds hand to their callers, either padded and scrolling or, when a
 * screen brings its own lazy list, full-bleed and left to scroll itself.
 */
@Composable
private fun ColumnScope.ScaffoldContent(
  scrollable: Boolean,
  content: @Composable ColumnScope.() -> Unit,
) {
  if (scrollable) {
    ScrollingContent(content = content)
  } else {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f),
      content = content,
    )
  }
}

/** The padded, scrolling variant of the content area. */
@Composable
private fun ColumnScope.ScrollingContent(content: @Composable ColumnScope.() -> Unit) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .weight(1f)
      .verticalScroll(rememberScrollState())
      .padding(horizontal = ScreenPadding)
      .padding(bottom = ContentSpacing),
    verticalArrangement = Arrangement.spacedBy(ContentSpacing),
    content = content,
  )
}

/** Vertical wash from the lighter ground tone into the flat ground colour behind every screen. */
@Composable
private fun groundBrush(): Brush = Brush.verticalGradient(
  0f to AppTheme.colors.groundTop,
  GradientEnd to AppTheme.colors.ground,
  1f to AppTheme.colors.ground,
)

/** Fraction of the screen height over which the background gradient has fully resolved. */
private const val GradientEnd = 0.35f

/** Gap the scaffolds put between two consecutive pieces of screen content. */
private val ContentSpacing = 12.dp

/** Vertical breathing room around a screen's header row. */
private val HeaderPadding = 4.dp

/** Gap between the header's title block and its trailing action. */
private val HeaderSpacing = 12.dp

/** Gap between a detail header's subtitle icon and its subtitle text. */
private val SubtitleSpacing = 6.dp

/** Size of the disclosure chevron that follows a tappable root title. */
private val TitleChevronSize = 28.dp
