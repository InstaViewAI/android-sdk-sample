package ai.instavision.sandbox.ui.pairing

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Walks one camera through setup as a five-step wizard: powering it on, finding it over Bluetooth,
 * connecting it to a network — over Bluetooth, or by showing it a code it reads itself — or
 * activating its SIM, waiting for the backend to report it online, and naming it. Every page comes
 * from the one state the ViewModel publishes, so the SDK's own sequence decides which is on screen.
 *
 * The ViewModel belongs to the activity rather than to this destination, so it outlives the screen.
 * Every way out therefore quits the flow explicitly: the Bluetooth link is dropped and the state
 * thrown away, and the next visit starts at step one instead of resuming an abandoned attempt.
 */
@Composable
fun PairCameraScreen(
  onBack: () -> Unit,
  onPaired: () -> Unit,
) {
  val viewModel: PairCameraViewModel = viewModel()
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val exit: () -> Unit = {
    viewModel.quitPairingFlow()
    onBack()
  }
  val back: () -> Unit = { if (!viewModel.goBack()) exit() }

  BackHandler(onBack = back)

  LaunchedEffect(state.finished) {
    if (state.finished) {
      viewModel.quitPairingFlow()
      onPaired()
    }
  }

  when (state.page) {
    PairPage.PowerOn -> PowerOnPage(
      onBack = back,
      onExit = exit,
      onBlinking = viewModel::startSearch,
      onNotBlinking = viewModel::showReset,
    )

    PairPage.ResetCamera -> ResetCameraPage(
      onBack = back,
      onExit = exit,
      onDone = viewModel::startSearch,
      onStillStuck = viewModel::showPowerOn,
    )

    PairPage.Searching -> SearchingPage(
      state = state,
      onBack = back,
      onExit = exit,
      onPermissionsResult = { granted, bluetoothEnabled ->
        viewModel.onPermissionsResult(granted = granted, bluetoothEnabled = bluetoothEnabled)
      },
      onChoose = viewModel::showPicker,
      onGenerateCode = viewModel::startQrPairing,
    )

    PairPage.PickCamera -> PickCameraPage(
      state = state,
      onBack = back,
      onExit = exit,
      onSelect = viewModel::selectCamera,
      onNotListed = viewModel::showNoCameraFound,
    )

    PairPage.NoCameraFound -> NoCameraFoundPage(
      onBack = back,
      onExit = exit,
      onSearchAgain = viewModel::startSearch,
      onGenerateCode = viewModel::startQrPairing,
    )

    PairPage.Connecting -> ConnectingPage(state = state, onBack = back, onExit = exit)

    PairPage.ChooseNetwork -> ChooseNetworkPage(
      state = state,
      onBack = back,
      onExit = exit,
      onSelect = viewModel::selectNetwork,
      onManual = viewModel::useNetwork,
    )

    PairPage.WifiDetails -> WifiDetailsPage(
      state = state,
      onBack = back,
      onExit = exit,
      onSsidChange = viewModel::setSsid,
      onPasswordChange = viewModel::setPassword,
      onRememberChange = viewModel::setRememberPassword,
      onContinue = viewModel::submitWifi,
    )

    PairPage.ShowCode -> ShowCodePage(state = state, onBack = back, onExit = exit)

    PairPage.SimIntro -> SimIntroPage(
      onBack = back,
      onExit = exit,
      onContinue = viewModel::showSimNumber,
    )

    PairPage.SimNumber -> SimNumberPage(
      state = state,
      onBack = back,
      onExit = exit,
      onSimNumberChange = viewModel::setSimNumber,
      onContinue = viewModel::validateSim,
    )

    PairPage.SimInvalid -> SimInvalidPage(
      state = state,
      onBack = back,
      onExit = exit,
      onRetry = viewModel::showSimNumber,
    )

    PairPage.SimActivating -> SimActivatingPage(state = state, onBack = back, onExit = exit)

    PairPage.Adding -> AddingPage(state = state)

    PairPage.Connected -> ConnectedPage(
      state = state,
      onNameChange = viewModel::setCameraName,
      onFinish = viewModel::finishSetup,
    )

    PairPage.Stopped -> StoppedPage(
      state = state,
      onExit = exit,
      onStartOver = viewModel::restart,
    )
  }
}
