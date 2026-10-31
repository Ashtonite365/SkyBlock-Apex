// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.dungeon.secrets

import dev.ashtonite365.apex.util.render.WaypointType
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.ambient.Bat
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.SkullBlockEntity
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ActionBarEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.ScreenChangeEvent
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.events.UseBlockEvent
import dev.ashtonite365.apex.events.WorldActionPacketEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.features.dungeon.DungeonRoomTracker
import dev.ashtonite365.apex.features.dungeon.RoomCornerFinder
import dev.ashtonite365.apex.features.world.BlockPathPlanner
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.mc.TabListReader
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.hypixelIdText
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.skyblock.DungeonSecretCounts
import dev.ashtonite365.apex.util.skyblock.DungeonUtil
import dev.ashtonite365.apex.util.bareText

object SecretWayfinderLogic {
	enum class Kind { CHEST, LEVER, ESSENCE, ITEM, BAT }

	data class Secret(val kind: Kind, val x: Double, val y: Double, val z: Double, val key: String)

	const val WITHER_ESSENCE_OWNER = "e0f3e929-869e-3dca-9504-54c666ee6f23"

	fun isWitherEssence(ownerId: String?) = ownerId != null && ownerId.equals(WITHER_ESSENCE_OWNER, ignoreCase = true)

	val SECRET_ITEM_IDS = setOf(
		"DECOY", "INFLATABLE_JERRY", "SPIRIT_LEAP", "TRAINING_WEIGHTS", "DUNGEON_TRAP", "DEFUSE_KIT",
		"DUNGEON_CHEST_KEY", "TREASURE_TALISMAN", "REVIVE_STONE", "ARCHITECT_FIRST_DRAFT",
	)

	val SECRET_ITEM_NAMES = setOf(
		"Decoy", "Inflatable Jerry", "Spirit Leap", "Training Weights", "Trap", "Defuse Kit", "Dungeon Chest Key",
		"Treasure Talisman", "Revive Stone", "Architect's First Draft",
	)

	fun isSecretItem(id: String?, name: String?): Boolean =
		(id != null && id in SECRET_ITEM_IDS) || (name != null && name.trim() in SECRET_ITEM_NAMES)

	fun blockKey(kind: Kind, x: Int, y: Int, z: Int) = "$kind@$x,$y,$z"

	fun inCells(x: Int, z: Int, cells: Set<Pair<Int, Int>>) = (RoomCornerFinder.cell(x) to RoomCornerFinder.cell(z)) in cells

	fun nearestUnfound(secrets: List<Secret>, found: Set<String>, px: Double, py: Double, pz: Double): Secret? =
		secrets.filter { it.key !in found }.minByOrNull { d2(it, px, py, pz) }

	fun d2(s: Secret, px: Double, py: Double, pz: Double) =
		(s.x - px) * (s.x - px) + (s.y - py) * (s.y - py) + (s.z - pz) * (s.z - pz)

	fun roomDone(count: DungeonSecretCounts.RoomCount?) = count != null && (count.total == 0 || count.done)

	fun pickTarget(secrets: List<Secret>, found: Set<String>, locked: Set<String>, px: Double, py: Double, pz: Double): Secret? {
		val unfound = secrets.filter { it.key !in found }
		val nearest = unfound.minByOrNull { d2(it, px, py, pz) } ?: return null
		val lever = unfound.filter { it.kind == Kind.LEVER }.minByOrNull { d2(it, px, py, pz) }
		if (nearest.kind == Kind.CHEST && lever != null) return lever
		if (nearest.key in locked) return unfound.filter { it.key !in locked }.minByOrNull { d2(it, px, py, pz) } ?: nearest
		return nearest
	}

	class LockedChests(private val windowMs: Long = 2000) {
		private val locked = LinkedHashSet<String>()
		private var lastClick: String? = null
		private var clickAt = Long.MIN_VALUE / 2
		private val leverPowered = HashMap<String, Boolean>()
		val keys: Set<String> get() = locked

		fun onChestClick(key: String, nowMs: Long) { lastClick = key; clickAt = nowMs }

		fun onChat(line: String, nowMs: Long): String? {
			if (line.trim() != LOCKED_MESSAGE) return null
			val k = lastClick ?: return null
			if (nowMs - clickAt > windowMs) return null
			locked += k
			return k
		}

		fun onLevers(levers: Map<String, Boolean>, chestsInRoom: Set<String>) {
			var flipped = false
			for ((k, on) in levers) {
				if (on && leverPowered[k] == false) flipped = true
				leverPowered[k] = on
			}
			if (flipped) locked.removeAll(chestsInRoom)
		}

		fun reset() { locked.clear(); lastClick = null; leverPowered.clear() }

		companion object { const val LOCKED_MESSAGE = "That chest is locked!" }
	}

	class Tracker {
		private val found = HashSet<String>()
		private var tabFound: Int? = null
		val foundKeys: Set<String> get() = found

		fun markFound(key: String) = found.add(key)

		fun onTabCount(count: Int?, secrets: List<Secret>, px: Double, py: Double, pz: Double): List<String> {
			if (count == null) return emptyList()
			val prev = tabFound
			tabFound = count
			if (prev == null || count <= prev) return emptyList()
			val near = secrets.filter { it.key !in found && d2(it, px, py, pz) <= NEAR * NEAR }
				.sortedBy { d2(it, px, py, pz) }.take(count - prev).map { it.key }
			found += near
			return near
		}

		fun reset() { found.clear(); tabFound = null }

		companion object { const val NEAR = 6.0 }
	}
}

object SecretWayfinder {
	private val cfg get() = DungeonSecrets.TConfig
	private val tracker = SecretWayfinderLogic.Tracker()
	private val lockedChests = SecretWayfinderLogic.LockedChests()
	private var secrets: List<SecretWayfinderLogic.Secret> = emptyList()
	private var target: SecretWayfinderLogic.Secret? = null
	private var roomCount: DungeonSecretCounts.RoomCount? = null
	private var lastLog: String? = null

	private val planner = BlockPathPlanner(
		nodeCap = 20_000, maxDist = 96, replanDistSq = 2.0, minTicksBetween = 10, cellsPerTick = 32_768,
		straightFallback = true, keepLastFound = true, goalRadius = 1.5, drawHeight = 0.1,
	)

	@Subscribe
	fun onWorld(event: WorldReadyEvent) {
		tracker.reset(); lockedChests.reset(); secrets = emptyList(); target = null; roomCount = null; lastLog = null; planner.clear()
	}

	@Subscribe
	fun onActionBar(event: ActionBarEvent) {
		if (!cfg.wayfinder || !DungeonUtil.isInDungeonIsland) return
		DungeonSecretCounts.fromActionBar(event.bareText)?.let { roomCount = it }
	}

	@Subscribe
	fun onWorldAction(event: WorldActionPacketEvent) {
		if (!cfg.wayfinder) return
		when (event) {
			is WorldActionPacketEvent.BlockAction ->
				if (event.type == 1 && event.param > 0 && (event.block == Blocks.CHEST || event.block == Blocks.TRAPPED_CHEST)) {
					OpenedChests.state.onLidOpen(event.pos.asLong())
					tracker.markFound(SecretWayfinderLogic.blockKey(SecretWayfinderLogic.Kind.CHEST, event.pos.x, event.pos.y, event.pos.z))
				}
			is WorldActionPacketEvent.ItemTaken -> tracker.markFound("${SecretWayfinderLogic.Kind.ITEM}#${event.item.id}")
			else -> {}
		}
	}

	@Subscribe
	fun onUseBlock(event: UseBlockEvent) {
		if ((!cfg.wayfinder && !DungeonSecrets.TConfig.chests) || !DungeonUtil.isInDungeonIsland) return
		val p = event.hitResult.blockPos
		val st = event.world.getBlockState(p)
		if (st.`is`(Blocks.CHEST) || st.`is`(Blocks.TRAPPED_CHEST)) {
			OpenedChests.state.onChestClick(p.asLong(), System.currentTimeMillis())
			lockedChests.onChestClick(SecretWayfinderLogic.blockKey(SecretWayfinderLogic.Kind.CHEST, p.x, p.y, p.z), System.currentTimeMillis())
		}
		if (st.`is`(Blocks.LEVER))
			tracker.markFound(SecretWayfinderLogic.blockKey(SecretWayfinderLogic.Kind.LEVER, p.x, p.y, p.z))
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if ((!cfg.wayfinder && !DungeonSecrets.TConfig.chests) || !DungeonUtil.isInDungeonIsland) return
		lockedChests.onChat(event.bareText, System.currentTimeMillis())?.let {
			OpenedChests.state.onLocked()
			Apex.logger.info("[ApexWayfinder] locked chest $it: lever first")
		}
	}

	@Subscribe
	fun onScreen(event: ScreenChangeEvent) {
		if ((!cfg.wayfinder && !DungeonSecrets.TConfig.chests) || !DungeonUtil.isInDungeonIsland) return
		val screen = event.new as? AbstractContainerScreen<*> ?: return
		if (!screen.title.string.contains("Chest")) return
		OpenedChests.state.onChestScreen(System.currentTimeMillis())?.let { Apex.logger.info("[ApexWayfinder] chest opened at ${BlockPos.of(it).toShortString()}") }
	}

	private fun leverOk(level: net.minecraft.world.level.Level, l: LeverGate.P, feetY: Double): Boolean {
		val pos = BlockPos(l.x, l.y, l.z)
		val st = level.getBlockState(pos)
		val face = st.getValue(BlockStateProperties.ATTACH_FACE)
		val support = when (face) {
			net.minecraft.world.level.block.state.properties.AttachFace.FLOOR -> pos.below()
			net.minecraft.world.level.block.state.properties.AttachFace.CEILING -> pos.above()
			else -> pos.relative(st.getValue(BlockStateProperties.HORIZONTAL_FACING).opposite)
		}
		fun id(p: BlockPos) = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).block).path
		val around = net.minecraft.core.Direction.entries.map { id(pos.relative(it)) } + id(support.below()) + id(support.above())
		if (LeverGate.isDecorative(id(support), around)) return false
		val ladder = (1..3).any { dy -> level.getBlockState(pos.below(dy)).let { it.`is`(Blocks.LADDER) || it.`is`(Blocks.VINE) } }
		return LeverGate.reachable(l, feetY, ladder)
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		if (!cfg.wayfinder || !DungeonUtil.isInDungeonIsland) { if (target != null || secrets.isNotEmpty()) { secrets = emptyList(); target = null; planner.clear() }; return }
		DungeonBlockIndex.wanted = true
		if (event.tickCount % 5 != 0) return
		val level = Client.level ?: return
		val player = Client.player ?: return
		val room = DungeonRoomTracker.currentRoom()
		val cells = (room?.cells ?: DungeonRoomTracker.currentCells())?.toSet()
		if (cells.isNullOrEmpty()) { secrets = emptyList(); target = null; planner.clear(); return }
		val out = ArrayList<SecretWayfinderLogic.Secret>()
		val m = BlockPos.MutableBlockPos()
		fun inRoom(x: Int, z: Int) = SecretWayfinderLogic.inCells(x, z, cells)

		val chestsOk = PuzzleChests.liveChestsAreSecrets(room?.info?.type, room?.info?.name)
		for ((x, y, z) in DungeonBlockIndex.chests) if (chestsOk && inRoom(x, z)) {
			val st = level.getBlockState(m.set(x, y, z))

			if (OpenedChests.state.isOpened(BlockPos.asLong(x, y, z))) continue
			if (st.`is`(Blocks.CHEST) || st.`is`(Blocks.TRAPPED_CHEST))
				out += SecretWayfinderLogic.Secret(SecretWayfinderLogic.Kind.CHEST, x + 0.5, y + 0.5, z + 0.5, SecretWayfinderLogic.blockKey(SecretWayfinderLogic.Kind.CHEST, x, y, z))
		}
		val leverStates = HashMap<String, Boolean>()

		val unflipped = ArrayList<LeverGate.P>()
		for ((x, y, z) in DungeonBlockIndex.levers) if (inRoom(x, z)) {
			val st = level.getBlockState(m.set(x, y, z))
			if (!st.`is`(Blocks.LEVER)) continue
			val key = SecretWayfinderLogic.blockKey(SecretWayfinderLogic.Kind.LEVER, x, y, z)
			val powered = st.getValue(BlockStateProperties.POWERED)
			leverStates[key] = powered
			if (powered) tracker.markFound(key) else unflipped += LeverGate.P(x, y, z)
		}
		val liveChests = out.filter { it.kind == SecretWayfinderLogic.Kind.CHEST }.map { LeverGate.P(kotlin.math.floor(it.x).toInt(), kotlin.math.floor(it.y).toInt(), kotlin.math.floor(it.z).toInt()) }
		val tracked = HashSet(LeverGate.gating(LeverGate.catalog[room?.info?.name].orEmpty(), unflipped, liveChests))
		if (lockedChests.keys.isNotEmpty()) LeverGate.backup(unflipped, { leverOk(level, it, player.y) }, player.x, player.y, player.z)?.let { tracked += it }
		for (l in tracked)
			out += SecretWayfinderLogic.Secret(SecretWayfinderLogic.Kind.LEVER, l.x + 0.5, l.y + 0.5, l.z + 0.5, SecretWayfinderLogic.blockKey(SecretWayfinderLogic.Kind.LEVER, l.x, l.y, l.z))
		for ((x, y, z) in DungeonBlockIndex.heads) if (inRoom(x, z)) {
			val be = level.getBlockEntity(m.set(x, y, z)) as? SkullBlockEntity ?: continue
			val owner = be.ownerProfile?.partialProfile()?.id()?.toString()
			if (SecretWayfinderLogic.isWitherEssence(owner))
				out += SecretWayfinderLogic.Secret(SecretWayfinderLogic.Kind.ESSENCE, x + 0.5, y + 0.5, z + 0.5, SecretWayfinderLogic.blockKey(SecretWayfinderLogic.Kind.ESSENCE, x, y, z))
		}
		for (e in level.entitiesForRendering()) {
			if (!e.isAlive || !inRoom(e.blockX, e.blockZ)) continue
			when (e) {
				is ItemEntity -> if (SecretWayfinderLogic.isSecretItem(e.item.view().hypixelIdText, e.item.hoverName.bareText))
					out += SecretWayfinderLogic.Secret(SecretWayfinderLogic.Kind.ITEM, e.x, e.y + 0.25, e.z, "${SecretWayfinderLogic.Kind.ITEM}#${e.id}")
				is Bat -> out += SecretWayfinderLogic.Secret(SecretWayfinderLogic.Kind.BAT, e.x, e.y + 0.4, e.z, "${SecretWayfinderLogic.Kind.BAT}#${e.id}")
				else -> {}
			}
		}
		secrets = out
		lockedChests.onLevers(leverStates, out.filter { it.kind == SecretWayfinderLogic.Kind.CHEST }.mapTo(HashSet()) { it.key })
		val tab = TabListReader.currentSnapshot.body.map { it.bareText.trim() }
		tracker.onTabCount(DungeonSecretCounts.fromTab(tab).found, out, player.x, player.y, player.z)
		val done = SecretWayfinderLogic.roomDone(roomCount)
		target = if (done) null else SecretWayfinderLogic.pickTarget(out, tracker.foundKeys, lockedChests.keys, player.x, player.y, player.z)
		val unfound = out.count { it.key !in tracker.foundKeys }
		val log = "room=${room?.info?.name ?: "?"} rot=${room?.rotation ?: "?"} seen=${out.size} unfound=$unfound count=${roomCount?.let { "${it.found}/${it.total}" } ?: "?"} target=${target?.kind}"
		if (log != lastLog) { lastLog = log; Apex.logger.info("[ApexWayfinder] $log") }
		val t = target
		if (t == null) { planner.clear(); return }
		val minI = cells.minOf { it.first }; val maxI = cells.maxOf { it.first }
		val minJ = cells.minOf { it.second }; val maxJ = cells.maxOf { it.second }
		val feet = player.blockPosition()
		val minY = minOf(feet.y, t.y.toInt()) - 12; val maxY = maxOf(feet.y, t.y.toInt()) + 12
		planner.update(feet, BlockPos.containing(t.x, t.y, t.z), BlockPathPlanner.MinecraftSource(level),
			maxOf(minY, level.minY), minOf(maxY, level.maxY),
			BlockPathPlanner.Box(-200 + 32 * minI, minY, -200 + 32 * minJ, -200 + 32 * maxI + 31, maxY, -200 + 32 * maxJ + 31))
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		if (!cfg.wayfinder || !DungeonUtil.isInDungeonIsland) return
		val t = target ?: return
		val colour = cfg.wayfinderColour.getEffectiveColourRGB()
		val route = planner.result?.points
		WorldDraw.drawInWorld(event, WaypointType.SECRET_WAYFINDER) {

			depthTest = true
			markerBox(AABB(t.x - 0.5, t.y - 0.5, t.z - 0.5, t.x + 0.5, t.y + 0.5, t.z + 0.5), colour)
			depthTest = false
			if (route != null && route.size > 1) line(route, color = colour, lineWidth = 4F)
			else tracer(Vec3(t.x, t.y, t.z), colour)
		}
	}
}
