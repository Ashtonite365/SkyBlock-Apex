// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.kit.kuudra

import dev.ashtonite365.apex.util.render.WaypointType
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.Giant
import net.minecraft.world.inventory.Slot
import net.minecraft.world.phys.Vec3
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.IsSlotProtectedEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.ScreenChangeEvent
import dev.ashtonite365.apex.events.SlotRenderEvents
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.features.kuudra.KuudraInfo
import dev.ashtonite365.apex.features.kuudra.KuudraProfit
import dev.ashtonite365.apex.features.kuudra.KuudraSupplies
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.render.drawHudLines
import dev.ashtonite365.apex.util.bareText

object KuudraKitLogic {

	val dropSpots: Map<String, BlockPos> = linkedMapOf(
		"Triangle" to BlockPos(-94, 78, -106),
		"X" to BlockPos(-106, 78, -112),
		"Equals" to BlockPos(-98, 78, -99),
		"Slash" to BlockPos(-106, 78, -99),
		"Shop" to BlockPos(-98, 78, -112),
		"xCannon" to BlockPos(-110, 78, -106),
	)

	val pickupSpots: Map<String, BlockPos> = linkedMapOf(
		"Triangle" to BlockPos(-67, 77, -122),
		"X" to BlockPos(-142, 77, -151),
		"Equals" to BlockPos(-65, 76, -87),
		"Slash" to BlockPos(-113, 77, -68),
		"Shop" to BlockPos(-81, 76, -143),
		"xCannon" to BlockPos(-143, 76, -125),
		"Square" to BlockPos(-143, 76, -80),
	)

	private val supplyLine = Regex("(?:\\[[^]]*])? ?(\\w{1,16}) recovered one of Elle's supplies! \\((\\d)/(\\d)\\)")
	private val buildName = Regex("Building Progress (\\d+)% \\((\\d+) Players Helping\\)")
	private val manaLine = Regex("^Used Extreme Focus! \\((\\d+) Mana\\)$")
	val PILE = Regex("PROGRESS: (\\d+)%")

	fun supplyPickup(line: String): Triple<String, Int, Int>? {
		val g = supplyLine.find(line)?.groupValues ?: return null
		return Triple(g[1], g[2].toInt(), g[3].toInt())
	}

	fun buildProgress(name: String): Pair<Int, Int>? {
		val g = buildName.find(name)?.groupValues ?: return null
		return Pair(g[1].toInt(), g[2].toInt())
	}

	fun manaDrain(line: String): Int? = manaLine.find(line.trim())?.let { it.groupValues[1].toInt() }

	private val alwaysUseless = setOf("Elle's Lava Rod", "Elle's Pickaxe", "Auto Revive")
	private val uselessParts = listOf("Steady Hands", "Bomberman", "Mining Frenzy")

	fun uselessPerk(name: String, showStun: Boolean): Boolean {
		if (uselessParts.any { name.contains(it) }) return true
		if (name in alwaysUseless) return true
		return !showStun && name.contains("Human Cannonball")
	}

	fun giantPickup(x: Double, z: Double, yRot: Float): BlockPos {
		val rad = Math.toRadians((yRot + 130).toDouble())
		val px = x + 3.7 * cos(rad)
		val pz = z + 3.7 * sin(rad)
		return BlockPos(px.toInt(), 73, pz.toInt())
	}

	fun colourBuild(b: Int): String {
		val c = if (b >= 75) 'a' else if (b >= 50) 'e' else if (b >= 25) '6' else 'c'
		return "§$c$b"
	}

	fun colourBuilders(b: Int): String {
		val c = if (b >= 3) 'a' else if (b >= 2) 'e' else 'c'
		return "§$c$b"
	}
}

object KuudraKitPorts {
	private const val RUN_DONE = "[NPC] Elle: Good job everyone. A hard fought battle come to an end. Let's get out of here before we run into any more trouble!"

	var runs = 0; private set
	var paidChests = 0; private set
	var freeChests = 0; private set

	private object Seen {
		var build: Pair<Int, Int>? = null
		var piles: List<ArmorStand> = emptyList()
		var giants: List<Giant> = emptyList()
		var stunShown = false

		fun clear() {
			build = null
			piles = emptyList()
			giants = emptyList()
			stunShown = false
		}
	}

	private fun inKuudra() = SkyBlockSession.skyblockIsland == SkyBlockIsland.KUUDRA

	@Subscribe
	fun onTick(event: TickEvent) {
		if (!inKuudra()) return Seen.clear()
		if (event.tickCount % 5 != 0) return
		val world = Client.level ?: return
		val all = world.entitiesForRendering()
		val stands = all.filterIsInstance<ArmorStand>()
		Seen.build = stands.firstNotNullOfOrNull { it.customName?.bareText?.let(KuudraKitLogic::buildProgress) }
		Seen.piles = stands.filter { stand -> stand.customName?.bareText?.let(KuudraKitLogic.PILE::containsMatchIn) ?: false }
		Seen.giants = all.filterIsInstance<Giant>().filter { it.mainHandItem.hoverName.bareText.endsWith("Head") }
		val percent = Seen.build?.first ?: return
		val stunAt = KuudraInfo.TConfig.stunPercent
		if (Seen.stunShown || stunAt <= 0 || percent < stunAt) return
		Seen.stunShown = true
		Client.hud.setTitle(Component.literal("§3§lGo to stun"))
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!inKuudra()) return
		val line = event.bareText.trim()
		if (KuudraSupplies.TConfig.sendSupplyTime && KuudraSupplies.inSupplyPhase) {
			val pickup = KuudraKitLogic.supplyPickup(line)
			val since = KuudraSupplies.runStart
			if (pickup != null && since != null) {
				val took = ApexFormatters.timespanText((System.currentTimeMillis() - since).milliseconds)
				Client.showChat(Component.literal("§6${pickup.first} §a§lrecovered a supply in $took! §8(${pickup.second}/${pickup.third})"))
			}
		}
		if (KuudraProfit.TConfig.runCounter && line == RUN_DONE) runs += 1
	}

	@Subscribe
	fun onScreen(event: ScreenChangeEvent) {
		if (!KuudraProfit.TConfig.runCounter) return
		val title = event.new?.title?.bareText?.trim() ?: return
		if (title == "Paid Chest") paidChests += 1
		if (title == "Free Chest") freeChests += 1
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!inKuudra() || !KuudraInfo.TConfig.buildHud) return
		val (percent, builders) = Seen.build ?: return
		event.drawHudLines(KuudraInfo.TConfig.buildHudPos, listOf(
			Component.literal("§bBuilders: " + KuudraKitLogic.colourBuilders(builders)),
			Component.literal("§bBuild: " + KuudraKitLogic.colourBuild(percent) + "%"),
		))
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		if (!inKuudra()) return
		val sup = KuudraSupplies.TConfig
		val supplyPhase = KuudraSupplies.inSupplyPhase
		WorldDraw.drawInWorld(event, WaypointType.KUUDRA) {
			if (supplyPhase) {
				if (sup.dropWaypoints) KuudraKitLogic.dropSpots.forEach { (name, at) -> waypoint(at, Component.literal("§ePlace Here! §7($name)")) }
				if (sup.supplyWaypoints) Seen.giants.forEach { waypoint(KuudraKitLogic.giantPickup(it.x, it.z, it.yRot), Component.literal("§ePick Up!")) }
				if (sup.renderArea) KuudraKitLogic.pickupSpots.forEach { (name, at) -> text(Vec3.atCenterOf(at), Component.literal("§e$name")) }
				val me = Client.player
				if (sup.pearlWaypoints && me != null) {
					for (lineup in PearlLineups.visible(PearlLineups.table, me.x, me.y, me.z, sup.pearlClosestOnly)) {
						lineup.start.forEach { waypoint(BlockPos(it.x, it.y, it.z), Component.literal("§7Pearl spot §f(${lineup.supply})")) }
						lineup.targets.forEach { waypoint(BlockPos(it.x, it.y, it.z), Component.literal("§bPearl here §7(${lineup.supply})")) }
					}
				}
			}
			if (KuudraInfo.TConfig.pileWaypoints && Seen.build != null) {
				Seen.piles.forEach { waypoint(it.blockPosition(), it.customName ?: Component.literal("Pile")) }
			}
		}
	}

	private fun dimmed(slot: Slot): Boolean {
		val info = KuudraInfo.TConfig
		if (!info.removePerks || Client.screenTitle != "Perk Menu") return false
		if (slot.container === Client.player?.inventory) return false
		return KuudraKitLogic.uselessPerk(slot.item.hoverName.bareText, info.removePerksShowStun)
	}

	@Subscribe
	fun onSlot(event: SlotRenderEvents.After) {
		val s = event.slot
		if (dimmed(s)) event.context.fill(s.x, s.y, s.x + 16, s.y + 16, 0xFF8B8B8B.toInt())
	}

	@Subscribe
	fun onProtect(event: IsSlotProtectedEvent) {
		val s = event.slot ?: return
		if (dimmed(s)) event.protectSilent()
	}
}
