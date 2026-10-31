// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.ours.apis

import io.github.moulberry.repo.NEURepository
import java.nio.file.Files
import kotlinx.serialization.encodeToString
import kotlin.io.path.writeBytes
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.apis.CollectionCategory
import dev.ashtonite365.apex.apis.CollectionResponse
import dev.ashtonite365.apex.apis.CollectionType
import dev.ashtonite365.apex.apis.Member
import dev.ashtonite365.apex.apis.MowojangNameLookup
import dev.ashtonite365.apex.apis.PlayerResponse
import dev.ashtonite365.apex.apis.Profiles
import dev.ashtonite365.apex.apis.Skill
import dev.ashtonite365.apex.repo.RepoBundle
import dev.ashtonite365.apex.test.ours.OursGolden

class ApisEquivalenceTest {
	init {
		dev.ashtonite365.apex.test.ApexTestBootstrap.bootstrapMinecraft()
	}

	private fun attempt(block: () -> Any?): String = try {
		block().toString()
	} catch (e: Throwable) {
		"ERR:" + e.javaClass.simpleName
	}

	private val profilesJson = """
		{"success":true,"profiles":[
		 {"profile_id":"0123456789abcdef0123456789abcdef","cute_name":"Apple","selected":true,"members":{
		  "fedcba9876543210fedcba9876543210":{"pets":[{"uuid":"00000000000000000000000000000001","type":"BEE","exp":1234.5,"active":true,"tier":"LEGENDARY","candyUsed":3,"heldItem":"PET_ITEM_X","skin":null},
		   {"type":"ROCK","tier":"COMMON"}],
		   "coop_invitation":{"timestamp":1700000000000,"invited_by":"fedcba9876543210fedcba9876543211","confirmed":false},
		   "experience_skill_farming":1.5,"experience_skill_social2":20,"experience_skill_runecrafting":3,"experience_skill_carpentry":4,
		   "experience_skill_alchemy":5,"experience_skill_combat":6,"experience_skill_taming":7,"experience_skill_enchanting":8,
		   "experience_skill_fishing":9,"experience_skill_foraging":10,"experience_skill_mining":11,
		   "collection":{"WHEAT":12,"MUSHROOM_COLLECTION":3,"INK_SACK:3":4}}}},
		 {"profile_id":"11111111111111111111111111111111","cute_name":"Banana","members":{}}
		]}
	""".trimIndent()

	private val playersJson = listOf(
		"""{"success":true,"player":{"uuid":"fedcba9876543210fedcba9876543210","firstLogin":1600000000000,"playername":"Someone"}}""",
		"""{"success":true,"player":{"uuid":"fedcba9876543210fedcba9876543210","firstLogin":1600000000000,"lastLogin":1700000000000,"playername":"Ranked","achievementsOneTime":["a","b"],"newPackageRank":"MVP_PLUS","monthlyPackageRank":"NONE","rankPlusColor":"DARK_GREEN"}}""",
		"""{"success":true,"player":{"uuid":"fedcba9876543210fedcba9876543210","firstLogin":1600000000000,"playername":"Plus","newPackageRank":"VIP","monthlyPackageRank":"SUPERSTAR","rankPlusColor":"NOT_A_COLOUR"}}""",
	)

	private val collectionsJson = """
		{"success":true,"collections":{"FARMING":{"items":{"WHEAT":{"name":"Wheat","maxTiers":2,"tiers":[{"tier":1,"amountRequired":50,"unlocks":["a"]},{"tier":2,"amountRequired":100,"unlocks":[]}]}}},
		 "MINING":{"items":{}}}}
	""".trimIndent()

	@Test
	fun modelsDecodeAndEncodeTheSame() {
		val out = mutableListOf<String>()
		val profiles = Apex.json.decodeFromString<Profiles>(profilesJson)
		out += "profiles $profiles"
		out += "profiles json ${Apex.json.encodeToString(profiles)}"
		for (p in profiles.profiles.orEmpty()) for ((id, m) in p.players) {
			out += "member $id pets=${m.pets.map { it.itemId }}"
			out += "member collections=${m.collection.keys.map { it.catalogItemId }}"
		}
		out += "empty member ${Member()} ${Apex.json.encodeToString(Member())}"
		for (j in playersJson) {
			val r = attempt { Apex.json.decodeFromString<PlayerResponse>(j) }
			out += "player $r"
			val p = runCatching { Apex.json.decodeFromString<PlayerResponse>(j).player }.getOrNull() ?: continue
			out += "  plus=${p.rankPlusDyeColor} rank=${attempt { p.rankData }} display=${attempt { p.getDisplayName() }} other=${attempt { p.getDisplayName("X") }}"
			out += "  json ${Apex.json.encodeToString(p)}"
		}
		val collections = Apex.json.decodeFromString<CollectionResponse>(collectionsJson)
		out += "collections $collections"
		out += "collections json ${Apex.json.encodeToString(collections)}"
		out += "mowojang ${Apex.json.decodeFromString<MowojangNameLookup>("""{"name":"N","id":"0123456789abcdef0123456789abcdef"}""")}"
		out += "bad ${attempt { Apex.json.decodeFromString<Profiles>("""{"success":true,"profiles":[{"profile_id":"zz","cute_name":"x","members":{}}]}""") }}"
		out += "missing ${attempt { Apex.json.decodeFromString<Profiles>("""{"success":true}""") }}"
		for (raw in listOf("WHEAT", "INK_SACK:3", "MUSHROOM_COLLECTION", "A:B:C", "")) out += "type '$raw' -> ${CollectionType(raw).catalogItemId}"
		OursGolden.check("apis-models", out)
	}

	@Test
	fun skillTablesAreTheSame() {
		val out = mutableListOf<String>()
		val root = Files.createTempDirectory("apex-ours-apis")
		try {
			val zst = root.resolve("repo.json.zst")
			zst.writeBytes(javaClass.getResourceAsStream("/apex-bundle/live/repo.json.zst")!!.readAllBytes())
			val dir = root.resolve("repo")
			RepoBundle.unpackZst(zst, dir)
			val leveling = NEURepository.of(dir).also { it.reload() }.constants.leveling
			val member = Apex.json.decodeFromString<Profiles>(profilesJson).profiles!!.first().players.values.first()
			for (s in Skill.entries) {
				val ladder = s.xpLadder(leveling)
				out += "${s.ordinal} ${s.name} ${s.xpOf.name} ${s.color} ${s.icon} xp=${s.xpOf.get(member)} max=${attempt { s.levelCap(leveling) }} ladder=${ladder.size}:${ladder.take(5)}:${ladder.sum()}"
			}
			for (c in CollectionCategory.entries) out += "${c.ordinal} ${c.name} ${c.skill} ${c.color} ${c.icon}"
		} finally {
			root.toFile().deleteRecursively()
		}
		OursGolden.check("apis-skills", out)
	}
}
