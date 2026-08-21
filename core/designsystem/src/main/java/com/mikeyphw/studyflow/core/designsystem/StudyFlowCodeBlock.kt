package com.mikeyphw.studyflow.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikeyphw.studyflow.core.model.ProgrammingLanguage

@Immutable
data class StudyFlowSyntaxTheme(
    val plain: Color,
    val keyword: Color,
    val stringLiteral: Color,
    val numberLiteral: Color,
    val comment: Color,
    val symbol: Color,
    val lineNumber: Color,
)

@Composable
fun studyFlowSyntaxTheme(): StudyFlowSyntaxTheme = StudyFlowSyntaxTheme(
    plain = MaterialTheme.colorScheme.onSurface,
    keyword = MaterialTheme.colorScheme.primary,
    stringLiteral = MaterialTheme.colorScheme.tertiary,
    numberLiteral = MaterialTheme.colorScheme.secondary,
    comment = MaterialTheme.colorScheme.onSurfaceVariant,
    symbol = MaterialTheme.colorScheme.outline,
    lineNumber = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
fun StudyFlowCodeBlock(
    code: String,
    language: ProgrammingLanguage,
    modifier: Modifier = Modifier,
    title: String? = null,
    showLineNumbers: Boolean = true,
    copyEnabled: Boolean = true,
) {
    val clipboard = LocalClipboardManager.current
    val syntaxTheme = studyFlowSyntaxTheme()
    val highlighted = remember(code, language, syntaxTheme) {
        StudyFlowSyntaxHighlighter.highlight(
            code = code,
            language = language,
            theme = syntaxTheme,
        )
    }
    val scrollState = rememberScrollState()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (!title.isNullOrBlank()) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    Text(
                        text = language.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (copyEnabled) {
                    IconButton(onClick = { clipboard.setText(AnnotatedString(code)) }) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "Copy code",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            SelectionContainer {
                Row(modifier = Modifier.horizontalScroll(scrollState)) {
                    if (showLineNumbers) {
                        Text(
                            text = code.lineNumberColumn(),
                            style = StudyFlowCodeTextStyle.copy(color = syntaxTheme.lineNumber),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Box {
                        Text(
                            text = highlighted,
                            style = StudyFlowCodeTextStyle,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

private val StudyFlowCodeTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp,
    lineHeight = 19.sp,
)

private fun String.lineNumberColumn(): String {
    val count = lineSequence().count().coerceAtLeast(1)
    val width = count.toString().length.coerceAtLeast(2)
    return (1..count).joinToString(separator = "\n") { number -> number.toString().padStart(width) }
}

object StudyFlowSyntaxHighlighter {
    fun highlight(
        code: String,
        language: ProgrammingLanguage,
        theme: StudyFlowSyntaxTheme,
    ): AnnotatedString {
        val keywordRegex = keywordRegexFor(language)
        return buildAnnotatedString {
            append(code)
            addStyle(SpanStyle(color = theme.plain), 0, code.length)
            Regex("""\b\d+(?:\.\d+)?\b""").findAll(code).forEach { match ->
                addStyle(SpanStyle(color = theme.numberLiteral), match.range.first, match.range.last + 1)
            }
            Regex("""[{}()\[\].,=:+\-*/%!?|&]""").findAll(code).forEach { match ->
                addStyle(SpanStyle(color = theme.symbol), match.range.first, match.range.last + 1)
            }
            keywordRegex.findAll(code).forEach { match ->
                addStyle(
                    SpanStyle(color = theme.keyword, fontWeight = FontWeight.SemiBold),
                    match.range.first,
                    match.range.last + 1,
                )
            }
            stringRegex.findAll(code).forEach { match ->
                addStyle(SpanStyle(color = theme.stringLiteral), match.range.first, match.range.last + 1)
            }
            commentRegexFor(language).findAll(code).forEach { match ->
                addStyle(SpanStyle(color = theme.comment), match.range.first, match.range.last + 1)
            }
        }
    }

    private fun keywordRegexFor(language: ProgrammingLanguage): Regex {
        val keywords = when (language) {
            ProgrammingLanguage.KOTLIN -> kotlinKeywords
            ProgrammingLanguage.JAVA -> javaKeywords
            ProgrammingLanguage.PYTHON -> pythonKeywords
            ProgrammingLanguage.JAVASCRIPT,
            ProgrammingLanguage.TYPESCRIPT,
            -> javascriptKeywords
            ProgrammingLanguage.SHELL -> shellKeywords
            ProgrammingLanguage.SQL -> sqlKeywords
            ProgrammingLanguage.HTML -> htmlKeywords
            ProgrammingLanguage.CSS -> cssKeywords
            ProgrammingLanguage.JSON,
            ProgrammingLanguage.YAML,
            -> dataKeywords
            ProgrammingLanguage.MARKDOWN -> markdownKeywords
            ProgrammingLanguage.C,
            ProgrammingLanguage.CPP,
            -> cFamilyKeywords
            ProgrammingLanguage.GO -> goKeywords
            ProgrammingLanguage.RUST -> rustKeywords
            ProgrammingLanguage.OTHER -> emptySet()
        }
        if (keywords.isEmpty()) return Regex("a^")
        return Regex("\\b(${keywords.joinToString("|") { Regex.escape(it) }})\\b")
    }

    private fun commentRegexFor(language: ProgrammingLanguage): Regex = when (language) {
        ProgrammingLanguage.PYTHON,
        ProgrammingLanguage.SHELL,
        ProgrammingLanguage.YAML,
        -> Regex("""(?m)#.*$""")
        ProgrammingLanguage.SQL -> Regex("""(?m)--.*$""")
        ProgrammingLanguage.HTML,
        ProgrammingLanguage.MARKDOWN,
        -> Regex("""<!--(?s:.*?)-->""")
        else -> Regex("""//.*|/\*(?s:.*?)\*/""")
    }
}

private val stringRegex = Regex(""""(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|`(?:\\.|[^`\\])*`""")
private val kotlinKeywords = setOf(
    "as", "break", "class", "continue", "data", "else", "false", "for", "fun", "if",
    "in", "interface", "is", "null", "object", "package", "return", "sealed", "true", "val", "var", "when", "while",
)
private val javaKeywords = setOf(
    "abstract", "boolean", "break", "class", "else", "extends", "false", "final", "for", "if", "implements",
    "import", "interface", "new", "null", "private", "protected", "public", "return", "static", "true", "void", "while",
)
private val pythonKeywords = setOf(
    "and", "as", "class", "def", "elif", "else", "False", "for", "from", "if", "import", "in", "is", "lambda",
    "None", "not", "or", "pass", "return", "True", "while", "with", "yield",
)
private val javascriptKeywords = setOf(
    "async", "await", "class", "const", "else", "export", "false", "for", "function", "if", "import", "interface",
    "let", "new", "null", "return", "true", "type", "undefined", "while",
)
private val shellKeywords = setOf(
    "case", "do", "done", "elif", "else", "esac", "fi", "for", "function", "if", "in", "then", "while",
)
private val sqlKeywords = setOf(
    "ALTER", "AND", "AS", "BY", "CREATE", "DELETE", "FROM", "GROUP", "INSERT", "INTO", "JOIN", "NOT", "NULL",
    "OR", "ORDER", "SELECT", "TABLE", "UPDATE", "VALUES", "WHERE",
)
private val htmlKeywords = setOf(
    "html", "head", "body", "div", "span", "main", "section", "script", "style", "class", "id",
)
private val cssKeywords = setOf(
    "color", "display", "flex", "grid", "margin", "padding", "background", "border", "font", "width", "height",
)
private val dataKeywords = setOf("true", "false", "null")
private val markdownKeywords = setOf("TODO", "NOTE", "IMPORTANT")
private val cFamilyKeywords = setOf(
    "auto", "break", "case", "char", "class", "const", "continue", "else", "enum", "false", "for", "if", "include",
    "int", "long", "namespace", "return", "struct", "true", "void", "while",
)
private val goKeywords = setOf(
    "break", "case", "chan", "const", "defer", "else", "fallthrough", "for", "func", "go", "if", "import", "package",
    "return", "struct", "type", "var",
)
private val rustKeywords = setOf(
    "async", "await", "break", "const", "else", "enum", "false", "fn", "for", "if", "impl", "let",
    "match", "mod", "mut", "pub", "return", "struct", "true", "use", "while",
)
