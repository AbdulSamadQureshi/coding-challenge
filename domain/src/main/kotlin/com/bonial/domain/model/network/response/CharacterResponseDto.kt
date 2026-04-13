package com.bonial.domain.model.network.response

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class CharacterResponseDto(
    @SerializedName("info")
    val info: PageInfoDto?,
    @SerializedName("results")
    val results: List<CharacterDto>?,
) : Parcelable
