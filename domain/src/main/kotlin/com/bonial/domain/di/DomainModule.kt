package com.bonial.domain.di

import com.bonial.domain.remote.service.CharactersApiService
import com.bonial.domain.repository.CharactersRepository
import com.bonial.domain.repository.CharactersRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DomainModule {

    @Binds
    @Singleton
    abstract fun bindCharactersRepository(
        charactersRepositoryImpl: CharactersRepositoryImpl
    ): CharactersRepository

    companion object {
        @Provides
        @Singleton
        fun provideCharactersApiService(retrofit: Retrofit): CharactersApiService {
            return retrofit.create(CharactersApiService::class.java)
        }
    }
}
