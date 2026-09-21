package org.qownnotes.mobile

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteWidgetTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun widgetIntentsRetainTheirDestination() {
        val open = WidgetIntents.openNote(context, "local-note")
        val create = WidgetIntents.createNote(context, "account-id")

        assertEquals(WidgetRequest.OpenNote("local-note"), WidgetIntents.request(open))
        assertEquals(WidgetRequest.CreateNote("account-id"), WidgetIntents.request(create))
        assertEquals(null, WidgetIntents.request(Intent(Intent.ACTION_MAIN)))
    }

    @Test
    fun noteListFillInOpensItsNoteThroughTheTemplateAction() {
        val combined = WidgetIntents.openNoteTemplate(context)
        combined.fillIn(WidgetIntents.openNoteFillIn("local-note"), 0)

        assertEquals(WidgetRequest.OpenNote("local-note"), WidgetIntents.request(combined))
        assertEquals(WidgetActionActivity::class.java.name, combined.component?.className)
    }

    @Test
    fun noteListDataIdentifiesTheNoteWhenTheLauncherDropsFillInExtras() {
        val fillIn = WidgetIntents.openNoteFillIn("local-note")
        val withoutExtras = WidgetIntents.openNoteTemplate(context).setData(fillIn.data)

        assertEquals(
            WidgetRequest.OpenNote("local-note"),
            WidgetIntents.request(withoutExtras)
        )
    }

    @Test
    fun widgetPreferencesKeepConfigurationsSeparate() {
        val account = org.qownnotes.mobile.core.Account(
            id = "account-id",
            displayName = "Personal",
            serverUrl = "https://cloud.example.com",
            ssoAccountName = "test",
            userId = "user"
        )

        WidgetPreferences.saveAccount(context, 101, account)
        WidgetPreferences.saveNote(context, 102, account, "local-note")

        assertEquals("account-id", WidgetPreferences.accountId(context, 101))
        assertEquals("Personal", WidgetPreferences.accountName(context, 101))
        assertEquals("local-note", WidgetPreferences.noteId(context, 102))
        WidgetPreferences.remove(context, 101)
        WidgetPreferences.remove(context, 102)
    }

    @Test
    fun manifestRegistersBothHomeScreenWidgets() {
        val receivers = context.packageManager.queryBroadcastReceivers(
            Intent("android.appwidget.action.APPWIDGET_UPDATE").setPackage(context.packageName),
            PackageManager.GET_META_DATA
        )
        val byName = receivers.associateBy { it.activityInfo.name }

        val list = byName[NoteListWidgetProvider::class.java.name]
        val single = byName[SingleNoteWidgetProvider::class.java.name]
        assertNotNull(list)
        assertNotNull(single)
        assertEquals(
            R.xml.note_list_widget_info,
            list?.activityInfo?.metaData?.getInt("android.appwidget.provider")
        )
        assertEquals(
            R.xml.single_note_widget_info,
            single?.activityInfo?.metaData?.getInt("android.appwidget.provider")
        )
        assertTrue(list?.activityInfo?.exported == true)
        assertTrue(single?.activityInfo?.exported == true)
        assertTrue(
            !context.packageManager.getActivityInfo(
                ComponentName(context, WidgetActionActivity::class.java),
                0
            ).exported
        )
    }
}
