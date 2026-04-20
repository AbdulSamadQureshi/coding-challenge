package com.bonial.domain.utils

import com.bonial.domain.model.network.response.ApiError
import com.bonial.domain.model.network.response.Request
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MapSuccessTest {
    @Test
    fun `transforming a successful result applies the mapping function`() {
        val result = Request.Success(42).mapSuccess { it * 2 }
        assertThat((result as Request.Success).data).isEqualTo(84)
    }

    @Test
    fun `transforming a loading state leaves it unchanged`() {
        val result = Request.Loading.mapSuccess<Nothing, String> { "should not run" }
        assertThat(result).isInstanceOf(Request.Loading::class.java)
    }

    @Test
    fun `transforming an error state leaves it unchanged`() {
        val error = ApiError("404", "Not found")
        val result = Request.Error(error).mapSuccess<Nothing, String> { "should not run" }
        assertThat((result as Request.Error).apiError?.code).isEqualTo("404")
    }
}
