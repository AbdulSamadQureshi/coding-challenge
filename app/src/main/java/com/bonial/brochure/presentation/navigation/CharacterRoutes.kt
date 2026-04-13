package com.bonial.brochure.presentation.navigation

import kotlinx.serialization.Serializable

@Serializable
object CharacterListRoute

@Serializable
data class CharacterDetailRoute(
    val id: Int,
)
