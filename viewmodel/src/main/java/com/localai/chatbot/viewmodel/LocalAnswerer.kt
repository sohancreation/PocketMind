package com.localai.chatbot.viewmodel

import com.localai.chatbot.models.ModelList
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.Locale

/**
 * Deterministic, offline answers for queries where LLMs commonly hallucinate or fail:
 * - Basic arithmetic
 * - App-specific "what models can I download?" questions
 * - A tiny built-in knowledge base for a few common queries
 */
object LocalAnswerer {

    fun tryAnswer(userInputRaw: String): String? {
        val userInput = userInputRaw.trim()
        if (userInput.isBlank()) return null

        tryAnswerModelList(userInput)?.let { ans ->
            println("PocketMind_Log: LocalAnswerer handled model list query.")
            return ans
        }
        tryAnswerNewton(userInput)?.let { ans ->
            println("PocketMind_Log: LocalAnswerer handled Newton query.")
            return ans
        }
        tryAnswerMath(userInput)?.let { ans ->
            println("PocketMind_Log: LocalAnswerer handled math query: '$userInput' -> '$ans'")
            return ans
        }

        return null
    }

    private fun tryAnswerModelList(userInput: String): String? {
        val s = userInput.lowercase(Locale.ROOT)
        val asksModels = s.contains("model")
        val asksList = s.contains("list") || s.contains("available") || s.contains("download")
        if (!asksModels || !asksList) return null

        val lines = ModelList.models.mapIndexed { idx, m ->
            "${idx + 1}. ${m.name} (${m.size}) - ${m.description}"
        }
        return buildString {
            append("Downloadable models in this app:\n\n")
            append(lines.joinToString("\n"))
            append("\n\nTip: start with SmolLM2 360M (Mini) on low-end devices/emulators, or TinyLlama 1.1B on a real phone for better replies.")
        }
    }

    private fun tryAnswerNewton(userInput: String): String? {
        val s = userInput.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()
        val asksWho = s.startsWith("who is ") || s.startsWith("who was ")
        if (!asksWho) return null
        if (!s.contains("newton")) return null

        return "Sir Isaac Newton (1642–1727) was an English mathematician, physicist, and astronomer. He formulated the laws of motion and universal gravitation, wrote *Principia Mathematica*, and made major contributions to calculus and optics."
    }

    private fun tryAnswerMath(userInput: String): String? {
        // Extract a math-like expression from a question such as "what is 45+35" or "45 + 35?"
        val cleaned = userInput
            .lowercase(Locale.ROOT)
            .replace("=", " ")
            .replace("?", " ")
            .replace(",", " ")
            .trim()

        // Find the first plausible expression substring. Keep it conservative: only digits/operators/parens/dot/spaces.
        val exprMatch = Regex("[-+*/().\\d\\s]{3,}").find(cleaned) ?: return null
        val expr = exprMatch.value.trim()
        if (!expr.any { it == '+' || it == '-' || it == '*' || it == '/' }) return null
        if (!Regex("^[\\d\\s+\\-*/().]+$").matches(expr)) return null

        val result = runCatching { evalExpression(expr) }.getOrNull() ?: return null
        return formatNumber(result)
    }

    // --- Simple expression evaluator (shunting-yard), supports + - * / and parentheses.

    private sealed interface Tok {
        data class Num(val v: BigDecimal) : Tok
        data class Op(val c: Char) : Tok
        data object LParen : Tok
        data object RParen : Tok
    }

    private fun evalExpression(expr: String): BigDecimal {
        val tokens = tokenize(expr)
        val rpn = toRpn(tokens)
        return evalRpn(rpn)
    }

    private fun tokenize(expr: String): List<Tok> {
        val out = ArrayList<Tok>(expr.length / 2)
        var i = 0
        var prevWasValue = false

        while (i < expr.length) {
            val ch = expr[i]
            when {
                ch.isWhitespace() -> i++
                ch == '(' -> {
                    out.add(Tok.LParen)
                    prevWasValue = false
                    i++
                }
                ch == ')' -> {
                    out.add(Tok.RParen)
                    prevWasValue = true
                    i++
                }
                ch == '+' || ch == '*' || ch == '/' -> {
                    out.add(Tok.Op(ch))
                    prevWasValue = false
                    i++
                }
                ch == '-' -> {
                    // Unary minus: if it appears where a value is expected, fold into the number token.
                    if (!prevWasValue) {
                        val (num, next) = readNumber(expr, i)
                        out.add(Tok.Num(num))
                        prevWasValue = true
                        i = next
                    } else {
                        out.add(Tok.Op('-'))
                        prevWasValue = false
                        i++
                    }
                }
                ch.isDigit() || ch == '.' -> {
                    val (num, next) = readNumber(expr, i)
                    out.add(Tok.Num(num))
                    prevWasValue = true
                    i = next
                }
                else -> throw IllegalArgumentException("Invalid character: $ch")
            }
        }

        return out
    }

    private fun readNumber(s: String, start: Int): Pair<BigDecimal, Int> {
        var i = start
        var hasDot = false
        if (s[i] == '-') i++
        val begin = start
        while (i < s.length) {
            val ch = s[i]
            if (ch.isDigit()) {
                i++
                continue
            }
            if (ch == '.' && !hasDot) {
                hasDot = true
                i++
                continue
            }
            break
        }
        val raw = s.substring(begin, i).trim()
        if (raw == "-" || raw == "." || raw == "-.") throw IllegalArgumentException("Bad number")
        return BigDecimal(raw) to i
    }

    private fun prec(op: Char): Int = when (op) {
        '+', '-' -> 1
        '*', '/' -> 2
        else -> 0
    }

    private fun toRpn(tokens: List<Tok>): List<Tok> {
        val output = ArrayList<Tok>(tokens.size)
        val ops = ArrayDeque<Tok>()

        for (t in tokens) {
            when (t) {
                is Tok.Num -> output.add(t)
                is Tok.Op -> {
                    while (ops.isNotEmpty()) {
                        val top = ops.last()
                        if (top is Tok.Op && prec(top.c) >= prec(t.c)) {
                            output.add(ops.removeLast())
                        } else {
                            break
                        }
                    }
                    ops.addLast(t)
                }
                Tok.LParen -> ops.addLast(t)
                Tok.RParen -> {
                    while (ops.isNotEmpty() && ops.last() !is Tok.LParen) {
                        output.add(ops.removeLast())
                    }
                    if (ops.isEmpty() || ops.last() !is Tok.LParen) throw IllegalArgumentException("Mismatched parentheses")
                    ops.removeLast()
                }
            }
        }

        while (ops.isNotEmpty()) {
            val t = ops.removeLast()
            if (t is Tok.LParen) throw IllegalArgumentException("Mismatched parentheses")
            output.add(t)
        }
        return output
    }

    private fun evalRpn(rpn: List<Tok>): BigDecimal {
        val st = ArrayDeque<BigDecimal>()
        val mc = MathContext(34, RoundingMode.HALF_UP)
        for (t in rpn) {
            when (t) {
                is Tok.Num -> st.addLast(t.v)
                is Tok.Op -> {
                    val b = st.removeLastOrNull() ?: throw IllegalArgumentException("Bad expression")
                    val a = st.removeLastOrNull() ?: throw IllegalArgumentException("Bad expression")
                    val r = when (t.c) {
                        '+' -> a.add(b, mc)
                        '-' -> a.subtract(b, mc)
                        '*' -> a.multiply(b, mc)
                        '/' -> a.divide(b, 16, RoundingMode.HALF_UP) // bounded precision
                        else -> throw IllegalArgumentException("Bad op")
                    }
                    st.addLast(r)
                }
                Tok.LParen, Tok.RParen -> throw IllegalArgumentException("Unexpected paren")
            }
        }
        return st.singleOrNull() ?: throw IllegalArgumentException("Bad expression")
    }

    private fun formatNumber(v: BigDecimal): String {
        val stripped = v.stripTrailingZeros()
        return if (stripped.scale() <= 0) stripped.toPlainString()
        else stripped.setScale(stripped.scale().coerceAtMost(10), RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }
}
