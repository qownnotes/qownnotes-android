package org.qownnotes.mobile

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ListView
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.qownnotes.mobile.core.NoteCategoryScope
import org.qownnotes.mobile.core.NoteListItem
import org.qownnotes.mobile.core.SyncState

@RunWith(AndroidJUnit4::class)
class NoteWidgetTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun widgetIntentsRetainTheirDestination() {
        val open = WidgetIntents.openNote(context, "local-note")
        val create = WidgetIntents.createNote(context, "account-id")
        val list = WidgetIntents.openNoteList(context, "account-id")

        assertEquals(WidgetRequest.OpenNoteList("account-id"), WidgetIntents.request(list))
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
    fun noteListWidgetsStoreTheirOwnCategoryScope() {
        val account = org.qownnotes.mobile.core.Account(
            id = "account-id",
            displayName = "Personal",
            serverUrl = "https://cloud.example.com",
            ssoAccountName = "test",
            userId = "user"
        )
        try {
            // A widget configured before filtering existed has no stored scope.
            WidgetPreferences.saveAccount(context, 201, account)
            WidgetPreferences.saveNoteList(
                context,
                202,
                account,
                NoteCategoryScope.Category("Work/Projects")
            )
            WidgetPreferences.saveNoteList(context, 203, account, NoteCategoryScope.Undefined)

            assertEquals(NoteCategoryScope.All, WidgetPreferences.categoryScope(context, 201))
            assertEquals(
                NoteCategoryScope.Category("Work/Projects"),
                WidgetPreferences.categoryScope(context, 202)
            )
            assertEquals(
                NoteCategoryScope.Undefined,
                WidgetPreferences.categoryScope(context, 203)
            )
            assertEquals("account-id", WidgetPreferences.accountId(context, 202))

            WidgetPreferences.remove(context, 202)
            assertEquals(NoteCategoryScope.All, WidgetPreferences.categoryScope(context, 202))
            assertEquals(null, WidgetPreferences.accountId(context, 202))
        } finally {
            listOf(201, 202, 203).forEach { WidgetPreferences.remove(context, it) }
        }
    }

    @Test
    fun widgetAppearanceIsStoredPerWidgetAndRemovedWithIt() {
        val custom = WidgetAppearance(
            background = 0xFF1565C0.toInt(),
            backgroundOpacityPercent = 60,
            header = 0xFFC62828.toInt(),
            row = 0xFFDCEDC8.toInt(),
            frame = true,
            frameColor = 0xFF000000.toInt()
        )
        try {
            WidgetPreferences.saveAppearance(context, 301, custom)

            assertEquals(custom, WidgetPreferences.appearance(context, 301))
            assertEquals(WidgetAppearance.DEFAULT, WidgetPreferences.appearance(context, 302))

            WidgetPreferences.saveAppearance(context, 301, WidgetAppearance.DEFAULT)
            assertEquals(WidgetAppearance.DEFAULT, WidgetPreferences.appearance(context, 301))

            WidgetPreferences.saveAppearance(context, 301, custom)
            WidgetPreferences.remove(context, 301)
            assertEquals(WidgetAppearance.DEFAULT, WidgetPreferences.appearance(context, 301))
        } finally {
            WidgetPreferences.remove(context, 301)
        }
    }

    @Test
    fun customAppearanceColorsTheWidgetAndResettingRestoresDefaultsOnTheSameViews() {
        val blue = 0xFF1565C0.toInt()
        val lightGreen = 0xFFDCEDC8.toInt()
        val custom = WidgetAppearance(
            background = blue,
            backgroundOpacityPercent = 60,
            header = 0xFFFFF9C4.toInt(),
            row = lightGreen,
            frame = true
        )
        fun list(appearance: WidgetAppearance) =
            RemoteViews(context.packageName, R.layout.widget_note_list).apply {
                WidgetAppearanceViews.applyContainer(this, appearance)
                NoteListWidgetHeader.apply(
                    context,
                    this,
                    NoteCategoryScope.All,
                    "Personal",
                    appearance
                )
            }
        val view = list(custom).apply(context, FrameLayout(context))

        assertEquals(
            AppearanceColors.alpha(60),
            view.findViewById<ImageView>(R.id.widget_background).imageAlpha
        )
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.widget_frame).visibility)
        assertEquals(
            View.VISIBLE,
            view.findViewById<View>(R.id.widget_header_background).visibility
        )
        // Light yellow header: dark title text.
        assertEquals(
            AppearanceColors.DARK_CONTENT,
            view.findViewById<TextView>(R.id.widget_title).currentTextColor
        )

        // Launchers reapply onto the existing tree; defaults must undo every custom value.
        list(WidgetAppearance.DEFAULT).reapply(context, view)
        assertEquals(255, view.findViewById<ImageView>(R.id.widget_background).imageAlpha)
        assertEquals(View.GONE, view.findViewById<View>(R.id.widget_frame).visibility)
        assertEquals(View.GONE, view.findViewById<View>(R.id.widget_header_background).visibility)
        assertEquals(
            context.getColor(R.color.widget_text),
            view.findViewById<TextView>(R.id.widget_title).currentTextColor
        )

        val row = NoteListWidgetRows.row(context, sampleNote(), appearance = custom)
            .apply(context, FrameLayout(context))
        assertEquals(
            AppearanceColors.contentColor(lightGreen),
            row.findViewById<TextView>(R.id.widget_note_title).currentTextColor
        )
    }

    @Test
    fun singleNoteWidgetUsesTheSameTintableContainer() {
        val views = RemoteViews(context.packageName, R.layout.widget_single_note)
        WidgetAppearanceViews.applyContainer(
            views,
            WidgetAppearance(background = 0xFF000000.toInt(), frame = true)
        )
        WidgetAppearanceViews.setPrimaryText(
            context,
            views,
            R.id.widget_single_title,
            0xFF000000.toInt()
        )
        val view = views.apply(context, FrameLayout(context))

        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.widget_frame).visibility)
        assertEquals(
            AppearanceColors.LIGHT_CONTENT,
            view.findViewById<TextView>(R.id.widget_single_title).currentTextColor
        )
    }

    private fun sampleNote() = NoteListItem(
        localId = "local-note",
        accountId = "account-id",
        remoteId = 1,
        title = "Title",
        category = "",
        modifiedAtEpochSeconds = 10,
        favorite = false,
        syncState = SyncState.SYNCHRONIZED,
        excerpt = "Excerpt"
    )

    @Test
    fun noteListHeaderShowsWhatIsListedAboveTheAccountName() {
        val parent = FrameLayout(context)
        fun header(scope: NoteCategoryScope, accountName: String?): View {
            val views = RemoteViews(context.packageName, R.layout.widget_note_list)
            NoteListWidgetHeader.apply(context, views, scope, accountName)
            return views.apply(context, parent)
        }

        val all = header(NoteCategoryScope.All, "Personal")
        assertEquals("Notes", all.findViewById<TextView>(R.id.widget_title).text.toString())
        val subtitle = all.findViewById<TextView>(R.id.widget_subtitle)
        assertEquals("Personal", subtitle.text.toString())
        assertEquals(View.VISIBLE, subtitle.visibility)

        val category = header(NoteCategoryScope.Category("Work"), "Personal")
        assertEquals("Work", category.findViewById<TextView>(R.id.widget_title).text.toString())

        val undefined = header(NoteCategoryScope.Undefined, null)
        assertEquals(
            "Root folder",
            undefined.findViewById<TextView>(R.id.widget_title).text.toString()
        )
        assertEquals(View.GONE, undefined.findViewById<TextView>(R.id.widget_subtitle).visibility)
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
        assertTrue(
            !context.packageManager.getServiceInfo(
                ComponentName(context, SingleNoteWidgetService::class.java),
                0
            ).exported
        )
    }

    @Test
    fun compactNoteListRowsUseLessSpacingAndOnePreviewLine() {
        val note = NoteListItem(
            localId = "local-note",
            accountId = "account-id",
            remoteId = 1,
            title = "Title",
            category = "",
            modifiedAtEpochSeconds = 10,
            favorite = false,
            syncState = SyncState.SYNCHRONIZED,
            excerpt = "First line\nSecond line"
        )
        val parent = FrameLayout(context)

        val regular = NoteListWidgetRows.row(context, note).apply(context, parent)
        val compact = NoteListWidgetRows.row(context, note, NoteListWidgetDisplay(compact = true))
            .apply(context, parent)

        val regularExcerpt = regular.findViewById<TextView>(R.id.widget_note_excerpt)
        val compactExcerpt = compact.findViewById<TextView>(R.id.widget_note_excerpt)
        assertEquals("First line\nSecond line", compactExcerpt.text.toString())
        assertEquals(View.VISIBLE, compactExcerpt.visibility)
        assertEquals(2, regularExcerpt.maxLines)
        assertEquals(1, compactExcerpt.maxLines)
        // Padding sits on the text container above the row's tintable background image.
        val regularContent = regularExcerpt.parent as View
        val compactContent = compactExcerpt.parent as View
        assertTrue(compactContent.paddingTop < regularContent.paddingTop)
        assertTrue(compactContent.paddingBottom < regularContent.paddingBottom)
        assertEquals(regularContent.paddingStart, compactContent.paddingStart)
    }

    @Test
    fun noteListRowsFollowThePreviewSettingAndShowPlainTextPreviews() {
        val note = sampleNote().copy(
            title = "Groceries",
            excerpt = "# Groceries\n\n- Milk and [bread](https://example.com)"
        )
        val parent = FrameLayout(context)

        val shown = NoteListWidgetRows.row(context, note).apply(context, parent)
        val excerpt = shown.findViewById<TextView>(R.id.widget_note_excerpt)
        assertEquals("Milk and bread", excerpt.text.toString())
        assertEquals(View.VISIBLE, excerpt.visibility)
        assertEquals(
            View.GONE,
            shown.findViewById<View>(R.id.widget_note_category_container).visibility
        )

        // Launchers reapply onto recycled rows, so hiding must undo a shown preview.
        NoteListWidgetRows.row(context, note, NoteListWidgetDisplay(showPreview = false))
            .reapply(context, shown)
        assertEquals(View.GONE, excerpt.visibility)
    }

    @Test
    fun noteListRowsShowCategoriesAsTextOrHighlightedLabels() {
        val purple = 0xFF4A148C.toInt()
        val note = sampleNote().copy(category = "Work/Projects")
        val parent = FrameLayout(context)
        val highlighted = NoteListWidgetDisplay(
            showCategory = true,
            highlightCategories = true,
            categoryHighlight = purple
        )

        val row = NoteListWidgetRows.row(context, note, highlighted).apply(context, parent)
        val container = row.findViewById<View>(R.id.widget_note_category_container)
        val label = row.findViewById<TextView>(R.id.widget_note_category)
        val background = row.findViewById<View>(R.id.widget_note_category_background)
        assertEquals(View.VISIBLE, container.visibility)
        assertEquals("Work/Projects", label.text.toString())
        assertEquals(View.VISIBLE, background.visibility)
        assertEquals(AppearanceColors.contentColor(purple), label.currentTextColor)
        assertTrue(label.paddingStart > 0)

        NoteListWidgetRows.row(
            context,
            note,
            highlighted.copy(categoryHighlight = null)
        ).reapply(context, row)
        assertEquals(View.VISIBLE, background.visibility)
        assertEquals(context.getColor(R.color.widget_category_text), label.currentTextColor)

        NoteListWidgetRows.row(
            context,
            note.copy(category = ""),
            NoteListWidgetDisplay(showCategory = true)
        ).reapply(context, row)
        assertEquals("Root folder", label.text.toString())
        assertEquals(View.GONE, background.visibility)
        assertEquals(0, label.paddingStart)
        assertEquals(context.getColor(R.color.widget_text), label.currentTextColor)

        NoteListWidgetRows.row(context, note).reapply(context, row)
        assertEquals(View.GONE, container.visibility)
    }

    @Test
    fun noteListWidgetDisplayFollowsTheAppListSettings() {
        val name = "widget-display-test"
        context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        try {
            val settings = AppSettings(context, name)
            assertEquals(
                NoteListWidgetDisplay.DEFAULT,
                NoteListWidgetDisplay.of(settings, "account-id")
            )

            settings.setCompactNoteList(true)
            settings.setShowNotePreview(false)
            settings.setShowCategory("account-id", true)
            settings.setAppearance(
                AppAppearance(highlightCategories = true, categoryHighlight = 0xFF4A148C.toInt())
            )

            assertEquals(
                NoteListWidgetDisplay(
                    compact = true,
                    showPreview = false,
                    showCategory = true,
                    highlightCategories = true,
                    categoryHighlight = 0xFF4A148C.toInt()
                ),
                NoteListWidgetDisplay.of(settings, "account-id")
            )
            // Showing categories is an account setting.
            assertEquals(false, NoteListWidgetDisplay.of(settings, "other-account").showCategory)
        } finally {
            context.deleteSharedPreferences(name)
        }
    }

    @Test
    fun singleNoteBodyUsesScrollableCollection() {
        val layout = LayoutInflater.from(context).inflate(R.layout.widget_single_note, null)

        assertTrue(
            layout.findViewById<ListView>(R.id.widget_single_content).isVerticalScrollBarEnabled
        )
    }
}
