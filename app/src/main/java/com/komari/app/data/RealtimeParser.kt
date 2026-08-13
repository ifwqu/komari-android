package com.komari.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject

/** 实时快照：在线列表 + 每节点最新上报 */
data class RealtimeSnapshot(
    val online: List<String>,
    val reports: Map<String, Report>
)

/** 宽松解析 /api/clients WebSocket 消息：任何单点结构异常都不会让整个解析失败 */
fun parseSnapshot(text: String): RealtimeSnapshot? = runCatching {
    val root = Json.parseToJsonElement(text).jsonObject
    if (root["status"]?.jsonPrimitiveContent() != "success") return@runCatching null
    val dataObj = root["data"]?.jsonObjectOrNull() ?: return@runCatching null
    val online = (dataObj["online"] as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        ?: emptyList()
    val reports = LinkedHashMap<String, Report>()
    val reportsObj = dataObj["data"]?.jsonObjectOrNull() ?: JsonObject(emptyMap())
    for ((uuid, el) in reportsObj) {
        val o = el.jsonObjectOrNull() ?: continue
        reports[uuid] = parseReportGeneric(o)
    }
    RealtimeSnapshot(online, reports)
}.getOrNull()

/** 从任意结构的 JSON 对象中安全提取 Report（容忍字段缺失 / 类型不一致） */
fun parseReportGeneric(o: JsonObject): Report {
    val cpu = o["cpu"]?.jsonObjectOrNull()
    val ram = o["ram"]?.jsonObjectOrNull()
    val swap = o["swap"]?.jsonObjectOrNull()
    val load = o["load"]?.jsonObjectOrNull()
    val disk = o["disk"]?.jsonObjectOrNull()
    val network = o["network"]?.jsonObjectOrNull()
    val conn = o["connections"]?.jsonObjectOrNull()
    return Report(
        cpu = CpuReport(
            name = cpu?.str("name"),
            cores = cpu?.num("cores")?.toInt(),
            arch = cpu?.str("arch"),
            usage = cpu?.num("usage") ?: 0.0
        ),
        ram = RamReport(
            total = ram?.num("total")?.toLong() ?: 0L,
            used = ram?.num("used")?.toLong() ?: 0L
        ),
        swap = swap?.let {
            RamReport(total = it.num("total")?.toLong() ?: 0L, used = it.num("used")?.toLong() ?: 0L)
        },
        load = load?.let {
            LoadReport(
                load1 = it.num("load1") ?: 0.0,
                load5 = it.num("load5") ?: 0.0,
                load15 = it.num("load15") ?: 0.0
            )
        },
        disk = DiskReport(
            total = disk?.num("total")?.toLong() ?: 0L,
            used = disk?.num("used")?.toLong() ?: 0L
        ),
        network = network?.let {
            NetworkReport(
                up = it.num("up")?.toLong() ?: 0L,
                down = it.num("down")?.toLong() ?: 0L,
                totalUp = it.num("totalUp")?.toLong() ?: 0L,
                totalDown = it.num("totalDown")?.toLong() ?: 0L
            )
        },
        connections = conn?.let {
            ConnectionsReport(tcp = it.num("tcp")?.toInt() ?: 0, udp = it.num("udp")?.toInt() ?: 0)
        },
        uptime = o.num("uptime")?.toLong() ?: 0L,
        process = o.num("process")?.toInt() ?: 0,
        updatedAt = o.str("updated_at")
    )
}

private fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject

private fun JsonElement.jsonPrimitiveContent(): String? =
    (this as? JsonPrimitive)?.contentOrNull

/** 字符串字段：字符串 / 数字统一转字符串 */
private fun JsonObject.str(k: String): String? = get(k)?.let { el ->
    if (el is JsonNull) null else (el as? JsonPrimitive)?.contentOrNull ?: el.toString()
}

/** 数字字段：容忍 "123"、123.0 等字符串数字 */
private fun JsonObject.num(k: String): Double? = when (val v = get(k)) {
    is JsonPrimitive -> v.doubleOrNull ?: v.contentOrNull?.toDoubleOrNull()
    else -> null
}