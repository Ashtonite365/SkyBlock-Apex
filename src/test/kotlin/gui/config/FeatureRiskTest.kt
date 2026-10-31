// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.gui.config

import java.io.File
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.gui.config.DangerousSettings
import dev.ashtonite365.apex.gui.config.FeatureRisk
import dev.ashtonite365.apex.gui.config.ManagedOption
import dev.ashtonite365.apex.gui.config.RiskDisclaimer
import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.gui.config.SettingsTabs

class FeatureRiskTest {
	private val configs by lazy { AllRegisteredConfigs.configs }
	private val byKey by lazy { configs.flatMap { it.sortedOptions }.associateBy { FeatureRisk.key(it) } }
	private val switch: ManagedOption<Boolean> get() = DangerousSettings.TConfig.allOptions.getValue(DangerousSettings.OPTION) as ManagedOption<Boolean>
	private val defaultConfirmer = DangerousSettings.confirmer
	private val defaultSave = DangerousSettings.save
	private var saved = 0

	private fun setSwitchRaw(on: Boolean) { switch._actualValue = on }

	@AfterEach fun reset() { DangerousSettings.confirmer = defaultConfirmer; DangerousSettings.save = defaultSave; setSwitchRaw(false) }

	@Test fun everyTagResolves() {
		val names = configs.map { it.name }.toSet()
		for (t in FeatureRisk.HARD + FeatureRisk.SOFT) {
			if ('.' in t) assertTrue(t in byKey, "tagged option $t is not registered")
			else assertTrue(t in names, "tagged config $t is not registered")
		}
		for ((c, opts) in FeatureRisk.MASTERS) for (o in opts) assertTrue("$c.$o" in byKey, "master $c.$o is not registered")
		for (t in FeatureRisk.HARD + FeatureRisk.SOFT) if ('.' !in t) assertTrue(t in FeatureRisk.MASTERS, "$t needs its master options")

		assertTrue(byKey.values.none { (it.element.name == "bz-menu" || it.element.name == "custom-menu-bazaar") && FeatureRisk.tierOf(it) != null })
	}

	@Test fun everyTaggedFeatureDefaultsOff() {
		val keys = FeatureRisk.defaultOffKeys(FeatureRisk.HARD + FeatureRisk.SOFT)

		val table = dev.ashtonite365.apex.modules.ModuleTable.DEFAULT
		val gatedOptions = table.rows.filter { it.kind == dev.ashtonite365.apex.modules.RowKind.OPTION && it.tag.gated }.map { it.key }
		assertTrue(gatedOptions.size >= 25)
		assertTrue(keys.containsAll(gatedOptions))
		for (m in table.modules.filter { it.tag.gated && it.master.isNotEmpty() }) assertTrue("${m.key}.${m.master}" in keys, m.key)
		for (k in keys) assertEquals(false, byKey.getValue(k).default(), "$k (soft/hard gray) must default OFF")
	}

	@Test fun everySoftEntryShowsTheText() {
		assertEquals("This could be potentially bannable, use at your own risk.", FeatureRisk.SOFT_TEXT)
		val soft = byKey.values.filter { FeatureRisk.tierOf(it) == FeatureRisk.Tier.SOFT }
		assertTrue(soft.size >= FeatureRisk.SOFT.size)
		for (o in soft) assertTrue(o.labelDescription.string.contains(FeatureRisk.SOFT_TEXT), "${FeatureRisk.key(o)} lacks the soft text")
		for (o in byKey.values.filter { FeatureRisk.tierOf(it) == null }) assertFalse(o.labelDescription.string.contains(FeatureRisk.SOFT_TEXT), FeatureRisk.key(o))

		assertTrue(dev.ashtonite365.apex.test.testutil.RepoFile("src/main/resources/assets/apex/gui/config/customchatcommands/index.xml").readText().contains(FeatureRisk.SOFT_TEXT))
		assertEquals(listOf("custom-chat-commands"), FeatureRisk.SOFT_SCREENS)
	}

	private fun hardOptions() = byKey.values.filter { FeatureRisk.tierOf(it) == FeatureRisk.Tier.HARD }

	@Test fun hardFeaturesHiddenAndOffWhileSwitchOff() {
		setSwitchRaw(false)
		val hard = hardOptions()
		assertTrue(hard.map { FeatureRisk.key(it) }.containsAll(FeatureRisk.HARD.filter { '.' in it }))
		val listed = SettingsTabs.items(configs).map { "${it.config}.${it.key}" }.toSet()
		for (o in hard) {
			assertFalse(FeatureRisk.key(o) in listed, "${FeatureRisk.key(o)} must be hidden while the switch is off")
			assertFalse(FeatureRisk.visibleInMenus(o))
		}

		val o = byKey.getValue("terminal-solver.block-wrong-clicks") as ManagedOption<Boolean>
		val before = o._actualValue
		o._actualValue = true
		try {
			assertEquals(false, o.value)
			assertEquals(JsonPrimitive(true), o.toJson())
		} finally { o._actualValue = before }

		val wallsMain = SettingsTabs.mainSectionOf(byKey.values.first { it.element.name == "breakable-walls" })
		assertTrue(SettingsTabs.configsIn(wallsMain, configs).none { it.name == "breakable-walls" })
		val partyMain = SettingsTabs.mainSectionOf(byKey.values.first { it.element.name == "party-commands" })
		assertTrue(SettingsTabs.configsIn(partyMain, configs).any { it.name == "party-commands" })
	}

	@Test fun hardFeaturesVisibleWhileSwitchOn() {
		setSwitchRaw(true)
		val listed = SettingsTabs.items(configs).map { "${it.config}.${it.key}" }.toSet()
		for (o in hardOptions()) assertTrue(FeatureRisk.key(o) in listed, "${FeatureRisk.key(o)} must show while the switch is on")
		val o = byKey.getValue("terminal-solver.block-wrong-clicks") as ManagedOption<Boolean>
		val before = o._actualValue
		o._actualValue = true
		try { assertEquals(true, o.value) } finally { o._actualValue = before }
	}

	@Test fun confirmationGatesTheFlip() {
		assertEquals("Allow borderline bannable features", DangerousSettings.TITLE)
		assertEquals(false, switch.default())
		setSwitchRaw(false)
		DangerousSettings.save = { saved++ }
		var pending: ((Boolean) -> Unit)? = null
		var asked = 0
		DangerousSettings.confirmer = { target, answer -> asked++; assertTrue(target); pending = answer }
		switch.value = true
		assertEquals(1, asked)
		assertFalse(DangerousSettings.allowed, "nothing changes before Yes")
		pending!!(false)
		assertFalse(DangerousSettings.allowed)
		switch.value = true
		pending!!(true)
		assertTrue(DangerousSettings.allowed)
		assertEquals(2, asked)

		DangerousSettings.confirmer = { target, answer -> assertFalse(target); pending = answer }
		switch.value = false
		assertTrue(DangerousSettings.allowed)
		pending!!(true)
		assertFalse(DangerousSettings.allowed)

		assertEquals(2, saved)
		assertEquals(JsonPrimitive(false), switch.toJson())

		assertEquals(dev.ashtonite365.apex.settings.MainSection.MISC, SettingsTabs.mainSectionOf(switch))
	}

	@Test fun purpleBoxOnHardEntriesOnly() {
		for (o in byKey.values) assertEquals(FeatureRisk.tierOf(o) == FeatureRisk.Tier.HARD, FeatureRisk.purpleBoxed(o), FeatureRisk.key(o))

		val styled = dev.ashtonite365.apex.test.testutil.RepoFile("src/main/kotlin/gui/config/styled/StyledSettingsScreen.kt").readText()
		assertTrue(styled.contains("if (FeatureRisk.purpleBoxed(o)) {"))
		assertTrue(styled.contains("\"⚠ \" + FeatureRisk.HARD_HEADER"))
		val mc = dev.ashtonite365.apex.test.testutil.RepoFile("src/main/java/dev/ashtonite365/apex/util/data/ManagedConfig.kt").readText()
		assertTrue(mc.contains("if (FeatureRisk.purpleBoxed(option)) ColumnComponent(header, TextComponent(\"⚠ \" + FeatureRisk.HARD_HEADER), scroller)"))
		assertFalse(FeatureRisk.HARD_HEADER.contains("Mouse Tweaks"))
	}

	@Test fun lostAdventurerForcedOffAndHidden() {
		val o = byKey.getValue("puzzle-solvers.lost-adventurer")
		assertFalse(FeatureRisk.visibleInMenus(o))
		setSwitchRaw(true)
		assertFalse(FeatureRisk.visibleInMenus(o))
		assertTrue(SettingsTabs.items(configs).none { it.config == "puzzle-solvers" && it.key == "lost-adventurer" })
	}

	@Test fun oneTimeMenuDisclaimer() {
		val data = RiskDisclaimer.Data()
		var shown: String? = null
		var ok: (() -> Unit)? = null
		var saves = 0
		assertTrue(RiskDisclaimer.onApexMenuOpen(data, { saves++ }) { text, k -> shown = text; ok = k })
		assertEquals("some of these modifications are potentially bannable or moderator dependent, so use at your own risk.", shown)
		ok!!()
		assertTrue(data.menuShown)
		assertEquals(1, saves)
		shown = null
		assertFalse(RiskDisclaimer.onApexMenuOpen(data, { saves++ }) { text, _ -> shown = text })
		assertNull(shown)
		assertTrue(dev.ashtonite365.apex.test.testutil.RepoFile("src/main/kotlin/gui/config/AllConfigsGui.kt").readText().contains("RiskDisclaimer.wrapApexMenu { makeMenuScreen(search, parent) }"))
	}

	@Test fun tableTagsShowTheWarning() {
		val tableOnly = byKey.getValue("ah-price-tools.lbin-minus-one")
		assertEquals(RiskLevel.NONE, tableOnly.riskLevel)
		assertEquals(RiskLevel.GREY_AREA, tableOnly.shownRisk)
		assertTrue(tableOnly.labelText.string.startsWith("⚠ "))
		assertEquals(RiskLevel.RISKY, byKey.getValue("breakable-walls.enabled").shownRisk)

		for (o in byKey.values) assertEquals(FeatureRisk.TABLE_RISK[FeatureRisk.key(o)] ?: RiskLevel.NONE, o.shownRisk, FeatureRisk.key(o))
		val codeOnly = byKey.values.filter { it.riskLevel.isRisky && FeatureRisk.key(it) !in FeatureRisk.TABLE_RISK }
		assertTrue(codeOnly.isNotEmpty() && codeOnly.all { !it.shownRisk.isRisky && !it.labelText.string.startsWith("⚠ ") })
		for ((k, _) in FeatureRisk.TABLE_RISK) assertTrue(k in byKey, "table-tagged $k is not registered")

		assertNull(FeatureRisk.tierOf("terminal-solver", "first-click-protection"))
	}
}
