package de.autosugar.data.network

import de.autosugar.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NightscoutApiFactory @Inject constructor() {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    /** Cache of one Retrofit-backed API instance per normalized base URL. */
    private val cache = mutableMapOf<String, NightscoutApi>()

    fun get(baseUrl: String): NightscoutApi {
        val normalized = baseUrl.trimEnd('/') + "/"
        return cache.getOrPut(normalized) { buildApi(normalized) }
    }

    fun invalidate(baseUrl: String) {
        cache.remove(baseUrl.trimEnd('/') + "/")
    }

    private fun buildApi(baseUrl: String): NightscoutApi {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.ENABLE_HTTP_LOGGING) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(NightscoutApi::class.java)
    }
}
