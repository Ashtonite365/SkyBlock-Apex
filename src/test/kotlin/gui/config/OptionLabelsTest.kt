package dev.ashtonite365.apex.test.gui.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.gui.config.OptionLabels

class OptionLabelsTest {
	private val otherMods = Regex("(?i)\\b(yacl|moulconfig|moul|rei|jei|emi|jade|roughly|enough items)\\b")

	@Test fun everyForcedIdHasANeutralLabel() {
		val expected = setOf(
			"apex.config.configconfig.enable-yacl", "apex.config.configconfig.enable-moulconfig",
			"apex.config.configconfig.wide-moulconfig", "apex.config.repo.enable-rei",
		)
		assertEquals(expected, OptionLabels.table.keys)
		for ((key, label) in OptionLabels.table) {
			assertFalse(otherMods.containsMatchIn(label.name), "$key name: ${label.name}")
			assertFalse(otherMods.containsMatchIn(label.description), "$key description: ${label.description}")
			assertEquals(label.name, OptionLabels.label(key)!!.string)
			assertEquals(label.description, OptionLabels.description(key)!!.string)
		}
	}

	@Test fun savedIdsStayInTheMigrationMap() {
		val map = javaClass.getResource("/assets/apex/settings/migration-map.tsv")!!.readText()
			.lines().map { it.substringBefore('\t') }.toSet()
		for (key in OptionLabels.table.keys) assertTrue(key.removePrefix("apex.config.") in map, key)
	}

	@Test fun otherKeysHaveNoOverride() {
		assertEquals(null, OptionLabels.label("apex.config.configconfig.zz-probe"))
		assertEquals(null, OptionLabels.description("apex.config.repo.reload"))
	}
}
