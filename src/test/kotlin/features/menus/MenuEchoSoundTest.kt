// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import net.minecraft.core.Holder
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.phys.Vec3
import dev.ashtonite365.apex.events.SoundReceiveEvent
import dev.ashtonite365.apex.features.menus.MenuPress

@ExtendWith(AutoBootstrapExtension::class)
class MenuEchoSoundTest {
	private var now = 50_000L
	private var localSounds = 0
	private val oldSound = MenuPress.sound; private val oldClock = MenuPress.clock
	private val click = Identifier.parse("minecraft:ui.button.click")

	init {
		MenuPress.resetForTests()
		MenuPress.clock = { now }
		MenuPress.sound = {
			localSounds++

			Assertions.assertFalse(MenuPress.onLocalSound(click, now))
			Assertions.assertFalse(MenuPress.swallowOtherSound(click, relative = true, nowMs = now))
			Assertions.assertFalse(MenuPress.swallowOtherSound(click, relative = false, nowMs = now))
		}
	}
	@AfterEach fun reset() { MenuPress.sound = oldSound; MenuPress.clock = oldClock; MenuPress.resetForTests() }

	private fun id(s: String) = Identifier.parse(s)
	private fun holder(s: String): Holder<SoundEvent> = Holder.direct(SoundEvent.createVariableRangeEvent(id(s)))

	private fun positional(s: String): Boolean {
		val e = SoundReceiveEvent(holder(s), SoundSource.BLOCKS, Vec3(-27.9, 121.0, 1.0), 1f, 0.5f, 0L)
		MenuPress.onServerSound(e)
		return !e.cancelled
	}

	private fun entity(s: String): Boolean = !MenuPress.swallowServerSoundNow(holder(s).value().location())

	private fun other(s: String, relative: Boolean = false): Boolean =
		!MenuPress.onLocalSound(id(s), now) && !MenuPress.swallowOtherSound(id(s), relative, now)

	private fun press() = MenuPress.press { MenuPress.acted() }

	private val fancy = listOf(
		"minecraft:entity.experience_orb.pickup", "minecraft:entity.player.levelup", "minecraft:block.note_block.harp",
		"minecraft:block.anvil.use", "minecraft:block.note_block.hat", "minecraft:block.note_block.pling",
		"minecraft:block.note_block.bell", "minecraft:entity.enderman.teleport", "minecraft:entity.wither.spawn",
		"minecraft:entity.ender_dragon.growl", "minecraft:entity.ender_pearl.throw", "minecraft:block.amethyst_block.chime",
		"minecraft:entity.splash_potion.break", "minecraft:ui.toast.challenge_complete", "minecraft:ui.stonecutter.take_result",
		"minecraft:ui.loom.take_result", "minecraft:block.note_block.chime",
	)

	@Test
	fun allowListHasTheLoggedEchoesAndOnlyRealIds() {
		for (s in listOf("minecraft:ui.button.click", "minecraft:block.lever.click", "minecraft:entity.item_frame.add_item"))
			Assertions.assertTrue(s in MenuPress.ECHO_SOUNDS, s)
		val vanilla = listOf(
			SoundEvents.UI_BUTTON_CLICK.value(), SoundEvents.LEVER_CLICK, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundEvents.ITEM_FRAME_REMOVE_ITEM,
			SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundEvents.GLOW_ITEM_FRAME_ADD_ITEM, SoundEvents.GLOW_ITEM_FRAME_REMOVE_ITEM,
			SoundEvents.GLOW_ITEM_FRAME_ROTATE_ITEM, SoundEvents.WOODEN_BUTTON_CLICK_ON, SoundEvents.WOODEN_BUTTON_CLICK_OFF,
			SoundEvents.STONE_BUTTON_CLICK_ON, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundEvents.BAMBOO_WOOD_BUTTON_CLICK_ON,
			SoundEvents.BAMBOO_WOOD_BUTTON_CLICK_OFF, SoundEvents.CHERRY_WOOD_BUTTON_CLICK_ON, SoundEvents.CHERRY_WOOD_BUTTON_CLICK_OFF,
			SoundEvents.NETHER_WOOD_BUTTON_CLICK_ON, SoundEvents.NETHER_WOOD_BUTTON_CLICK_OFF, SoundEvents.COMPARATOR_CLICK,
			SoundEvents.BOOK_PAGE_TURN, SoundEvents.UI_STONECUTTER_SELECT_RECIPE,
		).map { it.location().toString() }.toSet()
		Assertions.assertEquals(vanilla, MenuPress.ECHO_SOUNDS)
		for (s in fancy) Assertions.assertFalse(s in MenuPress.ECHO_SOUNDS, s)
	}

	@Test
	fun everyEchoIdIsMutedInsideTheWindowOnEveryRoute() {
		press()
		Assertions.assertEquals(1, localSounds)
		for (s in MenuPress.ECHO_SOUNDS) {
			now = 50_000L + 300
			Assertions.assertFalse(positional(s), "positional $s")
			Assertions.assertFalse(entity(s), "entity $s")
			Assertions.assertFalse(other(s), "other route $s")
			now = 50_000L + MenuPress.WINDOW_MS
			Assertions.assertFalse(positional(s), "positional $s at window end")
			Assertions.assertFalse(entity(s), "entity $s at window end")
		}
	}

	@Test
	fun echoIdsPlayOutsideTheWindow() {
		for (s in MenuPress.ECHO_SOUNDS) {
			Assertions.assertTrue(positional(s), s); Assertions.assertTrue(entity(s), s); Assertions.assertTrue(other(s), s)
		}
		press()
		now += MenuPress.WINDOW_MS + 1
		for (s in MenuPress.ECHO_SOUNDS) {
			Assertions.assertTrue(positional(s), s); Assertions.assertTrue(entity(s), s); Assertions.assertTrue(other(s), s)
		}
		Assertions.assertEquals(1, localSounds)
	}

	@Test
	fun fancySoundsAreNeverMuted() {
		press()
		now += 250
		for (s in fancy) {
			Assertions.assertTrue(positional(s), "positional $s")
			Assertions.assertTrue(entity(s), "entity $s")
			Assertions.assertTrue(other(s), "other $s")
		}
	}

	@Test
	fun apexOwnClickAndLocalGuiSoundsStillPlay() {
		press()
		Assertions.assertEquals(1, localSounds)
		now += 200

		Assertions.assertTrue(other("minecraft:ui.button.click", relative = true))

		MenuPress.press {
			Assertions.assertFalse(MenuPress.swallowOtherSound(click, relative = false, nowMs = now))
			MenuPress.acted()
		}
		Assertions.assertEquals(2, localSounds)
	}

	@Test
	fun realLogSequenceGivesExactlyOneAudibleClick() {
		var audible = 0
		val t0 = now
		MenuPress.press { MenuPress.acted() }
		audible += localSounds
		now = t0 + 223
		now = t0 + 265
		if (positional("minecraft:block.lever.click")) audible++
		Assertions.assertEquals(1, audible)

		now = t0 + 10_000; localSounds = 0; audible = 0
		MenuPress.press { MenuPress.acted() }; audible += localSounds
		now += 330
		if (positional("minecraft:entity.item_frame.add_item")) audible++
		if (entity("minecraft:entity.item_frame.add_item")) audible++
		Assertions.assertEquals(1, audible)
	}

	private val root = listOf("src/main", "../src/main", "../../src/main").map { java.io.File(it).absoluteFile.normalize() }
		.first { java.io.File(it, "kotlin/features/menus/MenuPress.kt").isFile }
	private fun text(rel: String) = java.io.File(root, rel).readText()

	@Test
	fun everySoundRouteIsHooked() {
		Assertions.assertTrue(text("java/dev/ashtonite365/apex/mixins/SoundReceiveEventPatch.java").contains("method = \"handleSoundEvent\""))
		val entity = text("java/dev/ashtonite365/apex/mixins/MenuEchoEntitySoundPatch.java")
		Assertions.assertTrue(entity.contains("method = \"handleSoundEntityEvent\""))
		Assertions.assertTrue(entity.contains("ClientLevel;playSeededSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V"))
		Assertions.assertTrue(entity.contains("MenuPress.INSTANCE.swallowServerSoundNow("))
		val manager = text("java/dev/ashtonite365/apex/mixins/MenuPressSoundPatch.java")
		Assertions.assertTrue(manager.contains("MenuPress.INSTANCE.swallowOtherSound(sound.getIdentifier(), sound.isRelative(),"))
	}
}
