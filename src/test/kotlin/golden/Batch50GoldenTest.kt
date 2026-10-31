// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.events.ParticleSpawnEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.SoundReceiveEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.features.misc.MiscRender
import dev.ashtonite365.apex.features.plus.foraging.PlusForaging
import dev.ashtonite365.apex.features.plus.reminders.MiscPlusPorts
import dev.ashtonite365.apex.test.golden.Harness
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import dev.ashtonite365.apex.util.Alerts
import dev.ashtonite365.apex.util.ScoreboardUtil
import dev.ashtonite365.apex.util.mc.TabListReader
import java.util.Optional
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import net.minecraft.core.Holder
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.ExperienceOrb
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.item.FallingBlockEntity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch50GoldenTest {
	companion object {
		private val unsafe = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").also { it.isAccessible = true }.get(null) as sun.misc.Unsafe
		fun <T> alloc(c: Class<T>): T = c.cast(unsafe.allocateInstance(c))
		private val alertField = Alerts::class.java.getDeclaredField("current").also { it.isAccessible = true }
		fun takeAlert(): String { val c = alertField.get(Alerts) as Component?; alertField.set(Alerts, null); return c?.let(Harness::dump) ?: "-" }
		fun chatEv(s: String) = ProcessChatEvent(Component.literal(s), false)
		val islands = listOf(null, Harness.skyblock("hub"), Harness.skyblock("foraging_1"), Harness.skyblock("foraging_2"), Harness.skyblock("dungeon"))
	}

	private fun <T> alerts(block: () -> T): T = Harness.withOpts(Alerts.TConfig, mapOf("titles" to true, "sounds" to false)) { takeAlert(); block() }

	@Test
	fun render() {
		val S = Batch50Misc
		val M = MiscRender
		val out = mutableListOf<Pair<String, String>>()
		for (s in S.splashes) out += "splash|$s" to "${M.isDamageSplash(s)}"
		for ((i, m) in S.migrations.withIndex()) out += "mig|$i|$m" to Golden.run { M.foldOldHideFire(Json.parseToJsonElement(m) as JsonObject).toString() }
		for (d in S.distances) {
			for (f in S.farDists) out += "far|$d|$f" to "${M.isFar(d, f)}"
			for (b in 0 until 8) out += "pd|$d|$b" to "${M.hideParticleByDistance(d, b and 1 != 0, b and 2 != 0, b and 4 != 0)}"
		}
		for (t in listOf(-1, 0, 9, 10, 11, 100)) for (b in 0 until 4) out += "temp|$t|$b" to "${M.isTempArmorStand(t, b and 1 != 0, b and 2 != 0)}"
		for (n in S.names) for (l in S.nameLists) out += "nm|$n|$l" to "${M.nameMatches(n, l)}"
		for (f in MiscRender.FireOverlay.entries) out += "fo|${f.name}" to "${f.id}|${f.serializedName}|${f.ordinal}"
		out += "oldkey" to M.OLD_HIDE_FIRE_KEY

		val ents: List<Pair<String, () -> Entity>> = listOf(
			"stand" to { alloc(ArmorStand::class.java) }, "orb" to { alloc(ExperienceOrb::class.java) }, "item" to { alloc(ItemEntity::class.java) },
			"fall" to { alloc(FallingBlockEntity::class.java) }, "bdisp" to { alloc(Display.BlockDisplay::class.java) }, "tdisp" to { alloc(Display.TextDisplay::class.java) },
		)
		for ((n, e) in ents) out += "anim|$n" to Golden.run { "${M.isAnimationBlock(e())}" }
		val keys = listOf("hide-damage-splash", "hide-temp-armor-stands", "hide-exp-orbs", "hide-far-entities", "hide-players-near-npcs", "item-glow", "party-outlines", "entity-outlines")
		for (loc in islands.take(2)) for (mask in 0 until 256) {
			if (loc == null && mask % 17 != 0) continue
			Harness.withOpts(MiscRender.TConfig, keys.withIndex().associate { (i, k) -> k to (mask shr i and 1 == 1) } + mapOf("outline-names" to (if (mask and 1 == 0) "" else "zombie"))) {
				Harness.at(loc) {
					for ((n, e) in ents) {
						out += "hide|${loc?.mode}|$mask|$n" to Golden.run { "${M.shouldHide(e())}" }
						out += "glow|${loc?.mode}|$mask|$n" to Golden.run { "${M.shouldGlow(e())}" }
					}
				}
			}
		}
		for (loc in islands.take(2)) for (fo in MiscRender.FireOverlay.entries) for (a in listOf(false, true)) {
			Harness.withOpts(MiscRender.TConfig, mapOf("fire-overlay" to fo, "hide-player-armour" to a)) {
				Harness.at(loc) { out += "flags|${loc?.mode}|${fo.id}|$a" to "${M.hideArmour()}|${M.hideFire()}|${M.lowFire()}" }
			}
		}
		for (z in listOf(false, true)) out += "zoom|$z" to Golden.run { "${M.zoomedFov(70f)}" }
		val parts: List<Pair<String, ParticleOptions>> = listOf("portal" to ParticleTypes.PORTAL, "smoke" to ParticleTypes.SMOKE, "flame" to ParticleTypes.FLAME)
		for (loc in islands.take(2)) for (mask in 0 until 32) Harness.withOpts(MiscRender.TConfig, listOf("hide-enderman-particles", "hide-far-particles", "hide-close-redstone", "hide-fireball-particles", "hide-players-near-npcs")
			.withIndex().associate { (i, k) -> k to (mask shr i and 1 == 1) }) {
			Harness.at(loc) {
				for ((pn, p) in parts) {
					val ev = ParticleSpawnEvent(p, Vec3(1.0, 2.0, 3.0), Vector3f(), false, 1, 0f)
					out += "psh|${loc?.mode}|$mask|$pn" to Golden.run { M.onParticleSh(ev); "${ev.cancelled}" }
					val ev2 = ParticleSpawnEvent(p, Vec3(1.0, 2.0, 3.0), Vector3f(), false, 1, 0f)
					out += "pen|${loc?.mode}|$mask|$pn" to Golden.run { M.onParticle(ev2); "${ev2.cancelled}" }
				}
				for (t in listOf(0, 1, 20)) out += "tick|${loc?.mode}|$mask|$t" to Golden.run { M.onTick(TickEvent(t)); "ok" }
			}
		}
		val n = Golden.verify("batch50-render", out, minCases = 6216)
		assertTrue(n >= 6216)
	}

	@Test
	fun foraging() {
		val S = Batch50Misc
		val F = PlusForaging
		val out = mutableListOf<Pair<String, String>>()
		for (p in S.soundPaths) for (v in S.volumes) out += "fus|$p|$v" to "${F.isFusionSound(p, v)}"
		for ((i, t) in S.tabs.withIndex()) out += "beacon|$i" to "${F.isBeaconReady(t)}"
		for (m in S.questMsgs) out += "quest|$m" to "${F.questFor(m)?.name}"
		for (q in PlusForaging.Quest.entries) out += "q|${q.name}" to "${q.questName}|${q.npc}|${q.pos}|${q.ordinal}"
		val keys = listOf("mute-fusion-machine", "mute-tree-breaking", "hide-tree-blocks", "beacon-ready-alert", "honeyhive-reminder", "queen-bee-alert", "tutorial-quest-waypoint")
		alerts {
			for (loc in islands) for (mask in 0 until 128) {
				if (loc == null && mask % 5 != 0) continue
				Harness.withOpts(PlusForaging.TConfig, keys.withIndex().associate { (i, k) -> k to (mask shr i and 1 == 1) }) {
					Harness.at(loc) {
						for (p in S.soundPaths) for (v in listOf(20f, 1f)) {
							val ev = SoundReceiveEvent(Holder.direct(SoundEvent.createVariableRangeEvent(Identifier.withDefaultNamespace(p))), SoundSource.MASTER, Vec3.ZERO, 1f, v, 0L)
							out += "snd|${loc?.mode}|$mask|$p|$v" to Golden.run { F.onSound(ev); "${ev.cancelled}" }
						}
						out += "hideE|${loc?.mode}|$mask|bd" to Golden.run { "${F.shouldHideEntity(alloc(Display.BlockDisplay::class.java))}" }
						out += "hideE|${loc?.mode}|$mask|st" to Golden.run { "${F.shouldHideEntity(alloc(ArmorStand::class.java))}" }
						if (mask % 3 == 0) for (m in S.forageChat) {
							val chat = Harness.chat { F.onChat(chatEv(m)) }
							out += "chat|${loc?.mode}|$mask|$m" to "${takeAlert()}|$chat"
						}
					}
				}
			}

			Harness.withOpts(PlusForaging.TConfig, mapOf("beacon-ready-alert" to true, "honeyhive-reminder" to true)) {
				for (loc in islands) Harness.at(loc) {
					for ((i, t) in S.tabs.withIndex()) {
						TabListReader.cachedSnapshot = TabListReader.TabSnapshot(Optional.empty(), Optional.empty(), t.map { Component.literal(it) })
						for (tick in listOf(1, 20, 40)) {
							val chat = Harness.chat { runCatching { F.onTick(TickEvent(tick)) } }
							out += "btick|${loc?.mode}|$i|$tick" to "${takeAlert()}|$chat"
						}
					}
				}
			}
			TabListReader.cachedSnapshot = null
		}
		val n = Golden.verify("batch50-foraging", out, minCases = 12090)
		assertTrue(n >= 12090)
	}

	@Test
	fun reminders() {
		val S = Batch50Misc
		val P = MiscPlusPorts
		val out = mutableListOf<Pair<String, String>>()
		alerts {
			for (k in listOf(false, true)) Harness.withOpts(MiscPlusPorts.TConfig, mapOf("kick-duration" to k)) {
				for (m in S.kickChat) {
					val chat = Harness.chat { P.onChat(chatEv(m)) }
					out += "kick|$k|$m" to "${takeAlert()}|$chat"
				}
			}

			for (loc in islands.take(2)) for (on in listOf(false, true)) Harness.withOpts(MiscPlusPorts.TConfig, mapOf("server-restart-title" to on)) {
				Harness.at(loc) {
					for ((i, b) in S.restartBoards.withIndex()) {
						ScoreboardUtil.simplifiedScoreboardLines = b
						for (t in listOf(19, 20)) out += "restart|${loc?.mode}|$on|$i|$t" to "${Golden.run { P.onTick(TickEvent(t)); "ok" }}|${takeAlert()}"
					}
				}
			}
			ScoreboardUtil.simplifiedScoreboardLines = listOf()

			for ((mi, minutes) in listOf(5, 1).withIndex()) for ((oi, on) in listOf(true, false, true).withIndex()) Harness.withOpts(MiscPlusPorts.TConfig, mapOf("last-servers" to on, "last-servers-minutes" to minutes)) {
				for ((i, srv) in S.servers.withIndex()) {
					for (loc in listOf(srv, null)) {
						val l = loc?.let { dev.ashtonite365.apex.util.ServerLocation(it, "SKYBLOCK", "hub", null) }
						for (t in listOf(20, 7, 40)) Harness.at(l) {
							val chat = Harness.chat { P.onTick(TickEvent(t)) }
							out += "srv|$mi|$oi$on|$i|$loc|$t" to "$chat".replace(Regex("§e\\d+m \\d+s ago"), "§eNm Ns ago")
						}
					}
					val other = dev.ashtonite365.apex.util.ServerLocation(srv, "PROTOTYPE", null, null)
					Harness.at(other) { out += "srvnon|$mi|$oi$on|$i" to "${Harness.chat { P.onTick(TickEvent(20)) }}" }
				}
			}
		}
		val n = Golden.verify("batch50-reminders", out, minCases = 496)
		assertTrue(n >= 496)
	}
}
