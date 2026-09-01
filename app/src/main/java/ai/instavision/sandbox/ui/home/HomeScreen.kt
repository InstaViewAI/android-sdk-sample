package ai.instavision.sandbox.ui.home

import ai.instavision.sandbox.ui.common.CircleIconButton
import ai.instavision.sandbox.ui.common.EmptyState
import ai.instavision.sandbox.ui.common.ErrorBanner
import ai.instavision.sandbox.ui.common.GroupCard
import ai.instavision.sandbox.ui.common.LoadingBox
import ai.instavision.sandbox.ui.common.PrimaryButton
import ai.instavision.sandbox.ui.common.RootScaffold
import ai.instavision.sandbox.ui.common.StatusPill
import ai.instavision.sandbox.ui.common.TextLink
import ai.instavision.sandbox.ui.theme.AppTheme
import ai.instavision.guardian.sdk.data.entity.Device
import ai.instavision.guardian.sdk.data.entity.Space
import ai.instavision.guardian.sdk.data.entity.isOnline
import ai.instavision.guardian.sdk.data.entity.primarySnapshotUrl
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoCall
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage

/**
 * Post-auth root: the selected home's cameras, and the way into pairing a camera and creating a
 * home. Has no back affordance by design — there is nothing beneath it once signed in.
 */
@Composable
fun HomeScreen(
  onCamera: () -> Unit,
  onCameraSettings: () -> Unit,
  onAddCamera: () -> Unit,
  onCreateSpace: () -> Unit,
) {
  val viewModel: HomeViewModel = viewModel()
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var addMenuOpen by remember { mutableStateOf(false) }
  var spaceMenuOpen by remember { mutableStateOf(false) }

  LifecycleResumeEffect(Unit) {
    viewModel.refreshOnResume()
    onPauseOrDispose {}
  }

  RootScaffold(
    title = state.selectedSpace?.name ?: DefaultTitle,
    subtitle = state.selectedSpace?.let { "${state.onlineCount} of ${state.cameraCount} online" },
    onTitleClick = if (state.spaces.size > 1) {
      { spaceMenuOpen = true }
    } else {
      null
    },
    action = {
      HeaderAction(
        addMenuOpen = addMenuOpen,
        onAddMenuOpenChange = { addMenuOpen = it },
        spaceMenuOpen = spaceMenuOpen,
        onSpaceMenuOpenChange = { spaceMenuOpen = it },
        spaces = state.spaces,
        onSelectSpace = viewModel::selectSpace,
        onAddCamera = onAddCamera,
        onCreateSpace = onCreateSpace,
      )
    },
  ) {
    ErrorBanner(message = state.error)
    if (state.error != null) {
      TextLink(text = "Try again", onClick = viewModel::load)
    }
    when {
      state.loading -> LoadingBox()
      state.devices.isNotEmpty() -> CameraList(
        devices = state.devices,
        onCamera = { device ->
          viewModel.selectDevice(device)
          onCamera()
        },
        onSettings = { device ->
          viewModel.selectDevice(device)
          onCameraSettings()
        },
      )
      state.error != null -> Unit
      state.spaces.isEmpty() -> NoSpaceState(onCreateSpace = onCreateSpace)
      else -> NoCamerasState(onAddCamera = onAddCamera)
    }
  }
}

/**
 * The header's "+" chip and both of the screen's menus. The home switcher is anchored here rather
 * than to the title because a `DropdownMenu` placed in the scaffold's content column would take a
 * slot in its spaced arrangement and push the camera list down by a gap.
 */
@Composable
private fun HeaderAction(
  addMenuOpen: Boolean,
  onAddMenuOpenChange: (Boolean) -> Unit,
  spaceMenuOpen: Boolean,
  onSpaceMenuOpenChange: (Boolean) -> Unit,
  spaces: List<Space>,
  onSelectSpace: (Space) -> Unit,
  onAddCamera: () -> Unit,
  onCreateSpace: () -> Unit,
) {
  Box {
    CircleIconButton(
      icon = Icons.Rounded.Add,
      contentDescription = "Add",
      onClick = { onAddMenuOpenChange(true) },
      size = AddButtonSize,
      background = AppTheme.colors.accent,
      tint = Color.White,
    )
    DropdownMenu(
      expanded = addMenuOpen,
      onDismissRequest = { onAddMenuOpenChange(false) },
    ) {
      MenuAction(
        label = "Add a camera",
        onClick = {
          onAddMenuOpenChange(false)
          onAddCamera()
        },
      )
      MenuAction(
        label = "Add a home",
        onClick = {
          onAddMenuOpenChange(false)
          onCreateSpace()
        },
      )
    }
    DropdownMenu(
      expanded = spaceMenuOpen,
      onDismissRequest = { onSpaceMenuOpenChange(false) },
    ) {
      spaces.forEach { space ->
        MenuAction(
          label = space.name,
          onClick = {
            onSpaceMenuOpenChange(false)
            onSelectSpace(space)
          },
        )
      }
    }
  }
}

/** One entry of either header menu; callers close the menu inside [onClick]. */
@Composable
private fun MenuAction(
  label: String,
  onClick: () -> Unit,
) {
  DropdownMenuItem(
    text = {
      Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = AppTheme.colors.textPrimary,
      )
    },
    onClick = onClick,
  )
}

/**
 * The camera list, one full-width card per row. Hand-built out of a plain column rather than a
 * `LazyColumn` because the scaffold's content slot already scrolls and nesting a lazy list inside
 * it crashes.
 */
@Composable
private fun CameraList(
  devices: List<Device>,
  onCamera: (Device) -> Unit,
  onSettings: (Device) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(CardGap)) {
    devices.forEach { device ->
      CameraCard(
        device = device,
        onClick = { onCamera(device) },
        onSettings = { onSettings(device) },
      )
    }
  }
}

/**
 * One camera: its latest snapshot with the live-view affordance and an online pill over it, above
 * a footer carrying the name and a shortcut into that camera's settings.
 */
@Composable
private fun CameraCard(
  device: Device,
  onClick: () -> Unit,
  onSettings: () -> Unit,
  modifier: Modifier = Modifier,
) {
  GroupCard(modifier = modifier) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(SnapshotAspect)
        .clickable(onClick = onClick)
        .semantics { contentDescription = "Live view, ${device.name}" },
    ) {
      CameraSnapshot(url = device.primarySnapshotUrl())
      StatusPill(
        text = if (device.isOnline()) "Online" else "Offline",
        dotColor = if (device.isOnline()) AppTheme.colors.success else AppTheme.colors.textTertiary,
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(OverlayPadding),
      )
      PlayBadge(modifier = Modifier.align(Alignment.Center))
    }
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(
          start = CardFooterPadding,
          end = CardFooterPadding - FooterActionInset,
          top = CardFooterPadding - FooterActionInset,
          bottom = CardFooterPadding - FooterActionInset,
        ),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = device.name,
        style = MaterialTheme.typography.titleMedium,
        color = AppTheme.colors.textPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      Box(
        modifier = Modifier
          .size(FooterActionSize)
          .clip(CircleShape)
          .clickable(onClick = onSettings),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Outlined.Settings,
          contentDescription = "Camera settings",
          tint = AppTheme.colors.textSecondary,
          modifier = Modifier.size(FooterIconSize),
        )
      }
    }
  }
}

/** The card's snapshot, falling back to a camera glyph when the device has never reported one. */
@Composable
private fun CameraSnapshot(url: String) {
  if (url.isBlank()) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(color = AppTheme.colors.surfaceHigh),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = Icons.Outlined.Videocam,
        contentDescription = null,
        tint = AppTheme.colors.textTertiary,
        modifier = Modifier.size(PlaceholderIconSize),
      )
    }
  } else {
    AsyncImage(
      model = url,
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = Modifier.fillMaxSize(),
    )
  }
}

/** The play disc over a snapshot; it is decoration, since the whole snapshot is the tap target. */
@Composable
private fun PlayBadge(modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .size(PlayBadgeSize)
      .clip(CircleShape)
      .background(color = Color.White.copy(alpha = PlayBadgeAlpha)),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = Icons.Filled.PlayArrow,
      contentDescription = null,
      tint = Color.Black,
      modifier = Modifier.size(PlayIconSize),
    )
  }
}

/**
 * What a brand-new account sees: with no homes there is nothing to list and nowhere to pair a
 * camera to, so the only sensible next step is creating one.
 */
@Composable
private fun NoSpaceState(onCreateSpace: () -> Unit) {
  EmptyState(
    title = "No home yet",
    body = "Create a home to group your cameras and start watching over the place that matters.",
    icon = Icons.Outlined.Home,
    action = { PrimaryButton(text = "Create a home", onClick = onCreateSpace) },
  )
}

/** Shown for a home that exists but has no cameras paired to it yet. */
@Composable
private fun NoCamerasState(onAddCamera: () -> Unit) {
  EmptyState(
    title = "No cameras yet",
    body = "Add your first camera to start seeing live video and events in this space.",
    icon = Icons.Outlined.VideoCall,
    iconTint = AppTheme.colors.textTertiary,
    iconBackground = null,
    action = {
      PrimaryButton(
        text = "Add a camera",
        onClick = onAddCamera,
        fillWidth = false,
      )
    },
  )
}

/** Header title used until the home list has arrived and a home has been selected. */
private const val DefaultTitle = "Home"

/** Gap between two stacked camera cards. */
private val CardGap = 12.dp

/** Width-to-height ratio of a camera snapshot. */
private const val SnapshotAspect = 16f / 9f

/** Diameter of the header's "+" chip, which is larger than a standard circle button. */
private val AddButtonSize = 52.dp

/** Inset of the status pill from the corner of a snapshot. */
private val OverlayPadding = 8.dp

/** Padding around a camera card's name-and-settings footer. */
private val CardFooterPadding = 12.dp

/** Size of the settings glyph in a camera card's footer. */
private val FooterIconSize = 20.dp

/** Tap target around the footer's settings glyph, sized for a fingertip rather than the glyph. */
private val FooterActionSize = 44.dp

/**
 * How much the footer's own padding is given back to the enlarged settings target on the sides it
 * reaches, so the row keeps its height and the glyph keeps its inset from the card's edge.
 */
private val FooterActionInset = (FooterActionSize - FooterIconSize) / 2

/** Diameter of the play disc centred on a snapshot. */
private val PlayBadgeSize = 56.dp

/** Opacity of the play disc, letting a little of the snapshot through. */
private const val PlayBadgeAlpha = 0.92f

/** Size of the triangle inside the play disc. */
private val PlayIconSize = 28.dp

/** Size of the glyph standing in for a snapshot the device has not reported yet. */
private val PlaceholderIconSize = 32.dp
