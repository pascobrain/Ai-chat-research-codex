package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
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
    
    data class Quiz(
        val title: String,
        val question: String,
        val options: List<String>,
        val correctIndex: Int,
        val explanation: String
    ) : GenerativeUiData

    data class Timer(
        val title: String,
        val durationSeconds: Int
    ) : GenerativeUiData

    data class Flashcard(
        val title: String,
        val front: String,
        val back: String
    ) : GenerativeUiData

    data class FeedbackForm(
        val title: String,
        val question: String,
        val submitButtonText: String
    ) : GenerativeUiData
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
            "quiz" -> {
                val jsonOptions = root.optJSONArray("options")
                val options = mutableListOf<String>()
                if (jsonOptions != null) {
                    for (i in 0 until jsonOptions.length()) {
                        options.add(jsonOptions.getString(i))
                    }
                }
                GenerativeUiData.Quiz(
                    title = title,
                    question = root.optString("question", "Quiz question"),
                    options = options,
                    correctIndex = root.optInt("correctIndex", 0),
                    explanation = root.optString("explanation", "Verification Details")
                )
            }
            "timer" -> {
                GenerativeUiData.Timer(
                    title = title,
                    durationSeconds = root.optInt("durationSeconds", 60)
                )
            }
            "flashcard" -> {
                GenerativeUiData.Flashcard(
                    title = title,
                    front = root.optString("front", "Front context"),
                    back = root.optString("back", "Back explanation")
                )
            }
            "feedback_form" -> {
                GenerativeUiData.FeedbackForm(
                    title = title,
                    question = root.optString("question", "Feedback details"),
                    submitButtonText = root.optString("submitButtonText", "Submit")
                )
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
                is GenerativeUiData.Quiz -> InteractiveQuizView(data)
                is GenerativeUiData.Timer -> InteractiveTimerView(data)
                is GenerativeUiData.Flashcard -> InteractiveFlashcardView(data)
                is GenerativeUiData.FeedbackForm -> InteractiveFeedbackFormView(data)
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

@Composable
fun InteractiveQuizView(data: GenerativeUiData.Quiz) {
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val correctIndex = data.correctIndex
    
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
                    Text("🎓", fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = data.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Text(
                text = data.question,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 20.sp,
                modifier = Modifier.padding(bottom = 14.dp)
            )
            
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                data.options.forEachIndexed { index, option ->
                    val isSelected = selectedIndex == index
                    val isCorrectIdx = index == correctIndex
                    val isAnswered = selectedIndex != null
                    
                    val cardBgColor = when {
                        isAnswered && isCorrectIdx -> Color(0xFF2E7D32).copy(alpha = 0.12f)
                        isAnswered && isSelected && !isCorrectIdx -> Color(0xFFD32F2F).copy(alpha = 0.12f)
                        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        else -> Color.Transparent
                    }
                    
                    val cardBorderColor = when {
                        isAnswered && isCorrectIdx -> Color(0xFF2E7D32)
                        isAnswered && isSelected && !isCorrectIdx -> Color(0xFFD32F2F)
                        isSelected -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outlineVariant
                    }
                    
                    val cardBorderWidth = if (isSelected || (isAnswered && isCorrectIdx)) 2.dp else 1.dp
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(cardBgColor)
                            .clickable(enabled = !isAnswered) {
                                selectedIndex = index
                            }
                            .border(cardBorderWidth, cardBorderColor, RoundedCornerShape(10.dp))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val letter = ('A' + index).toString()
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isAnswered && isCorrectIdx -> Color(0xFF2E7D32)
                                        isAnswered && isSelected && !isCorrectIdx -> Color(0xFFD32F2F)
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = letter,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isAnswered && (isCorrectIdx || isSelected)) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Text(
                            text = option,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        
                        if (isAnswered) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when {
                                    isCorrectIdx -> "✅"
                                    isSelected -> "❌"
                                    else -> ""
                                },
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
            
            if (selectedIndex != null) {
                Spacer(modifier = Modifier.height(14.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(14.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("💡", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (selectedIndex == correctIndex) "Correct Answer!" else "Incorrect, but here is why:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedIndex == correctIndex) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                            )
                        }
                        if (data.explanation.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = data.explanation,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                lineHeight = 18.sp
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        TextButton(
                            onClick = { selectedIndex = null },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Reset Quiz", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InteractiveTimerView(data: GenerativeUiData.Timer) {
    var timeLeft by remember { mutableStateOf(data.durationSeconds) }
    var isRunning by remember { mutableStateOf(false) }
    val totalDuration = remember { data.durationSeconds }
    
    LaunchedEffect(isRunning, timeLeft) {
        if (isRunning && timeLeft > 0) {
            delay(1000)
            timeLeft--
            if (timeLeft == 0) {
                isRunning = false
            }
        }
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⏱️", fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = data.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            val progress = remember(timeLeft, totalDuration) {
                if (totalDuration > 0) timeLeft.toFloat() / totalDuration else 0f
            }
            val progressAnimated by animateFloatAsState(
                targetValue = progress,
                animationSpec = tween(500),
                label = "timerProgress"
            )
            
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(120.dp)
            ) {
                CircularProgressIndicator(
                    progress = { progressAnimated },
                    modifier = Modifier.fillMaxSize(),
                    color = if (timeLeft < 10) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary,
                    strokeWidth = 6.dp,
                    trackColor = MaterialTheme.colorScheme.outlineVariant
                )
                
                val minutes = timeLeft / 60
                val seconds = timeLeft % 60
                val displayStr = String.format("%02d:%02d", minutes, seconds)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = displayStr,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isRunning) "ACTIVE" else if (timeLeft == 0) "FINISHED" else "PAUSED",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = if (isRunning) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(14.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        timeLeft = (timeLeft - 30).coerceAtLeast(0)
                    },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(),
                    modifier = Modifier.size(36.dp)
                ) {
                    Text("-30s", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                
                Button(
                    onClick = { isRunning = !isRunning },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) Color(0xFFFFA000) else MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(
                        text = if (isRunning) "Pause" else "Start",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                
                IconButton(
                    onClick = {
                        timeLeft = (timeLeft + 30).coerceAtMost(totalDuration * 2)
                    },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(),
                    modifier = Modifier.size(36.dp)
                ) {
                    Text("+30s", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                
                IconButton(
                    onClick = {
                        timeLeft = totalDuration
                        isRunning = false
                    },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(),
                    modifier = Modifier.size(36.dp)
                ) {
                    Text("🔄", fontSize = 11.sp)
                }
            }
            
            if (timeLeft == 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2E7D32).copy(alpha = 0.15f))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "🎉 Time is up! Focus Block accomplished.",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32)
                    )
                }
            }
        }
    }
}

@Composable
fun InteractiveFlashcardView(data: GenerativeUiData.Flashcard) {
    var isFlipped by remember { mutableStateOf(false) }
    
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "cardFlip"
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clickable { isFlipped = !isFlipped }
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12 * density
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (rotation <= 90f) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("💡", fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = data.title,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "TAP TO FLIP",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    
                    Text(
                        text = data.front,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    
                    Text(
                        text = "Question Side",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.outline,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationY = 180f }
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🔑", fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = data.title + " (Solution)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "SHOW FRONT",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp,
                            color = Color(0xFF2E7D32)
                        )
                    }
                    
                    Text(
                        text = data.back,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    
                    Text(
                        text = "Answer Side",
                        fontSize = 9.sp,
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun InteractiveFeedbackFormView(data: GenerativeUiData.FeedbackForm) {
    var selectedRating by remember { mutableStateOf(0) }
    var commentText by remember { mutableStateOf("") }
    var isSubmitted by remember { mutableStateOf(false) }
    
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
                    Text("⭐", fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = data.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            if (!isSubmitted) {
                Text(
                    text = data.question,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 1..5) {
                        val isStarred = i <= selectedRating
                        Text(
                            text = if (isStarred) "⭐" else "☆",
                            fontSize = 32.sp,
                            modifier = Modifier
                                .clickable { selectedRating = i }
                        )
                    }
                }
                
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    placeholder = { Text("Add any extra comments...", fontSize = 13.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Button(
                    onClick = {
                        if (selectedRating > 0) {
                            isSubmitted = true
                        }
                    },
                    enabled = selectedRating > 0,
                    modifier = Modifier.align(Alignment.End),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(data.submitButtonText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF2E7D32).copy(alpha = 0.1f))
                        .border(1.dp, Color(0xFF2E7D32).copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text("🎉", fontSize = 32.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Thank you for your feedback!",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                        Text(
                            text = "Rating registered: $selectedRating Star${if (selectedRating > 1) "s" else ""}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        if (commentText.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "\"$commentText\"",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        TextButton(onClick = {
                            isSubmitted = false
                            selectedRating = 0
                            commentText = ""
                        }) {
                            Text("Resubmit", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
