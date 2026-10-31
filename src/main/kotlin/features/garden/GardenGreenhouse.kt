// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.garden

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.Items
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ItemTooltipEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.SlotRenderEvents
import dev.ashtonite365.apex.events.SoundReceiveEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.features.garden.GardenTools.TConfig
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.mc.NeedsComponents
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.bareText

@OptIn(NeedsComponents::class)
object GardenGreenhouse {

	private const val RANGE = 30
	private const val LEAF_LAYER = 74
	private const val TOLERANCE = 0.001

	private var hunting = false
	private var heardFrom: Pair<Double, Double>? = null
	val candidates = mutableListOf<BlockPos>()

	fun candidatesAt(x: Double, z: Double, radius: Double): List<BlockPos> {
		val centreX = floor(x) + 0.5
		val centreZ = floor(z) + 0.5
		val reach = RANGE + 1
		return buildList {
			for (offX in -reach..reach) {
				val bx = centreX + offX
				for (offZ in -reach..reach) {
					val bz = centreZ + offZ
					if (abs(hypot(bx - x, bz - z) - radius) < TOLERANCE) add(BlockPos(floor(bx).toInt(), LEAF_LAYER, floor(bz).toInt()))
				}
			}
		}
	}

	fun distanceFromVolume(volume: Float): Double = RANGE * (1.0 - volume)

	private fun stopHunting() {
		hunting = false
		heardFrom = null
		candidates.clear()
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!TConfig.phantomleafSolver) return
		val text = event.bareText
		if ("Phantomleaf: Poof! Try and find me!" in text) hunting = true
		else if ("Phantomleaf: You found me!" in text || "Phantomleaf: That's not me!" in text) stopHunting()
	}

	@Subscribe
	fun onWorld(event: WorldReadyEvent) = stopHunting()

	private fun same(a: Double, b: Double) = abs(a - b) < 1e-6

	@Subscribe
	fun onSound(event: SoundReceiveEvent) {
		if (!TConfig.phantomleafSolver || !hunting || SkyBlockSession.skyblockIsland != SkyBlockIsland.GARDEN) return
		if (event.pitch !in 0.61f..0.62f) return
		if (event.sound.value().location().path != "block.note_block.basedrum") return
		val me = Client.player ?: return
		val previous = heardFrom
		heardFrom = me.x to me.z

		if (previous == null || !same(previous.first, me.x) || !same(previous.second, me.z)) return
		candidates.clear()
		candidates += candidatesAt(me.x, me.z, distanceFromVolume(event.volume))
		if (candidates.isEmpty()) Client.showChat(Component.literal("§ePhantomleaf: no spot found, move a little."))
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		if (!TConfig.phantomleafSolver || !hunting || candidates.isEmpty()) return
		val tint = if (candidates.size == 1) 0x8055FF55.toInt() else 0x80FFFF55.toInt()
		WorldDraw.drawInWorld(event) { candidates.forEach { block(it, tint) } }
	}

	enum class Status(val colour: Int) { HARVESTABLE(0x8055FF55.toInt()), DROPS(0x80FFFF55.toInt()), GROWING(0x80FF5555.toInt()) }

	fun harvestStatus(lore: List<String>): Status {
		val last = lore.lastOrNull { it == "Status: Harvestable" || it.startsWith("Drops: ") || it.startsWith("Rewards: ") }
		return when {
			last == null -> Status.GROWING
			last == "Status: Harvestable" -> Status.HARVESTABLE
			else -> Status.DROPS
		}
	}

	private val WATER_OK = setOf("This crop has enough water to", "This crop doesn't need water!")

	fun hasEnoughWater(lore: List<String>): Boolean = lore.any(WATER_OK::contains)

	private const val BEACON_SLOT = 24
	private const val WATER_SLOT = 21

	@Subscribe
	fun onSlot(event: SlotRenderEvents.Before) {
		if (!TConfig.harvestableHighlight || SkyBlockSession.skyblockIsland != SkyBlockIsland.GARDEN) return
		val slot = event.slot
		if (slot.container is Inventory) return
		val stack = slot.item
		fun lore() = stack.view().loreAccordingToNbt.map { it.bareText }
		val tint = when (slot.index) {
			BEACON_SLOT -> if (stack.`is`(Items.BEACON)) harvestStatus(lore()).colour else null
			WATER_SLOT -> if (stack.`is`(Items.WATER_BUCKET) && hasEnoughWater(lore())) Status.HARVESTABLE.colour else null
			else -> null
		} ?: return
		event.context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, tint)
	}

	@Subscribe
	fun onTooltip(event: ItemTooltipEvent) {
		if (!TConfig.carrolynHint) return
		if (event.lines.none { "of these to Carrolyn" in it.bareText }) return
		event.lines += Component.literal("")
		event.lines += Component.literal("§eCarrolyn is on the Crimson Isle (Scarleton).")
	}
}
