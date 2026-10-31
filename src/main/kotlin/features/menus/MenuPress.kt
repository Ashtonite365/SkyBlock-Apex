// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus

import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvents
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.SoundReceiveEvent
import dev.ashtonite365.apex.util.Client

object MenuPress {

	const val WINDOW_MS = 1500L

	var clock: () -> Long = { System.currentTimeMillis() }

	var sound: () -> Unit = { Client.sounds.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1F)) }

	var overRealMenu: () -> Boolean = {
		runCatching { Client.screen is net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<*> }.getOrDefault(false)
	}

	private var depth = 0
	private var carried = 0

	private var sounded = false
	private var playingOwn = false

	var muteUntilMs = Long.MIN_VALUE; private set

	var played = 0; private set

	val inPress get() = depth > 0

	val attributed get() = depth > 0 || carried > 0

	fun <T> press(block: () -> T): T {
		if (depth > 0) return block()
		depth = 1; sounded = false
		try { return block() } finally { depth = 0; sounded = false }
	}

	fun <T> carry(on: Boolean, block: () -> T): T {
		if (!on) return block()
		carried++
		try { return block() } finally { carried-- }
	}

	fun acted(nowMs: Long = clock()) {
		if ((depth > 0 || carried > 0) && overRealMenu()) return
		if (depth > 0) {
			arm(nowMs)
			if (!sounded) {
				sounded = true; played++
				playingOwn = true
				try { sound() } finally { playingOwn = false }
			}
		} else if (carried > 0) arm(nowMs)
	}

	fun clickSent(nowMs: Long = clock()) = acted(nowMs)

	fun onLocalSound(id: Identifier, nowMs: Long = clock()): Boolean {
		if (playingOwn || depth == 0 || !isClickSound(id)) return false
		if (overRealMenu()) return false
		if (sounded) return true
		sounded = true; arm(nowMs)
		return false
	}

	fun isApexScreen(className: String) = className.startsWith("dev.ashtonite365.apex.")

	fun isClickSound(id: Identifier) = SoundEvents.UI_BUTTON_CLICK.`is`(id)

	val ECHO_SOUNDS: Set<String> = setOf(
		"minecraft:ui.button.click",
		"minecraft:block.lever.click",
		"minecraft:entity.item_frame.add_item",
		"minecraft:entity.item_frame.remove_item",
		"minecraft:entity.item_frame.rotate_item",
		"minecraft:entity.glow_item_frame.add_item",
		"minecraft:entity.glow_item_frame.remove_item",
		"minecraft:entity.glow_item_frame.rotate_item",
		"minecraft:block.wooden_button.click_on",
		"minecraft:block.wooden_button.click_off",
		"minecraft:block.stone_button.click_on",
		"minecraft:block.stone_button.click_off",
		"minecraft:block.bamboo_wood_button.click_on",
		"minecraft:block.bamboo_wood_button.click_off",
		"minecraft:block.cherry_wood_button.click_on",
		"minecraft:block.cherry_wood_button.click_off",
		"minecraft:block.nether_wood_button.click_on",
		"minecraft:block.nether_wood_button.click_off",
		"minecraft:block.comparator.click",
		"minecraft:item.book.page_turn",
		"minecraft:ui.stonecutter.select_recipe",
	)

	fun isEchoSound(id: Identifier) = id.toString() in ECHO_SOUNDS

	fun swallowServerSound(id: Identifier, nowMs: Long = clock()) = isEchoSound(id) && nowMs <= muteUntilMs

	fun swallowServerSoundNow(id: Identifier) = swallowServerSound(id, clock())

	fun swallowOtherSound(id: Identifier, relative: Boolean, nowMs: Long = clock()) =
		!playingOwn && depth == 0 && !relative && swallowServerSound(id, nowMs)

	private fun arm(nowMs: Long) { if (nowMs + WINDOW_MS > muteUntilMs) muteUntilMs = nowMs + WINDOW_MS }

	@Subscribe
	fun onServerSound(event: SoundReceiveEvent) {
		if (swallowServerSound(event.sound.value().location)) event.cancel()
	}

	fun resetForTests() { overRealMenu = { false }; depth = 0; carried = 0; sounded = false; playingOwn = false; muteUntilMs = Long.MIN_VALUE; played = 0 }
}
