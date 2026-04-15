package com.bonial.domain.remote.model

import com.google.gson.annotations.SerializedName

data class PageInfoDto(
    @SerializedName("count")
    val count: Int = 0,
    @SerializedName("pages")
    val pages: Int = 1,
    @SerializedName("next")
    val next: String? = null,
    @SerializedName("prev")
    val prev: String? = null,
)
