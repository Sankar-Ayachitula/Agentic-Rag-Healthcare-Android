package ai.studio1119.medassist.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

/**
 * Minimal Markdown -> AnnotatedString for the assistant's replies.
 *
 * Covers what the LLM actually produces: **bold**, *italic*, `code`, # headings,
 * bullet and numbered lists, and simple | tables |. It runs on every typewriter
 * frame, so it must also cope with half-written text: an unclosed ** styles the
 * rest of the line instead of showing raw asterisks.
 */

private val HEADING = Regex("""^(#{1,6})\s+(.*)$""")
private val BULLET = Regex("""^(\s*)[-*+]\s+(.*)$""")
private val NUMBERED = Regex("""^(\s*)(\d+)[.)]\s+(.*)$""")
private val TABLE_SEPARATOR = Regex("""^\s*\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)*\|?\s*$""")
private val RULE = Regex("""^\s*(-{3,}|\*{3,}|_{3,})\s*$""")

fun markdownToAnnotated(markdown: String, codeBackground: Color): AnnotatedString =
    buildAnnotatedString {
        val lines = markdown.split("\n")
        var firstTableRow = true
        lines.forEachIndexed { index, line ->
            val isTableRow = line.trimStart().startsWith("|")
            if (!isTableRow) firstTableRow = true

            HEADING.matchEntire(line)?.let { match ->
                val size = if (match.groupValues[1].length <= 2) 19.sp else 17.sp
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = size)) {
                    appendInline(match.groupValues[2], codeBackground)
                }
            } ?: when {
                TABLE_SEPARATOR.matches(line) && line.contains("|") -> Unit  // |---|---|
                RULE.matches(line) -> Unit                                   // --- rule
                isTableRow -> {
                    // Render a table row as its cells separated by a dot; bold header.
                    val cells = line.trim().trim('|').split("|").map { it.trim() }
                    val row = cells.filter { it.isNotEmpty() }.joinToString("  ·  ")
                    if (firstTableRow) {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                            appendInline(row, codeBackground)
                        }
                        firstTableRow = false
                    } else {
                        appendInline(row, codeBackground)
                    }
                }
                BULLET.matches(line) -> {
                    val match = BULLET.matchEntire(line)!!
                    append(indent(match.groupValues[1]) + "•  ")
                    appendInline(match.groupValues[2], codeBackground)
                }
                NUMBERED.matches(line) -> {
                    val match = NUMBERED.matchEntire(line)!!
                    append(indent(match.groupValues[1]) + match.groupValues[2] + ".  ")
                    appendInline(match.groupValues[3], codeBackground)
                }
                else -> appendInline(line, codeBackground)
            }
            if (index < lines.lastIndex) append("\n")
        }
    }

private fun indent(leading: String) = "    ".repeat(leading.length / 2)

/** Inline spans: **bold**, *italic*, `code`. Markers are hidden. */
private fun AnnotatedString.Builder.appendInline(text: String, codeBackground: Color) {
    var bold = false
    var italic = false
    var code = false
    val run = StringBuilder()

    fun flush() {
        if (run.isEmpty()) return
        val style = SpanStyle(
            fontWeight = if (bold) FontWeight.SemiBold else null,
            fontStyle = if (italic) FontStyle.Italic else null,
            fontFamily = if (code) FontFamily.Monospace else null,
            background = if (code) codeBackground else Color.Unspecified,
        )
        withStyle(style) { append(run.toString()) }
        run.clear()
    }

    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            c == '`' -> { flush(); code = !code; i++ }
            code -> { run.append(c); i++ }
            text.startsWith("**", i) -> { flush(); bold = !bold; i += 2 }
            c == '*' && isItalicMarker(text, i, italic) -> { flush(); italic = !italic; i++ }
            else -> { run.append(c); i++ }
        }
    }
    flush()
}

/**
 * A lone * opens italics only before a non-space and closes them only after
 * one, so "5 * 3" stays literal. Underscores are never treated as markers:
 * symptom labels like skin_rash contain them.
 */
private fun isItalicMarker(text: String, i: Int, italicOpen: Boolean): Boolean =
    if (italicOpen) {
        i > 0 && !text[i - 1].isWhitespace()
    } else {
        i + 1 < text.length && !text[i + 1].isWhitespace()
    }
