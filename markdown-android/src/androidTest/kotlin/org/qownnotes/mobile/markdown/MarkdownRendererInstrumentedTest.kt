package org.qownnotes.mobile.markdown

import android.text.Spanned
import android.view.ContextThemeWrapper
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.noties.markwon.core.spans.LinkSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.qownnotes.mobile.core.ResolvedNoteLink

@RunWith(AndroidJUnit4::class)
class MarkdownRendererInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun stylesBrokenInternalLinksAndDisablesClicks() {
        lateinit var view: AppCompatTextView

        instrumentation.runOnMainSync {
            view = textView()
            MarkdownRenderer(view.context).render(
                view,
                "[[Missing]] and [Legacy](note://Also_Missing)"
            )
        }

        val text = view.text as Spanned
        assertEquals(2, text.getSpans(0, text.length, BrokenInternalLinkSpan::class.java).size)
        assertEquals(0, text.getSpans(0, text.length, LinkSpan::class.java).size)
    }

    @Test
    fun resolvedInternalLinkDispatchesResolvedDestination() {
        val expected = ResolvedNoteLink("target", "Section")
        var opened: ResolvedNoteLink? = null
        lateinit var view: AppCompatTextView

        instrumentation.runOnMainSync {
            view = textView()
            MarkdownRenderer(view.context).render(
                view = view,
                markdown = "[[Target#Section]]",
                resolveInternalLink = { expected },
                onInternalLink = { opened = it }
            )
            val text = view.text as Spanned
            text.getSpans(0, text.length, LinkSpan::class.java).single().onClick(view)
        }

        assertEquals(expected, opened)
    }

    @Test
    fun findsRenderedHeadingPositionBySlug() {
        lateinit var view: AppCompatTextView
        var headingTop: Int? = null

        instrumentation.runOnMainSync {
            view = textView()
            MarkdownRenderer(view.context).render(
                view,
                "Introductory paragraph before the target.\n\n## Target heading\n\nBody"
            )
            view.measure(
                View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            headingTop = findHeadingTop(view, "target-heading")
        }

        assertNotNull(headingTop)
        assertTrue(headingTop!! > 0)
    }

    private fun textView(): AppCompatTextView {
        val context = ContextThemeWrapper(
            instrumentation.targetContext,
            androidx.appcompat.R.style.Theme_AppCompat
        )
        return AppCompatTextView(context).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }
}
