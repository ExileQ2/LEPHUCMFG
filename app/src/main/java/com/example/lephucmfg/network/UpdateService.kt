package com.example.lephucmfg.network

import com.example.lephucmfg.data.AndroidVersionDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET

interface UpdateService {
    @GET("api/android/version")
    suspend fun checkVersion(): Response<AndroidVersionDto>

    @GET("api/android/download")
    suspend fun downloadApk(): Response<ResponseBody>
}
