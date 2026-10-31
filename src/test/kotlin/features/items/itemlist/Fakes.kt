// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.items.itemlist

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import me.shedaniel.math.Rectangle
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import dev.ashtonite365.apex.features.items.recipes.itemlist.ItemListPage
import dev.ashtonite365.apex.features.items.recipes.itemlist.PageCanvas
import dev.ashtonite365.apex.repo.SBItemStack
import dev.ashtonite365.apex.repo.recipes.RecipeLayouter
import dev.ashtonite365.apex.util.ItemId

class Recorder {
	val calls = mutableListOf<Pair<String, List<Any?>>>()

	fun named(name: String): List<List<Any?>> = calls.filter { it.first == name }.map { it.second }

	@Suppress("UNCHECKED_CAST")
	fun <T> fake(type: Class<T>, answer: (String, Array<Any?>) -> Any? = { _, _ -> null }): T =
		Proxy.newProxyInstance(type.classLoader, arrayOf(type), InvocationHandler { proxy, m, a ->
			val args = a ?: arrayOf()
			when (m.name) {
				"equals" -> proxy === args[0]
				"hashCode" -> System.identityHashCode(proxy)
				"toString" -> "fake ${type.simpleName}"
				else -> {
					calls += m.name to args.toList()
					answer(m.name, args) ?: zero(m.returnType)
				}
			}
		}) as T

	companion object {
		fun zero(t: Class<*>): Any? = when (t) {
			java.lang.Boolean.TYPE -> false
			java.lang.Integer.TYPE -> 0
			java.lang.Long.TYPE -> 0L
			java.lang.Double.TYPE -> 0.0
			java.lang.Float.TYPE -> 0f
			else -> null
		}
	}
}

fun sb(id: String, count: Int = 1) = SBItemStack(ItemId(id), count)

class FakePage(override val id: Identifier, private val recipes: List<String>, private val craft: (() -> Unit)? = null) : ItemListPage<String> {
	override val title: Component = Component.literal("Fake ${id.path}")
	override val icon: ItemStack get() = ItemStack.EMPTY
	override val width = 100
	override val height = 50
	override fun allRecipes(): List<String> = recipes
	override fun draw(recipe: String, bounds: Rectangle, canvas: PageCanvas) {
		canvas.cyclingSlotAt(bounds.x + 1, bounds.y + 1, listOf(sb("IN_$recipe"), sb("IN2_$recipe")), RecipeLayouter.SlotRole.SMALL_INPUT)
		canvas.cyclingSlotAt(bounds.x + 60, bounds.y + 10, listOf(sb("OUT_$recipe")), RecipeLayouter.SlotRole.BIG_OUTPUT)
		canvas.cyclingSlotAt(bounds.x + 30, bounds.y + 30, listOf(sb("SHOWN_$recipe")), RecipeLayouter.SlotRole.DISPLAY)
		canvas.placeLabel(bounds.centerX, bounds.y, Component.literal(recipe))
	}
	override fun craftAction(recipe: String): (() -> Unit)? = craft
}

fun apexId(path: String): Identifier = Identifier.fromNamespaceAndPath("apex", path)
