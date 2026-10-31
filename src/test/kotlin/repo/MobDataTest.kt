package dev.ashtonite365.apex.test.repo

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.repo.mobs.MobDrop
import dev.ashtonite365.apex.repo.mobs.MobIndex
import dev.ashtonite365.apex.repo.mobs.MobRenderPlanner

class MobDataTest {
	private val scathaSpec = JsonParser.parseString(
		"""{"entity":"ArmorStand","modifiers":[{"type":"invisible"},{"type":"equipment","helmet":"SCATHA_MONSTER"}]}"""
	).asJsonObject

	private fun scathaRecipe(host: String = "SCATHA_MONSTER") = MobIndex.DropRecipeInput(
		host, "§aScatha", 10, 10, mapOf("combat" to 50), "crystal_hollows", listOf(), "@neurepo:mobs/scatha.json",
		listOf(MobDrop("FINE_TOPAZ_GEM", 1, "100%"), MobDrop("WORM_MEMBRANE", 4, "100%")),
	)

	@Test
	fun mergesRecipesAndMobsFolder() {
		val extended = JsonParser.parseString(
			"""{"name":"Ghost","level":250,"drops":[{"id":"SORROW:1","chance":"0.1%"},{"id":""}],"unknown":{"x":1},"entity":"Zombie"}"""
		).asJsonObject
		val built = MobIndex.build(listOf(scathaRecipe(), scathaRecipe("OTHER_HOST")),
		                           mapOf("scatha" to scathaSpec, "ghost" to extended, "angry_archeologist" to scathaSpec))
		val scatha = built.getValue("scatha")
		assertEquals("§aScatha", scatha.name)
		assertEquals("Scatha", scatha.plainName)
		assertEquals(2, scatha.drops.size)
		assertEquals(scathaSpec, scatha.renderSpec)
		assertEquals("SCATHA_MONSTER", scatha.hostItem)
		val ghost = built.getValue("ghost")
		assertEquals(250, ghost.level)
		assertEquals(listOf(MobDrop("SORROW", 1, "0.1%")), ghost.drops)
		assertEquals("Angry Archeologist", built.getValue("angry_archeologist").name)
		MobIndex.install(built)
		assertEquals(listOf("scatha"), MobIndex.mobsDropping("FINE_TOPAZ_GEM").map { it.id })
	}

	@Test
	fun hostItemIsTheIdWithoutMobFileReference() {
		val r = scathaRecipe().copy(render = "Silverfish", hostItem = "WORM_MONSTER")
		assertEquals("worm_monster", MobIndex.build(listOf(r), mapOf()).keys.single())
	}

	@Test
	fun scathaRendersAsSilverfishNotARedBox() {
		val mob = MobIndex.build(listOf(scathaRecipe()), mapOf("scatha" to scathaSpec)).getValue("scatha")
		assertTrue(MobRenderPlanner.isHeadOnly(scathaSpec))
		val plan = MobRenderPlanner.plan(mob) { null }
		assertEquals("Silverfish", plan.entity)
		assertTrue(plan.fromHint)
		val thing = mob.copy(id = "thing", renderSpec = null, render = "@neurepo:mobs/x.json", name = "Thing")
		val resolved = MobRenderPlanner.plan(thing) { JsonParser.parseString("""{"entity":"Zombie"}""").asJsonObject }
		assertEquals("Zombie", resolved.entity)
		assertFalse(resolved.fromHint || resolved.unknown)
	}

	@Test
	fun missingOrEmptyRenderUsesTheQuestionMarkModel() {
		val base = MobIndex.build(listOf(scathaRecipe().copy(render = "", hostItem = "MYSTERY", name = "§cMystery")), mapOf()).getValue("mystery")
		for (m in listOf(base, base.copy(render = null), base.copy(render = "   "), base.copy(render = "NotAnEntity"),
		                 base.copy(render = "@neurepo:mobs/missing.json"))) {
			val p = MobRenderPlanner.plan(m, { it == "Zombie" || it == "Player" }) { null }
			assertTrue(p.unknown, "$m")
			assertEquals("Player", p.entity)
			assertEquals(MobRenderPlanner.UNKNOWN_SKIN, p.modifiers.first().get("skin").asString)
			assertEquals("§cMystery", p.modifiers.first { it.get("type").asString == "name" }.get("name").asString)
		}
		assertTrue(javaClass.getResource("/assets/apex/textures/entity/unknown_mob.png") != null)
	}

	@Test
	fun everyRepoMobResolvesToARender() {

		val base = java.nio.file.Path.of(System.getProperty("apex.testrepo"))
		val repo = io.github.moulberry.repo.NEURepository.of(base).also { it.reload() }
		val drops = repo.items.items.values.flatMap { item ->
			item.recipes.filterIsInstance<io.github.moulberry.repo.data.NEUMobDropRecipe>().map { MobIndex.fromRecipe(item.skyblockItemId, it) }
		}
		val mobs = MobIndex.build(drops, MobIndex.readMobFiles(base)).values
		val known = dev.ashtonite365.apex.repo.mobs.KnownEntityNames.names

		val src = java.io.File("../../src/main/kotlin/gui/entity/EntityRenderer.kt").takeIf { it.exists() }?.readText()
		if (src != null) {
			val block = src.substringAfter("val entityIds").substringBefore("\n\t)")
			assertEquals(Regex("\"([A-Za-z]+)\" to").findAll(block).map { it.groupValues[1] }.toSet(), known)
		}
		assertTrue(mobs.isNotEmpty())
		var unknown = 0; var hinted = 0
		for (mob in mobs) {
			val plan = MobRenderPlanner.plan(mob, known::contains) { ref ->
				runCatching { JsonParser.parseString(base.resolve(ref.substringAfter(':')).toFile().readText()).asJsonObject }.getOrNull()
			}
			assertTrue(plan.entity in known, "${mob.id} -> ${plan.entity}")
			if (plan.unknown) unknown++
			if (plan.fromHint) hinted++
		}
		println("APEX-MOBS total=${mobs.size} unknownModel=$unknown hinted=$hinted withDrops=${mobs.count { it.drops.isNotEmpty() }}")
	}
}
