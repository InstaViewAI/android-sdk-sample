package ai.instavision.sandbox

import ai.instavision.sandbox.data.SessionStore
import ai.instavision.sandbox.data.notifications.registerCurrentPushToken
import ai.instavision.sandbox.ui.common.EmptyState
import ai.instavision.sandbox.ui.common.ScreenPadding
import ai.instavision.sandbox.ui.common.sdkCall
import ai.instavision.sandbox.ui.nav.rememberAppNavigator
import ai.instavision.sandbox.ui.shell.MainShell
import ai.instavision.sandbox.ui.theme.AppTheme
import ai.instavision.sandbox.ui.theme.InstaVisionSDKSampleTheme
import ai.instavision.guardian.sdk.InstaVision
import ai.instavision.guardian.sdk.data.entity.User
import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/** Headline of the screen shown when the sample has not been configured yet. */
private const val SETUP_TITLE = "Setup needed"

/** Follow-up shown under [SETUP_TITLE], pointing at the file that explains the missing pieces. */
private const val SETUP_HINT =
  "See the README for the Firebase file and partner credentials the SDK needs."

/** Stand-in message for a start-up failure that produced no diagnosis of its own. */
private const val SETUP_UNKNOWN = "The Guardian SDK did not start."

/** Gap between the setup placeholder and the line of guidance under it. */
private val SetupHintSpacing = 12.dp

/** Single activity host; also tells the SDK when the app leaves the foreground. */
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    window.isNavigationBarContrastEnforced = false
    setContent {
      InstaVisionSDKSampleTheme {
        if (SampleApp.isSdkInitialized) AppRoot() else SetupRequired()
      }
    }
  }

  override fun onStart() {
    super.onStart()
    InstaVision.setAppInForeground(true)
  }

  override fun onStop() {
    super.onStop()
    InstaVision.setAppInForeground(false)
  }
}

/**
 * Owns the navigator for the whole app and hands it to the shell, sending an already signed-in user
 * straight into the tabs so the onboarding screen never flashes on a warm start.
 *
 * The tabs are shown before [publishSignedInUser] is awaited, so a slow or unreachable network
 * strands nobody on onboarding; the account lands in [SessionStore] a moment later and the screens
 * observing it recompose on their own.
 */
@Composable
private fun AppRoot() {
  val navigator = rememberAppNavigator()
  BackHandler(enabled = navigator.canPop) { navigator.pop() }
  LaunchedEffect(Unit) {
    if (InstaVision.userServices.isLoggedIn().filterNotNull().first()) {
      navigator.resetToTabs()
      publishSignedInUser()
    }
  }
  PushNotifications()
  MainShell(navigator = navigator)
}

/**
 * Binds this install to the signed-in account for push, and asks for the Android 13+ notification
 * permission, once per sign-in — which covers both a warm start with a session and a fresh sign-in
 * on the auth screens.
 *
 * Nothing waits on either: the permission answer is deliberately ignored, and a failed
 * registration is as silent as the account fetch beside it. Signing out re-arms both, so the next
 * account registers its own token.
 */
@Composable
private fun PushNotifications() {
  val signedIn by InstaVision.userServices.isLoggedIn().collectAsStateWithLifecycle()
  var configured by rememberSaveable { mutableStateOf(false) }
  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
    onResult = {},
  )
  LaunchedEffect(signedIn) {
    if (signedIn != true) {
      configured = false
      return@LaunchedEffect
    }
    if (configured) return@LaunchedEffect
    configured = true
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    registerCurrentPushToken()
  }
}

/**
 * Fetches the account behind an existing session and publishes it to [SessionStore], which is the
 * only thing that fills `SessionStore.user` on a warm start — every other writer runs at sign-in or
 * on entry to the account screen.
 *
 * A failure is silent by design rather than by omission: launch has no error surface, and every
 * screen already renders an empty state for a null user, so the store is left untouched and the
 * account screen's own fetch recovers it on entry.
 */
private suspend fun publishSignedInUser() {
  sdkCall<User> { onSuccess, onError ->
    InstaVision.userServices.getUser(onSuccess = onSuccess, onError = onError)
  }.onSuccess { SessionStore.putUser(it) }
}

/**
 * Replaces the whole app when [InstaVision.initialize] never ran, naming the missing piece of
 * configuration. Nothing else may be shown in that state: the SDK's service properties are
 * uninitialised `lateinit`s, so a screen that touched one would crash instead.
 */
@Composable
private fun SetupRequired() {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(color = AppTheme.colors.ground)
      .safeDrawingPadding(),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier.padding(horizontal = ScreenPadding),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(SetupHintSpacing),
    ) {
      EmptyState(
        title = SETUP_TITLE,
        body = SampleApp.setupProblem ?: SETUP_UNKNOWN,
        icon = Icons.Outlined.Warning,
        iconTint = AppTheme.colors.warning,
        iconBackground = AppTheme.colors.warningContainer,
      )
      Text(
        text = SETUP_HINT,
        style = MaterialTheme.typography.bodyMedium,
        color = AppTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
      )
    }
  }
}
