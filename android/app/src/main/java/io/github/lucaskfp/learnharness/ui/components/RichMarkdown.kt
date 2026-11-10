package io.github.lucaskfp.learnharness.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.rememberMarkdownState
import ru.noties.jlatexmath.JLatexMathAndroid
import ru.noties.jlatexmath.JLatexMathDrawable
import kotlin.math.abs

/**
 * Agent prose: GitHub-flavored Markdown, with display math (```math fences or `$$…$$`) drawn as
 * LaTeX and ```mermaid fences drawn by [MermaidStandIn]. Parsed synchronously so previews and screenshots show it.
 */
@Composable
fun RichMarkdown(markdown: String, modifier: Modifier = Modifier) {
    val state = rememberMarkdownState(fenceDisplayMath(markdown), immediate = true)
    Markdown(
        markdownState = state,
        modifier = modifier.fillMaxWidth(),
        components = markdownComponents(
            codeFence = { model ->
                MarkdownCodeFence(model.content, model.node) { code, language, style ->
                    when (language) {
                        "math" -> LatexBlock(code)
                        "mermaid" -> MermaidStandIn(code)
                        else -> Text(code, style = style, modifier = Modifier.horizontalScroll(rememberScrollState()))
                    }
                }
            },
        ),
    )
}

private val DisplayMath = Regex("""\$\$([\s\S]+?)\$\$""")

/** Rewrites `$$…$$` display math as a ```math fence, the one form the renderer draws as LaTeX. */
fun fenceDisplayMath(markdown: String): String =
    DisplayMath.replace(markdown) { "```math\n${it.groupValues[1].trim()}\n```" }

/** A display formula drawn with JLatexMath, centered and scrollable when wider than the column. */
@Composable
fun LatexBlock(latex: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val color = MaterialTheme.colorScheme.onSurface.toArgb()
    val textSizePx = with(density) { 20.sp.toPx() }
    val drawable = remember(latex, color, textSizePx) {
        JLatexMathAndroid.init(context.applicationContext)
        JLatexMathDrawable.builder(latex)
            .textSize(textSizePx)
            .color(color)
            .align(JLatexMathDrawable.ALIGN_CENTER)
            .build()
    }
    val width = with(density) { drawable.intrinsicWidth.toDp() }
    val height = with(density) { drawable.intrinsicHeight.toDp() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
            .semantics { contentDescription = latex },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(width, height)) {
            drawIntoCanvas { canvas ->
                drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
                drawable.draw(canvas.nativeCanvas)
            }
        }
    }
}

/**
 * A stand-in for Mermaid, which needs a WebView: it draws the participants and messages of a
 * `sequenceDiagram` (`A->>B: text` lines) in the theme's colors. Mockups only.
 */
@Composable
fun MermaidStandIn(source: String, modifier: Modifier = Modifier) {
    val messages = remember(source) { parseSequence(source) }
    val participants = remember(messages) { messages.flatMap { listOf(it.from, it.to) }.distinct() }
    val measurer = rememberTextMeasurer()
    val colors = MaterialTheme.colorScheme
    val labelStyle = MaterialTheme.typography.labelLarge.copy(color = colors.onSurface)
    val messageStyle = MaterialTheme.typography.bodySmall.copy(color = colors.onSurfaceVariant)
    val rowHeight = 44.dp
    val headerHeight = 40.dp
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(headerHeight + rowHeight * messages.size + 16.dp)
            .semantics { contentDescription = source },
    ) {
        if (participants.isEmpty()) return@Canvas
        val column = size.width / participants.size
        val header = headerHeight.toPx()
        val row = rowHeight.toPx()
        fun centerOf(name: String) = column * (participants.indexOf(name) + 0.5f)

        participants.forEach { name ->
            val x = centerOf(name)
            val boxWidth = column - 12.dp.toPx()
            drawRoundRect(
                color = colors.surfaceContainerHighest,
                topLeft = Offset(x - boxWidth / 2, 0f),
                size = Size(boxWidth, header - 8.dp.toPx()),
                cornerRadius = CornerRadius(12.dp.toPx()),
            )
            val text = measurer.measure(name, labelStyle, maxLines = 1)
            drawText(text, topLeft = Offset(x - text.size.width / 2f, (header - 8.dp.toPx() - text.size.height) / 2))
            drawLine(
                color = colors.outline,
                start = Offset(x, header - 8.dp.toPx()),
                end = Offset(x, size.height),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )
        }
        messages.forEachIndexed { i, message ->
            val y = header + row * i + row * 0.7f
            val from = centerOf(message.from)
            val to = centerOf(message.to)
            val direction = if (to >= from) 1f else -1f
            drawLine(colors.onSurface, Offset(from, y), Offset(to, y), strokeWidth = 1.5.dp.toPx())
            val head = 7.dp.toPx()
            drawLine(colors.onSurface, Offset(to, y), Offset(to - head * direction, y - head * 0.6f), strokeWidth = 1.5.dp.toPx())
            drawLine(colors.onSurface, Offset(to, y), Offset(to - head * direction, y + head * 0.6f), strokeWidth = 1.5.dp.toPx())
            val span = abs(to - from)
            val text = measurer.measure(
                message.text,
                messageStyle,
                maxLines = 1,
                constraints = Constraints(maxWidth = span.toInt().coerceAtLeast(1)),
            )
            drawText(text, topLeft = Offset(minOf(from, to) + (span - text.size.width) / 2, y - text.size.height - 2.dp.toPx()))
        }
    }
}

private data class SequenceMessage(val from: String, val to: String, val text: String)

private val MessageLine = Regex("""^\s*([^-\s>]+)\s*-+>>?\s*([^:\s]+)\s*:\s*(.+)$""")

private fun parseSequence(source: String): List<SequenceMessage> =
    source.lines().mapNotNull { line ->
        MessageLine.find(line)?.destructured?.let { (from, to, text) -> SequenceMessage(from, to, text.trim()) }
    }
