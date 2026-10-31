// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable

typealias DefaultSource = net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource

private typealias Node<B> = ArgumentBuilder<DefaultSource, B>

private typealias Word = AnyCaseLiteral.Builder<DefaultSource>

private typealias Slot<V> = RequiredArgumentBuilder<DefaultSource, V>

private typealias Ctx = CommandContext<DefaultSource>

inline val <X : CommandContext<*>> X.context: X
	get() = this

operator fun <V : Any, X : CommandContext<*>> X.get(arg: ArgRef<V>): V = arg.get(this)

fun literal(name: String, setup: Word.() -> Unit): Word {
	val word = Word(name)
	word.setup()
	return word
}

data class ArgRef<V : Any>(val label: String, val type: ArgumentType<V>) {

	val valueClass: Class<*> by lazy { declaredValueClass(type.javaClass) }

	@JvmName("getWithThis")
	fun <S> CommandContext<S>.get(): V = get(this)

	fun <S> get(context: CommandContext<S>): V {
		var current: CommandContext<S> = context
		while (true) {
			try {
				@Suppress("UNCHECKED_CAST")
				return current.getArgument(label, valueClass) as V
			} catch (failure: IllegalArgumentException) {
				current = current.child ?: throw failure
			}
		}
	}

	private companion object {
		fun declaredValueClass(origin: Class<*>): Class<*> {
			var cls: Class<*> = origin
			while (true) {
				for (iface in cls.genericInterfaces) {
					if (iface is ParameterizedType && iface.rawType == ArgumentType::class.java)
						return classOf(iface.actualTypeArguments[0])
				}
				cls = cls.superclass ?: throw NoSuchElementException("${origin.name} does not implement ArgumentType<T> directly")
			}
		}

		fun classOf(t: Type): Class<*> = when (t) {
			is Class<*> -> t
			is ParameterizedType -> classOf(t.rawType)
			is TypeVariable<*> -> classOf(t.bounds.first())
			else -> Any::class.java
		}
	}
}

fun <V : Any> argument(name: String, argument: ArgumentType<V>, setup: Slot<V>.(ArgRef<V>) -> Unit): Slot<V> {
	val slot = RequiredArgumentBuilder.argument<DefaultSource, V>(name, argument)
	slot.setup(ArgRef(name, argument))
	return slot
}

fun <B : Node<B>, V : Any> B.onArgument(name: String, argument: ArgumentType<V>, setup: Slot<V>.(ArgRef<V>) -> Unit): B =
	then(dev.ashtonite365.apex.commands.argument(name, argument, setup))

fun <B : Slot<String>> B.suggestFrom(candidates: Ctx.() -> Iterable<String>) {
	suggests(SuggestionProvider<DefaultSource> { context, builder ->
		val typed = builder.remaining.lowercase()
		for (option in context.candidates())
			if (option.lowercase().startsWith(typed)) builder.suggest(option)
		builder.buildFuture()
	})
}

fun <B : Node<B>> B.onLiteral(name: String, setup: Word.() -> Unit): B = then(literal(name, setup))

fun <B : Node<B>> B.then(child: Node<*>, more: B.() -> Unit): B {
	val self = then(child)
	self.more()
	return self
}

fun <B : Node<B>> B.onRun(action: Ctx.() -> Unit): B = executes(Command { context ->
	context.action()
	Command.SINGLE_SUCCESS
})
