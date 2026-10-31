package dev.ashtonite365.apex.features.inventory

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.features.items.recipes.ItemListMods
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.events.SkyblockServerUpdateEvent
import dev.ashtonite365.apex.repo.RepoManager
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.bold
import dev.ashtonite365.apex.util.runsOnClick
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.grey
import dev.ashtonite365.apex.util.red
import dev.ashtonite365.apex.util.yellow

object ItemListModNotice {
	private val supportedPresent by lazy { ItemListMods.anyUsable() }
	private var shownThisSession = false

	fun isDue(onSkyblock: Boolean, wanted: Boolean, supportedModInstalled: Boolean, alreadyShown: Boolean) =
		onSkyblock && wanted && !supportedModInstalled && !alreadyShown

	fun message(): Component =
		Component.translatable("apex.reiwarning").red().bold()
			.append("\n").append(Component.translatable("apex.reiwarning.disable").runsOnClick("/apex disablereiwarning").grey())

	@Subscribe
	fun maybeWarn(event: SkyblockServerUpdateEvent) {
		if (!isDue(SkyBlockSession.inSkyBlock, RepoManager.TConfig.warnForMissingItemListMod, supportedPresent, shownThisSession)) return
		shownThisSession = true
		Apex.coroutineScope.launch {
			delay(2.seconds)
			Client.showChat(message())
		}
	}

	@Subscribe
	fun registerOptOut(event: CommandEvent.SubCommand) {
		if (supportedPresent) return
		event.addSubcommand("disablereiwarning", ManagedConfig.Category.INVENTORY) {
			onRun { optOut() }
		}
	}

	private fun optOut() {
		with(RepoManager.TConfig) {
			warnForMissingItemListMod = false
			markDirty()
		}
		Client.showChat(Component.translatable("apex.reiwarning.disabled").yellow())
	}
}
