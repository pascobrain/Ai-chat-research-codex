package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp

@Composable
fun AnimatedProviderLogo(
    protocol: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "provider_logo")

    // Slow rotation for general or OpenAI logo
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Gentle breathing scale to make the icon feel "alive"
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing"
    )

    // Radiant sweeping effect for gradients
    val gradientShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gradientShift"
    )

    // Fast sweep/pulse shift for Groq LPU speed engine
    val sweepShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "groqSweep"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val onSurface = MaterialTheme.colorScheme.onSurface

    Canvas(
        modifier = modifier
            .size(28.dp)
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = Math.min(cx, cy) * breathingScale

        when (protocol.lowercase().trim()) {
            "gemini" -> {
                // Gemini: Dual sparkle (primary large sparkle and secondary smaller sparkle)
                // Linear gradient that sweeps subtly
                val geminiBrush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF4285F4), // Google Blue
                        Color(0xFF9B51E0), // Soft Indigo/Purple
                        Color(0xFFF2994A), // Amber/Orange
                        Color(0xFF4285F4)
                    ),
                    start = Offset(0f, size.height * gradientShift),
                    end = Offset(size.width, size.height * (1f - gradientShift))
                )

                withTransform({
                    // Subtle rotation around center
                    rotate(rotationAngle * 0.15f, Offset(cx, cy))
                }) {
                    // Draw Primary Star
                    val primaryR = r * 0.65f
                    val primaryPath = createSparklePath(cx, cy, primaryR)
                    drawPath(path = primaryPath, brush = geminiBrush)

                    // Draw Secondary Star (offset and floating)
                    val secR = r * 0.28f
                    val orbitAngleRad = Math.toRadians((rotationAngle * 0.5f).toDouble())
                    val orbitRadius = r * 0.5f
                    val secCx = cx + (orbitRadius * Math.cos(orbitAngleRad)).toFloat()
                    val secCy = cy + (orbitRadius * Math.sin(orbitAngleRad)).toFloat()
                    
                    val secPath = createSparklePath(secCx, secCy, secR)
                    drawPath(
                        path = secPath,
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFF9B51E0), Color(0xFFF2994A))
                        )
                    )
                }
            }
            "openai" -> {
                // OpenAI: Rotated spiraling 6-capsule rosette
                val isDark = onSurface.red > 0.5f // Simple theme detection
                val openAiColor = if (isDark) Color(0xFF10A37F) else Color(0xFF0F8F6E)
                val baseBrush = Brush.linearGradient(
                    colors = listOf(openAiColor, primaryColor),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height)
                )

                val capsuleWidth = r * 0.26f
                val capsuleHeight = r * 0.82f
                val shiftX = r * 0.16f

                withTransform({
                    rotate(rotationAngle, Offset(cx, cy))
                }) {
                    for (i in 0..5) {
                        withTransform({
                            // Rotate each petal by 60 degrees, so they form a beautiful rosette
                            rotate(i * 60f, Offset(cx, cy))
                            // Shift slightly on the X-axis to create the spiraling overlap holes
                            translate(left = shiftX, top = 0f)
                        }) {
                            drawRoundRect(
                                brush = baseBrush,
                                topLeft = Offset(cx - capsuleWidth / 2f, cy - capsuleHeight),
                                size = Size(capsuleWidth, capsuleHeight),
                                cornerRadius = CornerRadius(capsuleWidth / 2f, capsuleWidth / 2f)
                            )
                        }
                    }
                }
            }
            "groq" -> {
                // Groq: High speed orbital engine (concentric rings rotating fast)
                val groqColor = Color(0xFFF97316) // Soft Neon Orange

                withTransform({
                    // Rotate and breathing scale
                    rotate(rotationAngle * 1.5f, Offset(cx, cy))
                }) {
                    // Draw outer LPU accelerator ring
                    drawCircle(
                        color = groqColor,
                        radius = r,
                        style = Stroke(width = 1.6.dp.toPx())
                    )

                    // Draw faster middle ring in opposite direction
                    withTransform({
                        rotate(-sweepShift * 2.2f, Offset(cx, cy))
                    }) {
                        val strokeWidth = 1.2.dp.toPx()
                        drawArc(
                            color = Color.White,
                            startAngle = 0f,
                            sweepAngle = 120f,
                            useCenter = false,
                            topLeft = Offset(cx - r * 0.65f, cy - r * 0.65f),
                            size = Size(r * 1.3f, r * 1.3f),
                            style = Stroke(width = strokeWidth)
                        )
                    }

                    // Fast core particle pulse
                    val pulseRadius = r * (0.22f + 0.08f * Math.sin(Math.toRadians(rotationAngle.toDouble() * 3)).toFloat())
                    drawCircle(
                        color = groqColor,
                        radius = pulseRadius
                    )

                    // Center solid node
                    drawCircle(
                        color = Color.White,
                        radius = r * 0.12f
                    )
                }
            }
            "nvidia" -> {
                // Nvidia: Neon-green rotating tech iris
                val nvidiaGreen = Color(0xFF76B900)
                val eyeBrush = Brush.radialGradient(
                    colors = listOf(Color.White, nvidiaGreen, Color(0xFF152A05)),
                    center = Offset(cx, cy),
                    radius = r
                )

                withTransform({
                    rotate(rotationAngle * 0.4f, Offset(cx, cy))
                }) {
                    // Draw continuous spiral or 4 angled sci-fi quadrants
                    for (i in 0..3) {
                        withTransform({
                            rotate(i * 90f, Offset(cx, cy))
                        }) {
                            val path = Path().apply {
                                moveTo(cx, cy)
                                quadraticTo(
                                    cx + r * 0.5f, cy - r * 0.2f,
                                    cx + r * 0.9f, cy - r * 0.6f
                                )
                                quadraticTo(
                                    cx + r * 0.4f, cy + r * 0.1f,
                                    cx, cy
                                )
                                close()
                            }
                            drawPath(
                                path = path,
                                color = nvidiaGreen.copy(alpha = 0.85f)
                            )
                        }
                    }

                    // Central glowing iris aperture
                    drawCircle(
                        brush = eyeBrush,
                        radius = r * 0.28f
                    )

                    // Center eye core
                    drawCircle(
                        color = Color.White,
                        radius = r * 0.1f
                    )
                }
            }
            else -> {
                // Fallback: Elegant cosmic pulse representing General AI / Custom Provider
                // Concentric circles with flowing color sweep
                val fallbackBrush = Brush.sweepGradient(
                    colors = listOf(primaryColor, secondaryColor, tertiaryColor, primaryColor),
                    center = Offset(cx, cy)
                )

                withTransform({
                    rotate(-rotationAngle * 0.5f, Offset(cx, cy))
                }) {
                    // Outer pulsating halo
                    drawCircle(
                        brush = fallbackBrush,
                        radius = r,
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // Middle breathing star path
                    val starR = r * 0.45f
                    val starPath = Path().apply {
                        for (i in 0..7) {
                            val angleRad = Math.toRadians((i * 45.0).toDouble())
                            val factor = if (i % 2 == 0) 1.0 else 0.4
                            val currentR = starR * factor
                            val px = cx + (currentR * Math.cos(angleRad)).toFloat()
                            val py = cy + (currentR * Math.sin(angleRad)).toFloat()
                            if (i == 0) {
                                moveTo(px, py)
                            } else {
                                lineTo(px, py)
                            }
                        }
                        close()
                    }
                    drawPath(
                        path = starPath,
                        color = primaryColor.copy(alpha = 0.85f)
                    )

                    // Tiny core node
                    drawCircle(
                        color = Color.White,
                        radius = r * 0.14f
                    )
                }
            }
        }
    }
}

private fun createSparklePath(cx: Float, cy: Float, r: Float): Path {
    return Path().apply {
        moveTo(cx, cy - r)
        quadraticTo(cx, cy, cx + r, cy)
        quadraticTo(cx, cy, cx, cy + r)
        quadraticTo(cx, cy, cx - r, cy)
        quadraticTo(cx, cy, cx, cy - r)
        close()
    }
}
