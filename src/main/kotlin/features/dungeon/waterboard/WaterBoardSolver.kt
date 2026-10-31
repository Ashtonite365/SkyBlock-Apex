/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Dungeons Water Board solver (in game part). Written from scratch. Visual only: it highlights the next
 * lever, draws a guide line to it, numbers the lever flips, shows "Water ON" / "Water OFF" on the water lever and
 * previews where the water will go. It never clicks or sends packets. No timers: every instruction follows from the
 * blocks currently in the world (levers, gate blocks, water, wool).
 *
 * Pure parts: WaterBoardModel (board), WaterGrid (0/1/2 + gate letters grid, water flow, crosshair preview),
 * WaterBoardPlanner (plan + guide),
 * WaterBoardReader (finding / reading the board through a BlockView).
 */
package dev.ashtonite365.apex.features.dungeon.waterboard

import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.Vec3
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.commands.onLiteral
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.features.dungeon.PuzzleSolvers
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.render.drawHudLines
import dev.ashtonite365.apex.util.skyblock.DungeonUtil

object WaterBoardSolver {
	private val cfg get() = PuzzleSolvers.TConfig

	private const val SCAN_EVERY = 40
	private const val LEAVE_DISTANCE = 48.0

	private const val REREAD_AFTER = 40

	private var layout: BoardLayout? = null
	private var atlas: FlowAtlas? = null
	private var guide: WaterBoardGuide? = null
	private var doors: DoorTracker? = null
	private val polarity = LeverPolarity()
	private var step: WaterStep? = null
	private var remaining = 0
	private var inconsistent = 0
	private var announcedDone = false
	private var currentConfig = 0

	@Subscribe
	fun onWorld(event: WorldReadyEvent) = reset()

	fun reset() {
		layout = null; atlas = null; guide = null; doors = null; step = null
		remaining = 0; inconsistent = 0; announcedDone = false
	}

	class LevelView(private val level: Level) : BlockView {
		private val pos = BlockPos.MutableBlockPos()
		private fun state(x: Int, y: Int, z: Int): BlockState = level.getBlockState(pos.set(x, y, z))
		override fun id(x: Int, y: Int, z: Int): String = BuiltInRegistries.BLOCK.getKey(state(x, y, z).block).path
		override fun prop(x: Int, y: Int, z: Int, key: String): String? = props(state(x, y, z))[key]
		fun propString(x: Int, y: Int, z: Int) = props(state(x, y, z)).entries.joinToString(",") { "${it.key}=${it.value}" }

		private fun props(s: BlockState): Map<String, String> = buildMap {
			if (s.hasProperty(BlockStateProperties.LEVEL)) put("level", s.getValue(BlockStateProperties.LEVEL).toString())
			if (s.hasProperty(BlockStateProperties.POWERED)) put("powered", s.getValue(BlockStateProperties.POWERED).toString())
			if (s.hasProperty(BlockStateProperties.ATTACH_FACE)) put("face", s.getValue(BlockStateProperties.ATTACH_FACE).serializedName)
			if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) put("facing", s.getValue(BlockStateProperties.HORIZONTAL_FACING).serializedName)
		}
	}

	private fun scanBox(): WaterBoardReader.Box? {
		val p = Client.player ?: return null
		return WaterBoardReader.scanBoxFor(P(p.blockX, p.blockY, p.blockZ))
	}

	private fun lock(found: BoardLayout, view: BlockView) {
		layout = found
		val a = FlowAtlas(found.board)
		atlas = a
		guide = WaterBoardGuide(a, cfg.waterBoardChestPulse)
		val seen = WaterBoardReader.observe(view, found)
		doors = DoorTracker(found.board.doors, seen.gateWoolCount, seen.rowWoolCount)
		inconsistent = 0; announcedDone = false
		runCatching { Apex.logger.info("[WaterBoard] board found: ${found.board.width}x${found.board.height}, gates ${Gate.of(found.board.usedGates).joinToString { it.label }}, doors ${Door.of(found.board.doors).joinToString { it.label }}; ${found.notes.joinToString("; ")}") }
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		if (event.tickCount % 2 != 0) return
		if (!cfg.waterBoardSolver || !DungeonUtil.isInDungeonIsland) { if (layout != null) reset(); return }
		val player = Client.player ?: return
		val level = Client.level ?: return
		val view = LevelView(level)
		val current = layout
		if (current == null) {
			if (event.tickCount % SCAN_EVERY != 0) return
			val box = scanBox() ?: return
			WaterBoardReader.locate(view, box)?.let { lock(it, view) }
			return
		}
		val centre = current.frame.world(current.board.width / 2, 0)
		if (player.position().distanceTo(Vec3(centre.x + 0.5, centre.y + 0.5, centre.z + 0.5)) > LEAVE_DISTANCE) { reset(); return }
		val seen = WaterBoardReader.observe(view, current)
		if (seen.config == null) {
			if (++inconsistent >= REREAD_AFTER) { reset(); return }
		} else inconsistent = 0
		seen.config?.let { currentConfig = it }
		val waterOn = polarity.waterOn(seen.leverPowered, seen.inletWet)
		val tracker = doors ?: return
		tracker.noteTouching(seen.touching, waterOn)
		remaining = tracker.remaining(seen.gateWoolCount, seen.rowWoolCount)
		val g = guide ?: return
		g.chestPulse = cfg.waterBoardChestPulse
		step = g.update(
			WaterObservation(seen.config, waterOn, remaining, seen.touching, seen.water),
			player.position().let { Triple(it.x, it.y, it.z) },
			current.gateLevers.mapValues { it.value.triple() },
		)
		if (step == WaterStep.Done && !announcedDone) {
			announcedDone = true
			Client.showChat(Component.literal("§bWater Board: §aall doors done."))
		}
	}

	fun describe(s: WaterStep?): String = when (s) {
		null -> "§7Reading the board…"
		is WaterStep.Flip -> "§eFlip: " + s.order.joinToString(" §7→ ") { it.code + it.label } +
			if (s.chest) " §7(for the chest)" else " §7(then water to ${Door.names(s.target)}§7)"
		is WaterStep.WaterOn -> if (s.chest) "§bWater ON §7(short pulse for the chest)" else "§bWater ON §7→ ${Door.names(s.target)}"
		is WaterStep.KeepOn -> "§bKeep water ON §7until it reaches ${Door.names(s.waiting)}"
		is WaterStep.WaterOff -> "§cWater OFF " + when (s.reason) {
			OffReason.TARGET_REACHED -> "§7(door reached)"
			OffReason.WRONG_LEVERS -> "§7(levers are wrong for this)"
			OffReason.LEVERS_CHANGED -> "§7(levers changed while on)"
			OffReason.CHEST_PULSE -> "§7(chest pulse done)"
			OffReason.ALL_DONE -> "§7(all doors done)"
		}
		is WaterStep.WaitDrain -> "§7Wait for the water to drain, then water ON → ${Door.names(s.target)}"
		WaterStep.Done -> "§aWater Board done"
		is WaterStep.Stuck -> "§cNo safe route to ${Door.names(s.unreachable)} §7(use /apex waterboard dump)"
		WaterStep.Reading -> "§7Gates moving…"
	}

	private fun Int.rgb() = this and 0xFFFFFF
	private fun P.centre() = Vec3(x + 0.5, y + 0.5, z + 0.5)

	@Subscribe
	fun onWorldRender(event: WorldRenderLastEvent) {
		if (!cfg.waterBoardSolver) return
		val lay = layout ?: return
		val s = step ?: return
		val next = cfg.waterBoardNextColour.getEffectiveColourRGB()
		WorldDraw.drawInWorld(event) {

			val player = Client.player
			val looked = player?.let {
				val eye = it.eyePosition; val dir = it.getViewVector(1f)
				WaterPreview.gateFor(lay.board, WaterPreview.targetCell(lay.frame, lay.board.width, lay.board.height,
					Triple(eye.x, eye.y, eye.z), Triple(dir.x, dir.y, dir.z)))
			}
			val planned = guide?.lastPlan?.pulses?.firstOrNull()?.config?.takeIf { s !is WaterStep.Done }
			val preview = WaterPreview.previewConfig(currentConfig, looked, planned)
			if (cfg.waterBoardPreview && preview != null) {
				val flow = atlas?.flow(preview)
				if (flow != null) for (i in 0 until lay.board.size) if (flow.wet(i) && !lay.board.sink[i])
					block(lay.cellPos(i).let { BlockPos(it.x, it.y, it.z) }, cfg.waterBoardFlowColour.getEffectiveColourRGB())
				if (looked != null) {
					val reach = atlas?.doors(preview) ?: 0
					text(lay.frame.world(lay.board.width / 2, lay.board.height).centre(),
						Component.literal("§7If you flip ${looked.code}${looked.label}§7: " + if (reach == 0) "§8no door" else Door.names(reach)))
				}
			}
			if (looked != null) return@drawInWorld
			fun mark(p: P, label: String, guideLine: Boolean) {
				block(BlockPos(p.x, p.y, p.z), next)
				text(p.centre().add(0.0, 0.8, 0.0), Component.literal(label))
				if (guideLine && cfg.waterBoardGuideLine) tracer(p.centre(), next or 0xFF000000.toInt())
			}
			when (s) {
				is WaterStep.Flip -> s.order.forEachIndexed { n, gate ->
					lay.gateLevers[gate]?.let { mark(it, "§e${n + 1} ${gate.code}${gate.label}", n == 0) }
				}
				is WaterStep.WaterOn, is WaterStep.WaterOff, is WaterStep.KeepOn, is WaterStep.WaitDrain -> {
					val label = when (s) {
						is WaterStep.WaterOn -> "§bWater ON"
						is WaterStep.WaterOff -> "§cWater OFF"
						is WaterStep.KeepOn -> "§bKeep ON"
						else -> "§7Wait"
					}
					lay.waterLever?.let { mark(it, label, s !is WaterStep.KeepOn) }
				}
				is WaterStep.Stuck -> text(lay.frame.world(lay.board.width / 2, lay.board.height / 2).centre(), Component.literal(describe(s)))
				else -> {}
			}

			val target = when (s) {
				is WaterStep.Flip -> s.target
				is WaterStep.WaterOn -> s.target
				is WaterStep.KeepOn -> s.waiting
				is WaterStep.WaitDrain -> s.target
				else -> 0
			}
			for (d in Door.of(target)) lay.doorWool[d]?.firstOrNull()?.let { text(it.centre().add(0.0, 1.0, 0.0), Component.literal("${d.code}▼ ${d.label}")) }
		}
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!cfg.waterBoardSolver || !cfg.waterBoardHud) return
		val lay = layout ?: return
		val tracker = doors ?: return
		val lines = mutableListOf<Component>(Component.literal("§b§lWater Board"), Component.literal(describe(step)))
		val status = Door.of(lay.board.doors).joinToString("  ") { d ->
			when {
				tracker.wanted and d.bit == 0 -> "§8${d.label}"
				remaining and d.bit == 0 -> "${d.code}${d.label} §a✔"
				else -> "${d.code}${d.label}"
			}
		}
		lines += Component.literal(status)
		guide?.lastPlan?.let { if (it.pulses.size > 1) lines += Component.literal("§7Plan: ${it.pulses.size} water pulses, ${it.flips} lever flips") }
		event.drawHudLines(cfg.waterBoardHudPosition, lines)
	}

	@Subscribe
	fun onCommand(event: CommandEvent.SubCommand) {
		event.register(ManagedConfig.Category.DUNGEONS, "waterboard", "Water Board: dump (save the board blocks to a file), reset") {
			onLiteral("dump") { onRun { dumpNow() } }
			onLiteral("reset") { onRun { reset(); Client.showChat(Component.literal("§bWater Board: §7reading the board again.")) } }
		}
	}

	private fun dumpNow() {
		val level = Client.level ?: return
		val box = scanBox() ?: return
		val view = LevelView(level)
		val why = mutableListOf<String>()
		val found = layout ?: WaterBoardReader.locate(view, box, why)
		val header = mutableListOf("SkyBlock Apex water board dump ${LocalDateTime.now()}")
		if (found == null) header += "board not found: " + why.joinToString("; ")
		else {
			val seen = WaterBoardReader.observe(view, found)
			header += "frame ${found.frame}"
			header += "size ${found.board.width}x${found.board.height} entry ${found.board.entry} gates ${Gate.of(found.board.usedGates).joinToString { it.label }} doors ${Door.of(found.board.doors).joinToString { it.label }}"
			header += "levers " + found.gateLevers.entries.joinToString { "${it.key.label}@${it.value}" } + " water@${found.waterLever}"
			header += "config ${seen.config} powered ${seen.leverPowered} inletWet ${seen.inletWet} touching ${Door.of(seen.touching).joinToString { it.label }}"
			header += "gateWool ${seen.gateWoolCount} rowWool ${seen.rowWoolCount} step ${describe(step).replace(dev.ashtonite365.apex.util.SharedRegex.SECTION, "")}"
			header += found.notes.map { "note: $it" }
			header += found.board.grid(seen.config ?: 0, water = { seen.water[it] > 0 }).render().lines().map { "grid $it" }
		}
		val text = WaterBoardReader.dump(view, box, header) { x, y, z -> view.propString(x, y, z) }
		val dir = Apex.DATA_DIR.resolve("waterboard")
		Files.createDirectories(dir)
		val file = dir.resolve("dump-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt")
		Files.writeString(file, text)
		Client.showChat(Component.literal("§bWater Board: §7${if (found == null) "no board found; " else ""}saved ${file.toAbsolutePath()}"))
	}
}
