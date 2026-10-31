// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.kuudra

import org.joml.Vector2i
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.monster.Giant
import net.minecraft.world.entity.monster.cubemob.MagmaCube
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.ScoreboardUtil
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.render.drawHudLines
import dev.ashtonite365.apex.util.bareText

object KuudraLogic {

	enum class Supply(val x: Int, val z: Int) {
		Triangle(-67, -122), X(-142, -151), Equals(-65, -87), Slash(-113, -68),
		Shop(-81, -143), xCannon(-143, -125), Square(-143, -80), None(0, 0);

		fun distSq(px: Double, pz: Double): Double {
			val dx = px - x
			val dz = pz - z
			return dx * dx + dz * dz
		}

		fun within(px: Double, pz: Double, radius: Double) = distSq(px, pz) < radius * radius
	}

	private val bands = listOf(99000f to "§a", 75000f to "§2", 50000f to "§e", 25000f to "§6", 10000f to "§c")

	fun healthDisplay(hp: Float, tier: Int, scaled: Boolean = true): String {
		val colour = bands.firstOrNull { hp > it.first }?.second ?: "§4"
		val thousands = hp / 1000
		val lastPhaseInfernal = scaled && tier == 5 && hp <= 25000
		return if (lastPhaseInfernal) colour + "%.2f".format(thousands * 9.6) + "M§7/§a240M§c❤"
		else colour + "%.1f".format(thousands) + "K§7/§a100K§c❤"
	}

	private val preSpots = listOf(Supply.Triangle to 15.0, Supply.X to 30.0, Supply.Equals to 15.0, Supply.Slash to 15.0)

	fun preSpotAt(px: Double, pz: Double): Supply =
		preSpots.firstOrNull { (spot, r) -> spot.within(px, pz, r) }?.first ?: Supply.None

	private val partner = mapOf(Supply.Triangle to (Supply.Shop to 18.0), Supply.X to (Supply.xCannon to 16.0), Supply.Slash to (Supply.Square to 20.0))

	fun missingCall(pre: Supply, giants: List<Pair<Double, Double>>): String? {
		if (pre == Supply.None) return null
		val second = partner[pre]
		var preFound = false
		var secondFound = false
		for ((gx, gz) in giants) {
			if (pre.within(gx, gz, 18.0)) preFound = true
			else if (second != null && second.first.within(gx, gz, second.second)) secondFound = true
		}
		return when {
			!preFound -> "No ${pre.name}!"
			secondFound || second == null -> null
			else -> "No ${second.first.name}!"
		}
	}

	private fun spoken(s: Supply) = if (s == Supply.xCannon) "X Cannon" else s.name

	fun cratePriority(missing: Supply, pre: Supply, advanced: Boolean): String {
		if (missing == Supply.None || pre !in preSpots.map { it.first }) return ""
		val cannon = "Go X Cannon"
		val shop = "Go Shop"
		val equalsHelp = if (advanced) shop else cannon
		if (missing == pre) return if (advanced) "Pull Square and X Cannon. Next: collect Shop" else "Pull Square. Next: collect Shop"
		return when (pre) {
			Supply.X -> if (missing == Supply.xCannon) "Go Square, place on X Cannon" else cannon
			Supply.Slash -> if (missing == Supply.Square) cannon else "Go Square, place on ${spoken(missing)}"
			Supply.Triangle -> when (missing) {
				Supply.Shop, Supply.X -> cannon
				Supply.Equals -> equalsHelp
				Supply.Slash -> "Go Square, place on Slash"
				else -> shop
			}
			Supply.Equals -> when (missing) {
				Supply.Shop -> "Go Square, place on Shop"
				Supply.Square, Supply.xCannon -> shop
				else -> equalsHelp
			}
			else -> ""
		}
	}

	val partyMissingRegex = Regex("^Party > (?:\\[[^]]*?] )?(\\w{1,16}): No ?(Triangle|X|Equals|Slash|xCannon|Square|Shop)!$")
	val partyFreshRegex = Regex("^Party > (?:\\[[^]]*?] )?(\\w{1,16}): FRESH$")
	val tierRegex = Regex("Kuudra's Hollow \\(T(\\d)\\)")
}

object KuudraInfo {
	val identifier: String
		get() = "kuudra-info"

	@Config
	object TConfig : ManagedConfig(category = Category.KUUDRA, name = identifier) {
		val hpHud by toggle("hp-hud") { true }
		val scaledHp by toggle("scaled-hp") { true }
		val hud by position("hud", 110, 10) { Vector2i(4, 240) }
		val freshHud by toggle("fresh-hud") { true }
		val freshHudPos by position("fresh-hud-pos", 80, 30) { Vector2i(4, 255) }
		val preAlert by riskyToggle("pre-alert")
		val cratePriority by toggle("crate-priority") { false }
		val cratePriorityAdvanced by toggle("crate-priority-advanced") { false }
		val buildHud by toggle("build-hud") { false }
		val buildHudPos by position("build-hud-pos", 100, 20) { Vector2i(4, 285) }
		val stunPercent by integer("stun-percent", 0, 100) { 0 }
		val pileWaypoints by riskyToggle("pile-waypoints", RiskLevel.GREY_AREA)
		val removePerks by toggle("remove-perks") { false }
		val removePerksShowStun by toggle("remove-perks-show-stun") { false }
	}

	private const val FRESH_MILLIS = 10_000L
	private const val ELLE = "[NPC] Elle: "

	private val inKuudra get() = SkyBlockSession.skyblockIsland == SkyBlockIsland.KUUDRA

	private object RunState {
		var bossHp: Float? = null
		var tier = 0

		var stage = 0
		var pre = KuudraLogic.Supply.None
		val freshSince = LinkedHashMap<String, Long>()

		fun clear() {
			bossHp = null
			tier = 0
			stage = 0
			pre = KuudraLogic.Supply.None
			freshSince.clear()
		}
	}

	private val stageLines = mapOf(
		ELLE + "Okay adventurers, I will go and fish up Kuudra!" to 1,
		ELLE + "OMG! Great work collecting my supplies!" to 2,
		ELLE + "Phew! The Ballista is finally ready! It should be strong enough to tank Kuudra's blows now!" to 3,
	)

	private fun note(text: String) = Client.showChat(Component.literal(text))

	@Subscribe
	fun onTick(event: TickEvent) {
		if (event.tickCount % 5 != 0 || !inKuudra) return
		val world = Client.level ?: return
		RunState.bossHp = world.entitiesForRendering().asSequence()
			.filterIsInstance<MagmaCube>()
			.firstOrNull { it.size == 30 && it.getAttributeBaseValue(Attributes.MAX_HEALTH) == 100000.0 }
			?.health
		if (RunState.tier != 0) return
		val m = ScoreboardUtil.simplifiedScoreboardLines.firstNotNullOfOrNull { KuudraLogic.tierRegex.find(it) } ?: return
		RunState.tier = m.groupValues[1].toInt()
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!inKuudra) return
		val text = event.bareText.trim()
		val now = System.currentTimeMillis()
		val stage = stageLines[text]
		if (stage != null) RunState.stage = stage
		else when (text) {
			"Your Fresh Tools Perk bonus doubles your building speed for the next 10 seconds!" -> RunState.freshSince[Client.playerName] = now
			ELLE + "Head over to the main platform, I will join you when I get a bite!" -> {
				val me = Client.player ?: return
				RunState.pre = KuudraLogic.preSpotAt(me.x, me.z)
				if (TConfig.preAlert) note(
					if (RunState.pre == KuudraLogic.Supply.None) "§cPre-spot not registered: you weren't at one in time."
					else "§ePre-spot: ${RunState.pre.name}")
			}
			ELLE + "Not again!" -> if (TConfig.preAlert) missingNote()
		}
		KuudraLogic.partyFreshRegex.matchEntire(text)?.let { RunState.freshSince[it.groupValues[1]] = now }
		if (!TConfig.cratePriority) return
		val call = KuudraLogic.partyMissingRegex.matchEntire(text) ?: return
		val hint = KuudraLogic.cratePriority(KuudraLogic.Supply.valueOf(call.groupValues[2]), RunState.pre, TConfig.cratePriorityAdvanced)
		if (hint.isEmpty()) return
		Client.hud.setTitle(Component.literal("§e$hint"))
		note("§eCrate priority: $hint")
	}

	private fun missingNote() {
		val giants = Client.level?.entitiesForRendering()
			?.filterIsInstance<Giant>()
			?.filter { it.mainHandItem.hoverName.bareText.endsWith("Head") }
			?.map { it.x to it.z }
			.orEmpty()
		KuudraLogic.missingCall(RunState.pre, giants)?.let { note("§e$it") }
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!inKuudra) return
		val hp = RunState.bossHp
		if (TConfig.hpHud && hp != null)
			event.drawHudLines(TConfig.hud, listOf(Component.literal(KuudraLogic.healthDisplay(hp, RunState.tier, TConfig.scaledHp))))
		if (RunState.stage != 2 || !TConfig.freshHud) return
		val now = System.currentTimeMillis()
		val rows = RunState.freshSince.entries.mapNotNull { (who, since) ->
			val remaining = FRESH_MILLIS - (now - since)
			if (remaining <= 0) null else Component.literal("§6$who§f: ${"%.1f".format(remaining / 1000f)}s")
		}
		if (rows.isNotEmpty()) event.drawHudLines(TConfig.freshHudPos, rows)
	}

	@Subscribe
	fun onWorld(event: WorldReadyEvent) {
		RunState.clear()
	}
}
