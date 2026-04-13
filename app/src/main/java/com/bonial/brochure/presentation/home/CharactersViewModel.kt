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
import kotlinx.coroutines.flow.first
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
        observeFavourites()
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

    /**
     * Loads a page of characters. Pagination and favourites are intentionally decoupled:
     * this function only manages the list and page state; [observeFavourites] independently
     * keeps the isFavourite flag up-to-date on the accumulated list.
     */
    private fun loadCharacters(page: Int, isNextPage: Boolean) {
        viewModelScope.launch {
            if (isNextPage) {
                setState { copy(isLoadingNextPage = true) }
            } else {
                setState { copy(isLoading = true, error = null) }
            }

            val savedFavourites = getFavouriteCoverUrlsUseCase().first()

            charactersUseCase(page).collectLatest { response ->
                when (response) {
                    is Request.Loading -> Unit
                    is Request.Success -> {
                        val newItems = response.data.characters.map { character ->
                            CharacterUi(
                                id = character.id,
                                name = character.name,
                                status = character.status,
                                species = character.species,
                                imageUrl = character.imageUrl,
                                isFavourite = character.imageUrl != null && character.imageUrl in savedFavourites,
                            )
                        }
                        setState {
                            copy(
                                characters = if (isNextPage) characters + newItems else newItems,
                                isLoading = false,
                                isLoadingNextPage = false,
                                currentPage = page,
                                totalPages = response.data.totalPages,
                                error = null,
                            )
                        }
                    }
                    is Request.Error -> {
                        val message = response.apiError.toErrorMessage()
                        setEffect { CharactersEffect.ShowError(message) }
                        setState { copy(isLoading = false, isLoadingNextPage = false, error = message) }
                    }
                }
            }
        }
    }

    /**
     * Runs for the lifetime of the ViewModel, reactively updating the isFavourite flag
     * on every item whenever the favourites set changes — without touching pagination state.
     */
    private fun observeFavourites() {
        viewModelScope.launch {
            getFavouriteCoverUrlsUseCase().collectLatest { favouriteUrls ->
                setState {
                    copy(
                        characters = characters.map { item ->
                            item.copy(isFavourite = item.imageUrl != null && item.imageUrl in favouriteUrls)
                        },
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
