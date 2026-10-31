/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Hide Players (Render). Stops other real players from being drawn: body, armour, held items, cape,
 * cosmetics, nametag and shadow all go, because the entity is rejected at the render dispatcher's first check
 * (EntityRenderDispatcher.shouldRender, hook: dev.ashtonite365.apex.mixins.MiscRenderPatches via MiscRender.shouldHide).
 * Hypixel NPCs (UUID v2 or no tab-list entry), invisible players and the local player are never touched.
 * Render only: hit boxes, clicks, ray casts and collision are unchanged (a hidden player still blocks hits, as in
 * vanilla). Replaces the old Miscellaneous "Hide Far Players" (see [HidePlayersLogic.foldOldFarPlayers]).
 * Original implementation.
 */
package dev.ashtonite365.apex.features.render

import java.util.UUID
import kotlinx.serialization.json.JsonObject
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.commands.onLiteral
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldKeyboardEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.features.render.HidePlayersLogic.Action
import dev.ashtonite365.apex.features.render.HidePlayersLogic.DungeonMode
import dev.ashtonite365.apex.gui.config.ManagedOption
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.skyblock.PartyUtil

object HidePlayers {
	@Config
	object TConfig : ManagedConfig("hide-players", Category.RENDER) {
		var enabled by toggle("enabled") { false }
		val toggleKey by keyBindingWithDefaultUnbound("toggle-key")
		val keepParty by toggle("keep-party") { true }
		val whitelist by string("whitelist") { "" }
		val keepWithin by integer("keep-within", 0, HidePlayersLogic.MAX_KEEP_WITHIN) { 0 }
		val dungeonMode by choice("dungeon-mode") { DungeonMode.EXCEPT_PARTY }
		val screenshotKey by keyBindingWithDefaultUnbound("screenshot-key")
		val screenshotHideHud by toggle("screenshot-hide-hud") { true }
		val screenshotHideApexHud by toggle("screenshot-hide-apex-hud") { true }
		val screenshotHideSelf by toggle("screenshot-hide-self") { false }

		override fun migrateFromAll(saved: JsonObject, all: JsonObject): JsonObject = HidePlayersLogic.foldOldFarPlayers(saved, all)

		override fun onChange(option: ManagedOption<*>) {
			dirtySettings = true
		}
	}

	private val cache = HidePlayersLogic.DecisionCache()
	@Volatile private var dirtySettings = true
	private var settings = HidePlayersLogic.Settings()
	private var whitelist: Set<String> = emptySet()
	private var partyUuids: Set<UUID> = emptySet()
	private var partyNames: Set<String> = emptySet()
	private var partyVersion = -1
	private var island: SkyBlockIsland? = null
	private var inDungeonArea = false

	var screenshotMode = false
		private set
	private var hudHiddenByUs = false

	@JvmStatic
	@Volatile
	var hideApexHud = false
		private set

	private fun refreshSettings() {
		if (!dirtySettings) return
		dirtySettings = false
		val c = TConfig
		whitelist = HidePlayersLogic.parseWhitelist(c.whitelist)
		settings = HidePlayersLogic.Settings(c.enabled, c.keepParty, c.keepWithin, c.dungeonMode, screenshotMode, c.screenshotHideSelf)
		hideApexHud = screenshotMode && c.screenshotHideApexHud
		cache.invalidate()
	}

	private fun refreshParty() {
		val v = PartyUtil.version
		if (v == partyVersion) return
		partyVersion = v
		val members = PartyUtil.party?.members.orEmpty()
		partyUuids = members.mapNotNullTo(HashSet()) { it.uuid }
		partyNames = members.mapTo(HashSet()) { it.name.lowercase() }
		cache.invalidate()
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		refreshSettings()
		refreshParty()
		val now = SkyBlockSession.skyblockIsland
		if (now != island) {
			island = now
			inDungeonArea = now == SkyBlockIsland.DUNGEON || now == SkyBlockIsland.KUUDRA
		}

		cache.invalidate()
	}

	@JvmStatic
	fun shouldHide(entity: Player): Boolean {
		val s = settings
		if (!s.active) return false
		val me = Client.player ?: return false
		if (entity === me) return HidePlayersLogic.hideLocal(s, !Client.options.cameraType.isFirstPerson)
		if (entity.isInvisible) return false
		val flags = cache.flags(entity.uuid) { classify(entity) }
		if (flags == HidePlayersLogic.NEVER) return false
		val distanceSq = if (s.keepWithin > 0) entity.distanceToSqr(me) else 0.0
		return HidePlayersLogic.shouldHide(flags, distanceSq, s, inDungeonArea)
	}

	private fun classify(entity: Player): Int {
		val uuid = entity.uuid
		val inTab = Client.connection?.getPlayerInfo(uuid) != null
		return HidePlayersLogic.classify(uuid, false, inTab, { entity.gameProfile.name.lowercase() }, partyUuids, partyNames, whitelist)
	}

	fun setEnabled(value: Boolean) {
		if (TConfig.enabled != value) {
			TConfig.enabled = value
			TConfig.markDirty()
		}
		dirtySettings = true
		refreshSettings()
	}

	fun setScreenshot(on: Boolean) {
		if (screenshotMode == on) return
		screenshotMode = on
		val hud = Client.hud
		if (on) {
			hudHiddenByUs = TConfig.screenshotHideHud && !hud.isHidden
			if (hudHiddenByUs) hud.toggle()
		} else {
			if (hudHiddenByUs && hud.isHidden) hud.toggle()
			hudHiddenByUs = false
		}
		dirtySettings = true
		refreshSettings()
	}

	private fun toast(title: String, on: Boolean) {
		SystemToast.add(
			Client.gui.toastManager(),
			SystemToast.SystemToastId.NARRATOR_TOGGLE,
			Component.literal(title),
			Component.literal(if (on) "§aON" else "§cOFF"),
		)
	}

	@Subscribe
	fun onKey(event: WorldKeyboardEvent) {
		if (event.matches(TConfig.toggleKey)) {
			setEnabled(!TConfig.enabled)
			toast("Hide Players", TConfig.enabled)
		} else if (event.matches(TConfig.screenshotKey)) {
			setScreenshot(!screenshotMode)
			toast("Screenshot mode", screenshotMode)
		}
	}

	@Subscribe
	fun onWorldChange(event: WorldReadyEvent) {
		setScreenshot(false)
		cache.invalidate()
	}

	private fun run(action: Action): String = when (action) {
		Action.SCREENSHOT -> { setScreenshot(!screenshotMode); HidePlayersLogic.screenshotFeedback(screenshotMode) }
		else -> { setEnabled(HidePlayersLogic.applyAction(TConfig.enabled, action)); HidePlayersLogic.feedback(TConfig.enabled) }
	}

	@Subscribe
	fun onCommand(event: CommandEvent.SubCommand) {
		event.register(ManagedConfig.Category.RENDER, "hideplayers", "Hide other players: /apex hideplayers [on|off|toggle|screenshot]") {
			onRun { source.sendFeedback(Component.literal(run(Action.TOGGLE))) }
			for (word in HidePlayersLogic.COMMAND_WORDS) {
				val action = HidePlayersLogic.parseAction(word) ?: continue
				onLiteral(word) { onRun { source.sendFeedback(Component.literal(run(action))) } }
			}
		}
	}

}
