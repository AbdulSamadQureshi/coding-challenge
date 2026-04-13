package com.bonial.brochure.presentation.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.bonial.brochure.presentation.detail.CharacterDetailScreen
import com.bonial.brochure.presentation.home.CharacterDetailViewModel
import com.bonial.brochure.presentation.home.CharactersScreen
import com.bonial.brochure.presentation.home.CharactersViewModel

private const val SLIDE_DURATION_MS = 350

@Composable
fun CharacterNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = CharacterListRoute,
    ) {
        composable<CharacterListRoute>(
            exitTransition = {
                slideOutHorizontally(targetOffsetX = { -it / 3 }) + fadeOut(targetAlpha = 0.6f)
            },
            popEnterTransition = {
                slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn(initialAlpha = 0.6f)
            },
        ) {
            val viewModel: CharactersViewModel = hiltViewModel()
            CharactersScreen(
                viewModel = viewModel,
                onCharacterClick = { characterId ->
                    navController.navigate(CharacterDetailRoute(id = characterId))
                },
            )
        }

        composable<CharacterDetailRoute>(
            enterTransition = {
                slideInHorizontally(initialOffsetX = { it }) + fadeIn()
            },
            popExitTransition = {
                slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
            },
        ) {
            val viewModel: CharacterDetailViewModel = hiltViewModel()
            CharacterDetailScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
