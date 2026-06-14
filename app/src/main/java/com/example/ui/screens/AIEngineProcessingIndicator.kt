package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AIEngineProcessingIndicator(
    status: String,
    provider: String,
    modifier: Modifier = Modifier
) {
    val isSearching = status.startsWith("SEARCHING_")
    val isThinking = status == "THINKING"
    val isGenerating = status == "GENERATING"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        ),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // High-fidelity Orbital Spinner
                OrbitalIndicator()

                // Animated text status
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    val statusHeader = when {
                        isSearching -> "Web Search"
                        isThinking -> "Thinking"
                        isGenerating -> "Generating Response"
                        else -> "Processing Request"
                    }

                    val statusDescription = when (status) {
                        "SEARCHING_TAVILY" -> "Inquiring Tavily Search API..."
                        "SEARCHING_BRAVE" -> "Executing privacy Brave Web Search..."
                        "SEARCHING_WIKIPEDIA" -> "Browsing Wikipedia databases..."
                        "SEARCHING_GOOGLE" -> "Consulting Google live indices..."
                        "THINKING" -> "Synthesizing sources & reasoning..."
                        "GENERATING" -> "Formatting answers with markdown..."
                        else -> "Initializing AI engine & connections..."
                    }

                    Text(
                        text = statusHeader,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    AnimatedContent(
                        targetState = statusDescription,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(220, delayMillis = 90)) togetherWith
                            fadeOut(animationSpec = tween(90))
                        },
                        label = "status_desc"
                    ) { targetText ->
                        Text(
                            text = targetText,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            // Interactive detailed task tracker list inside card
            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), thickness = 1.dp)

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Step 1: Query planning
                ProcessingStepRow(
                    label = "Analyzing context & prompt",
                    isDone = true,
                    isActive = false
                )

                // Step 2: Web Search (Only visible or active if searching or finished searching)
                val searchDone = isThinking || isGenerating
                val searchActive = isSearching
                ProcessingStepRow(
                    label = when (status) {
                        "SEARCHING_TAVILY" -> "Web Search (Tavily Engine)"
                        "SEARCHING_BRAVE" -> "Web Search (Brave Engine)"
                        "SEARCHING_WIKIPEDIA" -> "Knowledge Search (Wikipedia)"
                        "SEARCHING_GOOGLE" -> "Web Search (Google Engine)"
                        else -> "Web Search"
                    },
                    isDone = searchDone,
                    isActive = searchActive,
                    isVisible = isSearching || searchDone
                )

                // Step 3: Synthesis / Gen
                ProcessingStepRow(
                    label = "Generating streaming response",
                    isDone = false,
                    isActive = isGenerating || isThinking
                )
            }
        }
    }
}

@Composable
fun ProcessingStepRow(
    label: String,
    isDone: Boolean,
    isActive: Boolean,
    isVisible: Boolean = true
) {
    if (!isVisible) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (isDone) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Completed",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
        } else if (isActive) {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse_step")
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "alpha"
            )
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = pulseAlpha))
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .align(Alignment.Center)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), CircleShape)
            )
        }

        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            color = if (isDone) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            } else if (isActive) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            }
        )
    }
}

@Composable
fun OrbitalIndicator(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "orbital")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_angle"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Canvas(modifier = modifier.size(42.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val maxR = size.width / 2f

        // Pulsing background ambient glow
        drawCircle(
            color = Color(0xFF4285F4).copy(alpha = pulseAlpha * 0.12f),
            radius = maxR
        )

        // Draw orbital track rings
        drawCircle(
            color = Color(0xFF4285F4).copy(alpha = 0.12f),
            radius = maxR * 0.72f,
            style = Stroke(width = 1.dp.toPx())
        )
        drawCircle(
            color = Color(0xFF9B51E0).copy(alpha = 0.08f),
            radius = maxR * 0.45f,
            style = Stroke(width = 1.dp.toPx())
        )

        // Draw primary orbiting particle
        val r1 = maxR * 0.72f
        val rad1 = Math.toRadians(angle.toDouble())
        val px1 = cx + (r1 * Math.cos(rad1)).toFloat()
        val py1 = cy + (r1 * Math.sin(rad1)).toFloat()
        drawCircle(
            color = Color(0xFF4285F4),
            radius = 3.dp.toPx(),
            center = Offset(px1, py1)
        )

        // Draw secondary opposing orbiting particle
        val r2 = maxR * 0.45f
        val rad2 = Math.toRadians((angle * -1.6f).toDouble())
        val px2 = cx + (r2 * Math.cos(rad2)).toFloat()
        val py2 = cy + (r2 * Math.sin(rad2)).toFloat()
        drawCircle(
            color = Color(0xFF9B51E0),
            radius = 2.2f.dp.toPx(),
            center = Offset(px2, py2)
        )

        // Draw tertiary core sparkle node
        val pulseR = maxR * 0.22f * (1f + 0.12f * Math.sin(rad1).toFloat())
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFF2994A), Color(0xFFFFFFFF)),
                center = Offset(cx, cy)
            ),
            radius = pulseR
        )
    }
}
