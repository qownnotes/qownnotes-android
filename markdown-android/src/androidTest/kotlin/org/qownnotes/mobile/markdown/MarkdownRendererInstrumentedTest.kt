package org.qownnotes.mobile.markdown

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.SystemClock
import android.text.Selection
import android.text.Spannable
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.appcompat.widget.AppCompatTextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.noties.markwon.core.spans.LinkSpan
import io.noties.markwon.ext.tasklist.TaskListSpan
import io.noties.markwon.image.AsyncDrawableSpan
import io.noties.markwon.image.ImageItem
import io.noties.markwon.image.SchemeHandler
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.qownnotes.mobile.core.InternalNoteLink
import org.qownnotes.mobile.core.ResolvedNoteLink

@RunWith(AndroidJUnit4::class)
class MarkdownRendererInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun sourceHighlightingPreservesTextAndSelection() {
        val source = "---\ntitle: Test\n---\n# Heading\n[[Wiki]] <!-- comment -->"
        val changed = CountDownLatch(1)
        lateinit var view: MarkdownEditText
        lateinit var binding: MarkdownEditorBinding

        instrumentation.runOnMainSync {
            view = MarkdownEditText(textView().context)
            binding = MarkdownEditorBinding(view.context, view) { changed.countDown() }
            view.setText(source)
            view.setSelection(5, 10)
        }

        assertTrue(changed.await(5, TimeUnit.SECONDS))
        Thread.sleep(250)
        instrumentation.runOnMainSync {
            assertEquals(source, view.text.toString())
            assertEquals(5, view.selectionStart)
            assertEquals(10, view.selectionEnd)
            assertEquals(
                3,
                view.text!!.getSpans(0, view.length(), SupplementalSyntaxSpan::class.java).size
            )
            binding.close()
        }
    }

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
    fun renderedNoteTextCanBeSelected() {
        lateinit var view: AppCompatTextView

        instrumentation.runOnMainSync {
            view = textView()
            MarkdownRenderer(view.context).render(view, "# Heading\n\nSelect **this** text.")
            Selection.setSelection(view.text as Spannable, 0, "Heading".length)
        }

        assertTrue(view.isTextSelectable)
        // Selection handles and the copy action are only offered when the movement method allows
        // arbitrary selection, so the rendered note cannot use a plain `LinkMovementMethod`.
        assertTrue(view.movementMethod.canSelectArbitrarily())
        assertTrue(view.hasSelection())
        assertEquals("Heading", view.text.substring(0, view.selectionEnd))
    }

    @Test
    fun tappingAResolvedLinkOpensItWhileTheNoteIsSelectable() {
        val expected = ResolvedNoteLink("target", null)
        var opened: ResolvedNoteLink? = null
        lateinit var view: AppCompatTextView

        instrumentation.runOnMainSync {
            view = laidOutView("[[Target]]", resolve = { expected }, open = { opened = it })
            view.touch(offset = 3, durationMillis = 20)
        }

        assertEquals(expected, opened)
    }

    @Test
    fun tappingARenderedCheckboxDispatchesItsTaskIndex() {
        var toggledTask = -1
        lateinit var view: AppCompatTextView

        instrumentation.runOnMainSync {
            view = textView()
            MarkdownRenderer(view.context).render(
                view = view,
                markdown = "- [ ] first\n- [x] second",
                onTaskToggle = { toggledTask = it }
            )
            view.measure(
                View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            val text = view.text as Spanned
            val task = text.getSpans(0, text.length, TaskToggleSpan::class.java)
                .single { it.index == 1 }
            view.touchTask(task)
        }

        assertEquals(1, toggledTask)
    }

    /** A press that is long enough to start a selection must not also follow the link under it. */
    @Test
    fun pressingALinkLongEnoughToSelectDoesNotOpenIt() {
        var opened: ResolvedNoteLink? = null
        lateinit var view: AppCompatTextView

        instrumentation.runOnMainSync {
            view = laidOutView(
                "[[Target]]",
                resolve = { ResolvedNoteLink("target", null) },
                open = { opened = it }
            )
            view.touch(
                offset = 3,
                durationMillis = ViewConfiguration.getLongPressTimeout().toLong() + 50
            )
        }

        assertNull(opened)
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

    @Test
    fun rendersQOwnNotesIndeterminateTasksWithoutChangingCode() {
        lateinit var view: AppCompatTextView

        instrumentation.runOnMainSync {
            view = textView()
            MarkdownRenderer(view.context).render(
                view,
                """
                    - [ ] open
                    - [x] done
                    - [-] partial
                      - [-] nested
                    * [-] star
                    + [-] plus
                    1. [-] ordered

                    `- [-] inline code`

                    ```text
                    - [-] fenced code
                    ```
                """.trimIndent()
            )
        }

        val text = view.text as Spanned
        assertEquals(7, text.getSpans(0, text.length, TaskListSpan::class.java).size)
        assertEquals(5, text.getSpans(0, text.length, IndeterminateTaskSpan::class.java).size)
        assertTrue(text.contains("- [-] inline code"))
        assertTrue(text.contains("- [-] fenced code"))
    }

    @Test
    fun canonicalizesEncodedImageBeforeDispatch() {
        val destination = AtomicReference<String>()
        val requested = CountDownLatch(1)
        val handler = object : SchemeHandler() {
            override fun supportedSchemes(): Collection<String> = listOf("https")

            override fun handle(raw: String, uri: Uri): ImageItem {
                destination.set(raw)
                requested.countDown()
                return ImageItem.withResult(ColorDrawable(Color.BLUE))
            }
        }

        instrumentation.runOnMainSync {
            val view = textView()
            MarkdownRenderer.forTest(view.context, handler).render(
                view,
                "![encoded](<HTTPS://EXAMPLE.COM/a b.png?x=1&amp;y=2>)",
                loadRemoteImages = true
            )
        }

        assertTrue(requested.await(5, TimeUnit.SECONDS))
        assertEquals("https://example.com/a%20b.png?x=1&y=2", destination.get())
    }

    @Test
    fun stripsRawHtmlWithoutCreatingLinksOrImageRequests() {
        val requestCount = AtomicInteger()
        val handler = object : SchemeHandler() {
            override fun supportedSchemes(): Collection<String> = listOf("https")

            override fun handle(raw: String, uri: Uri): ImageItem {
                requestCount.incrementAndGet()
                return ImageItem.withResult(ColorDrawable(Color.BLUE))
            }
        }
        lateinit var view: AppCompatTextView

        instrumentation.runOnMainSync {
            view = textView()
            MarkdownRenderer.forTest(view.context, handler).render(
                view,
                """
                    before <b>safe</b> after

                    danger <script>![tracker](https://example.com/tracker.png)</script> tail

                    prefix <a href="https://example.com">plain label</a> suffix

                    <img src="https://example.com/raw.png">

                    `<script>literal code</script>`
                """.trimIndent(),
                loadRemoteImages = true
            )
        }

        Thread.sleep(250)
        val text = view.text as Spanned
        assertTrue(text.contains("before safe after"))
        assertTrue(text.contains("plain label"))
        assertTrue(text.contains("<script>literal code</script>"))
        assertFalse(text.contains("tracker"))
        assertFalse(text.contains("<b>"))
        assertEquals(0, text.getSpans(0, text.length, LinkSpan::class.java).size)
        assertEquals(0, text.getSpans(0, text.length, AsyncDrawableSpan::class.java).size)
        assertEquals(0, requestCount.get())
    }

    @Test
    fun doesNotLeakHtmlOnlyInputThroughRawFallback() {
        lateinit var view: AppCompatTextView

        instrumentation.runOnMainSync {
            view = textView()
            MarkdownRenderer(view.context).render(view, "<!-- private comment -->")
        }

        assertEquals("", view.text.toString())
    }

    private fun laidOutView(
        markdown: String,
        resolve: (InternalNoteLink) -> ResolvedNoteLink?,
        open: (ResolvedNoteLink) -> Unit
    ): AppCompatTextView = textView().also { view ->
        MarkdownRenderer(view.context).render(
            view = view,
            markdown = markdown,
            resolveInternalLink = resolve,
            onInternalLink = open
        )
        view.measure(
            View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    /**
     * Drives the view's movement method the way `TextView.onTouchEvent` does, because that is where
     * a rendered link is turned into a click.
     */
    private fun AppCompatTextView.touch(offset: Int, durationMillis: Long) {
        val line = layout.getLineForOffset(offset)
        val x = layout.getPrimaryHorizontal(offset) + totalPaddingLeft
        val y = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f + totalPaddingTop
        val downTime = SystemClock.uptimeMillis()
        listOf(
            MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0),
            MotionEvent.obtain(
                downTime,
                downTime + durationMillis,
                MotionEvent.ACTION_UP,
                x,
                y,
                0
            )
        ).forEach { event ->
            movementMethod.onTouchEvent(this, text as Spannable, event)
            event.recycle()
        }
    }

    private fun AppCompatTextView.touchTask(task: TaskToggleSpan) {
        val text = text as Spannable
        val line = layout.getLineForOffset(text.getSpanStart(task))
        val x = layout.getLineLeft(line) + 8F * resources.displayMetrics.density + totalPaddingLeft
        val y = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2F + totalPaddingTop
        val time = SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
            MotionEvent.obtain(time, time + action, action, x, y, 0).also { event ->
                dispatchTouchEvent(event)
                event.recycle()
            }
        }
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
