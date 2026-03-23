package com.autosugar.data.network

import com.autosugar.data.network.dto.EntryDto
import retrofit2.http.GET
import retrofit2.http.Query

interface NightscoutApi {
    @GET("api/v1/entries/current.json")
    suspend fun getCurrentEntry(
        @Query("token") token: String? = null,
        @Query("count") count: Int = 2,
    ): List<EntryDto>
}
