package org.qownnotes.mobile.markdown

import io.noties.markwon.AbstractMarkwonPlugin
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Block
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Node

internal class MarkdownHtmlSanitizerPlugin : AbstractMarkwonPlugin() {
    override fun beforeRender(node: Node) {
        sanitizeMarkdownHtml(node)
    }
}

internal fun sanitizeMarkdownHtml(node: Node) {
    node.accept(
        object : AbstractVisitor() {
            override fun visit(htmlBlock: HtmlBlock) {
                htmlBlock.unlink()
            }

            override fun visit(htmlInline: HtmlInline) {
                if (ACTIVE_HTML_TAG.containsMatchIn(htmlInline.literal)) {
                    htmlInline.parentBlock()?.unlink()
                } else {
                    htmlInline.unlink()
                }
            }
        }
    )
}

private fun Node.parentBlock(): Block? {
    var current = parent
    while (current != null && current !is Block) current = current.parent
    return current as? Block
}

private val ACTIVE_HTML_TAG = Regex(
    "<\\s*/?\\s*(?:script|style|iframe|object|embed|svg|math|video|audio|form)(?:\\s|/?>)",
    RegexOption.IGNORE_CASE
)
