package com.bonial.domain.remote.model

import com.google.gson.annotations.SerializedName

data class CharacterResponseDto(
    @SerializedName("info")
    val info: PageInfoDto?,
    @SerializedName("results")
    val results: List<CharacterDto>?,
)
