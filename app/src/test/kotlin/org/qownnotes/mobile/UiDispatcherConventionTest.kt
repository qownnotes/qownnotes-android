package org.qownnotes.mobile

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the rule that every coroutine ending in the user interface names the dispatcher it needs.
 *
 * Compose hands an effect and a remembered scope the dispatcher of the composition they belong to.
 * The interceptor a Compose test installs is not a dispatcher, so a call that waits for the
 * database resumes on one of Room's executor threads, and the state writes, the snapshot
 * notification Compose sends on every resumption, and any call into a hosted Android view then run
 * there. That has already crashed the editor and poisoned Compose's layout observer mid-suite, and
 * neither failure reproduces reliably, so the rule is checked at the source rather than waited for.
 */
class UiDispatcherConventionTest {
    private val source = File("src/main/kotlin/org/qownnotes/mobile/MainActivity.kt").readText()

    @Test
    fun everyRememberedScopeNamesTheUiDispatcher() {
        assertEquals(
            "rememberCoroutineScope() must be given { UiDispatcher }",
            emptyList<String>(),
            source.lines()
                .filterNot { it.startsWith("import ") }
                .filter {
                    "rememberCoroutineScope" in it &&
                        "rememberCoroutineScope { UiDispatcher }" !in it
                }
                .map(String::trim)
        )
    }

    @Test
    fun everyLifecycleCollectionNamesTheUiDispatcher() {
        assertEquals(
            "collectAsStateWithLifecycle must be given context = UiDispatcher",
            emptyList<String>(),
            source.lines()
                .filter { "collectAsStateWithLifecycle(" in it && "context = UiDispatcher" !in it }
                .map(String::trim)
        )
    }

    /**
     * An effect cannot be given a dispatcher the way a remembered scope can, so anything it awaits
     * from the application component is either wrapped where it is called or handed to the
     * screen's own scope, which already has one.
     */
    @Test
    fun everyEffectAwaitsTheComponentOnTheUiDispatcher() {
        val called = Regex("""\bcomponent\.(\w+)\(""")
        val unconfined = blocks(source, "LaunchedEffect(")
            .map { effect ->
                blocks(effect, "scope.launch").fold(effect) { body, launched ->
                    body.replace(launched, "")
                }
            }
            .flatMap { effect ->
                effect.lines().filter { line ->
                    called.findAll(line).any { it.groupValues[1] in SUSPENDING_COMPONENT_CALLS } &&
                        "withContext(UiDispatcher)" !in line
                }
            }

        assertEquals(
            "a LaunchedEffect must await the component inside withContext(UiDispatcher) " +
                "or from the screen's remembered scope",
            emptyList<String>(),
            unconfined.map(String::trim)
        )
    }

    /** Every `marker { ... }` body in [text], matched by brace depth. */
    private fun blocks(text: String, marker: String): List<String> = buildList {
        var index = text.indexOf(marker)
        while (index >= 0) {
            val open = text.indexOf('{', index)
            if (open < 0) return@buildList
            var depth = 0
            var cursor = open
            while (cursor < text.length) {
                if (text[cursor] == '{') depth++
                if (text[cursor] == '}' && --depth == 0) break
                cursor++
            }
            add(text.substring(open, minOf(cursor + 1, text.length)))
            index = text.indexOf(marker, cursor)
        }
    }

    private companion object {
        /**
         * The suspending members of `ApplicationComponent`. Kept by hand so that adding one is a
         * decision about which thread it is awaited on rather than an oversight.
         */
        val SUSPENDING_COMPONENT_CALLS = setOf(
            "beginEditing",
            "checkpointDraft",
            "createNote",
            "createSharedNote",
            "moveNotesToTrash",
            "noteVersions",
            "refresh",
            "removeLocalData",
            "renameNote",
            "restoreNoteVersion",
            "restoreTrashedNote",
            "retryNote",
            "saveDraft",
            "setFavorite",
            "trashedNotes"
        )
    }
}
