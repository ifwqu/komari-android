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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.komari.app.BuildConfig
import com.komari.app.data.KomariApi
import com.komari.app.data.MeResponse
import com.komari.app.data.ServerStore
import com.komari.app.data.StoredServer
import com.komari.app.ui.theme.KomariGreen
import com.komari.app.ui.theme.KomariPurple
import com.komari.app.ui.theme.KomariRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerListScreen(
    onAddServer: () -> Unit,
    onEditServer: (String) -> Unit,
    onOpenServer: (String) -> Unit
) {
    val context = LocalContext.current
    var servers by remember { mutableStateOf(ServerStore.load(context)) }
    // serverId -> 会话是否有效
    var status by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var deleting by remember { mutableStateOf<StoredServer?>(null) }

    LaunchedEffect(servers) {
        val result = mutableMapOf<String, Boolean>()
        servers.forEach { s ->
            result[s.id] = if (s.sessionToken == null) {
                false
            } else {
                KomariApi(s).me().getOrDefault(MeResponse()).loggedIn
            }
        }
        status = result
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(20.dp)
                                    .background(KomariPurple, CircleShape)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Komari 监控", style = MaterialTheme.typography.titleLarge)
                        }
                        Text(
                            "v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddServer) {
                Icon(Icons.Default.Add, contentDescription = "添加服务器")
            }
        }
    ) { padding ->
        if (servers.isEmpty()) {
            EmptyServers(Modifier.padding(padding).fillMaxSize(), onAdd = onAddServer)
        } else {
            Column(Modifier.padding(padding).fillMaxSize()) {
                GroupHeader("服务器")
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(servers, key = { it.id }) { s ->
                        val ok = status[s.id]
                        ServerCard(
                            server = s,
                            sessionOk = ok,
                            onOpen = { onOpenServer(s.id) },
                            onEdit = { onEditServer(s.id) },
                            onDelete = { deleting = s }
                        )
                    }
                }
            }
        }
    }

    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除服务器") },
            text = { Text("确定删除服务器 ${target.host} 吗？本地保存的登录会话也会被清除。") },
            confirmButton = {
                TextButton(onClick = {
                    ServerStore.remove(context, target.id)
                    servers = ServerStore.load(context)
                    deleting = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun EmptyServers(modifier: Modifier = Modifier, onAdd: () -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("还没有配置服务器", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "添加你的 Komari 服务器地址与账号即可开始监控",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("添加服务器")
            }
        }
    }
}

@Composable
private fun ServerCard(
    server: StoredServer,
    sessionOk: Boolean?,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(server.host, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                Text(server.username, style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 1)
                Spacer(Modifier.height(6.dp))
                when (sessionOk) {
                    null -> StatusDot("检测中…", Color.Gray)
                    true -> StatusDot("会话有效", KomariGreen)
                    false -> StatusDot(if (server.sessionToken == null) "未登录" else "会话已失效", KomariRed)
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "编辑", tint = Color.Gray)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除", tint = Color.Gray)
            }
        }
    }
}

@Composable
private fun StatusDot(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}