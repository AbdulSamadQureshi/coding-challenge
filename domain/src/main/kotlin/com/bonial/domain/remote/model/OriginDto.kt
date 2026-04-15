package com.bonial.domain.remote.model

import com.google.gson.annotations.SerializedName

data class OriginDto(
    @SerializedName("name")
    val name: String?,
)
