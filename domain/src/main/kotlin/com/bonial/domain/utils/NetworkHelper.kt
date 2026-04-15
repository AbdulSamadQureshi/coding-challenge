package com.bonial.domain.utils

import com.bonial.domain.model.network.response.ApiError
import com.bonial.domain.model.network.response.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import retrofit2.HttpException
import java.io.IOException

suspend fun <T> withRetry(
    maxAttempts: Int = 20,
    retryDelayMs: Long = 500L,
    delayProvider: suspend (Long) -> Unit = { ms -> delay(ms) },
    isRetryable: (Throwable) -> Boolean = Throwable::isRateLimitError,
    block: suspend () -> T,
): T {
    for (attempt in 1..maxAttempts) {
        try {
            return block()
        } catch (t: Throwable) {
            if (!isRetryable(t) || attempt == maxAttempts) throw t
            delayProvider(retryDelayMs)
        }
    }
    error("withRetry: unreachable after $maxAttempts attempts")
}

fun Throwable.isRateLimitError(): Boolean =
    this is HttpException && code() == 429

inline fun <reified T> safeApiCall(crossinline apiCall: suspend () -> T): Flow<Request<T>> {
    return flow {
        emit(Request.Loading)
        try {
            val result = withRetry { apiCall() }
            emit(Request.Success(result))
        } catch (throwable: Throwable) {
            emit(Request.Error(manageThrowable(throwable)))
        }
    }.flowOn(Dispatchers.IO)
}

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
