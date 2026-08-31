package org.qownnotes.mobile.markdown

import android.content.Context
import androidx.appcompat.widget.AppCompatTextView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin

class MarkdownRenderer(context: Context) {
    private val markwon =
        Markwon.builder(context.applicationContext)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context.applicationContext))
            .usePlugin(TaskListPlugin.create(context.applicationContext))
            .build()

    fun render(view: AppCompatTextView, markdown: String) {
        markwon.setMarkdown(view, markdown.withoutFrontmatter())
    }
}

internal fun String.withoutFrontmatter(): String {
    if (!startsWith("---\n")) return this
    val end = indexOf("\n---\n", startIndex = 4)
    return if (end < 0) this else substring(end + 5)
}
