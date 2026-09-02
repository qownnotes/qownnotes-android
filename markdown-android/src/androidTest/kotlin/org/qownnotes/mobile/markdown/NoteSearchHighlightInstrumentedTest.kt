package org.qownnotes.mobile.markdown

import android.graphics.Color
import android.text.Spanned
import android.view.ContextThemeWrapper
import android.view.View.MeasureSpec
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.noties.markwon.core.spans.HeadingSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteSearchHighlightInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val colors = NoteSearchColors(
        matchBackground = Color.YELLOW,
        matchText = Color.BLACK,
        currentBackground = Color.BLUE,
        currentText = Color.WHITE
    )

    @Test
    fun highlightsEveryMatchAndDistinguishesTheCurrentOne() {
        lateinit var view: AppCompatTextView
        lateinit var matches: List<IntRange>

        instrumentation.runOnMainSync {
            view = textView("Salt the water, add salt.")
            matches = highlightNoteSearchMatches(view, "salt", currentMatch = 1, colors = colors)
        }

        assertEquals(listOf(0 until 4, 20 until 24), matches)
        val highlights = view.highlights()
        assertEquals(listOf(0, 20), highlights.map(view.spanned()::getSpanStart))
        assertEquals(Color.YELLOW, highlights[0].backgroundColor)
        assertEquals(Color.BLACK, highlights[0].textColor)
        assertEquals(Color.BLUE, highlights[1].backgroundColor)
        assertEquals(Color.WHITE, highlights[1].textColor)
    }

    @Test
    fun searchesTheRenderedTextAndKeepsItsMarkdownSpans() {
        lateinit var view: AppCompatTextView
        lateinit var sourceMatches: List<IntRange>
        lateinit var renderedMatches: List<IntRange>

        instrumentation.runOnMainSync {
            view = textView("")
            MarkdownRenderer(view.context).render(view, "# Recipe\n\nAdd **salt** to taste.")
            sourceMatches =
                highlightNoteSearchMatches(view, "**salt**", currentMatch = 0, colors = colors)
            renderedMatches =
                highlightNoteSearchMatches(view, "salt", currentMatch = 0, colors = colors)
        }

        // The rendered note no longer holds the emphasis markers, so only the word can be found.
        assertEquals(emptyList<IntRange>(), sourceMatches)
        assertEquals(1, renderedMatches.size)
        val match = renderedMatches.single()
        assertEquals("salt", view.text.subSequence(match.first, match.last + 1).toString())
        assertEquals(1, view.spanned().getSpans(0, view.length(), HeadingSpan::class.java).size)
    }

    @Test
    fun blankQueryRemovesTheHighlightsItAddedBefore() {
        lateinit var view: AppCompatTextView
        lateinit var cleared: List<IntRange>

        instrumentation.runOnMainSync {
            view = textView("Salt and salt.")
            highlightNoteSearchMatches(view, "salt", currentMatch = 0, colors = colors)
            cleared = highlightNoteSearchMatches(view, "", currentMatch = 0, colors = colors)
        }

        assertEquals(emptyList<IntRange>(), cleared)
        assertEquals(emptyList<NoteSearchHighlightSpan>(), view.highlights())
    }

    @Test
    fun reportsTheVerticalOffsetOfAMatchOnceTheViewIsLaidOut() {
        lateinit var view: AppCompatTextView
        lateinit var matches: List<IntRange>
        var beforeLayout: Int? = 0

        instrumentation.runOnMainSync {
            view = textView("salt\nfirst\nsecond\nsalt")
            matches = highlightNoteSearchMatches(view, "salt", currentMatch = 0, colors = colors)
            beforeLayout = noteSearchMatchTop(view, matches[0])
            view.measure(
                MeasureSpec.makeMeasureSpec(600, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            )
            view.layout(0, 0, 600, view.measuredHeight)
        }

        assertNull("an unmeasured view has no offsets to report", beforeLayout)
        val first = noteSearchMatchTop(view, matches[0])
        val last = noteSearchMatchTop(view, matches[1])
        assertEquals(0, first)
        assertNotNull(last)
        assertTrue("expected the last match at $last to sit below the first", last!! > 0)
    }

    private fun AppCompatTextView.spanned() = text as Spanned

    private fun AppCompatTextView.highlights(): List<NoteSearchHighlightSpan> =
        spanned().getSpans(0, length(), NoteSearchHighlightSpan::class.java)
            .sortedBy(spanned()::getSpanStart)

    private fun textView(text: String): AppCompatTextView {
        val context = ContextThemeWrapper(
            instrumentation.targetContext,
            androidx.appcompat.R.style.Theme_AppCompat
        )
        return AppCompatTextView(context).apply {
            setText(text, TextView.BufferType.SPANNABLE)
        }
    }
}
