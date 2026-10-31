// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.dungeon.secrets

import io.github.notenoughupdates.moulconfig.ChromaColour
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.commands.onLiteral
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldActionPacketEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.features.dungeon.DungeonRoomTracker
import dev.ashtonite365.apex.features.dungeon.RoomCornerFinder
import dev.ashtonite365.apex.features.dungeon.TagBodyPairing
import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.skyblock.DungeonUtil
import dev.ashtonite365.apex.util.bareText

object DungeonSecrets {
	val identifier: String
		get() = "dungeon-secrets"

	@Config
	object TConfig : ManagedConfig(category = Category.DUNGEONS, name = identifier) {
		val crypts by toggle("crypts") { false }
		val cryptsColour by colour("crypts-colour") { ChromaColour.fromStaticRGB(0xAA, 0x55, 0xFF, 0x70) }
		val cryptsThroughWalls by riskyToggle("crypts-through-walls", RiskLevel.RISKY)
		val cryptsWholeDungeon by toggle("crypts-whole-dungeon") { true }
		val chests by toggle("chests") { false }
		val chestsColour by colour("chests-colour") { ChromaColour.fromStaticRGB(0xFF, 0xAA, 0x00, 0x70) }
		val chestsThroughWalls by riskyToggle("chests-through-walls", RiskLevel.RISKY)
		val chestsHideOpened by toggle("chests-hide-opened") { true }
		val chestsWholeDungeon by toggle("chests-whole-dungeon") { true }
		val minibosses by toggle("minibosses") { false }
		val minibossesColour by colour("minibosses-colour") { ChromaColour.fromStaticRGB(0x00, 0xFF, 0xAA, 0x60) }
		val minibossesThroughWalls by riskyToggle("minibosses-through-walls", RiskLevel.RISKY)
		val minibossesLabel by toggle("minibosses-label") { true }
		val wayfinder by toggle("wayfinder") { false }
		val wayfinderColour by colour("wayfinder-colour") { ChromaColour.fromStaticRGB(0x55, 0xFF, 0xFF, 0xC0) }
		val superboomChestDepth by integer("superboom-chest-depth", 2, 12) { 6 }
		val superboomChestRadius by integer("superboom-chest-radius", 3, 16) { 8 }
		val superboomCryptRadius by integer("superboom-crypt-radius", 3, 12) { 6 }

		override fun migrateFromAll(saved: JsonObject, all: JsonObject): JsonObject = migrateLostAdventurer(saved, all)
	}

	fun migrateLostAdventurer(saved: JsonObject, all: JsonObject): JsonObject {
		if ("minibosses" in saved) return saved
		val old = (all["puzzle-solvers"] as? JsonObject) ?: return saved
		val on = (old["lost-adventurer"] as? JsonPrimitive)?.content == "true"
		val colour = old["lost-adventurer-colour"]
		if (!on && colour == null) return saved
		val out = saved.toMutableMap()
		if (on) { out["minibosses"] = JsonPrimitive(true); out["minibosses-through-walls"] = JsonPrimitive(true) }
		if (colour != null && "minibosses-colour" !in saved) out["minibosses-colour"] = colour
		return JsonObject(out)
	}

	fun superboomParams() = SuperboomRule.Params(TConfig.superboomChestDepth, TConfig.superboomChestRadius, TConfig.superboomCryptRadius)

	@Subscribe
	fun onBlockAction(event: WorldActionPacketEvent) {
		val a = event as? WorldActionPacketEvent.BlockAction ?: return
		if (a.type == 1 && a.param > 0 && (a.block == Blocks.CHEST || a.block == Blocks.TRAPPED_CHEST)) OpenedChests.state.onLidOpen(a.pos.asLong())
	}

	class Mark(val tagId: Int, val name: String, var box: AABB, var live: Boolean, val firstSeenDist: Double, val cell: Pair<Int, Int>?)
	private val marks = LinkedHashMap<Int, Mark>()
	private var pairs: Map<Int, Int> = emptyMap()
	private var farthest = 0.0

	val minibossMarks: Collection<Mark> get() = marks.values

	fun marksMiniboss(tag: String) = TConfig.minibosses && MinibossNames.of(tag) != null

	fun marksBody(id: Int) = TConfig.minibosses && id in pairs.values

	private fun cellOf(x: Double, z: Double): Pair<Int, Int>? {
		val c = RoomCornerFinder.cell(Math.floor(x).toInt()) to RoomCornerFinder.cell(Math.floor(z).toInt())
		return c.takeIf { it.first in 0 until RoomCornerFinder.GRID && it.second in 0 until RoomCornerFinder.GRID }
	}

	@Subscribe
	fun onWorld(event: WorldReadyEvent) { marks.clear(); pairs = emptyMap(); OpenedChests.state.reset(); farthest = 0.0 }

	@Subscribe
	fun onTick(event: TickEvent) {
		if (!DungeonUtil.isInDungeonIsland) { if (marks.isNotEmpty()) marks.clear(); return }
		if (TConfig.chests || TConfig.crypts) DungeonBlockIndex.wanted = true
		if (!TConfig.minibosses) { if (marks.isNotEmpty()) marks.clear(); return }
		if (event.tickCount % 5 != 0) return
		val level = Client.level ?: return
		val player = Client.player ?: return
		val tags = ArrayList<ArmorStand>(); val bodies = ArrayList<LivingEntity>()
		for (e in level.entitiesForRendering()) {
			if (e is ArmorStand) { if (e.hasCustomName() && e.isAlive) tags += e }
			else if (e is LivingEntity && e.isAlive && e !== player) bodies += e
		}
		val bossTags = tags.filter { t ->
			val n = t.customName!!.bareText
			MinibossNames.of(n) != null && !MinibossNames.isDead(n)
		}
		val paired = TagBodyPairing.pairEntities(bossTags, bodies, player, pairs)
		pairs = paired.mapValues { it.value.id }
		val seen = HashSet<Int>()
		for (t in bossTags) {
			seen += t.id
			val body = paired[t.id]
			val box = body?.boundingBox ?: AABB(t.x - 0.4, t.y - 2.2, t.z - 0.4, t.x + 0.4, t.y - 0.2, t.z + 0.4)
			val mark = marks[t.id]
			if (mark == null) {
				val d = t.distanceTo(player).toDouble()
				val cell = cellOf(t.x, t.z)
				marks[t.id] = Mark(t.id, MinibossNames.of(t.customName!!.bareText)!!, box, body != null, d, cell)
				val room = cell?.let { c -> DungeonRoomTracker.detected.firstOrNull { c in it.cells }?.info?.name }
				if (d > farthest) farthest = d
				Apex.logger.info("[ApexMiniboss] first seen \"${t.customName?.bareText}\" dist=${"%.1f".format(d)} cell=$cell room=${room ?: "?"} " +
					"live=${body != null} farthest=${"%.1f".format(farthest)} loadedRadius=${DungeonBlockIndex.loadedRadius(level, player.chunkPosition().x, player.chunkPosition().z)}")
			} else {
				mark.box = box
				if (!mark.live && body != null) Apex.logger.info("[ApexMiniboss] live ${mark.name} body=${body.id} dist=${"%.1f".format(body.distanceTo(player))}")
				mark.live = body != null
			}
		}
		marks.keys.retainAll(seen)
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		if (!DungeonUtil.isInDungeonIsland) return
		val doChests = TConfig.chests; val doCrypts = TConfig.crypts; val doBosses = TConfig.minibosses && marks.isNotEmpty()
		if (!doChests && !doCrypts && !doBosses) return
		val level = Client.level ?: return
		val room = DungeonRoomTracker.currentCells()?.toSet()
		fun inRoom(x: Int, z: Int) = room != null && (RoomCornerFinder.cell(x) to RoomCornerFinder.cell(z)) in room
		WorldDraw.drawInWorld(event) {
			if (doChests) {
				depthTest = !TConfig.chestsThroughWalls
				val colour = TConfig.chestsColour.getEffectiveColourRGB()
				val m = BlockPos.MutableBlockPos()
				for ((x, y, z) in DungeonBlockIndex.chests) {
					if (!TConfig.chestsWholeDungeon && !inRoom(x, z)) continue

					if (OpenedChests.state.isOpened(BlockPos.asLong(x, y, z))) continue

					val info = DungeonRoomTracker.roomInfoAt(level, RoomCornerFinder.cell(x) to RoomCornerFinder.cell(z))
					if (!PuzzleChests.liveChestsAreSecrets(info?.type, info?.name)) continue
					val st = level.getBlockState(m.set(x, y, z))
					if (!st.`is`(Blocks.CHEST) && !st.`is`(Blocks.TRAPPED_CHEST)) continue
					box(AABB(x + 0.0625, y.toDouble(), z + 0.0625, x + 0.9375, y + 0.875, z + 0.9375), colour)
				}
			}
			if (doCrypts) {
				depthTest = !TConfig.cryptsThroughWalls
				val colour = TConfig.cryptsColour.getEffectiveColourRGB()
				for (t in DungeonBlockIndex.crypts) {
					if (!TConfig.cryptsWholeDungeon && !inRoom(t.minX, t.minZ)) continue
					box(AABB(t.minX.toDouble(), t.minY.toDouble(), t.minZ.toDouble(), t.maxX + 1.0, t.maxY + 1.0, t.maxZ + 1.0), colour)
				}
			}
			if (doBosses) {
				depthTest = !TConfig.minibossesThroughWalls
				val colour = TConfig.minibossesColour.getEffectiveColourRGB()
				for (mk in marks.values) {
					box(mk.box, colour)
					if (TConfig.minibossesLabel) text(Vec3(mk.box.center.x, mk.box.maxY + 0.6, mk.box.center.z),
						Component.literal((if (mk.live) "§e" else "§7") + mk.name))
				}
			}
			depthTest = false
		}
	}

	@Subscribe
	fun onCommand(event: CommandEvent.SubCommand) {
		event.register(ManagedConfig.Category.DUNGEONS, "secrets", "Secrets: dumpblocks (save the blocks around you, for crypt shapes), status") {
			onLiteral("dumpblocks") { onRun { dumpBlocks() } }
			onLiteral("status") { onRun { status() } }
		}
	}

	private fun status() {
		val text = "§bSecrets: §7chests ${DungeonBlockIndex.chests.size}, cracked bricks ${DungeonBlockIndex.cracked.size}, " +
			"crypts ${DungeonBlockIndex.crypts.size}, mini-bosses ${marks.size} (farthest first seen ${"%.1f".format(farthest)})"
		Client.showChat(Component.literal(text))
	}

	private fun dumpBlocks() {
		val level = Client.level ?: return
		val p = Client.player?.blockPosition() ?: return
		val m = BlockPos.MutableBlockPos()
		val lines = mutableListOf("SkyBlock Apex secrets block dump ${LocalDateTime.now()} at ${p.x},${p.y},${p.z}")
		val near = DungeonBlockIndex.crypts.filter { Math.abs(it.minX - p.x) <= 16 && Math.abs(it.minZ - p.z) <= 16 }
		near.forEach { lines += "crypt ${it.minX},${it.minY},${it.minZ}..${it.maxX},${it.maxY},${it.maxZ} cracked=${it.cracked.joinToString(" ")}" }
		for (y in p.y - 4..p.y + 4) for (z in p.z - 8..p.z + 8) for (x in p.x - 8..p.x + 8) {
			val st = level.getBlockState(m.set(x, y, z))
			if (st.isAir) continue
			lines += "$x $y $z ${BuiltInRegistries.BLOCK.getKey(st.block)}"
		}
		val dir = Apex.DATA_DIR.resolve("secrets")
		Files.createDirectories(dir)
		val file = dir.resolve("dump-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt")
		Files.write(file, lines)
		Apex.logger.info("[ApexCrypt] dump ${file.toAbsolutePath()} crypts=${near.size}")
		Client.showChat(Component.literal("§bSecrets: §7saved ${file.toAbsolutePath()} (${near.size} crypt matches near you)"))
	}
}
