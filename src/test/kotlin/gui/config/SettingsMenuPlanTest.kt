package dev.ashtonite365.apex.test.gui.config

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.gui.config.SettingsEditorKind
import dev.ashtonite365.apex.gui.config.SettingsMenuPlan
import dev.ashtonite365.apex.gui.config.SettingsMenuPlan.Row
import dev.ashtonite365.apex.gui.config.SettingsSliders
import dev.ashtonite365.apex.gui.config.SettingsTabs
import dev.ashtonite365.apex.settings.MainSection
import dev.ashtonite365.apex.settings.SectionPage
import dev.ashtonite365.apex.settings.Subsection
import dev.ashtonite365.apex.settings.SubsectionView

class SettingsMenuPlanTest {
	private val configs by lazy { AllRegisteredConfigs.configs }
	private val pages by lazy { SettingsTabs.pages(configs) }

	private val a = Subsection(MainSection.MISC, "alpha")
	private val b = Subsection(MainSection.MISC, "beta")
	private val elsewhere = Subsection(MainSection.TIMERS, "gamma")
	private val sample = SectionPage(
		MainSection.MISC, "", listOf(SubsectionView(a, listOf("a1", "a2")), SubsectionView(b, listOf("b1"))), listOf(elsewhere))

	@Test
	fun rowsAreShortcutsThenHeaderAndOptionsPerSubsection() {
		assertEquals(
			listOf(Row.Shortcut(elsewhere), Row.Header(720, a), Row.Entry(720, "a1"), Row.Entry(720, "a2"), Row.Header(721, b), Row.Entry(721, "b1")),
			SettingsMenuPlan.rows(sample))
		assertEquals(emptyList<Row<String>>(), SettingsMenuPlan.rows(SectionPage<String>(MainSection.MISC, "", emptyList())))
	}

	@Test
	fun realPagesRestartAccordionIdsAndFoldEveryOptionUnderItsHeader() {
		for (page in pages) {
			val rows = SettingsMenuPlan.rows(page)
			assertEquals(page.links.size, rows.takeWhile { it is Row.Shortcut }.size, page.identifier)
			assertTrue(rows.drop(page.links.size).none { it is Row.Shortcut }, page.identifier)
			val headers = rows.filterIsInstance<Row.Header>()
			assertEquals((720 until 720 + page.subsections.size).toList(), headers.map { it.accordionId }, page.identifier)
			assertEquals(page.subsections.map { it.subsection }, headers.map { it.subsection })
			var current = -1
			for (row in rows) when (row) {
				is Row.Header -> current = row.accordionId
				is Row.Entry -> assertEquals(current, row.accordionId, page.identifier)
				is Row.Shortcut -> {}
			}
			assertEquals(page.subsections.sumOf { it.items.size }, rows.count { it is Row.Entry })
		}
	}

	@Test
	fun yaclGroupsCoverEachSubsectionOnceInPageOrder() {
		val all = MainSection.entries.flatMap { SettingsMenuPlan.groups(it, pages) }
		assertEquals(pages.flatMap { p -> p.subsections.map { p to it } }, all.map { it.page to it.view })
		for (main in MainSection.entries) assertTrue(SettingsMenuPlan.groups(main, pages).all { it.page.main == main })
	}

	@Test
	fun groupNamesArePrefixedOnlyOnGroupPages() {
		for (group in MainSection.entries.flatMap { SettingsMenuPlan.groups(it, pages) }) {
			val own = SettingsTabs.label(group.view.subsection).string
			val expected = if (group.page.group.isEmpty()) own else SettingsTabs.label(group.page).string + ": " + own
			assertEquals(expected, SettingsMenuPlan.groupName(group).string)
		}
		assertTrue(pages.any { it.group.isNotEmpty() }, "no group pages: prefix branch untested")
	}

	@Test
	fun everyShownOptionHasAMoulConfigEditor() {
		val missing = pages.flatMap { p -> p.subsections.flatMap { it.items } }
			.filter { SettingsMenuPlan.moulConfigKind(it.handler) == null }
			.map { SettingsTabs.optionKey(it) + " (" + it.handler.javaClass.simpleName + ")" }
		assertEquals(emptyList<String>(), missing, "MoulConfig screen would fail to open")
	}

	@Test
	fun yaclKindsAgreeWithMoulConfigExceptInputAndWaypoints() {
		val handlers = configs.flatMap { c -> c.sortedOptions.map { it.handler } }
		val unhandled = mutableSetOf<SettingsEditorKind?>()
		for (h in handlers) {
			val moul = SettingsMenuPlan.moulConfigKind(h)
			val yacl = SettingsMenuPlan.yaclKind(h)
			if (yacl == null) unhandled += moul else assertEquals(moul, yacl, h.javaClass.name)
		}
		assertTrue(unhandled.all { it == SettingsEditorKind.INT_INPUT || it == SettingsEditorKind.WAYPOINT_MANAGER }, "unhandled in YACL: $unhandled")
	}

	@Test
	fun sliderConversions() {
		assertEquals(1.5, SettingsSliders.seconds(1500.milliseconds))
		assertEquals(2.25.seconds, SettingsSliders.duration(2.25))
		assertEquals(0.5f, SettingsSliders.position(500.milliseconds))
		assertEquals(250.milliseconds, SettingsSliders.duration(0.25f))
		assertEquals(7f, SettingsSliders.position(7))
		assertEquals(3, SettingsSliders.whole(3.99f))
		assertEquals(-3, SettingsSliders.whole(-3.99f))
		assertEquals(0.1, SettingsSliders.DURATION_STEP)
		assertEquals(60_000 / 10, SettingsSliders.CHROMA_SPEED_MAX_MS / SettingsSliders.CHROMA_SPEED_STEP_MS)
	}

	@Test
	fun unknownHandlerHasNoKind() {
		val stranger = object : dev.ashtonite365.apex.util.data.ManagedConfig.OptionCodec<Int> {
			override fun encode(element: Int): kotlinx.serialization.json.JsonElement? = null
			override fun decode(element: kotlinx.serialization.json.JsonElement) = 0
			override fun appendRows(opt: dev.ashtonite365.apex.gui.config.ManagedOption<Int>, guiAppender: dev.ashtonite365.apex.gui.config.GuiAppender) = Unit
		}
		assertNull(SettingsMenuPlan.moulConfigKind(stranger))
		assertNull(SettingsMenuPlan.yaclKind(stranger))
	}
}
