package com.example.lephucmfg.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private val ok = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("http://192.168.1.77:5080/")
        .client(ok)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val ab: AbService = retrofit.create(AbService::class.java)
    val retrofitPublic: Retrofit = retrofit
}
