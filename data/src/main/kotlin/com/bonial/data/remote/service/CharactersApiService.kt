package com.bonial.data.remote.service

import com.bonial.domain.model.network.response.CharacterDto
import com.bonial.domain.model.network.response.CharacterResponseDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface CharactersApiService {
    @GET("character")
    suspend fun characters(@Query("page") page: Int): CharacterResponseDto

    @GET("character/{id}")
    suspend fun character(@Path("id") id: Int): CharacterDto
}
