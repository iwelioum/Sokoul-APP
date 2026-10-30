package com.sokoul.iptv.data.net

import com.sokoul.iptv.BuildConfig
import com.sokoul.iptv.data.AppSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object HttpModule {

    @Provides
    @Singleton
    fun okHttp(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            // pool généreux : le zapping réutilise les connexions déjà ouvertes
            .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
            .dispatcher(Dispatcher().apply { maxRequestsPerHost = 10 })
            .addInterceptor { chain ->
                val req = chain.request()
                if (req.header("User-Agent") == null) {
                    chain.proceed(req.newBuilder().header("User-Agent", AppSettings.DEFAULT_USER_AGENT).build())
                } else chain.proceed(req)
            }
        if (BuildConfig.DEBUG) {
            // Jamais d'URL complète dans les logs : elle contient les identifiants.
            builder.addInterceptor(HttpLoggingInterceptor { msg -> Timber.tag("HTTP").d(redact(msg)) }
                .apply { level = HttpLoggingInterceptor.Level.BASIC })
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    /** Masquage partagé avec l'app PC ([Redact], module :core). */
    fun redact(s: String): String = Redact.text(s)
}
// openStream, HttpException et httpMessage : module :core (HttpStreams.kt), même paquet
