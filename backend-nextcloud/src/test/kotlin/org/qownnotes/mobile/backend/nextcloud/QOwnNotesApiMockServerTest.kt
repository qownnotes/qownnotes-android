package org.qownnotes.mobile.backend.nextcloud

import com.google.gson.GsonBuilder
import java.net.HttpURLConnection
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.qownnotes.mobile.core.BackendException
import org.qownnotes.mobile.core.Note
import org.qownnotes.mobile.core.SyncState
import org.qownnotes.mobile.core.TrashedNote
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class QOwnNotesApiMockServerTest {
    private lateinit var server: MockWebServer
    private lateinit var notesApi: NotesApi
    private lateinit var archiveApi: QOwnNotesApi

    @Before
    fun startServer() {
        server = MockWebServer()
        server.start()
        notesApi = retrofit("/index.php/apps/notes/api/v1/").create(NotesApi::class.java)
        archiveApi = retrofit("/index.php/apps/qownnotesapi/api/v1/")
            .create(QOwnNotesApi::class.java)
    }

    @After
    fun stopServer() = server.close()

    @Test
    fun loadsVersionsUsingTheCanonicalInternalPath() {
        server.enqueue(jsonResponse("""{"notesPath":"My Notes","fileSuffix":".md"}"""))
        server.enqueue(appInfo(versions = true, trash = true))
        server.enqueue(
            jsonResponse("""{"id":7,"internalPath":"/My Notes/Legacy/Server note.markdown"}""")
        )
        server.enqueue(
            jsonResponse(
                """{"versions":[{"timestamp":42,"humanReadableTimestamp":"Yesterday","data":"old text","diffHtml":"ignored"}],"error_messages":[]}"""
            )
        )

        val versions = loadVersionsFromApis(
            notesApi,
            archiveApi,
            note().copy(
                title = "Locally renamed",
                category = "New category",
                lastSyncedTitle = "Server note",
                lastSyncedCategory = "Work"
            )
        )

        val settingsRequest = server.takeRequest()
        val infoRequest = server.takeRequest()
        val noteRequest = server.takeRequest()
        val versionsRequest = server.takeRequest()
        assertEquals("GET", settingsRequest.method)
        assertEquals(
            "/index.php/apps/notes/api/v1/settings",
            settingsRequest.requestUrl!!.encodedPath
        )
        assertEquals("GET", infoRequest.method)
        assertEquals(
            "/index.php/apps/qownnotesapi/api/v1/note/app_info",
            infoRequest.requestUrl!!.encodedPath
        )
        assertEquals("json", infoRequest.requestUrl!!.queryParameter("format"))
        assertEquals("/My Notes", infoRequest.requestUrl!!.queryParameter("notes_path"))
        assertEquals("GET", noteRequest.method)
        assertEquals("/index.php/apps/notes/api/v1/notes/7", noteRequest.requestUrl!!.encodedPath)
        assertEquals("GET", versionsRequest.method)
        assertEquals(
            "/index.php/apps/qownnotesapi/api/v1/note/versions",
            versionsRequest.requestUrl!!.encodedPath
        )
        assertEquals("json", versionsRequest.requestUrl!!.queryParameter("format"))
        assertEquals(
            "/My Notes/Legacy/Server note.markdown",
            versionsRequest.requestUrl!!.queryParameter("file_name")
        )
        assertEquals("Yesterday", versions.single().displayTimestamp)
        assertEquals("old text", versions.single().content)
    }

    @Test
    fun reportsMissingQOwnNotesApiAsUnavailable() {
        server.enqueue(jsonResponse("""{"notesPath":"Notes","fileSuffix":".txt"}"""))
        server.enqueue(MockResponse().setResponseCode(HttpURLConnection.HTTP_NOT_FOUND))

        val error = assertThrows(BackendException.FeatureUnavailable::class.java) {
            loadVersionsFromApis(notesApi, archiveApi, note())
        }

        assertTrue(error.message!!.contains("QOwnNotesAPI"))
    }

    @Test
    fun loadsAndDeduplicatesTrashForKnownNoteFolders() {
        server.enqueue(jsonResponse("""{"notesPath":"Notes","fileSuffix":".md"}"""))
        server.enqueue(appInfo(versions = true, trash = true))
        server.enqueue(
            jsonResponse(
                """{"directory":"Notes/Work","notes":[{"noteName":"Old","fileName":"Old.md","timestamp":20,"dateString":"Yesterday","data":"deleted"}]}"""
            )
        )
        server.enqueue(
            jsonResponse(
                """{"directory":"Notes","notes":[{"noteName":"Root","fileName":"Root.md","timestamp":10,"dateString":"Last week","data":"root"}]}"""
            )
        )

        val trash = loadTrashedNotesFromApis(notesApi, archiveApi, setOf("Work"))

        server.takeRequest()
        server.takeRequest()
        val workRequest = server.takeRequest()
        val rootRequest = server.takeRequest()
        assertEquals("GET", workRequest.method)
        assertEquals(
            "/index.php/apps/qownnotesapi/api/v1/note/trashed",
            workRequest.requestUrl!!.encodedPath
        )
        assertEquals("json", workRequest.requestUrl!!.queryParameter("format"))
        assertEquals("/Notes/Work", workRequest.requestUrl!!.queryParameter("dir"))
        assertEquals(
            listOf("md", "txt", "org", "markdown", "note"),
            workRequest.requestUrl!!.queryParameterValues("extensions[]")
        )
        assertEquals("GET", rootRequest.method)
        assertEquals(
            "/index.php/apps/qownnotesapi/api/v1/note/trashed",
            rootRequest.requestUrl!!.encodedPath
        )
        assertEquals("/Notes", rootRequest.requestUrl!!.queryParameter("dir"))
        assertEquals(listOf("Old", "Root"), trash.map { it.name })
        assertEquals("/Notes/Work/Old.md", trash.first().remotePath)
    }

    @Test
    fun restoresTrashWithItsPathAndTimestamp() {
        server.enqueue(jsonResponse("""{"result":true,"filename":"Old.md"}"""))
        val note = TrashedNote("Old", "Old.md", 42, "Yesterday", "text", "/Notes/Old.md")

        restoreTrashedNoteWithApi(archiveApi, note)

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals(
            "/index.php/apps/qownnotesapi/api/v1/note/restore_trashed",
            request.requestUrl!!.encodedPath
        )
        assertEquals("json", request.requestUrl!!.queryParameter("format"))
        assertEquals("/Notes/Old.md", request.requestUrl!!.queryParameter("file_name"))
        assertEquals("42", request.requestUrl!!.queryParameter("timestamp"))
    }

    private fun retrofit(path: String) = Retrofit.Builder()
        .baseUrl(server.url(path))
        .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
        .build()

    private fun appInfo(versions: Boolean, trash: Boolean) = jsonResponse(
        """{"versions_app":$versions,"trash_app":$trash,"versioning":true,"app_version":"26.8.0","notes_path_exists":true}"""
    )

    private fun jsonResponse(body: String) = MockResponse()
        .setResponseCode(HttpURLConnection.HTTP_OK)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun note() = Note(
        localId = "local",
        accountId = "account",
        remoteId = 7,
        title = "Note",
        content = "current",
        modifiedAtEpochSeconds = 1,
        syncState = SyncState.SYNCHRONIZED
    )
}
