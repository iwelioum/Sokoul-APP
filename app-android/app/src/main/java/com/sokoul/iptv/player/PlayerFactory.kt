package com.sokoul.iptv.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import com.sokoul.iptv.data.AppSettings
import com.sokoul.iptv.data.BufferProfile
import com.sokoul.iptv.data.DecoderMode
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Crée des ExoPlayer réglés pour l'IPTV :
 * - démarrage rapide (1 s de tampon) pour un zapping quasi instantané ;
 * - décodeur FFmpeg en renfort pour AC3/E-AC3/DTS/TrueHD, absents de beaucoup de box ;
 * - extracteur TS tolérant (flux sans IDR, PTS désordonnés) ;
 * - réseau partagé avec OkHttp (connexions réutilisées entre chaînes).
 */
@Singleton
class PlayerFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttp: OkHttpClient,
) {
    fun create(settings: AppSettings, forMultiview: Boolean = false): ExoPlayer {
        val renderers = DefaultRenderersFactory(context)
            .setExtensionRendererMode(
                when (settings.decoderMode) {
                    DecoderMode.HARDWARE -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
                    DecoderMode.AUTO -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                    DecoderMode.SOFTWARE -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                }
            )
            .setEnableDecoderFallback(true)

        val (minMs, maxMs, startMs, rebufferMs) = when (settings.bufferProfile) {
            BufferProfile.LOW -> listOf(8_000, 30_000, 800, 2_000)
            BufferProfile.NORMAL -> listOf(15_000, 50_000, 1_200, 3_000)
            BufferProfile.HIGH -> listOf(30_000, 120_000, 2_500, 6_000)
        }
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(minMs, maxMs, startMs, rebufferMs)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val params = TrackSelectionParameters.Builder(context)
            .setPreferredAudioLanguages(*listOf(settings.audioLanguage, "fra", "fre").filter { it.isNotBlank() }.toTypedArray())
            .apply {
                if (settings.subtitleLanguage.isNotBlank()) setPreferredTextLanguage(settings.subtitleLanguage)
                // multi-écran : on limite la résolution pour ne pas saturer le décodeur
                if (forMultiview) setMaxVideoSize(1280, 720)
            }
            .build()

        return ExoPlayer.Builder(context, renderers)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory(settings.userAgent, null))
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), !forMultiview)
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .build()
            .apply {
                trackSelectionParameters = params
                playWhenReady = true
            }
    }

    fun mediaSourceFactory(userAgent: String, referrer: String?): MediaSource.Factory {
        val http = OkHttpDataSource.Factory(okHttp)
            .setUserAgent(userAgent)
            .apply { if (!referrer.isNullOrBlank()) setDefaultRequestProperties(mapOf("Referer" to referrer)) }
        val upstream = DefaultDataSource.Factory(context, http)
        val extractors = DefaultExtractorsFactory()
            .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS)
            .setConstantBitrateSeekingEnabled(true)
        return DefaultMediaSourceFactory(upstream, extractors)
            .setLoadErrorHandlingPolicy(IptvLoadErrorPolicy())
    }

    /**
     * Ne réessaie pas en boucle un flux refusé par le serveur (401/403/404/406/429…) :
     * insister sur un refus peut faire bannir temporairement l'abonnement par le fournisseur.
     * Les coupures réseau restent réessayées normalement.
     */
    private class IptvLoadErrorPolicy : DefaultLoadErrorHandlingPolicy(6) {
        override fun getRetryDelayMsFor(info: androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo): Long {
            val e = info.exception
            if (e is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException && e.responseCode in 400..499) {
                return androidx.media3.common.C.TIME_UNSET
            }
            return super.getRetryDelayMsFor(info)
        }
    }

    /** Construit l'élément à lire ; `forceHls` sert de repli pour les URL sans extension. */
    fun mediaItem(url: String, title: String, forceHls: Boolean = false, live: Boolean): MediaItem {
        val builder = MediaItem.Builder().setUri(Uri.parse(url)).setMediaId(url)
            .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(title).build())
        val path = url.substringBefore('?').lowercase()
        when {
            forceHls || path.endsWith(".m3u8") || path.endsWith(".m3u") -> builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            path.endsWith(".mpd") -> builder.setMimeType(MimeTypes.APPLICATION_MPD)
            path.endsWith(".ts") -> builder.setMimeType(MimeTypes.VIDEO_MP2T)
        }
        if (live) {
            builder.setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(8_000).setMinPlaybackSpeed(0.97f).setMaxPlaybackSpeed(1.03f).build()
            )
        }
        return builder.build()
    }
}
