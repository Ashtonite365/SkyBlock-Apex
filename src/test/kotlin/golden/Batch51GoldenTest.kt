// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.events.ParticleSpawnEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.SoundReceiveEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.features.plus.rift.PlusBlockChangeEvent
import dev.ashtonite365.apex.features.plus.rift.PlusRiftDanceRoom
import dev.ashtonite365.apex.features.plus.rift.PlusRiftDreadfarm
import dev.ashtonite365.apex.features.plus.rift.PlusRiftEffigies
import dev.ashtonite365.apex.features.plus.rift.PlusRiftEnigma
import dev.ashtonite365.apex.features.plus.rift.PlusRiftHighlights2
import dev.ashtonite365.apex.features.plus.rift.PlusRiftLivingCave
import dev.ashtonite365.apex.features.plus.rift.PlusRiftMisc
import dev.ashtonite365.apex.features.plus.rift.PlusRiftParkour
import dev.ashtonite365.apex.features.plus.rift.PlusRiftPuzzles
import dev.ashtonite365.apex.test.golden.Harness
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import dev.ashtonite365.apex.util.Alerts
import dev.ashtonite365.apex.util.ServerLocation
import dev.ashtonite365.apex.util.ScoreboardUtil
import kotlin.random.Random
import net.minecraft.client.player.RemotePlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.particles.ColorParticleOption
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.animal.equine.Horse
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch51GoldenTest {
	companion object {
		private val unsafe = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").also { it.isAccessible = true }.get(null) as sun.misc.Unsafe
		fun <T> alloc(c: Class<T>): T = c.cast(unsafe.allocateInstance(c))
		private val alertField = Alerts::class.java.getDeclaredField("current").also { it.isAccessible = true }

		fun takeAlert(): String {
			val c = alertField.get(Alerts) as Component?
			alertField.set(Alerts, null)
			return c?.let(Harness::dump)?.replace(Regex("\\d\\.\\d\\ds left"), "N.NNs left") ?: "-"
		}
		fun chatEv(s: String) = ProcessChatEvent(Component.literal(s), false)
		fun sound(path: String, pitch: Float = 1f, volume: Float = 1f) =
			SoundReceiveEvent(Holder.direct(SoundEvent.createVariableRangeEvent(Identifier.withDefaultNamespace(path))), SoundSource.MASTER, Vec3.ZERO, pitch, volume, 0L)
		fun particle(p: ParticleOptions, at: Vec3 = Vec3(1.0, 2.0, 3.0), count: Int = 1, speed: Float = 0f) = ParticleSpawnEvent(p, at, Vector3f(), false, count, speed)
		val rift = Harness.skyblock("rift")
		val locs = listOf(null, Harness.skyblock("hub"), rift)
		fun board(vararg lines: String) { ScoreboardUtil.simplifiedScoreboardLines = lines.toList(); ScoreboardUtil.scoreboardLines = lines.map { Component.literal(it) } }
	}

	private fun <T> alerts(block: () -> T): T = Harness.withOpts(Alerts.TConfig, mapOf("titles" to true, "sounds" to false)) { takeAlert(); block() }
	private fun opts(cfg: dev.ashtonite365.apex.util.data.ManagedConfig, keys: List<String>, mask: Int) = keys.withIndex().associate { (i, k) -> k to (mask shr i and 1 == 1) }

	@Test
	fun misc() {
		val out = mutableListOf<Pair<String, String>>()
		val r = Random(5151)
		for (n in listOf(Int.MIN_VALUE, -1, 0, 1, 2, 11, 12, 13, 100, Int.MAX_VALUE)) out += "km|$n" to "${PlusRiftMisc.killMillis(n)}"
		val kz = listOf("⚠ Get back in the arena or you will DIE! ⚠", "⚠ Get back in the arena or you will DIE!!!!!!!!!!!! ⚠", "⚠ Get back in the arena or you will DIE ⚠",
			"Get back in the arena or you will DIE!", "⚠ Get back in the arena or you will DIE!!! ⚠ ", " ⚠ Get back in the arena or you will DIE!!! ⚠", "⚠ get back in the arena or you will DIE! ⚠") +
			List(60) { "⚠ Get back in the arena or you will DIE${"!".repeat(r.nextInt(0, 16))}${listOf(" ⚠", "⚠", " ⚠!", "").random(r)}" }
		for (m in kz) out += "kx|$m" to "${PlusRiftMisc.killExclamations(m)}"
		out += "shy" to PlusRiftMisc.shyNames.toList().toString()
		val ents: List<Pair<String, () -> Entity>> = listOf("horse" to { alloc(Horse::class.java) }, "stand" to { alloc(ArmorStand::class.java) })
		val keys = listOf("horsezooka-hider", "shy-crux-warning", "splatter-hearts", "kill-zone-warning", "ubik-quick-close")
		alerts {
			for (loc in locs) for (mask in 0 until 32) Harness.withOpts(PlusRiftMisc.TConfig, opts(PlusRiftMisc.TConfig, keys, mask)) {
				Harness.at(loc) {
					out += "inrift|${loc?.mode}|$mask" to "${PlusRiftMisc.inRift()}"
					for ((n, e) in ents) out += "hide|${loc?.mode}|$mask|$n" to Golden.run { "${PlusRiftMisc.shouldHideEntity(e())}" }
					for (m in kz.take(7)) {

						PlusRiftMisc.onChat(chatEv("⚠ Get back in the arena or you will DIE!!!!!!!!!!!! ⚠"))
						PlusRiftMisc.onChat(chatEv(m))
						out += "kill|${loc?.mode}|$mask|$m" to "${Golden.run { PlusRiftMisc.onTick(TickEvent(1)); "ok" }}|${takeAlert()}"
					}
					val ev = particle(ParticleTypes.HEART, count = 3)
					out += "heart|${loc?.mode}|$mask" to Golden.run { PlusRiftMisc.onParticle(ev); "${ev.cancelled}" }
				}
			}
		}
		val n = Golden.verify("batch51-misc", out, minCases = 1124)
		assertTrue(n >= 1124)
	}

	@Test
	fun parkour() {
		val out = mutableListOf<Pair<String, String>>()
		val P = PlusRiftParkour
		for (c in PlusRiftParkour.Course.entries) out += "course|${c.name}" to "${c.path}|${c.offset}|${c.platform}|${c.range}|${c.inOrder}|${c.ordinal}"
		for (c in PlusRiftParkour.Course.entries) out += "build|${c.name}" to Golden.run { val s = c.build(); "${s?.locations?.size}|${s?.locations?.take(3)}|${s?.shortCuts?.take(3)}" }
		val boards = listOf(listOf(), listOf("⏣ Mirrorverse"), listOf(" ⏣ West Village"), listOf("Mirrorverse"), listOf("x", "⏣ Stillgore Château"), listOf("⏣ mirrorverse"), listOf("⏣ The Mirrorverse x"))
		val areas = listOf("Mirrorverse", "West Village", "Stillgore", "", "⏣")
		for ((i, b) in boards.withIndex()) { board(*b.toTypedArray()); for (a in areas) out += "area|$i|$a" to "${P.inArea(a)}" }
		val chats = listOf("RIFT RACING Race started! Good luck!", "RIFT RACING Race finished in 1:02.345!", "RIFT RACING Race cancelled!", "RIFT RACING Race finished in 1:02.345! (PB)",
			"OH NO! THE LAVA OOFED YOU BACK TO THE START!", "EEK! THE LAVA OOFED YOU!", "RIFT RACING Race started! Good luck!!", "  RIFT RACING Race started! Good luck!  ", "RIFT RACING Race finished in 1:2!")
		val keys = listOf("gunthers-race", "upside-down", "lava-maze", "tubulator", "hide-players")
		P.onWorld(WorldReadyEvent())
		for (loc in locs) for ((bi, b) in boards.withIndex()) for (mask in 0 until 32) {
			if (mask % 3 != 0 && bi > 2) continue
			board(*b.toTypedArray())
			Harness.withOpts(PlusRiftParkour.TConfig, opts(PlusRiftParkour.TConfig, keys, mask)) {
				Harness.at(loc) {
					for (m in chats) {
						P.onChat(chatEv(m))
						out += "pk|${loc?.mode}|$bi|$mask|$m" to "${P.inRace} " + PlusRiftParkour.Course.entries.joinToString(",") { c -> Golden.run { "${P.enabled(c)}" } } +
							" " + Golden.run { "${P.shouldHideEntity(alloc(RemotePlayer::class.java))}" } + " " + Golden.run { "${P.shouldHideEntity(alloc(ArmorStand::class.java))}" }
					}
				}
			}
			if (mask == 0) { P.onWorld(WorldReadyEvent()); out += "pkw|${loc?.mode}|$bi" to "${P.inRace}" }
		}
		board()
		val n = Golden.verify("batch51-parkour", out, minCases = 3834)
		assertTrue(n >= 3834)
	}

	@Test
	fun puzzles() {
		val out = mutableListOf<Pair<String, String>>()
		val D = PlusRiftDanceRoom
		val F = PlusRiftDreadfarm
		val E = PlusRiftEffigies

		D.onWorld(WorldReadyEvent())
		for (loc in locs) for (on in listOf(false, true)) Harness.withOpts(PlusRiftDanceRoom.TConfig, mapOf("enabled" to on, "hide-players" to on)) {
			Harness.at(loc) {
				for (t in listOf(0, 1, 10, 20)) {
					D.onTick(TickEvent(t))
					for (s in listOf("entity.experience_orb.pickup", "block.note_block.pling", "entity.generic.explode", "entity.villager.no")) for (pv in listOf(0.0f to 1f, 1f to 1f, 2f to 0.5f)) {
						D.onSound(sound(s, pv.first, pv.second))
						out += "dance|${loc?.mode}|$on|$t|$s|$pv" to "${D.index}|${D.inRoom}"
					}
					out += "dhide|${loc?.mode}|$on|$t" to Golden.run { "${D.shouldHideEntity(alloc(RemotePlayer::class.java))}" }
				}
			}
		}

		val btn = listOf("You have hit 3/56 of the wooden buttons!", "§eYou have hit §r§b3/56 §r§eof the wooden buttons!", "You've hit all 56 wooden buttons!",
			"§eYou've hit all §r§b56 §r§ewooden buttons!", "You have hit 3/55 of the wooden buttons!", "hello")
		val dkeys = listOf("wooden-buttons", "wilted-berberis", "berberis-sequence", "only-on-farmland", "hide-berberis-particles", "mute-berberis")
		for (loc in locs) for (mask in 0 until 64) Harness.withOpts(PlusRiftDreadfarm.TConfig, opts(PlusRiftDreadfarm.TConfig, dkeys, mask)) {
			Harness.at(loc) {
				if (mask % 4 == 0) {
					F.onWorld(WorldReadyEvent())
					for (m in btn) {
						val c = Harness.chat { F.onChat(ProcessChatEvent(if (m.startsWith("§")) Component.literal(m) else Component.literal(m), false)) }
						out += "btn|${loc?.mode}|$mask|$m" to "${F.allHit}|$c"
					}
				}
				for (s in listOf("entity.donkey.death", "entity.donkey.hurt", "entity.donkey.ambient", "entity.horse.death")) {
					val ev = sound(s); F.onSound(ev); out += "bsnd|${loc?.mode}|$mask|$s" to "${ev.cancelled}"
				}
				for ((pn, p) in listOf<Pair<String, ParticleOptions>>("fw" to ParticleTypes.FIREWORK, "hv" to ParticleTypes.HAPPY_VILLAGER, "heart" to ParticleTypes.HEART)) {
					val ev = particle(p); out += "bpart|${loc?.mode}|$mask|$pn" to Golden.run { F.onParticle(ev); "${ev.cancelled}" }
				}
				out += "bblock|${loc?.mode}|$mask" to Golden.run {
					F.onBlock(PlusBlockChangeEvent(BlockPos(1, 2, 3), Blocks.DEAD_BUSH.defaultBlockState(), Blocks.AIR.defaultBlockState()))
					F.onBlock(PlusBlockChangeEvent(BlockPos(1, 2, 3), Blocks.AIR.defaultBlockState(), Blocks.DEAD_BUSH.defaultBlockState())); "ok" }
				out += "btick|${loc?.mode}|$mask" to Golden.run { F.onTick(TickEvent(mask)); "ok" }
			}
		}
		F.onWorld(WorldReadyEvent())

		val hearts = listOf("Effigies: ⧯⧯⧯⧯⧯⧯", "Effigies: §c⧯§c⧯§c⧯§c⧯§c⧯§c⧯", "Effigies: §7⧯§c⧯§7⧯§c⧯§7⧯§c⧯", "Effigies: §c⧯⧯⧯⧯⧯§7⧯", "Effigies: §7⧯§7⧯§7⧯§7⧯§7⧯§7⧯",
			"Effigies: ", "Effigies: ⧯⧯⧯", "Effigies: §c⧯⧯⧯⧯⧯⧯⧯⧯", "Effigies: x", "Effigies:§c⧯", "Effigies: §c⧯§c⧯")
		for (loc in locs) for (area in listOf("⏣ Stillgore Château", "⏣ Mirrorverse")) for ((ei, en) in listOf(false, true).withIndex()) {
			Harness.withOpts(PlusRiftEffigies.TConfig, mapOf("enabled" to en)) {
				Harness.at(loc) {
					E.onWorld(WorldReadyEvent())
					for (rep in 0 until 2) for ((hi, h) in hearts.withIndex()) {
						board(area, h)
						for (t in listOf(1, 20)) {
							val c = Harness.chat { out += "eff-run|${loc?.mode}|$area|$ei|$rep|$hi|$t" to Golden.run { E.onTick(TickEvent(t)); "ok" } }
							out += "eff|${loc?.mode}|$area|$ei|$rep|$hi|$t" to "$c"
						}
					}
				}
			}
		}
		board()
		for (s in PlusRiftEffigies.State.entries) out += "estate|$s" to "${s.ordinal}"
		out += "effigy" to PlusRiftEffigies.Effigy().let { "${it.state}|${it.respawnAt}" }

		for (loc in locs) for (on in listOf(false, true)) Harness.withOpts(PlusRiftEnigma.TConfig, mapOf("enabled" to on)) {
			Harness.at(loc) {
				for (m in listOf("SOUL! You unlocked an Enigma Soul!", "You have already found that Enigma Soul!", "SOUL!")) {
					val c = Harness.chat { PlusRiftEnigma.onChat(chatEv(m)) }
					out += "enigma|${loc?.mode}|$on|$m" to "${PlusRiftEnigma.trackingButtons}|$c"
				}
			}
		}

		for (loc in locs) Harness.at(loc) {
			for (m in listOf("PUNCHCARD! You punched [MVP+] Steve and both regained 5ф Rift Time!", "PUNCHCARD! You punched Alex and both regained 1ф!",
				"UH OH! You reached the limit of 20 players you can punch in one session!", "AWKWARD! This player has already been punched by you... somehow!", "x"))
				out += "punch|${loc?.mode}|$m" to Golden.run { PlusRiftPuzzles.onChat(chatEv(m)); PlusRiftPuzzles.onWorld(WorldReadyEvent()); "ok" }
		}
		val n = Golden.verify("batch51-puzzles", out, minCases = 3411)
		assertTrue(n >= 3411)
	}

	@Test
	fun cave() {
		val out = mutableListOf<Pair<String, String>>()
		val H = PlusRiftHighlights2
		val L = PlusRiftLivingCave
		val r = Random(5152)

		H.onWorld(WorldReadyEvent())
		val orbKeys = listOf("motes-orbs", "hide-orb-particles", "agaricus-cap")
		for (loc in locs) for (mask in 0 until 8) Harness.withOpts(PlusRiftHighlights2.TConfig, opts(PlusRiftHighlights2.TConfig, orbKeys, mask)) {
			Harness.at(loc) {
				H.onWorld(WorldReadyEvent())
				for (i in 0 until 40) {
					val at = Vec3(r.nextInt(-3, 4) * 1.5, 70.0 + r.nextInt(0, 3), r.nextInt(-3, 4) * 1.5)
					val p: ParticleOptions = if (i % 5 == 4) ParticleTypes.HEART else ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, -1)
					val ev = particle(p, at)
					out += "orb|${loc?.mode}|$mask|$i" to Golden.run { H.onParticle(ev); "${ev.cancelled} ${H.orbs.orbs.size} " + H.orbs.orbs.joinToString(";") { "${it.x},${it.y},${it.z},${it.count},${it.pickedUp}" } }
				}
				out += "orbtick|${loc?.mode}|$mask" to Golden.run { H.onTick(TickEvent(0)); H.onChat(chatEv("ORB! You picked up 10 Motes")); "${H.orbs.orbs.size}" }
			}
		}
		H.onWorld(WorldReadyEvent())

		val names = listOf(Blocks.AIR, Blocks.LAPIS_BLOCK, Blocks.LAPIS_ORE, Blocks.GLASS, Blocks.DIAMOND_BLOCK, Blocks.STONE)
		val ckeys = listOf("defense-blocks", "living-metal", "snakes")
		for (loc in locs) for (mask in 0 until 8) Harness.withOpts(PlusRiftLivingCave.TConfig, opts(PlusRiftLivingCave.TConfig, ckeys, mask)) {
			Harness.at(loc) {
				L.onWorld(WorldReadyEvent())
				for (i in 0 until 60) {
					val pos = BlockPos(r.nextInt(0, 4), 30 + r.nextInt(0, 2), r.nextInt(0, 4))
					val from = if (i % 3 == 0) Blocks.LAPIS_BLOCK else names.random(r)
					val to = names.random(r)
					out += "cave|${loc?.mode}|$mask|$i" to Golden.run {
						L.onBlock(PlusBlockChangeEvent(pos, from.defaultBlockState(), to.defaultBlockState()))
						L.snakes.snakes.joinToString(";") { s -> s.blocks.joinToString(" ") { "${it.x},${it.y},${it.z}" } }
					}
				}
				out += "cavetick|${loc?.mode}|$mask" to Golden.run { L.onTick(TickEvent(0)); "${L.snakes.snakes.size}" }
			}
		}
		L.onWorld(WorldReadyEvent())
		val n = Golden.verify("batch51-cave", out, minCases = 2438)
		assertTrue(n >= 2438)
	}
}
