package com.autosugar.data.network

import com.autosugar.data.network.dto.EntryDto
import com.autosugar.data.network.dto.StatusDto
import retrofit2.http.GET
import retrofit2.http.Query

interface NightscoutApi {
    @GET("api/v1/entries/current.json")
    suspend fun getCurrentEntry(
        @Query("token") token: String? = null,
        @Query("count") count: Int = 2,
    ): List<EntryDto>

    @GET("api/v1/entries.json")
    suspend fun getEntries(
        @Query("token") token: String? = null,
        @Query("count") count: Int = 24,
    ): List<EntryDto>

    @GET("api/v1/status.json")
    suspend fun getStatus(
        @Query("token") token: String? = null,
    ): StatusDto
}
