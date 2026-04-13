package com.bonial.domain.model.network.response

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class PageInfoDto(
    @SerializedName("count")
    val count: Int = 0,
    @SerializedName("pages")
    val pages: Int = 1,
    @SerializedName("next")
    val next: String? = null,
    @SerializedName("prev")
    val prev: String? = null,
) : Parcelable
