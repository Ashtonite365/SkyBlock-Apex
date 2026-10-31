// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.craft

import io.github.moulberry.repo.data.NEUCraftingRecipe
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.util.ApexClickDelay
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.minion.MinionCraftMenuSettings
import dev.ashtonite365.apex.features.menus.minion.MinionCraftView
import dev.ashtonite365.apex.features.menus.minion.MinionViewBase
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.repo.RepoManager
import dev.ashtonite365.apex.repo.SBItemStack
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.repoItemId

@Config
object CraftMenuSettings : MenuSettings("craft") {

}

object CraftMenu : CustomMenu {
	override val id = "craft"
	override val displayName = "Craft Item"
	override val title = CraftParser.TITLE
	override val settings get() = CraftMenuSettings
	override val defaultSize = 470 to 280

	override val enabled: Boolean get() = enabledFor(settings.apex, CraftSlotMap.VERIFIED) || MinionCraftMenuSettings.apex
	fun enabledFor(apex: Boolean, verified: Boolean) = apex && verified

	@Subscribe
	fun onInit(event: ClientInitEvent) { MenuRegistry.registerFirst(this) }

	override fun create(screen: AbstractContainerScreen<*>): MenuView? =
		if (screen.menu.slots.size == CraftSlotMap.CHEST_SLOTS + 36) CraftRouterView(screen) else null
}

class CraftRouterView(private val screen: AbstractContainerScreen<*>) : MenuView {
	private val general = CraftView(screen)
	private val minion = MinionCraftView(screen, tableOnly = true)
	private var session: dev.ashtonite365.apex.features.menus.MenuSession? = null
	private var kind: Boolean? = null
	private val cur: MenuView get() = if (kind == true) minion else general
	override val x get() = cur.x; override val y get() = cur.y; override val w get() = cur.w; override val h get() = cur.h
	override fun layout(x: Int, y: Int, w: Int, h: Int) { general.layout(x, y, w, h); minion.layout(x, y, w, h) }
	override fun attached(session: dev.ashtonite365.apex.features.menus.MenuSession) { this.session = session; general.attached(session); minion.attached(session) }

	private fun slots() = screen.menu.slots.take(CraftSlotMap.CHEST_SLOTS).mapIndexedNotNull { i, s ->
		s.item.takeIf { !it.isEmpty }?.let { dev.ashtonite365.apex.features.menus.minion.CraftSlot(i, it.hoverName.string, it.view().repoItemId?.skyblockId, it.count) }
	}

	private fun decide() {
		val k = CraftSplit.isMinionTable(slots())
		if (k == kind) return
		kind = k
		session?.setHidden(!(if (k) MinionCraftMenuSettings.apex else CraftMenuSettings.apex && CraftSlotMap.VERIFIED))
	}

	override fun render(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
		decide()

		if (session?.isHidden?.invoke() == true) return
		CraftHandoff.craftMenuId = screen.menu.containerId

		CraftHandoff.take()?.let { general.load(it); if (kind == true) kind = null }
		cur.render(ctx, mouseX, mouseY, delta)
	}
	override fun mouseClicked(click: net.minecraft.client.input.MouseButtonEvent, doubled: Boolean) = cur.mouseClicked(click, doubled)
	override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double) = cur.mouseScrolled(mouseX, mouseY, amount)
	override fun keyPressed(input: net.minecraft.client.input.KeyEvent) = cur.keyPressed(input)
	override fun charTyped(input: net.minecraft.client.input.CharacterEvent) = cur.charTyped(input)
	override val textFocused get() = cur.textFocused
	override fun interceptClose() = cur.interceptClose()
}

object CraftLayout {
	const val LIST_W = 8 * 18 + 8

	const val OUTPUT_W = 120
	const val CONTENT_W = 14 + (3 * 18 + 70) + 26 + OUTPUT_W
	fun showList(jeiLoaded: Boolean) = !jeiLoaded
	fun listWidth(showList: Boolean) = if (showList) LIST_W else 0
	fun gridX(x: Int, w: Int, pad: Int, showList: Boolean) =
		if (showList) x + pad + LIST_W + 14 else x + maxOf(pad + 5, (w - CONTENT_W) / 2 + 14)
}

object CraftRecipes {
	private val requests = java.util.IdentityHashMap<NEUCraftingRecipe, CraftRequest>()
	fun request(r: NEUCraftingRecipe): CraftRequest = requests.getOrPut(r) { CraftRequest.of(r) }
	val icons = HashMap<String, ItemStack>()
	val all: List<NEUCraftingRecipe> by lazy {
		RepoManager.getAllRecipes().filterIsInstance<NEUCraftingRecipe>().distinctBy { it.output.itemId }.sortedBy { it.output.itemId }.toList()
	}
}

class CraftView(screen: AbstractContainerScreen<*>) : MinionViewBase<CraftModel>(screen), MenuView {
	private val c get() = CraftMenuSettings.customisation
	override fun palette() = c.palette
	override fun parse(title: String, slots: List<SlotText>) = CraftParser.parse(title, slots, screen.menu.slots.size - 36)

	private val listScroll = dev.ashtonite365.apex.features.menus.support.ScrollView { scrollKey("list") }

	private var selected: CraftRequest? = null
	private var status: String? = null

	@OptIn(dev.ashtonite365.apex.repo.ExpensiveItemCacheApi::class, dev.ashtonite365.apex.util.mc.NeedsComponents::class)
	private fun stackOf(id: String, amt: Int = 1): ItemStack = CraftRecipes.icons.getOrPut("$id:$amt") {
		runCatching { SBItemStack(io.github.moulberry.repo.data.NEUIngredient.fromString("$id:$amt"))?.asImmutableItemStack()?.current() }.getOrNull() ?: ItemStack.EMPTY
	}

	override fun draw(ctx: GuiGraphicsExtractor, m: CraftModel, mx: Int, my: Int) {
		val p = palette(); val pad = 6
		ctx.text(font, "§6§lCraft Item", x + pad, y + 7, MenuTheme.WHITE)
		val vw = font.width("Vanilla") + 10

		val showList = CraftLayout.showList(CraftHandoff.jeiLoaded)
		val lx = x + pad; val ly = y + 22; val cols = 8; val lh = h - 22 - pad
		val lw = CraftLayout.listWidth(showList)
		if (showList) MenuTheme.panel(ctx, lx, ly, lw, lh, p)
		val recipes = if (showList) CraftRecipes.all else emptyList()

		val view = dev.ashtonite365.apex.features.menus.MenuBounds(lx + 1, ly + 1, lw - 2, maxOf(0, lh - 2))
		val totalRows = (recipes.size + cols - 1) / cols
		listScroll.layout(view, if (totalRows == 0) 0 else 3 + totalRows * 18 + 3)
		val first = maxOf(0, (listScroll.offset - 3) / 18)
		if (showList) beginClip(ctx, view)
		recipes.drop(first * cols).take((view.h / 18 + 2) * cols).forEachIndexed { j, r ->
			val i = first * cols + j
			val tx = lx + 4 + (i % cols) * 18; val ty = ly + 4 + (i / cols) * 18 - listScroll.offset
			if (ty >= view.y + view.h) return@forEachIndexed
			val hover = hovered(mx, my, tx, ty, 18, 18)
			val req = CraftRecipes.request(r)
			MenuTheme.frame(ctx, tx, ty, tx + 18, ty + 18, if (hover) MenuTheme.FILL_HOVER else MenuTheme.FILL, if (req == selected) MenuTheme.GOLD else MenuTheme.BORDER)
			val st = stackOf(r.output.itemId, r.outputCount.coerceAtLeast(1))
			if (!st.isEmpty) { ctx.item(st, tx + 1, ty + 1); if (hover) listTip = st }
			area(tx, ty, 18, 18) { _ -> selected = req; status = null }
		}
		if (showList) endClip(ctx)

		val gx = CraftLayout.gridX(x, w, pad, showList); val gy = y + 30
		MenuTheme.panel(ctx, gx - 5, gy - 5, 3 * 18 + 10 + 60, 3 * 18 + 10, p)
		val sel = selected
		val cells = sel?.cells
		CraftSlotMap.GRID.forEachIndexed { i, s ->
			val tx = gx + (i % 3) * 18; val ty = gy + (i / 3) * 18
			if (CraftSlotMap.isRealGridCell(s)) realTile(ctx, mx, my, tx, ty, s) else tile(ctx, mx, my, tx, ty, s)
			if (live(s).isEmpty) cells?.getOrNull(i)?.let { (id, n) ->
				val ghost = stackOf(id, n)
				if (!ghost.isEmpty) { ctx.item(ghost, tx + 1, ty + 1); ctx.itemDecorations(font, ghost, tx + 1, ty + 1); ctx.fill(tx + 1, ty + 1, tx + 17, ty + 17, 0x90202028.toInt()) }
			}
		}
		ctx.text(font, "§7→", gx + 3 * 18 + 8, gy + 23, MenuTheme.GREY)
		tile(ctx, mx, my, gx + 3 * 18 + 22, gy + 18, CraftSlotMap.OUTPUT, MenuTheme.GOLD)

		val sy = gy + 3 * 18 + 10
		val superSlot = Supercraft.slot(m)
		button(ctx, mx, my, gx - 5, sy, 3 * 18 + 10, 16, if (superSlot != null) "Supercraft" else "§8Supercraft", enabled = superSlot != null) { _ ->
			Supercraft.press(m) { clickSlot(it, 0) }
		}
		val hint = status ?: if (superSlot == null) "§8No Supercraft on Hypixel's page" else if (sel == null) { if (CraftHandoff.jeiLoaded) "§8Press + on a recipe in the item list" else "§8Pick a recipe on the left" } else "§8Place the shown items in the grid"
		ctx.text(font, MenuTheme.fit(hint, 3 * 18 + 70, font::width), gx - 5, sy + 20, MenuTheme.GREY)

		var qy = gy
		val qx = gx + 3 * 18 + 70
		ctx.text(font, "§7Quick craft", qx, gy - 10, MenuTheme.GREY)
		for (q in CraftSlotMap.QUICK_CRAFT) { tile(ctx, mx, my, qx, qy, q); qy += 20 }
		var ox = gx - 5
		val oy = sy + 32
		for (o in m.others) { tile(ctx, mx, my, ox, oy, o.slot); ox += 20 }

		val rx = qx + 26; val rw = if (showList) x + w - pad - rx else CraftLayout.OUTPUT_W
		MenuTheme.panel(ctx, rx, y + 22, rw, 3 * 18 + 40, p)
		if (sel != null) {
			val out = stackOf(sel.outputId, sel.outputCount)
			if (!out.isEmpty) { ctx.item(out, rx + 6, y + 28); ctx.itemDecorations(font, out, rx + 6, y + 28) }
			ctx.text(font, MenuTheme.fit("§f" + (out.hoverName.string.ifEmpty { sel.outputId }), rw - 30, font::width), rx + 26, y + 32, MenuTheme.WHITE)
			val totals = cells.orEmpty().filterNotNull().groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
			totals.entries.take(5).forEachIndexed { i, (id, n) ->
				ctx.text(font, MenuTheme.fit("§7${n}x §f${stackOf(id).hoverName.string.ifEmpty { id }}", rw - 10, font::width), rx + 6, y + 50 + i * 10, MenuTheme.GREY)
			}
		} else ctx.text(font, "§8No recipe selected", rx + 6, y + 32, MenuTheme.DIM)

		inventory(ctx, mx, my, gx - 5, y + h - 80, CraftSlotMap.CHEST_SLOTS)
		finish(ctx, mx, my)
		listTip?.takeIf { screen.menu.carried.isEmpty }?.let { ctx.setTooltipForNextFrame(font, it, mx, my) }
		listTip = null
	}

	private var listTip: ItemStack? = null

	fun load(r: CraftRequest) {
		selected = r; status = "§7Loaded from the item list"
	}

	override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean { listScroll.scroll(amount); return true }
}
