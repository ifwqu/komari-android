package com.komari.app.ui

import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.komari.app.data.ClientInfo
import com.komari.app.data.FlatAddResult
import com.komari.app.data.KomariApi
import com.komari.app.data.ServerStore
import com.komari.app.ui.theme.KomariGreen
import com.komari.app.ui.theme.KomariPurple
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale

private enum class AdminPage(val title: String) {
    Menu("管理"),
    Clients("节点管理"),
    Themes("主题"),
    Plugins("插件"),
    Notifications("通知"),
    Settings("站点设置")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(serverId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val server = remember(serverId) { ServerStore.get(context, serverId) }
    val api = remember(server) { server?.let { KomariApi(it) } }
    var page by remember { mutableStateOf(AdminPage.Menu) }

    if (api == null) {
        Scaffold(topBar = { TopAppBar(title = { Text("管理") }, navigationIcon = { BackIcon { onBack() } }) }) { p ->
            Box(Modifier.padding(p).fillMaxSize(), contentAlignment = Alignment.Center) { Text("服务器配置不存在") }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(page.title) },
                navigationIcon = {
                    BackIcon {
                        if (page == AdminPage.Menu) onBack() else page = AdminPage.Menu
                    }
                }
            )
        }
    ) { padding ->
        when (page) {
            AdminPage.Menu -> AdminMenu(
                modifier = Modifier.padding(padding),
                onOpen = { page = it }
            )
            AdminPage.Clients -> ClientsAdmin(Modifier.padding(padding), api)
            AdminPage.Themes -> ThemesAdmin(Modifier.padding(padding), api)
            AdminPage.Plugins -> PluginsAdmin(Modifier.padding(padding), api)
            AdminPage.Notifications -> NotificationsAdmin(Modifier.padding(padding), api)
            AdminPage.Settings -> SettingsAdmin(Modifier.padding(padding), api)
        }
    }
}

@Composable
private fun BackIcon(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
    }
}

/* ---------------- 菜单 ---------------- */

@Composable
private fun AdminMenu(modifier: Modifier, onOpen: (AdminPage) -> Unit) {
    val items = listOf(
        Triple(AdminPage.Clients, "节点管理", "添加 / 删除节点，查看部署令牌"),
        Triple(AdminPage.Themes, "主题", "应用服务器上已安装的主题"),
        Triple(AdminPage.Plugins, "插件", "启用 / 禁用插件"),
        Triple(AdminPage.Notifications, "通知", "节点离线提醒开关"),
        Triple(AdminPage.Settings, "站点设置", "查看站点配置（只读）")
    )
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(items) { (page, title, desc) ->
            Card(
                Modifier.fillMaxWidth().clickable { onOpen(page) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(2.dp))
                        Text(desc, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = null,
                        tint = KomariPurple
                    )
                }
            }
        }
    }
}

/* ---------------- 节点管理 ---------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClientsAdmin(modifier: Modifier, api: KomariApi) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var clients by remember { mutableStateOf<List<ClientInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var newClient by remember { mutableStateOf<FlatAddResult?>(null) }
    var deleting by remember { mutableStateOf<ClientInfo?>(null) }
    var toggling by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        api.adminClients()
            .onSuccess { clients = it; loading = false }
            .onFailure { error = it.message; loading = false }
    }

    LaunchedEffect(api) { reload() }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            error?.let {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(Color(0xFFE53935), CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(it, color = Color(0xFFE53935), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(clients, key = { it.uuid }) { c ->
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(c.name.ifBlank { "(未命名)" }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${c.os ?: ""} ${c.arch ?: ""}".trim(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Gray,
                                        maxLines = 1
                                    )
                                    Text(c.uuid, style = MaterialTheme.typography.labelSmall, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (toggling == c.uuid) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    TextButton(onClick = {
                                        val id = c.uuid
                                        toggling = id
                                        scope.launch {
                                            api.adminClientToken(id)
                                                .onSuccess { token ->
                                                    clipboard.setText(AnnotatedString(token))
                                                    Toast.makeText(context, "令牌已复制：$token", Toast.LENGTH_LONG).show()
                                                }
                                                .onFailure { Toast.makeText(context, "获取令牌失败：${it.message}", Toast.LENGTH_SHORT).show() }
                                            toggling = null
                                        }
                                    }) {
                                        Text("复制令牌", style = MaterialTheme.typography.labelMedium, color = KomariPurple)
                                    }
                                }
                                IconButton(onClick = { deleting = c }) {
                                    Icon(Icons.Default.Delete, contentDescription = "删除", tint = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "添加节点")
        }
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!busy) showAdd = false },
            title = { Text("添加节点") },
            text = {
                Column {
                    Text("名称可留空（将自动生成），保存后复制令牌到服务器上执行安装命令。", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("节点名称（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    scope.launch {
                        busy = true
                        api.adminAddClient(name.ifBlank { null })
                            .onSuccess {
                                newClient = it
                                showAdd = false
                                reload()
                            }
                            .onFailure { Toast.makeText(context, "添加失败：${it.message}", Toast.LENGTH_SHORT).show() }
                        busy = false
                    }
                }) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { if (!busy) showAdd = false }) { Text("取消") } }
        )
    }

    newClient?.let { r ->
        AlertDialog(
            onDismissRequest = { newClient = null },
            title = { Text("节点创建成功") },
            text = {
                Column {
                    Text("UUID：${r.uuid}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    Text("令牌：${r.token}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(10.dp))
                    Text("请复制令牌到目标服务器，用 komari 官方安装方式部署 agent。", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(r.token ?: ""))
                    Toast.makeText(context, "已复制令牌", Toast.LENGTH_SHORT).show()
                }) { Text("复制令牌") }
            },
            dismissButton = { TextButton(onClick = { newClient = null }) { Text("完成") } }
        )
    }

    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除节点") },
            text = { Text("确定删除节点「${target.name.ifBlank { target.uuid }}」吗？该节点的所有历史记录也会被清除。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        api.adminRemoveClient(target.uuid)
                            .onSuccess { Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show(); reload() }
                            .onFailure { Toast.makeText(context, "删除失败：${it.message}", Toast.LENGTH_SHORT).show() }
                    }
                    deleting = null
                }) { Text("删除", color = Color(0xFFE53935)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } }
        )
    }
}

/* ---------------- 主题 ---------------- */

@Composable
private fun ThemesAdmin(modifier: Modifier, api: KomariApi) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var themes by remember { mutableStateOf<List<com.komari.app.data.ThemeInfo>>(emptyList()) }
    var settings by remember { mutableStateOf<JsonObject?>(null) }
    var loading by remember { mutableStateOf(true) }
    var applying by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(api) {
        api.themes().onSuccess { themes = it }.onFailure { }
        api.adminSettings().onSuccess { settings = it }.onFailure { }
        loading = false
    }

    val current = settings?.get("theme")?.jsonPrimitive?.contentOrNull

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (loading) {
            Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        Text(
            "当前主题：${current ?: "default"}",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            color = KomariPurple,
            fontWeight = FontWeight.SemiBold
        )
        themes.forEach { t ->
            val isCurrent = t.short == current
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
                    .clickable(enabled = !isCurrent && applying == null) {
                        applying = t.short
                        scope.launch {
                            api.setTheme(t.short)
                                .onSuccess { Toast.makeText(context, "已应用主题：${t.displayName()}", Toast.LENGTH_SHORT).show() }
                                .onFailure { Toast.makeText(context, "应用失败：${it.message}", Toast.LENGTH_SHORT).show() }
                            applying = null
                        }
                    },
                colors = CardDefaults.cardColors(
                    containerColor = if (isCurrent) KomariPurple.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.displayName(), fontWeight = FontWeight.SemiBold)
                        Text(
                            listOfNotNull(t.short, t.version?.let { "v$it" }, t.author).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                    if (isCurrent) {
                        Text("使用中", color = KomariPurple, style = MaterialTheme.typography.labelMedium)
                    } else if (applying == t.short) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
}

/* ---------------- 插件 ---------------- */

@Composable
private fun PluginsAdmin(modifier: Modifier, api: KomariApi) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var plugins by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var toggling by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(api) {
        api.plugins()
            .onSuccess { plugins = it }
            .onFailure { error = it.message }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        error?.let {
            Text(it, color = Color(0xFFE53935), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
        }
        if (plugins.isEmpty() && error == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("未安装插件", color = Color.Gray) }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(plugins) { p ->
                val short = p["short"]?.jsonPrimitive?.contentOrNull ?: "?"
                val name = p["name"]?.jsonPrimitive?.contentOrNull ?: short
                val desc = p["description"]?.jsonPrimitive?.contentOrNull
                val enabled = p["enabled"]?.jsonPrimitive?.booleanOrNull ?: false
                val running = p["running"]?.jsonPrimitive?.booleanOrNull
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (!desc.isNullOrBlank()) {
                                Text(desc, style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Text(
                                buildString {
                                    append("short: $short")
                                    if (running != null) append(" · ${if (running) "运行中" else "未运行"}")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                        if (toggling == short) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Switch(
                                checked = enabled,
                                onCheckedChange = { target ->
                                    toggling = short
                                    scope.launch {
                                        api.setPluginEnabled(short, target)
                                            .onSuccess {
                                                plugins = plugins.map { p ->
                                                    if (p["short"]?.jsonPrimitive?.contentOrNull == short) {
                                                        kotlinx.serialization.json.JsonObject(
                                                            p.toMutableMap().apply {
                                                                put("enabled", kotlinx.serialization.json.JsonPrimitive(target))
                                                            }
                                                        )
                                                    } else p
                                                }
                                            }
                                            .onFailure { Toast.makeText(context, "操作失败：${it.message}", Toast.LENGTH_SHORT).show() }
                                        toggling = null
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/* ---------------- 通知 ---------------- */

@Composable
private fun NotificationsAdmin(modifier: Modifier, api: KomariApi) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var nodes by remember { mutableStateOf<List<ClientInfo>>(emptyList()) }
    var offline by remember { mutableStateOf<Map<String, com.komari.app.data.OfflineNotification>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var toggling by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        val ns = api.nodes().getOrNull() ?: emptyList()
        val of = api.offlineNotifications().getOrNull() ?: emptyList()
        nodes = ns
        offline = of.associateBy { it.client }
        loading = false
    }

    LaunchedEffect(api) { reload() }

    Column(Modifier.fillMaxSize()) {
        Text(
            "开启后，节点离线超过宽限期将通知你。",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        if (nodes.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("暂无节点", color = Color.Gray) }
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(nodes, key = { it.uuid }) { node ->
                val info = offline[node.uuid]
                val enabled = info?.enable ?: false
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(node.name.ifBlank { "(未命名)" }, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (enabled) "离线提醒已开启 · 宽限 ${info?.gracePeriod ?: "-"} 秒"
                                else "离线提醒未开启",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (enabled) KomariGreen else Color.Gray
                            )
                        }
                        if (toggling == node.uuid) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Switch(
                                checked = enabled,
                                onCheckedChange = { target ->
                                    toggling = node.uuid
                                    scope.launch {
                                        api.setOfflineNotification(listOf(node.uuid), target)
                                            .onSuccess {
                                                offline = offline + (node.uuid to com.komari.app.data.OfflineNotification(client = node.uuid, enable = target, gracePeriod = info?.gracePeriod ?: 0))
                                            }
                                            .onFailure { Toast.makeText(context, "操作失败：${it.message}", Toast.LENGTH_SHORT).show() }
                                        toggling = null
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/* ---------------- 站点设置 ---------------- */

@Composable
private fun SettingsAdmin(modifier: Modifier, api: KomariApi) {
    var data by remember { mutableStateOf<JsonObject?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(api) {
        api.adminSettings()
            .onSuccess { data = it }
            .onFailure { error = it.message }
        loading = false
    }

    Column(modifier.fillMaxSize()) {
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        error?.let {
            Text(it, color = Color(0xFFE53935), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
        }
        Text(
            "只读展示。修改请在 Web 管理后台进行。",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
        val obj = data ?: JsonObject(emptyMap())
        if (obj.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("无设置数据", color = Color.Gray) }
            return@Column
        }
        val rows = obj.keys.sortedBy { it }.mapNotNull { key ->
            val v = obj[key] ?: return@mapNotNull null
            when (v) {
                is JsonPrimitive -> key to v.content
                else -> null
            }
        }
        if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("无标量设置项", color = Color.Gray) }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(rows) { (k, v) ->
                    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(k, style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.weight(0.4f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(v, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}