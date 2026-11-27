package io.github.seven317.openstandby.data.lyrics

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

internal interface KugeciLyricsApi {
    @GET("search")
    suspend fun search(@Header("User-Agent") userAgent: String, @Query("q") title: String): Response<ResponseBody>

    @GET("song/{id}")
    suspend fun song(@Header("User-Agent") userAgent: String, @Path("id") id: String): Response<ResponseBody>
}
