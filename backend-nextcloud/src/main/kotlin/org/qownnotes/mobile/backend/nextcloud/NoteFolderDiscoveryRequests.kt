package org.qownnotes.mobile.backend.nextcloud

import com.nextcloud.android.sso.aidl.NextcloudRequest
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import javax.xml.parsers.DocumentBuilderFactory
import org.qownnotes.mobile.core.BackendException
import org.qownnotes.mobile.core.NoteFolderCandidates
import org.qownnotes.mobile.core.NoteFolderDiscovery
import org.qownnotes.mobile.core.NoteSettings
import org.qownnotes.mobile.core.RemoteFolderListing
import org.qownnotes.mobile.core.RemoteNoteFolder
import org.w3c.dom.Element

internal const val MAX_FOLDER_LISTING_BYTES = 8 * 1024 * 1024

/** Probing stays cheap even for users with many top-level folders named like note folders. */
internal const val MAX_PROBED_NOTE_FOLDERS = 8

private const val DAV_NAMESPACE = "DAV:"

/** One child of a WebDAV collection. */
internal data class DavEntry(val name: String, val isCollection: Boolean)

/**
 * Lists the user's top-level folders through WebDAV, then describes the current note folder and
 * every top-level folder named like a note folder. [folderEndpoint] turns a path relative to the
 * user's files root into its WebDAV URL; the empty path is the root itself.
 */
internal fun discoverNoteFoldersWithClient(
    client: WebDavRequestClient,
    settings: NoteSettings,
    folderEndpoint: (String) -> String
): NoteFolderDiscovery {
    val currentPath = NoteFolderCandidates.normalize(settings.notesPath)
    val topLevelFolders = listFolder(client, folderEndpoint(""))
        ?.filter(DavEntry::isCollection)
        ?.map(DavEntry::name)
        .orEmpty()
    val current = currentPath.takeIf(String::isNotEmpty)?.let { path ->
        describeFolder(client, path, settings.fileSuffix, folderEndpoint)
    }
    val probed = topLevelFolders
        .filter(NoteFolderCandidates::isDefaultFolderName)
        .filterNot { it == currentPath }
        .take(MAX_PROBED_NOTE_FOLDERS)
        .mapNotNull { name ->
            try {
                describeFolder(client, name, settings.fileSuffix, folderEndpoint)
            } catch (error: BackendException.Retryable) {
                throw error
            } catch (_: BackendException) {
                // A folder the user cannot read is not offered; the others still are.
                null
            }
        }
    return NoteFolderDiscovery(
        currentPath = currentPath,
        current = current,
        candidates = NoteFolderCandidates.offered(currentPath, probed)
    )
}

/** Lists one folder for the note folder picker, or returns `null` when it does not exist. */
internal fun browseFolderWithClient(
    client: WebDavRequestClient,
    path: String,
    fileSuffix: String,
    folderEndpoint: (String) -> String
): RemoteFolderListing? {
    val normalized = NoteFolderCandidates.normalize(path)
    val entries = listFolder(client, folderEndpoint(normalized)) ?: return null
    return RemoteFolderListing(
        folder = describeEntries(normalized, entries, fileSuffix),
        subfolders = entries
            .filter { it.isCollection && !it.name.startsWith('.') }
            .map(DavEntry::name)
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
    )
}

private fun describeFolder(
    client: WebDavRequestClient,
    path: String,
    fileSuffix: String,
    folderEndpoint: (String) -> String
): RemoteNoteFolder? =
    listFolder(client, folderEndpoint(path))?.let { describeEntries(path, it, fileSuffix) }

private fun describeEntries(path: String, entries: List<DavEntry>, fileSuffix: String) =
    RemoteNoteFolder(
        path = path,
        noteCount = entries.count {
            !it.isCollection && NoteFolderCandidates.isNoteFile(it.name, fileSuffix)
        },
        subfolderCount = entries.count { it.isCollection && !it.name.startsWith('.') },
        hasTagDatabase = entries.any { !it.isCollection && it.name == TAG_FILE_NAME }
    )

/** The direct children of a collection, or `null` when it does not exist. */
private fun listFolder(client: WebDavRequestClient, endpoint: String): List<DavEntry>? {
    val response = try {
        client.execute(
            NextcloudRequest.Builder()
                .setMethod("PROPFIND")
                .setUrl(endpoint)
                .setHeader(mapOf("Depth" to listOf("1")))
                .build(),
            MAX_FOLDER_LISTING_BYTES
        )
    } catch (error: WebDavStatusException) {
        if (error.statusCode == HttpURLConnection.HTTP_NOT_FOUND) return null
        throw backendExceptionForHttpStatus(error.statusCode, error)
    }
    val body = response.body ?: throw BackendException.Protocol("The folder listing was empty")
    return parseDavChildren(body, endpoint)
}

/**
 * Reads a WebDAV multistatus and returns every entry except the listed collection itself. The
 * collection is recognized by its decoded path ending with the decoded [requestPath], because a
 * server installed below a subpath prefixes every `href`.
 */
internal fun parseDavChildren(body: ByteArray, requestPath: String): List<DavEntry> {
    // Server listings never declare a document type; refusing one rules out entity expansion.
    if (String(body, Charsets.UTF_8).contains("<!DOCTYPE", ignoreCase = true)) {
        throw BackendException.Protocol("The folder listing declares a document type")
    }
    val document = try {
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            runCatching {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            }
        }.newDocumentBuilder().parse(body.inputStream())
    } catch (error: Exception) {
        throw BackendException.Protocol("The folder listing is not valid XML", error)
    }
    val ownPath = decodePath(requestPath).trim('/')
    val responses = document.getElementsByTagNameNS(DAV_NAMESPACE, "response")
    return (0 until responses.length).mapNotNull { index ->
        val response = responses.item(index) as Element
        val href = response.firstDav("href")?.textContent?.trim().orEmpty()
        val path = decodePath(href.substringBefore('?').substringBefore('#')).trim('/')
        val name = path.substringAfterLast('/')
        if (path.isEmpty() || name.isEmpty() || path == ownPath || path.endsWith("/$ownPath")) {
            return@mapNotNull null
        }
        val isCollection = response.getElementsByTagNameNS(DAV_NAMESPACE, "resourcetype")
            .let { types ->
                (0 until types.length).any { typeIndex ->
                    (types.item(typeIndex) as Element)
                        .getElementsByTagNameNS(DAV_NAMESPACE, "collection").length > 0
                }
            }
        DavEntry(name, isCollection)
    }
}

private fun Element.firstDav(localName: String): Element? =
    getElementsByTagNameNS(DAV_NAMESPACE, localName).item(0) as Element?

/** Decodes percent escapes as UTF-8 without treating `+` as a space, as paths require. */
private fun decodePath(path: String): String {
    if ('%' !in path) return path
    val bytes = ByteArrayOutputStream(path.length)
    var index = 0
    while (index < path.length) {
        val char = path[index]
        val hex = if (char == '%' && index + 2 < path.length) {
            path.substring(index + 1, index + 3).toIntOrNull(16)
        } else {
            null
        }
        if (hex != null) {
            bytes.write(hex)
            index += 3
        } else {
            bytes.write(char.toString().toByteArray(Charsets.UTF_8))
            index++
        }
    }
    return bytes.toString(Charsets.UTF_8.name())
}
