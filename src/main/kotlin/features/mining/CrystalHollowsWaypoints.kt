package dev.ashtonite365.apex.features.mining

import dev.ashtonite365.apex.util.render.WaypointType
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.render.WorldDraw

object CrystalHollowsWaypoints {
	@Config
	object TConfig : ManagedConfig("crystal-hollows-waypoints", Category.MINING) {
		val enabled by toggle("enabled") { true }
		val showNucleus by toggle("show-nucleus") { true }
	}

	val notableAreas = setOf(
		"Goblin Queen's Den", "Khazad-dûm", "Jungle Temple", "Mines of Divan", "Lost Precursor City",
		"Fairy Grotto", "Dragon's Lair", "Mithril Deposits", "Precursor Remnants", "Goblin Holdout", "Jungle", "Magma Fields",
	)
	val nucleus = BlockPos(513, 107, 513)

	val discovered = mutableMapOf<String, BlockPos>()

	@Subscribe
	fun onWorld(event: WorldReadyEvent) {
		discovered.clear()
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		if (!TConfig.enabled) return
		if (SkyBlockSession.skyblockIsland != SkyBlockIsland.CRYSTAL_HOLLOWS) return
		val area = DwarvenMinesWaypoints.scoreboardArea() ?: return
		if (area !in notableAreas || area in discovered) return
		if (area in setOf("Mithril Deposits", "Precursor Remnants", "Goblin Holdout", "Jungle", "Magma Fields")) return
		discovered[area] = Client.player?.blockPosition() ?: return
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		if (!TConfig.enabled) return
		if (SkyBlockSession.skyblockIsland != SkyBlockIsland.CRYSTAL_HOLLOWS) return
		WorldDraw.drawInWorld(event, WaypointType.CRYSTAL_HOLLOWS) {
			if (TConfig.showNucleus) waypoint(nucleus, Component.literal("§dCrystal Nucleus"))
			discovered.forEach { (name, pos) -> waypoint(pos, Component.literal("§b$name")) }
		}
	}
}
