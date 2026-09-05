package org.qownnotes.mobile.markdown

import android.text.Spannable
import android.text.method.ArrowKeyMovementMethod
import android.text.style.ClickableSpan
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.TextView
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

    override fun canSelectArbitrarily(): Boolean = true

    override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
            }
            MotionEvent.ACTION_UP -> {
                if (isTap(widget, event)) {
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
}
