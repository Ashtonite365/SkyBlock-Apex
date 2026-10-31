// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.util

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot

object ApexClickDelay {
	const val MIN_MS = 100
	const val MAX_MS = 200
	val RANGE_MS = MIN_MS..MAX_MS

	private val defaultRandom: (IntRange) -> Int = { it.random() }
	private val defaultScheduler: (Long, () -> Unit) -> Unit = { ms, action ->
		CompletableFuture.runAsync({ action() }, CompletableFuture.delayedExecutor(ms, TimeUnit.MILLISECONDS, Client.game))
	}

	var random: (IntRange) -> Int = defaultRandom

	var scheduler: (Long, () -> Unit) -> Unit = defaultScheduler

	private val defaultProtection: (Slot, ContainerInput) -> Boolean = { s, i ->
		dev.ashtonite365.apex.events.IsSlotProtectedEvent.shouldBlockInteraction(s, i, dev.ashtonite365.apex.events.IsSlotProtectedEvent.MoveOrigin.INVENTORY_MOVE)
	}
	var protection: (Slot, ContainerInput) -> Boolean = defaultProtection

	fun guardApplies(isPlayerInventorySlot: Boolean) = isPlayerInventorySlot

	fun isProtected(menu: AbstractContainerMenu, slot: Slot, input: ContainerInput): Boolean =
		guardApplies(slot.container is net.minecraft.world.entity.player.Inventory) && protection(slot, input)

	var testSink: ((containerId: Int, stateId: Int, slot: Int, button: Int, input: ContainerInput) -> Unit)? = null

	fun resetForTests() { random = defaultRandom; scheduler = defaultScheduler; protection = defaultProtection; testSink = null }

	fun clamp(ms: Long): Long = ms.coerceIn(MIN_MS.toLong(), MAX_MS.toLong())

	fun nextMs(): Long = clamp(random(RANGE_MS).toLong())
	fun nextDuration(): Duration = nextMs().milliseconds

	fun later(action: () -> Unit) = laterMs(nextMs(), action)

	fun laterMs(ms: Long, action: () -> Unit) = scheduler(clamp(ms), attributed(action))

	fun laterMsPlus(ms: Long, extraMs: Long, action: () -> Unit) = scheduler(clamp(ms) + extraMs.coerceAtLeast(0), attributed(action))

	private fun attributed(action: () -> Unit): () -> Unit {
		val press = dev.ashtonite365.apex.features.menus.MenuPress.attributed
		return { dev.ashtonite365.apex.features.menus.MenuPress.carry(press, action) }
	}

	fun send(menu: AbstractContainerMenu, slot: Slot, button: Int = 0, input: ContainerInput = ContainerInput.PICKUP) {

		testSink?.let { dev.ashtonite365.apex.features.menus.MenuPress.clickSent(); dev.ashtonite365.apex.features.menus.StaticMenus.viewSend(menu, slot) { it(menu.containerId, menu.stateId, slot.index, button, input) }; return }
		val player = Client.player ?: return
		if (isProtected(menu, slot, input)) return
		dev.ashtonite365.apex.features.menus.MenuPress.clickSent()
		dev.ashtonite365.apex.features.menus.StaticMenus.viewSend(menu, slot) { Client.gameMode?.handleContainerInput(menu.containerId, slot.index, button, input, player) }
	}

	fun clickLater(menu: AbstractContainerMenu, slot: Slot, button: Int = 0, then: () -> Unit = {},
	               click: (AbstractContainerMenu, Slot, Int) -> Unit = { m, s, b -> send(m, s, b) }) {
		val id = menu.containerId
		later { if (Client.player?.containerMenu?.containerId == id) { click(menu, slot, button); then() } }
	}
}
