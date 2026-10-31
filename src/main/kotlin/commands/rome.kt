// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType.string
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import kotlinx.coroutines.launch
import net.minecraft.commands.CommandBuildContext
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.features.debug.DebugLogger
import dev.ashtonite365.apex.features.debug.DeveloperFeatures
import dev.ashtonite365.apex.features.inventory.buttons.InventoryButtons
import dev.ashtonite365.apex.features.inventory.storageoverlay.StorageOverlay
import dev.ashtonite365.apex.features.inventory.storageoverlay.StorageOverlayScreen
import dev.ashtonite365.apex.features.inventory.storageoverlay.StorageOverviewScreen
import dev.ashtonite365.apex.features.inventory.storageoverlay.StoragePageSlot
import dev.ashtonite365.apex.gui.config.AllConfigsGui
import dev.ashtonite365.apex.gui.config.BooleanHandler
import dev.ashtonite365.apex.gui.config.ManagedOption
import dev.ashtonite365.apex.repo.HypixelStaticData
import dev.ashtonite365.apex.repo.ItemCache
import dev.ashtonite365.apex.repo.RepoDownloadManager
import dev.ashtonite365.apex.repo.RepoManager
import dev.ashtonite365.apex.repo.itemsSafe
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.ApexFormatters.debugPath
import dev.ashtonite365.apex.util.ApexFormatters.boolText
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.ScreenUtil
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.bazaarProduct
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.tr

private typealias Branch = LiteralArgumentBuilder<DefaultSource>

private fun DefaultSource.say(lines: List<Component>): Unit = lines.forEach { sendFeedback(it) }

private fun managedConfigNamed(name: String): ManagedConfig? = ManagedConfig.everyHolder.getAll().find { it.name == name }

@Suppress("UNCHECKED_CAST")
private fun flipToggle(configName: String, propertyName: String): Component {
	val config = managedConfigNamed(configName)
		?: return Component.translatableEscape("apex.command.toggle.no-config-found", configName)
	val option = config.allOptions[propertyName]
		?: return Component.translatableEscape("apex.command.toggle.no-property-found", propertyName)
	if (option.handler !is BooleanHandler) return Component.translatableEscape("apex.command.toggle.not-a-toggle", propertyName)
	val toggle = option as ManagedOption<Boolean>
	toggle.value = toggle.value.not()
	config.markDirty()
	val state = Component.translatable("apex.toggle.${toggle.value}")
	return Component.translatableEscape("apex.command.toggle.toggled", config.labelText, toggle.labelText, state)
}

private fun Branch.configBranch(): Branch = onLiteral("config") {
	onRun { AllConfigsGui.showAllGuis() }
	onLiteral("toggle") {
		onArgument("config", string()) { configArg ->
			suggestFrom { ManagedConfig.everyHolder.getAll().map { it.name } }
			onArgument("property", string()) { propertyArg ->
				suggestFrom {
					val config = managedConfigNamed(this[configArg]) ?: return@suggestFrom emptyList()
					config.allOptions.filterValues { it.handler is BooleanHandler }.keys
				}
				onRun { source.sendFeedback(flipToggle(this[configArg], this[propertyArg])) }
			}
		}
	}
}

private fun sendCoordinates(suffix: String?) {
	val player = Client.player ?: return
	val coordinates = "x: ${player.blockX}, y: ${player.blockY}, z: ${player.blockZ}"
	Client.chatAsPlayer(if (suffix == null) coordinates else "$coordinates $suffix")
}

private fun openStorage(screen: net.minecraft.client.gui.screens.Screen) {
	ScreenUtil.setScreenLater(screen)
	Client.player?.connection?.sendCommand("storage")
}

private fun Branch.screenBranches(): Branch {
	onLiteral("buttons") { onRun { InventoryButtons.openEditor() } }
	onLiteral("sendcoords") {
		onRun { sendCoordinates(null) }
		onArgument("rest", RestArgumentType) { rest -> onRun { sendCoordinates(this[rest]) } }
	}
	onLiteral("storageoverview") { onRun { openStorage(StorageOverviewScreen()) } }
	onLiteral("storage") { onRun { openStorage(StorageOverlayScreen()) } }
	return this
}

private fun renameStorage(source: DefaultSource, slot: StoragePageSlot, newName: String?) {
	val names = StorageOverlay.Data.data.pageLabels
	if (newName == null) names.remove(slot) else names[slot] = newName
	StorageOverlay.Data.markDirty()
	val message = if (newName == null) tr("apex.command.storagename.reset", "Reset the name of ${slot.defaultName()}.")
	else tr("apex.command.storagename.set", "Renamed ${slot.defaultName()} to \"$newName\".")
	source.sendFeedback(message)
}

private fun Branch.storagePages(kind: String, pages: Int, slotFor: (Int) -> StoragePageSlot): Branch = onLiteral(kind) {
	onArgument("page", IntegerArgumentType.integer(1, pages)) { page ->
		onRun { renameStorage(source, slotFor(this[page]), null) }
		onArgument("name", RestArgumentType) { name -> onRun { renameStorage(source, slotFor(this[page]), this[name]) } }
	}
}

private fun Branch.storageNameBranch(): Branch = onLiteral("storagename") {
	storagePages("enderchest", 9) { StoragePageSlot.ofEnderChestPage(it) }
	storagePages("backpack", 18) { StoragePageSlot.ofBackPackPage(it) }
}

private fun loadPullRequest(source: DefaultSource, number: Int) {
	source.sendFeedback(tr("apex.repo.reload.pr", "Temporarily reloading repo from PR #${number}."))
	RepoManager.downloadOverridenBranch("refs/pull/$number/head")
}

private fun reloadRepo(source: DefaultSource, fromNetwork: Boolean) {
	source.sendFeedback(if (fromNetwork) Component.translatable("apex.repo.reload.network") else Component.translatable("apex.repo.reload.disk"))
	if (fromNetwork) RepoManager.launchAsyncUpdate() else Apex.coroutineScope.launch { RepoManager.reload()  }
}

private fun Branch.repoBranch(): Branch = onLiteral("repo") {
	onLiteral("checkpr") {
		onArgument("prnum", IntegerArgumentType.integer(1)) { pr -> onRun { loadPullRequest(source, this[pr]) } }
	}
	onLiteral("reload") {
		onLiteral("fetch") { onRun { reloadRepo(source, fromNetwork = true) } }
		onRun { reloadRepo(source, fromNetwork = false) }
	}
}

private fun priceReport(id: ItemId): List<Component> = buildList {
	add(Component.translatableEscape("apex.price", id.skyblockId))
	HypixelStaticData.bazaarData[id.bazaarProduct]?.let { product ->
		val quick = product.quickStatus
		add(Component.translatable("apex.price.bazaar"))
		add(Component.translatableEscape("apex.price.bazaar.productid", product.productId.stockKey))
		add(Component.translatableEscape("apex.price.bazaar.buy.price", ApexFormatters.withThousands(quick.buyPrice, 1)))
		add(Component.translatableEscape("apex.price.bazaar.buy.order", quick.buyOrders))
		add(Component.translatableEscape("apex.price.bazaar.sell.price", ApexFormatters.withThousands(quick.sellPrice, 1)))
		add(Component.translatableEscape("apex.price.bazaar.sell.order", quick.sellOrders))
	}
	HypixelStaticData.lowestBin[id]?.let { bin -> add(Component.translatableEscape("apex.price.lowestbin", ApexFormatters.withThousands(bin, 1))) }
}

private fun Branch.priceBranch(): Branch = onLiteral("price") {
	onArgument("item", string()) { itemArg ->
		suggestFrom { RepoManager.coreRepo.items.itemsSafe.keys }
		onRun { source.say(priceReport(ItemId(this[itemArg]))) }
	}
}

private fun knownDebugTags(): List<String> = DebugLogger.allInstances.getAll().map { logger -> logger.tag } + DebugLogger.EnabledLogs.data

private fun flipDebugLog(tag: String): Component {
	val enabledTags = DebugLogger.EnabledLogs.data
	val nowOn = enabledTags.remove(tag).not()
	if (nowOn) enabledTags.add(tag)
	return if (nowOn) Component.literal("Enabled $tag debug logging") else Component.literal("Disabled $tag debug logging")
}

private fun repoStatus(): List<Component> = buildList {
	for ((index, src) in RepoManager.configuredSources().withIndex()) {
		val number = index + 1
		val disabledNote = if (src.enabled) "" else " (disabled)"
		val version = RepoDownloadManager.savedVersionFor(src) ?: "not downloaded"
		val last = RepoDownloadManager.lastResult[src.ref] ?: "none this session"
		val cache = debugPath(RepoDownloadManager.folderFor(src))
		add(tr("apex.repo.info.source", "#$number ${src.ref}$disabledNote"))
		add(tr("apex.repo.info.source.version", "  Version: $version"))
		add(tr("apex.repo.info.source.last", "  Last update: $last"))
		add(tr("apex.repo.info.source.dir", "  Cache: $cache"))
	}
	val core = RepoManager.coreRepo
	val merged = RepoDownloadManager.repoSavedLocation
	val incomplete = boolText(core.isIncomplete, trueIsGood = false)
	val unstable = boolText(core.isUnstable, trueIsGood = false)
	val itemCount = core.items?.items?.size
	val overlayCount = RepoManager.overlayData.overlays.size
	val flawless = boolText(ItemCache.isFlawless)
	val itemDir = debugPath(merged.resolve("items"))
	add(tr("apex.repo.info.location", "Merged location: ${debugPath(merged)}"))
	add(tr("apex.repo.info.reloadstatus", "Incomplete: $incomplete, Unstable $unstable"))
	add(tr("apex.repo.info.items", "Loaded items: $itemCount"))
	add(tr("apex.repo.info.overlays", "Overlays: $overlayCount"))
	add(tr("apex.repo.info.itemcache", "ItemCache flawless: $flawless"))
	add(tr("apex.repo.info.itemdir", "Items on disk: $itemDir"))
}

private fun Branch.developerBranch(): Branch = onLiteral(DeveloperFeatures.DEVELOPER_SUBCOMMAND) {
	onLiteral("debuglog") {
		onLiteral("toggle") {
			onArgument("tag", string()) { tagArg ->
				suggestFrom { knownDebugTags() }
				onRun { source.sendFeedback(flipDebugLog(this[tagArg])) }
			}
		}
	}
	onLiteral("repo") { onRun { source.say(repoStatus()) } }
}

fun apexCommand(ctx: CommandBuildContext): AnyCaseLiteral.Builder<DefaultSource> {
	val root = AnyCaseLiteral.Builder<DefaultSource>(ApexCommands.ROOT)
	root.configBranch()
		.screenBranches()
		.storageNameBranch()
		.repoBranch()
		.priceBranch()
		.developerBranch()
		.onRun { AllConfigsGui.showAllGuis() }
	CommandEvent.SubCommand.publish(CommandEvent.SubCommand(root, ctx))
	ApexCommands.finish(root)
	return root
}

fun registerApexCommand(
	dispatcher: CommandDispatcher<DefaultSource>,
	ctx: CommandBuildContext,
) {

	ApexCommands.entries.clear()
	dispatcher.register(apexCommand(ctx))
	for ((alias, target) in ApexCommands.ALIASES) ApexCommands.registerAlias(dispatcher, alias, target)
}
