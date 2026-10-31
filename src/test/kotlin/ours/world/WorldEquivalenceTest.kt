// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.ours.world

import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.features.world.CoordListWaypoints
import dev.ashtonite365.apex.features.world.WaypointRouteManager
import dev.ashtonite365.apex.features.world.WaypointRoute
import dev.ashtonite365.apex.features.world.NavigableWaypoint
import dev.ashtonite365.apex.features.world.NavigationHelper
import dev.ashtonite365.apex.features.world.TemporaryWaypoints
import dev.ashtonite365.apex.features.world.Waypoints
import dev.ashtonite365.apex.features.world.wrappingWindow
import dev.ashtonite365.apex.repo.RepoManager
import dev.ashtonite365.apex.repo.SafeRepoConstants
import dev.ashtonite365.apex.repo.itemsSafe
import dev.ashtonite365.apex.test.ours.OursGolden
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.ShareCodes

class WorldEquivalenceTest {
	init {
		dev.ashtonite365.apex.test.ApexTestBootstrap.bootstrapMinecraft()
		RepoManager.initialize()
	}

	private fun <T> attempt(block: () -> T): String = try {
		block().toString()
	} catch (e: Throwable) {
		"ERR:" + e.javaClass.simpleName
	}

	private fun sample(relative: String?) = WaypointRoute(
		"Route", "route-1", relative,
		mutableListOf(WaypointRoute.Waypoint(1, 2, 3), WaypointRoute.Waypoint(-10, 70, 400), WaypointRoute.Waypoint(0, 0, 0)),
		true,
	)

	@Test
	fun waypointFormatsMatch() {
		val out = mutableListOf<String>()
		val abs = sample(null)
		val json = Apex.json.encodeToString(WaypointRoute.serializer(), abs)
		out += "json $json"
		out += "tight " + Apex.tightJson.encodeToString(WaypointRoute.serializer(), abs)
		out += "back ${Apex.json.decodeFromString(WaypointRoute.serializer(), json)}"
		out += "lenient " + attempt { Apex.json.decodeFromString(WaypointRoute.serializer(), """{"label":"a","id":"b","waypoints":[{"x":1,"y":2,"z":3}],"isOrdered":false}""") }
		out += "size ${abs.size} ${abs.waypoints[1].blockPos} ${WaypointRoute.Waypoint.from(BlockPos(4, 5, 6))}"
		val copy = abs.deepCopy()
		copy.waypoints.add(WaypointRoute.Waypoint(9, 9, 9))
		out += "deep ${abs.size} ${copy.size}"
		out += "export-abs ${WaypointRouteManager.createExportableCopy(abs)}"
		val rel = sample("by the fountain").also { it.lastRelativeImport = BlockPos(100, 64, -20) }
		out += "export-rel ${WaypointRouteManager.createExportableCopy(rel)} ${rel.waypoints}"
		out += "export-rel-noorigin " + attempt { WaypointRouteManager.createExportableCopy(sample("x")) }
		out += "prefix ${WaypointRouteManager.SHARE_PREFIX} ${WaypointRouteManager.ENCODED_SHARE_PREFIX}"
		val enc = ShareCodes.toShareCode(WaypointRouteManager.SHARE_PREFIX, abs)
		out += "template $enc"
		out += "decode ${ShareCodes.fromShareCode<WaypointRoute>(WaypointRouteManager.SHARE_PREFIX, enc)}"
		OursGolden.check("world-formats", out)
	}

	@Test
	fun coordListMatches() {
		val out = mutableListOf<String>()
		val abs = sample(null)
		for (origin in listOf(BlockPos.ZERO, BlockPos(10, -5, 3))) {
			val cw = CoordListWaypoints.fromRoute(abs, origin)
			out += "from $origin $cw"
			out += "enc " + Apex.tightJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(CoordListWaypoints.CoordListEntry.serializer()), cw)
			out += "into ${CoordListWaypoints.intoRoute(cw, origin)}"
		}
		for (text in listOf(
			"""[{"x":1,"y":2,"z":3},{"x":4,"y":5,"z":6,"r":1,"g":2,"b":3}]""",
			"""[{"x":1,"y":null,"z":3},{"x":7,"y":8,"z":9,"options":{"name":1}}]""",
			"""[{"y":2}]""", """[]""", "{}", "not json", "",
			"""[{"x":"1","y":2,"z":3}]""",
		)) {
			val r = CoordListWaypoints.tryParse(text)
			out += "parse $text -> ${r.getOrNull()} ${r.exceptionOrNull()?.javaClass?.simpleName} ${r.getOrNull()?.let { CoordListWaypoints.intoRoute(it, BlockPos(1, 1, 1)) }}"
		}
		OursGolden.check("world-coordlist", out)
	}

	@Test
	fun waypointHelpersMatch() {
		val out = mutableListOf<String>()
		val l = (0..6).toList()
		for (start in 0..6) for (size in 0..8) out += "win $start $size ${attempt { l.wrappingWindow(start, size) }}"
		out += "win-empty ${attempt { emptyList<Int>().wrappingWindow(0, 3) }}"
		Waypoints.waypoints = null
		out += "nonEmpty ${Waypoints.useNonEmptyWaypoints()}"
		out += "editable ${Waypoints.useEditableWaypoints()}"
		out += "nonEmpty2 ${Waypoints.useNonEmptyWaypoints()}"
		Waypoints.waypoints = null
		out += "styles ${Waypoints.MarkerStyle.entries.map { "${it.name}:${it.id}:${it.serializedName}:${it.beam}:${it.box}" }}"
		out += "texts ${Waypoints.textInvalidIndex(4).string} | ${Waypoints.textNothingToExport().string} | ${Waypoints.WAYPOINTS_SUBCOMMAND} ${Waypoints.identifier}"
		out += "temp ${TemporaryWaypoints.temporaryPlayerWaypointMatcher.pattern()}"
		for (msg in listOf("Party > Foo: x: 1, y: 2, z: 3", "X: -5 Y: 10 Z: 7", "x: 1 y: 2", "x:1,y:2,z:3", "at x: 12, y: 70, z: -3 here"))
			out += "match $msg ${TemporaryWaypoints.temporaryPlayerWaypointMatcher.matcher(msg).let { m -> if (m.find()) (1..3).map { m.group(it) } else null }}"
		OursGolden.check("world-helpers", out)
	}

	@Suppress("UNCHECKED_CAST")
	private fun route(from: SkyBlockIsland, to: SkyBlockIsland): List<Any>? {
		val m = NavigationHelper::class.java.getDeclaredMethod("routeFrom", SkyBlockIsland::class.java, SkyBlockIsland::class.java, MutableSet::class.java)
		m.isAccessible = true
		return m.invoke(NavigationHelper, from, to, mutableSetOf<SkyBlockIsland>()) as List<Any>?
	}

	@Test
	fun routesAndNpcsMatch() {
		val out = mutableListOf<String>()
		val tps = SafeRepoConstants.teleporters()
		out += "teleporters ${tps.size}"
		val islands = tps.flatMap { listOf(it.getFrom(), it.getTo()) }.distinct().sorted().map(SkyBlockIsland::ofMode)
		for (t in tps) with(NavigationHelper) { out += "tp ${t.getFrom()}->${t.getTo()} ${t.exitPos} ${t.originIsland.serverMode} ${t.destIsland.serverMode}" }
		for (a in islands) for (b in islands) {
			val r = route(a, b)
			out += "route ${a.serverMode}->${b.serverMode} ${r?.map { val t = it as io.github.moulberry.repo.constants.Islands.Teleporter; t.getFrom() + ">" + t.getTo() }}"
		}
		out += "override ${NavigationHelper.portalOverride("hub", "rift")} ${NavigationHelper.portalOverride("rift", "hub")}"
		val npcs = RepoManager.coreRepo.items.itemsSafe.values.filter { it.island != null && it.island.isNotBlank() }.sortedBy { it.skyblockItemId }
		for (n in npcs) {
			val w = NavigableWaypoint.RepoNpc(n)
			out += "npc ${n.skyblockItemId} ${w.name} ${w.position} ${w.island.serverMode}"
		}
		OursGolden.check("world-routes", out)
	}
}
