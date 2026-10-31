// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.dungeon

import net.minecraft.network.chat.Component
import net.minecraft.world.entity.decoration.ArmorStand
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.gui.config.ManagedOption
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.events.UseBlockEvent
import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.util.Alerts
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.skyblock.DungeonUtil
import dev.ashtonite365.apex.util.bareText
import dev.ashtonite365.apex.events.SlotRenderEvents
import dev.ashtonite365.apex.events.HandledScreenKeyPressedEvent
import dev.ashtonite365.apex.events.HandledScreenKeyReleasedEvent
import dev.ashtonite365.apex.events.HandledScreenInputEvent
import dev.ashtonite365.apex.features.kit.dungeon.LeapMenuOptions
import dev.ashtonite365.apex.util.TimeMark
import net.minecraft.world.item.Items
import net.minecraft.world.inventory.Slot
import net.minecraft.world.entity.player.Inventory
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.core.BlockPos
import net.minecraft.tags.BlockTags
import net.minecraft.util.Mth
import io.github.notenoughupdates.moulconfig.ChromaColour
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.joml.Vector2i
import dev.ashtonite365.apex.features.kit.puzzles.StarredMobs
import dev.ashtonite365.apex.features.dungeon.secrets.DungeonSecrets

object PuzzleSolvers {
	val identifier: String
		get() = "puzzle-solvers"

	@Config
	object TConfig : ManagedConfig(category = Category.DUNGEONS, name = identifier) {
		val threeWeirdos by riskyToggle("three-weirdos", RiskLevel.GREY_AREA)
		val threeWeirdosNpc by toggle("three-weirdos-npc") { true }
		val threeWeirdosWrongRed by toggle("three-weirdos-wrong-red") { false }
		val blazeSolver by toggle("blaze-solver") { true }
		val blazeCurrentColour by colour("blaze-current-colour") { ChromaColour.fromStaticRGB(0x40, 0xFF, 0x40, 0x80) }
		val blazeNextColour by colour("blaze-next-colour") { ChromaColour.fromStaticRGB(0x55, 0xC8, 0xFF, 0xFF) }
		val blazeDoneColour by colour("blaze-done-colour") { ChromaColour.fromStaticRGB(0x55, 0xFF, 0x55, 0xFF) }

		val blazeTargetLine by toggle("blaze-target-line") { true }
		val blazeTargetLineColour by colour("blaze-target-line-colour") { ChromaColour.fromStaticRGB(0x40, 0xFF, 0x40, 0xFF) }
		val blazeNextLine by toggle("blaze-next-line") { true }
		val blazeNextLineColour by colour("blaze-next-line-colour") { ChromaColour.fromStaticRGB(0x55, 0xC8, 0xFF, 0xFF) }

		val blazeBlockedLineColour by colour("blaze-blocked-line-colour") { ChromaColour.fromStaticRGB(0xFF, 0x20, 0x20, 0xFF) }
		val creeperBeamsSolver by toggle("creeper-beams-solver") { true }
		val creeperBeamsColour1 by colour("creeper-beams-colour-1") { ChromaColour.fromStaticRGB(0xFF, 0xAA, 0x00, 0x60) }
		val creeperBeamsColour2 by colour("creeper-beams-colour-2") { ChromaColour.fromStaticRGB(0x55, 0xFF, 0x55, 0x60) }
		val creeperBeamsColour3 by colour("creeper-beams-colour-3") { ChromaColour.fromStaticRGB(0xFF, 0x55, 0xFF, 0x60) }
		val creeperBeamsColour4 by colour("creeper-beams-colour-4") { ChromaColour.fromStaticRGB(0x55, 0xFF, 0xFF, 0x60) }
		val boulder by riskyToggle("boulder", RiskLevel.GREY_AREA)
		val boulderShowAll by toggle("boulder-show-all") { false }
		val boulderPath by toggle("boulder-path") { true }
		val boulderPathColour by colour("boulder-path-colour") { ChromaColour.fromStaticRGB(0x40, 0xFF, 0x40, 0xFF) }
		val waterBoardSolver by toggle("water-board-solver") { true }
		val waterBoardGuideLine by toggle("water-board-guide-line") { true }
		val waterBoardPreview by toggle("water-board-preview") { true }
		val waterBoardHud by toggle("water-board-steps-hud") { true }
		val waterBoardHudPosition by position("water-board-hud-position", 160, 40) { Vector2i(4, 200) }
		val waterBoardNextColour by colour("water-board-next-colour") { ChromaColour.fromStaticRGB(0x55, 0xFF, 0x55, 0xFF) }
		val waterBoardFlowColour by colour("water-board-flow-colour") { ChromaColour.fromStaticRGB(0x40, 0x80, 0xFF, 0x50) }
		val waterBoardChestPulse by toggle("water-board-chest-pulse") { true }
		val teleportMaze by riskyToggle("teleport-maze", RiskLevel.GREY_AREA)
		val teleportMazeColour by colour("teleport-maze-colour") { ChromaColour.fromStaticRGB(0xFF, 0x50, 0x50, 0x70) }
		val teleportMazeGuide by toggle("teleport-maze-guide") { true }
		val teleportMazeGuideColour by colour("teleport-maze-guide-colour") { ChromaColour.fromStaticRGB(0x40, 0xFF, 0x40, 0xFF) }
		val teleportMazeGuideLine by toggle("teleport-maze-guide-line") { true }
		val teleportMazePadLinks by toggle("teleport-maze-pad-links") { true }
		val livid by riskyToggle("livid", RiskLevel.GREY_AREA)
		val lividColour by colour("livid-colour") { ChromaColour.fromStaticRGB(0x40, 0xFF, 0x40, 0x60) }
		val lividOwnColour by toggle("livid-own-colour") { false }
		val lividWhileBlind by toggle("livid-while-blind") { false }
		val lividLegacyGlass by toggle("livid-legacy-glass") { false }
		val starredMobs by riskyToggle("starred-mobs", RiskLevel.RISKY)
		val highlightDeathmites by riskyToggle("highlight-deathmites", RiskLevel.GREY_AREA)
		val spiritLeapOverlay by riskyToggle("spirit-leap-overlay", RiskLevel.GREY_AREA)
		val spiritLeapKeybinds by riskyToggle("spirit-leap-keybinds", RiskLevel.RISKY)
		val leapKeys = (1..4).map { keyBindingWithDefaultUnbound("spirit-leap-key-$it") }
		val leapAlert by toggle("leap-alert") { false }
		val ticTacToe by riskyToggle("tic-tac-toe", RiskLevel.GREY_AREA)
		val ticTacToeColour by colour("tic-tac-toe-colour") { ChromaColour.fromStaticRGB(64, 255, 64, 110) }
		val ticTacToeKey by keyBindingWithDefaultUnbound("tic-tac-toe-key")
		val quiz by riskyToggle("quiz", RiskLevel.GREY_AREA)
		val iceFillSolver by toggle("ice-fill-solver") { true }
		val iceFillBundled by toggle("ice-fill-bundled") { false }
		val iceFillOptimal by toggle("ice-fill-optimal") { false }
		val iceFillColour by colour("ice-fill-colour") { ChromaColour.fromStaticRGB(0x40, 0xE0, 0xFF, 255) }
		val iceFillNextColour by colour("ice-fill-next-colour") { ChromaColour.fromStaticRGB(0xFF, 0xD0, 0x40, 0x90) }
		val icePathSolver by toggle("ice-path-solver") { true }
		val icePathColour by colour("ice-path-colour") { ChromaColour.fromStaticRGB(0xFF, 0x50, 0x50, 0xFF) }
		val icePathNextColour by colour("ice-path-next-colour") { ChromaColour.fromStaticRGB(0x40, 0xFF, 0x40, 0xFF) }
		val puzzleHud by toggle("puzzle-hud") { false }
		val puzzleHudNames by toggle("puzzle-hud-names") { true }
		val puzzleHudPosition by position("puzzle-hud-position", 120, 60) { Vector2i(4, 140) }
		val starredColour by colour("starred-colour") { ChromaColour.fromStaticRGB(0xFF, 0xD0, 0x00, 0x50) }
		val starredOutline by toggle("starred-outline") { false }
		val starredStrict by toggle("starred-strict") { false }
		val hideNonStarredNames by toggle("hide-non-starred-names") { false }
		val starredRoomOnly by toggle("starred-room-only") { false }
		val lostAdventurer by riskyToggle("lost-adventurer", RiskLevel.RISKY)
		val lostAdventurerColour by colour("lost-adventurer-colour") { ChromaColour.fromStaticRGB(0x00, 0xFF, 0xAA, 0x60) }
		val leapSort by choice("leap-sort") { LeapSort.SLOT }
		val leapQuadrants by riskyToggle("leap-quadrants", RiskLevel.GREY_AREA)
		val leapScale by integer("leap-scale", 50, 200) { 100 }
		val leapClassKeys = listOf("archer", "berserk", "healer", "mage", "tank").map { keyBindingWithDefaultUnbound("spirit-leap-key-$it") }
		val leapOnRelease by toggle("leap-on-release") { false }
		@Suppress("UNCHECKED_CAST")
		val quizBox by riskySubToggle("quiz-box", RiskLevel.GREY_AREA, allOptions.getValue("quiz") as ManagedOption<Boolean>) { true }
		val quizBoxColour by colour("quiz-box-colour") { ChromaColour.fromStaticRGB(0x40, 0xFF, 0x40, 0x80) }
		val quizTimer by riskyToggle("quiz-timer", RiskLevel.GREY_AREA)
		val quizTimerPosition by position("quiz-timer-position", 80, 12) { Vector2i(4, 120) }
		val puzzleHudHideInBoss by toggle("puzzle-hud-hide-in-boss") { true }

		val leapClassKeybinds: Boolean get() = spiritLeapKeybinds

		override fun migrate(saved: JsonObject) =
			listOf(::foldLeapClassKeybinds, ::foldOldBlaze, ::foldOldIceFill, CreeperBeamsLogic::foldOldToggle, ::foldOldWaterBoard)
				.fold(saved) { acc, step -> step(acc) }
	}

	const val OLD_LEAP_CLASS_KEY = "leap-class-keybinds"
	const val OLD_BLAZE_KEY = "blaze"
	const val OLD_BLAZE_HIGHEST_KEY = "blaze-highest-first"
	const val OLD_ICE_FILL_KEY = "ice-fill"
	val OLD_WATER_BOARD_KEYS = setOf(
		"water-board", "water-board-optimized", "water-board-mode", "water-board-tracer", "water-board-tracer-first",
		"water-board-tracer-second", "water-board-fix-wrong", "water-board-hud", "water-board-reset-key", "water-board-reset",
	)

	private fun JsonObject.isTrue(key: String) = (this[key] as? JsonPrimitive)?.content == "true"

	private fun retire(saved: JsonObject, retired: Collection<String>, carry: String?, target: String?, onlyIfAbsent: Boolean): JsonObject {
		if (retired.none { it in saved }) return saved
		val turnOn = carry != null && saved.isTrue(carry)
		val kept = LinkedHashMap(saved)
		retired.forEach { kept.remove(it) }
		if (turnOn && target != null && !(onlyIfAbsent && target in kept)) kept[target] = JsonPrimitive(true)
		return JsonObject(kept)
	}

	fun foldLeapClassKeybinds(saved: JsonObject) =
		retire(saved, listOf(OLD_LEAP_CLASS_KEY), OLD_LEAP_CLASS_KEY, "spirit-leap-keybinds", onlyIfAbsent = false)

	fun foldOldBlaze(saved: JsonObject) =
		retire(saved, listOf(OLD_BLAZE_KEY, OLD_BLAZE_HIGHEST_KEY), OLD_BLAZE_KEY, "blaze-solver", onlyIfAbsent = true)

	fun foldOldIceFill(saved: JsonObject) =
		retire(saved, listOf(OLD_ICE_FILL_KEY), OLD_ICE_FILL_KEY, "ice-fill-solver", onlyIfAbsent = true)

	fun foldOldWaterBoard(saved: JsonObject): JsonObject {
		if (OLD_WATER_BOARD_KEYS.none { it in saved }) return saved
		return JsonObject(saved.filterKeys { it !in OLD_WATER_BOARD_KEYS })
	}

	enum class LeapSort(val id: String) : net.minecraft.util.StringRepresentable {
		SLOT("slot"), NAME("name");
		override fun getSerializedName() = id
	}

	private object Weirdos {
		val log = org.slf4j.LoggerFactory.getLogger("Apex/ApexWeirdos")
		val heard = mutableMapOf<String, WeirdosLogic.Line>()
		var rewardChest: BlockPos? = null
		var rewardNpc: Entity? = null
		val wrongChests = mutableListOf<BlockPos>()

		fun clear() { heard.clear(); rewardChest = null; rewardNpc = null; wrongChests.clear() }

		fun npcNamed(name: String): Entity? {
			val level = Client.level ?: return null
			val me = Client.player ?: return null
			return level.entitiesForRendering()
				.filter { it.distanceToSqr(me) < 32.0 * 32.0 && it.customName?.bareText?.let(WeirdosLogic::strip) == name }
				.minByOrNull { it.distanceToSqr(me) }
		}

		fun chestBeside(npc: Entity): BlockPos? {
			val level = Client.level ?: return null
			val at = npc.blockPosition()
			return sequenceOf(at.north(), at.south(), at.east(), at.west()).firstOrNull { level.getBlockState(it).`is`(Blocks.CHEST) }
		}
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!TConfig.threeWeirdos || !DungeonUtil.isInDungeonIsland) return
		val raw = event.bareText
		if (WeirdosLogic.isSolved(raw)) return Weirdos.clear()
		val line = WeirdosLogic.parse(raw) ?: return
		val solution = line.kind == WeirdosLogic.Kind.SOLUTION
		if (line.kind == WeirdosLogic.Kind.UNKNOWN && Weirdos.heard.isEmpty() && Weirdos.npcNamed(line.npc) == null) return
		Weirdos.heard[line.npc] = line
		val chosen = WeirdosLogic.correctNpc(Weirdos.heard.values.toList())
		Weirdos.log.info("[ApexWeirdos] chat='{}' kind={} chosen={}", WeirdosLogic.strip(raw), line.kind, chosen)
		if (!solution) {
			val chest = Weirdos.npcNamed(line.npc)?.let(Weirdos::chestBeside) ?: return
			if (chest !in Weirdos.wrongChests) Weirdos.wrongChests += chest
			return
		}
		val npc = Weirdos.npcNamed(line.npc)
		val chest = npc?.let(Weirdos::chestBeside)
		when {
			npc == null -> Weirdos.log.info("[ApexWeirdos] mapping failed: no entity named '{}' within 32 blocks", line.npc)
			chest == null -> Weirdos.log.info("[ApexWeirdos] mapping failed: no chest beside '{}' at {}", line.npc, npc.blockPosition())
		}
		Weirdos.rewardNpc = npc
		Weirdos.rewardChest = chest
	}

	@Subscribe
	fun onWeirdosWorld(event: WorldReadyEvent) = Weirdos.clear()

	@Subscribe
	fun onWeirdosUse(event: UseBlockEvent) {
		if (event.hitResult.blockPos == Weirdos.rewardChest) Weirdos.clear()
	}

	@Subscribe
	fun onWeirdosRender(event: WorldRenderLastEvent) {
		if (!TConfig.threeWeirdos || !DungeonUtil.isInDungeonIsland) return
		val chest = Weirdos.rewardChest
		val npc = Weirdos.rewardNpc
		if (chest == null && npc == null) return
		WorldDraw.drawInWorld(event) {
			if (chest != null) block(chest, RIGHT_TINT)
			if (TConfig.threeWeirdosNpc && npc != null) box(npc.boundingBox, RIGHT_TINT)
			if (TConfig.threeWeirdosWrongRed) for (w in Weirdos.wrongChests) if (w != chest) block(w, WRONG_TINT)
		}
	}

	private const val RIGHT_TINT = 0x6040FF40
	private const val WRONG_TINT = 0x60FF4040

	private object BoulderRoom {
		val log = org.slf4j.LoggerFactory.getLogger("Apex/Boulder")
		var solvedCorner: BlockPos? = null
		var pushes: MutableList<Boulder.Step> = mutableListOf()

		var floorY: Int? = null
		var lastStage: String? = null
		var warnedLayout: String? = null

		val walker = BoulderPath.Tracker()
		var chest: BlockPos? = null
		var chestFor: BlockPos? = null
		var lastPathNote: String? = null

		fun forgetSolve() { solvedCorner = null; pushes = mutableListOf(); floorY = null; lastStage = null }

		fun stage(text: String) {
			if (text == lastStage) return
			lastStage = text
			log.info("[Boulder] {}", text)
		}

		fun pathNote(text: String) {
			if (text == lastPathNote) return
			lastPathNote = text
			log.info("[BoulderPath] {}", text)
		}

		fun inCell(anchor: BlockPos, x: Int, z: Int) =
			RoomCornerFinder.cell(anchor.x) == RoomCornerFinder.cell(x) && RoomCornerFinder.cell(anchor.z) == RoomCornerFinder.cell(z)

		fun rewardChest(level: Level, corner: BlockPos, feetY: Int): BlockPos? {
			val midX = RoomCornerFinder.centre(RoomCornerFinder.cell(corner.x))
			val midZ = RoomCornerFinder.centre(RoomCornerFinder.cell(corner.z))
			val probe = BlockPos.MutableBlockPos()
			for (y in feetY..feetY + 1) for (x in midX - 16..midX + 16) for (z in midZ - 16..midZ + 16) {
				if (!inCell(corner, x, z)) continue
				val state = level.getBlockState(probe.set(x, y, z))
				if (state.`is`(Blocks.CHEST) || state.`is`(Blocks.TRAPPED_CHEST)) return BlockPos(x, feetY, z)
			}
			return null
		}
	}

	@Subscribe
	fun onBoulderTick(event: TickEvent) {
		if (event.tickCount % 10 != 0) return
		if (!TConfig.boulder || !DungeonUtil.isInDungeonIsland) return BoulderRoom.forgetSolve()
		val level = Client.level ?: return
		val corner = PuzzleRoom.cornerFor("Boulder") ?: return BoulderRoom.stage("no room frame (blue terracotta corner not found)")
		if (corner == BoulderRoom.solvedCorner) return
		val isAir = { p: BlockPos -> level.getBlockState(p).isAir }
		val isPlank = { p: BlockPos -> level.getBlockState(p).`is`(BlockTags.PLANKS) }
		when (val scan = Boulder.scan(corner, Boulder.table, isAir, isPlank)) {
			Boulder.Scan.NotBoulder -> BoulderRoom.stage("room corner=$corner: not the Boulder room")
			is Boulder.Scan.Blocked -> {
				BoulderRoom.stage("room detected corner=$corner grid=${scan.key}: no solution")
				if (BoulderRoom.warnedLayout == scan.key) return
				BoulderRoom.warnedLayout = scan.key
				Alerts.show(Component.literal("§cBoulder: blocked or unknown layout, reset the room"))
			}
			is Boulder.Scan.Solved -> {
				val feetY = BoulderPath.feetLevel(scan.steps.map { it.render.x to it.render.z }, isPlank)
				BoulderRoom.stage("room detected corner=$corner grid=${scan.key}: solution len=${scan.steps.size} feetY=${feetY ?: "?"}")
				BoulderRoom.solvedCorner = corner
				BoulderRoom.floorY = feetY
				BoulderRoom.pushes = scan.steps.toMutableList()
				BoulderRoom.warnedLayout = null
			}
		}
	}

	@Subscribe
	fun onBoulderUse(event: UseBlockEvent) {
		if (BoulderRoom.pushes.isEmpty() || event.hand != net.minecraft.world.InteractionHand.MAIN_HAND) return
		val hit = event.hitResult.blockPos
		val i = BoulderRoom.pushes.indexOfFirst { it.click == hit }
		if (i >= 0) BoulderRoom.pushes.removeAt(i)
	}

	@Subscribe
	fun onBoulderRender(event: WorldRenderLastEvent) {
		val steps = BoulderRoom.pushes
		if (steps.isEmpty()) return
		val shown = if (TConfig.boulderShowAll) steps else steps.subList(0, 1)
		WorldDraw.drawInWorld(event) { shown.forEach { block(it.render, RIGHT_TINT) } }
	}

	@Subscribe
	fun onBoulderPathTick(event: TickEvent) {
		if (event.tickCount % 2 != 0) return
		val corner = BoulderRoom.solvedCorner
		val level = Client.level
		val player = Client.player
		if (corner == null || level == null || player == null || !BoulderPath.enabled(TConfig.boulder, TConfig.boulderPath)) {
			BoulderRoom.walker.clear()
			if (corner == null) { BoulderRoom.chest = null; BoulderRoom.chestFor = null; BoulderRoom.lastPathNote = null }
			return
		}
		val px = Mth.floor(player.x)
		val pz = Mth.floor(player.z)
		if (!BoulderRoom.inCell(corner, px, pz)) return BoulderRoom.walker.clear()
		val feetY = BoulderPath.resolveFeetY(BoulderRoom.floorY, player.blockPosition().y, player.onGround())
		val rescan = BoulderRoom.chestFor != corner || (BoulderRoom.chest == null && event.tickCount % 20 == 0)
		if (rescan) {
			BoulderRoom.chestFor = corner
			BoulderRoom.chest = BoulderRoom.rewardChest(level, corner, feetY)
		}
		val walkable = BoulderPath.Walkable { x, z ->
			BoulderRoom.inCell(corner, x, z) && BoulderPath.standable(x, z, feetY) { p -> !level.getBlockState(p).getCollisionShape(level, p).isEmpty }
		}

		val route = BoulderRoom.walker.update(px, pz, BoulderRoom.pushes.toList(), BoulderRoom.chest, walkable, TConfig.boulder, TConfig.boulderPath,
			force = event.tickCount % 10 == 0, feetY = feetY)
		BoulderRoom.pathNote(when {
			route != null -> "route to ${if (route.toChest) "chest" else "box"} ${route.target.box.toShortString()} feetY=$feetY (${route.tiles.size} tiles)"
			BoulderPath.startTile(px, pz, walkable, feetY) == null -> "no route: player tile $px,$pz not standable at feetY=$feetY"
			BoulderRoom.pushes.isEmpty() && BoulderRoom.chest == null -> "no route: all buttons pressed, reward chest not found (feetY=$feetY)"
			else -> "no route: target not reachable (feetY=$feetY, ${BoulderRoom.pushes.size} steps left)"
		})
	}

	@Subscribe
	fun onBoulderPathRender(event: WorldRenderLastEvent) {
		if (!BoulderPath.enabled(TConfig.boulder, TConfig.boulderPath)) return
		val points = BoulderRoom.walker.route?.points ?: return
		if (points.size < 2) return
		val argb = TConfig.boulderPathColour.getEffectiveColourRGB() or OPAQUE
		WorldDraw.drawInWorld(event) { line(points, color = argb, lineWidth = 3F) }
	}

	private val OPAQUE = 0xFF000000.toInt()

	private object Starred {
		var boxed: List<LivingEntity> = emptyList()

		var lostIds: Set<Int> = emptySet()

		var pairing: Map<Int, Int> = emptyMap()
		var loggedSignature = ""
		var loggedAt = TimeMark.farPast()
	}

	fun inStarredRoom(roomOnly: Boolean, px: Double, pz: Double, ex: Double, ez: Double) =
		if (roomOnly) BloodMobEsp.sameRoom(px, pz, ex, ez) else true

	@Subscribe
	fun onStarredTick(event: TickEvent) {
		if (event.tickCount % 5 != 0) return
		Starred.boxed = emptyList()
		Starred.lostIds = emptySet()
		val starOn = TConfig.starredMobs
		val lostOn = TConfig.lostAdventurer
		val anything = starOn || lostOn || TConfig.hideNonStarredNames
		if (!anything || !DungeonUtil.isInDungeonIsland) { Starred.pairing = emptyMap(); return }
		val player = Client.player ?: return
		val level = Client.level ?: return
		val tags = level.getEntitiesOfClass(ArmorStand::class.java, player.boundingBox.inflate(40.0)) { it.hasCustomName() }
		fun ArmorStand.tagText() = customName!!.bareText
		if (TConfig.hideNonStarredNames) {
			tags.filter { it.isInvisible && StarredMobs.isDungeonMobName(it.tagText()) && !StarredMobs.isStarred(it.tagText(), true) }
				.forEach { it.remove(Entity.RemovalReason.DISCARDED) }
		}
		if (!starOn && !lostOn) { Starred.pairing = emptyMap(); return }

		val marked = tags.filter { it.isAlive && !DungeonSecrets.marksMiniboss(it.tagText()) }
			.mapNotNull { tag -> StarredMobs.highlightKind(tag.tagText(), starOn, TConfig.starredStrict, lostOn)?.let { tag to it } }
		val markedTags = marked.map { it.first }
		if (markedTags.isEmpty() && !lostOn) { Starred.pairing = emptyMap(); return }
		val bodies = level.getEntitiesOfClass(LivingEntity::class.java, player.boundingBox.inflate(43.0)) { it !is ArmorStand && it.isAlive }
		val paired = TagBodyPairing.pairEntities(markedTags, bodies, player, Starred.pairing).filterValues { !DungeonSecrets.marksBody(it.id) }
		Starred.pairing = paired.mapValues { it.value.id }

		val namedLost = if (!lostOn) emptyList() else bodies.filter { b ->
			val name = (b.customName ?: (b as? Player)?.name)?.bareText ?: ""
			StarredMobs.isLostAdventurer(name) && TagBodyPairing.eligible(TagBodyPairing.bodyOf(b, player)) && !DungeonSecrets.marksBody(b.id)
		}
		val roomOnly = TConfig.starredRoomOnly
		Starred.boxed = (paired.values + namedLost).distinct().filter { inStarredRoom(roomOnly, player.x, player.z, it.x, it.z) }
		Starred.lostIds = marked.filter { it.second == StarredMobs.Kind.LOST_ADVENTURER }.mapNotNull { paired[it.first.id]?.id }.toSet() +
			namedLost.map { it.id }
		logPairing(markedTags, paired, player)
	}

	private fun logPairing(tags: List<ArmorStand>, paired: Map<Int, LivingEntity>, player: Player) {
		val signature = TagBodyPairing.signature(tags.map(TagBodyPairing::tagOf), paired.mapValues { it.value.id })
		if (signature == Starred.loggedSignature || Starred.loggedAt.passedTime() < kotlin.time.Duration.parse("1s")) return
		Starred.loggedSignature = signature
		Starred.loggedAt = TimeMark.now()
		val log = dev.ashtonite365.apex.Apex.logger
		for (tag in tags) {
			val name = tag.customName?.bareText
			val body = paired[tag.id]
			if (body == null) {
				log.info("[ApexStarBox] NO_BODY tag id=${tag.id} name=\"$name\" pos=${"%.2f,%.2f,%.2f".format(tag.x, tag.y, tag.z)}")
				continue
			}
			val kind = TagBodyPairing.kindOf(body, player)
			val dy = tag.y - (body.y + body.bbHeight)
			val dxz = Math.hypot(body.x - tag.x, body.z - tag.z)
			log.info("[ApexStarBox] tag id=${tag.id} name=\"$name\" -> body id=${body.id} class=${body.javaClass.simpleName} kind=$kind " +
				"dY=${"%.2f".format(dy)} dXZ=${"%.2f".format(dxz)} idOrder=${tag.id == body.id + 1} " +
				"dist=${"%.1f".format(body.distanceTo(player))} sameRoom=${BloodMobEsp.sameRoom(player.x, player.z, body.x, body.z)}")
		}
	}

	@Subscribe
	fun onStarredRender(event: WorldRenderLastEvent) {
		val bodies = Starred.boxed
		if (bodies.isEmpty()) return
		val starOn = TConfig.starredMobs
		val lostOn = TConfig.lostAdventurer
		if (!starOn && !lostOn) return
		val starArgb = TConfig.starredColour.getEffectiveColourRGB()
		val lostArgb = TConfig.lostAdventurerColour.getEffectiveColourRGB()
		val lost = Starred.lostIds
		WorldDraw.drawInWorld(event) {

			for (body in bodies) {
				if (!body.isAlive || body.isInvisible) continue
				val isLost = body.id in lost
				if (if (isLost) !TConfig.lostAdventurer else !TConfig.starredMobs) continue
				val argb = if (isLost) lostArgb else starArgb
				if (TConfig.starredOutline) StarredMobs.boxEdges(body.boundingBox).forEach { (a, b) -> line(a, b, color = argb or OPAQUE, lineWidth = 3F) }
				else box(body.boundingBox, argb)
			}
		}
	}

	private val leapTitles = setOf("Spirit Leap", "Infinileap")

	internal fun leapHeads(screen: AbstractContainerScreen<*>): List<Slot>? {
		if (screen.title.bareText.trim() !in leapTitles) return null
		val heads = screen.menu.slots.asSequence()
			.filter { it.container !is Inventory && it.item.`is`(Items.PLAYER_HEAD) }
			.take(4).toList()
		if (TConfig.leapSort != LeapSort.NAME) return heads
		return heads.sortedBy { it.item.hoverName.bareText.lowercase() }
	}

	@Subscribe
	fun onLeapSlot(event: SlotRenderEvents.After) {
		if (!TConfig.spiritLeapOverlay) return
		val screen = Client.screen as? AbstractContainerScreen<*> ?: return
		val rank = leapHeads(screen)?.indexOf(event.slot) ?: return
		if (rank >= 0) event.context.text(Client.font, "§e${rank + 1}", event.slot.x + 11, event.slot.y + 9, -1, true)
	}

	private fun leapTarget(event: HandledScreenInputEvent, matches: (dev.ashtonite365.apex.keybindings.SavedKeyBinding) -> Boolean): Pair<AbstractContainerScreen<*>, Slot>? {
		val screen = event.screen as? AbstractContainerScreen<*> ?: return null
		val heads = leapHeads(screen) ?: return null
		val key = TConfig.leapKeys.indexOfFirst { it.get().isBound && matches(it.get()) }
		if (key < 0) return null
		return screen to (heads.getOrNull(key) ?: return null)
	}

	@Subscribe
	fun onLeapKey(event: HandledScreenKeyPressedEvent) {
		if (!TConfig.spiritLeapKeybinds || event.isRepeat) return
		val (screen, slot) = leapTarget(event) { event.matches(it) } ?: return
		event.cancel()
		if (!TConfig.leapOnRelease) LeapMenuOptions.clickLeapSlot(screen, slot)
	}

	@Subscribe
	fun onLeapKeyRelease(event: HandledScreenKeyReleasedEvent) {
		if (!TConfig.spiritLeapKeybinds || !TConfig.leapOnRelease) return
		val (screen, slot) = leapTarget(event) { event.matches(it) } ?: return
		event.cancel()
		LeapMenuOptions.clickLeapSlot(screen, slot)
	}

	private val teleportedTo = Regex("You have teleported to (\\w{2,16})!")

	@Subscribe
	fun onLeapChat(event: ProcessChatEvent) {
		if (!TConfig.leapAlert || !DungeonUtil.isInDungeonIsland) return
		val who = teleportedTo.matchEntire(event.bareText.trim())?.groupValues?.get(1) ?: return
		Alerts.show(Component.literal("§dLeapt to §e$who"))
	}
}
