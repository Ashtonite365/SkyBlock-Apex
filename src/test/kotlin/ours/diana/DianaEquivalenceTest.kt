
package dev.ashtonite365.apex.test.ours.diana

import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.events.ParticleSpawnEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.features.diana.AncestralSpadeSolver
import dev.ashtonite365.apex.features.diana.DianaWaypoints
import dev.ashtonite365.apex.features.diana.NearbyBurrowsSolver
import dev.ashtonite365.apex.features.diana.toBlockPos
import dev.ashtonite365.apex.test.ApexTestBootstrap
import dev.ashtonite365.apex.test.ours.OursGolden

class DianaEquivalenceTest {
	private fun particle(type: ParticleOptions, x: Double, y: Double, z: Double, ox: Float, oy: Float, oz: Float, count: Int, speed: Float) =
		ParticleSpawnEvent(type, Vec3(x, y, z), Vector3f(ox, oy, oz), false, count, speed)

	private fun snapshot() = NearbyBurrowsSolver.burrows.entries.map { "${it.key.toShortString()}=${it.value}" }.sorted().toString()

	@Test
	fun burrowsAreFoundTheSame() {
		ApexTestBootstrap.bootstrapMinecraft()
		val out = mutableListOf<String>()
		val s = NearbyBurrowsSolver

		@Suppress("UNCHECKED_CAST")
		val options = DianaWaypoints.TConfig.sortedOptions as List<dev.ashtonite365.apex.gui.config.ManagedOption<Any>>
		val previous = options.map { it._actualValue }
		DianaWaypoints.TConfig.explicitDefaultLoad()
		try { run(s, out) } finally { options.forEachIndexed { i, o -> o._actualValue = previous[i] } }
		OursGolden.check("diana-burrows", out)
	}

	private fun run(s: NearbyBurrowsSolver, out: MutableList<String>) {
		s.onSwapWorld(WorldReadyEvent())
		val enchant = { x: Double, y: Double, z: Double -> s.onParticles(particle(ParticleTypes.ENCHANT, x, y, z, 0.5f, 0.4f, 0.5f, 5, 0.05f)) }

		s.onParticles(particle(ParticleTypes.CRIT, 10.5, 71.2, 10.5, 0.5f, 0.1f, 0.5f, 3, 0.01f))
		out += "crit without enchant ${snapshot()}"
		enchant(10.5, 71.2, 10.5)
		out += "after enchant ${snapshot()} enchanted=${s.wasRecentlyEnchanted(BlockPos(10, 70, 10))}"
		s.onParticles(particle(ParticleTypes.CRIT, 10.5, 71.2, 10.5, 0.5f, 0.1f, 0.5f, 3, 0.01f))
		out += "crit ${snapshot()}"
		enchant(-3.2, 64.0, 7.9)
		s.onParticles(particle(ParticleTypes.ENCHANTED_HIT, -3.2, 64.0, 7.9, 0.5f, 0.1f, 0.5f, 4, 0.01f))
		out += "hit ${snapshot()}"
		enchant(0.0, 0.5, -0.5)
		s.onParticles(particle(ParticleTypes.DRIPPING_LAVA, 0.0, 0.5, -0.5, 0.35f, 0.1f, 0.35f, 2, 0.01f))
		out += "lava ${snapshot()}"
		enchant(100.0, 80.0, 100.0)
		for ((label, e) in listOf(
			"count" to particle(ParticleTypes.CRIT, 100.0, 80.0, 100.0, 0.5f, 0.1f, 0.5f, 2, 0.01f),
			"speed" to particle(ParticleTypes.CRIT, 100.0, 80.0, 100.0, 0.5f, 0.1f, 0.5f, 3, 0.02f),
			"oy" to particle(ParticleTypes.ENCHANTED_HIT, 100.0, 80.0, 100.0, 0.5f, 0.2f, 0.5f, 4, 0.01f),
			"spread" to particle(ParticleTypes.CRIT, 100.0, 80.0, 100.0, 0.4f, 0.1f, 0.5f, 3, 0.01f),
			"lava spread" to particle(ParticleTypes.DRIPPING_LAVA, 100.0, 80.0, 100.0, 0.5f, 0.1f, 0.5f, 2, 0.01f),
			"other" to particle(ParticleTypes.FLAME, 100.0, 80.0, 100.0, 0.5f, 0.1f, 0.5f, 3, 0.01f),
		)) {
			s.onParticles(e); out += "miss $label ${snapshot()}"
		}

		s.onParticles(particle(ParticleTypes.ENCHANT, 50.0, 60.0, 50.0, 0.5f, 0.4f, 0.5f, 4, 0.05f))
		s.onParticles(particle(ParticleTypes.CRIT, 50.0, 60.0, 50.0, 0.5f, 0.1f, 0.5f, 3, 0.01f))
		out += "bad marker ${snapshot()}"

		s.onParticles(particle(ParticleTypes.ENCHANTED_HIT, 10.5, 71.2, 10.5, 0.5f, 0.1f, 0.5f, 4, 0.01f))
		out += "reclassified ${snapshot()}"

		s.onBlockClick(BlockPos(10, 70, 10))
		out += "clicked ${snapshot()}"
		for (msg in listOf("Something else", "You dug out a Griffin Burrow! (1/4)", " ☠ You were killed by a Minos Hunter", "You finished the Griffin burrow chain! (4/4)")) {
			s.onChatEvent(ProcessChatEvent(Component.literal(msg), false))
			out += "chat '$msg' ${snapshot()} dug=${s.wasRecentlyDug(BlockPos(10, 70, 10))}"
		}
		enchant(10.5, 71.2, 10.5)
		s.onParticles(particle(ParticleTypes.CRIT, 10.5, 71.2, 10.5, 0.5f, 0.1f, 0.5f, 3, 0.01f))
		out += "dug block ignored ${snapshot()}"
		s.onBlockClick(BlockPos(-4, 63, 7))
		out += "click removes ${snapshot()}"
		s.markAsDug(BlockPos(1, 2, 3)); s.markAsEnchanted(BlockPos(4, 5, 6))
		out += "marks dug=${s.wasRecentlyDug(BlockPos(1, 2, 3))}/${s.wasRecentlyDug(BlockPos(1, 2, 4))} ench=${s.wasRecentlyEnchanted(BlockPos(4, 5, 6))}/${s.wasRecentlyEnchanted(BlockPos(4, 5, 7))}"
		s.onSwapWorld(WorldReadyEvent())
		out += "swapped ${snapshot()} dug=${s.wasRecentlyDug(BlockPos(1, 2, 3))} ench=${s.wasRecentlyEnchanted(BlockPos(4, 5, 6))}"
		s.onChatEvent(ProcessChatEvent(Component.literal("You dug out a Griffin Burrow!"), false))
		out += "chat without click ${snapshot()} dug=${s.wasRecentlyDug(BlockPos(-4, 63, 7))}"
		for (v in listOf(Vec3(0.0, 0.0, 0.0), Vec3(-0.1, 63.99, 7.5), Vec3(-1.0, -1.5, 2.999), Vec3(1e6 + 0.5, -64.0, -1e6 - 0.5)))
			out += "toBlockPos $v -> ${v.toBlockPos().toShortString()}"
		out += "types ${NearbyBurrowsSolver.BurrowType.entries}"

		AncestralSpadeSolver.onSwapWorld(WorldReadyEvent())
		AncestralSpadeSolver.onParticleSpawn(particle(ParticleTypes.DRIPPING_LAVA, 1.0, 2.0, 3.0, 0f, 0f, 0f, 1, 0f))
		out += "spade enabled=${AncestralSpadeSolver.isEnabled()} particles=${AncestralSpadeSolver.particlePositions} guess=${AncestralSpadeSolver.nextGuess} ding=${AncestralSpadeSolver.lastDing.passedTime().isInfinite() || AncestralSpadeSolver.lastDing.passedTime().inWholeDays > 1000}"
		s.onSwapWorld(WorldReadyEvent())
	}
}
