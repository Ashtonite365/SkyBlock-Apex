/*
 * SPDX-FileCopyrightText: 2026 SkyBlock Apex contributors
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package dev.ashtonite365.apex.features.foraging

import org.joml.Vector2i
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.StringRepresentable
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.Shulker
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.events.EntityRenderTintEvent
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ItemTooltipEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.SlotRenderEvents
import dev.ashtonite365.apex.events.SoundReceiveEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.repo.HypixelStaticData
import dev.ashtonite365.apex.util.Alerts
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.TabListReader
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.render.TintOverlay
import dev.ashtonite365.apex.util.render.drawHudLines
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.tr
import dev.ashtonite365.apex.util.tracker.SessionTracker
import dev.ashtonite365.apex.util.bareText

object GalateaHelpers {
	enum class ShulkerColour(val id: String, val argb: Int) : StringRepresentable {
		OFF("off", 0), RED("red", 0xFFFF4040.toInt()), GREEN("green", 0xFF40FF40.toInt()),
		BLUE("blue", 0xFF4080FF.toInt()), YELLOW("yellow", 0xFFFFFF40.toInt()), WHITE("white", 0xFFFFFFFF.toInt());

		override fun getSerializedName() = id
	}

	@Config
	object TConfig : ManagedConfig("galatea", Category.FORAGING) {
		val hud by position("hud", 150, 60) { Vector2i(0, 220) }
		val treeProgress by toggle("tree-progress") { true }
		val sweepDisplay by toggle("sweep-display") { true }
		val lassoAlert by toggle("lasso-alert") { true }
		val shardTracker by toggle("shard-tracker") { true }
		val shardValue by toggle("shard-value") { true }
		val huntingBoxValue by toggle("hunting-box-value") { true }
		val hotfLevelAsAmount by toggle("hotf-level-as-amount") { true }
		val hotfTotalProgress by toggle("hotf-total-progress") { true }
		val mutePhantoms by toggle("mute-phantoms") { false }

		val compactSweepDetails by toggle("compact-sweep-details") { false }
		val compactStarlynContest by toggle("compact-starlyn-contest") { false }
		val shulkerColour by choice("shulker-colour") { ShulkerColour.OFF }
	}

	private fun onForagingIsland() = SkyBlockSession.skyblockIsland == SkyBlockIsland.GALATEA || SkyBlockSession.skyblockIsland == SkyBlockIsland.PARK

	val shards = SessionTracker(idleTimeout = 3.minutes, resetAfter = 15.minutes)
	private var tree: String? = null
	private var sweep: String? = null
	private var lassoReady = false

	private val TREE = Regex("^(.*?(?:Tree|Mangrove|Fig).*?)\\s+(\\d{1,3}(?:\\.\\d+)?)%$")
	private val CHARMED = Regex("^You charmed an? (.+?) and captured (\\d+|a) Shards? from it\\.$")
	private val CAUGHT = Regex("^(?:[A-Z ]+! )?You (?:caught|received|trapped|found) (?:x?(\\d+) |an? )?(.+?) Shards?(?: x(\\d+))?[.!]?$")
	private val LEVEL = Regex("Level (\\d+)(?:/(\\d+))?")

	fun shardId(name: String) = ItemId("SHARD_" + name.uppercase().replace(' ', '_').replace("'", ""))

	@Subscribe
	fun onTick(event: TickEvent) {
		if (event.tickCount % 5 != 0) return
		if (!onForagingIsland()) {
			tree = null; sweep = null; lassoReady = false; return
		}
		val player = Client.player ?: return
		val level = Client.level ?: return
		val names = level.getEntitiesOfClass(ArmorStand::class.java, player.boundingBox.inflate(12.0)) { it.customName != null }
			.sortedBy { it.distanceToSqr(player) }
			.map { it.customName!!.bareText.trim() }
		tree = if (TConfig.treeProgress) names.firstNotNullOfOrNull { TREE.matchEntire(it) }?.let { "${it.groupValues[1]}: ${it.groupValues[2]}%" } else null
		val reel = TConfig.lassoAlert && names.any { it == "REEL" || it.startsWith("REEL ") }
		if (reel && !lassoReady) Alerts.show(Component.literal("§6§lREEL!"), 1.seconds, sound = true)
		lassoReady = reel
		sweep = if (TConfig.sweepDisplay) TabListReader.currentSnapshot.body.map { it.bareText.trim() }
			.firstOrNull { it.startsWith("Sweep") } else null
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!TConfig.shardTracker) return
		val line = event.bareText.trim()
		val (name, count) = CHARMED.matchEntire(line)?.let { it.groupValues[1] to (it.groupValues[2].toIntOrNull() ?: 1) }
			?: CAUGHT.matchEntire(line)?.let { it.groupValues[2] to ((it.groupValues[1].ifEmpty { it.groupValues[3] }).toIntOrNull() ?: 1) }
			?: return
		shards.add("Shards", count.toDouble())
		shards.add(name, count.toDouble())
		if (TConfig.shardValue) HypixelStaticData.getPriceOfItem(shardId(name))?.let { shards.add("Coins", it * count) }
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!onForagingIsland() && shards.isEmpty) return
		val lines = mutableListOf<Component>()
		tree?.let { lines += Component.literal("§2$it") }
		sweep?.let { lines += Component.literal("§a$it") }
		if (lassoReady) lines += Component.literal("§6§lLasso: REEL")
		if (TConfig.shardTracker && !shards.isEmpty) {
			lines += Component.literal("§d§lShards §7(session)")
			lines += shards.lines()
		}
		if (lines.isNotEmpty()) event.drawHudLines(TConfig.hud, lines)
	}

	private fun inHotf() = Client.screenTitle == "Heart of the Forest"

	private fun perkLevel(stack: net.minecraft.world.item.ItemStack): Pair<Int, Int?>? =
		stack.view().loreAccordingToNbt.firstNotNullOfOrNull { LEVEL.find(it.bareText) }
			?.let { it.groupValues[1].toInt() to it.groupValues[2].toIntOrNull() }

	@Subscribe
	fun onSlot(event: SlotRenderEvents.After) {
		if (!TConfig.hotfLevelAsAmount || !inHotf()) return
		if (event.slot.container is net.minecraft.world.entity.player.Inventory) return
		val (lvl, max) = perkLevel(event.slot.item) ?: return
		val text = lvl.toString()
		val colour = if (max != null && lvl >= max) 0xFF55FF55.toInt() else 0xFFFFFFFF.toInt()
		event.context.text(Client.font, text, event.slot.x + 17 - Client.font.width(text), event.slot.y + 9, colour, true)
	}

	@Subscribe
	fun onTooltip(event: ItemTooltipEvent) {
		if (TConfig.hotfTotalProgress && inHotf()) {
			val (lvl, max) = perkLevel(event.stack) ?: return
			if (max != null && max > 0)
				event.lines.add(Component.literal("§7Progress: §a${lvl}§7/§a$max §8(${lvl * 100 / max}%)"))
		}
	}

	@Subscribe
	fun onHuntingBoxTooltip(event: ItemTooltipEvent) {
		if (!TConfig.huntingBoxValue || Client.screenTitle?.contains("Hunting Box") != true) return
		val id = event.stack.view().repoItemId ?: return
		if (!id.skyblockId.startsWith("SHARD_")) return
		val stored = event.stack.view().loreAccordingToNbt.firstNotNullOfOrNull {
			Regex("(?:Owned|Stored|Amount): ([\\d,]+)").find(it.bareText)
		}?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull() ?: event.stack.count.toLong()
		val price = HypixelStaticData.getPriceOfItem(id) ?: return
		event.lines.add(Component.literal("§7Value (x$stored): §6${ApexFormatters.compactNumber(price * stored)} coins"))
	}

	@Subscribe
	fun onSound(event: SoundReceiveEvent) {
		if (!TConfig.mutePhantoms || SkyBlockSession.skyblockIsland != SkyBlockIsland.GALATEA) return
		val id = event.sound.value().location().path
		if (id.startsWith("entity.phantom")) event.cancel()
	}

	@Subscribe
	fun onTint(event: EntityRenderTintEvent) {
		val c = TConfig.shulkerColour
		if (c == ShulkerColour.OFF || event.entity !is Shulker || SkyBlockSession.skyblockIsland != SkyBlockIsland.GALATEA) return
		event.renderState.overlayTexture_apex = overlays.getOrPut(c) { TintOverlay().tinted(c.argb) }
	}

	private val overlays = mutableMapOf<ShulkerColour, TintOverlay>()

	@Subscribe
	fun onCommand(event: CommandEvent.SubCommand) {
		event.register(ManagedConfig.Category.FORAGING, "resetshards", "Reset the session shard tracker") {
			onRun {
				shards.reset()
				source.sendFeedback(tr("apex.galatea.reset-shards", "Reset the shard tracker."))
			}
		}
	}
}
