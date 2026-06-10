package com.example.ui.screens

import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.core.CorePlugin
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.core.MarkwonTheme

@Composable
fun MarkwonMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: Color = Color.Unspecified
) {
    val context = LocalContext.current
    val markwon = remember {
        Markwon.builder(context)
            .usePlugin(CorePlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder.blockMargin((12 * context.resources.displayMetrics.density).toInt())
                    builder.codeBlockTextSize((13 * context.resources.displayMetrics.density).toInt())
                    builder.codeBlockBackgroundColor(android.graphics.Color.parseColor("#1E1E1E"))
                    builder.codeBlockTextColor(android.graphics.Color.LTGRAY)
                    builder.codeTextColor(android.graphics.Color.parseColor("#FF5252"))
                    builder.codeBackgroundColor(android.graphics.Color.parseColor("#333333"))
                    if (textColor != Color.Unspecified) {
                        builder.headingBreakColor(textColor.copy(alpha = 0.2f).toArgb())
                    }
                }
            })
            .build()
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                if (textColor != Color.Unspecified) {
                    setTextColor(textColor.toArgb())
                }
                textSize = 15f
                setLineSpacing(0f, 1.3f)
            }
        },
        update = { textView ->
            if (textColor != Color.Unspecified) {
                textView.setTextColor(textColor.toArgb())
            }
            markwon.setMarkdown(textView, markdown)
        }
    )
}
