package org.qownnotes.mobile.backend.nextcloud

import com.nextcloud.android.sso.aidl.NextcloudRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.qownnotes.mobile.core.BackendException
import org.qownnotes.mobile.core.NoteSettings
import org.qownnotes.mobile.core.RemoteNoteFolder

class NoteFolderDiscoveryRequestTest {
    private val root = "/remote.php/dav/files/user/"

    private fun endpoint(path: String) =
        root + path.split('/').filter(String::isNotEmpty).joinToString("") {
            "${it.replace(" ", "%20")}/"
        }

    @Test
    fun findsTheLocalizedNextcloudNotesFolderNextToTheDesktopFolder() {
        val server = FakeDav(
            "" to listOf("Documents/", "Notes/", "Notizen/", "Photos/", "readme.md"),
            "Notes" to listOf("Ideas.md", "Todo.txt", "notes.sqlite", "media/"),
            "Notizen" to listOf("Einkauf.md", "Reise.md", "Arbeit/", ".attachments.12/")
        )

        val discovery = discoverNoteFoldersWithClient(
            server.client,
            NoteSettings("Notizen", ".md"),
            ::endpoint
        )

        assertEquals("Notizen", discovery.currentPath)
        assertEquals(RemoteNoteFolder("Notizen", 2, 1, false), discovery.current)
        assertEquals(listOf(RemoteNoteFolder("Notes", 2, 1, true)), discovery.candidates)
        assertEquals(listOf(root, "${root}Notizen/", "${root}Notes/"), server.paths)
        server.requests.forEach { request ->
            assertEquals("PROPFIND", request.method)
            assertEquals(listOf("1"), request.header["Depth"])
        }
    }

    @Test
    fun reportsAMissingCurrentFolderAndSkipsUnreadableCandidates() {
        val server = FakeDav(
            "" to listOf("Notes/", "Notas/"),
            "Notas" to listOf("Lista.md"),
            failures = mapOf("Notes" to 403)
        )

        val discovery = discoverNoteFoldersWithClient(
            server.client,
            NoteSettings("Work/Notes", ".md"),
            ::endpoint
        )

        assertNull(discovery.current)
        assertEquals(listOf("Notas"), discovery.candidates.map(RemoteNoteFolder::path))
    }

    @Test
    fun aRootListingFailureIsClassified() {
        val server = FakeDav(failures = mapOf("" to 401))
        try {
            discoverNoteFoldersWithClient(server.client, NoteSettings("Notes", ".md"), ::endpoint)
            fail()
        } catch (_: BackendException.Authentication) {
        }
    }

    @Test
    fun browsingListsVisibleSubfoldersSortedAndDescribesTheFolder() {
        val server = FakeDav(
            "Work Files" to listOf("zeta/", "Alpha/", ".hidden/", "Plan.md", "notes.sqlite")
        )

        val listing = browseFolderWithClient(server.client, "/Work Files/", ".md", ::endpoint)

        assertEquals(listOf("Alpha", "zeta"), listing?.subfolders)
        assertEquals(RemoteNoteFolder("Work Files", 1, 2, true), listing?.folder)
        assertEquals(listOf("${root}Work%20Files/"), server.paths)
        assertNull(browseFolderWithClient(server.client, "Missing", ".md", ::endpoint))
    }

    @Test
    fun parsesSubpathInstallationsEncodedNamesAndOwnEntry() {
        val body = multistatus(
            "/nextcloud/remote.php/dav/files/user/My%20Notes/" to true,
            "/nextcloud/remote.php/dav/files/user/My%20Notes/Caf%C3%A9%2B1/" to true,
            "/nextcloud/remote.php/dav/files/user/My%20Notes/a+b.md" to false
        )

        val entries = parseDavChildren(body, "/remote.php/dav/files/user/My%20Notes/")

        assertEquals(listOf(DavEntry("Café+1", true), DavEntry("a+b.md", false)), entries)
    }

    @Test
    fun rejectsDocumentTypesAndMalformedListings() {
        val doctype = """<?xml version="1.0"?><!DOCTYPE x [<!ENTITY e "x">]>""" +
            """<d:multistatus xmlns:d="DAV:"/>"""
        for (body in listOf(doctype, "<d:multistatus")) {
            try {
                parseDavChildren(body.toByteArray(), root)
                fail()
            } catch (error: BackendException.Protocol) {
                assertTrue(error.message.orEmpty().isNotEmpty())
            }
        }
    }

    private inner class FakeDav(
        vararg folders: Pair<String, List<String>>,
        private val failures: Map<String, Int> = emptyMap()
    ) {
        private val contents = folders.toMap()
        val requests = mutableListOf<NextcloudRequest>()
        val paths get() = requests.map { it.url }

        val client = WebDavRequestClient { request, limit ->
            requests += request
            assertEquals(MAX_FOLDER_LISTING_BYTES, limit)
            val path = request.url.removePrefix(root).trim('/').replace("%20", " ")
            failures[path]?.let { throw WebDavStatusException(it) }
            val children = contents[path] ?: throw WebDavStatusException(404)
            val own = request.url
            WebDavResponse(
                null,
                multistatus(
                    own to true,
                    *children.map { child ->
                        "$own${child.trimEnd('/').replace(" ", "%20")}${
                            if (child.endsWith('/')) "/" else ""
                        }" to child.endsWith('/')
                    }.toTypedArray()
                )
            )
        }
    }

    private fun multistatus(vararg entries: Pair<String, Boolean>): ByteArray = buildString {
        append(
            """<?xml version="1.0"?><d:multistatus xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns">"""
        )
        entries.forEach { (href, collection) ->
            append("<d:response><d:href>").append(href).append("</d:href>")
            append("<d:propstat><d:prop><d:resourcetype>")
            if (collection) append("<d:collection/>")
            append("</d:resourcetype><oc:size>1</oc:size></d:prop>")
            append("<d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>")
        }
        append("</d:multistatus>")
    }.toByteArray()
}
