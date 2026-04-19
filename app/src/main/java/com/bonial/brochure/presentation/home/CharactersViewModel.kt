package com.bonial.brochure.presentation.home

import androidx.lifecycle.viewModelScope
import com.bonial.brochure.presentation.model.CharacterUi
import com.bonial.brochure.presentation.utils.toErrorMessage
import com.bonial.core.base.MviViewModel
import com.bonial.domain.model.CharactersWithFavouritePage
import com.bonial.domain.model.network.response.Request
import com.bonial.domain.useCase.characters.CharactersParams
import com.bonial.domain.useCase.characters.GetEnrichedCharactersUseCase
import com.bonial.domain.useCase.favourites.ToggleFavouriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CharactersState(
    val characters: List<CharacterUi> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingNextPage: Boolean = false,
    val error: String? = null,
    val paginationError: String? = null,
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val searchQuery: String = "",
    val isInitialLoading: Boolean = true,
)

sealed class CharactersIntent {
    data object LoadCharacters : CharactersIntent()

    data object LoadNextPage : CharactersIntent()

    data class ToggleFavourite(
        val character: CharacterUi,
    ) : CharactersIntent()

    data class Search(
        val query: String,
    ) : CharactersIntent()
}

// No one-shot effects on the list screen — errors and empty states are part of persistent state.
sealed class CharactersEffect

@OptIn(FlowPreview::class)
@HiltViewModel
class CharactersViewModel
    @Inject
    constructor(
        private val getEnrichedCharactersUseCase: GetEnrichedCharactersUseCase,
        private val toggleFavouriteUseCase: ToggleFavouriteUseCase,
    ) : MviViewModel<CharactersState, CharactersIntent, CharactersEffect>() {
        /**
         * Pair<query, generation> — incrementing generation forces re-emission from StateFlow
         * even when the query string hasn't changed (e.g. retry after an error).
         */
        private val searchParams = MutableStateFlow("" to 0)

        /**
         * Tracks the currently running pagination coroutine so it can be cancelled
         * immediately when a new search fires, preventing stale page-N results from
         * landing on top of the fresh result set.
         */
        private var paginationJob: Job? = null

        override fun createInitialState(): CharactersState = CharactersState()

        init {
            observeSearch()
            sendIntent(CharactersIntent.LoadCharacters)
        }

        override fun handleIntent(intent: CharactersIntent) {
            when (intent) {
                is CharactersIntent.LoadCharacters -> {
                    // Increment generation so StateFlow re-emits even if query is unchanged.
                    searchParams.update { (query, gen) -> query to gen + 1 }
                }

                is CharactersIntent.LoadNextPage -> {
                    val state = uiState.value
                    // Guard: skip if initial load is still running OR we're already on the last page.
                    if (!state.isLoading && !state.isLoadingNextPage && state.currentPage < state.totalPages) {
                        setState { copy(paginationError = null) }
                        loadNextPage(
                            page = state.currentPage + 1,
                            query = state.searchQuery,
                        )
                    }
                }

                is CharactersIntent.ToggleFavourite -> toggleFavourite(intent.character)

                is CharactersIntent.Search -> {
                    // Cancel any in-flight pagination so stale results don't arrive after the
                    // new search result set lands.
                    paginationJob?.cancel()
                    paginationJob = null

                    setState {
                        copy(
                            searchQuery = intent.query,
                            characters = emptyList(),
                            isLoading = true,
                            error = null,
                        )
                    }
                    // Emit with generation=0 for a brand-new query (debounce applies).
                    searchParams.value = intent.query to 0
                }
            }
        }

        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        private fun observeSearch() {
            viewModelScope.launch {
                searchParams
                    .debounce { (query, gen) ->
                        when {
                            gen > 0 -> 0L // Retry — fire immediately.
                            query.isEmpty() -> 0L // Clear — fire immediately.
                            else -> SEARCH_DEBOUNCE_MS
                        }
                    }.flatMapLatest { (query, _) ->
                        getEnrichedCharactersUseCase(CharactersParams(page = 1, name = query))
                    }.collect { response ->
                        handleResponse(
                            response = response,
                            isNextPage = false,
                            page = 1,
                        )
                    }
            }
        }

        private fun loadNextPage(
            page: Int,
            query: String?,
        ) {
            paginationJob =
                viewModelScope.launch {
                    setState { copy(isLoadingNextPage = true) }

                    getEnrichedCharactersUseCase(CharactersParams(page, query)).collectLatest { response ->
                        handleResponse(response, isNextPage = true, page = page)
                    }
                }
        }

        private fun handleResponse(
            response: Request<CharactersWithFavouritePage>,
            isNextPage: Boolean,
            page: Int,
        ) {
            when (response) {
                is Request.Loading ->
                    setState {
                        copy(
                            isLoading = if (isNextPage) isLoading else true,
                            isLoadingNextPage = isNextPage,
                        )
                    }
                is Request.Success -> {
                    val newItems =
                        response.data.characters.map { character ->
                            CharacterUi(
                                id = character.id,
                                name = character.name,
                                status = character.status,
                                species = character.species,
                                imageUrl = character.imageUrl,
                                isFavourite = character.isFavourite,
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
                            paginationError = null,
                            isInitialLoading = false,
                        )
                    }
                }
                is Request.Error -> handleError(response, isNextPage)
            }
        }

        private fun handleError(
            response: Request.Error,
            isNextPage: Boolean,
        ) {
            val isNoResults = response.apiError?.code == "404"
            val message = response.apiError.toErrorMessage()

            if (isNextPage && !isNoResults) {
                // Pagination failure — preserve the loaded list and show a
                // sticky retry banner in the grid footer.
                setState { copy(isLoadingNextPage = false, paginationError = message) }
            } else {
                setState {
                    copy(
                        characters = if (!isNextPage && isNoResults) emptyList() else characters,
                        isLoading = false,
                        isLoadingNextPage = false,
                        error = if (isNoResults) null else message,
                        paginationError = null,
                        isInitialLoading = false,
                    )
                }
            }
        }

        private fun toggleFavourite(character: CharacterUi) {
            val imageUrl = character.imageUrl ?: return
            viewModelScope.launch {
                toggleFavouriteUseCase(imageUrl, character.isFavourite)
            }
        }

        companion object {
            private const val SEARCH_DEBOUNCE_MS = 1_000L
        }
    }
