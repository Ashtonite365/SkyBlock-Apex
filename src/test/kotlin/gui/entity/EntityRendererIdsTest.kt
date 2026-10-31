package dev.ashtonite365.apex.test.gui.entity

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.gui.entity.EntityRenderer
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension

@ExtendWith(AutoBootstrapExtension::class)
class EntityRendererIdsTest {

	private val repoIds = listOf("ArmorStand", "Bee", "Blaze", "Bogged", "Chicken", "Cow", "Creeper", "Drowned", "Eisengolem",
		"Enderman", "Endermite", "Frog", "Ghast", "Guardian", "Hoglin", "Horse", "MagmaCube", "Pig", "Pigman", "Player", "Sheep",
		"SiNelverfish", "Skeleton", "SkeletonHorse", "Slime", "Sniffer", "Spider", "Squid", "Stray", "Strider", "Witch",
		"WitherSkeleton", "Wolf", "Zombie", "Parrot", "Allay", "Nautilus", "Parched")
	private val repoModifiers = listOf("equipment", "playerdata", "invisible", "riding", "age", "horse", "charged", "size")

	@Test
	fun everyRepoEntityIdIsMapped() {
		val missing = repoIds.filter { it !in EntityRenderer.entityIds }
		assertTrue(missing.isEmpty(), "unmapped entity ids: $missing")
	}

	@Test
	fun everyRepoModifierIsMapped() {
		val missing = repoModifiers.filter { it !in EntityRenderer.entityModifiers }
		assertTrue(missing.isEmpty(), "unmapped modifiers: $missing")
	}
}
