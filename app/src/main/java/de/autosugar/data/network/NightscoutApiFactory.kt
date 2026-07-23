package de.autosugar.data.network

import de.autosugar.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NightscoutApiFactory @Inject constructor() {

    private companion object {
        /** Matches the `token=` query parameter value in a logged request line. */
        val TOKEN_QUERY = Regex("""token=[^&\s]+""")
    }

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    /** Cache of one Retrofit-backed API instance per normalized base URL. */
    private val cache = ConcurrentHashMap<String, NightscoutApi>()

    fun get(baseUrl: String): NightscoutApi {
        val normalized = baseUrl.trimEnd('/') + "/"
        return cache.computeIfAbsent(normalized) { buildApi(it) }
    }

    fun invalidate(baseUrl: String) {
        cache.remove(baseUrl.trimEnd('/') + "/")
    }

    private fun buildApi(baseUrl: String): NightscoutApi {
        // The API token is passed as a `?token=` query parameter, so it appears in the
        // request line that BODY-level logging prints. Redact it so it never reaches
        // logcat or a captured bug report even when HTTP logging is enabled in debug.
        val logger = HttpLoggingInterceptor.Logger { message ->
            HttpLoggingInterceptor.Logger.DEFAULT.log(TOKEN_QUERY.replace(message, "token=REDACTED"))
        }
        val loggingInterceptor = HttpLoggingInterceptor(logger).apply {
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
