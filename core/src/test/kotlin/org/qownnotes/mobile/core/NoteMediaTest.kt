package org.qownnotes.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NoteMediaTest {
    @Test
    fun createsAUniqueNameWithTheMimeTypeExtension() {
        assertEquals(
            "image-123e4567e89b12d3a456426614174000.jpg",
            uniqueMediaImageFileName("image/jpeg", "123e4567-e89b-12d3-a456-426614174000")
        )
        assertEquals("image-unique.png", uniqueMediaImageFileName("image/png", "unique"))
        assertEquals("image-unique.webp", uniqueMediaImageFileName("image/webp", "unique"))
    }

    @Test
    fun rejectsImagesTheRendererCannotDisplay() {
        assertThrows(IllegalArgumentException::class.java) {
            uniqueMediaImageFileName("image/gif", "unique")
        }
    }

    @Test
    fun linksToTopLevelMediaFromAnyCategory() {
        assertEquals("media/photo.png", mediaImagePath("", "photo.png"))
        assertEquals("../media/photo.png", mediaImagePath("Projects", "photo.png"))
        assertEquals("../../media/photo.png", mediaImagePath("Projects/Android", "photo.png"))
    }

    @Test
    fun audioLinksToAttachmentsFromAnyCategoryWhileImagesStayInMedia() {
        assertEquals("attachments/audio.m4a", noteMediaPath("", "audio.m4a", "audio/mp4"))
        assertEquals(
            "../attachments/audio.m4a",
            noteMediaPath("Projects", "audio.m4a", "audio/mp4")
        )
        assertEquals(
            "../../attachments/audio.m4a",
            noteMediaPath("Projects/Android", "audio.m4a", "audio/mp4")
        )
        assertEquals("media", noteMediaFolder("image/png"))
        assertEquals("attachments", noteMediaFolder("audio/mp4"))
        assertThrows(IllegalArgumentException::class.java) {
            noteMediaPath("", "nested/audio.m4a", "audio/mp4")
        }
    }

    @Test
    fun rejectsAPathInPlaceOfAFileName() {
        assertThrows(IllegalArgumentException::class.java) {
            mediaImagePath("", "nested/photo.png")
        }
    }
}
