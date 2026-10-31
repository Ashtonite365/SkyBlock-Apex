// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.kit.boss

import org.joml.Vector2i
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.level.block.FlowerPotBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ParticleSpawnEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.UseBlockEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.features.plus.dungeon.PlusDungeonState
import dev.ashtonite365.apex.features.plus.rift.PlusBlockChangeEvent
import dev.ashtonite365.apex.util.kit.ServerTickEvent
import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.util.Alerts
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.render.drawHudLines
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.skyblock.DungeonUtil

private object BossArea {

	val floor: Int? get() = PlusDungeonState.floor?.drop(1)?.toIntOrNull()
	val master: Boolean get() = PlusDungeonState.floor?.let { it.startsWith("M") } ?: false
	fun boss(n: Int): Boolean = DungeonUtil.isInDungeonIsland && PlusDungeonState.inBoss && floor == n

	val dragons: Boolean get() = boss(7) && (Client.player?.y ?: 100.0) < 50.0
	fun id(state: BlockState): String = BuiltInRegistries.BLOCK.getKey(state.block).path
	fun say(text: String) = Client.showChat(Component.literal(text))
	fun seconds(ticks: Number) = "%.2f".format(ticks.toFloat() / 20f)
}

object BossTickTimers {
	@Config
	object TConfig : ManagedConfig("boss-tick-timers", Category.DUNGEONS) {
		val enabled by toggle("enabled") { false }
		val hud by position("hud", 90, 50) { Vector2i(200, 120) }
		val goldor by toggle("goldor") { true }
		val goldorStart by toggle("goldor-start") { false }
		val stormPad by toggle("storm-pad") { true }
		val stormLightning by toggle("storm-lightning") { false }
		val stormPy by toggle("storm-py") { false }
		val stormTick by toggle("storm-tick") { false }
		val fireFreeze by toggle("fire-freeze") { false }
		val inTicks by toggle("in-ticks") { false }
		val symbol by toggle("symbol") { true }
		val prefix by toggle("prefix") { true }
	}

	val state = TickTimerState()

	@Subscribe fun onWorld(e: WorldReadyEvent) { state.reset() }

	@Subscribe fun onChat(e: ProcessChatEvent) {
		if (!DungeonUtil.isInDungeonIsland) return
		state.onChat(e.bareText.trim())
	}

	@Subscribe fun onTick(e: ServerTickEvent) {
		if (!PlusDungeonState.inBoss) return
		state.onServerTick(TConfig.goldor, TConfig.stormPad)
	}

	private fun rows(): List<String> = buildList {
		val c = TConfig
		val s = state
		fun row(ticks: Int, max: Int, label: String) = add(TickTimerState.format(ticks, max, label, c.inTicks, c.symbol, c.prefix))
		if (c.goldor) when {
			c.goldorStart && s.goldorStart >= 0 -> row(s.goldorStart, 64, "§aStart:")
			s.goldorTick >= 0 -> row(s.goldorTick, 60, "§7Tick:")
		}
		if (c.stormPad && s.pad >= 0) row(s.pad, 20, "§bPad:")
		if (c.stormLightning && s.lightning >= 0) row(s.lightning, 520, "§bLightning:")
		if (c.stormPy && s.py >= 0) row(s.py, 55, "§bPY:")
		if (c.stormTick && s.stormTick >= 0) row(s.stormTick, 540, "§bStorm:")
		if (c.fireFreeze && s.fireFreeze >= 0) {
			if (s.fireFreeze <= 100) add("§cUse Fire Freeze") else row(s.fireFreeze - 100, 106, "Fire Freeze:")
		}
	}

	@Subscribe fun onHud(e: HudRenderEvent) {
		if (!TConfig.enabled || !DungeonUtil.isInDungeonIsland) return
		val out = rows()
		if (out.isEmpty()) return
		e.drawHudLines(TConfig.hud, out.map(Component::literal))
	}
}

object SpiritBearTimer {
	@Config
	object TConfig : ManagedConfig("spirit-bear-timer", Category.DUNGEONS) {
		val enabled by toggle("enabled") { false }
		val hud by position("hud", 70, 10) { Vector2i(200, 180) }
	}

	val state = SpiritBearState()

	@Subscribe fun onWorld(e: WorldReadyEvent) { state.reset() }
	@Subscribe fun onTick(e: ServerTickEvent) { state.onServerTick() }

	@Subscribe fun onBlock(e: PlusBlockChangeEvent) {
		if (!BossArea.boss(4)) return
		val p = e.pos
		state.onBlock(p.x, p.y, p.z, BossArea.id(e.old), BossArea.id(e.new), BossArea.master)
	}

	@Subscribe fun onHud(e: HudRenderEvent) {
		if (!TConfig.enabled || !BossArea.boss(4)) return
		e.drawHudLines(TConfig.hud, listOf(Component.literal("§6Bear: ${state.display(BossArea.master)}")))
	}
}

object TerracottaRespawnTimer {
	@Config
	object TConfig : ManagedConfig("terracotta-timer", Category.DUNGEONS) {
		val enabled by toggle("enabled") { false }
	}

	val state = TerracottaState()

	@Subscribe fun onWorld(e: WorldReadyEvent) { state.spawning.clear() }
	@Subscribe fun onTick(e: ServerTickEvent) { state.onServerTick() }

	@Subscribe fun onBlock(e: PlusBlockChangeEvent) {
		if (e.new.block !is FlowerPotBlock || !BossArea.boss(6)) return
		state.onFlowerPot(e.pos.x, e.pos.y, e.pos.z, BossArea.master)
	}

	@Subscribe fun onRender(e: WorldRenderLastEvent) {
		if (!TConfig.enabled || state.spawning.isEmpty() || !BossArea.boss(6)) return
		val snapshot = ArrayList(state.spawning)
		WorldDraw.drawInWorld(e) {
			snapshot.forEach { t ->
				val label = TerracottaState.colour(t.seconds) + "%.1fs".format(t.seconds)
				text(Vec3.atCenterOf(BlockPos(t.x, t.y, t.z)), Component.literal(label))
			}
		}
	}
}

object WitherDragonHelper {
	@Config
	object TConfig : ManagedConfig("wither-dragons", Category.DUNGEONS) {
		val timer by toggle("timer") { false }
		val timerStyle by integer("timer-style", 0, 2) { 0 }
		val symbol by toggle("symbol") { true }
		val hud by position("hud", 60, 10) { Vector2i(200, 200) }
		val title by toggle("title") { false }
		val sendSpawned by toggle("send-spawned") { false }
		val sendTime by toggle("send-time") { false }
		val boxes by riskyToggle("boxes", RiskLevel.RISKY)
		val tracer by riskyToggle("tracer", RiskLevel.RISKY)
		val health by riskyToggle("health", RiskLevel.RISKY)
	}

	val state = DragonState()

	private val tracked = HashMap<DragonState.Dragon, java.util.UUID>()

	private fun DragonState.Dragon.tag() = "§$colour$name"
	private fun DragonState.Dragon.centre() = Vec3(sx + 0.5, sy + 0.5, sz + 0.5)
	private fun DragonState.Dragon.rgb(): Int = when (colour) {
		'c' -> 0xFF5555
		'6' -> 0xFFAA00
		'a' -> 0x55FF55
		'b' -> 0x55FFFF
		else -> 0xAA00AA
	}
	private fun countdown(d: DragonState.Dragon, ticks: Int) =
		"§${d.colour}${d.name[0]}: ${DragonState.timer(ticks, TConfig.timerStyle, TConfig.symbol)}"

	private fun nearbyDragons(onlyLiving: Boolean): List<EnderDragon> {
		val world = Client.level ?: return emptyList()
		val player = Client.player ?: return emptyList()
		return world.getEntitiesOfClass(EnderDragon::class.java, player.boundingBox.inflate(200.0)) { !onlyLiving || it.health > 0 }
	}

	@Subscribe fun onWorld(e: WorldReadyEvent) {
		state.reset()
		tracked.clear()
	}

	@Subscribe fun onParticle(e: ParticleSpawnEvent) {
		if (!BossArea.dragons) return
		val at = e.position
		val off = e.offset
		val spawning = state.onParticle(e.particleEffect.type == ParticleTypes.FLAME, e.count, at.x, at.y, at.z, off.x, off.y, off.z) ?: return
		if (TConfig.title) Alerts.show(Component.literal("${spawning.tag()} is spawning!"))
	}

	@Subscribe fun onTick(e: ServerTickEvent) {
		val spawned = state.onServerTick()
		if (!TConfig.sendSpawned) return
		spawned.forEach { BossArea.say("${it.tag()} §fdragon spawned") }
	}

	@Subscribe fun onClientTick(e: TickEvent) {
		if (!BossArea.dragons || Client.level == null || Client.player == null) return
		for (entity in nearbyDragons(onlyLiving = false)) {
			val area = DragonState.Dragon.entries.firstOrNull { it.contains(entity.x, entity.y, entity.z) } ?: continue
			if (tracked[area] == null && state.state[area] == DragonState.S.ALIVE) tracked[area] = entity.uuid
			if (entity.health <= 0f && tracked[area] == entity.uuid) onDeath(state.kill(area))
		}
	}

	@Subscribe fun onBlock(e: PlusBlockChangeEvent) {
		if (!BossArea.dragons || !e.new.isAir) return
		onDeath(state.onStatueAir(e.pos.x, e.pos.y, e.pos.z))
	}

	private fun onDeath(result: Pair<DragonState.Dragon, Long>?) {
		if (result == null) return
		val dragon = result.first
		tracked.remove(dragon)
		if (TConfig.sendTime) BossArea.say("${dragon.tag()} §7was alive for §6${BossArea.seconds(result.second)}s§7!")
	}

	@Subscribe fun onHud(e: HudRenderEvent) {
		if (!TConfig.timer || !BossArea.dragons) return
		val next = state.priority ?: return
		val left = state.timeToSpawn[next] ?: 0
		if (left <= 0) return
		e.drawHudLines(TConfig.hud, listOf(Component.literal(countdown(next, left))))
	}

	@Subscribe fun onRender(e: WorldRenderLastEvent) {
		if (!BossArea.dragons || Client.level == null || Client.player == null) return
		val c = TConfig
		WorldDraw.drawInWorld(e) {
			DragonState.Dragon.entries.forEach { d ->
				val left = state.timeToSpawn[d] ?: 0
				if (c.timer && left > 0) text(d.centre(), Component.literal(countdown(d, left)))
				if (c.boxes && state.state[d] != DragonState.S.DEAD) {
					val b = d.box
					box(AABB(b[0], b[1], b[2], b[3], b[4], b[5]), 0x30000000 or d.rgb())
				}
			}
			val first = state.priority
			if (c.tracer && first != null && state.state[first] == DragonState.S.SPAWNING)
				tracer(first.centre(), 0xFF000000.toInt() or first.rgb())
			if (c.health) nearbyDragons(onlyLiving = true).forEach { text(it.position(), Component.literal(DragonState.health(it.health))) }
		}
	}
}

object KingRelicHelper {
	@Config
	object TConfig : ManagedConfig("king-relics", Category.DUNGEONS) {
		val placeTimer by toggle("place-timer") { false }
		val beacon by riskyToggle("beacon", RiskLevel.GREY_AREA)
	}

	private var relic: KingRelic? = null

	private var heldTicks = IDLE
	private const val IDLE = -1L
	private const val PLACED = -2L

	@Subscribe fun onWorld(e: WorldReadyEvent) {
		relic = null
		heldTicks = IDLE
	}

	@Subscribe fun onTick(e: ServerTickEvent) {
		if (!BossArea.dragons) return
		val inSlot = KingRelic.of(Client.player?.inventory?.getItem(8)?.view()?.repoItemId?.skyblockId)
		if (inSlot != null) {
			relic = inSlot
			if (heldTicks == IDLE) heldTicks = 0
		}
		if (heldTicks >= 0) heldTicks += 1
	}

	@Subscribe fun onUse(e: UseBlockEvent) {
		if (!BossArea.dragons || heldTicks < 0) return
		val target = BossArea.id(e.world.getBlockState(e.hitResult.blockPos))
		if (target != "cauldron" && target != "anvil") return
		val r = relic ?: return
		if (TConfig.placeTimer) BossArea.say("§${r.colour}${r.name} relic §7placed in §6${BossArea.seconds(heldTicks)}s")
		heldTicks = PLACED
	}

	@Subscribe fun onRender(e: WorldRenderLastEvent) {
		if (!TConfig.beacon || heldTicks == PLACED || !BossArea.dragons) return
		val r = relic ?: return
		val base = AABB(r.x.toDouble(), r.y.toDouble(), r.z.toDouble(), r.x + 1.0, r.y + 30.0, r.z + 1.0)
		WorldDraw.drawInWorld(e) { box(base, 0x50000000 or r.rgb) }
	}
}
