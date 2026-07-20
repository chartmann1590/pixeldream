package com.hartmann.pixeldream.feedback

import com.hartmann.pixeldream.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit

/**
 * Retrofit definition of the GitHub REST endpoints the in-app feedback reporter
 * needs. Owner/repo are filled in at runtime from BuildConfig so the same
 * interface works across any project.
 */
interface GithubApi {
    @POST("repos/{owner}/{repo}/issues")
    suspend fun createIssue(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: CreateIssueRequest,
    ): GithubIssue

    @GET("repos/{owner}/{repo}/issues/{number}")
    suspend fun getIssue(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("number") number: Int,
    ): GithubIssue

    @GET("repos/{owner}/{repo}/issues/{number}/comments")
    suspend fun listComments(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("number") number: Int,
    ): List<GithubComment>

    @POST("repos/{owner}/{repo}/issues/{number}/comments")
    suspend fun postComment(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("number") number: Int,
        @Body body: PostCommentRequest,
    ): GithubComment

    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun uploadAsset(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Body body: UploadAssetRequest,
        @Header("Accept") accept: String = "application/vnd.github+json",
    ): UploadAssetResponse
}

object GithubClient {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    /**
     * The app only knows the Worker URL and repository coordinates. The GitHub
     * token remains an encrypted Worker secret and is never packaged in the APK.
     */
    val isConfigured: Boolean
        get() = BuildConfig.FEEDBACK_PROXY_URL.isNotBlank() &&
            BuildConfig.GITHUB_REPO_OWNER.isNotBlank() &&
            BuildConfig.GITHUB_REPO_NAME.isNotBlank()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.HEADERS
        redactHeader("Authorization")
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .writeTimeout(40, TimeUnit.SECONDS)
            .build()
    }

    val api: GithubApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.FEEDBACK_PROXY_URL.let { if (it.endsWith('/')) it else "$it/" })
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GithubApi::class.java)
    }
}
