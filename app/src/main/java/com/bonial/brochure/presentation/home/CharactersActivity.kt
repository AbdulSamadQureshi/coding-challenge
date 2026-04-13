package com.bonial.brochure.presentation.home

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.bonial.brochure.presentation.navigation.CharacterNavGraph
import com.bonial.brochure.presentation.theme.CloseLoopWalletTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CharactersActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CloseLoopWalletTheme {
                val navController = rememberNavController()
                CharacterNavGraph(navController = navController)
            }
        }
    }
}
