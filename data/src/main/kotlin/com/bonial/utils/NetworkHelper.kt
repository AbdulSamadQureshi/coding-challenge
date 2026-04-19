package com.bonial.utils

import com.bonial.domain.model.network.response.ApiError
import com.bonial.domain.model.network.response.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import retrofit2.HttpException
import java.io.IOException

private const val HTTP_BAD_REQUEST = 400
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private const val HTTP_REQUEST_TIMEOUT = 408
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_SERVER_ERROR_MIN = 500
private const val HTTP_SERVER_ERROR_MAX = 599

@Suppress("TooGenericExceptionCaught")
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

fun Throwable.isRateLimitError(): Boolean = this is HttpException && code() == HTTP_TOO_MANY_REQUESTS

@Suppress("TooGenericExceptionCaught")
inline fun <reified T> safeApiCall(crossinline apiCall: suspend () -> T): Flow<Request<T>> =
    flow {
        emit(Request.Loading)
        try {
            val result = withRetry { apiCall() }
            emit(Request.Success(result))
        } catch (throwable: Throwable) {
            emit(Request.Error(manageThrowable(throwable)))
        }
    }.flowOn(Dispatchers.IO)

fun manageThrowable(throwable: Throwable): ApiError =
    when (throwable) {
        is IOException ->
            ApiError(
                code = "NetworkError",
                message = "Check your internet connection and try again.",
            )
        is HttpException -> {
            val httpCode = throwable.response()?.code() ?: 0
            ApiError(code = httpCode.toString(), message = httpCode.toUserMessage())
        }
        else ->
            ApiError(
                code = "Unknown",
                message = throwable.message ?: "An unexpected error occurred.",
            )
    }

private fun Int.toUserMessage(): String =
    when (this) {
        HTTP_BAD_REQUEST -> "The request was invalid. Please try again."
        HTTP_UNAUTHORIZED -> "Your session has expired. Please sign in again."
        HTTP_FORBIDDEN -> "You do not have permission to access this resource."
        HTTP_NOT_FOUND -> "The requested resource was not found."
        HTTP_REQUEST_TIMEOUT -> "The request timed out. Please try again."
        HTTP_TOO_MANY_REQUESTS -> "Too many requests. Please wait a moment and retry."
        in HTTP_SERVER_ERROR_MIN..HTTP_SERVER_ERROR_MAX -> "The server is having trouble right now. Please try again later."
        else -> "Something went wrong. Please try again."
    }
