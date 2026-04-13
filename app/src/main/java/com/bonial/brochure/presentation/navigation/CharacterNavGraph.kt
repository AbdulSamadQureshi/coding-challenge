package com.bonial.brochure.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.bonial.brochure.presentation.home.CharacterDetailViewModel
import com.bonial.brochure.presentation.home.CharactersScreen
import com.bonial.brochure.presentation.home.CharactersViewModel
import com.bonial.brochure.presentation.detail.CharacterDetailScreen

@Composable
fun CharacterNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = CharacterListRoute,
    ) {
        composable<CharacterListRoute> {
            val viewModel: CharactersViewModel = hiltViewModel()
            CharactersScreen(
                viewModel = viewModel,
                onCharacterClick = { characterId ->
                    navController.navigate(CharacterDetailRoute(id = characterId))
                },
            )
        }

        composable<CharacterDetailRoute> {
            val viewModel: CharacterDetailViewModel = hiltViewModel()
            CharacterDetailScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
