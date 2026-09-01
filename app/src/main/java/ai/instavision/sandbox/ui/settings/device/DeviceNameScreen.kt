package ai.instavision.sandbox.ui.settings.device

import ai.instavision.sandbox.ui.common.AppTextField
import ai.instavision.sandbox.ui.common.PrimaryButton
import ai.instavision.sandbox.ui.common.SectionHeader
import ai.instavision.sandbox.ui.settings.DeviceSubScreen
import androidx.compose.runtime.Composable

/**
 * Renaming of the selected camera and of the spot it watches. Both fields commit through a single
 * Save, which is why the button stays enabled while only one of them has been edited.
 */
@Composable
fun DeviceNameScreen(onBack: () -> Unit) {
  DeviceSubScreen(title = "Name", onBack = onBack) { state, viewModel ->
    SectionHeader(text = "Camera")
    AppTextField(
      value = state.name,
      onValueChange = viewModel::onNameChange,
      placeholder = "Camera name",
      enabled = !state.busy,
    )
    SectionHeader(text = "Location")
    AppTextField(
      value = state.locationName,
      onValueChange = viewModel::onLocationChange,
      placeholder = "Where it watches",
      enabled = !state.busy,
    )
    PrimaryButton(
      text = "Save",
      onClick = viewModel::saveDetails,
      enabled = state.name.isNotBlank(),
      loading = state.busy,
    )
  }
}
