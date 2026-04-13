package com.bonial.brochure.presentation.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.bonial.brochure.presentation.model.CharacterDetailUi
import com.bonial.brochure.presentation.navigation.CharacterDetailRoute
import com.bonial.brochure.presentation.utils.toErrorMessage
import com.bonial.core.base.MviViewModel
import com.bonial.domain.model.network.response.Request
import com.bonial.domain.useCase.characters.CharacterDetailUseCase
import com.bonial.domain.useCase.favourites.IsFavouriteFlowUseCase
import com.bonial.domain.useCase.favourites.ToggleFavouriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CharacterDetailState(
    val character: CharacterDetailUi? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val isFavourite: Boolean = false,
)

sealed class CharacterDetailIntent {
    object ToggleFavourite : CharacterDetailIntent()
}

sealed class CharacterDetailEffect

@HiltViewModel
class CharacterDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val characterDetailUseCase: CharacterDetailUseCase,
    private val isFavouriteFlowUseCase: IsFavouriteFlowUseCase,
    private val toggleFavouriteUseCase: ToggleFavouriteUseCase,
) : MviViewModel<CharacterDetailState, CharacterDetailIntent, CharacterDetailEffect>() {

    private val route = savedStateHandle.toRoute<CharacterDetailRoute>()

    override fun createInitialState(): CharacterDetailState = CharacterDetailState()

    init {
        loadCharacter(route.id)
    }

    override fun handleIntent(intent: CharacterDetailIntent) {
        when (intent) {
            is CharacterDetailIntent.ToggleFavourite -> toggleFavourite()
        }
    }

    private fun loadCharacter(id: Int) {
        viewModelScope.launch {
            setState { copy(isLoading = true, error = null) }
            characterDetailUseCase(id).collectLatest { response ->
                when (response) {
                    is Request.Loading -> Unit
                    is Request.Success -> {
                        val detail = response.data
                        setState {
                            copy(
                                character = CharacterDetailUi(
                                    id = detail.id,
                                    name = detail.name,
                                    status = detail.status,
                                    species = detail.species,
                                    gender = detail.gender,
                                    origin = detail.origin,
                                    location = detail.location,
                                    imageUrl = detail.imageUrl,
                                ),
                                isLoading = false,
                            )
                        }
                        detail.imageUrl?.let { observeFavourite(it) }
                    }
                    is Request.Error -> {
                        val message = response.apiError.toErrorMessage()
                        setState { copy(isLoading = false, error = message) }
                    }
                }
            }
        }
    }

    private fun observeFavourite(imageUrl: String) {
        viewModelScope.launch {
            isFavouriteFlowUseCase(imageUrl).collectLatest { isFav ->
                setState { copy(isFavourite = isFav) }
            }
        }
    }

    private fun toggleFavourite() {
        val imageUrl = uiState.value.character?.imageUrl ?: return
        viewModelScope.launch {
            toggleFavouriteUseCase(imageUrl, uiState.value.isFavourite)
        }
    }
}
