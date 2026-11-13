package org.qownnotes.mobile.markdown

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.text.Spannable
import android.text.method.ArrowKeyMovementMethod
import android.text.style.ClickableSpan
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.TextView
import io.noties.markwon.image.AsyncDrawableSpan
import kotlin.math.hypot

/**
 * Keeps rendered note text selectable while its links stay tappable.
 *
 * Selection is the base behavior here and link clicking is added on top of it, rather than the
 * other way round. A `TextView` only offers selection when its movement method reports
 * [canSelectArbitrarily], which `LinkMovementMethod` does not, and `LinkMovementMethod` also drops
 * the current selection whenever a touch lands outside a link, which is exactly what lifting a
 * finger after a long press does.
 */
internal class SelectableLinkMovementMethod : ArrowKeyMovementMethod() {
    private var downX = 0f
    private var downY = 0f
    private var moved = false

    override fun canSelectArbitrarily(): Boolean = true

    override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                moved = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> moved = true
            MotionEvent.ACTION_MOVE -> {
                if (hypot(event.x - downX, event.y - downY) >
                    ViewConfiguration.get(widget.context).scaledTouchSlop
                ) {
                    moved = true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isTap(widget, event)) {
                    imageAt(widget, buffer, event)?.let { image ->
                        // Only a decoded image opens; a placeholder or blocked image swallows the
                        // tap so it cannot fall through to a link wrapping the image.
                        openImage(buffer, image)
                        return true
                    }
                    taskAt(widget, buffer, event)?.let { task ->
                        task.toggle(task.index)
                        return true
                    }
                    linkAt(widget, buffer, event)?.let { link ->
                        link.onClick(widget)
                        return true
                    }
                }
            }
        }
        return super.onTouchEvent(widget, buffer, event)
    }

    /**
     * Opens [tapped] together with the note's other loaded images, in reading order, so the
     * viewer can move between them.
     */
    private fun openImage(buffer: Spannable, tapped: NoteImageSpan) {
        if (tapped.bitmap() == null) return
        val spans = buffer.getSpans(0, buffer.length, NoteImageSpan::class.java)
            .sortedWith(compareBy({ buffer.getSpanStart(it) }, { buffer.getSpanEnd(it) }))
        val images = spans.mapNotNull { span ->
            span.bitmap()?.let { bitmap ->
                span to NoteImage(
                    bitmap,
                    buffer.subSequence(buffer.getSpanStart(span), buffer.getSpanEnd(span))
                        .toString().trim(),
                    span.sourceIndex()
                )
            }
        }
        NoteImage.gallery(images.map { it.second })
        images.first { it.first === tapped }.second.let(tapped.open)
    }

    private fun NoteImageSpan.bitmap(): Bitmap? = (image.drawable.result as? BitmapDrawable)?.bitmap

    private fun taskAt(widget: TextView, buffer: Spannable, event: MotionEvent): TaskToggleSpan? {
        val layout = widget.layout ?: return null
        val x = event.x - widget.totalPaddingLeft + widget.scrollX
        val y = event.y - widget.totalPaddingTop + widget.scrollY
        if (y < 0 || y > layout.height) return null
        val line = layout.getLineForVertical(y.toInt())
        val task = buffer.getSpans(
            layout.getLineStart(line),
            layout.getLineEnd(line),
            TaskToggleSpan::class.java
        ).filter { layout.getLineForOffset(buffer.getSpanStart(it)) == line }
            .maxByOrNull(buffer::getSpanStart) ?: return null
        val direction = layout.getParagraphDirection(line)
        // The checkbox sits in the leading margin directly before the task's text. Nested items
        // stack a leading margin per list level, so the paragraph edge (`getLineLeft` /
        // `getLineRight`) stays at the outermost margin and cannot locate a nested checkbox. The
        // horizontal position of the task's first character includes every level's margin, which
        // places the checkbox of any nesting level half a margin before it.
        val textStart = layout.getPrimaryHorizontal(buffer.getSpanStart(task))
        val center = if (direction > 0) {
            textStart - task.leadingMargin / 2F
        } else {
            textStart + task.leadingMargin / 2F
        }
        val touchWidth = maxOf(
            task.leadingMargin.toFloat(),
            48F * widget.resources.displayMetrics.density
        )
        val start = center - touchWidth / 2F
        val end = center + touchWidth / 2F
        return task.takeIf { x in start..end }
    }

    /**
     * Only a short, stationary touch opens a link. A press long enough to start a selection and a
     * drag that scrolls the note must leave the link alone, and a touch that ends while text is
     * selected belongs to the selection.
     */
    private fun isTap(widget: TextView, event: MotionEvent): Boolean {
        if (moved) return false
        if (widget.hasSelection()) return false
        if (event.eventTime - event.downTime >=
            ViewConfiguration.getLongPressTimeout()
        ) {
            return false
        }
        val slop = ViewConfiguration.get(widget.context).scaledTouchSlop
        return hypot(event.x - downX, event.y - downY) <= slop
    }

    private fun linkAt(widget: TextView, buffer: Spannable, event: MotionEvent): ClickableSpan? {
        val layout = widget.layout ?: return null
        val x = event.x - widget.totalPaddingLeft + widget.scrollX
        val y = event.y - widget.totalPaddingTop + widget.scrollY
        if (y < 0 || y > layout.height) return null
        val line = layout.getLineForVertical(y.toInt())
        // Touching past either end of a line must not activate the link that happens to end it.
        if (x < layout.getLineLeft(line) || x > layout.getLineRight(line)) return null
        val offset = layout.getOffsetForHorizontal(line, x)
        return buffer.getSpans(offset, offset, ClickableSpan::class.java).firstOrNull()
    }

    private fun imageAt(widget: TextView, buffer: Spannable, event: MotionEvent): NoteImageSpan? {
        val layout = widget.layout ?: return null
        val x = event.x - widget.totalPaddingLeft + widget.scrollX
        val y = event.y - widget.totalPaddingTop + widget.scrollY
        if (y < 0 || y >= layout.height) return null
        val line = layout.getLineForVertical(y.toInt())
        return buffer.getSpans(
            layout.getLineStart(line),
            layout.getLineEnd(line),
            NoteImageSpan::class.java
        ).firstOrNull { image ->
            val start = buffer.getSpanStart(image)
            val end = buffer.getSpanEnd(image)
            val left = minOf(layout.getPrimaryHorizontal(start), layout.getPrimaryHorizontal(end))
            val bounds = image.image.drawable.bounds
            val bottom = layout.getLineBottom(line).toFloat()
            x >= left && x < left + bounds.width() &&
                y >= bottom - bounds.height() && y < bottom
        }
    }
}

internal class NoteImageSpan(
    val image: AsyncDrawableSpan,
    val sourceIndex: () -> Int?,
    val open: (NoteImage) -> Unit
)

/**
 * A rendered note image the reader tapped. [bitmap] is the bounded decode already shown in the
 * note, so viewing it needs no new request and never exposes the image's source URL.
 *
 * [sourceIndex] is the image's index in `inlineMarkdownImages` of the rendered Markdown, or null
 * when the image cannot be matched to its source with certainty, such as a reference-style image.
 */
class NoteImage(val bitmap: Bitmap, val description: String, val sourceIndex: Int? = null) {
    /**
     * The note's loaded images in reading order, including this one, for moving between them in
     * the viewer. Images that are still loading or were blocked are left out.
     */
    var gallery: List<NoteImage> = listOf(this)
        private set

    companion object {
        /** Makes [images] one gallery, in the given order, and returns it. */
        fun gallery(images: List<NoteImage>): List<NoteImage> {
            val gallery = images.toList()
            gallery.forEach { it.gallery = gallery }
            return gallery
        }
    }
}
