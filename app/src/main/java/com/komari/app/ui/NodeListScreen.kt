package com.komari.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberSaveable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.komari.app.data.ClientInfo
import com.komari.app.data.KomariApi
import com.komari.app.data.Report
import com.komari.app.data.ServerStore
import com.komari.app.data.formatBytes
import com.komari.app.data.formatSpeed
import com.komari.app.data.parseSnapshot
import com.komari.app.data.percentOf
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
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private enum class ServerTab(val label: String, val icon: ImageVector) {
    Nodes("节点", Icons.Default.List),
    Notifications("通知", Icons.Default.Notifications),
    Themes("主题", Icons.Default.Star),
    Plugins("插件", Icons.Default.Info),
    Settings("设置", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeListScreen(
    serverId: String,
    onBack: () -> Unit,
    onOpenNode: (nodeId: String, name: String) -> Unit
) {
    val context = LocalContext.current
    val server = remember(serverId) { ServerStore.get(context, serverId) }
    val api = remember(server) { server?.let { KomariApi(it) } }
    var tabName by rememberSaveable { mutableStateOf(ServerTab.Nodes.name) }
    val tab = ServerTab.valueOf(tabName)

    if (api == null) {
        Scaffold(topBar = { TopAppBar(title = { Text("节点") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } }) }) { p ->
            Box(Modifier.padding(p).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("服务器配置不存在")
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(server?.host ?: "服务器") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                ServerTab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tabName = t.name },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) }
                    )
                }
            }
        }
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        when (tab) {
            ServerTab.Nodes -> NodeListContent(contentModifier, api, onOpenNode)
            ServerTab.Notifications -> NotificationsAdmin(contentModifier, api)
            ServerTab.Themes -> ThemesAdmin(contentModifier, api)
            ServerTab.Plugins -> PluginsAdmin(contentModifier, api)
            ServerTab.Settings -> SettingsTab(contentModifier, api)
        }
    }
}

@Composable
private fun NodeListContent(
    modifier: Modifier,
    api: KomariApi,
    onOpenNode: (nodeId: String, name: String) -> Unit
) {
    var clients by remember { mutableStateOf<List<ClientInfo>>(emptyList()) }
    val reports = remember { mutableStateMapOf<String, Report>() }
    val online = remember { mutableStateMapOf<String, Boolean>() }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var lastUpdate by remember { mutableStateOf("—") }

    LaunchedEffect(api) {
        api.nodes()
            .onSuccess { clients = it }
            .onFailure { error = "加载节点失败：${it.message}" }
        loading = false
    }

    // 实时 WebSocket：宽松解析，任何消息异常都不影响其余节点
    DisposableEffect(api) {
        val job = SupervisorJob()
        val scope = kotlinx.coroutines.CoroutineScope(job + Dispatchers.Default)
        var ws: WebSocket? = null
        ws = api.connectWs(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val snap = parseSnapshot(text) ?: return
                lastUpdate = String.format(Locale.getDefault(), "%tR", Date())
                val on = snap.online.toSet()
                online.clear()
                on.forEach { online[it] = true }
                snap.reports.forEach { (uuid, rep) -> reports[uuid] = rep }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                error = "实时连接断开：${t.message}"
            }
        })
        scope.launch {
            while (isActive) {
                ws?.send("get")
                delay(2000)
            }
        }
        onDispose {
            job.cancel()
            ws?.cancel()
        }
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val onlineCount = clients.count { online[it.uuid] == true }
            Text(
                "在线 $onlineCount / ${clients.size}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            Text("更新 $lastUpdate", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }

        error?.let {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(KomariRed, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(it, color = KomariRed, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(4.dp))
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            clients.isEmpty() && !loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("暂无节点", color = Color.Gray)
                    Spacer(Modifier.height(6.dp))
                    Text("可在「设置 → 节点管理」中添加节点", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(clients, key = { it.uuid }) { node ->
                    NodeCard(
                        node = node,
                        report = reports[node.uuid],
                        isOnline = online[node.uuid] == true,
                        onClick = { onOpenNode(node.uuid, node.name) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NodeCard(
    node: ClientInfo,
    report: Report?,
    isOnline: Boolean,
    onClick: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).background(if (isOnline) KomariGreen else Color(0xFFBDBDBD), CircleShape))
                Spacer(Modifier.width(7.dp))
                Text(
                    node.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                val cpu = report?.cpu?.usage
                Text(
                    when {
                        report == null -> "—"
                        isOnline -> "${cpu?.roundToInt() ?: 0}%"
                        else -> "离线"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (report != null && isOnline) KomariPurple else Color.Gray
                )
            }

            Spacer(Modifier.height(2.dp))
            val meta = listOfNotNull(
                node.os,
                node.arch,
                node.cpuName,
                node.region
            ).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            if (report != null) {
                Spacer(Modifier.height(8.dp))
                BarRow("内存", report.ram.used, report.ram.total, isOnline)
                Spacer(Modifier.height(6.dp))
                BarRow("磁盘", report.disk.used, report.disk.total, isOnline)
                Spacer(Modifier.height(8.dp))
                val net = report.network
                val load = report.load
                Text(
                    buildString {
                        if (net != null) {
                            append("↓ ").append(formatSpeed(net.down)).append("  ↑ ").append(formatSpeed(net.up))
                        }
                        if (load != null) {
                            append("    负载 ").append(String.format(Locale.getDefault(), "%.1f", load.load1))
                        }
                    }.trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.DarkGray,
                    maxLines = 1
                )
            } else if (!isOnline) {
                Spacer(Modifier.height(6.dp))
                Text("离线，暂无数据", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
}

@Composable
private fun BarRow(label: String, used: Long, total: Long, isOnline: Boolean) {
    val percent = percentOf(used, total)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.Gray, modifier = Modifier.width(36.dp))
        Box(
            Modifier.weight(1f).height(7.dp).background(Color(0xFFECE5F7), RoundedCornerShape(4.dp))
        ) {
            if (percent > 0) {
                Box(
                    Modifier.fillMaxWidth(percent.coerceIn(0f, 100f) / 100f)
                        .height(7.dp)
                        .background(if (isOnline) KomariPurple else Color.Gray, RoundedCornerShape(4.dp))
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "${percent.toInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = if (percent > 90) KomariRed else Color.DarkGray,
            modifier = Modifier.width(40.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
        Text(
            "${formatBytes(used)} / ${formatBytes(total)}",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            modifier = Modifier.width(104.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            maxLines = 1
        )
    }
}

/** 设置分组：节点管理 / 站点设置 */
@Composable
private fun SettingsTab(modifier: Modifier, api: KomariApi) {
    var sub by remember { mutableStateOf<String?>(null) }
    Box(modifier.fillMaxSize()) {
        when (sub) {
            null -> Column(
                Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("管理分组", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                SettingsEntry("节点管理", "添加 / 删除节点，复制部署令牌", { sub = "clients" })
                SettingsEntry("站点设置", "查看服务器站点配置（只读）", { sub = "settings" })
            }
            "clients" -> Column(Modifier.fillMaxSize()) {
                SubHeader("节点管理") { sub = null }
                ClientsAdmin(Modifier.weight(1f), api)
            }
            "settings" -> Column(Modifier.fillMaxSize()) {
                SubHeader("站点设置") { sub = null }
                SettingsAdmin(Modifier.weight(1f), api)
            }
        }
    }
}

@Composable
private fun SettingsEntry(title: String, desc: String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(desc, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Icon(Icons.Default.Settings, contentDescription = null, tint = KomariPurple)
        }
    }
}

@Composable
private fun SubHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}