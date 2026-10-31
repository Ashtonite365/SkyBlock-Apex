// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.recipebook

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.MenuTitles
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.collections.LoreParse
import dev.ashtonite365.apex.features.menus.collections.LoreProgress
import dev.ashtonite365.apex.features.menus.collections.NavButton
import dev.ashtonite365.apex.features.menus.collections.TileMenuView
import dev.ashtonite365.apex.features.menus.collections.TileSpec
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.util.data.Config

data class RecipeTile(val slot: Int, val name: String, val unlocked: Boolean, val progress: LoreProgress?, val reason: String? = null)

sealed interface RecipePage {
	val title: String; val header: LoreProgress?; val nav: List<NavButton>
	data class Main(override val title: String, override val header: LoreProgress?, val categories: List<RecipeTile>, override val nav: List<NavButton>) : RecipePage
	data class Category(override val title: String, val category: String, override val header: LoreProgress?, val recipes: List<RecipeTile>,
	                    override val nav: List<NavButton>, val page: Pair<Int, Int>?) : RecipePage {
		val subCategories get() = recipes.filter { it.progress != null }
		val unlockedCount get() = recipes.count { it.unlocked && it.progress == null }
		val lockedCount get() = recipes.count { !it.unlocked }
	}
}

object RecipeBookParser {
	val MAIN = Regex("^Recipe Book$", RegexOption.IGNORE_CASE)

	val CATEGORY = MenuTitles.anyOf(
		Regex("^(?:\\(\\d+/\\d+\\)\\s*)?(?!.*\\bMinion\\b).+\\sRecipes(?:\\s*\\(\\d+/\\d+\\))?$", RegexOption.IGNORE_CASE),
		MenuTitles.cutTail("(?!.*\\bMinion\\b).+", "Recipes"),
	)
	val ANY = MenuTitles.anyOf(MAIN, CATEGORY)

	private val LOCKED = listOf("locked", "not unlocked", "haven't unlocked", "unlock this recipe", "requires")

	fun unlocked(s: SlotText): Boolean {
		if (s.cleanName == "???" || s.cleanName.isEmpty()) return false
		val low = s.cleanLore.joinToString("\n").lowercase()
		if ("click to view recipe" in low || "click to view recipes" in low) return true
		return LOCKED.none { k -> Regex("(?<!un)\\b" + Regex.escape(k)).containsMatchIn(low) }
	}

	fun parse(title: String, slots: List<SlotText>, chestSize: Int = 54): RecipePage? {
		val t = MenuText.clean(title)
		val chest = slots.filter { it.slot < chestSize && it.cleanName.isNotEmpty() }
		if (chest.isEmpty()) return null
		val nav = chest.filter { LoreParse.isNav(it.name) }.map { NavButton(it.slot, it.cleanName, it.cleanLore) }
		val body = chest.filter { s -> nav.none { it.slot == s.slot } }
		if (MAIN.matches(t)) {
			val head = body.firstOrNull { it.cleanName.equals("Recipe Book", true) }
			val cats = body.filter { it !== head }.mapNotNull { s ->
				val p = LoreParse.progress(s.lore).firstOrNull { it.label.contains("Recipes", true) }

				p?.let { RecipeTile(s.slot, s.cleanName, true, it) }
			}
			if (cats.isEmpty()) return null
			val extra = body.filter { s -> s !== head && cats.none { it.slot == s.slot } }.map { NavButton(it.slot, it.cleanName, it.cleanLore) }
			return RecipePage.Main(t, head?.let { h -> LoreParse.progress(h.lore).firstOrNull() }, cats, nav + extra)
		}
		if (!CATEGORY.containsMatchIn(t)) return null
		val bare = t.replace(Regex("^\\(\\d+/\\d+\\)\\s*|\\s*\\(\\d+/\\d+\\)$"), "")
		val head = body.firstOrNull { it.cleanName.equals(bare, true) && LoreParse.progress(it.lore).isNotEmpty() }
		val recipes = body.filter { it !== head }.map { s ->
			val prog = LoreParse.progress(s.lore).firstOrNull { it.label.contains("Recipes", true) || it.label.contains("Unlocked", true) }
			val ok = prog != null || unlocked(s)
			RecipeTile(s.slot, s.cleanName, ok, prog, if (ok) null else s.cleanLore.firstOrNull { it.isNotBlank() })
		}
		return RecipePage.Category(t, bare.replace(Regex("\\s*Recipes$", RegexOption.IGNORE_CASE), ""),
			head?.let { LoreParse.progress(it.lore).firstOrNull() }, recipes, nav, LoreParse.pageOf(t))
	}
}

@Config
object RecipeBookMenuSettings : MenuSettings("recipe-book") {

	val showLocked by toggle("show-locked") { true }
	val reset by button("reset") { resetToDefaults() }
}

object RecipeBookMenu : CustomMenu {
	override val id = "recipe-book"
	override val displayName = "Recipe Book"
	override val title = RecipeBookParser.ANY
	override val settings get() = RecipeBookMenuSettings
	override val defaultSize = 470 to 280
	@Subscribe
	fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }
	override fun create(screen: AbstractContainerScreen<*>): MenuView = RecipeBookView(screen)
}

class RecipeBookView(screen: AbstractContainerScreen<*>) : TileMenuView<RecipePage>(screen) {
	override fun palette() = RecipeBookMenuSettings.customisation.palette
	override val tileW get() = 104

	override val tileH get() = 28

	override val widenTiles = true

	private fun categoryTile(c: RecipeTile): TileSpec {
		val text = c.progress?.let { p -> listOfNotNull(p.pct?.let(::fmtPct), p.current?.let { "${fmtNum(it)}/${p.max?.let(::fmtNum) ?: "?"}" }).joinToString(" · ") }

		val f = c.progress?.fraction
		return if (f != null) TileSpec(c.slot, c.name.removeSuffix(" Recipes"), null, f, MenuTheme.GREY, barText = text)
		else TileSpec(c.slot, c.name.removeSuffix(" Recipes"), text, null, if (c.progress?.done == true) MenuTheme.GOLD else MenuTheme.GREY)
	}
	override fun parse(title: String, slots: List<SlotText>) = RecipeBookParser.parse(title, slots, screen.menu.slots.size - 36)

	override fun draw(ctx: GuiGraphicsExtractor, m: RecipePage, mx: Int, my: Int) {
		val bottom = y + h - 24
		when (m) {
			is RecipePage.Main -> {
				val top = header(ctx, mx, my, "Recipe Book", m.header)
				groups(ctx, mx, my, top, bottom, listOf("Categories" to m.categories.map(::categoryTile)))
			}
			is RecipePage.Category -> {
				val top = header(ctx, mx, my, m.category + " Recipes" + (m.page?.let { " §8(${it.first}/${it.second})" } ?: ""), m.header,
					if (m.subCategories.isNotEmpty() && m.unlockedCount + m.lockedCount == 0) null
					else "§a${m.unlockedCount} unlocked §8· §c${m.lockedCount} locked §7on this page")
				val sub = m.subCategories.map { c ->
					categoryTile(c)
				}
				val g = (listOf("Categories" to sub) + listOf(
					"Unlocked" to m.recipes.filter { it.unlocked && it.progress == null },
					"Locked" to if (RecipeBookMenuSettings.showLocked) m.recipes.filter { !it.unlocked } else emptyList())
					.map { (l, rs) -> l to rs.map { r -> TileSpec(r.slot, r.name, if (r.unlocked) "§aview recipe" else "§c" + (r.reason ?: "locked"), null, MenuTheme.GREY) } })
					.filter { it.second.isNotEmpty() }
				groups(ctx, mx, my, top, bottom, g)
			}
		}
		navRow(ctx, mx, my, m.nav.map { it.slot to it.name })
		tooltip(ctx, mx, my)
	}
}
