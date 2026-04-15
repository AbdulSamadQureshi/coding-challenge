package com.bonial.brochure.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.bonial.brochure.presentation.detail.CharacterDetailScreen
import com.bonial.brochure.presentation.home.CharacterDetailViewModel
import com.bonial.brochure.presentation.home.CharactersScreen
import com.bonial.brochure.presentation.home.CharactersViewModel

@Composable
fun CharacterNavGraph() {
    val backStack = rememberNavBackStack(CharacterListKey)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = remember {
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            )
        },
        entryProvider = entryProvider {
            entry<CharacterListKey> {
                val viewModel: CharactersViewModel = hiltViewModel()
                CharactersScreen(
                    viewModel = viewModel,
                    onCharacterClick = { characterId ->
                        backStack.add(CharacterDetailKey(id = characterId))
                    },
                )
            }

            entry<CharacterDetailKey> { key ->
                val viewModel = hiltViewModel<CharacterDetailViewModel, CharacterDetailViewModel.Factory>(
                    creationCallback = { factory -> factory.create(key) },
                )
                CharacterDetailScreen(
                    viewModel = viewModel,
                    onBack = { backStack.removeLastOrNull() },
                )
            }
        },
    )
}
