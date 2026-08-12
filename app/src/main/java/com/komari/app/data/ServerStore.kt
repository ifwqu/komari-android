package com.komari.app.data

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

/** 服务器配置的本地持久化（SharedPreferences + JSON） */
object ServerStore {

    private const val PREFS = "komari_servers"
    private const val KEY = "servers"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(context: Context): List<StoredServer> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(StoredServer.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, servers: List<StoredServer>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, json.encodeToString(ListSerializer(StoredServer.serializer()), servers))
            .apply()
    }

    fun get(context: Context, id: String): StoredServer? = load(context).firstOrNull { it.id == id }

    fun upsert(context: Context, server: StoredServer) {
        val list = load(context).toMutableList()
        val idx = list.indexOfFirst { it.id == server.id }
        if (idx >= 0) list[idx] = server else list.add(server)
        save(context, list)
    }

    fun remove(context: Context, id: String) {
        save(context, load(context).filterNot { it.id == id })
    }

    fun newId(): String = UUID.randomUUID().toString()
}