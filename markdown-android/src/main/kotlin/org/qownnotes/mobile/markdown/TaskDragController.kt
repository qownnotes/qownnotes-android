package org.qownnotes.mobile.markdown

import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.text.Layout
import android.text.Spanned
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.PopupWindow
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatTextView
import kotlin.math.abs
import kotlin.math.roundToInt

/** Colors of the task drag handles, the lifted task, and the drop indicator. */
data class TaskDragColors(
    /** The grip dots of a handle at rest. */
    val handle: Int,
    /** The active handle, the drop indicator, and the outline of the lifted task. */
    val accent: Int,
    /** The opaque background of the lifted task. */
    val surface: Int,
    val destructive: Int = accent
)

/**
 * Scrolls the container of the rendered note while a task is dragged near its edges.
 *
 * [viewportInWindow] is the visible area of the note in window coordinates, and [scrollBy] scrolls
 * the note by the given pixels and returns how far it actually scrolled.
 */
class TaskDragAutoScroll(val viewportInWindow: () -> Rect?, val scrollBy: (Float) -> Float)

private class RenderedTask(val index: Int, val start: Int, val end: Int, val margin: Int)

private class TaskDrag(
    val task: RenderedTask,
    val group: List<Int>,
    val downY: Float,
    var eventY: Float,
    var rawY: Float,
    var scrolledSinceEvent: Float = 0F,
    var target: Int
)

/**
 * Lets the reader reorder sibling task-list items of the rendered note by dragging their handles.
 *
 * Handles sit in a strip at the end of each movable task's first line. Dragging one lifts the task
 * with all of its nested items, and releasing it reports the sibling whose place it takes.
 */
internal class TaskDragController(private val view: AppCompatTextView) : View.OnTouchListener {
    private val density = view.resources.displayMetrics.density
    private val stripWidth = (36 * density).roundToInt()
    private val overlay = TaskDragOverlay()
    private var tasks: Map<Int, RenderedTask> = emptyMap()
    private var groupOf: Map<Int, List<Int>> = emptyMap()
    private var onMove: ((Int, Int) -> Unit)? = null
    private var onDelete: ((Int) -> Unit)? = null
    private var deletable = emptySet<Int>()
    private var downX = 0F
    private var downY = 0F
    private var selected: RenderedTask? = null
    private var actions: PopupWindow? = null
    private var autoScroll: TaskDragAutoScroll? = null
    private var colors = TaskDragColors(0, 0, 0)
    private var drag: TaskDrag? = null
    private var lift = 0F
    private var liftAnimator: ValueAnimator? = null
    private val autoScroller = object : Runnable {
        override fun run() {
            if (drag == null) return
            scrollNearEdge()
            view.postOnAnimation(this)
        }
    }

    init {
        view.overlay.add(overlay)
        overlay.setBounds(0, 0, view.width, view.height)
        view.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            overlay.setBounds(0, 0, right - left, bottom - top)
        }
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) {
                actions?.dismiss()
                cancelDrag()
            }
        })
    }

    fun attach(
        markdown: String,
        sources: RenderedTaskSources?,
        onMove: ((Int, Int) -> Unit)?,
        onDelete: ((Int) -> Unit)?,
        colors: TaskDragColors,
        autoScroll: TaskDragAutoScroll?
    ) {
        cancelDrag()
        actions?.dismiss()
        this.onMove = onMove
        this.onDelete = onDelete
        deletable = if (onDelete == null) emptySet() else deletableTaskIndices(markdown)
        this.colors = colors
        this.autoScroll = autoScroll
        val text = view.text as? Spanned
        val spans = text?.renderedTaskSpans().orEmpty()
        if ((onMove == null && onDelete == null) || sources == null || text == null ||
            spans.size != sources.sourceIndices.size
        ) {
            tasks = emptyMap()
            groupOf = emptyMap()
        } else {
            tasks = spans.mapIndexedNotNull { renderedIndex, span ->
                sources.sourceIndices[renderedIndex]?.let { index ->
                    RenderedTask(
                        index,
                        text.getSpanStart(span),
                        text.getSpanEnd(span),
                        span.getLeadingMargin(true)
                    )
                }
            }.associateBy(RenderedTask::index)
            // A list is offered for reordering only when the lines that would be moved are
            // exactly the tasks the renderer shows as siblings of that list.
            groupOf = taskListStructure(markdown).groups
                .filter { onMove != null }
                .filter { it in sources.siblingGroups }
                .flatMap { group -> group.map { it to group } }
                .toMap()
        }
        view.setOnTouchListener(if (tasks.isEmpty()) null else this)
        view.setOnLongClickListener(
            if (onDelete == null ||
                tasks.isEmpty()
            ) {
                null
            } else {
                View.OnLongClickListener {
                    val layout = view.layout ?: return@OnLongClickListener false
                    val task = tasks.values.filter { it.index in deletable }
                        .filter { blockBounds(it, layout).contains(downX, downY) }
                        .maxByOrNull { it.start } ?: return@OnLongClickListener false
                    showActions(task)
                    true
                }
            }
        )
        val endPadding = if (groupOf.isEmpty()) 0 else stripWidth
        if (view.paddingEnd != endPadding) {
            view.setPaddingRelative(
                view.paddingStart,
                view.paddingTop,
                endPadding,
                view.paddingBottom
            )
        }
        overlay.invalidateSelf()
    }

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                val task = handleAt(event.x, event.y) ?: return false
                val group = groupOf[task.index] ?: return false
                drag = TaskDrag(
                    task = task,
                    group = group,
                    downY = event.y,
                    eventY = event.y,
                    rawY = event.rawY,
                    target = task.index
                )
                view.parent?.requestDisallowInterceptTouchEvent(true)
                view.performHapticFeedback(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        HapticFeedbackConstants.GESTURE_START
                    } else {
                        HapticFeedbackConstants.LONG_PRESS
                    }
                )
                animateLift(1F)
                view.postOnAnimation(autoScroller)
                overlay.invalidateSelf()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val current = drag ?: return false
                current.eventY = event.y
                current.rawY = event.rawY
                current.scrolledSinceEvent = 0F
                updateTarget()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val current = drag ?: return false
                val commit = event.actionMasked == MotionEvent.ACTION_UP
                finishDrag()
                if (commit && current.target != current.task.index) {
                    view.performHapticFeedback(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            HapticFeedbackConstants.CONFIRM
                        } else {
                            HapticFeedbackConstants.VIRTUAL_KEY
                        }
                    )
                    onMove?.invoke(current.task.index, current.target)
                }
                return true
            }
        }
        return drag != null
    }

    private fun showActions(task: RenderedTask) {
        if (!view.isAttachedToWindow) return
        actions?.dismiss()
        selected = task
        overlay.invalidateSelf()
        val row = LinearLayout(view.context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = GradientDrawable().apply {
                setColor(this@TaskDragController.colors.surface)
                cornerRadius = 20 * density
                setStroke(dp(1), withAlpha(this@TaskDragController.colors.accent, 55))
            }
        }
        val popup = PopupWindow(
            row,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(row.background)
            row.background = null
            elevation = 8 * density
            isOutsideTouchable = true
            setOnDismissListener {
                selected = null
                actions = null
                overlay.invalidateSelf()
            }
        }
        fun button(label: Int, color: Int, action: () -> Unit) {
            row.addView(
                AppCompatButton(view.context).apply {
                    text = view.context.getString(label)
                    isAllCaps = false
                    textSize = 14F
                    setTextColor(color)
                    if (label == R.string.task_delete_block) {
                        setCompoundDrawablesRelativeWithIntrinsicBounds(
                            R.drawable.ic_task_delete,
                            0,
                            0,
                            0
                        )
                        compoundDrawableTintList = ColorStateList.valueOf(color)
                        compoundDrawablePadding = dp(8)
                    }
                    minWidth = dp(48)
                    minimumWidth = dp(48)
                    minHeight = dp(48)
                    minimumHeight = dp(48)
                    setPadding(dp(16), 0, dp(16), 0)
                    background =
                        RippleDrawable(
                            ColorStateList.valueOf(withAlpha(color, 35)),
                            GradientDrawable().apply {
                                setColor(withAlpha(color, 12))
                                cornerRadius = 16 * density
                            },
                            null
                        )
                    setOnClickListener {
                        popup.dismiss()
                        action()
                    }
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)).apply {
                    marginStart = dp(2)
                    marginEnd = dp(2)
                }
            )
        }
        button(R.string.task_delete_block, colors.destructive) { onDelete?.invoke(task.index) }
        button(R.string.task_cancel, colors.accent) { }
        row.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val visible = Rect().also(view::getWindowVisibleDisplayFrame)
        val location = IntArray(2).also(view::getLocationOnScreen)
        val bounds = blockBounds(task, view.layout)
        val x = (location[0] + bounds.centerX() - row.measuredWidth / 2).roundToInt()
            .coerceIn(visible.left, maxOf(visible.left, visible.right - row.measuredWidth))
        val above = location[1] + downY.toInt() - row.measuredHeight - dp(12)
        val y = (if (above >= visible.top) above else location[1] + downY.toInt() + dp(12))
            .coerceIn(visible.top, maxOf(visible.top, visible.bottom - row.measuredHeight))
        actions = popup
        popup.showAtLocation(view, Gravity.TOP or Gravity.LEFT, x, y)
    }

    private fun dp(value: Int) = (value * density).roundToInt()

    private fun cancelDrag() {
        if (drag != null) finishDrag()
    }

    private fun finishDrag() {
        drag = null
        view.removeCallbacks(autoScroller)
        view.parent?.requestDisallowInterceptTouchEvent(false)
        liftAnimator?.cancel()
        lift = 0F
        overlay.invalidateSelf()
    }

    private fun animateLift(to: Float) {
        liftAnimator?.cancel()
        liftAnimator = ValueAnimator.ofFloat(lift, to).apply {
            duration = 160
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                lift = it.animatedValue as Float
                overlay.invalidateSelf()
            }
            start()
        }
    }

    private fun scrollNearEdge() {
        val current = drag ?: return
        val scroller = autoScroll ?: return
        val viewport = scroller.viewportInWindow() ?: return
        val edge = 64 * density
        val maxSpeed = 18 * density
        val speed = when {
            current.rawY < viewport.top + edge ->
                -maxSpeed * ((viewport.top + edge - current.rawY) / edge).coerceAtMost(1F)
            current.rawY > viewport.bottom - edge ->
                maxSpeed * ((current.rawY - viewport.bottom + edge) / edge).coerceAtMost(1F)
            else -> 0F
        }
        if (speed == 0F) return
        val scrolled = scroller.scrollBy(speed)
        if (scrolled != 0F) {
            current.scrolledSinceEvent += scrolled
            updateTarget()
        }
    }

    private fun fingerOffsetOf(drag: TaskDrag): Float =
        drag.eventY + drag.scrolledSinceEvent - drag.downY

    /**
     * How far the lifted task is drawn from its own place. It stays within the note, which the
     * finger may leave to reach the first or last place of a list at the note's edge.
     */
    private fun offsetOf(drag: TaskDrag, layout: Layout): Float {
        val bounds = blockBounds(drag.task, layout)
        return fingerOffsetOf(drag).coerceIn(-bounds.top, view.height - bounds.bottom)
    }

    private fun updateTarget() {
        val current = drag ?: return
        val layout = view.layout ?: return
        val bounds = blockBounds(current.task, layout)
        val center = bounds.centerY() + fingerOffsetOf(current)
        val others = current.group.filter { it != current.task.index }
        val position = others.count { other ->
            blockBounds(tasks.getValue(other), layout).centerY() < center
        }
        val target = current.group[position]
        if (target != current.target) {
            current.target = target
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        overlay.invalidateSelf()
    }

    private fun handleAt(x: Float, y: Float): RenderedTask? {
        val layout = view.layout ?: return null
        val inStrip = if (isRtl()) {
            x <= stripWidth + 8 * density
        } else {
            x >= view.width - stripWidth - 8 * density
        }
        if (!inStrip) return null
        val reach = 24 * density
        return groupOf.keys.map(tasks::getValue)
            .map { it to handleCenterY(it, layout) }
            .filter { (task, center) ->
                val line = layout.getLineForOffset(task.start)
                val halfHeight = maxOf(
                    reach,
                    (layout.getLineBottom(line) - layout.getLineTop(line)) / 2F
                )
                abs(y - center) <= halfHeight
            }
            .minByOrNull { (_, center) -> abs(y - center) }
            ?.first
    }

    private fun isRtl() = view.layoutDirection == View.LAYOUT_DIRECTION_RTL

    private fun handleCenterX(): Float =
        if (isRtl()) stripWidth / 2F else view.width - stripWidth / 2F

    private fun handleCenterY(task: RenderedTask, layout: Layout): Float {
        val line = layout.getLineForOffset(task.start)
        return view.totalPaddingTop + (layout.getLineTop(line) + layout.getLineBottom(line)) / 2F
    }

    /** The area of a task and its nested items, from its checkbox to the handle strip. */
    private fun blockBounds(task: RenderedTask, layout: Layout): RectF {
        val firstLine = layout.getLineForOffset(task.start)
        val lastLine = layout.getLineForOffset(maxOf(task.start, task.end - 1))
        val textStart = layout.getPrimaryHorizontal(task.start) + view.totalPaddingLeft
        val top = view.totalPaddingTop + layout.getLineTop(firstLine).toFloat()
        val bottom = view.totalPaddingTop + layout.getLineBottom(lastLine).toFloat()
        return if (isRtl()) {
            RectF(0F, top, textStart + task.margin, bottom)
        } else {
            RectF(textStart - task.margin, top, view.width.toFloat(), bottom)
        }
    }

    /** The part of [block] that holds the text of the task and its nested items. */
    private fun contentBounds(task: RenderedTask, layout: Layout, block: RectF): RectF {
        val firstLine = layout.getLineForOffset(task.start)
        val lastLine = layout.getLineForOffset(maxOf(task.start, task.end - 1))
        val padding = 12 * density
        return if (isRtl()) {
            val left = (firstLine..lastLine).minOf(layout::getLineLeft) + view.totalPaddingLeft
            RectF(maxOf(stripWidth.toFloat(), left - padding), block.top, block.right, block.bottom)
        } else {
            val right = (firstLine..lastLine).maxOf(layout::getLineRight) + view.totalPaddingLeft
            val limit = view.width - stripWidth.toFloat()
            RectF(block.left, block.top, minOf(limit, right + padding), block.bottom)
        }
    }

    private inner class TaskDragOverlay : Drawable() {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val dashed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5F * density
            pathEffect = DashPathEffect(floatArrayOf(6 * density, 4 * density), 0F)
        }

        override fun draw(canvas: Canvas) {
            val layout = view.layout ?: return
            selected?.let { task ->
                val bounds = blockBounds(task, layout).apply { inset(2 * density, 0F) }
                fill.color = withAlpha(colors.accent, 24)
                canvas.drawRoundRect(bounds, 10 * density, 10 * density, fill)
                stroke.color = withAlpha(colors.accent, 130)
                stroke.strokeWidth = density
                canvas.drawRoundRect(bounds, 10 * density, 10 * density, stroke)
            }
            val current = drag
            groupOf.keys.forEach { index ->
                if (index !=
                    current?.task?.index
                ) {
                    drawHandle(canvas, tasks.getValue(index), layout, false)
                }
            }
            if (current != null) drawDrag(canvas, current, layout)
        }

        private fun drawDrag(canvas: Canvas, drag: TaskDrag, layout: Layout) {
            val origin = blockBounds(drag.task, layout)
            val radius = 12 * density
            val place = RectF(origin).apply { inset(2 * density, 0F) }
            // The task's own place fades and becomes an outlined slot it can return to.
            fill.color = withAlpha(colors.surface, (200 * lift).toInt())
            canvas.drawRoundRect(place, radius, radius, fill)
            fill.color = withAlpha(colors.accent, (20 * lift).toInt())
            canvas.drawRoundRect(place, radius, radius, fill)
            dashed.color = withAlpha(colors.accent, (110 * lift).toInt())
            canvas.drawRoundRect(place, radius, radius, dashed)

            val offset = offsetOf(drag, layout)
            // The card only covers the task's own text, so that the drop indicator stays visible
            // beside it, and the grip keeps following the finger in the handle column.
            val card = contentBounds(drag.task, layout, origin).apply {
                inset(-6 * density, -4 * density)
                offset(0F, offset)
            }
            val scale = 1F + 0.02F * lift
            canvas.save()
            canvas.scale(scale, scale, card.centerX(), card.centerY())
            fill.color = colors.surface
            fill.setShadowLayer(
                14 * density * lift,
                0F,
                5 * density * lift,
                withAlpha(0xFF000000.toInt(), (90 * lift).toInt())
            )
            canvas.drawRoundRect(card, radius, radius, fill)
            fill.clearShadowLayer()
            fill.color = withAlpha(colors.accent, 22)
            canvas.drawRoundRect(card, radius, radius, fill)
            stroke.strokeWidth = 1.5F * density
            stroke.color = withAlpha(colors.accent, (160 * lift).toInt())
            canvas.drawRoundRect(card, radius, radius, stroke)
            canvas.save()
            canvas.clipRect(card)
            canvas.translate(view.totalPaddingLeft.toFloat(), view.totalPaddingTop + offset)
            layout.draw(canvas)
            canvas.restore()
            canvas.restore()
            canvas.save()
            canvas.translate(0F, offset)
            drawHandle(canvas, drag.task, layout, true)
            canvas.restore()
            // Drawn last, so the lifted task can never hide where it is going to be placed.
            if (drag.target != drag.task.index) drawDropIndicator(canvas, drag, origin, layout)
        }

        private fun drawDropIndicator(
            canvas: Canvas,
            drag: TaskDrag,
            origin: RectF,
            layout: Layout
        ) {
            val target = blockBounds(tasks.getValue(drag.target), layout)
            val movesDown = drag.group.indexOf(drag.target) > drag.group.indexOf(drag.task.index)
            val gap = 2 * density
            val y = if (movesDown) target.bottom + gap else target.top - gap
            val dot = 5F * density
            val start = if (isRtl()) origin.right - dot - gap else origin.left + dot + gap
            val end = if (isRtl()) stripWidth / 2F else view.width - stripWidth / 2F
            val lineStart = if (isRtl()) start - dot else start + dot
            stroke.strokeCap = Paint.Cap.ROUND
            // A halo in the background color separates the indicator from the text it crosses.
            stroke.color = withAlpha(colors.surface, 230)
            stroke.strokeWidth = 8 * density
            canvas.drawLine(lineStart, y, end, y, stroke)
            fill.color = withAlpha(colors.surface, 230)
            canvas.drawCircle(start, y, dot + 2 * density, fill)
            stroke.color = colors.accent
            stroke.strokeWidth = 4 * density
            canvas.drawLine(lineStart, y, end, y, stroke)
            fill.color = colors.surface
            canvas.drawCircle(start, y, dot, fill)
            stroke.strokeWidth = 3 * density
            canvas.drawCircle(start, y, dot - 0.5F * density, stroke)
        }

        /**
         * A grip of three columns and two rows of dots on a soft rounded pill. The pill fits its
         * line, so the handles of a dense list never touch each other.
         */
        private fun drawHandle(
            canvas: Canvas,
            task: RenderedTask,
            layout: Layout,
            active: Boolean
        ) {
            val centerX = handleCenterX()
            val centerY = handleCenterY(task, layout)
            val line = layout.getLineForOffset(task.start)
            val lineHeight = (layout.getLineBottom(line) - layout.getLineTop(line)).toFloat()
            val halfHeight = ((lineHeight - 4 * density) / 2F).coerceIn(6 * density, 11 * density)
            val halfWidth = 12 * density
            val pill = RectF(
                centerX - halfWidth,
                centerY - halfHeight,
                centerX + halfWidth,
                centerY + halfHeight
            )
            fill.color = if (active) colors.accent else withAlpha(colors.accent, 28)
            canvas.drawRoundRect(pill, halfHeight, halfHeight, fill)
            fill.color = if (active) colors.surface else withAlpha(colors.handle, 190)
            val dot = 1.6F * density
            val spacing = 5F * density
            for (column in -1..1) {
                for (row in -1..1 step 2) {
                    canvas.drawCircle(
                        centerX + column * spacing,
                        centerY + row * spacing / 2F,
                        dot,
                        fill
                    )
                }
            }
        }

        override fun setAlpha(alpha: Int) = Unit

        override fun setColorFilter(colorFilter: ColorFilter?) = Unit

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}

private fun withAlpha(color: Int, alpha: Int): Int =
    (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
