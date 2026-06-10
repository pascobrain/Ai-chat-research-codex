package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

sealed interface GenerativeUiData {
    data class Poll(val title: String, val options: List<PollOption>) : GenerativeUiData
    data class Checklist(val title: String, val items: List<ChecklistItem>) : GenerativeUiData
    data class Comparison(
        val title: String,
        val headerA: String,
        val contentA: String,
        val headerB: String,
        val contentB: String
    ) : GenerativeUiData
    data class Metrics(val title: String, val metrics: List<PerformanceMetric>) : GenerativeUiData
}

data class PollOption(val id: String, val text: String, val votes: Int)
data class ChecklistItem(val id: String, val text: String, val done: Boolean)
data class PerformanceMetric(val label: String, val value: Int)

fun parseGenerativeUiPayload(jsonStr: String): GenerativeUiData? {
    try {
        val trimmed = jsonStr.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null
        val root = JSONObject(jsonStr)
        val type = root.optString("type")
        if (type != "generative_ui") return null

        val component = root.optString("component")
        val title = root.optString("title", "Interactive Component")

        return when (component) {
            "poll" -> {
                val jsonOptions = root.optJSONArray("options")
                val options = mutableListOf<PollOption>()
                if (jsonOptions != null) {
                    for (i in 0 until jsonOptions.length()) {
                        val obj = jsonOptions.getJSONObject(i)
                        options.add(
                            PollOption(
                                id = obj.optString("id", i.toString()),
                                text = obj.optString("text", ""),
                                votes = obj.optInt("votes", 0)
                            )
                        )
                    }
                }
                GenerativeUiData.Poll(title, options)
            }
            "checklist" -> {
                val jsonItems = root.optJSONArray("items")
                val items = mutableListOf<ChecklistItem>()
                if (jsonItems != null) {
                    for (i in 0 until jsonItems.length()) {
                        val obj = jsonItems.getJSONObject(i)
                        items.add(
                            ChecklistItem(
                                id = obj.optString("id", i.toString()),
                                text = obj.optString("text", ""),
                                done = obj.optBoolean("done", false)
                            )
                        )
                    }
                }
                GenerativeUiData.Checklist(title, items)
            }
            "comparison" -> {
                GenerativeUiData.Comparison(
                    title = title,
                    headerA = root.optString("headerA", "Option A"),
                    contentA = root.optString("contentA", ""),
                    headerB = root.optString("headerB", "Option B"),
                    contentB = root.optString("contentB", "")
                )
            }
            "metrics" -> {
                val jsonMetrics = root.optJSONArray("metrics")
                val metrics = mutableListOf<PerformanceMetric>()
                if (jsonMetrics != null) {
                    for (i in 0 until jsonMetrics.length()) {
                        val obj = jsonMetrics.getJSONObject(i)
                        metrics.add(
                            PerformanceMetric(
                                label = obj.optString("label", "Metric"),
                                value = obj.optInt("value", 50)
                            )
                        )
                    }
                }
                GenerativeUiData.Metrics(title, metrics)
            }
            else -> null
        }
    } catch (e: Exception) {
        return null
    }
}

@Composable
fun GenerativeUiContainer(
    jsonCode: String,
    modifier: Modifier = Modifier,
    onFallback: @Composable () -> Unit
) {
    val data = remember(jsonCode) { parseGenerativeUiPayload(jsonCode) }
    if (data == null) {
        onFallback()
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            when (data) {
                is GenerativeUiData.Poll -> InteractivePollView(data)
                is GenerativeUiData.Checklist -> InteractiveChecklistView(data)
                is GenerativeUiData.Comparison -> InteractiveComparisonView(data)
                is GenerativeUiData.Metrics -> InteractiveMetricsView(data)
            }
        }
    }
}

@Composable
fun InteractivePollView(data: GenerativeUiData.Poll) {
    var vOptions by remember { mutableStateOf(data.options) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val totalVotes = remember(vOptions, selectedId) { vOptions.sumOf { it.votes } }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "📊",
                        fontSize = 16.sp
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = data.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 22.sp
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                vOptions.forEach { opt ->
                    val percentage = if (totalVotes > 0) (opt.votes.toFloat() / totalVotes) else 0f
                    val progressAnimated by animateFloatAsState(
                        targetValue = percentage,
                        animationSpec = tween(600),
                        label = "progress"
                    )

                    val isSelected = selectedId == opt.id
                    val itemBgColor = if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(itemBgColor)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                if (selectedId == null) {
                                    selectedId = opt.id
                                    vOptions = vOptions.map {
                                        if (it.id == opt.id) it.copy(votes = it.votes + 1) else it
                                    }
                                }
                            }
                    ) {
                        // Progress filler
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressAnimated)
                                .fillMaxHeight()
                                .matchParentSize()
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                    else MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                                )
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = opt.text,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${(percentage * 100).toInt()}% (${opt.votes})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (selectedId != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Thank you for voting! ($totalVotes total feedback logs)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun InteractiveChecklistView(data: GenerativeUiData.Checklist) {
    var items by remember { mutableStateOf(data.items) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2E7D32).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = data.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { item ->
                    val isChecked = item.done
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isChecked) Color(0xFF2E7D32).copy(alpha = 0.06f)
                                else Color.Transparent
                            )
                            .clickable {
                                items = items.map {
                                    if (it.id == item.id) it.copy(done = !it.done) else it
                                }
                            }
                            .border(
                                width = 1.dp,
                                color = if (isChecked) Color(0xFF2E7D32).copy(alpha = 0.25f) else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isChecked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (isChecked) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = item.text,
                            fontSize = 14.sp,
                            color = if (isChecked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
                            textDecoration = if (isChecked) TextDecoration.LineThrough else TextDecoration.None,
                            lineHeight = 18.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Completed counter
            val doneCount = items.count { it.done }
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { if (items.isNotEmpty()) doneCount.toFloat() / items.size else 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = Color(0xFF2E7D32),
                trackColor = MaterialTheme.colorScheme.outlineVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$doneCount of ${items.size} steps completed",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                if (doneCount == items.size && items.isNotEmpty()) {
                    Text(
                        text = "🎉 Standard verified!",
                        fontSize = 11.sp,
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun InteractiveComparisonView(data: GenerativeUiData.Comparison) {
    var activeTab by remember { mutableStateOf(0) } // 0 = Side-by-side (expanded) / Tab A, 1 = Tab B

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = data.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            TabRow(
                selectedTabIndex = activeTab,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(8.dp)),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                indicator = {}
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    modifier = Modifier
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (activeTab == 0) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        )
                ) {
                    Text(
                        text = data.headerA,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (activeTab == 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    modifier = Modifier
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (activeTab == 1) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        )
                ) {
                    Text(
                        text = data.headerB,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (activeTab == 1) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                    .padding(14.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (activeTab == 0) data.headerA else data.headerB,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = if (activeTab == 0) data.contentA else data.contentB,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

@Composable
fun InteractiveMetricsView(data: GenerativeUiData.Metrics) {
    var metricsList by remember { mutableStateOf(data.metrics) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = data.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                metricsList.forEachIndexed { index, metric ->
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = metric.label,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${metric.value}%",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (metric.value > 80) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = metric.value.toFloat(),
                            onValueChange = { newVal ->
                                metricsList = metricsList.mapIndexed { idx, m ->
                                    if (idx == index) m.copy(value = newVal.toInt()) else m
                                }
                            },
                            valueRange = 0f..100f,
                            modifier = Modifier.height(20.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                    }
                }
            }

            // Calculation Panel
            val overallAverage = remember(metricsList) {
                if (metricsList.isNotEmpty()) metricsList.sumOf { it.value } / metricsList.size else 0
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                    .border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        RoundedCornerShape(10.dp)
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Composite Rating Index",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = when {
                            overallAverage > 85 -> "Outstanding Architecture"
                            overallAverage > 70 -> "Optimal Design Block"
                            else -> "Requires Attention"
                        },
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "$overallAverage%",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
