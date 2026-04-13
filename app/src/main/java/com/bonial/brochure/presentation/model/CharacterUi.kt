package com.bonial.brochure.presentation.model

data class CharacterUi(
    val id: Int,
    val name: String?,
    val status: String?,
    val species: String?,
    val imageUrl: String?,
    val isFavourite: Boolean = false,
)
