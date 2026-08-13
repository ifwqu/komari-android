package com.komari.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.komari.app.data.ClientInfo
import com.komari.app.data.KomariApi
import com.komari.app.data.RecordsResponse
import com.komari.app.data.Report
import com.komari.app.data.ServerStore
import com.komari.app.data.WsEnvelope
import com.komari.app.data.formatBytes
import com.komari.app.data.formatSpeed
import com.komari.app.data.formatUptime
import com.komari.app.data.percentOf
import com.komari.app.ui.theme.KomariBlue
import com.komari.app.ui.theme.KomariGreen
import com.komari.app.ui.theme.KomariPurple
import com.komari.app.ui.theme.KomariRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.Locale

private val METRICS = listOf(
    Triple("cpu", "CPU", 100f),
    Triple("ram", "内存", 100f),
    Triple("network", "网络", 0f),
    Triple("load", "负载", 0f),
    Triple("disk", "磁盘", 100f)
)

private val HOURS = listOf(1, 3, 6, 24)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeDetailScreen(
    serverId: String,
    nodeId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val server = remember(serverId) { ServerStore.get(context, serverId) }
    val api = remember(server) { server?.let { KomariApi(it) } }

    var info by remember { mutableStateOf<ClientInfo?>(null) }
    var report by remember { mutableStateOf<Report?>(null) }
    var isOnline by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var metric by remember { mutableStateOf("cpu") }
    var hours by remember { mutableStateOf(6) }

    LaunchedEffect(api, nodeId) {
        api?.nodes()?.onSuccess { list ->
            info = list.firstOrNull { it.uuid == nodeId }
        }?.onFailure { error = it.message }
    }

    DisposableEffect(api, nodeId) {
        if (api == null) return@DisposableEffect onDispose {}
        val job = SupervisorJob()
        val scope = kotlinx.coroutines.CoroutineScope(job + Dispatchers.Default)
        var ws: WebSocket? = null
        ws = api.connectWs(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val env = runCatching { api.json.decodeFromString<WsEnvelope>(text) }.getOrNull()
                    ?: return
                if (env.status != "success" || env.data == null) return
                val d = env.data
                isOnline = d.online.contains(nodeId)
                val rep = d.data[nodeId]
                if (rep != null) report = rep
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                error = "实时连接断开：${t.message}"
            }
        })
        scope.launch {
            while (isActive) {
                ws.send("get $nodeId")
                delay(2000)
            }
        }
        onDispose {
            job.cancel()
            ws?.cancel()
        }
    }

    val recordsState = produceState<RecordsResponse?>(null, metric, hours, api, nodeId) {
        value = api?.records(nodeId, metric, hours)?.getOrNull()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(info?.name ?: "节点详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
        ) {
            // 头部信息
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).background(if (isOnline) KomariGreen else Color(0xFFBDBDBD), CircleShape))
                        Spacer(Modifier.width(7.dp))
                        Text(
                            info?.name ?: "…",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.weight(1f))
                        if (isOnline) StatusText("在线", KomariGreen) else StatusText("离线", Color.Gray)
                    }
                    val meta = listOfNotNull(
                        info?.os, info?.arch, info?.cpuName,
                        info?.cpuCores?.let { "$it 核" }, info?.region,
                        info?.virtualization
                    ).joinToString("  ·  ")
                    if (meta.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(meta, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    error?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = KomariRed)
                    }
                }
            }

            if (report != null) {
                // 环形 CPU + 内存/磁盘
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            UsageRing((report!!.cpu.usage ?: 0.0).toFloat(), "CPU 使用率", "cpu · ${report!!.cpu.name ?: ""}")
                            Spacer(Modifier.height(10.dp))
                            val load = report!!.load
                            if (load != null) {
                                Text(
                                    "负载 " + String.format(Locale.getDefault(), "%.2f / %.2f / %.2f", load.load1, load.load5, load.load15),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                    Card(
                        Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            DetailBar("内存", report!!.ram.used, report!!.ram.total)
                            Spacer(Modifier.height(12.dp))
                            DetailBar("磁盘", report!!.disk.used, report!!.disk.total)
                            report!!.swap?.let {
                                Spacer(Modifier.height(12.dp))
                                DetailBar("Swap", it.used, it.total)
                            }
                        }
                    }
                }

                // 网络 & 其他指标
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        val net = report!!.network
                        if (net != null) {
                            Text("网络传输", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth()) {
                                MetricCell("当前下行", formatSpeed(net.down), KomariBlue, Modifier.weight(1f))
                                MetricCell("当前上行", formatSpeed(net.up), KomariPurple, Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth()) {
                                MetricCell("累计下行", formatSpeed(net.totalDown), Color.Gray, Modifier.weight(1f))
                                MetricCell("累计上行", formatSpeed(net.totalUp), Color.Gray, Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                        Row(Modifier.fillMaxWidth()) {
                            val conn = report!!.connections
                            MetricCell("运行时长", formatUptime(report!!.uptime), Color.Gray, Modifier.weight(1f))
                            MetricCell(
                                "连接数",
                                if (conn != null) "${conn.tcp + conn.udp} (T/U)" else "-",
                                Color.Gray, Modifier.weight(1f)
                            )
                            MetricCell("进程", report!!.process.toString(), Color.Gray, Modifier.weight(1f))
                        }
                    }
                }

                // 历史图表
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("历史趋势", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(10.dp))

                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            METRICS.forEachIndexed { index, (key, label, _) ->
                                SegmentedButton(
                                    selected = metric == key,
                                    onClick = { metric = key },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = METRICS.size)
                                ) { Text(label) }
                            }
                        }
                        Spacer(Modifier.height(10.dp))

                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            HOURS.forEachIndexed { index, h ->
                                val selected = hours == h
                                SegmentedButton(
                                    selected = selected,
                                    onClick = { hours = h },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = HOURS.size)
                                ) { Text("${h}h") }
                            }
                        }
                        Spacer(Modifier.height(12.dp))

                        val records = recordsState.value?.records.orEmpty()
                        if (records.isEmpty()) {
                            Text("该时间段暂无数据", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        } else {
                            when (metric) {
                                "network" -> {
                                    val ins = records.map { it.netIn?.toFloat() }
                                    val outs = records.map { it.netOut?.toFloat() }
                                    NetworkLineChart(ins, outs)
                                    Text(
                                        "峰值 下行 ${formatSpeed(records.mapNotNull { it.netIn }.maxOrNull() ?: 0L)} · 上行 ${formatSpeed(records.mapNotNull { it.netOut }.maxOrNull() ?: 0L)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Gray
                                    )
                                }
                                "ram" -> {
                                    val pts = records.map { it.ramPercent?.toFloat() ?: percentOf(it.ram ?: 0L, it.ramTotal ?: 0L) }
                                    LineChart(pts, KomariPurple, maxOverride = 100f)
                                    Text(
                                        "峰值 ${records.mapNotNull { it.ramPercent?.toFloat() ?: percentOf(it.ram ?: 0L, it.ramTotal ?: 0L) }.maxOrNull()?.toInt() ?: "-"}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Gray
                                    )
                                }
                                "disk" -> {
                                    val pts = records.map { it.diskPercent?.toFloat() ?: percentOf(it.disk ?: 0L, it.diskTotal ?: 0L) }
                                    LineChart(pts, KomariPurple, maxOverride = 100f)
                                }
                                else -> {
                                    val field = if (metric == "cpu") "cpu" else "load"
                                    val pts = records.map { if (field == "cpu") it.cpu?.toFloat() else it.load?.toFloat() }
                                    val maxO = if (field == "cpu") 100f else 0f
                                    LineChart(pts, KomariPurple, maxOverride = maxO)
                                }
                            }
                        }
                    }
                }
            } else {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Text(if (isOnline) "等待实时数据…" else "节点离线", color = Color.Gray)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatusText(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun DetailBar(label: String, used: Long, total: Long) {
    val percent = percentOf(used, total)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Color.Gray, modifier = Modifier.width(40.dp))
            Text(
                "${formatBytes(used)} / ${formatBytes(total)}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.DarkGray,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            Text(
                "${percent.toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = if (percent > 90) KomariRed else Color.DarkGray
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(7.dp).background(Color(0xFFECE5F7), RoundedCornerShape(4.dp))) {
            if (percent > 0) {
                Box(
                    Modifier.fillMaxWidth(percent / 100f).height(7.dp)
                        .background(KomariPurple, RoundedCornerShape(4.dp))
                )
            }
        }
    }
}

@Composable
private fun MetricCell(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.padding(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}