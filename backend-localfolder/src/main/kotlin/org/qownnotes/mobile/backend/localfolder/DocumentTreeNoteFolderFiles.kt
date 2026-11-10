package org.qownnotes.mobile.backend.localfolder

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * File access through a folder the user granted with the Storage Access Framework. No broad
 * storage permission is needed, and the folder may come from any document provider.
 *
 * Document IDs are cached by path so that reading a note does not walk the tree each time. A
 * cached ID that stopped working is resolved again once before the file counts as missing.
 */
class DocumentTreeNoteFolderFiles(private val resolver: ContentResolver, private val treeUri: Uri) :
    NoteFolderFiles {
    private val rootId: String = DocumentsContract.getTreeDocumentId(treeUri)
    private val ids = ConcurrentHashMap<String, String>()

    override fun checkAccess() {
        val granted = resolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isReadPermission
        }
        if (!granted) throw FolderAccessException("Access to the note folder was revoked")
        val readable = guarded {
            resolver.query(documentUri(rootId), arrayOf(Document.COLUMN_DOCUMENT_ID), null, null)
                ?.use { it.moveToFirst() } == true
        }
        if (!readable) throw FolderAccessException("The note folder no longer exists")
    }

    override fun list(descend: (String) -> Boolean): List<NoteFolderFile> = buildList {
        fun visit(directoryId: String, prefix: String) {
            children(directoryId).forEach { child ->
                val path = prefix + child.name
                ids[path] = child.id
                if (child.isDirectory) {
                    if (descend(path)) visit(child.id, "$path/")
                } else {
                    add(child.toNoteFolderFile(path))
                }
            }
        }
        visit(rootId, "")
    }

    override fun stat(path: String): NoteFolderFile? = withDocument(path) { id ->
        query(id)?.takeUnless(Child::isDirectory)?.toNoteFolderFile(path)
            ?: throw FileNotFoundException(path)
    }

    override fun exists(path: String): Boolean = find(path, ignoreCase = true) != null

    override fun read(path: String): ByteArray = withDocument(path) { id ->
        resolver.openInputStream(documentUri(id))?.use { it.readBytes() }
            ?: throw FileNotFoundException(path)
    } ?: throw FileNotFoundException(path)

    override fun write(path: String, content: ByteArray) {
        withDocument(path) { id ->
            val stream = try {
                resolver.openOutputStream(documentUri(id), "wt")
            } catch (unsupported: IllegalArgumentException) {
                // Some providers reject truncation. Rather than risk a stale tail after a shorter
                // text, replace the file under the same name.
                replace(path, id, content)
                return@withDocument
            }
            (stream ?: throw FileNotFoundException(path)).use { it.write(content) }
        } ?: throw FileNotFoundException(path)
    }

    override fun create(path: String, content: ByteArray): String = guarded {
        val parentPath = path.substringBeforeLast('/', "")
        val parentId = ensureDirectory(parentPath)
        val uri = DocumentsContract.createDocument(
            resolver,
            documentUri(parentId),
            // A neutral type keeps providers from appending an extension of their own.
            "application/octet-stream",
            path.substringAfterLast('/')
        ) ?: throw IOException("$path could not be created")
        val id = DocumentsContract.getDocumentId(uri)
        try {
            resolver.openOutputStream(uri, "w")?.use { it.write(content) }
                ?: throw IOException("$path could not be written")
        } catch (error: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, uri) }
            throw error
        }
        val name = query(id)?.name ?: path.substringAfterLast('/')
        val actual = if (parentPath.isEmpty()) name else "$parentPath/$name"
        ids[actual] = id
        actual
    }

    override fun delete(path: String) {
        withDocument(path) { id ->
            DocumentsContract.deleteDocument(resolver, documentUri(id))
            ids.keys.removeAll { it == path || it.startsWith("$path/") }
        }
    }

    private fun replace(path: String, id: String, content: ByteArray) {
        DocumentsContract.deleteDocument(resolver, documentUri(id))
        ids.remove(path)
        val created = create(path, content)
        if (created != path) {
            throw IOException("$path was recreated as $created")
        }
    }

    /**
     * Runs [block] with the document of [path] and returns null when there is none. A stale cached
     * ID is resolved once more before the file counts as missing.
     */
    private fun <T> withDocument(path: String, block: (String) -> T): T? = guarded {
        val cached = ids[path]
        if (cached != null) {
            try {
                return@guarded block(cached)
            } catch (_: FileNotFoundException) {
                ids.remove(path)
            } catch (_: IllegalArgumentException) {
                ids.remove(path)
            }
        }
        val id = find(path, ignoreCase = false) ?: return@guarded null
        try {
            block(id)
        } catch (_: FileNotFoundException) {
            ids.remove(path)
            null
        }
    }

    private fun find(path: String, ignoreCase: Boolean): String? = guarded {
        if (path.isEmpty()) return@guarded rootId
        if (!ignoreCase) ids[path]?.let { return@guarded it }
        var currentId = rootId
        var currentPath = ""
        for (segment in path.split('/')) {
            val children = children(currentId)
            val child = children.firstOrNull { it.name == segment }
                ?: children.firstOrNull { it.name.equals(segment, ignoreCase = true) }
                    ?.takeIf { ignoreCase }
                ?: return@guarded null
            currentPath = if (currentPath.isEmpty()) child.name else "$currentPath/${child.name}"
            ids[currentPath] = child.id
            currentId = child.id
        }
        currentId
    }

    private fun ensureDirectory(path: String): String {
        if (path.isEmpty()) return rootId
        var currentId = rootId
        for (segment in path.split('/')) {
            val existing = children(currentId).let { children ->
                children.firstOrNull { it.name == segment }
                    ?: children.firstOrNull { it.name.equals(segment, ignoreCase = true) }
            }
            currentId = when {
                existing == null -> DocumentsContract.createDocument(
                    resolver,
                    documentUri(currentId),
                    Document.MIME_TYPE_DIR,
                    segment
                )?.let(DocumentsContract::getDocumentId)
                    ?: throw IOException("The folder $segment could not be created")
                existing.isDirectory -> existing.id
                else -> throw IOException("$segment is a file, not a folder")
            }
        }
        return currentId
    }

    private fun children(parentId: String): List<Child> = resolver.query(
        DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId),
        PROJECTION,
        null,
        null
    )?.use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toChild()) } }
        ?: throw FolderAccessException("The note folder cannot be listed")

    private fun query(id: String): Child? =
        resolver.query(documentUri(id), PROJECTION, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.toChild() else null
        }

    private fun documentUri(id: String): Uri =
        DocumentsContract.buildDocumentUriUsingTree(treeUri, id)

    /** A revoked grant surfaces as a security exception; report it as lost folder access. */
    private inline fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (denied: SecurityException) {
        throw FolderAccessException("Access to the note folder was revoked", denied)
    }

    private class Child(
        val id: String,
        val name: String,
        val isDirectory: Boolean,
        val size: Long,
        val lastModifiedMillis: Long,
        val writable: Boolean
    ) {
        fun toNoteFolderFile(path: String) =
            NoteFolderFile(path, size, lastModifiedMillis, writable)
    }

    private fun Cursor.toChild(): Child {
        val flags = getInt(4)
        return Child(
            id = getString(0),
            name = getString(1).orEmpty(),
            isDirectory = getString(2) == Document.MIME_TYPE_DIR,
            size = if (isNull(3)) 0 else getLong(3),
            lastModifiedMillis = if (isNull(5)) 0 else getLong(5),
            writable = flags and Document.FLAG_SUPPORTS_WRITE != 0
        )
    }

    companion object {
        private val PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_FLAGS,
            Document.COLUMN_LAST_MODIFIED
        )

        /** The name the provider shows for the selected folder itself. */
        fun displayName(resolver: ContentResolver, treeUri: Uri): String? = runCatching {
            val id = DocumentsContract.getTreeDocumentId(treeUri)
            resolver.query(
                DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                arrayOf(Document.COLUMN_DISPLAY_NAME),
                null,
                null
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()?.takeIf(String::isNotBlank)
    }
}
