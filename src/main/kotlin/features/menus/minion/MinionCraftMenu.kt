// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.minion

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.ContainerGate
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSession
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.support.MenuSlots
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.features.minions.MinionHelper
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.repoItemId

@Config
object MinionCraftMenuSettings : MenuSettings("minion-craft", panels = listOf("requirements", "inventory"))

object MinionCraftMenu : CustomMenu {
	override val id = "minion-craft"
	override val displayName = "Minion Crafting"
	override val title = Regex("^Craft Item$")
	override val settings get() = MinionCraftMenuSettings
	override val defaultSize = 330 to 250

	@Subscribe
	fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }

	val lastResult = WindowScoped<MinionCraftParser.CraftResult>()
	@Subscribe
	fun onScreenChange(event: dev.ashtonite365.apex.events.ScreenChangeEvent) {
		lastResult.onWindowChange((event.new as? AbstractContainerScreen<*>)?.menu)
	}
	@Subscribe
	fun onChat(event: dev.ashtonite365.apex.events.ProcessChatEvent) {
		if (!enabled) return
		val r = MinionCraftParser.craftResult(event.bareText) ?: return
		val menu = dev.ashtonite365.apex.util.Client.player?.containerMenu ?: return
		lastResult.set(menu, r)
	}

	override fun create(screen: AbstractContainerScreen<*>): MenuView = MinionCraftView(screen, tableOnly = true)
}

object MinionTierMemory {
	val maxByName = mutableMapOf<String, Int>()

	fun maxTier(base: String): Int? = maxByName[base] ?: runCatching {
		MinionHelper.baseIdForName("$base Minion")?.let { MinionHelper.minionTiers()[it] }
	}.getOrNull()
}

data class MinionCraftPage(
	val kind: MinionScreenKind,
	val slots: List<CraftSlot>,
	val craft: MinionCraftModel?,
	val requirements: List<MinionCraftParser.Requirement>,
	val tiers: List<Pair<Int, MinionRef>>,
	val allTiersSlot: Int?,
)

class MinionCraftView(screen: AbstractContainerScreen<*>, private val tableOnly: Boolean = false) : MinionViewBase<MinionCraftPage>(screen), MenuView {
	private val c get() = MinionCraftMenuSettings.customisation
	override fun palette() = c.palette

	override fun parse(title: String, slots: List<SlotText>): MinionCraftPage? {
		val cs = slots.map { s -> CraftSlot(s.slot, s.cleanName, stacks[s.slot]?.view()?.repoItemId?.skyblockId, stacks[s.slot]?.count ?: 1) }
		val kind = MinionCraftParser.classify(cs)
		if (tableOnly && !MinionCraftParser.isMinionCraftTable(cs)) { handBack(); return null }
		if (kind != MinionScreenKind.TIER_RECIPE && kind != MinionScreenKind.RECIPES_LIST) return null
		val tiers = MinionCraftParser.tiers(cs)
		if (kind == MinionScreenKind.RECIPES_LIST) tiers.firstOrNull()?.second?.base?.let { b -> MinionCraftParser.maxTierFromList(cs)?.let { MinionTierMemory.maxByName[b] = it } }
		return MinionCraftPage(kind, cs, MinionCraftParser.parse(cs), MinionCraftParser.requirements(cs, slots.associate { it.slot to it.cleanLore }),
			tiers, MinionCraftParser.allTiersSlot(cs))
	}

	private fun handBack() { showVanilla() }

	override fun draw(ctx: GuiGraphicsExtractor, m: MinionCraftPage, mx: Int, my: Int) {
		val pad = 6
		val vw = font.width("Vanilla") + 10

		val by = m.slots.associateBy { it.index }
		listOf(MinionCraftSlots.GO_BACK to "Back", MinionCraftSlots.CLOSE to "Close").forEachIndexed { i, (s, l) ->
			if (by[s] != null) { val bw = font.width(l) + 10; button(ctx, mx, my, x + w - vw - 8 - (i + 1) * (bw + 4), y + 3, bw, 14, l) { b -> forward(s, b) } }
		}
		when (m.kind) {
			MinionScreenKind.RECIPES_LIST -> drawList(ctx, m, mx, my)
			else -> drawRecipe(ctx, m, mx, my)
		}
		if (c.shows("inventory")) inventory(ctx, mx, my, x + (w - 162) / 2, y + h - 80, MinionCraftSlots.CHEST_SLOTS)
		finish(ctx, mx, my)
	}

	private fun drawList(ctx: GuiGraphicsExtractor, m: MinionCraftPage, mx: Int, my: Int) {
		val base = m.tiers.firstOrNull()?.second?.base ?: "?"
		val max = MinionCraftParser.maxTierFromList(m.slots)
		ctx.text(font, "§6§l$base Minion §7· recipes", x + 6, y + 7, MenuTheme.WHITE)
		ctx.text(font, "§7Max tier §f${max?.let(::roman) ?: "?"} §8· click a tier to see its recipe", x + 6, y + 19, MenuTheme.GREY)
		val cols = 6; val tw = (w - 12) / cols
		m.tiers.forEachIndexed { i, (slot, ref) ->
			val tx = x + 6 + (i % cols) * tw; val ty = y + 34 + (i / cols) * 30
			tile(ctx, mx, my, tx, ty, slot, if (ref.tier == max) MenuTheme.GOLD else null, realItem = MinionClicks.tileIsRealItem(m.kind))
			ctx.text(font, "§f${roman(ref.tier)}" + if (ref.tier == max) " §6max" else "", tx + 21, ty + 5, MenuTheme.WHITE)
		}
	}

	private fun drawRecipe(ctx: GuiGraphicsExtractor, m: MinionCraftPage, mx: Int, my: Int) {
		val cm = m.craft ?: return
		val target = cm.result ?: cm.centre?.let { MinionRef(it.base, it.tier + 1) }
		val base = target?.base ?: cm.centre?.base ?: "?"
		val max = MinionTierMemory.maxTier(base)
		ctx.text(font, "§6§l$base Minion " + (target?.let { roman(it.tier) } ?: ""), x + 6, y + 7, MenuTheme.WHITE)
		val sub = when {
			cm.centre != null && max != null && MinionCraftParser.isMaxTier(cm.centre, max.let { mapOf(base to it) }) -> "§6${roman(cm.centre.tier)} is the max tier: no further upgrade"
			cm.centre != null && target != null -> "§7Upgrade §f${roman(cm.centre.tier)} §7→ §a${roman(target.tier)}"
			target != null -> "§7Craft tier §a${roman(target.tier)}"
			else -> "§7Place a minion recipe in the grid"
		} + (if (target != null && max != null && target.tier >= max) " §6(max tier)" else "")
		ctx.text(font, MenuTheme.fit(sub, w - 12, font::width), x + 6, y + 19, MenuTheme.GREY)

		val gx = x + 10; val gy = y + 34

		cm.gridSlots.forEachIndexed { i, s -> realTile(ctx, mx, my, gx + (i % 3) * 19, gy + (i / 3) * 19, s, if (s == MinionCraftSlots.CENTRE && cm.centre != null) MenuTheme.GOLD else null) }
		ctx.text(font, "§7➜", gx + 62, gy + 24, MenuTheme.GREY)
		tile(ctx, mx, my, gx + 76, gy + 19, cm.resultSlot, if (cm.result != null) 0xFF55FF55.toInt() else null)
		m.allTiersSlot?.let { s -> button(ctx, mx, my, gx, gy + 62, 95, 14, "All tiers") { b -> forward(s, b) } }
		MinionCraftMenu.lastResult.get(screen.menu)?.let { r ->
			ctx.text(font, MenuTheme.fit("§cNot enough items: §f${r.have}/${r.need} §7(need §c${r.missing}§7 more)", x + w - 6 - gx, font::width), gx, gy + 80, MenuTheme.WHITE)
		}

		if (c.shows("requirements") && m.requirements.isNotEmpty()) {
			val rx = gx + 104; var ry = gy
			MenuTheme.panel(ctx, rx, ry - 2, x + w - 6 - rx, 14 + 11 * m.requirements.size, palette())
			ctx.text(font, "§7Ingredients §8(have/need)", rx + 4, ry + 1, MenuTheme.GREY); ry += 12
			for (r in m.requirements) {
				ctx.text(font, MenuTheme.fit((if (r.ok) "§a✔ " else "§c✖ ") + "§f${r.have}/${r.need} §7${r.name}", x + w - 14 - rx, font::width), rx + 4, ry, MenuTheme.WHITE); ry += 11
			}
		}
	}

	private fun roman(n: Int): String {
		val v = listOf(10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I"); var x = n; val sb = StringBuilder()
		for ((k, s) in v) while (x >= k) { sb.append(s); x -= k }
		return sb.toString()
	}
}

class MinionRouterView(private val screen: AbstractContainerScreen<*>) : MenuView {
	private val minion = MinionView(screen)
	private val craft = MinionCraftView(screen)
	private var session: MenuSession? = null
	private val opened = System.currentTimeMillis()
	private var pick: MenuView = minion
	override val x get() = pick.x; override val y get() = pick.y; override val w get() = pick.w; override val h get() = pick.h

	override fun layout(x: Int, y: Int, w: Int, h: Int) { minion.layout(x, y, w, h); craft.layout(x, y, w, h) }
	override fun attached(session: MenuSession) { this.session = session; minion.attached(session); craft.attached(session) }

	private fun choose() {
		val (texts, st) = MenuSlots.snapshot(screen)
		val cs = texts.map { CraftSlot(it.slot, it.cleanName, st[it.slot]?.view()?.repoItemId?.skyblockId) }
		pick = when (MinionCraftParser.classify(cs)) {
			MinionScreenKind.TIER_RECIPE, MinionScreenKind.RECIPES_LIST -> if (MinionCraftMenu.enabled) craft else { session?.setHidden(true); craft }
			MinionScreenKind.MINION_WINDOW -> minion
			MinionScreenKind.OTHER -> { if (texts.isNotEmpty() && System.currentTimeMillis() - opened > ContainerGate.GRACE_MS) session?.fallback("not a minion page"); minion }
		}
	}

	override fun render(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) { choose(); pick.render(ctx, mouseX, mouseY, delta) }
	override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean) = pick.mouseClicked(click, doubled)
	override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double) = pick.mouseScrolled(mouseX, mouseY, amount)
	override fun keyPressed(input: KeyEvent) = pick.keyPressed(input)
	override fun charTyped(input: CharacterEvent) = pick.charTyped(input)
	override val textFocused get() = pick.textFocused
	override fun interceptClose() = pick.interceptClose()
}
