package com.komari.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** komari REST 统一响应封装 {status, message, data} */
@Serializable
data class ApiEnvelope<T>(
    val status: String = "",
    val message: String? = null,
    val data: T? = null
)

/** GET /api/me 扁平响应 */
@Serializable
data class MeResponse(
    val username: String = "",
    @SerialName("logged_in") val loggedIn: Boolean = false,
    val uuid: String? = null,
    @SerialName("2fa_enabled") val twoFaEnabled: Boolean? = null
)

/** GET /api/nodes 节点基础信息 */
@Serializable
data class ClientInfo(
    val uuid: String = "",
    val name: String = "",
    @SerialName("cpu_name") val cpuName: String? = null,
    val virtualization: String? = null,
    val arch: String? = null,
    @SerialName("cpu_cores") val cpuCores: Int? = null,
    val os: String? = null,
    @SerialName("kernel_version") val kernelVersion: String? = null,
    @SerialName("gpu_name") val gpuName: String? = null,
    val ipv4: String? = null,
    val ipv6: String? = null,
    val region: String? = null,
    @SerialName("public_remark") val publicRemark: String? = null,
    @SerialName("mem_total") val memTotal: Long? = null,
    @SerialName("swap_total") val swapTotal: Long? = null,
    @SerialName("disk_total") val diskTotal: Long? = null,
    val weight: Int = 0,
    val hidden: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)

/** POST /api/login 响应 data 部分 */
@Serializable
data class LoginEnvelopeData(
    @SerialName("set-cookie") val setCookie: SetCookie? = null
)

@Serializable
data class SetCookie(
    @SerialName("session_token") val sessionToken: String? = null
)

/* ---------------- WebSocket 实时数据 (/api/clients) ---------------- */

@Serializable
data class WsEnvelope(
    val status: String = "",
    val data: WsData? = null,
    val error: String? = null
)

@Serializable
data class WsData(
    val online: List<String> = emptyList(),
    val data: Map<String, Report> = emptyMap()
)

@Serializable
data class Report(
    val uuid: String? = null,
    val cpu: CpuReport = CpuReport(),
    val ram: RamReport = RamReport(),
    val swap: RamReport? = null,
    val load: LoadReport? = null,
    val disk: DiskReport = DiskReport(),
    val network: NetworkReport? = null,
    val connections: ConnectionsReport? = null,
    val gpu: GpuDetailReport? = null,
    val uptime: Long = 0,
    val process: Int = 0,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class CpuReport(
    val name: String? = null,
    val cores: Int? = null,
    val arch: String? = null,
    val usage: Double = 0.0
)

@Serializable
data class RamReport(val total: Long = 0, val used: Long = 0)

@Serializable
data class LoadReport(
    val load1: Double = 0.0,
    val load5: Double = 0.0,
    val load15: Double = 0.0
)

@Serializable
data class DiskReport(val total: Long = 0, val used: Long = 0)

@Serializable
data class NetworkReport(
    val up: Long = 0,
    val down: Long = 0,
    @SerialName("totalUp") val totalUp: Long = 0,
    @SerialName("totalDown") val totalDown: Long = 0
)

@Serializable
data class ConnectionsReport(val tcp: Int = 0, val udp: Int = 0)

@Serializable
data class GpuDetailReport(
    val count: Int? = null,
    @SerialName("average_usage") val averageUsage: Double? = null,
    @SerialName("detailed_info") val detailedInfo: List<GpuDeviceInfo> = emptyList()
)

@Serializable
data class GpuDeviceInfo(
    val name: String? = null,
    @SerialName("memory_total") val memoryTotal: Long = 0,
    @SerialName("memory_used") val memoryUsed: Long = 0,
    val utilization: Double? = null,
    val temperature: Int? = null
)

/* ---------------- 历史记录 (/api/records/load) ---------------- */

@Serializable
data class RecordsResponse(
    val records: List<RecordPoint> = emptyList(),
    val count: Int = 0,
    @SerialName("load_type") val loadType: String? = null,
    @SerialName("has_gpu_data") val hasGpuData: Boolean? = null
)

@Serializable
data class RecordPoint(
    val client: String? = null,
    val time: String? = null,
    val cpu: Double? = null,
    val gpu: Double? = null,
    val ram: Long? = null,
    @SerialName("ram_total") val ramTotal: Long? = null,
    @SerialName("ram_percent") val ramPercent: Double? = null,
    val swap: Long? = null,
    @SerialName("swap_total") val swapTotal: Long? = null,
    val load: Double? = null,
    val temp: Double? = null,
    val disk: Long? = null,
    @SerialName("disk_total") val diskTotal: Long? = null,
    @SerialName("disk_percent") val diskPercent: Double? = null,
    @SerialName("net_in") val netIn: Long? = null,
    @SerialName("net_out") val netOut: Long? = null,
    @SerialName("net_total_up") val netTotalUp: Long? = null,
    @SerialName("net_total_down") val netTotalDown: Long? = null,
    val process: Int? = null,
    val connections: Int? = null,
    @SerialName("connections_udp") val connectionsUdp: Int? = null
)

/** 本地保存的服务器配置 */
@Serializable
data class StoredServer(
    val id: String,
    val host: String,
    val username: String,
    val password: String,
    val sessionToken: String? = null
)