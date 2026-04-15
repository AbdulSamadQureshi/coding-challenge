package com.bonial.domain.remote.model

import com.google.gson.annotations.SerializedName

data class LocationDto(
    @SerializedName("name")
    val name: String?,
)
