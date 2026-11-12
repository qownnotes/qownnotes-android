package org.qownnotes.mobile.markdown

import io.noties.markwon.Markwon
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Image
import org.commonmark.node.Node
import org.commonmark.node.Text
import org.qownnotes.mobile.core.inlineMarkdownImages

/**
 * Where the images rendered into the note text come from in its Markdown source.
 *
 * A lexical scan also finds image syntax inside code, and reference-style images have no inline
 * syntax at all, so counting cannot match rendered images to source. Like the task source map,
 * every inline image in the source is tagged and the renderer's own parser reports which tag each
 * of its images carries. Images inside tables are drawn by the table rows, not as spans of the
 * note text, so they are left out just as they are left out of the rendered spans.
 *
 * Returns, for every rendered image in order, its index in [inlineMarkdownImages] of [markdown],
 * or null where the image has no inline source.
 */
internal fun Markwon.renderedImageSources(markdown: String): List<Int?> {
    val tagged = StringBuilder(markdown)
    inlineMarkdownImages(markdown).withIndex().reversed().forEach { (index, image) ->
        tagged.insert(image.descriptionStart, "$SOURCE_TAG_START$index$SOURCE_TAG_END")
    }
    val sources = mutableListOf<Int?>()
    parse(tagged.toString().withoutFrontmatter().withInternalLinks()).accept(
        object : AbstractVisitor() {
            override fun visit(image: Image) {
                if (!image.isInTable()) sources += image.sourceTag()
                visitChildren(image)
            }
        }
    )
    return sources
}

private fun Node.isInTable(): Boolean {
    var node = parent
    while (node != null) {
        if (node is TableBlock) return true
        node = node.parent
    }
    return false
}

private fun Image.sourceTag(): Int? {
    var child = firstChild
    while (child != null) {
        // The tag starts the description, so it is in the description's first text.
        if (child is Text) return SOURCE_TAG.find(child.literal)?.groupValues?.get(1)?.toInt()
        child = child.firstChild ?: child.next
    }
    return null
}

private const val SOURCE_TAG_START = '\uE002'
private const val SOURCE_TAG_END = '\uE003'
private val SOURCE_TAG = Regex("^$SOURCE_TAG_START(\\d+)$SOURCE_TAG_END")
