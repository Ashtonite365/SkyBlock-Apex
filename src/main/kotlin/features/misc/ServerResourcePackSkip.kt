/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex (26.3 port): optionally skip Hypixel's server resource pack. Original implementation.
 */
package dev.ashtonite365.apex.features.misc

import net.minecraft.client.resources.model.ModelManager
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.PackResources
import net.minecraft.server.packs.repository.Pack
import net.minecraft.server.packs.repository.PackSource
import net.minecraft.world.item.ItemStack
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.features.misc.oldlook.ItemsOnlyPackResources
import dev.ashtonite365.apex.features.misc.oldlook.OldLookPolicy
import dev.ashtonite365.apex.mixins.accessor.AccessorModelManager
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig

object ServerResourcePackSkip {
	@Config
	object TConfig : ManagedConfig("hypixel-resource-pack", Category.GENERAL) {
		val skipPack by riskyToggle("skip-pack", dev.ashtonite365.apex.gui.config.RiskLevel.RISKY)

		val classicLooks by toggle("classic-item-looks") { true }

		val keepNewerItemTextures by toggle("keep-new-item-textures") { true }
	}

	val packMode get() = OldLookPolicy.packMode(TConfig.skipPack, TConfig.keepNewerItemTextures)

	@JvmStatic
	fun itemsOnlyView(pack: Pack, opened: java.util.stream.Stream<PackResources>): java.util.stream.Stream<PackResources> {
		if (pack.location().source() != PackSource.SERVER || !packMode.itemsOnly || !onHypixel()) return opened
		if (!loggedItemsOnly) {
			loggedItemsOnly = true
			Apex.logger.info("Skip Hypixel Resource Pack: using only the item models and item textures of Hypixel's pack")
		}
		return opened.map { if (it is ItemsOnlyPackResources) it else ItemsOnlyPackResources(it) }
	}

	private var loggedItemsOnly = false

	fun isHypixel(url: String, serverIp: String?): Boolean =
		url.contains("hypixel", ignoreCase = true) || serverIp?.contains("hypixel", ignoreCase = true) == true

	private fun onHypixel() = Client.game.currentServer?.ip?.contains("hypixel", ignoreCase = true) == true

	val active get() = TConfig.skipPack && onHypixel()

	@JvmStatic
	fun shouldSkip(url: String): Boolean {

		if (packMode.wanted || !isHypixel(url, Client.game.currentServer?.ip)) return false
		clearAppliedServerPacks()
		return true
	}

	fun hasActiveServerPack(): Boolean =
		Client.game.resourcePackRepository.selectedPacks.any { it.location().source() == PackSource.SERVER }

	fun clearAppliedServerPacks(): Boolean {
		if (!hasActiveServerPack()) return false
		Apex.logger.info("Skip Hypixel Resource Pack: removing the applied server pack and reloading resources")
		Client.game.downloadedPackSource.popAll()
		return true
	}

	data class SkippedPush(val id: java.util.UUID, val url: String, val hash: String?)

	@Volatile
	var lastSkipped: SkippedPush? = null
		private set

	@JvmStatic
	fun rememberSkipped(id: java.util.UUID, url: String, hash: String?) {
		lastSkipped = SkippedPush(id, url, hash)
	}

	fun reapplySkipped(): Boolean {
		val push = lastSkipped ?: return false
		if (!onHypixel()) return false
		val url = try { java.net.URI(push.url).toURL() } catch (e: Exception) {
			Apex.logger.warn("Skip Hypixel Resource Pack: cannot re-apply, bad server URL", e); return false
		}
		Apex.logger.info("Skip Hypixel Resource Pack: option turned off, applying the server's pack")
		Client.game.downloadedPackSource.pushPack(push.id, url, push.hash)
		lastSkipped = null
		return true
	}

	private var lastMode: OldLookPolicy.PackMode? = null

	@Subscribe
	fun onTick(event: TickEvent) {
		val now = packMode
		val prev = lastMode
		lastMode = now

		if (prev != null && prev != now) when (OldLookPolicy.onModeChange(prev, now, onHypixel(), hasActiveServerPack())) {
			OldLookPolicy.ModeAction.NONE -> {}
			OldLookPolicy.ModeAction.POP_SERVER_PACK -> clearAppliedServerPacks()
			OldLookPolicy.ModeAction.RELOAD -> Client.game.reloadResourcePacks()
			OldLookPolicy.ModeAction.REAPPLY_SERVER_PACK -> if (!reapplySkipped()) Client.game.reloadResourcePacks()
		}
		if (Client.game.currentServer == null) lastSkipped = null
	}

	fun shouldFallBack(model: Identifier?, knownModels: Set<Identifier>): Boolean =
		model != null && model.namespace != "minecraft" && model !in knownModels

	fun resolveItemModel(original: Identifier, itemKey: Identifier, knownModels: Set<Identifier>): Identifier =
		if (shouldFallBack(original, knownModels)) itemKey else original

	@JvmStatic
	fun fallbackItemModel(original: Any?, stack: ItemStack, modelManager: ModelManager): Any? {
		val id = original as? Identifier ?: return original
		val known = (modelManager as AccessorModelManager).bakedItemStackModels_Apex
		return resolveItemModel(id, BuiltInRegistries.ITEM.getKey(stack.item), known.keys)
	}
}
