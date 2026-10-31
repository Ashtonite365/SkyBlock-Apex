// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.events.IsSlotProtectedEvent
import dev.ashtonite365.apex.events.SlotRenderEvents
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.features.dungeon.TerminalSolver
import dev.ashtonite365.apex.test.golden.FakeMinecraft
import dev.ashtonite365.apex.test.golden.Harness
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import kotlin.random.Random
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
@Tag("screen")
class Batch53GoldenTest {
	companion object {
		fun stack(item: Item, count: Int = 1, name: String? = null, foil: Boolean = false) = ItemStack(item, count).also {
			if (name != null) it.set(DataComponents.CUSTOM_NAME, Component.literal(name))
			if (foil) it.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
		}
		val colours = DyeColor.entries
		val titles = listOf("Correct all the panes!", "Click in order!", "Change all to same color!", "Click the button on time!",
			"What starts with: 'A'?", "What starts with: 'r'?", "What starts with: 'AB'?", "Select all the RED items!", "Select all the LIGHT_GRAY items!",
			"Select all the SILVER items!", "Select all the light blue items!", " Correct all the panes! ", "Correct all the panes", "Spirit Leap", "Chest")
		val names = listOf("Apple", "apple pie", "Arrow", "Red Wool", "Light Gray Dye", "Light Grey Concrete", "Rose Red", "Light Blue Wool", "Blue Wool",
			"Silver Ingot", "Ruby", "red", "§aArrow", "Bone", "")
		fun randomStack(r: Random): ItemStack = when (r.nextInt(8)) {
			0 -> ItemStack.EMPTY
			1, 2 -> stack(Items.STAINED_GLASS_PANE.pick(listOf(DyeColor.RED, DyeColor.LIME).random(r)), r.nextInt(1, 15))
			3 -> stack(Items.STAINED_GLASS_PANE.pick(colours.random(r)), 1, names.random(r).takeIf { r.nextBoolean() })
			4 -> stack(Items.DYED_TERRACOTTA.pick(listOf(DyeColor.LIME, DyeColor.RED).random(r)))
			5 -> stack(listOf(Items.APPLE, Items.ARROW, Items.BONE, Items.REDSTONE).random(r), 1, names.random(r), r.nextInt(4) == 0)
			else -> stack(Items.STAINED_GLASS_PANE.pick(listOf(DyeColor.ORANGE, DyeColor.YELLOW, DyeColor.GREEN, DyeColor.BLUE, DyeColor.RED, DyeColor.MAGENTA, DyeColor.BLACK).random(r)), 1, names.random(r).takeIf { r.nextInt(3) == 0 }, r.nextInt(6) == 0)
		}
		private val unsafe = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").also { it.isAccessible = true }.get(null) as sun.misc.Unsafe
		val gfx = unsafe.allocateInstance(GuiGraphicsExtractor::class.java) as GuiGraphicsExtractor
		val opts = listOf("highlight", "order-numbers", "block-wrong-clicks", "rubix", "melody", "first-click-protection")
	}

	@Test
	fun solver() {
		val out = mutableListOf<Pair<String, String>>()
		val r = Random(5353)
		if (runCatching { dev.ashtonite365.apex.features.items.ItemCustomizer.DConfig.data }.isFailure) dev.ashtonite365.apex.features.items.ItemCustomizer.DConfig.explicitDefaultLoad()
		Harness.load(dev.ashtonite365.apex.features.items.ItemCustomizer.TConfig)
		val S = TerminalSolver
		for (case in 0 until 600) {
			val title = titles.random(r)
			val size = listOf(9, 18, 27, 36, 45, 54).random(r)
			val items = List(size) { randomStack(r) }
			val screen = dev.ashtonite365.apex.test.golden.Batch52GoldenTest.screen(title, items)
			val all = r.nextInt(3) != 0
			val mode = TerminalSolver.RubixModeOption.entries.random(r)
			val ms = listOf(0, 600).random(r)
			val values: Map<String, Any> = opts.associateWith { (all || r.nextBoolean()) as Any } + mapOf("rubix-mode" to mode, "first-click-ms" to ms)
			Harness.withOpts(TerminalSolver.TConfig, values) {
				val res = FakeMinecraft.withScreen(screen) {
					val slots = screen.menu.slots
					val sol = Golden.run { S.solution()?.map { it.index }.toString() }
					val rub = Golden.run { S.rubixClicks()?.toSortedMap().toString() }
					val renders = slots.joinToString("") { s ->
						listOf<(SlotRenderEvents.After) -> Unit>(S::onSlotRender, S::onOrderNumbers, S::onRubixRender, S::onMelodyRender).joinToString("") { h ->
							if (Golden.run { h(SlotRenderEvents.After(gfx, s)); "ok" } == "ok") "." else "x"
						} + " "
					}
					val clicks = slots.joinToString("") { s ->
						val ev = IsSlotProtectedEvent(s, ContainerInput.PICKUP, false, null, IsSlotProtectedEvent.MoveOrigin.INVENTORY_MOVE)
						Golden.run { S.onClick(ev); if (ev.isProtected) "P" else "-" }
					}
					S.onScreenTick(TickEvent(1))
					val first = IsSlotProtectedEvent(slots[0], ContainerInput.PICKUP, false, null, IsSlotProtectedEvent.MoveOrigin.INVENTORY_MOVE)
					val fc = Golden.run { S.onFirstClick(first); "${first.isProtected}" }
					"$sol|$rub|$renders|$clicks|$fc"
				}
				out += "ts|$case|$title|$values|" + items.joinToString(",") { "${it.item}x${it.count}:${it.customName?.string}:${it.has(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)}" } to res
			}
		}
		FakeMinecraft.withScreen(null) { S.onScreenTick(TickEvent(2)) }
		val n = Golden.verify("batch53-solver", out, minCases = 590)
		assertTrue(n >= 590)
	}
}
