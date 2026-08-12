package com.komari.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * Komari 服务器 API 客户端。
 * 鉴权：登录后持有 session_token，通过 Cookie 头携带。
 */
class KomariApi(val server: StoredServer) {

    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val baseUrl: String get() = server.host.trimEnd('/')

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private fun Request.Builder.auth(): Request.Builder = apply {
        val token = server.sessionToken
        if (!token.isNullOrBlank()) addHeader("Cookie", "session_token=$token")
    }

    private fun buildUrl(path: String, query: Map<String, String> = emptyMap()): String {
        val sb = StringBuilder(baseUrl).append(path)
        if (query.isNotEmpty()) {
            sb.append('?')
            query.forEach { (k, v) ->
                if (sb.last() != '?') sb.append('&')
                sb.append(k).append('=').append(java.net.URLEncoder.encode(v, "UTF-8"))
            }
        }
        return sb.toString()
    }

    /** 执行请求并在 IO 线程解析 */
    private suspend fun <T> execute(request: Request, parse: (String, Response) -> T): Result<T> =
        withContext(Dispatchers.IO) {
            runCatching {
                httpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        throw Exception("HTTP ${resp.code} ${resp.body?.string()?.take(200)}")
                    }
                    parse(resp.body?.string().orEmpty(), resp)
                }
            }
        }

    /** 登录，成功返回 session_token */
    suspend fun login(username: String, password: String, twoFaCode: String?): Result<String> {
        val body = buildJsonObject {
            put("username", username)
            put("password", password)
            if (!twoFaCode.isNullOrBlank()) put("2fa_code", twoFaCode)
        }
        val req = Request.Builder()
            .url("$baseUrl/api/login")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return execute(req) { text, resp ->
            val envelope = json.decodeFromString<ApiEnvelope<LoginEnvelopeData>>(text)
            val token = envelope.data?.setCookie?.sessionToken
                ?: sessionFromSetCookieHeader(resp)
            if (envelope.status == "success" && token != null) token
            else throw Exception(envelope.message ?: "登录失败（HTTP ${resp.code}）")
        }
    }

    /** 校验会话是否有效 */
    suspend fun me(): Result<MeResponse> {
        val req = Request.Builder().url(buildUrl("/api/me")).auth().get().build()
        return execute(req) { text, _ -> json.decodeFromString<MeResponse>(text) }
    }

    /** 节点列表 */
    suspend fun nodes(): Result<List<ClientInfo>> {
        val req = Request.Builder().url(buildUrl("/api/nodes")).auth().get().build()
        return execute(req) { text, _ ->
            val env = json.decodeFromString<ApiEnvelope<List<ClientInfo>>>(text)
            if (env.status == "success") env.data ?: emptyList()
            else throw Exception(env.message ?: "获取节点失败")
        }
    }

    /** 历史记录：loadType=cpu/ram/network/disk/load/temp/process/connections/all */
    suspend fun records(uuid: String, loadType: String, hours: Int): Result<RecordsResponse> {
        val req = Request.Builder()
            .url(buildUrl("/api/records/load", mapOf(
                "uuid" to uuid,
                "load_type" to loadType,
                "hours" to hours.toString()
            )))
            .auth().get().build()
        return execute(req) { text, _ ->
            val env = json.decodeFromString<ApiEnvelope<RecordsResponse>>(text)
            if (env.status == "success") env.data ?: RecordsResponse()
            else throw Exception(env.message ?: "获取记录失败")
        }
    }

    /**
     * 建立实时 WebSocket（/api/clients），客户端发送 "get" 拉取全量实时数据。
     * 说明：komari 校验 WebSocket Origin 必须与服务器 Host 一致，因此显式携带 Origin 头。
     */
    fun connectWs(listener: WebSocketListener): WebSocket {
        val wsUrl = baseUrl.replaceFirst("http", "ws") + "/api/clients"
        val req = Request.Builder()
            .url(wsUrl)
            .auth()
            .addHeader("Origin", baseUrl)
            .build()
        return httpClient.newWebSocket(req, listener)
    }

    private fun sessionFromSetCookieHeader(resp: Response): String? =
        resp.headers("Set-Cookie").firstOrNull { it.startsWith("session_token=") }
            ?.substringBefore(';')
            ?.substringAfter("=")
            ?.trim()
}