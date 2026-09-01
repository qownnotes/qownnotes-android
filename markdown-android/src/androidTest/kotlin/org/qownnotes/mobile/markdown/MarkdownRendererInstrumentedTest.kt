package org.qownnotes.mobile.markdown

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.ContextThemeWrapper
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.noties.markwon.core.spans.LinkSpan
import io.noties.markwon.image.AsyncDrawableSpan
import io.noties.markwon.image.ImageItem
import io.noties.markwon.image.SchemeHandler
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun encryptedPayloadIsRemovedBeforeRendering() {
        lateinit var view: AppCompatTextView

        instrumentation.runOnMainSync {
            view = textView()
            MarkdownRenderer(view.context).render(
                view,
                """
                    # Visible
                    <!-- BEGIN ENCRYPTED TEXT --
                    ciphertext [[Secret]] ![tracker](https://example.com/tracker.png)
                    -- END ENCRYPTED TEXT -->
                """.trimIndent()
            )
        }

        val text = view.text as Spanned
        assertTrue(text.contains("Encrypted content is locked"))
        assertTrue(!text.contains("ciphertext"))
        assertEquals(0, text.getSpans(0, text.length, LinkSpan::class.java).size)
        assertEquals(0, text.getSpans(0, text.length, AsyncDrawableSpan::class.java).size)
    }

    @Test
    fun highlightsSupportedFencedCodeWithoutChangingItsText() {
        lateinit var view: AppCompatTextView

        instrumentation.runOnMainSync {
            view = textView()
            MarkdownRenderer(view.context).render(
                view,
                "```kotlin\nval answer = 42 // highlighted\n```"
            )
        }

        val text = view.text as Spanned
        assertTrue(text.contains("val answer = 42 // highlighted"))
        assertTrue(text.getSpans(0, text.length, ForegroundColorSpan::class.java).isNotEmpty())
    }

    @Test
    fun dispatchesOnlyHttpsImagesToConfiguredHandler() {
        val requested = CountDownLatch(1)
        val requestCount = AtomicInteger()
        lateinit var renderer: MarkdownRenderer
        lateinit var view: AppCompatTextView
        val handler = object : SchemeHandler() {
            override fun supportedSchemes(): Collection<String> = listOf("https")

            override fun handle(raw: String, uri: Uri): ImageItem {
                requestCount.incrementAndGet()
                requested.countDown()
                return ImageItem.withResult(ColorDrawable(Color.BLUE))
            }
        }

        instrumentation.runOnMainSync {
            view = textView()
            renderer = MarkdownRenderer.forTest(view.context, handler)
            renderer.render(
                view,
                "![allowed](https://example.com/image.png)"
            )
        }
        assertFalse(requested.await(250, TimeUnit.MILLISECONDS))

        instrumentation.runOnMainSync {
            renderer.render(
                view,
                "![allowed](https://example.com/image.png) ![blocked](file:///sdcard/private.png)",
                loadRemoteImages = true
            )
        }

        assertTrue(requested.await(5, TimeUnit.SECONDS))
        Thread.sleep(250)
        assertEquals(1, requestCount.get())
    }

    @Test
    fun detectsOnlySafeRemoteImagesOutsideEncryptedNotes() {
        val renderer = MarkdownRenderer(instrumentation.targetContext)

        assertTrue(renderer.hasRemoteImages("![image](https://example.com/image.png)"))
        assertFalse(renderer.hasRemoteImages("![image](http://example.com/image.png)"))
        assertFalse(
            renderer.hasRemoteImages(
                """
            <!-- BEGIN ENCRYPTED TEXT --
            ![image](https://example.com/private.png)
            -- END ENCRYPTED TEXT -->
                """.trimIndent()
            )
        )
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
