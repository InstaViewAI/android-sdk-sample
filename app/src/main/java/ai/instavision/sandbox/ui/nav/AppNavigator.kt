package ai.instavision.sandbox.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList

/** A destination in the app. Auth destinations live outside the tabs; the rest belong to one. */
sealed interface Screen {
  /** First launch: what the app does, and the way into sign-up or sign-in. */
  data object Onboarding : Screen

  /** Email and password sign-in for an existing account. */
  data object SignIn : Screen

  /** Registration for a new account. */
  data object SignUp : Screen

  /** One-time-code step that confirms the address given during sign-up. */
  data object VerifyEmail : Screen

  /** Password reset for an account the user can no longer get into. */
  data object ForgotPassword : Screen

  /** Root of the Home tab: every camera in the selected space. */
  data object Home : Screen

  /** Live video and the controls for a single camera. */
  data object CameraDetail : Screen

  /** Per-camera configuration, reachable from both the Home and Settings tabs. */
  data object DeviceSettings : Screen

  /** Onboarding of a new camera into the selected space. */
  data object PairCamera : Screen

  /** Creation of a new space, reached from the cameras list when none exists yet. */
  data object CreateSpace : Screen

  /** The runtime permissions the SDK needs, and the prompts that grant them. */
  data object Permissions : Screen

  /** Root of the Events tab: the activity captured across the space's cameras. */
  data object Events : Screen

  /** A single captured event, with its clip and metadata. */
  data object EventDetail : Screen

  /** Root of the Security tab: the space's security posture and its controls. */
  data object Security : Screen

  /** The guided run through the security setup steps, reachable from Security and Settings. */
  data object SecuritySetup : Screen

  /** The people alerted when the space raises an alarm. */
  data object SecurityContact : Screen

  /** Every arming session the space's plan still retains, newest first. */
  data object SecurityLog : Screen

  /** Everything about monitoring that can still be changed once the checklist is finished. */
  data object SecuritySettings : Screen

  /**
   * One setup step's screen, identified by its [apiName] as the backend names it, for example
   * `"CameraSetup"`. [standalone] marks the screen as opened from security settings rather than
   * from the checklist, which is what stops an edit rewriting the step's `setup_step`.
   */
  data class SecurityStep(val apiName: String, val standalone: Boolean = false) : Screen

  /** Root of the Settings tab: the account, the space and the cameras in it. */
  data object Settings : Screen

  /** Profile, region and sign-out for the signed-in account. */
  data object AccountSettings : Screen

  /** Password change for the signed-in account, which knows its current password. */
  data object ChangePassword : Screen

  /** The selected space itself: its details, members and invitations. */
  data object SpaceSettings : Screen

  /** Renaming of a single camera. */
  data object DeviceName : Screen

  /** Hardware, network and account details for a single camera. */
  data object DeviceInfo : Screen

  /** Firmware version of a single camera, and the update for it when one is waiting. */
  data object DeviceFirmware : Screen

  /** What a single camera detects, and how sensitive it is to each kind of motion. */
  data object DeviceDetection : Screen

  /** The blocks of a single camera's frame it reports movement in. */
  data object DeviceActivityZone : Screen

  /** Which of a single camera's detections raise a notification. */
  data object DeviceNotifications : Screen

  /** Streaming quality and related live-view options for a single camera. */
  data object DeviceLiveView : Screen

  /** Microphone, speaker and siren options for a single camera. */
  data object DeviceAudio : Screen
}

/** Which of the four bottom-bar tabs is showing. */
enum class Tab {
  /** The cameras in the selected space. */
  Home,

  /** The activity those cameras captured. */
  Events,

  /** The space's security posture and its setup. */
  Security,

  /** The account, the space and per-camera configuration. */
  Settings,
}

/** Whether the app is showing the signed-out auth flow or the signed-in tabbed shell. */
enum class NavMode {
  /** Signed out: one flat stack rooted at [Screen.Onboarding], with no bottom bar. */
  Auth,

  /** Signed in: four independent tab stacks behind the bottom bar. */
  Tabs,
}

/** The destination a tab's stack is rooted at and is emptied back to when its tab is re-selected. */
private fun rootOf(tab: Tab): Screen = when (tab) {
  Tab.Home -> Screen.Home
  Tab.Events -> Screen.Events
  Tab.Security -> Screen.Security
  Tab.Settings -> Screen.Settings
}

/** Empties [stack] and seeds it with [root] again, since a stack is never allowed to be empty. */
private fun resetStack(stack: SnapshotStateList<Screen>, root: Screen) {
  stack.clear()
  stack.add(root)
}

/**
 * Back stacks of [Screen]s held in Compose state — one for the auth flow and one per [Tab], so each
 * tab keeps the history the user left it with. Deliberately hand-rolled so the sample stays about
 * the SDK rather than about a navigation library. Not thread-safe; drive it from the UI.
 */
class AppNavigator {
  /** The signed-out stack, never empty and rooted at [Screen.Onboarding]. */
  private val authStack = mutableStateListOf<Screen>(Screen.Onboarding)

  /** One never-empty stack per tab, each rooted at that tab's own root destination. */
  private val tabStacks: Map<Tab, SnapshotStateList<Screen>> =
    Tab.entries.associateWith { mutableStateListOf(rootOf(tab = it)) }

  /** Whether the app is in the signed-out auth flow or the signed-in tabbed shell. */
  var mode: NavMode by mutableStateOf(NavMode.Auth)
    private set

  /** The tab on screen; only meaningful, and only navigated, while [mode] is [NavMode.Tabs]. */
  var currentTab: Tab by mutableStateOf(Tab.Home)
    private set

  /** The stack every navigation operation applies to, chosen by [mode] and [currentTab]. */
  private val activeStack: SnapshotStateList<Screen>
    get() = if (mode == NavMode.Auth) authStack else tabStacks.getValue(currentTab)

  /** The destination currently on screen: the top of whichever stack is active. */
  val current: Screen get() = activeStack.last()

  /** Whether anything sits beneath [current] in the active stack, which gates the back affordance. */
  val canPop: Boolean get() = activeStack.size > 1

  /** True when the active tab stack holds only its root, which is what shows the bottom bar. */
  val atTabRoot: Boolean get() = mode == NavMode.Tabs && activeStack.size == 1

  /** Opens [screen] on top of the current one, within the active stack. */
  fun push(screen: Screen) {
    activeStack.add(screen)
  }

  /** Drops back one destination; returns false when the active stack holds only its root. */
  fun pop(): Boolean = canPop.also { if (it) activeStack.removeAt(activeStack.lastIndex) }

  /** Shows [tab]; selecting the tab already showing pops its stack back to its root instead. */
  fun selectTab(tab: Tab) {
    if (tab == currentTab) {
      resetStack(stack = tabStacks.getValue(tab), root = rootOf(tab = tab))
    } else {
      currentTab = tab
    }
  }

  /** The sign-out transition: back to onboarding, with every tab's history discarded. */
  fun resetToAuth() {
    mode = NavMode.Auth
    resetStack(stack = authStack, root = Screen.Onboarding)
    resetTabStacks()
  }

  /** The sign-in transition: into the Home tab, with both the tabs and the auth flow made clean. */
  fun resetToTabs() {
    mode = NavMode.Tabs
    currentTab = Tab.Home
    resetTabStacks()
    resetStack(stack = authStack, root = Screen.Onboarding)
  }

  /** Returns every tab stack to just its root so no account ever inherits another's history. */
  private fun resetTabStacks() {
    tabStacks.forEach { (tab, stack) -> resetStack(stack = stack, root = rootOf(tab = tab)) }
  }
}

/** Remembers one [AppNavigator] for the lifetime of the calling composition. */
@Composable
fun rememberAppNavigator(): AppNavigator = remember { AppNavigator() }
