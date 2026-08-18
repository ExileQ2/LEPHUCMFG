package com.example.lephucmfg.network

import com.example.lephucmfg.data.AndroidReleaseDto
import com.example.lephucmfg.data.AndroidVersionDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

interface UpdateService {
    @GET("api/android/releases/latest")
    suspend fun latestRelease(
        @Query("currentVersionCode") currentVersionCode: Int
    ): Response<AndroidReleaseDto>

    @Streaming
    @GET
    suspend fun downloadRelease(@Url downloadUrl: String): Response<ResponseBody>

    // Kept so old APKs can update through the same backend during migration.
    @GET("api/android/version")
    suspend fun checkVersion(): Response<AndroidVersionDto>
}
