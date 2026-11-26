package io.github.seven317.openstandby.data.lyrics

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

internal interface LrclibApi {
    @GET("api/get")
    suspend fun getLyrics(
        @Header("User-Agent") userAgent: String,
        @Query("track_name") track: String,
        @Query("artist_name") artist: String,
        @Query("album_name") album: String?,
        @Query("duration") durationSeconds: Int?,
    ): Response<LrclibTrack>

    @GET("api/search")
    suspend fun searchLyrics(
        @Header("User-Agent") userAgent: String,
        @Query("track_name") track: String,
        @Query("artist_name") artist: String,
    ): Response<List<LrclibTrack>>
}

internal data class LrclibTrack(
    @SerializedName("syncedLyrics") val synced: String?,
    @SerializedName("plainLyrics") val plain: String?,
    @SerializedName("trackName") val title: String?,
    @SerializedName("artistName") val artist: String?,
    @SerializedName("albumName") val album: String?,
    @SerializedName("duration") val duration: Double?,
    @SerializedName("instrumental") val instrumental: Boolean,
)
