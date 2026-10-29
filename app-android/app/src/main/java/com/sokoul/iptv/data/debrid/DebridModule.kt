package com.sokoul.iptv.data.debrid

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DebridModule {
    @Binds
    @Singleton
    abstract fun debridService(realDebrid: RealDebridClient): DebridService
}
