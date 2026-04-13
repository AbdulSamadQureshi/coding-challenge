package com.bonial.data.di

import android.content.Context
import androidx.room.Room
import com.bonial.data.local.BrochureLocalDataSource
import com.bonial.data.local.BrochureLocalDataSourceImpl
import com.bonial.data.local.BrochuresDao
import com.bonial.data.local.BrochuresDatabase
import com.bonial.data.local.FavouritesDao
import com.bonial.data.remote.service.CharactersApiService
import com.squareup.anvil.annotations.ContributesTo
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
@ContributesTo(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideCharactersApiService(retrofit: Retrofit): CharactersApiService {
        return retrofit.create(CharactersApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideBrochuresDatabase(@ApplicationContext context: Context): BrochuresDatabase {
        return Room.databaseBuilder(
            context,
            BrochuresDatabase::class.java,
            "brochures.db",
        )
            .addMigrations(BrochuresDatabase.MIGRATION_1_2)
            .build()
    }

    @Provides
    @Singleton
    fun provideBrochuresDao(database: BrochuresDatabase): BrochuresDao {
        return database.brochuresDao()
    }

    @Provides
    @Singleton
    fun provideFavouritesDao(database: BrochuresDatabase): FavouritesDao {
        return database.favouritesDao()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class LocalDataSourceModule {

    @Binds
    @Singleton
    abstract fun bindBrochureLocalDataSource(
        impl: BrochureLocalDataSourceImpl,
    ): BrochureLocalDataSource
}
