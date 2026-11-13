package org.qownnotes.mobile

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal enum class AnnotationTool { PEN, HIGHLIGHTER, ARROW, RECTANGLE, TEXT, ERASER }

/** A mark on an image. Positions and sizes are in pixels of the annotated bitmap. */
internal sealed interface ImageAnnotation {
    val color: Int

    data class Stroke(
        val points: List<Offset>,
        override val color: Int,
        val width: Float,
        val highlighter: Boolean = false
    ) : ImageAnnotation

    data class Arrow(
        val start: Offset,
        val end: Offset,
        override val color: Int,
        val width: Float
    ) : ImageAnnotation

    data class Rectangle(
        val start: Offset,
        val end: Offset,
        override val color: Int,
        val width: Float
    ) : ImageAnnotation

    /** Text whose first line's top left corner is at [position]. */
    data class Label(
        val position: Offset,
        val text: String,
        override val color: Int,
        val size: Float
    ) : ImageAnnotation
}

/** The marks on an image, with undo and redo of every completed change. */
internal class AnnotationHistory {
    var annotations by mutableStateOf(emptyList<ImageAnnotation>())
        private set
    private val undone = mutableStateListOf<List<ImageAnnotation>>()
    private val redone = mutableStateListOf<List<ImageAnnotation>>()

    val canUndo: Boolean get() = undone.isNotEmpty()
    val canRedo: Boolean get() = redone.isNotEmpty()

    fun commit(changed: List<ImageAnnotation>) {
        if (changed == annotations) return
        undone += annotations
        redone.clear()
        annotations = changed
    }

    fun undo() {
        if (undone.isEmpty()) return
        redone += annotations
        annotations = undone.removeAt(undone.lastIndex)
    }

    fun redo() {
        if (redone.isEmpty()) return
        undone += annotations
        annotations = redone.removeAt(redone.lastIndex)
    }
}

/**
 * Whether the eraser touching [point] with [radius] hits [annotation]. Shapes are hit on their
 * outline, so a rectangle drawn around something does not get in the way of erasing inside it.
 */
internal fun ImageAnnotation.isHit(point: Offset, radius: Float): Boolean = when (this) {
    is ImageAnnotation.Stroke -> {
        val reach = radius + width / 2f
        if (points.size == 1) {
            (points[0] - point).getDistance() <= reach
        } else {
            points.zipWithNext().any { (a, b) -> distanceToSegment(point, a, b) <= reach }
        }
    }
    is ImageAnnotation.Arrow -> distanceToSegment(point, start, end) <= radius + width
    is ImageAnnotation.Rectangle -> {
        val topLeft = Offset(min(start.x, end.x), min(start.y, end.y))
        val bottomRight = Offset(max(start.x, end.x), max(start.y, end.y))
        val topRight = Offset(bottomRight.x, topLeft.y)
        val bottomLeft = Offset(topLeft.x, bottomRight.y)
        listOf(topLeft to topRight, topRight to bottomRight, bottomRight to bottomLeft)
            .plus(bottomLeft to topLeft)
            .any { (a, b) -> distanceToSegment(point, a, b) <= radius + width / 2f }
    }
    is ImageAnnotation.Label -> labelBounds(this).run {
        point.x in left - radius..right + radius && point.y in top - radius..bottom + radius
    }
}

internal fun distanceToSegment(point: Offset, start: Offset, end: Offset): Float {
    val segment = end - start
    val lengthSquared = segment.x * segment.x + segment.y * segment.y
    if (lengthSquared == 0f) return (point - start).getDistance()
    val along = (((point - start).x * segment.x + (point - start).y * segment.y) / lengthSquared)
        .coerceIn(0f, 1f)
    return (point - (start + segment * along)).getDistance()
}

internal fun labelBounds(label: ImageAnnotation.Label): RectF {
    val paint = labelPaint(label)
    val lines = label.text.split('\n')
    val width = lines.maxOf(paint::measureText)
    val height = paint.fontSpacing * lines.size
    return RectF(
        label.position.x,
        label.position.y,
        label.position.x + width,
        label.position.y + height
    )
}

internal fun Canvas.drawAnnotation(annotation: ImageAnnotation) {
    when (annotation) {
        is ImageAnnotation.Stroke -> drawStroke(annotation)
        is ImageAnnotation.Arrow -> drawArrow(annotation)
        is ImageAnnotation.Rectangle -> drawRoundRect(
            min(annotation.start.x, annotation.end.x),
            min(annotation.start.y, annotation.end.y),
            max(annotation.start.x, annotation.end.x),
            max(annotation.start.y, annotation.end.y),
            annotation.width,
            annotation.width,
            linePaint(annotation.color, annotation.width)
        )
        is ImageAnnotation.Label -> drawLabel(annotation)
    }
}

private fun Canvas.drawStroke(stroke: ImageAnnotation.Stroke) {
    val paint = linePaint(stroke.color, stroke.width).apply {
        if (stroke.highlighter) alpha = HIGHLIGHTER_ALPHA
    }
    val points = stroke.points
    if (points.size == 1) {
        paint.style = Paint.Style.FILL
        drawCircle(points[0].x, points[0].y, stroke.width / 2f, paint)
        return
    }
    // Curving through the midpoints of the touch samples smooths out their corners.
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (index in 1 until points.lastIndex) {
            val point = points[index]
            val next = points[index + 1]
            quadTo(point.x, point.y, (point.x + next.x) / 2f, (point.y + next.y) / 2f)
        }
        lineTo(points.last().x, points.last().y)
    }
    drawPath(path, paint)
}

private fun Canvas.drawArrow(arrow: ImageAnnotation.Arrow) {
    val length = (arrow.end - arrow.start).getDistance()
    if (length == 0f) return
    val headLength = min(max(arrow.width * 4.5f, 12f), length * 0.6f)
    val angle = atan2(arrow.end.y - arrow.start.y, arrow.end.x - arrow.start.x)
    val direction = Offset(cos(angle), sin(angle))
    // The shaft stops inside the head, so its round end does not poke out of the tip.
    val shaftEnd = arrow.end - direction * (headLength * 0.6f)
    drawLine(
        arrow.start.x,
        arrow.start.y,
        shaftEnd.x,
        shaftEnd.y,
        linePaint(arrow.color, arrow.width)
    )
    val spread = Math.toRadians(ARROW_HEAD_DEGREES.toDouble()).toFloat()
    val left = arrow.end - Offset(cos(angle - spread), sin(angle - spread)) * headLength
    val right = arrow.end - Offset(cos(angle + spread), sin(angle + spread)) * headLength
    val head = Path().apply {
        moveTo(arrow.end.x, arrow.end.y)
        lineTo(left.x, left.y)
        lineTo(right.x, right.y)
        close()
    }
    drawPath(
        head,
        linePaint(arrow.color, arrow.width / 2f).apply { style = Paint.Style.FILL_AND_STROKE }
    )
}

private fun Canvas.drawLabel(label: ImageAnnotation.Label) {
    val fill = labelPaint(label)
    // A contrasting outline keeps text readable on busy photos.
    val outline = labelPaint(label).apply {
        style = Paint.Style.STROKE
        strokeWidth = label.size * 0.16f
        strokeJoin = Paint.Join.ROUND
        color = if (isLight(label.color)) 0xCC000000.toInt() else 0xE6FFFFFF.toInt()
    }
    label.text.split('\n').forEachIndexed { index, line ->
        val baseline = label.position.y - fill.fontMetrics.ascent + fill.fontSpacing * index
        drawText(line, label.position.x, baseline, outline)
        drawText(line, label.position.x, baseline, fill)
    }
}

private fun linePaint(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color
    style = Paint.Style.STROKE
    strokeWidth = width
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
}

private fun labelPaint(label: ImageAnnotation.Label) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = label.color
    textSize = label.size
    typeface = Typeface.DEFAULT_BOLD
}

internal fun isLight(color: Int): Boolean {
    val red = (color shr 16 and 0xFF) / 255f
    val green = (color shr 8 and 0xFF) / 255f
    val blue = (color and 0xFF) / 255f
    return 0.299f * red + 0.587f * green + 0.114f * blue > 0.6f
}

/** [source] with [annotations] drawn onto a copy at its own resolution. */
internal fun renderAnnotatedImage(source: Bitmap, annotations: List<ImageAnnotation>): Bitmap {
    val result = source.copy(Bitmap.Config.ARGB_8888, true)
    Canvas(result).apply { annotations.forEach { drawAnnotation(it) } }
    return result
}

/** Drops points closer than [minimumDistance] to the previous one, keeping strokes light. */
internal fun List<Offset>.plusPoint(point: Offset, minimumDistance: Float): List<Offset> {
    val last = lastOrNull() ?: return listOf(point)
    return if (hypot(point.x - last.x, point.y - last.y) < minimumDistance) this else this + point
}

private const val HIGHLIGHTER_ALPHA = 0x66
private const val ARROW_HEAD_DEGREES = 28f
