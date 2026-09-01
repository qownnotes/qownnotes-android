package org.qownnotes.mobile.markdown

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import io.noties.markwon.syntax.SyntaxHighlight
import java.util.Locale

internal class BoundedSyntaxHighlight(context: Context) : SyntaxHighlight {
    private val palette = if (context.resources.configuration.uiMode and
        Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    ) {
        SyntaxPalette(
            keyword = Color.rgb(204, 120, 50),
            string = Color.rgb(106, 171, 115),
            comment = Color.rgb(128, 128, 128),
            number = Color.rgb(104, 151, 187),
            tag = Color.rgb(229, 192, 123)
        )
    } else {
        SyntaxPalette(
            keyword = Color.rgb(0, 51, 179),
            string = Color.rgb(0, 110, 40),
            comment = Color.rgb(90, 100, 110),
            number = Color.rgb(120, 70, 150),
            tag = Color.rgb(145, 60, 15)
        )
    }

    override fun highlight(info: String?, code: String): CharSequence {
        val language = normalizeFenceLanguage(info) ?: return code
        if (code.length > MAX_HIGHLIGHTED_CODE_LENGTH) return code

        val output = SpannableString(code)
        val occupied = BooleanArray(code.length)
        output.applyMatches(STRING, palette.string, occupied)
        if (language in HASH_COMMENT_LANGUAGES) {
            output.applyMatches(HASH_COMMENT, palette.comment, occupied)
        } else if (language !in MARKUP_LANGUAGES) {
            output.applyMatches(C_STYLE_COMMENT, palette.comment, occupied)
        }
        if (language in MARKUP_LANGUAGES) {
            output.applyMatches(MARKUP_TAG, palette.tag, occupied)
        }
        KEYWORD_PATTERNS[language]?.let { pattern ->
            output.applyMatches(pattern, palette.keyword, occupied)
        }
        output.applyMatches(NUMBER, palette.number, occupied)
        return output
    }
}

internal fun normalizeFenceLanguage(info: String?): String? {
    val language = info?.trim()?.takeWhile { !it.isWhitespace() }
        ?.lowercase(Locale.ROOT).orEmpty()
    val normalized = LANGUAGE_ALIASES[language] ?: language
    return normalized.takeIf(KEYWORDS::containsKey)
}

private fun Spannable.applyMatches(regex: Regex, color: Int, occupied: BooleanArray) {
    regex.findAll(toString()).forEach { match ->
        val range = match.range
        if (range.none { occupied[it] }) {
            setSpan(
                ForegroundColorSpan(color),
                range.first,
                range.last + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            range.forEach { occupied[it] = true }
        }
    }
}

private data class SyntaxPalette(
    val keyword: Int,
    val string: Int,
    val comment: Int,
    val number: Int,
    val tag: Int
)

private val KEYWORDS = mapOf(
    "c" to keywordSet(
        "auto break case char const continue default do double else enum float for if int long",
        "return short signed sizeof static struct switch typedef union unsigned void volatile while"
    ),
    "cpp" to keywordSet(
        "alignas bool break case class concept const constexpr continue delete else for if int",
        "namespace new noexcept nullptr private protected public requires return template this throw",
        "using virtual void while"
    ),
    "java" to keywordSet(
        "abstract boolean break byte case catch char class const continue default do double else enum",
        "extends final finally float for if implements import instanceof int interface long native new",
        "null package private protected public return short static super switch synchronized this",
        "throw throws transient try void volatile while"
    ),
    "kotlin" to keywordSet(
        "as break by catch class continue data do else false finally for fun if import in interface is",
        "null object package return sealed super this throw true try typealias val var when while"
    ),
    "javascript" to keywordSet(
        "async await break case catch class const continue default delete do else export extends false",
        "finally for function if import in instanceof let new null return super switch this throw true",
        "try typeof undefined var while yield"
    ),
    "json" to keywordSet("false null true"),
    "python" to keywordSet(
        "False None True and as assert async await break class continue def del elif else except",
        "finally for from global if import in is lambda nonlocal not or pass raise return try while",
        "with yield"
    ),
    "sql" to keywordSet(
        "ALTER AND AS ASC BEGIN BY CASE CREATE DELETE DESC DISTINCT DROP ELSE END FROM GROUP HAVING",
        "INSERT INTO JOIN LIMIT NOT NULL ON OR ORDER SELECT SET TABLE THEN UNION UPDATE VALUES WHEN WHERE"
    ),
    "yaml" to keywordSet("false null true"),
    "markup" to emptySet(),
    "css" to keywordSet("important"),
    "makefile" to keywordSet(
        "define else endef endif export if ifdef ifeq ifndef ifneq include override private",
        "undefine unexport vpath"
    )
)

private fun keywordSet(vararg groups: String): Set<String> =
    groups.flatMap { it.split(' ') }.toSet()

private val KEYWORD_PATTERNS = KEYWORDS.filterValues {
    it.isNotEmpty()
}.mapValues { (language, keywords) ->
    val pattern = "\\b(?:${keywords.joinToString("|")})\\b"
    if (language == "sql") Regex(pattern, RegexOption.IGNORE_CASE) else Regex(pattern)
}

private val LANGUAGE_ALIASES = mapOf(
    "c++" to "cpp",
    "cxx" to "cpp",
    "html" to "markup",
    "js" to "javascript",
    "make" to "makefile",
    "py" to "python",
    "xml" to "markup",
    "yml" to "yaml"
)
private val HASH_COMMENT_LANGUAGES = setOf("python", "yaml", "makefile")
private val MARKUP_LANGUAGES = setOf("markup")
private val STRING = Regex("\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'")
private val C_STYLE_COMMENT = Regex("//[^\\n]*|/\\*[\\s\\S]*?\\*/")
private val HASH_COMMENT = Regex("#[^\\n]*")
private val MARKUP_TAG = Regex("</?[A-Za-z][^>]*>")
private val NUMBER = Regex("\\b(?:0[xX][0-9A-Fa-f]+|\\d+(?:\\.\\d+)?)\\b")
private const val MAX_HIGHLIGHTED_CODE_LENGTH = 64 * 1024
