// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.inventory.golden

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemLore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import me.shedaniel.math.Rectangle
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.features.inventory.storageoverlay.StorageMenuHandle
import dev.ashtonite365.apex.features.inventory.storageoverlay.StorageData
import dev.ashtonite365.apex.features.inventory.storageoverlay.StorageOverlay
import dev.ashtonite365.apex.features.inventory.storageoverlay.StorageOverlayGeometry
import dev.ashtonite365.apex.features.inventory.storageoverlay.StorageOverlayScreen
import dev.ashtonite365.apex.features.inventory.storageoverlay.StorageOverviewLayout
import dev.ashtonite365.apex.features.inventory.storageoverlay.StorageOverviewScreen
import dev.ashtonite365.apex.features.inventory.storageoverlay.StoragePageSlot
import dev.ashtonite365.apex.features.inventory.storageoverlay.VirtualInventory
import dev.ashtonite365.apex.util.mc.KeptStack
import dev.ashtonite365.apex.util.mc.NeedsComponents

@OptIn(NeedsComponents::class)
@ExtendWith(dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension::class)
class StorageGoldenTest {
	companion object {
		private const val LINE_HEIGHT = 9

		fun stack(spec: String): ItemStack {
			val pieces = spec.split("^")
			val lore = pieces.drop(1)
			val (idAndCount, name) = pieces[0].split("~", limit = 2).let { it[0] to it.getOrNull(1) }
			val id = idAndCount.substringBefore('*')
			val count = idAndCount.substringAfter('*', "1").toInt()
			if (id == "air") return ItemStack.EMPTY
			return ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(id)), count).also { s ->
				if (name != null) s.set(DataComponents.CUSTOM_NAME, Component.literal(name))
				if (lore.isNotEmpty()) s.set(DataComponents.LORE, ItemLore(lore.map { Component.literal(it) }))
			}
		}

		private fun pagesOf(spec: String): List<Int?> = if (spec.isEmpty()) emptyList() else spec.split(",").map { it.toIntOrNull() }
		private fun Rectangle.csv() = "$x,$y,$width,$height"
		private fun Pair<Int, Int>.csv() = "$first,$second"

		fun configure(values: Map<String, JsonPrimitive> = emptyMap()) =
			StorageOverlay.TConfig.loadFrom(Unit, JsonObject(mapOf(StorageOverlay.TConfig.name to JsonObject(values))))
	}

	init {
		configure()

		dev.ashtonite365.apex.features.items.ItemCustomizer.DConfig.loadFrom(Unit, JsonObject(mapOf()))
	}

	@Test fun pageSlots() {
		val n = Golden.check("storage-page-slot") { key ->
			val parts = key.split(" ")
			when (parts[0]) {
				"overview" -> "${StoragePageSlot.fromOverviewSlotIndex(parts[1].toInt())?.index}"
				"ender" -> "${StoragePageSlot.ofEnderChestPage(parts[1].toInt()).index}"
				"backpack" -> "${StoragePageSlot.ofBackPackPage(parts[1].toInt()).index}"
				else -> StoragePageSlot(key.toInt()).let { p ->
					listOf(p.isEnderChest, p.isBackPack, p.slotIndexInOverviewPage, p.defaultName(),
						Apex.json.encodeToString(StoragePageSlot.serializer(), p)).joinToString("|")
				}
			}
		}
		assertEquals(117, n)
	}

	@Test fun titles() {
		Golden.check("storage-titles") { title ->
			when {
				StorageMenuHandle.isOverviewTitle(title) -> "overview"
				else -> StorageMenuHandle.pageOfTitle(title)?.let { "page ${it.index}" } ?: "none"
			}
		}
	}

	@Test fun search() {
		Golden.check("storage-search") { key ->
			val (spec, query) = key.split("|", limit = 2)
			"${StorageOverlayScreen.matchesSearch(stack(spec), query)}"
		}
	}

	@Test fun virtualInventories() {
		Golden.check("storage-virtual-inventory") { key ->
			val inv = VirtualInventory(key.split(",").map { KeptStack.ofStack(stack(it)) })
			Apex.json.encodeToString(VirtualInventory.serializer(), inv)
		}
		Golden.check("storage-virtual-inventory-decode") { json ->
			val inv = Apex.json.decodeFromString(VirtualInventory.serializer(), json)
			"rows=${inv.rows};" + inv.stacks.joinToString(",") { lazy ->
				val s = lazy.current()
				if (s.isEmpty) "air" else "${BuiltInRegistries.ITEM.getKey(s.item).path}*${s.count}~${s.hoverName.string}"
			}
		}
	}

	@Test fun storageDataJson() {
		fun inv(specs: List<String>) = VirtualInventory(specs.map { KeptStack.ofStack(stack(it)) })
		val contents = inv(listOf("diamond_sword~Aspect of the End^Ability", "stone*64", "dirt*3") + List(6) { "air" })
		val icon = inv(listOf("chest~Ender Chest") + List(8) { "air" })
		val slot = ::StoragePageSlot
		val sample = StorageData(
			sortedMapOf(slot(0) to StorageData.RememberedPage(slot(0), null),
				slot(3) to StorageData.RememberedPage(slot(3), contents, icon),
				slot(12) to StorageData.RememberedPage(slot(12), null, icon)),
			sortedMapOf(slot(1) to "Pets", slot(12) to "Mining stuff"),
			slot(12),
		)
		Golden.check("storage-data-json") { key ->
			when (key) {
				"sample" -> Apex.json.encodeToString(StorageData.serializer(), sample)
				"empty" -> Apex.json.encodeToString(StorageData.serializer(), StorageData())
				else -> sample.labelFor(slot(key.removePrefix("name ").toInt()))
			}
		}

		val back = Apex.json.decodeFromString(StorageData.serializer(), Apex.json.encodeToString(StorageData.serializer(), sample))
		assertEquals(sample.knownPages.keys, back.knownPages.keys)
		assertEquals(sample.pageLabels, back.pageLabels)
	}

	@Test fun emptyMarkers() {
		val markers = StorageOverviewScreen.placeholderPanes.map { BuiltInRegistries.ITEM.getKey(it).toString() }
		assertEquals(Golden.read("storage-empty-markers").map { it.first }, markers)
	}

	@Test fun scrollSpeed() {
		try {
			Golden.check("storage-scroll-speed") { key ->
				val (amount, speed, inverse) = key.split(",")
				configure(mapOf("scroll-speed" to JsonPrimitive(speed.toInt()), "inverse-scroll" to JsonPrimitive(inverse.toBoolean())))
				"${StorageOverlay.adjustScrollSpeed(amount.toDouble())}"
			}
		} finally {
			configure()
		}
	}

	@Test fun overlayLayout() {
		val n = Golden.check("storage-overlay-layout") { key ->
			val (dims, pageSpec) = key.split("|", limit = 2)
			val (w, h, cols, heightSetting) = dims.split(",").take(4).map { it.toInt() }
			val requestedScroll = dims.split(",")[4].toFloat()
			overlayRow(StorageOverlayGeometry(w, h, cols, heightSetting), requestedScroll, pagesOf(pageSpec))
		}
		assertEquals(1350, n)
	}

	private fun overlayRow(g: StorageOverlayGeometry, requestedScroll: Float, pages: List<Int?>): String {
		val heights = pages.map { StorageOverlayGeometry.pageHeight(it, LINE_HEIGHT) }
		val before = g.layoutPages(heights, requestedScroll)
		val scroll = g.clampScroll(requestedScroll, before.contentHeight)
		val layout = g.layoutPages(heights, scroll)
		val content = layout.contentHeight
		val bar = g.scrollBar()
		val drags = listOf(bar.y - 5.0, bar.y.toDouble(), bar.y + 30.0, bar.y + g.panelHeight.toDouble())
			.map { g.scrollForKnobTop(it, content) }
		val firstPage = pages.firstOrNull()?.let { rows ->
			val box = layout.boxes.first()
			val P = StorageOverlayGeometry.Page
			(0 until rows * 9).filter { it % 4 == 0 }.joinToString(" ") { "${P.slotX(box.x, it)},${P.slotY(box.y, it, LINE_HEIGHT)}" } +
				";name=${P.titleX(box.x)},${P.titleY(box.y)};outline=${P.outline(box.x, box.y, rows, LINE_HEIGHT).csv()}" +
				";row=${P.grid(box.x, box.y, rows, LINE_HEIGHT).csv()}"
		}
		val m = listOf(g.frameX, g.frameY, g.frameWidth, g.frameHeight, g.panelWidth, g.panelHeight, g.playerX, g.playerY,
			g.controlsX, g.controlsY, g.frameWidth, g.totalHeight)
		return "pwc=${g.columns};m=${m.joinToString(",")};first=${before.boxes.size};scroll=$scroll" +
			";rects=${layout.boxes.joinToString(" ") { it.csv() }};inner=$content;sb=${bar.csv()};knob=${g.knobTop(scroll, content)}" +
			";drag=${drags.joinToString(",")};bounds=${g.bounds().joinToString(" ") { it.csv() }}" +
			";player=${(0 until 36).joinToString(" ") { g.playerSlot(it).csv() }};page0=$firstPage"
	}

	@Test fun overviewLayout() {
		val n = Golden.check("storage-overview-layout") { key ->
			val (dims, pageSpec) = key.split("|", limit = 2)
			val (w, h, cols, pad, margin, scalePct, requestedScroll) = dims.split(",").map { it.toInt() }
			val scale = scalePct / 100F
			val layout = StorageOverviewLayout((w / scale).toInt(), (h / scale).toInt(), cols, pad, margin, LINE_HEIGHT)
			val heights = pagesOf(pageSpec).map { layout.pageHeight(it) }
			val scroll = layout.clampScroll(requestedScroll, layout.contentHeight(heights))
			val placed = layout.place(heights, scroll)
			val content = layout.contentHeight(heights)
			val wheel = listOf(-1.0, 1.0, -3.0).map { layout.clampScroll((scroll + StorageOverlay.adjustScrollSpeed(it)).toInt(), content) }
			val probes = placed.flatMap { p ->
				listOf(p.x to p.y, p.x + 85 to p.y + p.height / 2, p.x + 171 to p.y + p.height, p.x + 172 to p.y, p.x - 1 to p.y)
			}
			val hits = probes.map { (x, y) -> StorageOverviewLayout.pageAt(placed, x.toDouble(), y.toDouble()) }
			"sw=${layout.scaledWidth};sh=${layout.scaledHeight};scroll=$scroll;pos=${placed.joinToString(" ") { "${it.x},${it.y},${it.height}" }}" +
				";height=$content;max=${layout.maxScroll(content)};wheel=${wheel.joinToString(",")};hits=${hits.joinToString(",")}"
		}
		assertEquals(1944, n)
	}

	private operator fun <T> List<T>.component6() = this[5]
	private operator fun <T> List<T>.component7() = this[6]
}
