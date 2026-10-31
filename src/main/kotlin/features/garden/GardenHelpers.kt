/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex (26.3 port): garden helpers, options of GardenTools.
 * Inspired by the visitor shopping list, money per hour, Jacob contest warning and composter display
 * (feature descriptions only); original implementation, no code copied.
 */
package dev.ashtonite365.apex.features.garden

import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ChestInventoryUpdateEvent
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.features.calendar.SkyBlockEventTimers
import dev.ashtonite365.apex.repo.HypixelStaticData
import dev.ashtonite365.apex.repo.ItemNameLookup
import dev.ashtonite365.apex.repo.RepoConstants
import dev.ashtonite365.apex.util.Alerts
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.data.ProfileSpecificDataHolder
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.render.drawHudLines
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.skyblock.TabListAPI
import dev.ashtonite365.apex.util.bareText

object GardenHelpers {
	private val T get() = GardenTools.TConfig

	@Serializable
	data class Visitors(val wanted: MutableMap<String, MutableMap<String, Int>> = mutableMapOf())

	@Config
	object DConfig : ProfileSpecificDataHolder<Visitors>(serializer(), "garden-visitors", ::Visitors)

	private val ARRIVED = Regex("^(.+) has arrived on your Garden!$")
	private val ACCEPTED = Regex("^OFFER ACCEPTED with (.+?)(?: \\(.*\\))?$")
	private val REQUIRED = Regex("^ ?([A-Za-z' \\-]+?)(?: x([\\d,]+))?$")

	fun parseVisitorArrival(line: String): String? = ARRIVED.matchEntire(line.trim())?.groupValues?.get(1)
	fun parseVisitorAccepted(line: String): String? = ACCEPTED.matchEntire(line.trim())?.groupValues?.get(1)

	fun parseRequired(lore: List<String>): Map<String, Int> {
		val start = lore.indexOfFirst { it.trim() == "Items Required:" }
		if (start < 0) return emptyMap()
		val out = linkedMapOf<String, Int>()
		for (line in lore.drop(start + 1)) {
			if (line.isBlank()) break
			val m = REQUIRED.matchEntire(line) ?: continue
			out.merge(m.groupValues[1].trim(), m.groupValues[2].replace(",", "").ifEmpty { "1" }.toInt(), Int::plus)
		}
		return out
	}

	fun shoppingList(v: Visitors): Map<String, Int> {
		val out = sortedMapOf<String, Int>()
		v.wanted.values.forEach { it.forEach { (k, n) -> out.merge(k, n, Int::plus) } }
		return out
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (SkyBlockSession.skyblockIsland != SkyBlockIsland.GARDEN) return
		val line = event.bareText
		parseVisitorArrival(line)?.let { if (T.visitorAlert) Alerts.show(Component.literal("§a§lVisitor: " + if (T.visitorColourNames) GardenVisitorExtras.colouredName(it) else "§f$it"), kotlin.time.Duration.parse("2s")) }
		parseVisitorAccepted(line)?.let { name -> DConfig.data?.wanted?.remove(name); DConfig.markDirty(); visitorsChanged() }
	}

	@Subscribe
	fun onVisitorMenu(event: ChestInventoryUpdateEvent) {
		if (SkyBlockSession.skyblockIsland != SkyBlockIsland.GARDEN || !T.visitorShoppingList) return
		val screen = Client.containerScreen ?: return
		val offer = screen.menu.slots.map { it.item }.firstOrNull { it.hoverName.bareText.trim() == "Accept Offer" } ?: return
		val wanted = parseRequired(offer.view().loreAccordingToNbt.map { it.bareText })
		if (wanted.isEmpty()) return
		DConfig.data?.wanted?.set(screen.title.bareText.trim(), wanted.toMutableMap())
		DConfig.markDirty()
		visitorsChanged()
	}

	@Subscribe
	fun onCommand(event: CommandEvent.SubCommand) {
		event.addSubcommand("clearvisitors", ManagedConfig.Category.GARDEN) {
			onRun { DConfig.data?.wanted?.clear(); DConfig.markDirty(); visitorsChanged() }
		}
	}

	fun cropForTool(toolId: String): ItemId? {
		val crop = when {
			toolId.startsWith("THEORETICAL_HOE_WHEAT") -> "WHEAT"
			toolId.startsWith("THEORETICAL_HOE_CARROT") -> "CARROT_ITEM"
			toolId.startsWith("THEORETICAL_HOE_POTATO") -> "POTATO_ITEM"
			toolId.startsWith("THEORETICAL_HOE_CANE") -> "SUGAR_CANE"
			toolId.startsWith("THEORETICAL_HOE_WARTS") -> "NETHER_STALK"
			toolId.startsWith("PUMPKIN_DICER") -> "PUMPKIN"
			toolId.startsWith("MELON_DICER") -> "MELON"
			toolId.startsWith("CACTUS_KNIFE") -> "CACTUS"
			toolId.startsWith("COCO_CHOPPER") -> "INK_SACK:3"
			toolId.startsWith("FUNGI_CUTTER") -> "RED_MUSHROOM"
			else -> return null
		}
		return ItemId(crop)
	}

	fun coinsPerHour(cropsPerMinute: Double, pricePerCrop: Double) = cropsPerMinute * 60 * pricePerCrop

	fun coinsPerHourLine(cropsPerMinute: Double): Component? {
		if (!T.coinsPerHour) return null
		val tool = Client.player?.mainHandItem?.view()?.repoItemId?.skyblockId ?: return null
		val crop = cropForTool(tool) ?: return null
		val price = RepoConstants.price(crop) ?: return null
		return Component.literal("§eCoins/h: §6" + ApexFormatters.compactNumber(coinsPerHour(cropsPerMinute, price)))
	}

	private val NUMBER = Regex("([0-9.,]+)([kKmM]?)")

	fun parseShortNumber(text: String): Double? {
		val m = NUMBER.find(text) ?: return null
		val base = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
		return base * when (m.groupValues[2].lowercase()) { "k" -> 1e3; "m" -> 1e6; else -> 1.0 }
	}

	fun parseComposter(lines: List<String>): Pair<Double?, Double?> {
		val om = lines.firstOrNull { it.trim().startsWith("Organic Matter:") }?.substringAfter(':')?.let(::parseShortNumber)
		val fuel = lines.firstOrNull { it.trim().startsWith("Fuel:") }?.substringAfter(':')?.let(::parseShortNumber)
		return om to fuel
	}

	private var composterWarned = false
	private var jacobWarnedFor = 0L

	@Subscribe
	fun onTick(event: TickEvent) {
		if (event.tickCount % 40 != 0 || !SkyBlockSession.inSkyBlock) return
		if (T.composterLowAlert && SkyBlockSession.skyblockIsland == SkyBlockIsland.GARDEN) {
			val (om, fuel) = parseComposter(TabListAPI.getWidgetLines(TabListAPI.WidgetName.COMPOSTER).map { it.bareText })
			val limit = T.composterLowThousands * 1000.0
			val low = (om != null && om < limit) || (fuel != null && fuel < limit)
			if (low && !composterWarned) Alerts.show(Component.literal("§6§lComposter low on " + if (om != null && om < limit) "organic matter" else "fuel"))
			composterWarned = low
		}
		if (T.jacobWarningMinutes > 0) {
			val now = System.currentTimeMillis()
			val (start, _) = SkyBlockEventTimers.nextHourly(15, 20, now)
			val left = start - now
			if (left in 1..T.jacobWarningMinutes * 60_000L && jacobWarnedFor != start) {
				jacobWarnedFor = start
				Alerts.show(Component.literal("§e§lJacob's Contest in ${left / 60_000 + 1} min"))
			}
		}
	}

	fun shoppingListLines(data: Visitors, lookup: (String) -> ItemId?, price: (ItemId) -> Double?): List<Component> {
		val list = shoppingList(data)
		if (list.isEmpty()) return emptyList()
		var total = 0.0
		val lines = mutableListOf<Component>(Component.literal("§aVisitor shopping list §7(${data.wanted.size})"))
		for ((name, amount) in list) {
			val price = lookup(name)?.let(price)?.times(amount)
			price?.let { total += it }
			lines.add(Component.literal("§f${ApexFormatters.withThousands(amount.toLong())}x $name §6" + (price?.let { ApexFormatters.compactNumber(it) } ?: "?")))
		}
		lines.add(Component.literal("§eTotal: §6" + ApexFormatters.compactNumber(total)))
		return lines
	}

	class ShoppingListCache(
		private val lookup: (String) -> ItemId?,
		private val price: (ItemId) -> Double?,
		private val lookupGeneration: () -> Any?,
		private val bazaarRef: () -> Any?,
		private val lowestBinRef: () -> Any?,
	) {
		var rebuilds = 0
			private set
		private val memo = HashMap<String, ItemId?>()
		private var memoGeneration: Any? = null
		private var valid = false
		private var lines: List<Component> = emptyList()
		private var lastData: Visitors? = null
		private var lastVersion = 0
		private var lastHash = 0
		private var lastGeneration: Any? = null
		private var lastBazaar: Any? = null
		private var lastLowestBin: Any? = null

		fun invalidate() { valid = false }

		private fun memoLookup(name: String): ItemId? {
			if (memo.containsKey(name)) return memo[name]
			return lookup(name).also { memo[name] = it }
		}

		fun lines(data: Visitors, version: Int): List<Component> {
			val generation = lookupGeneration()
			val bazaar = bazaarRef()
			val lowestBin = lowestBinRef()
			val hash = data.wanted.hashCode()
			if (valid && data === lastData && version == lastVersion && hash == lastHash && generation === lastGeneration
				&& bazaar === lastBazaar && lowestBin === lastLowestBin) return lines
			if (generation !== memoGeneration) { memo.clear(); memoGeneration = generation }
			lines = shoppingListLines(data, ::memoLookup, price)
			lastData = data; lastVersion = version; lastHash = hash; lastGeneration = generation
			lastBazaar = bazaar; lastLowestBin = lowestBin
			valid = true
			rebuilds++
			return lines
		}
	}

	private var visitorsVersion = 0
	private fun visitorsChanged() { visitorsVersion++ }

	val shoppingListCache = ShoppingListCache(
		lookup = { ItemNameLookup.guessItemByName(it, false) },
		price = RepoConstants::price,
		lookupGeneration = { ItemNameLookup.generation },
		bazaarRef = { HypixelStaticData.bazaarData },
		lowestBinRef = { HypixelStaticData.lowestBin },
	)

	@Subscribe
	fun onHud(event: HudRenderEvent) {

		if (!T.visitorShoppingList || SkyBlockSession.skyblockIsland != SkyBlockIsland.GARDEN) { shoppingListCache.invalidate(); return }
		val data = DConfig.data ?: return
		val lines = shoppingListCache.lines(data, visitorsVersion)
		if (lines.isEmpty()) return
		event.drawHudLines(T.visitorListPosition, lines)
	}
}
