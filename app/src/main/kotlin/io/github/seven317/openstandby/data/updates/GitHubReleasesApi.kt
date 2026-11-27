package io.github.seven317.openstandby.data.updates

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers

internal const val GITHUB_REPOSITORY = "seven-317/OpenStandBy"

internal interface GitHubReleasesApi {
    @Headers("Accept: application/vnd.github+json", "User-Agent: OpenStandBy")
    @GET("repos/$GITHUB_REPOSITORY/releases/latest")
    suspend fun latestRelease(): Response<GitHubRelease>
}

internal data class GitHubRelease(@SerializedName("tag_name") val tag: String?)
