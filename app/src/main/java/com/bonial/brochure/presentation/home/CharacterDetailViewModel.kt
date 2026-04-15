package com.bonial.brochure.presentation.home

import com.bonial.brochure.presentation.model.CharacterDetailUi
import com.bonial.brochure.presentation.navigation.CharacterDetailKey
import com.bonial.brochure.presentation.utils.toErrorMessage
import com.bonial.core.base.MviViewModel
import com.bonial.domain.model.network.response.Request
import com.bonial.domain.useCase.characters.CharacterDetailUseCase
import com.bonial.domain.useCase.favourites.IsFavouriteFlowUseCase
import com.bonial.domain.useCase.favourites.ToggleFavouriteUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope

data class CharacterDetailState(
    val character: CharacterDetailUi? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val isFavourite: Boolean = false,
)

sealed class CharacterDetailIntent {
    object ToggleFavourite : CharacterDetailIntent()
    object Retry : CharacterDetailIntent()
    object ShareCharacter : CharacterDetailIntent()
}

sealed class CharacterDetailEffect {
    /** Carry the fully-formed share text so the UI only needs to call startActivity. */
    data class Share(val text: String) : CharacterDetailEffect()
}

@HiltViewModel(assistedFactory = CharacterDetailViewModel.Factory::class)
class CharacterDetailViewModel @AssistedInject constructor(
    @Assisted val navKey: CharacterDetailKey,
    private val characterDetailUseCase: CharacterDetailUseCase,
    private val isFavouriteFlowUseCase: IsFavouriteFlowUseCase,
    private val toggleFavouriteUseCase: ToggleFavouriteUseCase,
) : MviViewModel<CharacterDetailState, CharacterDetailIntent, CharacterDetailEffect>() {

    /**
     * Tracks the active load coroutine. If the screen is somehow recreated or the
     * id changes (deep-link scenario), the in-flight request is cancelled before
     * starting a new one so stale results can never overwrite a fresher response.
     */
    private var loadJob: Job? = null

    override fun createInitialState(): CharacterDetailState = CharacterDetailState()

    init {
        loadCharacter(navKey.id)
    }

    override fun handleIntent(intent: CharacterDetailIntent) {
        when (intent) {
            is CharacterDetailIntent.ToggleFavourite -> toggleFavourite()
            is CharacterDetailIntent.Retry -> loadCharacter(navKey.id)
            is CharacterDetailIntent.ShareCharacter -> shareCharacter()
        }
    }

    private fun loadCharacter(id: Int) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
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

    /**
     * Builds the plain-text share payload from the current character state and
     * emits it as a [CharacterDetailEffect.Share] one-shot effect.
     *
     * Pure string logic — no Android framework dependency — so it is fully unit-testable.
     * The composable receives the ready-made text and only calls startActivity.
     */
    private fun shareCharacter() {
        val character = uiState.value.character ?: return
        val text = buildString {
            append(character.name ?: "")
            character.species?.let { append(" · $it") }
            character.status?.let { append(" · $it") }
            character.imageUrl?.let { append("\n$it") }
        }
        setEffect { CharacterDetailEffect.Share(text) }
    }

    private fun toggleFavourite() {
        val imageUrl = uiState.value.character?.imageUrl ?: return
        viewModelScope.launch {
            toggleFavouriteUseCase(imageUrl, uiState.value.isFavourite)
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(navKey: CharacterDetailKey): CharacterDetailViewModel
    }
}
