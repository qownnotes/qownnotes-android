package org.qownnotes.mobile.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupplementalSyntaxHighlightTest {
    @Test
    fun findsEveryRequiredSupplementalSyntaxWithoutChangingSource() {
        val source =
            """
                ---
                title: Test
                ---
                # ATX heading
                Setext heading
                ===
                **strong** _emphasis_ ~~strike~~
                - [x] task
                > quote
                `inline`
                ~~~kotlin
                val answer = 42
                ~~~
                [link](https://example.com) ![image](https://example.com/image.png)
                | A | B |
                [[Wiki]]
                <!-- comment -->
            """.trimIndent()

        val ranges = findSupplementalSyntax(source)

        assertEquals(MarkdownSyntax.entries.toSet(), ranges.mapTo(mutableSetOf()) { it.syntax })
        assertTrue(ranges.all { it.start in source.indices && it.end in 1..source.length })
        assertEquals(
            listOf("![image](https://example.com/image.png)"),
            ranges.filter { it.syntax == MarkdownSyntax.IMAGE }
                .map { source.substring(it.start, it.end) }
        )
        assertEquals(
            listOf("[link](https://example.com)"),
            ranges.filter { it.syntax == MarkdownSyntax.LINK }
                .map { source.substring(it.start, it.end) }
        )
    }

    @Test
    fun scansALargeDocumentWithoutProducingInvalidRanges() {
        val source = buildString {
            repeat(10_000) { index ->
                append("## Heading ").append(index).append('\n')
                append("- [ ] item with [[Wiki]] and `code`").append('\n')
            }
        }

        val ranges = findSupplementalSyntax(source)

        assertEquals(50_000, ranges.size)
        assertTrue(ranges.all { it.start >= 0 && it.start < it.end && it.end <= source.length })
    }

    @Test
    fun findsBareWebAddressesWithoutIncludingSentencePunctuation() {
        val source =
            "Visit https://example.com/docs, www.example.org or example.net. " +
                "Keep [label](https://marked.example) as one link."

        val links = findSupplementalSyntax(source)
            .filter { it.syntax == MarkdownSyntax.LINK }
            .map { source.substring(it.start, it.end) }

        assertEquals(
            listOf(
                "https://example.com/docs",
                "www.example.org",
                "example.net",
                "[label](https://marked.example)"
            ),
            links
        )
    }
}
