// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.rift

import org.joml.Vector2i
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.player.RemotePlayer
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.SlotRenderEvents
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.plus.drawPlusLines
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.bareText

object PlusRiftPuzzles {
	@Config
	object TConfig : ManagedConfig("rift-puzzles", Category.RIFT) {
		val kloon by riskyToggle("kloon-hacking", RiskLevel.RISKY)
		val punchcard by toggle("punchcard") { false }
		val riftGuide by toggle("rift-guide") { false }
		val crux by toggle("crux-talisman") { false }
		val cruxHud by position("crux-hud", 140, 60) { Vector2i(5, 380) }
	}

	private const val GREEN = 0x8000FF00.toInt()
	private const val RED = 0x80FF0000.toInt()
	private const val YELLOW = 0x80FFFF00.toInt()

	private fun inRift() = SkyBlockSession.skyblockIsland == SkyBlockIsland.RIFT

	private val alreadyPunched = HashSet<String>()
	private var sessionFull = false

	@Subscribe
	fun onWorld(event: WorldReadyEvent) {
		alreadyPunched.clear()
		sessionFull = false
	}

	private fun markClosestPlayer() {
		val me = Client.player ?: return
		val closest = Client.level?.players()?.filterIsInstance<RemotePlayer>()?.minByOrNull { it.distanceTo(me) } ?: return
		alreadyPunched.add(closest.name.string)
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!inRift()) return
		val text = event.bareText.trim()
		val punch = RiftRules.punched.matchEntire(text)
		if (punch != null) alreadyPunched.add(punch.groups["name"]!!.value)
		if (RiftRules.punchLimit.matches(text)) sessionFull = true
		if (RiftRules.awkward.matches(text)) markClosestPlayer()
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		if (!(TConfig.punchcard && inRift()) || sessionFull) return
		val level = Client.level ?: return
		WorldDraw.drawInWorld(event) {
			for (other in level.players()) {
				if (other !is RemotePlayer || other.name.string in alreadyPunched || other.uuid.version() != 4) continue
				box(other.boundingBox, 0x40FFFF00)
			}
		}
	}

	private fun openSlotStack(index: Int): ItemStack? {
		val screen = Client.screen as? AbstractContainerScreen<*> ?: return null
		return screen.menu.slots.getOrNull(index)?.item
	}

	private fun slotName(index: Int): String? = openSlotStack(index)?.takeUnless { it.isEmpty }?.hoverName?.bareText

	private fun slotLore(index: Int): List<String> {
		if (Client.screen !is AbstractContainerScreen<*>) return emptyList()
		return openSlotStack(index)?.view()?.loreAccordingToNbt?.map { it.bareText } ?: emptyList()
	}

	private fun wearingVisor() = Client.player?.getItemBySlot(EquipmentSlot.HEAD)?.view()?.repoItemId?.skyblockId == "RETRO_ENCABULATING_VISOR"

	private fun hackingColour(slotIndex: Int) = when (RiftRules.hackingColour(slotIndex, ::slotName)) {
		1 -> GREEN
		2 -> RED
		3 -> YELLOW
		else -> 0
	}

	private fun pickerColour(slotIndex: Int): Int {
		val me = Client.player
		val terminal = if (me == null) null else RiftRules.nearestTerminal(me.x, me.y, me.z)
		if (terminal == null) return 0
		return if (slotLore(slotIndex).any { terminal.label in it }) GREEN else 0
	}

	private fun guideColour(stack: ItemStack): Int {
		if (stack.isEmpty) return 0
		val lore = stack.view().loreAccordingToNbt.map { it.bareText }
		return if (RiftRules.guideNotCompleted(lore)) YELLOW else 0
	}

	@Subscribe
	fun onSlot(event: SlotRenderEvents.Before) {
		if (!inRift()) return
		val slot = event.slot
		if (slot.container is Inventory) return
		val title = Client.screenTitle ?: return
		val colour = if (TConfig.kloon && RiftRules.isHackingInv(title) && wearingVisor()) hackingColour(slot.index)
		else if (TConfig.kloon && title == "Hacked Terminal Color Picker" && wearingVisor()) pickerColour(slot.index)
		else if (TConfig.riftGuide && RiftRules.isRiftGuide(slotLore(40))) guideColour(slot.item)
		else 0
		if (colour != 0) event.context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, colour)
	}

	private fun findCrux(): ItemStack? {
		val inventory = Client.player?.inventory ?: return null
		for (i in 0 until inventory.containerSize) {
			val stack = inventory.getItem(i)
			if (stack.view().repoItemId?.skyblockId?.startsWith("CRUX_TALISMAN") == true) return stack
		}
		return null
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!(TConfig.crux && inRift())) return
		val crux = findCrux() ?: return
		val progress = RiftRules.cruxProgress(crux.view().loreAccordingToNbt.map { it.bareText })
		val percent = RiftRules.cruxPercent(progress) ?: return
		event.drawPlusLines(TConfig.cruxHud) {
			add("§e${crux.hoverName.bareText} §7($percent%)")
			for (line in progress) {
				val count = line.total?.let { "§e${line.done}/$it" } ?: "§aMAXED"
				add(" §7${line.tier} §f${line.name}: $count")
			}
		}
	}
}
