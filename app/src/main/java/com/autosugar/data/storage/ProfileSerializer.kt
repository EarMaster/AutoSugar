package com.autosugar.data.storage

import com.autosugar.data.model.NightscoutProfile
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import javax.inject.Inject
import javax.inject.Singleton

/** Moshi-backed JSON serializer for the list of saved profiles. */
@Singleton
class ProfileSerializer @Inject constructor() {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val type = Types.newParameterizedType(List::class.java, NightscoutProfileJson::class.java)
    private val adapter = moshi.adapter<List<NightscoutProfileJson>>(type)

    fun toJson(profiles: List<NightscoutProfile>): String =
        adapter.toJson(profiles.map { it.toJson() })

    fun fromJson(json: String): List<NightscoutProfile> =
        adapter.fromJson(json)?.map { it.toModel() } ?: emptyList()
}

// Separate JSON DTO to avoid coupling the domain model to Moshi annotations.
@JsonClass(generateAdapter = true)
internal data class NightscoutProfileJson(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val apiToken: String,
    val unit: String,
    val icon: String = com.autosugar.data.model.ProfileIcon.PERSON.name,
)

private fun NightscoutProfile.toJson() = NightscoutProfileJson(
    id = id,
    displayName = displayName,
    baseUrl = baseUrl,
    apiToken = apiToken,
    unit = unit.name,
    icon = icon.name,
)

private fun NightscoutProfileJson.toModel() = NightscoutProfile(
    id = id,
    displayName = displayName,
    baseUrl = baseUrl,
    apiToken = apiToken,
    unit = com.autosugar.data.model.GlucoseUnit.valueOf(unit),
    icon = runCatching { com.autosugar.data.model.ProfileIcon.valueOf(icon) }
        .getOrDefault(com.autosugar.data.model.ProfileIcon.PERSON),
)
