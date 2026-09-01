package ai.instavision.sandbox.ui.space

import ai.instavision.sandbox.ui.common.AppTextField
import ai.instavision.sandbox.ui.common.DetailScaffold
import ai.instavision.sandbox.ui.common.ErrorBanner
import ai.instavision.sandbox.ui.common.PrimaryButton
import ai.instavision.sandbox.ui.common.SectionHeader
import ai.instavision.sandbox.ui.theme.AppTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Form that creates a space and makes it the one the rest of the app works with. Only the name is
 * asked for; the address is offered but never insisted on.
 */
@Composable
fun CreateSpaceScreen(onBack: () -> Unit, onCreated: () -> Unit) {
  val viewModel: CreateSpaceViewModel = viewModel()
  val state by viewModel.state.collectAsStateWithLifecycle()

  LaunchedEffect(state.created) {
    if (state.created) {
      viewModel.onNavigationHandled()
      onCreated()
    }
  }

  DetailScaffold(title = "", onBack = onBack) {
    Column(verticalArrangement = Arrangement.spacedBy(space = HeroSpacing)) {
      Text(
        text = "Name your space",
        style = MaterialTheme.typography.headlineMedium,
        color = AppTheme.colors.textPrimary,
      )
      Text(
        text = "A space groups the cameras in one place — a home, an office, a cabin. You can " +
          "add more later.",
        style = MaterialTheme.typography.bodyLarge,
        color = AppTheme.colors.textSecondary,
      )
    }
    ErrorBanner(message = state.error)
    AppTextField(
      value = state.name,
      onValueChange = viewModel::onNameChange,
      placeholder = "Space name",
      enabled = !state.loading,
    )
    SectionHeader(text = "Address (optional)")
    AddressForm(state = state, viewModel = viewModel)
    PrimaryButton(
      text = "Continue",
      onClick = viewModel::createSpace,
      enabled = state.canSubmit,
      loading = state.loading,
    )
  }
}

/** The address lines, grouped the way the edit-space form groups them. Every one may be left blank. */
@Composable
private fun AddressForm(state: CreateSpaceUiState, viewModel: CreateSpaceViewModel) {
  Column(verticalArrangement = Arrangement.spacedBy(space = FieldGap)) {
    AppTextField(
      value = state.street,
      onValueChange = viewModel::onStreetChange,
      placeholder = "Street",
      enabled = !state.loading,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(space = FieldGap)) {
      AppTextField(
        value = state.city,
        onValueChange = viewModel::onCityChange,
        placeholder = "City",
        modifier = Modifier.weight(1f),
        enabled = !state.loading,
      )
      AppTextField(
        value = state.region,
        onValueChange = viewModel::onRegionChange,
        placeholder = "State",
        modifier = Modifier.weight(1f),
        enabled = !state.loading,
      )
    }
    AppTextField(
      value = state.postalCode,
      onValueChange = viewModel::onPostalCodeChange,
      placeholder = "ZIP",
      enabled = !state.loading,
    )
  }
}

/** Gap between the hero title and the line underneath it. */
private val HeroSpacing = 8.dp

/** Gap between two fields of the same address block. */
private val FieldGap = 12.dp
