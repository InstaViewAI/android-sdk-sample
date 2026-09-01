package ai.instavision.sandbox.ui.shell

import ai.instavision.sandbox.ui.auth.ForgotPasswordScreen
import ai.instavision.sandbox.ui.auth.OnboardingScreen
import ai.instavision.sandbox.ui.auth.SignInScreen
import ai.instavision.sandbox.ui.auth.SignUpScreen
import ai.instavision.sandbox.ui.auth.VerifyEmailScreen
import ai.instavision.sandbox.ui.camera.CameraDetailScreen
import ai.instavision.sandbox.ui.common.LocalBottomBar
import ai.instavision.sandbox.ui.events.EventDetailScreen
import ai.instavision.sandbox.ui.events.EventsScreen
import ai.instavision.sandbox.ui.home.HomeScreen
import ai.instavision.sandbox.ui.nav.AppNavigator
import ai.instavision.sandbox.ui.nav.NavMode
import ai.instavision.sandbox.ui.nav.Screen
import ai.instavision.sandbox.ui.nav.Tab
import ai.instavision.sandbox.ui.pairing.PairCameraScreen
import ai.instavision.sandbox.ui.permissions.PermissionsScreen
import ai.instavision.sandbox.ui.security.SecurityContactScreen
import ai.instavision.sandbox.ui.security.SecurityLogScreen
import ai.instavision.sandbox.ui.security.SecurityScreen
import ai.instavision.sandbox.ui.security.SecuritySettingsScreen
import ai.instavision.sandbox.ui.security.SecuritySetupScreen
import ai.instavision.sandbox.ui.security.SecuritySteps
import ai.instavision.sandbox.ui.security.SecurityStepScreen
import ai.instavision.sandbox.ui.settings.AccountSettingsScreen
import ai.instavision.sandbox.ui.settings.ChangePasswordScreen
import ai.instavision.sandbox.ui.settings.DeviceSettingsScreen
import ai.instavision.sandbox.ui.settings.SettingsScreen
import ai.instavision.sandbox.ui.settings.SpaceSettingsScreen
import ai.instavision.sandbox.ui.settings.device.DeviceActivityZoneScreen
import ai.instavision.sandbox.ui.settings.device.DeviceAudioScreen
import ai.instavision.sandbox.ui.settings.device.DeviceDetectionScreen
import ai.instavision.sandbox.ui.settings.device.DeviceFirmwareScreen
import ai.instavision.sandbox.ui.settings.device.DeviceInfoScreen
import ai.instavision.sandbox.ui.settings.device.DeviceLiveViewScreen
import ai.instavision.sandbox.ui.settings.device.DeviceNameScreen
import ai.instavision.sandbox.ui.settings.device.DeviceNotificationsScreen
import ai.instavision.sandbox.ui.space.CreateSpaceScreen
import ai.instavision.sandbox.ui.theme.AppTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Hairline the tab bar draws along its top edge, separating it from the screen above. */
private val BarDividerThickness = 1.dp

/**
 * The whole signed-in and signed-out UI: the destination on top of [navigator]'s active stack, with
 * the tab bar offered to it while that destination is a tab root. The bar travels through
 * [LocalBottomBar] rather than a screen parameter, so a screen only has to hand it to its scaffold.
 */
@Composable
fun MainShell(navigator: AppNavigator) {
  val tabBar: @Composable () -> Unit = remember(navigator) { { TabBar(navigator = navigator) } }
  CompositionLocalProvider(LocalBottomBar provides tabBar.takeIf { navigator.atTabRoot }) {
    Destination(navigator = navigator)
  }
}

/**
 * The four-tab bar shown on the tab roots. Carries no padding of its own because [NavigationBar]
 * already insets itself past the system navigation bar.
 */
@Composable
private fun TabBar(navigator: AppNavigator) {
  Column {
    HorizontalDivider(thickness = BarDividerThickness, color = AppTheme.colors.outline)
    NavigationBar(containerColor = AppTheme.colors.ground) {
      Tab.entries.forEach { tab ->
        val selected = tab == navigator.currentTab
        NavigationBarItem(
          selected = selected,
          onClick = { navigator.selectTab(tab) },
          icon = {
            Icon(
              imageVector = tab.icon(selected = selected),
              contentDescription = null,
            )
          },
          label = { Text(text = tab.label) },
          alwaysShowLabel = true,
          colors = NavigationBarItemDefaults.colors(
            selectedIconColor = AppTheme.colors.accent,
            selectedTextColor = AppTheme.colors.accent,
            unselectedIconColor = AppTheme.colors.textSecondary,
            unselectedTextColor = AppTheme.colors.textSecondary,
            indicatorColor = Color.Transparent,
          ),
        )
      }
    }
  }
}

/** The tab bar glyph for this tab, filled while its tab is the one on screen. */
private fun Tab.icon(selected: Boolean): ImageVector = when (this) {
  Tab.Home -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
  Tab.Events -> if (selected) Icons.Filled.Schedule else Icons.Outlined.Schedule
  Tab.Security -> if (selected) Icons.Filled.Shield else Icons.Outlined.Shield
  Tab.Settings -> if (selected) Icons.Filled.Settings else Icons.Outlined.Settings
}

/** The caption printed under this tab's glyph. */
private val Tab.label: String
  get() = when (this) {
    Tab.Home -> "Home"
    Tab.Events -> "Events"
    Tab.Security -> "Security"
    Tab.Settings -> "Settings"
  }

/**
 * The destination for one of [SecuritySteps]' screens opened from security settings rather than
 * from the checklist, which is what suppresses its `setup_step` write.
 */
private fun securityStep(step: SecuritySteps): Screen =
  Screen.SecurityStep(apiName = step.apiName, standalone = true)

/**
 * Renders the destination on top of the back stack and wires every screen's callbacks to
 * [navigator]. Sign-in, verification and sign-out swap the whole navigation mode rather than push,
 * so the back gesture can never walk from the tabs into the account flow or the other way round.
 */
@Composable
private fun Destination(navigator: AppNavigator) {
  val back: () -> Unit = { navigator.pop() }
  val toTabRoot: () -> Unit = { navigator.selectTab(navigator.currentTab) }
  when (val screen = navigator.current) {
    Screen.Onboarding -> OnboardingScreen(
      onCreateAccount = { navigator.push(Screen.SignUp) },
      onSignIn = { navigator.push(Screen.SignIn) },
    )

    Screen.SignUp -> SignUpScreen(
      onBack = back,
      onSignedUp = { navigator.push(Screen.VerifyEmail) },
      onSignIn = { navigator.push(Screen.SignIn) },
    )

    Screen.VerifyEmail -> VerifyEmailScreen(
      onVerified = { navigator.push(Screen.CreateSpace) },
      onUseDifferentAccount = { navigator.resetToAuth() },
    )

    Screen.SignIn -> SignInScreen(
      onBack = back,
      onSignedIn = { navigator.resetToTabs() },
      onSignUp = { navigator.push(Screen.SignUp) },
      onForgotPassword = { navigator.push(Screen.ForgotPassword) },
    )

    Screen.ForgotPassword -> ForgotPasswordScreen(onBack = back, onDone = back)

    Screen.Home -> HomeScreen(
      onCamera = { navigator.push(Screen.CameraDetail) },
      onCameraSettings = { navigator.push(Screen.DeviceSettings) },
      onAddCamera = { navigator.push(Screen.Permissions) },
      onCreateSpace = { navigator.push(Screen.CreateSpace) },
    )

    Screen.CameraDetail -> CameraDetailScreen(
      onBack = back,
      onSettings = { navigator.push(Screen.DeviceSettings) },
    )

    Screen.Permissions -> PermissionsScreen(
      onBack = back,
      onContinue = { navigator.push(Screen.PairCamera) },
    )

    Screen.PairCamera -> PairCameraScreen(onBack = back, onPaired = toTabRoot)

    Screen.CreateSpace -> CreateSpaceScreen(
      onBack = back,
      onCreated = {
        if (navigator.mode == NavMode.Auth) navigator.resetToTabs() else navigator.pop()
      },
    )

    Screen.Events -> EventsScreen(onEvent = { navigator.push(Screen.EventDetail) })

    Screen.EventDetail -> EventDetailScreen(onBack = back)

    Screen.Security -> SecurityScreen(
      onSetup = { navigator.push(Screen.SecuritySetup) },
      onLog = { navigator.push(Screen.SecurityLog) },
      onSettings = { navigator.push(Screen.SecuritySettings) },
      onEditCameras = { navigator.push(securityStep(SecuritySteps.CameraSetup)) },
    )

    Screen.SecuritySettings -> SecuritySettingsScreen(
      onBack = back,
      onPersonalInfo = { navigator.push(securityStep(SecuritySteps.ContactInformation)) },
      onSafeWord = { navigator.push(securityStep(SecuritySteps.DisarmSettings)) },
      onCallList = { navigator.push(securityStep(SecuritySteps.InviteHouseholds)) },
      onCameras = { navigator.push(securityStep(SecuritySteps.CameraSetup)) },
      onSchedule = { navigator.push(securityStep(SecuritySteps.ScheduleSystem)) },
      onLog = { navigator.push(Screen.SecurityLog) },
    )

    Screen.SecurityLog -> SecurityLogScreen(
      onBack = back,
      onEvent = { navigator.push(Screen.EventDetail) },
    )

    Screen.SecuritySetup -> SecuritySetupScreen(
      onBack = back,
      onStep = { navigator.push(Screen.SecurityStep(apiName = it)) },
      onContact = { navigator.push(Screen.SecurityContact) },
    )

    Screen.SecurityContact -> SecurityContactScreen(onBack = back, onDone = back)

    is Screen.SecurityStep -> SecurityStepScreen(
      apiName = screen.apiName,
      onBack = back,
      onDone = back,
      standalone = screen.standalone,
    )

    Screen.Settings -> SettingsScreen(
      onAccount = { navigator.push(Screen.AccountSettings) },
      onChangePassword = { navigator.push(Screen.ChangePassword) },
      onEditSpace = { navigator.push(Screen.SpaceSettings) },
      onAddCamera = { navigator.push(Screen.Permissions) },
      onSecuritySetup = { navigator.push(Screen.SecuritySetup) },
      onCamera = { navigator.push(Screen.DeviceSettings) },
      onSignedOut = { navigator.resetToAuth() },
    )

    Screen.AccountSettings -> AccountSettingsScreen(
      onBack = back,
      onSignedOut = { navigator.resetToAuth() },
    )

    Screen.ChangePassword -> ChangePasswordScreen(onBack = back, onDone = back)

    Screen.SpaceSettings -> SpaceSettingsScreen(onBack = back)

    Screen.DeviceSettings -> DeviceSettingsScreen(
      onBack = back,
      onSubScreen = { navigator.push(it) },
    )

    Screen.DeviceName -> DeviceNameScreen(onBack = back)

    Screen.DeviceInfo -> DeviceInfoScreen(onBack = back)

    Screen.DeviceFirmware -> DeviceFirmwareScreen(onBack = back)

    Screen.DeviceDetection -> DeviceDetectionScreen(
      onBack = back,
      onSubScreen = { navigator.push(it) },
    )

    Screen.DeviceActivityZone -> DeviceActivityZoneScreen(onBack = back)

    Screen.DeviceNotifications -> DeviceNotificationsScreen(onBack = back)

    Screen.DeviceLiveView -> DeviceLiveViewScreen(onBack = back)

    Screen.DeviceAudio -> DeviceAudioScreen(onBack = back)
  }
}
