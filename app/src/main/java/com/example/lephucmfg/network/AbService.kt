package com.example.lephucmfg.network

import com.example.lephucmfg.data.AbInsertDto
import com.example.lephucmfg.data.AbRow
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AbService {
    @POST("api/Ab")
    suspend fun postAb(@Body dto: AbInsertDto): Response<Unit>

    @GET("api/Ab")
    suspend fun getAll(): Response<List<AbRow>>
}
