/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex inventory overlays.
 */
package dev.ashtonite365.apex.features.inventory

import java.math.BigDecimal
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.network.chat.Component
import net.minecraft.util.StringRepresentable
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.entity.player.Inventory
import io.github.moulberry.repo.data.Rarity
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ChestInventoryUpdateEvent
import dev.ashtonite365.apex.events.ItemTooltipEvent
import dev.ashtonite365.apex.events.ReloadRegistrationEvent
import dev.ashtonite365.apex.events.ScreenRenderPostEvent
import dev.ashtonite365.apex.features.inventory.storageoverlay.VirtualInventory
import dev.ashtonite365.apex.mixins.accessor.AccessorHandledScreen
import dev.ashtonite365.apex.repo.ExpLadders
import dev.ashtonite365.apex.repo.HypixelStaticData
import dev.ashtonite365.apex.repo.ReforgeStore.kJson
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.ItemListSearchProvider
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.data.ProfileSpecificDataHolder
import dev.ashtonite365.apex.util.getLegacyFormatString
import dev.ashtonite365.apex.util.math.Calculator
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.keepable
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.petBlob
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.bareText

object InventoryOverlays {
	enum class CalcMode : StringRepresentable {
		OFF, PREFIX, ALWAYS;
		override fun getSerializedName() = name
	}

	@Config
	object TConfig : ManagedConfig("core-inventory-overlays", Category.INVENTORY) {
		val searchCalculator by choice("search-calculator") { CalcMode.PREFIX }
		val equipmentOverlay by toggle("equipment-overlay") { true }
		val katSitter by toggle("kat-sitter") { true }
		val trophyReward by toggle("trophy-reward") { true }
		val tradeValue by toggle("trade-value") { true }
	}

	@Serializable
	data class Data(
		@Serializable(with = VirtualInventory.Serializer::class)
		var equipment: VirtualInventory? = null,
	)

	@Config
	object DConfig : ProfileSpecificDataHolder<Data>(serializer(), "equipment-overlay", ::Data)

	fun priceOf(id: ItemId): Double? = HypixelStaticData.getPriceOfItem(id)

	val PRICE_VARIABLES = Calculator.VariableProvider { name -> priceOf(ItemId(name))?.let { BigDecimal.valueOf(it) } }

	fun expressionIn(text: String, mode: CalcMode): String? = when {
		text.isEmpty() -> null
		mode == CalcMode.OFF -> null
		mode == CalcMode.ALWAYS -> text
		text.startsWith("!") -> text.drop(1)
		else -> null
	}

	fun searchResult(text: String, mode: CalcMode, vars: Calculator.VariableProvider = PRICE_VARIABLES): String? {
		val expr = expressionIn(text, mode)?.takeUnless(Calculator::isPlainNumber) ?: return null
		return Calculator.evaluateOrNull(expr, vars)?.let(Calculator::format)
	}

	private var cachedQuery = ""
	private var cachedAnswer: String? = null

	@Subscribe
	fun onRenderSearch(event: ScreenRenderPostEvent) {
		val mode = TConfig.searchCalculator
		if (mode == CalcMode.OFF) return
		val query = ItemListSearchProvider.searchText() ?: return
		if (query != cachedQuery) {
			cachedQuery = query
			cachedAnswer = searchResult(query, mode)
		}
		val label = "§e= §a" + (cachedAnswer ?: return)
		val width = Client.font.width(label)
		val x = event.screen.width - width - 6
		val y = event.screen.height - 36
		event.drawContext.nextStratum()
		event.drawContext.fill(x - 3, y - 3, x + width + 3, y + 11, 0xC0000000.toInt())
		event.drawContext.text(Client.font, label, x, y, 0xFFFFFFFF.toInt())
	}

	val EQUIPMENT_SLOTS = intArrayOf(10, 19, 28, 37)

	@Subscribe
	fun onChest(event: ChestInventoryUpdateEvent) {
		if (!TConfig.equipmentOverlay || Client.screenTitle != "Your Equipment and Armor") return
		val slots = Client.containerScreen?.menu?.slots ?: return
		val worn = EQUIPMENT_SLOTS.map { slots.getOrNull(it)?.item?.copy() ?: ItemStack.EMPTY }
		if (worn.all { it.isEmpty }) return
		val data = DConfig.data ?: return
		val padded = worn + List(5) { ItemStack.EMPTY }
		data.equipment = VirtualInventory(padded.map { it.keepable() })
		DConfig.markDirty()
	}

	@OptIn(dev.ashtonite365.apex.util.mc.NeedsComponents::class)
	@Subscribe
	fun onRenderInventory(event: ScreenRenderPostEvent) {
		if (!TConfig.equipmentOverlay || !SkyBlockSession.inSkyBlock) return
		val screen = event.screen as? InventoryScreen ?: return
		val saved = DConfig.data?.equipment ?: return
		val origin = screen as AccessorHandledScreen
		val x = origin.x_Apex - 26
		val top = origin.y_Apex + 8
		val ctx = event.drawContext
		ctx.fill(x - 4, top - 4, x + 20, top + 72, 0xE0303030.toInt())
		repeat(4) { row ->
			val stack = saved.stacks.getOrNull(row)?.rebuildLive() ?: return@repeat
			val y = top + row * 18
			ctx.fill(x - 1, y - 1, x + 17, y + 17, 0xFF8B8B8B.toInt())
			if (stack.isEmpty) return@repeat
			ctx.item(stack, x, y)
			val hovered = event.mouseX >= x && event.mouseX < x + 16 && event.mouseY >= y && event.mouseY < y + 16
			if (hovered) ctx.setTooltipForNextFrame(Client.font, stack, event.mouseX, event.mouseY)
		}
	}

	fun nextRarity(r: Rarity): Rarity? = Rarity.entries.getOrNull(r.ordinal + 1)

	fun katLines(current: Any, upgraded: Any?): List<String> {
		val out = arrayListOf("Current pet level: $current")
		if (upgraded != null) out += "Upgraded pet level: $upgraded"
		return out
	}

	@Subscribe
	fun onRenderKat(event: ScreenRenderPostEvent) {
		if (!TConfig.katSitter || Client.screenTitle != "Pet Sitter") return
		val screen = event.screen as? AbstractContainerScreen<*> ?: return
		val slots = screen.menu.slots
		val pet = slots.getOrNull(13)?.item?.view()?.petBlob ?: return
		val upgradeItem = slots.getOrNull(22)?.item
		val canUpgrade = upgradeItem != null && !upgradeItem.isEmpty && upgradeItem.item != Items.BARRIER
		val after = if (!canUpgrade) null else nextRarity(pet.rarity)?.let {
			ExpLadders.getExpLadder(pet.species, it).getPetLevel(pet.xp).levelText
		}
		val lines = katLines(pet.level.levelText, after)
		val origin = screen as AccessorHandledScreen
		val x = origin.x_Apex - 10 - lines.maxOf { Client.font.width(it) }
		for ((i, line) in lines.withIndex()) event.drawContext.text(Client.font, line, x, origin.y_Apex + 25 + i * 20, 0xFFFFD700.toInt())
	}

	private val TROPHY_TIERS = arrayOf("_BRONZE", "_SILVER", "_GOLD", "_DIAMOND")
	private var trophyValues: Map<String, Int> = mapOf()

	fun trophyTable(raw: Map<String, List<Int>>): Map<String, Int> {
		val out = LinkedHashMap<String, Int>()
		for ((fish, perTier) in raw) for (i in 0 until minOf(perTier.size, TROPHY_TIERS.size)) out[fish + TROPHY_TIERS[i]] = perTier[i]
		return out
	}

	@Subscribe
	fun onRepo(event: ReloadRegistrationEvent) {
		event.repo.registerReloadListener { repo ->
			val raw = runCatching { repo.file("constants/trophyfish.json")?.kJson(serializer<Map<String, List<Int>>>()) }.getOrNull()
			trophyValues = trophyTable(raw ?: mapOf())
		}
	}

	data class TrophyLine(val name: String, val amount: Int, val value: Int)

	data class Held(val coreId: String?, val name: String, val count: Int)

	fun tally(stacks: List<Held>, values: Map<String, Int>): List<TrophyLine> {
		val groups = LinkedHashMap<String, IntArray>()
		for (s in stacks) {
			val rate = values[s.coreId ?: continue] ?: continue
			val acc = groups.getOrPut(s.name) { IntArray(2) }
			acc[0] += s.count
			acc[1] += rate * s.count
		}
		return groups.map { (name, acc) -> TrophyLine(name, acc[0], acc[1]) }.sortedByDescending { it.value }
	}

	fun trophyPanel(entries: List<TrophyLine>, haveData: Boolean): List<String> = buildList {
		add("§lTrophy Fish Exchange")
		add("Magma Fish: §e" + entries.sumOf { it.value })
		for (e in entries.take(10)) add("${e.amount}x ${e.name}§r: §e${e.value}")
		val hidden = entries.size - 10
		if (hidden > 0) add("§8And $hidden more..")
		if (!haveData) add("§cNo data in repo found!")
	}

	private fun chestStacks(screen: AbstractContainerScreen<*>): List<Held> = screen.menu.slots
		.filter { it.container !is Inventory }
		.mapNotNull { slot ->
			val stack = slot.item
			val id = stack.view().repoItemId ?: return@mapNotNull null
			if (id.skyblockId !in trophyValues) return@mapNotNull null
			Held(id.skyblockId, stack.hoverName.getLegacyFormatString(), stack.count)
		}

	private fun isTrophyMenu() = Client.screenTitle.equals("Trophy Fishing", ignoreCase = true)

	@Subscribe
	fun onRenderTrophy(event: ScreenRenderPostEvent) {
		if (!TConfig.trophyReward || !isTrophyMenu()) return
		val screen = event.screen as? AbstractContainerScreen<*> ?: return
		val origin = screen as AccessorHandledScreen
		val lines = trophyPanel(tally(chestStacks(screen), trophyValues), trophyValues.isNotEmpty())
		drawPanel(event, origin.x_Apex + origin.backgroundWidth_Apex + 3, origin.y_Apex, lines)
	}

	@Subscribe
	fun onTrophyTooltip(event: ItemTooltipEvent) {
		if (!TConfig.trophyReward || !isTrophyMenu()) return
		val stack = event.stack
		if (stack.hoverName.bareText != "Fillet Trophy Fish") return
		if (stack.view().loreAccordingToNbt.any { it.bareText == "Sacks" }) return
		val screen = Client.containerScreen ?: return
		val magma = tally(chestStacks(screen), trophyValues).sumOf { it.value }
		event.lines.add(minOf(2, event.lines.size), Component.literal("§7Magma Fish from this inventory: §e$magma"))
	}

	private val COIN_STACK = Regex("([.,0-9]+)([kKmMbB]?) coins")

	fun coinStackValue(name: String): Double? {
		val m = COIN_STACK.find(name) ?: return null
		val scale = when (m.groupValues[2]) { "k", "K" -> 1e3; "m", "M" -> 1e6; "b", "B" -> 1e9; else -> 1.0 }
		return (m.groupValues[1].replace(",", "").toDoubleOrNull() ?: 0.0) * scale
	}

	data class Offer(val id: ItemId?, val name: String, val count: Int)

	fun sideTotal(offers: List<Offer>, price: (ItemId) -> Double?): Pair<Double, Int> {
		var sum = 0.0
		var missing = 0
		for (o in offers) {
			val coins = if (o.id == null) coinStackValue(o.name) else null
			if (coins != null) { sum += coins; continue }
			val each = o.id?.let(price)
			if (each == null) missing++ else sum += each * o.count
		}
		return sum to missing
	}

	fun tradePanel(you: Pair<Double, Int>, them: Pair<Double, Int>): List<String> {
		fun side(v: Pair<Double, Int>) = "§6" + ApexFormatters.compactNumber(v.first) + if (v.second > 0) " §8(+${v.second} unpriced)" else ""
		return listOf("§lTrade value", "You: " + side(you), "Them: " + side(them))
	}

	@Subscribe
	fun onRenderTrade(event: ScreenRenderPostEvent) {
		if (!TConfig.tradeValue || !SkyBlockSession.inSkyBlock) return
		if (Client.screenTitle?.startsWith("You    ") != true) return
		val screen = event.screen as? AbstractContainerScreen<*> ?: return
		val slots = screen.menu.slots
		fun offers(columns: IntRange) = (0 until 4).flatMap { row ->
			columns.mapNotNull { col ->
				val stack = slots.getOrNull(row * 9 + col)?.item
				if (stack == null || stack.isEmpty) null
				else Offer(stack.view().repoItemId, stack.hoverName.bareText, stack.count)
			}
		}
		val origin = screen as AccessorHandledScreen
		val lines = tradePanel(sideTotal(offers(0..3), ::priceOf), sideTotal(offers(5..8), ::priceOf))
		drawPanel(event, origin.x_Apex + origin.backgroundWidth_Apex + 3, origin.y_Apex, lines)
	}

	private fun drawPanel(event: ScreenRenderPostEvent, x: Int, y: Int, lines: List<String>) {
		val ctx = event.drawContext
		ctx.fill(x, y, x + lines.maxOf { Client.font.width(it) } + 8, y + lines.size * 10 + 6, 0xE0202020.toInt())
		for ((i, line) in lines.withIndex()) ctx.text(Client.font, line, x + 4, y + 4 + i * 10, 0xFFFFFFFF.toInt())
	}
}
