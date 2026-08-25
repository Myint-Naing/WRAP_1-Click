package com.warp.android.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class RegisterRequest(
    @SerializedName("key") val key: String,
    @SerializedName("install_id") val installId: String = "",
    @SerializedName("warp_enabled") val warpEnabled: Boolean = true,
    @SerializedName("tos") val tos: String,
    @SerializedName("type") val type: String = "Android",
    @SerializedName("locale") val locale: String = "en_US"
)

data class RegisterResponse(
    @SerializedName("id") val id: String?,
    @SerializedName("type") val type: String?,
    @SerializedName("config") val config: WarpConfig?,
    @SerializedName("account") val account: WarpAccount?,
    @SerializedName("created") val created: String?
)

data class WarpConfig(
    @SerializedName("interface") val interfaceInfo: InterfaceInfo?,
    @SerializedName("peers") val peers: List<PeerInfo>?
)

data class InterfaceInfo(
    @SerializedName("addresses") val addresses: Addresses?
)

data class Addresses(
    @SerializedName("v4") val ipv4: String?,
    @SerializedName("v6") val ipv6: String?
)

data class PeerInfo(
    @SerializedName("public_key") val publicKey: String?,
    @SerializedName("endpoint") val endpoint: EndpointInfo?
)

data class EndpointInfo(
    @SerializedName("host") val host: String?,
    @SerializedName("v4") val ipv4: String?,
    @SerializedName("v6") val ipv6: String?
)

data class WarpAccount(
    @SerializedName("id") val id: String?,
    @SerializedName("account_type") val accountType: String?,
    @SerializedName("license") val license: String?,
    @SerializedName("created") val created: String?,
    @SerializedName("updated") val updated: String?,
    @SerializedName("ttl") val ttl: String?
)

data class WarpAccountCredentials(
    val privateKey: String,
    val publicKey: String,
    val peerPublicKey: String,
    val ipv4: String,
    val ipv6: String?,
    val endpointHost: String,
    val licenseKey: String?,
    val expirationTimestampEpochMs: Long
)

interface WarpApiService {
    @POST("v0a737/reg")
    @Headers("Content-Type: application/json", "User-Agent: okhttp/3.12.1")
    suspend fun register(
        @Body request: RegisterRequest
    ): RegisterResponse
}

class WarpApi(private val context: Context) {

    private val apiService: WarpApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        Retrofit.Builder()
            .baseUrl("https://api.cloudflareclient.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WarpApiService::class.java)
    }

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "warp_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    suspend fun registerOrRenewKey(privateKey: String, publicKey: String): WarpAccountCredentials {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val tosDate = sdf.format(Date())

        val request = RegisterRequest(
            key = publicKey,
            tos = tosDate
        )

        val response = apiService.register(request)

        val peerPublicKey = response.config?.peers?.firstOrNull()?.publicKey
            ?: throw IllegalStateException("Missing peer public key in WARP response")

        val ipv4 = response.config.interfaceInfo?.addresses?.ipv4
            ?: throw IllegalStateException("Missing IPv4 address in WARP response")

        val ipv6 = response.config.interfaceInfo.addresses.ipv6
        val endpointHost = response.config.peers.firstOrNull()?.endpoint?.host ?: "engage.cloudflareclient.com:2408"
        val licenseKey = response.account?.license

        val expirationTimestampEpochMs = parseExpirationTimestamp(
            response.account?.ttl,
            response.created
        )

        val credentials = WarpAccountCredentials(
            privateKey = privateKey,
            publicKey = publicKey,
            peerPublicKey = peerPublicKey,
            ipv4 = ipv4,
            ipv6 = ipv6,
            endpointHost = endpointHost,
            licenseKey = licenseKey,
            expirationTimestampEpochMs = expirationTimestampEpochMs
        )

        saveCredentials(credentials)
        return credentials
    }

    fun parseExpirationTimestamp(ttlStr: String?, createdStr: String?): Long {
        val now = System.currentTimeMillis()
        var expEpoch = 0L

        if (!ttlStr.isNullOrEmpty()) {
            expEpoch = parseIsoTimestamp(ttlStr)
        }

        if (expEpoch <= now) {
            val createdEpoch = if (!createdStr.isNullOrEmpty()) parseIsoTimestamp(createdStr) else now
            val validCreatedEpoch = if (createdEpoch > 0) createdEpoch else now
            // Default 30 days validity if TTL is absent or expired
            expEpoch = validCreatedEpoch + (30L * 24 * 60 * 60 * 1000)
        }

        return expEpoch
    }

    private fun parseIsoTimestamp(isoString: String): Long {
        val parsers = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        )

        for (parser in parsers) {
            parser.timeZone = TimeZone.getTimeZone("UTC")
            try {
                val date = parser.parse(isoString)
                if (date != null) return date.time
            } catch (_: Exception) {
            }
        }
        return 0L
    }

    fun saveCredentials(credentials: WarpAccountCredentials) {
        prefs.edit()
            .putString(KEY_PRIVATE_KEY, credentials.privateKey)
            .putString(KEY_PUBLIC_KEY, credentials.publicKey)
            .putString(KEY_PEER_PUBLIC_KEY, credentials.peerPublicKey)
            .putString(KEY_IPV4, credentials.ipv4)
            .putString(KEY_IPV6, credentials.ipv6)
            .putString(KEY_ENDPOINT, credentials.endpointHost)
            .putString(KEY_LICENSE, credentials.licenseKey)
            .putLong(KEY_EXPIRATION, credentials.expirationTimestampEpochMs)
            .apply()
    }

    fun getSavedCredentials(): WarpAccountCredentials? {
        val privateKey = prefs.getString(KEY_PRIVATE_KEY, null) ?: return null
        val publicKey = prefs.getString(KEY_PUBLIC_KEY, null) ?: return null
        val peerPublicKey = prefs.getString(KEY_PEER_PUBLIC_KEY, null) ?: return null
        val ipv4 = prefs.getString(KEY_IPV4, null) ?: return null
        val ipv6 = prefs.getString(KEY_IPV6, null)
        val endpointHost = prefs.getString(KEY_ENDPOINT, "engage.cloudflareclient.com:2408") ?: "engage.cloudflareclient.com:2408"
        val licenseKey = prefs.getString(KEY_LICENSE, null)
        val exp = prefs.getLong(KEY_EXPIRATION, 0L)

        return WarpAccountCredentials(
            privateKey = privateKey,
            publicKey = publicKey,
            peerPublicKey = peerPublicKey,
            ipv4 = ipv4,
            ipv6 = ipv6,
            endpointHost = endpointHost,
            licenseKey = licenseKey,
            expirationTimestampEpochMs = exp
        )
    }

    fun clearSavedCredentials() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_PRIVATE_KEY = "warp_private_key"
        private const val KEY_PUBLIC_KEY = "warp_public_key"
        private const val KEY_PEER_PUBLIC_KEY = "warp_peer_public_key"
        private const val KEY_IPV4 = "warp_ipv4"
        private const val KEY_IPV6 = "warp_ipv6"
        private const val KEY_ENDPOINT = "warp_endpoint"
        private const val KEY_LICENSE = "warp_license"
        private const val KEY_EXPIRATION = "warp_expiration"
    }
}
