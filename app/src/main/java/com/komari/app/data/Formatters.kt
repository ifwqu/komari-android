package com.komari.app.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

/** 字节数格式化 */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
    return String.format(
        Locale.US,
        if (v >= 100 || v % 1.0 == 0.0) "%.0f %s" else "%.1f %s",
        v, units[i]
    )
}

/** 速率格式化：bytes/s */
fun formatSpeed(bytesPerSec: Long): String = "${formatBytes(bytesPerSec)}/s"

/** 百分比：used / total * 100 */
fun percentOf(used: Long, total: Long): Float =
    if (total <= 0) 0f else (used.toDouble() * 100 / total).coerceIn(0.0, 100.0).toFloat()

/** 运行时长格式化（秒） */
fun formatUptime(seconds: Long): String {
    if (seconds <= 0) return "—"
    val d = seconds / 86400
    val h = seconds % 86400 / 3600
    val m = seconds % 3600 / 60
    return when {
        d > 0 -> "${d}天${h}小时"
        h > 0 -> "${h}小时"
        m > 0 -> "${m}分钟"
        else -> "刚刚重启"
    }
}

/** RFC3339 时间格式化为 HH:mm */
fun formatClock(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return runCatching { TIME_FMT.format(Instant.parse(iso)) }.getOrDefault("")
}