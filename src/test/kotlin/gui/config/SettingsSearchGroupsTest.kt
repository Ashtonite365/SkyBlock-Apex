package dev.ashtonite365.apex.test.gui.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.gui.config.styled.StyledSettingsScreen
import dev.ashtonite365.apex.settings.MainSection
import dev.ashtonite365.apex.settings.SectionPage
import dev.ashtonite365.apex.settings.Subsection
import dev.ashtonite365.apex.settings.SubsectionView

class SettingsSearchGroupsTest {
	private val water = Subsection(MainSection.DUNGEONS, "water-board")
	private val boulder = Subsection(MainSection.DUNGEONS, "boulder")
	private val garden = Subsection(MainSection.FARMING, "garden")
	private val pages = listOf(
		SectionPage(MainSection.DUNGEONS, "", listOf(SubsectionView(water, listOf("water.Enable", "water.Show path")), SubsectionView(boulder, listOf("boulder.Enable")))),
		SectionPage(MainSection.FARMING, "", listOf(SubsectionView(garden, listOf("garden.Plot icons")))),
	)
	private val names = mapOf(water to "Water Board", boulder to "Boulder", garden to "Garden")
	private fun search(q: String) = StyledSettingsScreen.searchGroups(pages, q, { "${it.main.name} › ${names.getValue(it)}" }, { it.substringAfter('.') }, { "" })

	@Test fun sameNameSettingsAppearUnderTheirOwnModuleHeaders() {
		assertEquals(listOf("DUNGEONS › Water Board" to listOf("water.Enable"), "DUNGEONS › Boulder" to listOf("boulder.Enable")), search("enable"))
	}

	@Test fun moduleNameMatchShowsAllItsSettings() {
		assertEquals(listOf("DUNGEONS › Water Board" to listOf("water.Enable", "water.Show path")), search("water board"))
	}

	@Test fun groupsKeepMenuOrderAndEmptyGroupsAreLeftOut() {
		assertEquals(listOf("FARMING › Garden"), search("plot").map { it.first })
		assertEquals(emptyList<Pair<String, List<String>>>(), search("zzz"))
	}
}
