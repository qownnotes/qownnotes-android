package org.qownnotes.mobile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.qownnotes.mobile.core.NoteFolderScope

@RunWith(AndroidJUnit4::class)
class NoteFolderSettingsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "note-folder-settings-test"
    private val preferences get() = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    @Before
    fun clear() {
        preferences.edit().clear().commit()
    }

    @After
    fun delete() {
        context.deleteSharedPreferences(name)
    }

    @Test
    fun flatCategorySelectionsKeepShowingTheSameNotes() {
        preferences.edit()
            .putString("noteCategoryScope.undefined", "undefined")
            .putString("noteCategoryScope.all", "all")
            .putString("noteCategoryScope.work", "category:Work/Projects")
            .commit()
        val settings = AppSettings(context, name)

        assertEquals(NoteFolderScope("", false), settings.noteFolderScope("missing"))
        assertEquals(NoteFolderScope("", false), settings.noteFolderScope("undefined"))
        assertEquals(NoteFolderScope("", true), settings.noteFolderScope("all"))
        assertEquals(NoteFolderScope("Work/Projects", false), settings.noteFolderScope("work"))
    }

    @Test
    fun folderAndSubfolderChoiceAreStoredPerAccountAndRemovedWithIt() {
        val settings = AppSettings(context, name)

        settings.setNoteFolderScope("first", NoteFolderScope("Work", includeSubfolders = true))
        settings.setNoteFolderScope("second", NoteFolderScope("", includeSubfolders = true))
        settings.setNoteFolderScope("third", NoteFolderScope("", includeSubfolders = false))

        val reread = AppSettings(context, name)
        assertEquals(NoteFolderScope("Work", true), reread.noteFolderScope("first"))
        assertEquals(NoteFolderScope("", true), reread.noteFolderScope("second"))
        assertEquals(NoteFolderScope("", false), reread.noteFolderScope("third"))

        reread.removeNoteFolderScope("first")
        assertEquals(NoteFolderScope("", false), reread.noteFolderScope("first"))
        assertEquals(NoteFolderScope("", true), reread.noteFolderScope("second"))
    }

    @Test
    fun subfoldersAreUsedByDefaultAndCanBeTurnedOffPerAccount() {
        val settings = AppSettings(context, name)
        assertEquals(true, settings.useSubfolders("first").value)

        settings.setUseSubfolders("first", false)

        assertEquals(false, AppSettings(context, name).useSubfolders("first").value)
        assertEquals(true, AppSettings(context, name).useSubfolders("second").value)
        settings.removeUseSubfolders("first")
        assertEquals(true, AppSettings(context, name).useSubfolders("first").value)
    }
}
