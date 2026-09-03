package org.qownnotes.mobile

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.text.SpannableString
import android.text.style.StyleSpan
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the application accepts from a share. An `Intent` is an Android object, so this runs on a
 * device even though it asserts a decision rather than a screen.
 */
@RunWith(AndroidJUnit4::class)
class ShareIntentTest {
    @Test
    fun readsTheSharedTextAndItsSubject() {
        val shared = sharedTextOf(
            sendIntent("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "https://example.com")
                .putExtra(Intent.EXTRA_SUBJECT, "Example page")
        )

        assertEquals("https://example.com", shared?.text)
        assertEquals("Example page", shared?.subject)
    }

    /** Styled text is shared as a `CharSequence`, and reading it as a `String` would drop it. */
    @Test
    fun readsStyledSharedTextAsPlainText() {
        val styled = SpannableString("bold and plain").apply {
            setSpan(StyleSpan(android.graphics.Typeface.BOLD), 0, 4, 0)
        }

        val shared = sharedTextOf(sendIntent("text/plain").putExtra(Intent.EXTRA_TEXT, styled))

        assertEquals("bold and plain", shared?.text)
    }

    @Test
    fun treatsABlankSubjectAsNoSubject() {
        val shared = sharedTextOf(
            sendIntent("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "body")
                .putExtra(Intent.EXTRA_SUBJECT, "   ")
        )

        assertNull(shared?.subject)
        assertEquals("body", shared?.text)
    }

    /** An attachment is a stream this release cannot store, so it is not mistaken for a note. */
    @Test
    fun ignoresASharedStreamWithoutText() {
        val shared = sharedTextOf(
            sendIntent("image/png")
                .putExtra(Intent.EXTRA_STREAM, Uri.parse("content://example/photo.png"))
        )

        assertNull(shared)
    }

    @Test
    fun ignoresAnOrdinaryStart() {
        assertNull(sharedTextOf(null))
        assertNull(sharedTextOf(Intent(Intent.ACTION_MAIN)))
        assertNull(sharedTextOf(sendIntent("text/plain")))
    }

    /**
     * The application has to be offered in the system share sheet at all, and a share has to reach
     * the note the user is looking at rather than a second copy of the application behind it.
     */
    @Test
    fun offersItselfAsASingleInstancedShareTargetForText() {
        val target = shareTargetFor("text/plain")

        assertNotNull("the application is not offered as a share target", target)
        assertEquals(ActivityInfo.LAUNCH_SINGLE_TASK, target?.launchMode)
    }

    /**
     * An application that shares a page or a message may label its text with a subtype other than
     * `text/plain`, and missing from its share sheet is indistinguishable from being broken.
     */
    @Test
    fun offersItselfForTextSubtypesOtherThanPlain() {
        assertNotNull("no share target for text/html", shareTargetFor("text/html"))
        assertNotNull("no share target for text/markdown", shareTargetFor("text/markdown"))
    }

    private fun shareTargetFor(type: String): ActivityInfo? {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return context.packageManager
            .queryIntentActivities(sendIntent(type).setPackage(context.packageName), 0)
            .map { it.activityInfo }
            .firstOrNull { it.name == MainActivity::class.java.name }
    }

    private fun sendIntent(type: String) = Intent(Intent.ACTION_SEND).setType(type)
}
