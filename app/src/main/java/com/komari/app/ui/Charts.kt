package com.komari.app.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.komari.app.ui.theme.KomariBlue
import com.komari.app.ui.theme.KomariPurple
import kotlin.math.roundToInt

/** 环形百分比（如 CPU 使用率） */
@Composable
fun UsageRing(
    percent: Float,
    centerTop: String,
    slot: String,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    strokeWidth: Dp = 11.dp
) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(
                color = Color(0xFFE9E0F7),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke)
            )
            val sweep = (percent.coerceIn(0f, 100f) / 100f) * 360f
            if (sweep > 0) {
                drawArc(
                    color = KomariPurple,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${percent.roundToInt()}%",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(centerTop, style = MaterialTheme.typography.labelSmall)
            Text(slot, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

/** 单系列折线图 */
@Composable
fun LineChart(
    points: List<Float?>,
    color: Color,
    modifier: Modifier = Modifier,
    maxOverride: Float? = null,
    chartHeight: Dp = 140.dp
) {
    val maxValue = maxOverride ?: (points.filterNotNull().maxOrNull()?.let { (it * 1.15f).coerceAtLeast(1f) } ?: 100f)
    Canvas(modifier.fillMaxWidth().height(chartHeight)) {
        val gridColor = Color(0xFFEDE6F8)
        for (i in 0..3) {
            val y = size.height * i / 3f
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        val valid = points.filterNotNull()
        if (valid.isEmpty()) return@Canvas
        val gap = if (points.size > 1) size.width / (points.size - 1) else size.width
        val yFor = { v: Float -> size.height - (v / maxValue).coerceIn(0f, 1f) * size.height }

        val fillPath = Path()
        val linePath = Path()
        var hasPrevious = false
        var lastY = size.height
        points.forEachIndexed { i, v ->
            val x = i * gap
            if (v == null) {
                hasPrevious = false
                return@forEachIndexed
            }
            val y = yFor(v)
            lastY = y
            if (!hasPrevious) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, size.height)
                fillPath.lineTo(x, y)
                hasPrevious = true
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(size.width, lastY)
        fillPath.lineTo(size.width, size.height)
        fillPath.close()
        drawPath(
            fillPath,
            brush = Brush.verticalGradient(
                listOf(color.copy(alpha = 0.28f), Color.Transparent),
                endY = size.height
            )
        )
        drawPath(linePath, color = color, style = Stroke(width = 5f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

/** 双系列折线图（网络进出） */
@Composable
fun NetworkLineChart(
    inPoints: List<Float?>,
    outPoints: List<Float?>,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 140.dp
) {
    val all = (inPoints + outPoints).filterNotNull()
    val maxValue = if (all.isEmpty()) 1f else (all.maxOrNull()!! * 1.15f).coerceAtLeast(1f)

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 4.dp)) {
            Dot(Color(0xFF42A5F5), "下行")
            Spacer(Modifier.width(12.dp))
            Dot(KomariPurple, "上行")
        }
        Canvas(Modifier.fillMaxWidth().height(chartHeight)) {
            val gridColor = Color(0xFFEDE6F8)
            for (i in 0..3) {
                val y = size.height * i / 3f
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            fun drawSeries(values: List<Float?>, color: Color) {
                if (values.filterNotNull().isEmpty()) return
                val gap = if (values.size > 1) size.width / (values.size - 1) else size.width
                val path = Path()
                var started = false
                values.forEachIndexed { i, v ->
                    val x = i * gap
                    if (v == null) { started = false; return@forEachIndexed }
                    val y = size.height - (v / maxValue).coerceIn(0f, 1f) * size.height
                    if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
                }
                drawPath(path, color = color, style = Stroke(width = 4f, cap = StrokeCap.Round))
            }
            drawSeries(inPoints, Color(0xFF42A5F5))
            drawSeries(outPoints, KomariPurple)
        }
    }
}

@Composable
private fun Dot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(10.dp)) {
            drawCircle(color = color, radius = size.minDimension / 2)
        }
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }
}