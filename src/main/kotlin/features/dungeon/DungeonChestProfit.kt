/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex dungeon / Kuudra chest profit panel.
 * Reads the Croesus chest overview or an opened reward chest and lists value, cost and profit beside it.
 * Display only: nothing here clicks, closes or sends anything.
 * Line parsing and book naming live in ChestLootPricing.kt and repo/EnchantedBookNames.kt.
 */
package dev.ashtonite365.apex.features.dungeon

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.util.render.SidePanel
import dev.ashtonite365.apex.events.ScreenRenderPostEvent
import dev.ashtonite365.apex.events.SlotRenderEvents
import dev.ashtonite365.apex.repo.HypixelStaticData
import dev.ashtonite365.apex.repo.EnchantedBookNames
import dev.ashtonite365.apex.repo.ItemNameLookup
import dev.ashtonite365.apex.repo.RepoManager
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.accessors.getProperRectangle
import dev.ashtonite365.apex.util.bazaarProduct
import dev.ashtonite365.apex.util.hypixelIdText
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.mc.NeedsComponents
import dev.ashtonite365.apex.util.render.outlineBox
import dev.ashtonite365.apex.util.tr
import dev.ashtonite365.apex.util.bareText

@OptIn(NeedsComponents::class)
object DungeonChestProfit {
	val identifier: String
		get() = "dungeon-chest-profit"

	@Config
	object TConfig : ManagedConfig(category = Category.DUNGEONS, name = identifier) {
		val croesusOverlay by toggle("croesus-overlay") { true }
		val croesusSortByProfit by toggle("croesus-sort-by-profit") { true }
		val croesusHighlightBest by toggle("croesus-highlight-best") { true }
		val croesusDimOpened by toggle("croesus-dim-opened") { true }
		val rewardChestOverlay by toggle("reward-chest-overlay") { true }
		val includeEssence by toggle("include-essence") { true }
		val includeChestKey by toggle("include-chest-key") { true }
		val includeKismet by toggle("include-kismet") { true }
		val croesusHighlightUnopened by toggle("croesus-highlight-unopened") { true }
		val rerollConfirm by toggle("reroll-confirm") { true }

		val croesusKismetMark by toggle("croesus-kismet-mark") { false }
	}

	data class PricedItem(val label: Component, val value: Double?)

	data class ChestValue(
		val name: Component,
		val slot: Int,
		val items: List<PricedItem>,
		val cost: Double,

		val openable: Boolean = true,

		val keyCost: Double = 0.0,
	) {

		val value get() = items.sumOf { (it.value ?: 0.0).coerceAtLeast(0.0) }

		val coinCost get() = (cost - keyCost).coerceAtLeast(0.0)

		val profit get() = value - cost.coerceAtLeast(0.0)
		val missingPrices get() = items.any { it.value == null }
	}

	private val log = org.apache.logging.log4j.LogManager.getLogger("Apex/ChestProfit")

	private const val CHEST_KEY = "DUNGEON_CHEST_KEY"
	private const val REWARD_BUTTON = 31
	private const val REROLL_BUTTON = 50
	private val REWARD_ROW = 10 until 17

	object RepoLookup : ChestLootPricing.Lookup {
		override fun byName(name: String): String? = ItemNameLookup.guessItemByName(name, false)?.skyblockId
		override fun bookByName(enchantName: String): String? = RepoManager.enchantedBookCache.byName[enchantName]?.skyblockId
		override fun exists(id: String): Boolean = runCatching { RepoManager.getCoreItem(ItemId(id)) != null }.getOrDefault(false)
	}

	private object EssenceOnly : ChestLootPricing.Lookup {
		override fun byName(name: String): String? = null
		override fun bookByName(enchantName: String): String? = null
		override fun exists(id: String) = false
	}

	private fun bazaar(id: ItemId): HypixelStaticData.BazaarData? {
		val market = HypixelStaticData.bazaarData
		market[id.bazaarProduct]?.let { return it }
		val bookStock = EnchantedBookNames.bazaarStockFor(id.skyblockId) ?: return null
		return market[ItemId.BazaarProduct(bookStock)]
	}

	fun priceOf(id: ItemId): Double? = bazaar(id)?.quickStatus?.sellPrice ?: HypixelStaticData.lowestBin[id]

	private fun priceOfId(id: String): Double? {
		val sky = ItemId(id)
		return if (id.startsWith("ESSENCE_")) bazaar(sky)?.quickStatus?.sellPrice else priceOf(sky)
	}

	private fun instantBuy(id: String): Double? = bazaar(ItemId(id))?.quickStatus?.buyPrice

	fun priceLoreLine(line: Component, masterMode: Boolean = false): PricedItem? {
		val parsed = ChestLootPricing.parseLine(line.bareText, masterMode, RepoLookup) ?: return null
		return when {
			parsed is ChestLootPricing.Line.Essence && !TConfig.includeEssence -> PricedItem(line, 0.0)
			parsed is ChestLootPricing.Line.Item && parsed.id == CHEST_KEY -> null
			else -> PricedItem(line, ChestLootPricing.value(parsed, ::priceOfId))
		}
	}

	fun rewardStackId(stack: ItemStack): String? {
		val item = stack.view()
		if (item.hypixelIdText.isNullOrEmpty()) return null
		val core = item.repoItemId?.skyblockId ?: return null
		return if (core == "ENCHANTED_BOOK") null else core
	}

	private fun bookLabel(id: String, style: Component): Component? {
		val (enchant, level) = EnchantedBookNames.parseBookId(id) ?: return null
		val known = RepoManager.enchantedBookCache.byName.entries.firstOrNull { it.value.skyblockId == id }?.key
		val title = known ?: (EnchantedBookNames.baseNameFromId(enchant) + " " + EnchantedBookNames.roman(level))
		return Component.literal("Enchanted Book ($title)").withStyle(style.style)
	}

	fun priceRewardStack(stack: ItemStack, masterMode: Boolean = false, contentsBooks: MutableList<String>? = null): PricedItem? {
		val shown = stack.hoverName
		val plain = shown.bareText.trim()
		if (ChestLootPricing.parseLine(plain, masterMode, EssenceOnly) is ChestLootPricing.Line.Essence) return priceLoreLine(shown, masterMode)
		val id = rewardStackId(stack)
		if (id == null) {
			val fromContents = if (plain == "Enchanted Book" && !contentsBooks.isNullOrEmpty()) contentsBooks.removeAt(0) else null
			return priceLoreLine(fromContents?.let { Component.literal(it) } ?: shown, masterMode)
		}
		if (id == CHEST_KEY) return null
		val count = ChestLootPricing.amountFromName(plain, stack.count)
		return PricedItem(bookLabel(id, shown) ?: shown, priceOfId(id)?.times(count))
	}

	private val KEY_WITH_COUNT = Regex("Dungeon Chest Key x[0-9]+")
	private val KUUDRA_KEY = Regex("(.*Kuudra Key)(?: x([0-9]+))?")

	fun openingCost(
		lines: List<String>,
		countDungeonKey: Boolean, dungeonKeyPrice: () -> Double,
		countKuudraKeys: Boolean, kuudraKeyId: (String) -> String?, price: (String) -> Double?,
	): Double {
		var total = ChestLootPricing.coinCost(lines)

		if (countDungeonKey) {
			val keys = if (lines.any { it == "Dungeon Chest Key" || KEY_WITH_COUNT.matches(it) }) 1 else 0
			total += keys * dungeonKeyPrice()
		}
		if (countKuudraKeys) for (line in lines) {
			if ("Kuudra Key" !in line || line.startsWith("Requires")) continue
			val m = KUUDRA_KEY.matchEntire(line) ?: continue
			val id = runCatching { kuudraKeyId(m.groupValues[1].trim()) }.getOrNull() ?: continue
			val n = m.groupValues[2].let { if (it.isEmpty()) 1 else it.toIntOrNull() ?: 1 }
			total += (price(id) ?: 0.0) * n
		}
		return total
	}

	private fun costBelow(lore: List<Component>, from: Int, countKey: Boolean = true): Double = openingCost(
		lore.drop(from).map { it.bareText.trim() },
		countKey && TConfig.includeChestKey, { instantBuy(CHEST_KEY) ?: 0.0 },
		dev.ashtonite365.apex.features.kuudra.KuudraProfit.TConfig.includeKey, RepoLookup::byName, ::priceOfId,
	)

	private fun chestSlots(screen: AbstractContainerScreen<*>) = screen.menu.slots.filter { it.container !== Client.player?.inventory }

	private fun isCroesusOverview(screen: AbstractContainerScreen<*>): Boolean {
		if (screen.menu.slots.getOrNull(11)?.item?.`is`(Items.FILLED_MAP) == true) return false
		val title = screen.title.bareText
		if (ChestLootPricing.isChestPreviewTitle(title)) return true
		if (title.trim() == "Croesus") return false

		val shown = chestSlots(screen).filterNot { it.item.isEmpty }.map { slot ->
			slot.item.hoverName.bareText to slot.item.view().loreAccordingToNbt.map { it.bareText }
		}
		return ChestLootPricing.isCroesusPreview(title, shown)
	}

	fun parseCroesusChest(slot: Int, stack: ItemStack, masterMode: Boolean = false): ChestValue? {
		if (stack.isEmpty) return null
		val lore = stack.view().loreAccordingToNbt
		val (lootLines, costFrom) = ChestLootPricing.croesusSplit(lore.map { it.bareText }) ?: return null
		val loot = ArrayList<PricedItem>()
		for (i in lootLines) {
			val priced = try {
				priceLoreLine(lore[i], masterMode)
			} catch (e: Throwable) {
				failSoft("croesus chest line '${lore[i].bareText}'", e)
				PricedItem(lore[i], null)
			}
			if (priced != null) loot += priced
		}
		val total = costBelow(lore, costFrom)
		val withoutKey = costBelow(lore, costFrom, countKey = false)
		return ChestValue(stack.hoverName, slot, loot, total, openable = isOpenable(lore.map { it.bareText }),
			keyCost = (total - withoutKey).coerceAtLeast(0.0))
	}

	fun isOpenable(lore: List<String>): Boolean = lore.none { val t = it.trim(); t == "Already opened!" || t == "Can't open another chest!" }

	fun bestChest(chests: List<ChestValue>): ChestValue? =
		chests.filter { it.openable && it.profit > 0.0 }.maxByOrNull { it.profit }

	fun croesusChests(screen: AbstractContainerScreen<*>): List<ChestValue> {
		val stacks = screen.menu.slots.map { if (it.container === Client.player?.inventory) ItemStack.EMPTY else it.item }
		return croesusChests(stacks, ChestLootPricing.isMasterMode(screen.title.bareText))
	}

	fun croesusChests(stacks: List<ItemStack>, masterMode: Boolean): List<ChestValue> {
		val found = ArrayList<ChestValue>()
		for ((slot, stack) in stacks.withIndex()) {

			val chest = try {
				parseCroesusChest(slot, stack, masterMode)
			} catch (e: Throwable) {
				failSoft("croesus chest slot $slot '${stack.hoverName.bareText}'", e)
				null
			}
			if (chest != null) found += chest
		}
		return if (TConfig.croesusSortByProfit) found.sortedByDescending { it.profit } else found
	}

	private object Frame {
		var screen: Any? = null
		var at = 0L
		var chests: List<ChestValue> = emptyList()
	}

	private fun croesusFrame(screen: AbstractContainerScreen<*>): List<ChestValue> {
		val now = System.nanoTime()
		if (Frame.screen === screen && now - Frame.at <= 50_000_000L) return Frame.chests
		Frame.chests = croesusChests(screen)
		Frame.screen = screen
		Frame.at = now
		return Frame.chests
	}

	@Subscribe
	fun onSlotRender(event: SlotRenderEvents.After) {
		if (!TConfig.croesusOverlay || !TConfig.croesusHighlightBest) return
		val screen = Client.screen as? AbstractContainerScreen<*> ?: return
		try {
			if (!isCroesusOverview(screen)) return
			val top = bestChest(croesusFrame(screen)) ?: return
			val s = event.slot
			if (screen.menu.slots.getOrNull(top.slot) === s) event.context.fill(s.x, s.y, s.x + 16, s.y + 16, 0x8000FF00.toInt())
		} catch (e: Exception) {
			failSoft("best-chest highlight in '${screen.title.bareText}'", e)
		}
	}

	@Subscribe
	fun onCroesusRunSlot(event: SlotRenderEvents.After) {
		if (!TConfig.croesusDimOpened) return
		val screen = Client.screen as? AbstractContainerScreen<*> ?: return
		if (screen.title.bareText.trim() != "Croesus") return
		val s = event.slot
		if (s.container === Client.player?.inventory) return
		val looted = s.item.view().loreAccordingToNbt.any { it.bareText.contains("No more Chests to open", ignoreCase = true) }
		if (looted) event.context.fill(s.x, s.y, s.x + 16, s.y + 16, 0xA0000000.toInt())
	}

	private val reported = java.util.Collections.synchronizedSet(HashSet<String>())

	fun failSoft(where: String, e: Throwable) {
		val key = where.substringBefore(" '") + "|" + e.javaClass.name
		if (reported.add(key)) log.warn("[ApexChestProfit] chest profit failed in $where: $e", e)
	}

	private var emptyScreen: Any? = null
	private var emptySince = 0L

	private fun reportEmpty(screen: AbstractContainerScreen<*>, kind: String) {
		val now = System.nanoTime()
		if (emptyScreen !== screen) {
			emptyScreen = screen
			emptySince = now
			return
		}
		if (emptySince == Long.MIN_VALUE || now - emptySince < 1_500_000_000L) return
		emptySince = Long.MIN_VALUE
		val present = chestSlots(screen).filterNot { it.item.isEmpty }
		val withContents = present.count { it.item.view().loreAccordingToNbt.firstOrNull()?.bareText?.trim() == "Contents" }
		log.info("[ApexChestProfit] $kind '${screen.title.bareText}': no chest rows " +
			         "(${present.size} items, $withContents with Contents lore, bazaar=${HypixelStaticData.bazaarData.size}, " +
			         "lbin=${HypixelStaticData.lowestBin.size})")
	}

	fun parseRewardChest(screen: AbstractContainerScreen<*>): Pair<ChestValue, Double>? =
		parseRewardChest(screen.title, screen.menu.slots.map { it.item })

	fun parseRewardChest(title: Component, slots: List<ItemStack>): Pair<ChestValue, Double>? {
		val button = slots.getOrNull(REWARD_BUTTON) ?: return null
		if (button.hoverName.bareText.trim() != "Open Reward Chest") return null
		val lore = button.view().loreAccordingToNbt
		val plainLore = lore.map { it.bareText }
		val costHeader = plainLore.indexOfFirst { it.trim() == "Cost" }
		val cost = if (costHeader < 0) 0.0 else costBelow(lore, costHeader + 1)

		val books = ChestLootPricing.contentsBookLines(plainLore).toMutableList()
		val masterMode = ChestLootPricing.isMasterMode(title.bareText)
		val loot = ArrayList<PricedItem>()
		for (index in REWARD_ROW) {
			val stack = slots.getOrNull(index) ?: continue
			if (stack.isEmpty) continue
			if (stack.view().loreAccordingToNbt.any { "Soulbound" in it.bareText }) continue
			val priced = try {
				priceRewardStack(stack, masterMode, books)
			} catch (e: Throwable) {
				failSoft("reward chest slot $index '${stack.hoverName.bareText}'", e)
				PricedItem(stack.hoverName, null)
			}
			if (priced != null) loot += priced
		}
		val rerolled = slots.getOrNull(REROLL_BUTTON)?.view()?.loreAccordingToNbt
			?.any { "You already rerolled a chest!" in it.bareText } == true
		val feather = if (TConfig.includeKismet && rerolled) instantBuy("KISMET_FEATHER") ?: 0.0 else 0.0
		return ChestValue(title, REWARD_BUTTON, loot, cost) to feather
	}

	private fun short(v: Double) = ApexFormatters.compactNumber(v)

	fun profitLabel(profit: Double, missing: Boolean): String {
		val sign = if (profit >= 0) "§a" else "§c-"
		return sign + short(kotlin.math.abs(profit)) + if (missing) "§e?" else ""
	}

	fun tooltip(chest: ChestValue, kismet: Double): List<Component> {
		val out = ArrayList<Component>()
		out += chest.name
		for (item in chest.items) {
			val price = item.value?.let { "§a" + short(it) } ?: "§e(no price)"
			out += Component.literal("§b - ").append(item.label).append(Component.literal(" $price"))
		}
		out += Component.empty()
		out += Component.literal("§bValue: §6" + short(chest.value))
		out += Component.literal("§bCost to open: §c" + short(chest.coinCost))
		if (chest.keyCost > 0) out += Component.literal("§bDungeon Chest Key (listed in this chest's cost): §c" + short(chest.keyCost))
		if (kismet > 0) out += Component.literal("§bKismet Feather: §c" + short(kismet))
		out += Component.literal("§bProfit: ").append(Component.literal(profitLabel(chest.profit - kismet, chest.missingPrices)))
		return out
	}

	@Subscribe
	fun onScreenRender(event: ScreenRenderPostEvent) {
		val screen = event.screen as? AbstractContainerScreen<*> ?: return
		val rows: List<Pair<ChestValue, List<Component>>> = try {
			when {
				TConfig.croesusOverlay && isCroesusOverview(screen) -> {
					val chests = croesusFrame(screen)
					if (chests.isEmpty()) {
						reportEmpty(screen, "Croesus chest screen")
						return
					}
					chests.map { it to tooltip(it, 0.0) }
				}
				TConfig.rewardChestOverlay -> {
					val (chest, feather) = parseRewardChest(screen) ?: return
					listOf(chest.copy(cost = chest.cost + feather) to tooltip(chest, feather))
				}
				else -> return
			}
		} catch (e: Exception) {
			failSoft("'${screen.title.bareText}'", e)
			drawFailure(event, screen)
			return
		}
		if (rows.isEmpty()) return

		val panelRows = panelRows(rows.map { it.first })
		val placed = SidePanel.place("chest-profit", screen, SidePanel.Side.RIGHT,
			tr("apex.dungeon.chest-profit.title", "Chest profit").string, panelRows, minInner = 168) ?: return
		val hoveredRow = (0 until panelRows.size).firstOrNull { i ->
			event.mouseX >= placed.x && event.mouseX < placed.x + placed.w && event.mouseY >= placed.rowY(i) && event.mouseY < placed.rowY(i) + placed.rowH()
		}
		SidePanel.draw(event.drawContext, placed, highlightRow = hoveredRow)
		hoveredRow?.let { rows.getOrNull(it / ROWS_PER_CHEST)?.second }?.let { event.drawContext.setComponentTooltipForNextFrame(Client.font, it, event.mouseX, event.mouseY) }
	}

	const val ROWS_PER_CHEST = 2

	fun panelRows(chests: List<ChestValue>): List<SidePanel.Row> = chests.flatMap { c ->
		val name = "§f" + c.name.string
		val key = if (c.keyCost > 0) " §7key §c" + short(c.keyCost) else ""
		listOf(SidePanel.Row(name, profitLabel(c.profit, c.missingPrices)),
			SidePanel.Row("§8 loot §6" + short(c.value) + " §7open §c" + short(c.coinCost) + key))
	}

	private fun drawFailure(event: ScreenRenderPostEvent, screen: AbstractContainerScreen<*>) {
		runCatching {
			val bounds = SidePanel.anchorOf(screen)
			val x = bounds.x + bounds.width + 4
			val y = bounds.y
			val ctx = event.drawContext
			SidePanel.drawBox(ctx, x, y, x + 180, y + 26)
			SidePanel.text(ctx, tr("apex.dungeon.chest-profit.title", "Chest profit"), x + 6, y + 4)
			SidePanel.text(ctx, tr("apex.dungeon.chest-profit.error", "§cCould not read this chest (see log)"), x + 6, y + 14)
		}
	}
}
