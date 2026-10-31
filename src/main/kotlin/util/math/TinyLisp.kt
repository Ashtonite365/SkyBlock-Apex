/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: a tiny evaluator for the Lisp-style formulas in the item repo's constants/hotmlayout.json
 * (numbers, strings, keywords, symbols, if, defun and the arithmetic the repo uses). Written for Apex;
 * no external Lisp library is used or bundled.
 */
package dev.ashtonite365.apex.util.math

import kotlin.math.pow
import kotlin.math.roundToLong

object TinyLisp {
	sealed interface Node
	data class Num(val v: Double) : Node
	data class Str(val v: String) : Node
	data class Sym(val name: String) : Node
	data class ListNode(val items: List<Node>) : Node

	fun parse(src: String): List<Node> {
		val out = mutableListOf<Node>()
		var i = 0
		fun skip() { while (i < src.length && src[i].isWhitespace()) i++ }
		fun read(): Node {
			skip()
			val c = src[i]
			return when {
				c == '(' -> { i++; val items = mutableListOf<Node>(); skip()
					while (src[i] != ')') { items.add(read()); skip() }
					i++; ListNode(items) }
				c == '"' -> { val end = src.indexOf('"', i + 1); val s = src.substring(i + 1, end); i = end + 1; Str(s) }
				else -> { val start = i
					while (i < src.length && !src[i].isWhitespace() && src[i] != '(' && src[i] != ')') i++
					val tok = src.substring(start, i)
					tok.toDoubleOrNull()?.let { Num(it) } ?: Sym(tok) }
			}
		}
		skip()
		while (i < src.length) { out.add(read()); skip() }
		return out
	}

	class Env(val vars: MutableMap<String, Any> = mutableMapOf(), val parent: Env? = null) {
		val functions: MutableMap<String, Pair<List<String>, Node>> = parent?.functions ?: mutableMapOf()
		fun lookup(name: String): Any? = vars[name] ?: parent?.lookup(name)
	}

	private fun num(v: Any?): Double = when (v) {
		is Double -> v; is Boolean -> if (v) 1.0 else 0.0; is String -> v.toDoubleOrNull() ?: 0.0
		else -> error("not a number: $v")
	}

	fun eval(node: Node, env: Env): Any = when (node) {
		is Num -> node.v
		is Str -> node.v
		is Sym -> when {
			node.name == "true" -> true
			node.name == "false" -> false
			node.name.startsWith(":") -> node.name
			else -> env.lookup(node.name) ?: error("unbound ${node.name}")
		}
		is ListNode -> {
			val head = (node.items.firstOrNull() as? Sym)?.name ?: error("bad call")
			val args = node.items.drop(1)
			when (head) {
				"if" -> {
					val c = eval(args[0], env)
					if (c == true || (c is Double && c != 0.0)) eval(args[1], env) else eval(args[2], env)
				}
				"defun" -> {
					val name = (args[0] as Sym).name
					val params = (args[1] as ListNode).items.map { (it as Sym).name }
					env.functions[name] = params to args[2]; name
				}
				else -> {
					val v = args.map { eval(it, env) }
					when (head) {
						"+" -> v.sumOf(::num)
						"-" -> if (v.size == 1) -num(v[0]) else v.drop(1).fold(num(v[0])) { a, b -> a - num(b) }
						"*" -> v.fold(1.0) { a, b -> a * num(b) }
						"/" -> v.drop(1).fold(num(v[0])) { a, b -> a / num(b) }
						"pow" -> num(v[0]).pow(num(v[1]))
						"lt" -> num(v[0]) < num(v[1])
						"=" -> v[0] == v[1] || (v[0] is Double && v[1] is Double && num(v[0]) == num(v[1]))
						"round" -> num(v[0]).roundToLong().toDouble()
						"floor" -> kotlin.math.floor(num(v[0]))
						"ceil" -> kotlin.math.ceil(num(v[0]))
						else -> {
							val (params, body) = env.functions[head] ?: error("unknown function $head")
							eval(body, Env(params.zip(v).toMap().toMutableMap(), env))
						}
					}
				}
			}
		}
	}

	fun evaluate(prelude: List<String>, expr: String, vars: Map<String, Any>): Any? = runCatching {
		val env = Env(vars.toMutableMap())
		prelude.forEach { p -> parse(p).forEach { eval(it, env) } }
		parse(expr).map { eval(it, env) }.lastOrNull()
	}.getOrNull()
}
