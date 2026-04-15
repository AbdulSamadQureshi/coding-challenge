package com.bonial.domain.utils

import com.bonial.domain.model.network.response.Request

internal inline fun <T, R> Request<T>.mapSuccess(transform: (T) -> R): Request<R> = when (this) {
    is Request.Loading -> Request.Loading
    is Request.Error -> Request.Error(apiError)
    is Request.Success -> Request.Success(transform(data))
}
