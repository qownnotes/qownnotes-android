package org.qownnotes.mobile.markdown

import android.text.Spannable
import android.text.Spanned
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.UpdateAppearance
import android.widget.TextView
import org.qownnotes.mobile.core.findTextMatches

/**
 * Colors for the matches of a note search. The match the reader is currently on is drawn
 * differently from the others so that moving through the matches is visible.
 */
data class NoteSearchColors(
    val matchBackground: Int,
    val matchText: Int,
    val currentBackground: Int,
    val currentText: Int
)

/**
 * Highlights every occurrence of [query] in the text that [view] already displays and returns the
 * matched ranges in document order. [currentMatch] indexes those ranges; an index outside them
 * simply leaves every match drawn in the ordinary match colors.
 *
 * The search runs over the displayed text rather than over the Markdown source, because that is
 * what the reader is looking at. Rendering removes the source markers and shifts every offset, so
 * source offsets cannot be used to mark up the rendered note.
 *
 * Only the spans added here are removed, so Markdown rendering and editor highlighting survive a
 * search. A blank query removes the highlights and reports no matches, which is also how the
 * caller clears them.
 */
fun highlightNoteSearchMatches(
    view: TextView,
    query: String,
    currentMatch: Int,
    colors: NoteSearchColors
): List<IntRange> {
    val text = view.text as? Spannable ?: return emptyList()
    text.getSpans(0, text.length, NoteSearchHighlightSpan::class.java).forEach(text::removeSpan)
    val matches = findTextMatches(text, query)
    matches.forEachIndexed { index, match ->
        val current = index == currentMatch
        text.setSpan(
            NoteSearchHighlightSpan(
                backgroundColor = if (current) colors.currentBackground else colors.matchBackground,
                textColor = if (current) colors.currentText else colors.matchText
            ),
            match.first,
            match.last + 1,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }
    // A rendered note is held in a `SpannableString`, which has no watchers and therefore does not
    // tell the view that its appearance changed. Without this the matches are counted but stay
    // invisible until something else repaints the view.
    view.invalidate()
    return matches
}

/**
 * Vertical offset of [match] inside [view], for scrolling it into sight. Returns `null` while the
 * view has not been laid out for its current text, because no offset can be resolved before then.
 */
fun noteSearchMatchTop(view: TextView, match: IntRange): Int? {
    val layout = view.layout ?: return null
    if (match.first !in 0..view.text.length) return null
    return layout.getLineTop(layout.getLineForOffset(match.first))
}

/**
 * Marks a search match. It is a distinct span type so the highlights can be removed again without
 * disturbing the Markdown spans they are drawn over, and it sets both colors so a match stays
 * readable on top of the color it is given.
 */
internal class NoteSearchHighlightSpan(val backgroundColor: Int, val textColor: Int) :
    CharacterStyle(),
    UpdateAppearance {
    override fun updateDrawState(textPaint: TextPaint) {
        textPaint.bgColor = backgroundColor
        textPaint.color = textColor
    }
}
