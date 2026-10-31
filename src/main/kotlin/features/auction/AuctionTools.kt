// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.auction

import dev.ashtonite365.apex.repo.itemsSafe
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket
import dev.ashtonite365.apex.annotations.Subscribe
import com.mojang.brigadier.arguments.StringArgumentType
import dev.ashtonite365.apex.commands.RestArgumentType
import dev.ashtonite365.apex.commands.get
import dev.ashtonite365.apex.commands.suggestFrom
import dev.ashtonite365.apex.commands.onArgument
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.events.ScreenChangeEvent
import dev.ashtonite365.apex.repo.RepoManager
import dev.ashtonite365.apex.repo.SBItemStack
import dev.ashtonite365.apex.util.ScreenUtil
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.mixins.accessor.AccessorSignEditScreen
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.DataHolder
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.bareText

object AuctionTools {
	val identifier: String get() = "auction-tools"

	@Config
	object TConfig : ManagedConfig(category = Category.AUCTION_BAZAAR, name = identifier) {
		val ahSearchOverlay by toggle("ah-search-overlay") { true }
		val bzSearchOverlay by toggle("bz-search-overlay") { true }
		val searchHistorySize by integer("search-history-size", 0, 20) { 5 }
		val keepPreviousSearch by toggle("keep-previous-search") { false }
		val searchCommands by toggle("search-commands") { true }
		val binWarning by toggle("bin-warning") { true }
		val undercutThreshold by integer("undercut-threshold", 0, 100) { 10 }
		val overcutWarning by toggle("overcut-warning") { true }
		val overcutThreshold by integer("overcut-threshold", 0, 1000) { 50 }
		val buyGuard by toggle("buy-guard") { true }
		val buyGuardThreshold by integer("buy-guard-threshold", 0, 1000) { 50 }
		val priceGraphKeybind by keyBindingWithDefaultUnbound("price-graph-keybind")
		val recordPriceHistory by toggle("record-price-history") { true }
		val priceHistoryRetention by integer("price-history-retention", 1, 60) { 7 }
	}

	@Serializable
	data class SearchHistory(
		val auction: MutableList<String> = mutableListOf(),
		val bazaar: MutableList<String> = mutableListOf(),
	)

	@Config
	object DConfig : DataHolder<SearchHistory>(serializer(), "auction-search-history", ::SearchHistory)

	enum class SearchKind { AUCTION, BAZAAR }

	var lastContainerTitle: String = ""
		private set

	private const val RULE = "^^^^^^^^^^^^^^^"
	private const val PROMPT = "Enter query"

	fun searchKind(lines: List<String>, containerTitle: String): SearchKind? {
		if (lines.getOrNull(2) != RULE || lines.getOrNull(3) != PROMPT) return null
		return when {
			containerTitle == "Auctions Browser" || containerTitle.startsWith("Auctions: ") -> SearchKind.AUCTION
			containerTitle.startsWith("Bazaar") -> SearchKind.BAZAAR
			else -> null
		}
	}

	fun detectSearchSign(screen: AbstractSignEditScreen, containerTitle: String): SearchKind? {
		val accessor = screen as AccessorSignEditScreen
		val sign = accessor.sign_Apex ?: return null
		return searchKind(sign.getText(accessor.slot_Apex).getMessages(false).map { it.bareText.trim() }, containerTitle)
	}

	@Subscribe
	fun onScreenChange(event: ScreenChangeEvent) {
		val old = event.old
		if (old is AbstractContainerScreen<*>) lastContainerTitle = old.title.bareText.trim()
		val new = event.new as? AbstractSignEditScreen ?: return
		if (!SkyBlockSession.inSkyBlock) return
		val kind = detectSearchSign(new, lastContainerTitle) ?: return
		when (kind) {
			SearchKind.AUCTION -> if (!TConfig.ahSearchOverlay) return
			SearchKind.BAZAAR -> if (!TConfig.bzSearchOverlay) return
		}
		val accessor = new as AccessorSignEditScreen
		event.overrideScreen = SignSearchScreen(kind, accessor.sign_Apex.blockPos, accessor.slot_Apex)
	}

	fun history(kind: SearchKind) = when (kind) {
		SearchKind.AUCTION -> DConfig.data.auction
		SearchKind.BAZAAR -> DConfig.data.bazaar
	}

	fun pushSearch(list: MutableList<String>, search: String, max: Int): Boolean {
		val entry = search.trim().ifEmpty { return false }
		list.remove(entry)
		list.add(0, entry)
		if (list.size > max) list.subList(maxOf(max, 0), list.size).clear()
		return true
	}

	fun rememberSearch(kind: SearchKind, search: String) {
		if (pushSearch(history(kind), search, TConfig.searchHistorySize)) DConfig.markDirty()
	}

	fun splitIntoSignLines(search: String): List<String> {
		if (search.length <= SIGN_WIDTH) return listOf(search, "", "", "")
		val words = ArrayDeque(search.split(' ').filter { it.isNotEmpty() })
		fun pack(): String {
			val line = mutableListOf<String>()
			var used = 0
			while (words.isNotEmpty() && used + words.first().length <= SIGN_WIDTH) {
				used += words.first().length + 1
				line += words.removeFirst()
			}
			return line.joinToString(" ")
		}
		val first = pack()
		val second = pack()
		return listOf(first, second, "", "")
	}

	private const val SIGN_WIDTH = 15

	fun sendSearchCommand(kind: SearchKind, search: String) {
		if (search.isEmpty()) return

		when (kind) {
			SearchKind.AUCTION -> Client.runCommandUnchecked("ahs $search")
			SearchKind.BAZAAR -> Client.runCommandUnchecked("bz $search")
		}
	}

	@Subscribe
	fun onApexCommands(event: CommandEvent.SubCommand) {
		for ((name, kind) in listOf("ahs" to SearchKind.AUCTION, "bzs" to SearchKind.BAZAAR)) {
			event.register(dev.ashtonite365.apex.util.data.ManagedConfig.Category.AUCTION_BAZAAR, name, "Open the {name} search overlay, or search directly".replace("{name}", if (kind == SearchKind.AUCTION) "auction" else "bazaar")) {
				onArgument("search", RestArgumentType) { search ->
					onRun { sendSearchCommand(kind, this[search].trim()) }
				}
				onRun { ScreenUtil.setScreenLater(SignSearchScreen(kind, null, null)) }
			}
		}
	}

	@Subscribe
	fun onCommands(event: CommandEvent) {
		if (!TConfig.searchCommands) return
		for ((name, kind) in listOf("ahs" to SearchKind.AUCTION, "bzs" to SearchKind.BAZAAR)) {
			event.register(name) {
				onArgument("search", RestArgumentType) { search ->
					onRun { sendSearchCommand(kind, this[search].trim()) }
				}
				onRun { ScreenUtil.setScreenLater(SignSearchScreen(kind, null, null)) }
			}
		}
	}

	@OptIn(dev.ashtonite365.apex.repo.ExpensiveItemCacheApi::class, dev.ashtonite365.apex.util.mc.NeedsComponents::class)
	@Subscribe
	fun onSubCommand(event: CommandEvent.SubCommand) {
		event.addSubcommand("pricegraph", dev.ashtonite365.apex.util.data.ManagedConfig.Category.AUCTION_BAZAAR) {
			onArgument("item", StringArgumentType.string()) { item ->
				suggestFrom { RepoManager.coreRepo.items.itemsSafe.keys }
				onRun {
					val id = ItemId(this[item])
					val stack = SBItemStack(id).asImmutableItemStack().rebuildLive()
					ScreenUtil.setScreenLater(PriceGraphScreen(null, id, stack))
				}
			}
		}
	}

	fun sendSign(pos: net.minecraft.core.BlockPos, slot: net.minecraft.world.level.block.entity.SignTextSlot, search: String) {
		Client.connection?.send(ServerboundSignUpdatePacket(pos, splitIntoSignLines(search), slot))
	}
}
