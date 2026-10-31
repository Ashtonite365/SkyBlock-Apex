// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.experiments.apexchrono

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ChestInventoryUpdateEvent
import dev.ashtonite365.apex.events.IsSlotProtectedEvent
import dev.ashtonite365.apex.events.SlotClickEvent
import dev.ashtonite365.apex.events.SlotRenderEvents
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.bareText

object ApexChrono {

	object TConfig {
		private val t get() = dev.ashtonite365.apex.features.experiments.ExperimentationTable

		val enabled get() = t.enabled && t.chronoApexMenus
		val currentColour get() = t.chronoCurrentColour
		val nextColour get() = t.chronoNextColour
		val showNext get() = t.chronoShowNext
		val numbers get() = t.chronoNumbers
		val blockWrongClicks get() = t.blockWrongClicks
		val debugLog get() = t.chronoDebugLog
	}

	private fun log(s: String) { if (TConfig.debugLog) Apex.logger.info("[ApexChrono] $s") }
	val solver = ApexChronoSolver(::log)
	private var screenRef: AbstractContainerScreen<*>? = null
	private var active = false
	private var lastContainer = -1

	private fun top(screen: AbstractContainerScreen<*>) = screen.menu.slots.filter { it.container !is Inventory }
	private fun view(i: Int, st: ItemStack) =
		ChronoSlot(i, if (st.isEmpty) "" else st.hoverName.bareText, if (st.isEmpty) "" else BuiltInRegistries.ITEM.getKey(st.item).path, st.hasFoil())

	private fun snapshot(screen: AbstractContainerScreen<*>, override: Map<Int, ItemStack> = emptyMap()) =
		top(screen).mapIndexed { i, s -> view(i, override[i] ?: s.item) }

	@Subscribe
	fun onTick(event: TickEvent) {
		val screen = Client.screen as? AbstractContainerScreen<*>
		if (screen !== screenRef) {
			screenRef = screen
			val title = screen?.title?.bareText ?: ""
			val tier = ApexChronoModel.tier(title)
			if (tier != null) {
				log("title match '$title' tier=$tier enabled=${TConfig.enabled} slots=${screen!!.menu.slots.size}")
				if (!active || screen!!.menu.containerId != lastContainer) solver.reset()
				lastContainer = screen!!.menu.containerId
				active = true
			} else {
				if (active) log("left Chronomatron ('$title')")
				active = false; solver.reset(); lastContainer = -1
			}
		}
		if (!active || screen == null) return
		solver.update(snapshot(screen))
	}

	@Subscribe
	fun onSlotUpdate(event: ChestInventoryUpdateEvent) {
		val screen = screenRef ?: return
		if (!active || Client.screen !== screen) return
		when (event) {
			is ChestInventoryUpdateEvent.Single -> if (event.slot < top(screen).size) solver.update(snapshot(screen, mapOf(event.slot to event.stack)))
			is ChestInventoryUpdateEvent.Multi -> solver.update(event.contents.take(top(screen).size).mapIndexed(::view))
		}
	}

	@Subscribe
	fun onClick(event: SlotClickEvent) {
		val screen = screenRef ?: return
		if (!active || event.slot.container is Inventory) return
		val i = top(screen).indexOf(event.slot).takeIf { it >= 0 } ?: return
		solver.onClick(i)
	}

	@Subscribe
	fun onGuard(event: IsSlotProtectedEvent) {
		if (!active || !TConfig.enabled || !TConfig.blockWrongClicks) return
		if (event.origin != IsSlotProtectedEvent.MoveOrigin.INVENTORY_MOVE) return
		val screen = screenRef ?: return
		val slot = event.slot ?: return
		val i = top(screen).indexOf(slot).takeIf { it >= 0 } ?: return
		val want = solver.expected ?: return
		val alt = com.mojang.blaze3d.platform.InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_LALT)
			|| com.mojang.blaze3d.platform.InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_RALT)
		if (!alt && solver.groupOf(i) != null && i !in solver.slotsOf(want)) { log("blocked wrong click $i (want $want)"); event.protectSilent() }
	}

	@Subscribe
	fun onSlotRender(event: SlotRenderEvents.After) {
		val screen = screenRef ?: return
		if (!active || !TConfig.enabled || Client.screen !== screen || event.slot.container is Inventory) return
		val i = top(screen).indexOf(event.slot).takeIf { it >= 0 } ?: return
		val m = solver.markFor(i) ?: return
		if (m.kind == ChronoMarkKind.NEXT && !TConfig.showNext) return
		val col = (if (m.kind == ChronoMarkKind.CURRENT) TConfig.currentColour else TConfig.nextColour).getEffectiveColourRGB()
		val x = event.slot.x; val y = event.slot.y
		event.context.fill(x, y, x + 16, y + 16, col)
		if (TConfig.numbers) {
			event.context.text(Client.font, "${m.number}", x + 1, y + 1, 0xFFFFFFFF.toInt())
			if (m.repeat > 1) event.context.text(Client.font, "×${m.repeat}", x + 17 - Client.font.width("×${m.repeat}"), y + 9, 0xFFFFFF55.toInt())
		}
	}
}
