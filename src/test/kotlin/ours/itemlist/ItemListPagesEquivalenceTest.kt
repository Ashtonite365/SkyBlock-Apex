// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.ours.itemlist

import me.shedaniel.math.Point
import me.shedaniel.math.Rectangle
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import org.junit.jupiter.api.Test
import io.github.notenoughupdates.moulconfig.gui.GuiComponent
import dev.ashtonite365.apex.features.items.recipes.ViewerTab
import dev.ashtonite365.apex.features.items.recipes.ViewerTabs
import dev.ashtonite365.apex.features.items.recipes.itemlist.BoxPageRef
import dev.ashtonite365.apex.features.items.recipes.itemlist.HowToPage
import dev.ashtonite365.apex.features.items.recipes.itemlist.ItemListNavigation
import dev.ashtonite365.apex.features.items.recipes.itemlist.ItemListNavigator
import dev.ashtonite365.apex.features.items.recipes.itemlist.ItemListPage
import dev.ashtonite365.apex.features.items.recipes.itemlist.ItemListPages
import dev.ashtonite365.apex.features.items.recipes.itemlist.ItemPageRef
import dev.ashtonite365.apex.features.items.recipes.itemlist.MobPageRef
import dev.ashtonite365.apex.features.items.recipes.itemlist.PageCanvas
import dev.ashtonite365.apex.features.items.recipes.itemlist.PageLine
import dev.ashtonite365.apex.features.items.recipes.itemlist.PetBaseRef
import dev.ashtonite365.apex.features.items.recipes.itemlist.dropStacks
import dev.ashtonite365.apex.features.items.recipes.itemlist.mobTooltip
import dev.ashtonite365.apex.features.menus.craft.CraftHandoff
import dev.ashtonite365.apex.repo.RepoManager
import dev.ashtonite365.apex.repo.SBItemStack
import dev.ashtonite365.apex.repo.mobs.MobEntry
import dev.ashtonite365.apex.repo.mobs.MobIndex
import dev.ashtonite365.apex.repo.recipes.RecipeLayouter
import dev.ashtonite365.apex.test.ours.OursGolden

class ItemListPagesEquivalenceTest {
	init {
		dev.ashtonite365.apex.test.ApexTestBootstrap.bootstrapMinecraft()
		RepoManager.initialize()
	}

	private val log = mutableListOf<String>()

	private fun <T> attempt(block: () -> T): String = try {
		block().toString()
	} catch (e: Throwable) {
		"ERR:" + e.javaClass.simpleName
	}

	private fun t(c: Component?): String = if (c == null) "null" else attempt { OursGolden.text(c) }
	private fun sb(s: SBItemStack?): String = if (s == null) "-" else
		"${s.catalogItemId.skyblockId}x${s.getStackSize()}*${s.stars}r${s.reforge?.id}p${s.getPetData()}[" +
			s.extraLore.joinToString("|") { it.string } + "]"
	private fun sbs(l: List<SBItemStack>) = l.joinToString(",", "[", "]") { sb(it) }
	private fun rect(r: Rectangle) = "${r.x},${r.y},${r.width}x${r.height}"
	private fun icon(s: ItemStack) = attempt { "${BuiltInRegistries.ITEM.getKey(s.item)}x${s.count}" } + ":" +
		attempt { s.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME)?.let { t(it) } }

	private inner class Rec : PageCanvas {
		val out = mutableListOf<String>()
		val actions = mutableListOf<Pair<String, () -> Unit>>()
		override fun cyclingSlotAt(x: Int, y: Int, content: List<SBItemStack>, slotRole: RecipeLayouter.SlotRole): RecipeLayouter.RotatingCell {
			out += "slot $x,$y $slotRole ${sbs(content)}"
			return object : RecipeLayouter.RotatingCell {
				override fun showing() = content.firstOrNull() ?: SBItemStack(dev.ashtonite365.apex.util.ItemId("AIR"))
				override fun setTo(value: SBItemStack) { out += "  setTo ${sb(value)}" }
				override fun onRotate(listener: () -> Unit) { out += "  onRotate" }
			}
		}
		override fun itemSlotAt(x: Int, y: Int, content: SBItemStack?, slotRole: RecipeLayouter.SlotRole): RecipeLayouter.StackCell {
			out += "item $x,$y $slotRole ${sb(content)}"
			return object : RecipeLayouter.StackCell {
				override fun showing() = content ?: SBItemStack(dev.ashtonite365.apex.util.ItemId("AIR"))
				override fun setTo(value: SBItemStack) { out += "  setTo ${sb(value)}" }
			}
		}
		override fun placeLabel(x: Int, y: Int, text: Component): RecipeLayouter.Settable<Component> {
			out += "label $x,$y ${t(text)}"
			return RecipeLayouter.Settable { out += "  relabel ${t(it)}" }
		}
		override fun hoverArea(rectangle: Rectangle, label: List<Component>) { out += "hover ${rect(rectangle)} ${label.map { t(it) }}" }
		override fun arrowAt(left: Int, top: Int): Rectangle { out += "arrow $left,$top"; return Rectangle(left, top, 24, 17) }
		override fun flameAt(point: Point, animationTicks: Int) { out += "flame ${point.x},${point.y} $animationTicks" }
		override fun entityIn(rectangle: Rectangle, entity: LivingEntity) { out += "entity ${rect(rectangle)} ${entity.javaClass.simpleName}" }
		override fun embedAt(box: Rectangle, component: GuiComponent) { out += "embed ${rect(box)} ${component.javaClass.simpleName}" }
		override fun wrappedText(x: Int, y: Int, width: Int, lines: List<Component>) { out += "text $x,$y w$width ${lines.map { t(it) }}" }
		override fun mobPreview(mob: MobEntry, box: Rectangle, tooltip: List<Component>) { out += "mob ${mob.id} ${rect(box)} ${tooltip.map { t(it) }}" }
		override fun clickArea(box: Rectangle, onClick: () -> Unit) { out += "click ${rect(box)}"; actions += "click ${rect(box)}" to onClick }
		override fun scrollLines(box: Rectangle, lines: List<PageLine>) {
			out += "scroll ${rect(box)} ${lines.size}"
			lines.forEachIndexed { i, l ->
				out += "  line $i ${t(l.text)} ${if (l.action == null) "-" else "act"}"
				l.action?.let { actions += "line $i" to it }
			}
		}
		override fun lookupItems(items: List<SBItemStack>) { out += "lookup ${sbs(items)}" }
	}

	private val navigator = object : ItemListNavigator {
		override fun leaveRecipeScreen() { log += "nav.leave" }
		override fun openMob(mobId: String?) { log += "nav.mob $mobId" }
		override fun openItemPage(page: ItemListPage<ItemPageRef>, itemId: String) { log += "nav.page ${page.id} $itemId" }
		override fun openRecipesFor(itemId: String) { log += "nav.recipes $itemId" }
	}

	private fun <T : Any> record(page: ItemListPage<T>, recipe: T, out: MutableList<String>) {
		out += "== ${page.id} $recipe"
		val rec = Rec()
		val drawn = attempt { page.draw(recipe, Rectangle(0, 0, page.width, page.height), rec) }
		out += rec.out
		if (drawn != "kotlin.Unit") out += "draw $drawn"
		out += "inputs " + attempt { page.inputs(recipe).map { sbs(it) } }
		out += "outputs " + attempt { page.outputs(recipe).map { sbs(it) } }
		val craft = attempt { page.craftAction(recipe) }
		out += "craft " + if (craft.startsWith("ERR")) craft else if (craft == "null") "null" else "set"
		for ((what, act) in rec.actions) {
			log.clear()
			val r = attempt(act)
			out += "run $what -> $log ${if (r == "kotlin.Unit") "" else r}".trimEnd()
		}
		page.craftAction(recipe)?.let { log.clear(); val r = attempt(it); out += "run craft -> $log ${if (r == "kotlin.Unit") "" else r}".trimEnd() }
	}

	private fun <T> sample(all: List<T>, n: Int): List<T> =
		if (all.size <= n) all else (0 until n).map { all[it * all.size / n] }

	private fun withHooks(block: () -> Unit) {
		val oldNav = ItemListNavigation.navigator
		val oldSend = CraftHandoff.sendCommand
		val oldOpen = CraftHandoff.craftWindowOpen
		val oldNow = CraftHandoff.now
		ViewerTabs.resetForTest()
		for ((i, id) in listOf("items", "mobs", "howto", "prices").withIndex())
			ViewerTabs.register(ViewerTab(id, Component.literal(id), i, { e -> log += "tab.$id $e" }, { true }))
		ItemListNavigation.navigator = navigator
		CraftHandoff.sendCommand = { log += "cmd $it" }
		CraftHandoff.craftWindowOpen = { false }
		CraftHandoff.now = { 1_000_000L }
		try { block() } finally {
			ItemListNavigation.navigator = oldNav
			CraftHandoff.sendCommand = oldSend
			CraftHandoff.craftWindowOpen = oldOpen
			CraftHandoff.now = oldNow
			CraftHandoff.resetForTests()
			ViewerTabs.resetForTest()
		}
	}

	@Test
	fun pageHeaders() {
		val out = mutableListOf<String>()
		out += "MOBS ${ItemListPages.MOBS} HOW_TO ${ItemListPages.HOW_TO} PRICES ${ItemListPages.PRICES} CRAFTING ${ItemListPages.CRAFTING}"
		out += "id ${ItemListPages.id("x_y")}"
		for ((i, p) in ItemListPages.all().withIndex()) {
			out += "$i ${p.javaClass.simpleName} ${p.id} ${t(p.title)} ${icon(p.icon)} ${p.width}x${p.height} n=${attempt { p.allRecipes().size }}"
		}
		OursGolden.check("itemlist-pages-headers", out)
	}

	@Suppress("UNCHECKED_CAST")
	@Test
	fun pageDrawing() = withHooks {
		val out = mutableListOf<String>()
		for (p in ItemListPages.all()) {
			val page = p as ItemListPage<Any>
			val recipes = runCatching { page.allRecipes() }.getOrDefault(listOf())
			for (r in sample(recipes, 12)) { CraftHandoff.resetForTests(); record(page, r, out) }
		}
		OursGolden.checkDigests("itemlist-pages-draw", out.mapIndexed { i, s -> "$i" to s })
		OursGolden.check("itemlist-pages-draw-sample", out.filterIndexed { i, _ -> i % 7 == 0 })
	}

	@Test
	fun itemPages() = withHooks {
		val out = mutableListOf<String>()
		val ids = RepoManager.allItemIds().sorted()
		val dropped = MobIndex.droppedBy.keys.sorted()
		val picks = (sample(ids, 40) + sample(dropped, 10) + listOf("NOT_AN_ITEM")).distinct()
		for (id in picks) {
			out += "data $id " + MobIndex.mobsDropping(id).map { m -> m.id + ":" + m.drops.filter { it.itemId == id } }
			out += "lines $id " + attempt { HowToPage.lines(id).map { "${t(it.text)}${if (it.action == null) "" else "*"}" } }
			record(HowToPage, ItemPageRef(id), out)
			record(dev.ashtonite365.apex.features.items.recipes.itemlist.PricesPage, ItemPageRef(id), out)
		}
		OursGolden.checkDigests("itemlist-pages-items", out.mapIndexed { i, s -> "$i" to s })
		OursGolden.check("itemlist-pages-items-sample", out.filterIndexed { i, _ -> i % 5 == 0 })
	}

	@Test
	fun mobPages() = withHooks {
		val out = mutableListOf<String>()
		val mobs = MobIndex.all().sortedBy { it.id }
		out += "mobs ${mobs.size}"
		for (m in sample(mobs, 25)) {
			out += "tooltip ${m.id} " + attempt { mobTooltip(m).map { t(it) } }
			out += "drops ${m.id} " + attempt { sbs(dropStacks(m)) }
			record(dev.ashtonite365.apex.features.items.recipes.itemlist.MobsPage, MobPageRef(m.id), out)
			record(dev.ashtonite365.apex.features.items.recipes.itemlist.MobDropsPage, MobPageRef(m.id), out)
		}
		record(dev.ashtonite365.apex.features.items.recipes.itemlist.MobsPage, MobPageRef("no_such_mob"), out)
		record(dev.ashtonite365.apex.features.items.recipes.itemlist.MobDropsPage, MobPageRef("no_such_mob"), out)
		out += "ref ${attempt { MobPageRef(mobs.firstOrNull()?.id ?: "x").mob?.id }} ${attempt { MobPageRef("no_such_mob").mob }}"
		OursGolden.checkDigests("itemlist-pages-mobs", out.mapIndexed { i, s -> "$i" to s })
		OursGolden.check("itemlist-pages-mobs-sample", out.filterIndexed { i, _ -> i % 5 == 0 })
	}

	@Test
	fun oddRefs() = withHooks {
		val out = mutableListOf<String>()
		record(dev.ashtonite365.apex.features.items.recipes.itemlist.ContentsPage, BoxPageRef("NO_SUCH_BOX", 0), out)
		record(dev.ashtonite365.apex.features.items.recipes.itemlist.PetRaritiesPage, PetBaseRef("NO_SUCH_PET"), out)
		val boxes = runCatching { dev.ashtonite365.apex.features.items.recipes.itemlist.ContentsPage.allRecipes() }.getOrDefault(listOf())
		boxes.firstOrNull()?.let { b ->
			for (pg in listOf(1, 99)) record(dev.ashtonite365.apex.features.items.recipes.itemlist.ContentsPage, BoxPageRef(b.box, pg), out)
		}
		out += "BODY_TOP ${dev.ashtonite365.apex.features.items.recipes.itemlist.ContentsPage.BODY_TOP}"
		OursGolden.check("itemlist-pages-odd", out)
	}

	@Test
	fun pricedItems() = withHooks {
		val h = dev.ashtonite365.apex.repo.HypixelStaticData
		val f = h.javaClass.getDeclaredField("bazaarData").apply { isAccessible = true }
		val oldBz = f.get(h); val oldLb = h.lowestBin; val oldAvg = h.avg1dlowestBin
		fun bz(id: String, sell: Double, buy: Double) = dev.ashtonite365.apex.util.ItemId.BazaarProduct(id) to
			dev.ashtonite365.apex.repo.HypixelStaticData.BazaarData(dev.ashtonite365.apex.util.ItemId.BazaarProduct(id), dev.ashtonite365.apex.repo.HypixelStaticData.BazaarStatus(sell, 1, 1, 1, buy, 1, 1, 1))
		val out = mutableListOf<String>()
		try {
			f.set(h, mapOf(bz("ENCHANTED_DIAMOND", 1234.5, 1300.25), bz("FEATHER", 0.4, 12_345_678.9)))
			h.lowestBin = mapOf(dev.ashtonite365.apex.util.ItemId("IRON_BOOTS") to 750_000_000.0, dev.ashtonite365.apex.util.ItemId("FEATHER") to 3.0)
			h.avg1dlowestBin = mapOf(dev.ashtonite365.apex.util.ItemId("IRON_BOOTS") to 1_500.0)
			for (id in listOf("ENCHANTED_DIAMOND", "FEATHER", "IRON_BOOTS", "GOLD_NUGGET")) {
				out += "lines $id " + attempt { HowToPage.lines(id).map { "${t(it.text)}${if (it.action == null) "" else "*"}" } }
				record(HowToPage, ItemPageRef(id), out)
				record(dev.ashtonite365.apex.features.items.recipes.itemlist.PricesPage, ItemPageRef(id), out)
			}
		} finally {
			f.set(h, oldBz); h.lowestBin = oldLb; h.avg1dlowestBin = oldAvg
		}
		OursGolden.check("itemlist-pages-prices", out)
	}
}
