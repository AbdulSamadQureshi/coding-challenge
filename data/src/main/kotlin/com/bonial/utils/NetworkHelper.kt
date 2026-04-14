package com.bonial.utils

import com.bonial.domain.model.network.response.ApiError
import com.bonial.domain.model.network.response.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import retrofit2.HttpException
import java.io.IOException

/**
 * A safeApiCall wrapper for Retrofit that converts suspend functions into a Flow emitting Request states.
 * It automatically emits loading, success, and error states.
 *
 * @param apiCall The Retrofit API call to be executed (e.g., apiService.getUser(id)).
 */
inline fun <reified T> safeApiCall(crossinline apiCall: suspend () -> T): Flow<Request<T>> {
    return flow {
        try {
            emit(Request.Loading)
            val result = apiCall()
            emit(Request.Success(result))
        } catch (throwable: Throwable) {
            emit(Request.Error(manageThrowable(throwable)))
        }
    }.flowOn(Dispatchers.IO)
}

/**
 * Parses a Throwable to a user-friendly ApiError. HTTP codes are mapped to messages
 * that are safe to surface in the UI — raw Retrofit messages often leak URLs or
 * framework-internal text that does not help the user.
 */
fun manageThrowable(throwable: Throwable): ApiError = when (throwable) {
    is IOException -> ApiError(
        code = "NetworkError",
        message = "Check your internet connection and try again.",
    )
    is HttpException -> {
        val httpCode = throwable.response()?.code() ?: 0
        ApiError(code = httpCode.toString(), message = httpCode.toUserMessage())
    }
    else -> ApiError(
        code = "Unknown",
        message = throwable.message ?: "An unexpected error occurred.",
    )
}

private fun Int.toUserMessage(): String = when (this) {
    400 -> "The request was invalid. Please try again."
    401 -> "Your session has expired. Please sign in again."
    403 -> "You do not have permission to access this resource."
    404 -> "The requested resource was not found."
    408 -> "The request timed out. Please try again."
    429 -> "Too many requests. Please wait a moment and retry."
    in 500..599 -> "The server is having trouble right now. Please try again later."
    else -> "Something went wrong. Please try again."
}
