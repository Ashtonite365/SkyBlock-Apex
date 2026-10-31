package dev.ashtonite365.apex.test.features.render

import net.minecraft.client.renderer.fog.FogData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.render.NoFog
import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.settings.MainSection
import dev.ashtonite365.apex.settings.MigrationMap

class NoFogTest {
	@Test
	fun everywhereWhenEnabledNoIslandCondition() {
		assertTrue(NoFog.shouldClear(true))
		assertFalse(NoFog.shouldClear(false))
	}

	@Test
	fun clearsDistanceAndEveryEnvironmentFog() {

		for ((s, e) in listOf(0F to 96F, 0F to 2F, 0F to 5F, 0F to 15F)) {
			val d = FogData().apply { environmentalStart = s; environmentalEnd = e; renderDistanceStart = 172F; renderDistanceEnd = 192F }
			NoFog.clear(d, 192F, 128F); NoFog.clearDistance(d, 192F)
			assertTrue(d.environmentalStart >= 384F && d.environmentalEnd >= 384F)
			assertTrue(d.renderDistanceStart >= 384F && d.renderDistanceEnd >= 384F)
		}
	}

	@Test
	fun clearPushesEnvironmentalFogPastRenderDistance() {

		val lava = FogData().apply { environmentalStart = 0.25F; environmentalEnd = 1F; skyEnd = 1F; cloudEnd = 1F }
		NoFog.clear(lava, 192F, 128F)
		assertEquals(384F, lava.environmentalStart)
		assertEquals(384F, lava.environmentalEnd)
		assertEquals(192F, lava.skyEnd)
		assertEquals(128F, lava.cloudEnd)

		val nether = FogData().apply { environmentalStart = 10F; environmentalEnd = 96F; renderDistanceStart = 172F; renderDistanceEnd = 192F; cloudEnd = 2048F }
		NoFog.clear(nether, 192F, 128F)
		assertTrue(nether.environmentalStart >= 192F * 1.5F)
		assertEquals(172F, nether.renderDistanceStart)
		assertEquals(192F, nether.renderDistanceEnd)
		assertEquals(2048F, nether.cloudEnd)
	}

	@Test
	fun keyAndValueKeptOneToggle() {
		dev.ashtonite365.apex.test.ApexTestBootstrap.bootstrapMinecraft()
		val opt = NoFog.TConfig.sortedOptions.single()
		assertEquals("no-fog", NoFog.TConfig.name)
		assertEquals("crimson-isle", opt.propertyName)
		assertEquals(true, opt.default())
		assertEquals(RiskLevel.NONE, opt.riskLevel)
		assertFalse(opt.labelText.string.startsWith("⚠"))
	}

	@Test
	fun shownInMiscRenderTab() {
		val e = MigrationMap.bundled()["no-fog.crimson-isle"]!!
		assertEquals(MainSection.MISC, e.newPath.main)
		assertEquals("visibility", e.newPath.subsection)
		assertEquals("render", e.group)

		assertEquals(MigrationMap.bundled()["fullbright.enabled"]!!.group, e.group)
	}
}
