package com.komari.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
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

    /* ---------------- 管理端 ---------------- */

    private fun jsonGet(path: String) = Request.Builder().url(buildUrl(path)).auth().get()

    private fun jsonPost(path: String, body: String) =
        Request.Builder().url(buildUrl(path)).auth().post(body.toRequestBody("application/json".toMediaType()))

    /** 管理端：客户端完整列表（WithRaw，直接数组） */
    suspend fun adminClients(): Result<List<ClientInfo>> {
        val req = jsonGet("/api/admin/client/list").build()
        return execute(req) { text, _ -> json.decodeFromString<List<ClientInfo>>(text) }
    }

    /** 管理端：添加客户端，可选名称 */
    suspend fun adminAddClient(name: String?): Result<FlatAddResult> {
        val body = if (name.isNullOrBlank()) "{}" else buildJsonObject { put("name", name) }.toString()
        val req = jsonPost("/api/admin/client/add", body).build()
        return execute(req) { text, resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            val r = json.decodeFromString<FlatAddResult>(text)
            if (r.status == "success" && !r.uuid.isNullOrBlank()) r
            else throw Exception("添加失败")
        }
    }

    /** 管理端：删除客户端 */
    suspend fun adminRemoveClient(uuid: String): Result<Unit> {
        val req = jsonPost("/api/admin/client/$uuid/remove", "{}").build()
        return execute(req) { text, _ ->
            val env = json.decodeFromString<ApiEnvelope<JsonElement>>(text)
            if (env.status != "success") throw Exception(env.message ?: "删除失败")
        }
    }

    /** 管理端：获取客户端令牌（用于部署 agent） */
    suspend fun adminClientToken(uuid: String): Result<String> {
        val req = jsonGet("/api/admin/client/$uuid/token").build()
        return execute(req) { text, _ ->
            val r = json.decodeFromString<FlatTokenResult>(text)
            if (r.status == "success" && r.token != null) r.token
            else throw Exception("获取令牌失败")
        }
    }

    /** 管理端：主题列表 */
    suspend fun themes(): Result<List<ThemeInfo>> {
        val req = jsonGet("/api/admin/theme/list").build()
        return execute(req) { text, _ ->
            val env = json.decodeFromString<ApiEnvelope<List<ThemeInfo>>>(text)
            if (env.status == "success") env.data ?: emptyList()
            else throw Exception(env.message ?: "获取主题失败")
        }
    }

    /** 管理端：应用主题（short） */
    suspend fun setTheme(short: String): Result<Unit> {
        val req = jsonGet("/api/admin/theme/set?theme=${java.net.URLEncoder.encode(short, "UTF-8")}").build()
        return execute(req) { text, _ ->
            val env = json.decodeFromString<ApiEnvelope<JsonObject>>(text)
            if (env.status != "success") throw Exception(env.message ?: "应用主题失败")
        }
    }

    /** 管理端：插件列表（结构松散的 JSON 数组） */
    suspend fun plugins(): Result<List<JsonObject>> {
        val req = jsonGet("/api/admin/plugin/list").build()
        return execute(req) { text, _ ->
            val env = json.decodeFromString<ApiEnvelope<List<JsonObject>>>(text)
            if (env.status == "success") env.data ?: emptyList()
            else throw Exception(env.message ?: "获取插件失败")
        }
    }

    /** 管理端：启用/禁用插件 */
    suspend fun setPluginEnabled(short: String, enabled: Boolean): Result<Unit> {
        val body = buildJsonObject { put("short", short); put("enabled", enabled) }.toString()
        val req = jsonPost("/api/admin/plugin/enabled", body).build()
        return execute(req) { text, _ ->
            val env = json.decodeFromString<ApiEnvelope<JsonElement>>(text)
            if (env.status != "success") throw Exception(env.message ?: "操作失败")
        }
    }

    /** 管理端：离线通知列表 */
    suspend fun offlineNotifications(): Result<List<OfflineNotification>> {
        val req = jsonGet("/api/admin/notification/offline").build()
        return execute(req) { text, _ ->
            val env = json.decodeFromString<ApiEnvelope<List<OfflineNotification>>>(text)
            if (env.status == "success") env.data ?: emptyList()
            else throw Exception(env.message ?: "获取通知设置失败")
        }
    }

    /** 管理端：启用/禁用指定客户端的离线通知（参数为 uuid 数组） */
    suspend fun setOfflineNotification(uuids: List<String>, enable: Boolean): Result<Unit> {
        val path = if (enable) "/api/admin/notification/offline/enable" else "/api/admin/notification/offline/disable"
        val body = json.encodeToString(uuids)
        val req = jsonPost(path, body).build()
        return execute(req) { text, _ ->
            val env = json.decodeFromString<ApiEnvelope<JsonElement>>(text)
            if (env.status != "success") throw Exception(env.message ?: "操作失败")
        }
    }

    /** 管理端：站点设置（只读展示） */
    suspend fun adminSettings(): Result<JsonObject> {
        val req = jsonGet("/api/admin/settings/").build()
        return execute(req) { text, _ ->
            val env = json.decodeFromString<ApiEnvelope<JsonObject>>(text)
            if (env.status == "success") env.data ?: JsonObject(emptyMap())
            else throw Exception(env.message ?: "获取设置失败")
        }
    }

    private fun sessionFromSetCookieHeader(resp: Response): String? =
        resp.headers("Set-Cookie").firstOrNull { it.startsWith("session_token=") }
            ?.substringBefore(';')
            ?.substringAfter("=")
            ?.trim()
}