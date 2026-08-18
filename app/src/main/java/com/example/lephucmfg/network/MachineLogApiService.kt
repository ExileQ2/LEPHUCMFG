package com.example.lephucmfg.network

import com.example.lephucmfg.data.machinelog.MachineInfoDto
import com.example.lephucmfg.data.machinelog.JigWorkInfoDto
import com.example.lephucmfg.data.machinelog.MachineLogRequest
import com.example.lephucmfg.data.machinelog.ProcessInfoDto
import com.example.lephucmfg.data.machinelog.ProductionOrderDto
import com.example.lephucmfg.data.machinelog.RoutingStepDto
import com.example.lephucmfg.data.machinelog.SerialDto
import com.example.lephucmfg.data.machinelog.SerialListDto
import com.example.lephucmfg.data.machinelog.StaffInfoDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface MachineLogApiService {
    @GET("api/GetStaff/{staffNo}")
    suspend fun getStaff(@Path("staffNo") staffNo: Int): StaffInfoDto

    @GET("api/GetMachine/{machine}")
    suspend fun getMachine(@Path("machine") machine: String): MachineInfoDto

    @GET("api/GetProcessNoChuaKetThuc/{staffNo}/{machine}")
    suspend fun getActiveProcess(
        @Path("staffNo") staffNo: String,
        @Path("machine") machine: String
    ): ProcessInfoDto

    @GET("api/Laylsx/{job}")
    suspend fun getProductionOrders(@Path("job") job: String): List<ProductionOrderDto>

    @GET("api/GetSerial/{productionOrder}")
    suspend fun getSerials(@Path("productionOrder") productionOrder: String): List<SerialDto>

    @GET("api/GetSerialListByJobControlNo/{job}")
    suspend fun getUsedSerials(@Path("job") job: String): SerialListDto

    @GET("api/GetInfoRouting/{job}")
    suspend fun getRouting(@Path("job") job: String): List<RoutingStepDto>

    @GET("api/JigWork/{job}")
    suspend fun getJigWork(@Path("job") job: String): JigWorkInfoDto

    @POST("api/postNhatKyGiaCong")
    suspend fun submit(@Body request: MachineLogRequest): Response<ResponseBody>
}
