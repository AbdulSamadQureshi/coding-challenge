package com.bonial.brochure.presentation.home

import androidx.lifecycle.viewModelScope
import com.bonial.brochure.presentation.model.CharacterUi
import com.bonial.brochure.presentation.utils.toErrorMessage
import com.bonial.core.base.MviViewModel
import com.bonial.domain.model.network.response.Request
import com.bonial.domain.useCase.characters.CharactersUseCase
import com.bonial.domain.useCase.favourites.GetFavouriteCoverUrlsUseCase
import com.bonial.domain.useCase.favourites.ToggleFavouriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CharactersState(
    val characters: List<CharacterUi> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingNextPage: Boolean = false,
    val error: String? = null,
    val currentPage: Int = 1,
    val totalPages: Int = 1,
)

sealed class CharactersIntent {
    object LoadCharacters : CharactersIntent()
    object LoadNextPage : CharactersIntent()
    data class ToggleFavourite(val character: CharacterUi) : CharactersIntent()
}

sealed class CharactersEffect {
    data class ShowError(val message: String) : CharactersEffect()
}

@HiltViewModel
class CharactersViewModel @Inject constructor(
    private val charactersUseCase: CharactersUseCase,
    private val getFavouriteCoverUrlsUseCase: GetFavouriteCoverUrlsUseCase,
    private val toggleFavouriteUseCase: ToggleFavouriteUseCase,
) : MviViewModel<CharactersState, CharactersIntent, CharactersEffect>() {

    override fun createInitialState(): CharactersState = CharactersState()

    init {
        sendIntent(CharactersIntent.LoadCharacters)
    }

    override fun handleIntent(intent: CharactersIntent) {
        when (intent) {
            is CharactersIntent.LoadCharacters -> loadCharacters(page = 1, isNextPage = false)
            is CharactersIntent.LoadNextPage -> {
                val state = uiState.value
                if (!state.isLoadingNextPage && state.currentPage < state.totalPages) {
                    loadCharacters(page = state.currentPage + 1, isNextPage = true)
                }
            }
            is CharactersIntent.ToggleFavourite -> toggleFavourite(intent.character)
        }
    }

    private fun loadCharacters(page: Int, isNextPage: Boolean) {
        viewModelScope.launch {
            if (isNextPage) {
                setState { copy(isLoadingNextPage = true) }
            } else {
                setState { copy(isLoading = true, error = null) }
            }

            combine(
                charactersUseCase(page),
                getFavouriteCoverUrlsUseCase(),
            ) { response, favouriteUrls ->
                when (response) {
                    is Request.Loading -> null
                    is Request.Error -> {
                        val message = response.apiError.toErrorMessage()
                        setEffect { CharactersEffect.ShowError(message) }
                        setState { copy(isLoading = false, isLoadingNextPage = false, error = message) }
                        null
                    }
                    is Request.Success -> {
                        val newItems = response.data.characters.map { character ->
                            CharacterUi(
                                id = character.id,
                                name = character.name,
                                status = character.status,
                                species = character.species,
                                imageUrl = character.imageUrl,
                                isFavourite = character.imageUrl != null && character.imageUrl in favouriteUrls,
                            )
                        }
                        Triple(newItems, response.data.totalPages, favouriteUrls)
                    }
                }
            }.collectLatest { result ->
                result ?: return@collectLatest
                val (newItems, totalPages, favouriteUrls) = result
                setState {
                    val updatedAll = if (isNextPage) {
                        characters + newItems
                    } else {
                        newItems
                    }
                    // Re-apply favourite state on all items in case favourites changed
                    val withFavourites = updatedAll.map { item ->
                        item.copy(isFavourite = item.imageUrl != null && item.imageUrl in favouriteUrls)
                    }
                    copy(
                        characters = withFavourites,
                        isLoading = false,
                        isLoadingNextPage = false,
                        currentPage = if (isNextPage) currentPage + 1 else 1,
                        totalPages = totalPages,
                        error = null,
                    )
                }
            }
        }
    }

    private fun toggleFavourite(character: CharacterUi) {
        val imageUrl = character.imageUrl ?: return
        viewModelScope.launch {
            toggleFavouriteUseCase(imageUrl, character.isFavourite)
        }
    }
}
