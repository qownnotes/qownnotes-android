package org.qownnotes.mobile.markdown

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.MarkwonSpansFactory
import io.noties.markwon.MarkwonVisitor
import io.noties.markwon.ext.tasklist.TaskListItem
import io.noties.markwon.ext.tasklist.TaskListSpan
import io.noties.markwon.utils.ParserUtils
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.Paragraph
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import org.commonmark.parser.PostProcessor

internal class IndeterminateTaskListPlugin(context: Context) : AbstractMarkwonPlugin() {
    private val drawable =
        IndeterminateTaskDrawable(context.resolveColor(android.R.attr.textColorLink))

    override fun configureParser(builder: Parser.Builder) {
        builder.postProcessor(IndeterminateTaskPostProcessor())
    }

    override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
        builder.setFactory(IndeterminateTaskListItem::class.java) { configuration, _ ->
            IndeterminateTaskSpan(configuration, drawable)
        }
    }

    override fun configureVisitor(builder: MarkwonVisitor.Builder) {
        builder.on(IndeterminateTaskListItem::class.java) { visitor, item ->
            val start = visitor.length()
            visitor.visitChildren(item)
            visitor.setSpansForNode(item, start)
            if (visitor.hasNext(item)) visitor.ensureNewLine()
        }
    }
}

internal class IndeterminateTaskSpan(configuration: MarkwonConfiguration, drawable: Drawable) :
    TaskListSpan(configuration.theme(), drawable, false)

private class IndeterminateTaskListItem : TaskListItem(false)

private class IndeterminateTaskPostProcessor : PostProcessor {
    override fun process(node: Node): Node {
        node.accept(
            object : AbstractVisitor() {
                override fun visit(listItem: ListItem) {
                    val paragraph = listItem.firstChild as? Paragraph
                    val text = paragraph?.firstChild as? Text
                    val match = text?.literal?.let(INDETERMINATE_TASK::matchEntire)
                    if (paragraph == null || text == null || match == null) {
                        visitChildren(listItem)
                        return
                    }

                    val task = IndeterminateTaskListItem()
                    val taskParagraph = Paragraph()
                    listItem.insertBefore(task)
                    match.groupValues[1].takeIf(String::isNotEmpty)?.let {
                        taskParagraph.appendChild(Text(it))
                    }
                    ParserUtils.moveChildren(taskParagraph, text)
                    task.appendChild(taskParagraph)
                    ParserUtils.moveChildren(task, paragraph)
                    listItem.unlink()
                    visitChildren(task)
                }
            }
        )
        return node
    }
}

private class IndeterminateTaskDrawable(private val color: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color =
            this@IndeterminateTaskDrawable.color
    }

    override fun draw(canvas: Canvas) {
        val side = minOf(bounds.width(), bounds.height()).toFloat()
        val stroke = side / 8F
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        val inset = stroke / 2F
        val box = RectF(inset, inset, side - inset, side - inset)
        canvas.drawRoundRect(box, side / 8F, side / 8F, paint)
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(
            side * .25F,
            side * .44F,
            side * .75F,
            side * .56F,
            stroke / 2F,
            stroke / 2F,
            paint
        )
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

private fun Context.resolveColor(attribute: Int): Int {
    val attributes = obtainStyledAttributes(intArrayOf(attribute))
    return try {
        attributes.getColor(0, 0xFF666666.toInt())
    } finally {
        attributes.recycle()
    }
}

private val INDETERMINATE_TASK = Regex("^\\[-]\\s+(.*)")
