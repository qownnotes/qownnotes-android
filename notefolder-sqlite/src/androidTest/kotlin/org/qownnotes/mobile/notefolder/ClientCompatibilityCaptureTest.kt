package org.qownnotes.mobile.notefolder

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.qownnotes.mobile.core.NoteTagKey
import org.qownnotes.mobile.core.NoteTagOperation

/** Exercises the production Android reader/writer on a real Desktop schema, not transcribed DDL. */
@RunWith(AndroidJUnit4::class)
class ClientCompatibilityCaptureTest {
    @Test
    fun desktopDatabaseRoundTrip() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(
            instrumentation.targetContext.filesDir,
            "compatibility-capture"
        ).apply {
            mkdirs()
        }
        val file = File(directory, "android-16.sqlite")
        instrumentation.context.assets.open("desktop-16-seeded.sqlite").use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        try {
            val key = NoteTagKey("Meeting", "Work/Notes")
            val initial = NoteFolderTagDatabase.read(file)
            assertTrue(initial.snapshot.links.any { it.key == key })
            assertTrue(
                NoteFolderTagDatabase.apply(
                    file,
                    listOf(NoteTagOperation.Link(key, listOf("Work", "Android child")))
                )
            )
            assertTrue(
                NoteFolderTagDatabase.apply(
                    file,
                    listOf(
                        NoteTagOperation.Relink(key, NoteTagKey("Android meeting", "Archive/Notes"))
                    )
                )
            )
            val written = NoteFolderTagDatabase.read(file)
            assertEquals(16, written.schemaVersion)
            assertEquals(
                setOf("Project", "Android child"),
                written.snapshot.tags.filter { tag ->
                    written.snapshot.links.any {
                        it.tagId ==
                            tag.id &&
                            it.key == NoteTagKey("Android meeting", "Archive/Notes")
                    }
                }.map { it.name }.toSet()
            )
            assertFalse(
                NoteFolderTagDatabase.apply(
                    file,
                    listOf(
                        NoteTagOperation.Link(
                            NoteTagKey("Android meeting", "Archive/Notes"),
                            listOf("Work", "Android child")
                        )
                    )
                )
            )
            assertEquals(1, file.readBytes()[18].toInt())
            assertEquals(1, file.readBytes()[19].toInt())
        } finally {
            // Opt-in export leaves only this known synthetic-data fixture for an adb capture.
            if (InstrumentationRegistry.getArguments().getString("captureTagFixture") !=
                "true"
            ) {
                file.delete()
            }
        }
    }
}
