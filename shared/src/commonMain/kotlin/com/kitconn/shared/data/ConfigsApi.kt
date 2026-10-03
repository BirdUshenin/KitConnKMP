package com.kitconn.shared.data

import com.kitconn.shared.model.VpnConfig
import com.kitconn.shared.model.VpnConfigsResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class UpdateRequiredException : Exception("Версия приложения устарела")
class HttpStatusException(val code: Int) : Exception("Сервер ответил кодом $code")

class ConfigsApi(
    private val baseUrl: String,
    private val token: String,
    private val appVersion: Int,
    private val client: HttpClient = defaultClient(),
) {
    suspend fun fetchConfigs(): List<VpnConfig> {
        val response = client.get("${baseUrl.trimEnd('/')}/api/v1/configs") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header("X-App-Version", appVersion.toString())
        }
        // 426 — сервер просит обновить приложение
        if (response.status.value == 426) throw UpdateRequiredException()
        if (!response.status.isSuccess()) throw HttpStatusException(response.status.value)
        return response.body<VpnConfigsResponse>().configs
    }

    private companion object {
        fun defaultClient() = HttpClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(HttpTimeout) { requestTimeoutMillis = 15_000 }
        }
    }
}
