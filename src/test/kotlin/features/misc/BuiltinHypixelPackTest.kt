package dev.ashtonite365.apex.test.features.misc

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.misc.BuiltinHypixelPack
import dev.ashtonite365.apex.features.misc.BuiltinHypixelPack.ToggleAction

class BuiltinHypixelPackTest {
	private val kept = listOf(
		"assets/minecraft/font/default.json",
		"assets/hypixel_skyblock/textures/gui/stats.png",
		"assets/hypixel_skyblock/textures/gui/icons.png",
		"assets/hypixel_skyblock/textures/gui/icons/staff.png",
		"assets/hypixel_skyblock/textures/gui/sprites/tooltip/legendary_frame.png",
		"assets/hypixel_skyblock/textures/gui/sprites/tooltip/legendary_frame.png.mcmeta",
		"assets/minecraft/textures/gui/sprites/tooltip/background.png",
		"assets/minecraft/shaders/core/text.vsh",
		"assets/minecraft/shaders/include/modify_vanilla_color.glsl",
		"pack.mcmeta", "LICENSE",
	)
	private val dropped = listOf(
		"assets/hypixel_skyblock/items/item/island_relevant/winter/weapons/ice_spray_wand.json",
		"assets/hypixel_skyblock/models/item/island_relevant/winter/weapons/ice_spray_wand.json",
		"assets/hypixel_skyblock/textures/item/island_relevant/winter/weapons/ice_spray_wand.png",
		"assets/minecraft/items/paper.json",
		"assets/minecraft/models/item/diamond_sword.json",
		"assets/minecraft/textures/item/player_head.png",
		"assets/minecraft/shaders/core/item.fsh",
		"assets/minecraft/textures/gui/sprites/hud/armor_full.png",
		"assets/minecraft/textures/gui/title/background/panorama_0.png",
		"assets/hypixel_skyblock/sounds/music/hub.ogg",
		"assets/hypixel_skyblock/sounds.json",
		"assets/hypixel_skyblock/textures/gui/sprites/tooltip/../../../item/x.png",
	)

	@Test
	fun glyphsAndTooltipsStayInBothStates() {
		for (skip in listOf(true, false)) for (p in kept) assertTrue(BuiltinHypixelPack.serves(p, skip), "$p skip=$skip")
	}

	@Test
	fun itemContentDropsInBothStates() {
		for (skip in listOf(true, false)) for (p in dropped) assertFalse(BuiltinHypixelPack.serves(p, skip), "$p skip=$skip")
	}

	@Test
	fun referenceCopyOnlyContainsServedPaths() {

		val project = File(System.getProperty("apex.projectDir") ?: error("apex.projectDir not set by the build"))
		val root = File(project, "reference/hypixel_skyblock-trimmed")
		org.junit.jupiter.api.Assumptions.assumeTrue(root.isDirectory, "reference copy not in this source tree")
		val files = root.walkTopDown().filter { it.isFile }.map { it.relativeTo(root).invariantSeparatorsPath }.toList()
		assertTrue(files.contains("assets/minecraft/font/default.json"))
		val bad = files.filterNot { BuiltinHypixelPack.serves(it, true) && BuiltinHypixelPack.serves(it, false) }
		assertEquals(listOf<String>(), bad)
	}

	@Test
	fun toggleTriggersReload() {

		assertEquals(ToggleAction.NONE, BuiltinHypixelPack.onToggle(null, true, true, true))
		assertEquals(ToggleAction.NONE, BuiltinHypixelPack.onToggle(true, true, true, true))
		assertEquals(ToggleAction.NONE, BuiltinHypixelPack.onToggle(false, false, true, false))

		assertEquals(ToggleAction.POP_SERVER_PACK, BuiltinHypixelPack.onToggle(false, true, true, true))
		assertEquals(ToggleAction.RELOAD, BuiltinHypixelPack.onToggle(false, true, true, false))
		assertEquals(ToggleAction.RELOAD, BuiltinHypixelPack.onToggle(false, true, false, false))

		assertEquals(ToggleAction.REAPPLY_SERVER_PACK, BuiltinHypixelPack.onToggle(true, false, true, false))
		assertEquals(ToggleAction.RELOAD, BuiltinHypixelPack.onToggle(true, false, false, false))

		for (prev in listOf(true, false)) for (h in listOf(true, false)) for (a in listOf(true, false))
			assertTrue(BuiltinHypixelPack.onToggle(prev, !prev, h, a) != ToggleAction.NONE)
	}
}
