package io.github.seven317.openstandby.data.lyrics

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

internal interface NeteaseLyricsApi {
    @GET("cloudsearch?type=1&limit=15")
    suspend fun searchSongs(@Query("keywords") keywords: String): Response<NeteaseSearchResponse>

    @GET("lyric/new")
    suspend fun detailedLyrics(@Query("id") songId: Long): Response<NeteaseLyricResponse>

    @GET("lyric")
    suspend fun lyrics(@Query("id") songId: Long): Response<NeteaseLyricResponse>
}

internal data class NeteaseSearchResponse(val code: Int, val result: NeteaseSearchResult?)
internal data class NeteaseSearchResult(val songs: List<NeteaseSong?>?)

internal data class NeteaseSong(
    val id: Long?,
    val name: String?,
    @SerializedName("ar") val artists: List<NeteaseName?>?,
    @SerializedName("al") val album: NeteaseName?,
    @SerializedName("dt") val durationMs: Long?,
)

internal data class NeteaseName(val name: String?)

internal data class NeteaseLyricResponse(val code: Int, val yrc: NeteaseLyricText?, val lrc: NeteaseLyricText?)
internal data class NeteaseLyricText(val lyric: String?)
