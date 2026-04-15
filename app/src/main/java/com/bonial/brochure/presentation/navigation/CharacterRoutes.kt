package com.bonial.brochure.presentation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object CharacterListKey : NavKey

@Serializable
data class CharacterDetailKey(val id: Int) : NavKey
