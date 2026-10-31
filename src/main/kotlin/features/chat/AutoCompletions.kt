package dev.ashtonite365.apex.features.chat

import com.mojang.brigadier.arguments.StringArgumentType.string
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.get
import dev.ashtonite365.apex.commands.suggestFrom
import dev.ashtonite365.apex.commands.onArgument
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.events.MaskCommands
import dev.ashtonite365.apex.repo.RepoManager
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.tr

object AutoCompletions {

	@Config
	object TConfig : ManagedConfig(category = Category.CHAT, name = identifier) {
		val provideWarpTabCompletion by toggle("warp-complete", default = { true })
		val replaceWarpIsByWarpIsland by toggle("warp-is", default = { true })

		val acceptLastInvite by toggle("accept-last-invite") { false }
		val shortenWarp by toggle("shorten-warp") { false }
		val transferCooldown by riskyToggle("transfer-cooldown", dev.ashtonite365.apex.gui.config.RiskLevel.GREY_AREA)
		val transferCooldownMessage by toggle("transfer-cooldown-message") { true }
		val viewRecipeLowercase by toggle("view-recipe-lowercase") { false }
		val openLastStorage by toggle("open-last-storage") { false }
		val preventEarlyCommands by riskyToggle("prevent-early-commands", dev.ashtonite365.apex.gui.config.RiskLevel.GREY_AREA)
	}

	val identifier: String get() = "auto-completions"

	fun migrateWikiKey(data: com.google.gson.JsonObject) {
		val holder = data.getAsJsonObject(identifier) ?: return
		val k = holder.get("wiki-key")?.takeIf { it.isJsonObject }?.asJsonObject ?: return
		val button = k.get("button")
		val mods = k.getAsJsonObject("modifiers")?.get("modifiers")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
		if ((button == null || button.isJsonNull) && mods == 0)
			k.add("button", com.google.gson.JsonObject().apply { addProperty("keyCode", com.mojang.blaze3d.platform.InputConstants.KEY_J) })
	}

	@dev.ashtonite365.apex.annotations.Subscribe
	fun onConfigFix(event: dev.ashtonite365.apex.gui.config.storage.ConfigFixEvent) {
		event.on(1002, dev.ashtonite365.apex.gui.config.storage.ConfigStorageClass.CONFIG) { migrateWikiKey(event.data) }
	}

	@Subscribe
	fun onMaskCommands(event: MaskCommands) = if (TConfig.provideWarpTabCompletion) event.mask("warp") else Unit

	private fun knownWarps(): List<String> {
		val warps = RepoManager.coreRepo.constants?.islands?.warps ?: return emptyList()
		return warps.flatMap { warp -> buildList { add(warp.warp); addAll(warp.aliases) } }
	}

	private fun CommandContext<*>.warpTo(destination: String) {
		val shorthandIsland = TConfig.replaceWarpIsByWarpIsland && destination == "is"
		if (!shorthandIsland) redirectToServer()
		Client.runCommand("warp island")
	}

	@Subscribe
	fun onCommandEvent(event: CommandEvent.SubCommand) {
		if (TConfig.provideWarpTabCompletion) event.register(ManagedConfig.Category.MISC, "warp", "Warp with tab-completion of every warp name") {
			onArgument("to", string()) { destinationArg ->
				suggestFrom { knownWarps() }
				onRun { warpTo(get(destinationArg)) }
			}
		}
	}

	fun CommandContext<*>.redirectToServer(): Nothing = throw CommandSyntaxException(
		CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand(),
		tr("apex.warp.auto-complete.internal-throw", "This is an internal syntax exception that should not show up in gameplay, used to pass on a command to the server"),
	)
}
