package ai.instavision.sandbox.ui.permissions

import ai.instavision.sandbox.ui.common.DetailScaffold
import ai.instavision.sandbox.ui.common.GroupCard
import ai.instavision.sandbox.ui.common.PrimaryButton
import ai.instavision.sandbox.ui.common.RowDivider
import ai.instavision.sandbox.ui.common.ScreenPadding
import ai.instavision.sandbox.ui.theme.AppTheme
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * Grants that gate BLE scanning. Android 12 introduced two dedicated ones; older releases used
 * location as the proxy, which is the same grant the location row of the card already asks for.
 */
val BluetoothPermissions: List<String> =
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
  } else {
    listOf(Manifest.permission.ACCESS_FINE_LOCATION)
  }

/** A capability the pre-flight card explains, together with the grants it actually requests. */
data class PreflightPermission(
  /** Leading glyph for the row, drawn in the accent colour. */
  val icon: ImageVector,
  /** Row title, naming the capability rather than the Android permission behind it. */
  val title: String,
  /** One line saying what the app does with the grant. */
  val rationale: String,
  /** Manifest permissions requested together when the row is tapped. */
  val permissions: List<String>,
)

/** The grants the app asks for up front, in the order the pre-flight card lists them. */
val PreflightPermissions: List<PreflightPermission> = listOf(
  PreflightPermission(
    icon = Icons.Outlined.PhotoCamera,
    title = "Camera",
    rationale = "To scan setup and SIM codes",
    permissions = listOf(Manifest.permission.CAMERA),
  ),
  PreflightPermission(
    icon = Icons.Outlined.Wifi,
    title = "Location",
    rationale = "To read your current Wi-Fi name",
    permissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION),
  ),
  PreflightPermission(
    icon = Icons.Outlined.Bluetooth,
    title = "Bluetooth",
    rationale = "To find cameras nearby",
    permissions = BluetoothPermissions,
  ),
)

/**
 * The pre-flight screen shown before setup, explaining each grant the app will ask for. Continue
 * stays enabled whatever the user has refused, so nothing here can trap them: a capability that is
 * still missing is asked for again at the point it is needed.
 */
@Composable
fun PermissionsScreen(
  onBack: () -> Unit,
  onContinue: () -> Unit,
) {
  DetailScaffold(
    title = "",
    onBack = onBack,
    bottomBar = {
      PrimaryButton(
        text = "Continue",
        onClick = onContinue,
        modifier = Modifier
          .navigationBarsPadding()
          .padding(horizontal = ScreenPadding, vertical = BottomBarPadding),
      )
    },
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(space = HeroSpacing)) {
      Text(
        text = "Before we start",
        style = MaterialTheme.typography.headlineMedium,
        color = AppTheme.colors.textPrimary,
      )
      Text(
        text = "Setup needs a few permissions. You can change these later in Settings.",
        style = MaterialTheme.typography.bodyLarge,
        color = AppTheme.colors.textSecondary,
      )
    }
    PermissionsCard()
  }
}

/**
 * The card of [PreflightPermissions] rows, shared by [PermissionsScreen] and the pairing wizard's
 * permissions step. It owns the request launcher and re-reads the grants on every resume, so a
 * trip to the system settings updates the ticks; [onGrantsChanged] reports the current grant set
 * to a host that has to react to one of them, such as the pairing flow waiting on Bluetooth.
 */
@Composable
fun PermissionsCard(
  modifier: Modifier = Modifier,
  onGrantsChanged: (Set<String>) -> Unit = {},
) {
  val context = LocalContext.current
  var granted by remember { mutableStateOf(context.grantedPreflightPermissions()) }
  val launcher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions(),
  ) { granted = context.grantedPreflightPermissions() }

  LifecycleResumeEffect(Unit) {
    granted = context.grantedPreflightPermissions()
    onPauseOrDispose {}
  }
  LaunchedEffect(granted) { onGrantsChanged(granted) }

  GroupCard(modifier = modifier) {
    PreflightPermissions.forEachIndexed { index, permission ->
      if (index > 0) RowDivider()
      PermissionRow(
        permission = permission,
        granted = permission.permissions.all { name -> name in granted },
        onRequest = { launcher.launch(permission.permissions.toTypedArray()) },
      )
    }
  }
}

/**
 * One row of [PermissionsCard]. A granted row is inert, since the system dialog would not be shown
 * a second time and the user has to go to the app settings to take a grant back.
 */
@Composable
private fun PermissionRow(
  permission: PreflightPermission,
  granted: Boolean,
  onRequest: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(enabled = !granted, onClick = onRequest)
      .padding(all = RowPadding),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(space = RowSpacing),
  ) {
    Icon(
      imageVector = permission.icon,
      contentDescription = null,
      tint = AppTheme.colors.accent,
      modifier = Modifier.size(size = RowIconSize),
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = permission.title,
        style = MaterialTheme.typography.titleMedium,
        color = AppTheme.colors.textPrimary,
      )
      Text(
        text = permission.rationale,
        style = MaterialTheme.typography.bodyMedium,
        color = AppTheme.colors.textSecondary,
      )
    }
    Icon(
      imageVector = if (granted) Icons.Rounded.CheckCircle else Icons.Outlined.Circle,
      contentDescription = if (granted) "Granted" else "Not granted yet",
      tint = if (granted) AppTheme.colors.success else AppTheme.colors.textTertiary,
      modifier = Modifier.size(size = StateIconSize),
    )
  }
}

/** Which of the [PreflightPermissions] grants the app currently holds. */
private fun Context.grantedPreflightPermissions(): Set<String> = PreflightPermissions
  .flatMap { permission -> permission.permissions }
  .filterTo(mutableSetOf()) { name ->
    ContextCompat.checkSelfPermission(this, name) == PackageManager.PERMISSION_GRANTED
  }

/** Gap between the hero title and the sentence under it. */
private val HeroSpacing = 8.dp

/** Vertical breathing room around the screen's pinned Continue button. */
private val BottomBarPadding = 12.dp

/** Padding inside a permission row, matching the design system's grouped rows. */
private val RowPadding = 16.dp

/** Gap between the parts of a permission row. */
private val RowSpacing = 12.dp

/** Size of a row's leading capability icon. */
private val RowIconSize = 22.dp

/** Size of the trailing tick or empty circle that reports the grant state. */
private val StateIconSize = 28.dp
