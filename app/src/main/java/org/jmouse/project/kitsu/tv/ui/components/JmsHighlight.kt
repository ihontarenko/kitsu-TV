package org.jmouse.project.kitsu.tv.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/*
  ==============================================================================
  jMouse Script, coloured — a rule's source on the television.

  ⚠️ A PORT, NOT A GRAMMAR OF ITS OWN. The scanner is `jmsSyntax.ts` in
  `@jmouse/codemirror`, token for token: the same keyword sets, the same "a
  keyword is only a keyword where a keyword can be" order, the same facade /
  constant / field states. The browser and the television must colour one rule
  the same way, so a change there is a change here.

  ⚠️ AND THE COLOURS ARE THE LIBRARY'S, not Kitsu's: its dark palette's
  `--syntax-*` values. Decorative colour chosen for a near-black cinema shell
  would put whole classes of token a few percent from the background.

  It colours; it never decides. Whether a rule is valid is the server's answer.
  ==============================================================================
 */

private enum class JmsToken {
    Declaration, Keyword, Branch, Event, Facade, Member, Constant, Field, Definition,
    Text, Number, Comment, Operator, Assign, Punctuation, Literal, Variable,
}

/** The library's dark syntax palette — `@jmouse/codemirror/styles.css`, the default palette. */
private object SyntaxPalette {
    val keyword = Color(0xFFFF7EB6)
    val role = Color(0xFF7EE787)
    val string = Color(0xFF7FD8A6)
    val scope = Color(0xFFC3A6FF)
    val literal = Color(0xFFFFAB70)
    val property = Color(0xFF86D8F0)
    val declaration = Color(0xFFC3A6FF)
    val variable = Color(0xFFD9D6CF)
    val comment = Color(0xFF8B8880)
    val operator = Color(0xFFA9A69F)
    val punctuation = Color(0xFF8B8880)
}

private fun styleOf(token: JmsToken): SpanStyle = when (token) {
    JmsToken.Declaration -> SpanStyle(color = SyntaxPalette.keyword, fontWeight = FontWeight.SemiBold)
    JmsToken.Keyword, JmsToken.Branch -> SpanStyle(color = SyntaxPalette.keyword)
    JmsToken.Event -> SpanStyle(color = SyntaxPalette.role, fontWeight = FontWeight.SemiBold)
    JmsToken.Facade -> SpanStyle(color = SyntaxPalette.scope, fontWeight = FontWeight.SemiBold)
    JmsToken.Member, JmsToken.Field -> SpanStyle(color = SyntaxPalette.property)
    JmsToken.Constant, JmsToken.Number, JmsToken.Literal -> SpanStyle(color = SyntaxPalette.literal)
    JmsToken.Definition -> SpanStyle(color = SyntaxPalette.declaration, fontWeight = FontWeight.SemiBold)
    JmsToken.Text -> SpanStyle(color = SyntaxPalette.string)
    JmsToken.Comment -> SpanStyle(color = SyntaxPalette.comment, fontStyle = FontStyle.Italic)
    JmsToken.Operator -> SpanStyle(color = SyntaxPalette.operator)
    /* ⚠️ `=` apart from `==` — writing the comparison where the assignment was meant parses and
       reports nothing, so the one is coloured unlike the other. */
    JmsToken.Assign -> SpanStyle(color = SyntaxPalette.declaration, fontWeight = FontWeight.SemiBold)
    JmsToken.Punctuation -> SpanStyle(color = SyntaxPalette.punctuation)
    JmsToken.Variable -> SpanStyle(color = SyntaxPalette.variable)
}

private val DECLARATIONS = setOf("include", "script", "behaviour", "behavior")
private val KEYWORDS = setOf("on", "when", "do", "then", "end", "function", "local")
private val BRANCHES = setOf("if", "elseif", "else", "for", "return")
private val OPERATORS = setOf("and", "or", "not", "is", "in")
private val LITERALS = setOf("true", "false", "null", "none")

private val NUMBER = Regex("""\d+(\.\d+)?([eE][-+]?\d+)?""")
private val MULTI_OPERATOR = Regex("""==|!=|<>|<=|>=|&&|\|\||\?\?|->|\.\.""")
private val IDENTIFIER = Regex("""[A-Za-z_]\w*""")

private const val SINGLE_OPERATORS = "+-*/%<>!|&^~?"
private const val PUNCTUATION = "(){}[],;:"

/** What the token in front decided the next name is — the scanner's `expect`. */
private enum class Expecting { Facade, Member, Constant, Field, Event, Name }

/** Colours a whole `.jms` document. Every line starts afresh, as the library's scanner does. */
fun highlightJms(source: String): AnnotatedString = buildAnnotatedString {
    source.split('\n').forEachIndexed { index, line ->
        if (index > 0) {
            append('\n')
        }
        highlightLine(line)
    }
}

private fun AnnotatedString.Builder.highlightLine(line: String) {
    var position = 0
    var expect: Expecting? = null
    var afterFacade = false
    var lineStart = true

    fun emit(end: Int, token: JmsToken?) {
        val text = line.substring(position, end)
        if (token == null) {
            append(text)
        } else {
            withStyle(styleOf(token)) { append(text) }
        }
        position = end
    }

    while (position < line.length) {
        val character = line[position]

        if (character.isWhitespace()) {
            var end = position
            while (end < line.length && line[end].isWhitespace()) {
                end++
            }
            emit(end, null)
            continue
        }

        // ⚠️ The hash reaches a facade's constant right after a facade, and opens a comment anywhere else.
        if (character == '#' && afterFacade) {
            expect = Expecting.Constant
            afterFacade = false
            emit(position + 1, JmsToken.Operator)
            continue
        }
        if (character == '#') {
            emit(line.length, JmsToken.Comment)
            continue
        }

        val wasExpecting = expect
        val wasLineStart = lineStart
        expect = null
        lineStart = false

        if (character == '"' || character == '\'') {
            afterFacade = false
            var end = position + 1
            var escaped = false
            while (end < line.length) {
                val next = line[end]
                end++
                if (escaped) {
                    escaped = false
                } else if (next == '\\') {
                    escaped = true
                } else if (next == character) {
                    break
                }
            }
            emit(end, JmsToken.Text)
            continue
        }

        if (character == '@') {
            expect = Expecting.Facade
            afterFacade = false
            emit(position + 1, JmsToken.Operator)
            continue
        }

        // ⚠️ Only where a facade has just been named; everywhere else a colon is punctuation.
        if (character == ':' && afterFacade) {
            afterFacade = true
            emit(position + 1, JmsToken.Operator)
            continue
        }

        if (character == '$' && afterFacade) {
            expect = Expecting.Field
            afterFacade = false
            emit(position + 1, JmsToken.Operator)
            continue
        }

        afterFacade = false

        val number = NUMBER.matchAt(line, position)
        if (number != null) {
            emit(number.range.last + 1, JmsToken.Number)
            continue
        }

        val multiOperator = MULTI_OPERATOR.matchAt(line, position)
        if (multiOperator != null) {
            emit(multiOperator.range.last + 1, JmsToken.Operator)
            continue
        }

        if (character == '=') {
            emit(position + 1, JmsToken.Assign)
            continue
        }

        if (character in SINGLE_OPERATORS) {
            emit(position + 1, JmsToken.Operator)
            continue
        }

        if (character == '.') {
            expect = Expecting.Member
            emit(position + 1, JmsToken.Operator)
            continue
        }

        if (character in PUNCTUATION) {
            emit(position + 1, JmsToken.Punctuation)
            continue
        }

        val identifier = IDENTIFIER.matchAt(line, position)
        if (identifier == null) {
            emit(position + 1, null)
            continue
        }
        val word = identifier.value
        val end = identifier.range.last + 1

        // ⚠️ Before the keyword sets, and that order is the rule: the token in front decided that a
        // name goes here, so `@world.end()` and `on end do` are both names where they stand.
        val decided = when (wasExpecting) {
            Expecting.Facade -> {
                afterFacade = true
                JmsToken.Facade
            }
            Expecting.Member -> JmsToken.Member
            Expecting.Constant -> JmsToken.Constant
            Expecting.Field -> JmsToken.Field
            Expecting.Event -> JmsToken.Event
            Expecting.Name -> JmsToken.Definition
            null -> null
        }
        if (decided != null) {
            emit(end, decided)
            continue
        }

        val token = when {
            word == "on" -> {
                expect = Expecting.Event
                JmsToken.Keyword
            }
            word == "function" || word == "local" || word == "for" -> {
                expect = Expecting.Name
                if (word in BRANCHES) JmsToken.Branch else JmsToken.Keyword
            }
            // ⚠️ A file-scope word is only one at the start of a line.
            word in DECLARATIONS && wasLineStart -> JmsToken.Declaration
            word in OPERATORS -> JmsToken.Operator
            word in LITERALS -> JmsToken.Literal
            word in BRANCHES -> JmsToken.Branch
            word in KEYWORDS -> JmsToken.Keyword
            else -> JmsToken.Variable
        }
        emit(end, token)
    }
}
