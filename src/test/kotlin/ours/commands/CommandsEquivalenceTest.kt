// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.ours.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContextBuilder
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.mojang.brigadier.tree.CommandNode
import java.lang.reflect.Proxy
import net.minecraft.commands.CommandBuildContext
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.commands.AnyCaseLiteral
import dev.ashtonite365.apex.commands.DefaultSource
import dev.ashtonite365.apex.commands.DurationArgumentType
import dev.ashtonite365.apex.commands.RestArgumentType
import dev.ashtonite365.apex.commands.ArgRef
import dev.ashtonite365.apex.commands.apexCommand
import dev.ashtonite365.apex.commands.get
import dev.ashtonite365.apex.commands.literal
import dev.ashtonite365.apex.commands.suggestFrom
import dev.ashtonite365.apex.commands.onArgument
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.commands.onLiteral
import dev.ashtonite365.apex.features.debug.DebugLogger
import dev.ashtonite365.apex.test.ours.OursGolden

class CommandsEquivalenceTest {
	init {
		dev.ashtonite365.apex.test.ApexTestBootstrap.bootstrapMinecraft()
	}

	private val feedback = mutableListOf<String>()

	private val source: DefaultSource = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(DefaultSource::class.java)) { _, m, args ->
		when (m.name) {
			"sendFeedback" -> { feedback += "fb " + (args[0] as Component).string; null }
			"sendError" -> { feedback += "err " + (args[0] as Component).string; null }
			"hashCode" -> 1
			"equals" -> false
			"toString" -> "src"
			else -> null
		}
	} as DefaultSource

	private fun syntax(block: () -> Any?): String = try {
		"ok " + block()
	} catch (e: CommandSyntaxException) {
		"syntax ${e.rawMessage.string} cursor=${e.cursor} input=${e.input}"
	} catch (e: Exception) {
		"ex ${e.javaClass.simpleName} ${e.message?.replace(Regex("@[0-9a-f]+"), "@#")}"
	}

	@Test
	fun durationArgumentParsesTheSame() {
		val out = mutableListOf<String>()
		out += "regex ${DurationArgumentType.regex.pattern()} suffixes=${DurationArgumentType.timeSuffixes} examples=${DurationArgumentType.examples}"
		for (s in listOf("3m", "20s", "1h45m", "1h45m30s", "0s", "10", "m", "5x", "5m3", "3m 2s", "1h1h", "45s2m", "", "007s", "99999999999s", "2d", "3M", "1.5h", "\"3m\"", "5mfoo"))
			out += "parse [$s] ${syntax { val r = StringReader(s); DurationArgumentType.parse(r).toString() + " cursor=" + r.cursor }}"
		for (s in listOf("", "1", "12", "1m", "1m2", "1M 3", "x", "3 4"))
			out += "suggest [$s] ${DurationArgumentType.listSuggestions<Any>(CommandContextBuilder<Any>(null, Any(), null, 0).build(s), SuggestionsBuilder(s, 0)).get().list.map { it.text }}"
		for (s in listOf("", "abc", "a b c", "  lead"))
			out += "rest [$s] ${syntax { val r = StringReader(s); r.cursor = if (s.isEmpty()) 0 else 1; RestArgumentType.parse(r) + " cursor=" + r.cursor + " canRead=" + r.canRead() }}"
		OursGolden.check("commands-arguments", out)
	}

	@Test
	fun caseInsensitiveLiteralsResolveTheSame() {
		val out = mutableListOf<String>()
		val calls = mutableListOf<String>()
		val d = CommandDispatcher<Any>()
		val b = AnyCaseLiteral.Builder<Any>("MiXed")
			.executes { calls += "root"; 1 }
			.then(AnyCaseLiteral.Builder<Any>("Sub").executes { calls += "sub"; 2 })
			.then(com.mojang.brigadier.builder.RequiredArgumentBuilder.argument<Any, Int>("n", IntegerArgumentType.integer(0, 9)).executes { calls += "n" + IntegerArgumentType.getInteger(it, "n"); 3 })
		val node = d.register(b)
		out += "node ${node.javaClass.simpleName} literal=${node.literal} name=${node.name} str=$node usage=${node.usageText} children=${node.children.map { it.toString() }}"
		val rebuilt = node.createBuilder().build()
		out += "rebuilt ${rebuilt.javaClass.simpleName} $rebuilt cmd=${rebuilt.command != null} children=${rebuilt.children.size}"
		for (s in listOf("mixed", "MIXED", "MiXed sub", "mixed SUB", "mixedsub", "mix", "mixed 4", "mixed 12", "mixed sub extra", "MIXED  sub", "other", "mixed\tsub"))
			out += "exec [$s] ${syntax { d.execute(s, Any()) }} calls=$calls".also { calls.clear() }
		val parsed = d.parse("MIXED SU", Any())
		out += "parse partial ${parsed.reader.cursor} nodes=${parsed.context.nodes.map { it.node.toString() + "@" + it.range }}"
		out += "completions ${d.getCompletionSuggestions(d.parse("mixed ", Any())).get().list.map { it.text }} ${d.getCompletionSuggestions(d.parse("MIXED s", Any())).get().list.map { it.text }}"
		out += "smart ${d.getSmartUsage(node, Any()).values}"
		OursGolden.check("commands-literal", out)
	}

	@Test
	fun dslBuildsAndExecutesTheSame() {
		val out = mutableListOf<String>()
		val d = CommandDispatcher<DefaultSource>()
		val seen = mutableListOf<String>()
		d.register(literal("Root") {
			onRun { seen += "root" }
			onLiteral("Word") {
				onArgument("w", StringArgumentType.string()) { w ->
					suggestFrom { listOf("alpha", "Beta", "gamma", "ALPHABET") }
					onRun { seen += "w=" + this[w] }
					onArgument("rest", RestArgumentType) { rest ->
						onRun { seen += "w=" + get(w) + " rest=" + this[rest] }
					}
				}
			}
			onArgument("d", DurationArgumentType) { dur -> onRun { seen += "d=" + this[dur] } }
		})
		for (s in listOf("root", "ROOT word abc", "root Word abc the rest  here", "root 3m", "root 1h2m", "root word", "root 3q"))
			out += "exec [$s] ${syntax { d.execute(s, source) }} seen=$seen".also { seen.clear() }
		for (s in listOf("root word ", "root word a", "root word AL", "root word g"))
			out += "suggest [$s] ${d.getCompletionSuggestions(d.parse(s, source)).get().list.map { it.text }}"
		out += "argClass ${ArgRef("x", DurationArgumentType).valueClass.name} ${ArgRef("x", RestArgumentType).valueClass.name} ${ArgRef("x", StringArgumentType.string()).valueClass.name} ${ArgRef("x", IntegerArgumentType.integer()).valueClass.name}"
		out += "eq ${ArgRef("x", RestArgumentType) == ArgRef("x", RestArgumentType)} ${ArgRef("x", RestArgumentType)}"
		OursGolden.check("commands-dsl", out)
	}

	private fun dump(node: CommandNode<*>, depth: Int, out: MutableList<String>, filter: (String) -> Boolean = { true }) {
		for (child in node.children.sortedBy { it.name }) {
			if (!filter(child.name)) continue
			out += "  ".repeat(depth) + "${child.javaClass.simpleName} ${child.name} exec=${child.command != null} redirect=${child.redirect != null}"
			dump(child, depth + 1, out)
		}
	}

	@Test
	fun apexTreeIsTheSame() {
		val out = mutableListOf<String>()
		val ctx = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(CommandBuildContext::class.java)) { _, m, _ ->
			when (m.name) { "hashCode" -> 2; "equals" -> false; "toString" -> "ctx"; else -> null }
		} as CommandBuildContext
		val d = CommandDispatcher<DefaultSource>()
		val built = runCatching { d.register(apexCommand(ctx)) }
		out += "built ${built.exceptionOrNull()?.javaClass?.simpleName ?: "ok"}"
		val root = built.getOrNull()
		if (root != null) {
			out += "root ${root.javaClass.simpleName} ${root.name} exec=${root.command != null}"
			val builtIns = setOf("config", "buttons", "sendcoords", "storageoverview", "storage", "storagename", "repo", "price", "help")
			dump(root, 1, out) { it in builtIns }
			val dev = root.getChild("dev")
			if (dev != null) dump(dev, 1, out) { it == "debuglog" || it == "repo" }
			for (s in listOf("apex config toggle nosuchconfig prop", "apex dev debuglog toggle zz-ours-test", "apex dev debuglog toggle zz-ours-test"))
				out += "exec [$s] ${syntax { d.execute(s, source) }} fb=$feedback".also { feedback.clear() }
			out += "debuglog ${syntax { "zz-ours-test" in DebugLogger.EnabledLogs.data }}"
		}
		OursGolden.check("commands-tree", out)
	}
}
