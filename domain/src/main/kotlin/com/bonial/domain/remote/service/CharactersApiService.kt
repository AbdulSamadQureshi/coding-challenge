package com.bonial.domain.remote.service

import com.bonial.domain.remote.model.CharacterDto
import com.bonial.domain.remote.model.CharacterResponseDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface CharactersApiService {
    @GET("character")
    suspend fun characters(
        @Query("page") page: Int,
        @Query("name") name: String? = null
    ): CharacterResponseDto

    @GET("character/{id}")
    suspend fun character(@Path("id") id: Int): CharacterDto
}
