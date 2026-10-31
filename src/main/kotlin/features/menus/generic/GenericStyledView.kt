// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.generic

import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.style.MenuStyle
import dev.ashtonite365.apex.features.menus.style.StyledChestLayout
import dev.ashtonite365.apex.features.menus.support.ApexViewBase
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.util.bareText
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen

class GenericStyledView(screen: AbstractContainerScreen<*>) : ApexViewBase<GenericStyledView.Page>(screen) {
	data class Page(val title: String, val rows: Int)

	val rows get() = GenericWrapper.rowsOf(screen.menu.slots.size)

	var withInventory = StyledChestLayout.usesInventory(screen.title.string) && !bazaarNow()
		private set

	private var bazaarChecks = 0

	private fun bazaarNow(): Boolean = runCatching {
		val n = maxOf(0, screen.menu.slots.size - 36)
		dev.ashtonite365.apex.features.apexbzmenu.BzPages.isBazaarChest(screen.title.string,
			(0 until n).mapNotNull { i -> screen.menu.slots[i].item.takeIf { !it.isEmpty }?.let { dev.ashtonite365.apex.features.menus.support.SlotText(i, it.hoverName.string, emptyList()) } })
	}.getOrDefault(false)
	private var layout = StyledChestLayout.build(screen.width, screen.height, rows, withInventory)

	override fun layout(x: Int, y: Int, w: Int, h: Int) {
		layout = StyledChestLayout.build(screen.width.takeIf { it > 0 } ?: (2 * x + w), screen.height.takeIf { it > 0 } ?: (2 * y + h), rows, withInventory)
		super.layout(layout.frame.x, layout.frame.y, layout.frame.w, layout.frame.h)
	}

	override fun parse(title: String, slots: List<SlotText>) = Page(title, rows)

	private var planKey: IntArray? = null
	private var planMemo: Map<Int, GenericWrapper.Plan> = emptyMap()
	private var fillerMemo: List<Boolean> = emptyList()
	override fun palette() = MenuStyle.palette.theme()

	override fun draw(ctx: GuiGraphicsExtractor, m: Page, mx: Int, my: Int) =
		dev.ashtonite365.apex.features.debug.FrameProfiler.time(dev.ashtonite365.apex.features.debug.FrameProfiler.Phase.GENERIC_WRAPPER) { drawImpl(ctx, m, mx, my) }

	private fun drawImpl(ctx: GuiGraphicsExtractor, m: Page, mx: Int, my: Int) {
		val p = palette()
		val sp = MenuStyle.palette
		ctx.text(font, screen.title.bareText.let { if (font.width(it) > w - 16) font.plainSubstrByWidth(it, w - 20) + "…" else it },
			x + 8, y + 6, sp.text, false)
		val s = session

		val sky = sp.style == dev.ashtonite365.apex.features.menus.style.Style.SKYGLASS
		val hideEmpty = GenericWrapper.hideEmptyCells(sky, screen.title.bareText)
		val stacks = (0 until rows * 9).map { screen.menu.slots.getOrNull(it)?.item }

		val key = GenericWrapper.planKey(sky, stacks)
		if (!key.contentEquals(planKey)) {
			planKey = key
			planMemo = if (sky) GenericWrapper.planPanes(stacks.mapIndexedNotNull { i, st ->
				if (st == null || st.isEmpty) null else GenericWrapper.PaneIn(i,
					net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.item).path.endsWith("glass_pane"),
					st.hoverName.string, loreOf(st))
			}) else emptyMap()
			fillerMemo = stacks.map { st -> st != null && !st.isEmpty && GenericWrapper.isFiller(
				net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.item).path, st.hoverName.string) }
		}
		val plan = planMemo
		val free = (0 until rows * 9).filter { i -> stacks[i].let { it == null || it.isEmpty } || plan[i] == GenericWrapper.Plan.Empty }.toSet()
		var tip: List<net.minecraft.network.chat.Component>? = null
		for (i in 0 until rows * 9) {
			val c = layout.cellScreen(i)
			val st = stacks[i]
			when (val pl = plan[i]) {
				is GenericWrapper.Plan.Text -> {
					val span = GenericWrapper.span(i, free, layout.pitch, c.w)
					textCell(ctx, pl.lines, c.x + 1, c.y + 1, span - 2, c.h - 2, sp.text)
					area(c.x, c.y, c.w, c.h) { b -> clickSlot(i, b) }
					if (MenuTheme.inside(mx, my, c.x, c.y, span, c.h)) tip = st?.let { net.minecraft.client.gui.screens.Screen.getTooltipFromItem(dev.ashtonite365.apex.util.Client.game, it) }
					continue
				}
				GenericWrapper.Plan.Hidden -> {
					area(c.x, c.y, c.w, c.h) { b -> clickSlot(i, b) }
					continue
				}
				else -> {}
			}

			if (fillerMemo.getOrElse(i) { false }) continue
			val empty = st == null || st.isEmpty
			if (empty && hideEmpty) { realSlotCell(i, c.x, c.y); continue }
			val hov = MenuTheme.inside(mx, my, c.x, c.y, c.w, c.h)
			MenuTheme.frame(ctx, c.x, c.y, c.x + c.w, c.y + c.h, if (hov) p.fillHover else p.fill, if (hov) p.borderHover else p.border)
			if (realSlotCell(i, c.x, c.y) && s != null && layout.scale != 1f) {
				s.realCells[i] = GenericWrapper.itemPos(c.x, c.y, layout.cellScale)
				s.cellScales[i] = layout.cellScale
			}
		}

		if (withInventory && bazaarChecks++ < 40 && bazaarNow()) { withInventory = false; layout(x, y, w, h); return }
		if (withInventory) layout.inventory?.let { (ix, iy) -> inventoryPanel(ctx, mx, my, ix, iy) }
		if (screen.menu.carried.isEmpty) tip?.takeIf { it.isNotEmpty() }?.let { ctx.setComponentTooltipForNextFrame(font, it, mx, my) }
	}

	private fun loreOf(st: net.minecraft.world.item.ItemStack): List<String> =
		st.get(net.minecraft.core.component.DataComponents.LORE)?.lines()?.map { it.string } ?: emptyList()

	private fun textCell(ctx: GuiGraphicsExtractor, lines: List<String>, tx: Int, ty: Int, tw: Int, th: Int, colour: Int) {
		val k = 0.5f
		val maxW = (tw / k).toInt()
		val wrapped = lines.flatMap { l -> GenericWrapper.wrap(l, maxW) { font.width(it) } }
		val fit = (th / (9 * k)).toInt().coerceAtLeast(1)
		val shown = if (wrapped.size > fit) wrapped.take(fit - 1) + "…" else wrapped
		ctx.pose().pushMatrix(); ctx.pose().translate(tx.toFloat(), ty.toFloat()); ctx.pose().scale(k, k)
		shown.forEachIndexed { n, l -> ctx.text(font, l, 0, n * 9, colour, false) }
		ctx.pose().popMatrix()
	}
}

object GenericWrapper {

	fun rowsOf(totalSlots: Int) = ((totalSlots - 36) / 9).coerceIn(1, 6)

	fun itemPos(fx: Int, fy: Int, scale: Float) = fx + Math.round(scale) to fy + Math.round(scale)

	fun eligible(isChest: Boolean, inSkyBlock: Boolean, enabled: Boolean, denied: Boolean, vanillaOwned: Boolean, claimed: Boolean) =
		isChest && inSkyBlock && enabled && !denied && !vanillaOwned && !claimed

	val INVENTORY_LIKE = Regex("(?i)^(?:large )?chest$|^barrel$|shulker box|ender chest|backpack|^storage|wardrobe|personal vault|minion chest|" +
		"^trades$|sell item|salvage|reforge item|anvil|enchant item|craft item")
	fun inventoryLike(title: String) = INVENTORY_LIKE.containsMatchIn(title.replace(CODES, "").trim())

	private val CODES = dev.ashtonite365.apex.util.SharedRegex.SECTION

	fun planKey(sky: Boolean, stacks: List<net.minecraft.world.item.ItemStack?>): IntArray {
		val out = IntArray(1 + stacks.size * 2)
		out[0] = if (sky) 1 else 0
		stacks.forEachIndexed { i, st -> out[1 + 2 * i] = System.identityHashCode(st); out[2 + 2 * i] = st?.count ?: 0 }
		return out
	}

	fun hideEmptyCells(skyglass: Boolean, title: String) = skyglass && !inventoryLike(title)

	data class PaneIn(val slot: Int, val isPane: Boolean, val name: String, val lore: List<String>)
	sealed interface Plan {
		data class Text(val lines: List<String>) : Plan

		data object Hidden : Plan

		data object Empty : Plan
	}
	private fun bare(s: String) = s.replace(CODES, "").trim()

	fun planPanes(items: List<PaneIn>): Map<Int, Plan> {
		val seen = HashSet<String>()
		val out = LinkedHashMap<Int, Plan>()
		for (it in items.sortedBy { it.slot }) {
			if (!it.isPane) continue
			val lines = ArrayList<String>(); val lineSeen = HashSet<String>()
			for (l in listOf(it.name) + it.lore) { val b = bare(l); if (b.isNotEmpty() && lineSeen.add(b)) lines += l }
			if (lines.isEmpty()) { out[it.slot] = Plan.Empty; continue }
			out[it.slot] = if (seen.add(lines.joinToString("\n") { l -> bare(l) })) Plan.Text(lines) else Plan.Hidden
		}
		return out
	}

	fun span(slot: Int, free: Set<Int>, pitch: Int, cellW: Int): Int {
		var n = 0
		while (n < 3 && (slot + n + 1) % 9 != 0 && (slot + n + 1) in free) n++
		return cellW + n * pitch
	}

	fun wrap(text: String, maxW: Int, width: (String) -> Int): List<String> {
		val out = ArrayList<String>(); var cur = ""
		for (w in text.split(' ')) {
			val t = if (cur.isEmpty()) w else "$cur $w"
			if (width(t) <= maxW || cur.isEmpty()) cur = t else { out += cur; cur = w }
		}
		if (cur.isNotEmpty()) out += cur
		return out
	}

	fun isFiller(itemPath: String, name: String) = itemPath.endsWith("stained_glass_pane") && name.replace(CODES, "").isBlank()

	fun featureOwned(title: String, foreignGuiInstalled: Boolean) = foreignGuiInstalled ||
		dev.ashtonite365.apex.features.inventory.storageoverlay.StorageMenuHandle.isOverviewTitle(title) ||
		dev.ashtonite365.apex.features.inventory.storageoverlay.StorageMenuHandle.pageOfTitle(title) != null

	fun lateAttach(alreadyAttached: Boolean, eligibleNow: Boolean) = !alreadyAttached && eligibleNow
}
