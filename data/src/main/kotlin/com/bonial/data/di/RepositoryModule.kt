package com.bonial.data.di

import com.bonial.data.repository.CharactersRepositoryImpl
import com.bonial.data.repository.FavouritesRepositoryImpl
import com.bonial.data.repository.LocalStorageRepositoryImpl
import com.bonial.domain.repository.CharactersRepository
import com.bonial.domain.repository.FavouritesRepository
import com.bonial.domain.repository.LocalStorageRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindCharactersRepository(impl: CharactersRepositoryImpl): CharactersRepository

    @Binds
    @Singleton
    abstract fun bindLocalStorageRepository(impl: LocalStorageRepositoryImpl): LocalStorageRepository

    @Binds
    @Singleton
    abstract fun bindFavouritesRepository(impl: FavouritesRepositoryImpl): FavouritesRepository
}
