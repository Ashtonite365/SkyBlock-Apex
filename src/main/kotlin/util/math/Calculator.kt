// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.util.math

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

object Calculator {
	class CalculatorException(message: String) : Exception(message)

	fun interface VariableProvider {
		fun resolve(name: String): BigDecimal?
	}

	val NO_VARIABLES = VariableProvider { null }

	private val precision = MathContext.DECIMAL64
	private val suffixes = mapOf(
		'k' to BigDecimal("1000"), 'm' to BigDecimal("1000000"), 'b' to BigDecimal("1000000000"), 't' to BigDecimal("1000000000000"),
		's' to BigDecimal("64"), 'e' to BigDecimal("160"), '%' to BigDecimal("0.01"),
	)
	private const val MAX_POWER = 999

	fun evaluate(input: String, variables: VariableProvider = NO_VARIABLES): BigDecimal =
		Evaluation(input.replace(",", "").lowercase(), variables).whole()

	fun evaluateOrNull(input: String, variables: VariableProvider = NO_VARIABLES): BigDecimal? =
		try {
			evaluate(input, variables)
		} catch (_: CalculatorException) {
			null
		} catch (_: ArithmeticException) {
			null
		}

	fun isPlainNumber(input: String): Boolean {
		val s = input.replace(",", "").trim()
		val digitsFrom = if (s.startsWith('-')) 1 else 0
		val point = s.indexOf('.', digitsFrom)
		val whole = if (point < 0) s.substring(digitsFrom) else s.substring(digitsFrom, point)
		val fraction = if (point < 0) null else s.substring(point + 1)
		fun allDigits(t: String) = t.isNotEmpty() && t.all { it in '0'..'9' }
		return allDigits(whole) && (fraction == null || allDigits(fraction))
	}

	fun format(value: BigDecimal): String = value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

	private class Evaluation(private val text: String, private val variables: VariableProvider) {
		private var at = 0

		private fun fail(why: String): Nothing = throw CalculatorException(why)

		private fun next(): Char? {
			while (at < text.length && text[at] == ' ') at++
			return if (at < text.length) text[at] else null
		}

		private fun take(c: Char): Boolean = (next() == c).also { if (it) at++ }

		fun whole(): BigDecimal {
			if (text.isBlank()) fail("Nothing to calculate")
			val result = sum()
			if (next() != null) fail("Unexpected '${text[at]}' at $at")
			return result
		}

		private fun sum(): BigDecimal {
			var acc = product()
			while (true) {
				acc = when {
					take('+') -> acc.add(product(), precision)
					take('-') -> acc.subtract(product(), precision)
					else -> return acc
				}
			}
		}

		private fun product(): BigDecimal {
			var acc = exponent()
			while (true) {
				acc = when {
					take('*') || take('x') -> acc.multiply(exponent(), precision)
					take('/') -> {
						val by = exponent()
						if (by.signum() == 0) fail("Division by zero")
						acc.divide(by, precision)
					}
					else -> return acc
				}
			}
		}

		private fun exponent(): BigDecimal {
			val base = signed()
			if (!take('^')) return base
			val power = exponent()
			val n = power.toInt()
			if (power.compareTo(BigDecimal(n)) != 0 || n !in -MAX_POWER..MAX_POWER) fail("Exponent must be a whole number up to $MAX_POWER")
			return base.pow(n, precision)
		}

		private fun signed(): BigDecimal = when {
			take('-') -> signed().negate()
			take('+') -> signed()
			else -> withSuffixes(value())
		}

		private fun withSuffixes(v: BigDecimal): BigDecimal {
			var acc = v
			while (true) {
				val factor = next()?.let(suffixes::get) ?: return acc
				at++
				acc = acc.multiply(factor, precision)
			}
		}

		private fun value(): BigDecimal {
			val c = next() ?: fail("Unexpected end")
			return when {
				c == '(' -> {
					at++
					val inner = sum()
					if (!take(')')) fail("Missing )")
					inner
				}
				c.isLetter() -> variable()
				else -> number(c)
			}
		}

		private fun variable(): BigDecimal {
			val from = at
			while (at < text.length && text[at].let { it.isLetterOrDigit() || it == '_' || it == ';' || it == ':' }) at++
			val name = text.substring(from, at).uppercase()
			return variables.resolve(name) ?: fail("Unknown variable $name")
		}

		private fun number(first: Char): BigDecimal {
			val from = at
			while (at < text.length && text[at].let { it.isDigit() || it == '.' || it == '_' }) at++
			if (at == from) fail("Unexpected '$first' at $at")
			return text.substring(from, at).filterNot { it == '_' }.toBigDecimalOrNull() ?: fail("Bad number")
		}
	}
}
