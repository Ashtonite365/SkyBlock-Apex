/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex texture engine: items, skulls, armour, blocks, screens and texts.
 */
package dev.ashtonite365.apex.texengine.apex

import dev.ashtonite365.apex.util.trail.Crumb
import dev.ashtonite365.apex.util.trail.Trail
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.FinalizeResourceManagerEvent
import dev.ashtonite365.apex.events.ScreenChangeEvent
import dev.ashtonite365.apex.events.SkyblockServerUpdateEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.texengine.ApexTextureEngines
import dev.ashtonite365.apex.texengine.TextureEngine
import net.minecraft.server.packs.resources.PreparableReloadListener
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimplePreparableReloadListener
import net.minecraft.util.Util
import net.minecraft.util.profiling.ProfilerFiller
import java.util.concurrent.Executor

internal fun reloadSetupExecutor(): Executor = Util.backgroundExecutor().forName("resourceLoad")

object ApexEngine : TextureEngine {
	override val id = "apex"
	override val items = ApexItems
	override val skulls = ApexSkulls
	override val armor = ApexArmor
	override val blocks = ApexBlocks
	override val screens = ApexScreens
	override val texts = ApexTexts
}

object ApexEngineEvents {
	@Subscribe
	fun onFinalize(event: FinalizeResourceManagerEvent) {
		Trail.crumb(Crumb.TEXTURE_ENGINE, ApexTextureEngines.active.id)
		val manager = event.resourceManager
		manager.registerReloadListener(ReloadCrumb)
		ItemModelTypes.register()
		manager.registerReloadListener(ScreenItemOverrides)
		manager.registerReloadListener(ApexArmor.Listener)
		manager.registerReloadListener(ApexScreens.Listener)
		manager.registerReloadListener(ApexTextColors.Listener)
		manager.registerReloadListener(ApexTexts.Listener)
		manager.registerReloadListener(ApexBlocks.Listener)
		manager.registerReloadListener(CacheClearer)
	}

	@Subscribe
	fun onLocation(event: SkyblockServerUpdateEvent) {
		ApexBlocks.useIsland(event.currentLocation?.skyblockIsland?.serverMode)
	}

	@Subscribe
	fun onScreenChange(event: ScreenChangeEvent) {
		ApexScreens.screenChanged(event.new)
		ScreenItemOverrides.screenChanged(event.new?.title)
		ApexItems.clearCache()
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		val s = settings
		if (s.cacheForever) return
		if (s.cacheDuration < 1 || event.tickCount % s.cacheDuration == 0) ApexItems.clearCache()
	}

	private object ReloadCrumb : SimplePreparableReloadListener<Unit>() {
		override fun prepareSharedState(state: PreparableReloadListener.SharedState) {
			Trail.crumb(Crumb.TEXTURE_RELOAD_START)
		}

		override fun prepare(manager: ResourceManager, profiler: ProfilerFiller) = Unit
		override fun apply(prepared: Unit, manager: ResourceManager, profiler: ProfilerFiller) {
			Trail.crumb(Crumb.TEXTURE_RELOAD_END)
		}
	}

	private object CacheClearer : SimplePreparableReloadListener<Unit>() {
		override fun prepare(manager: ResourceManager, profiler: ProfilerFiller) = Unit
		override fun apply(prepared: Unit, manager: ResourceManager, profiler: ProfilerFiller) {
			ApexItems.clearCache()
			ApexSkulls.clearCache()
		}
	}
}
