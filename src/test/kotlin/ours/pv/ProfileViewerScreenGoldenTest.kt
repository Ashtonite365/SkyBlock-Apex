// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.ours.pv

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.features.profileviewer.ProfileViewer
import dev.ashtonite365.apex.features.profileviewer.ProfileViewerScreen
import dev.ashtonite365.apex.test.golden.HeadlessGui
import dev.ashtonite365.apex.test.ours.OursGolden
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import java.util.UUID

@ExtendWith(AutoBootstrapExtension::class)
class ProfileViewerScreenGoldenTest {
	private val rich = """
	{
	 "player_data": {"experience": {"SKILL_FARMING": 55172425, "SKILL_MINING": 1250000, "SKILL_COMBAT": 15000000, "SKILL_FORAGING": 300,
	   "SKILL_FISHING": 0, "SKILL_ENCHANTING": 111672425, "SKILL_ALCHEMY": 50000, "SKILL_TAMING": 2000000, "SKILL_CARPENTRY": 7000,
	   "SKILL_RUNECRAFTING": 94450, "SKILL_SOCIAL": 1000, "SKILL_HUNTING": 12}},
	 "leveling": {"experience": 23456},
	 "currencies": {"coin_purse": 1234567.8, "motes_purse": 4321, "essence": {"WITHER": {"current": 100}, "DRAGON": {"current": 5}}},
	 "fairy_soul": {"total_collected": 201},
	 "profile": {"bank_account": 5000000},
	 "slayer": {"slayer_bosses": {"zombie": {"xp": 1500000}, "spider": {"xp": 400}, "wolf": {"xp": 250000}, "enderman": {"xp": 0},
	   "blaze": {"xp": 30000}, "vampire": {"xp": 2400}}},
	 "dungeons": {"selected_dungeon_class": "mage", "secrets": 4321,
	   "dungeon_types": {"catacombs": {"experience": 3500000, "tier_completions": {"0": 3, "1": 10, "7": 55},
	       "fastest_time_s": {"7": 245000}, "fastest_time_s_plus": {"7": 299500}},
	     "master_catacombs": {"tier_completions": {"5": 12}, "fastest_time_s_plus": {"5": 190000}}},
	   "player_classes": {"healer": {"experience": 100}, "mage": {"experience": 2000000}, "berserk": {"experience": 50000},
	     "archer": {"experience": 7}, "tank": {"experience": 0}}},
	 "mining_core": {"experience": 3000000, "powder_mithril": 12000, "powder_spent_mithril": 500000, "powder_gemstone": 800,
	   "powder_spent_gemstone": 1200000, "powder_glacite": 33, "powder_spent_glacite": 4000, "tokens": 3, "tokens_spent": 40,
	   "selected_pickaxe_ability": "mining_speed_boost", "nodes": {"mining_speed": 50, "mining_fortune": 10, "toggle_mining_fortune": false},
	   "crystals": {"jade_crystal": {"total_found": 2}, "amber_crystal": {"total_found": 0}, "topaz_crystal": {"total_found": 1}}},
	 "rift": {"enigma": {"found_souls": ["a", "b", "c"]}, "gallery": {"secured_trophies": [{"type": "x"}, {"type": "y"}]},
	   "dead_cats": {"found_cats": ["c1", "c2"]}, "castle": {"grubber_stacks": 4}, "lifetime_motes_earned": 99999},
	 "nether_island_crimson_isle": {"selected_faction": "mages", "mages_reputation": 4500, "barbarians_reputation": -200,
	   "kuudra_completed_tiers": {"none": 20, "hot": 5, "burning": 1, "fiery": 0, "infernal": 0},
	   "dojo": {"dojo_points_mob_kb": 900, "dojo_points_wall_jump": 300}},
	 "collection": {"WHEAT": 120000, "COBBLESTONE": 50, "ENDER_PEARL": 3000},
	 "pets_data": {"pets": [{"type": "ENDER_DRAGON", "tier": "LEGENDARY", "exp": 25353230, "active": true, "heldItem": "PET_ITEM_TIER_BOOST", "skin": null, "candyUsed": 0},
	   {"type": "BEE", "tier": "RARE", "exp": 10000, "active": false, "heldItem": null, "candyUsed": 3},
	   {"type": "BABY_YETI", "tier": "EPIC", "exp": 4000000, "active": false, "skin": "YETI_GROWN_UP", "candyUsed": 10}]},
	 "accessory_bag_storage": {"highest_magical_power": 1234},
	 "player_stats": {"candy_collected": {"total": 5}},
	 "garden_player_data": {"copper": 10},
	 "inventory": {}
	}""".trimIndent()

	private fun json(s: String) = Json.parseToJsonElement(s) as JsonObject

	private fun profile(id: String, cute: String, member: JsonObject, sel: Boolean, mode: String?, bank: Double?, members: Int) =
		ProfileViewer.SkyblockProfile(id, cute, mode, sel, bank, member, members)

	private fun screen(result: ProfileViewer.LoadResult?, garden: Map<String, JsonObject>, museum: Map<String, JsonObject>, ids: List<String>): ProfileViewerScreen {
		val s = ProfileViewerScreen("Tester")
		fun set(c: Class<*>, n: String, v: Any?) = c.getDeclaredField(n).also { it.isAccessible = true }.set(s, v)
		set(Screen::class.java, "width", 480); set(Screen::class.java, "height", 270)
		set(Screen::class.java, "minecraft", HeadlessGui.fakeGame); set(Screen::class.java, "font", HeadlessGui.font)
		@Suppress("UNCHECKED_CAST")
		fun <T> get(n: String) = ProfileViewerScreen::class.java.getDeclaredField(n).also { it.isAccessible = true }.get(s) as T
		get<MutableMap<String, JsonObject>>("gardenData").putAll(garden)
		get<MutableMap<String, JsonObject>>("museumData").putAll(museum)

		for (id in ids) { if (id !in garden) get<MutableSet<String>>("gardenLoading") += id; if (id !in museum) get<MutableSet<String>>("museumLoading") += id }
		if (result != null) s.setResult(result)
		return s
	}

	private fun setField(s: ProfileViewerScreen, n: String, v: Any?) = ProfileViewerScreen::class.java.getDeclaredField(n).also { it.isAccessible = true }.set(s, v)

	private fun pinStatics() {
		val h = dev.ashtonite365.apex.repo.HypixelStaticData
		h.lowestBin = emptyMap()
		fun set(n: String, v: Any?) = h.javaClass.getDeclaredField(n).also { it.isAccessible = true }.set(h, v)
		set("bazaarData", emptyMap<Any, Any>())
		set("collectionDataState", dev.ashtonite365.apex.repo.HypixelStaticData.CollectionDataState.LOADING)
	}
	private fun frame(s: ProfileViewerScreen, mx: Int = 0, my: Int = 0) = HeadlessGui.record { s.extractRenderState(it, mx, my, 0f) }.joinToString(" | ")

	@Test
	fun pages() = HeadlessGui.withFakeGame {
		dev.ashtonite365.apex.test.gui.config.storage.MainConfigRegistry.registered.filter {
			!it.isLoaded || (it is dev.ashtonite365.apex.util.data.ManagedConfig && it.sortedOptions.any { o -> o._actualValue == null })
		}.forEach { it.explicitDefaultLoad() }
		pinStatics()
		val out = ArrayList<String>()
		val garden = json("""{"garden": {"garden_experience": 50000, "unlocked_plots_ids": ["a", "b"], "commission_data": {"visits": {"JACOB": 3}, "completed": {"JACOB": 2}}, "composter_data": {}}}""")
		val museum = json("""{"members": {"00000000000000030000000000000004": {"value": 12345678, "appraisal": true, "items": {"BONE_BOOMERANG": {"donated_time": 1, "borrowing": false, "items": {"type": 0, "data": ""}}}, "special": [{"items": {"type": 0, "data": ""}}]}}}""")
		val p1 = profile("p1", "Apple", json(rich), true, null, 5_000_000.0, 3)
		val p2 = profile("p2", "Banana", json("{}"), false, "ironman", null, 1)
		val players = ProfileViewer.PlayerProfiles(UUID(1, 2), "Tester", "§b[MVP§c+§b] Tester", listOf(p1, p2), null)
		out += "null result: " + frame(screen(null, mapOf(), mapOf(), listOf()))
		out += "error: " + frame(screen(ProfileViewer.LoadResult.Error(Component.literal("Player not found")), mapOf(), mapOf(), listOf()))
		out += "no profiles: " + frame(screen(ProfileViewer.LoadResult.Success(players.copy(profiles = emptyList())), mapOf(), mapOf(), listOf()))
		for ((gi, gm) in listOf(mapOf("p1" to garden, "p2" to json("{}")), mapOf<String, JsonObject>()).withIndex()) for (idx in 0..1) for (page in ProfileViewerScreen.Page.entries) {
			val s = screen(ProfileViewer.LoadResult.Success(players), gm, if (gi == 0) mapOf("p1" to museum, "p2" to json("{}")) else mapOf(), listOf("p1", "p2"))
			setField(s, "profileIndex", idx); setField(s, "page", page)
			val kinds = if (page == ProfileViewerScreen.Page.INVENTORIES) ProfileViewerScreen.InventoryKind.entries else listOf(null)
			for (k in kinds) {
				if (k != null) { setField(s, "inventoryKind", k); setField(s, "cachedItems", null) }
				Screen::class.java.getDeclaredMethod("rebuildWidgets").also { it.isAccessible = true }.invoke(s)
				out += "g$gi profile $idx $page ${k ?: ""}: " + frame(s)
				out += "  hover: " + frame(s, 120, 90).hashCode() + " / " + frame(s, 300, 60).hashCode()
				out += "  scroll: " + runCatching { s.mouseScrolled(200.0, 120.0, 0.0, -3.0) }.getOrElse { "!" + it.javaClass.simpleName } + " " + frame(s).hashCode()
			}
		}
		for (kind in ProfileViewerScreen.InventoryKind.entries) {
			val rows = ProfileViewerScreen.twoColumnBlockRows(kind)
			out += "kind $kind label=${kind.label} rows=$rows cells=" + listOf(0, 8, 9, 44, 45, 90, 200).map { ProfileViewerScreen.gridCell(it, rows) } +
				" gridRows=" + listOf(0, 1, 9, 27, 45, 100, 405).map { ProfileViewerScreen.gridRows(it, rows) }
		}
		for (p in ProfileViewerScreen.Page.entries) out += "page $p ${p.label.string}"
		OursGolden.check("profileviewer-pages", out)
	}

	private fun blob(items: List<Triple<String, Int, String>?>): String {
		val list = net.minecraft.nbt.ListTag()
		for (it in items) list.add(net.minecraft.nbt.CompoundTag().apply {
			if (it == null) return@apply
			putShort("id", 1); putByte("Count", it.second.toByte())
			put("tag", net.minecraft.nbt.CompoundTag().apply {
				put("ExtraAttributes", net.minecraft.nbt.CompoundTag().apply { putString("id", it.first) })
				put("display", net.minecraft.nbt.CompoundTag().apply {
					putString("Name", it.third)
					put("Lore", net.minecraft.nbt.ListTag().apply { add(net.minecraft.nbt.StringTag.valueOf("§7Line one")); add(net.minecraft.nbt.StringTag.valueOf("§6§lLEGENDARY")) })
				})
			})
		})
		val root = net.minecraft.nbt.CompoundTag().apply { put("i", list) }
		val bytes = java.io.ByteArrayOutputStream().also { net.minecraft.nbt.NbtIo.writeCompressed(root, it) }.toByteArray()
		return "{\"type\": 0, \"data\": \"" + java.util.Base64.getEncoder().encodeToString(bytes) + "\"}"
	}

	@Test
	fun inventories() = HeadlessGui.withFakeGame {
		dev.ashtonite365.apex.test.gui.config.storage.MainConfigRegistry.registered.filter {
			!it.isLoaded || (it is dev.ashtonite365.apex.util.data.ManagedConfig && it.sortedOptions.any { o -> o._actualValue == null })
		}.forEach { it.explicitDefaultLoad() }
		pinStatics()
		val out = ArrayList<String>()
		fun items(n: Int, seed: Int) = (0 until n).map { i -> if ((i + seed) % 5 == 3) null else Triple(listOf("ASPECT_OF_THE_END", "WHEAT", "NOPE_ITEM", "BOOSTER_COOKIE")[(i + seed) % 4], 1 + (i * 7 + seed) % 64, "§6Item $i") }
		val inv = """{
		 "inv_contents": ${blob(items(36, 0))}, "inv_armor": ${blob(items(4, 1))}, "equipment_contents": ${blob(items(4, 2))},
		 "ender_chest_contents": ${blob(items(90, 3))}, "wardrobe_contents": ${blob(items(72, 4))}, "personal_vault_contents": ${blob(items(27, 5))},
		 "backpack_contents": {"0": ${blob(items(27, 6))}, "3": ${blob(items(9, 7))}},
		 "bag_contents": {"talisman_bag": ${blob(items(45, 8))}, "potion_bag": ${blob(items(18, 9))}, "fishing_bag": ${blob(items(18, 10))},
		   "quiver": ${blob(items(27, 11))}, "sacks_bag": ${blob(items(5, 12))}},
		 "candy_inventory_contents": ${blob(items(3, 13))}
		}"""
		val member = json("""{"inventory": $inv, "pets_data": {"pets": [{"type": "WOLF", "tier": "EPIC", "exp": 100000, "active": true, "heldItem": "DWARF_TURTLE_SHELMET", "candyUsed": 2}]}}""")
		val museum = json("""{"members": {"00000000000000030000000000000004": {"value": 12345678, "appraisal": false, "items": {"ASPECT_OF_THE_END": {"donated_time": 1, "borrowing": true, "items": ${blob(items(1, 0))}}, "WHEAT": {"items": ${blob(items(2, 1))}}}, "special": [{"items": ${blob(items(1, 2))}}]}}}""")
		val p = profile("q1", "Cherry", member, true, "island", 7.0, 2)
		val players = ProfileViewer.PlayerProfiles(UUID(3, 4), "Inv", "§7Inv", listOf(p), null)
		for (page in listOf(ProfileViewerScreen.Page.INVENTORIES, ProfileViewerScreen.Page.PETS, ProfileViewerScreen.Page.MUSEUM, ProfileViewerScreen.Page.OVERVIEW)) {
			val s = screen(ProfileViewer.LoadResult.Success(players), mapOf("q1" to json("{}")), mapOf("q1" to museum), listOf("q1"))
			setField(s, "page", page)
			val kinds = if (page == ProfileViewerScreen.Page.INVENTORIES) ProfileViewerScreen.InventoryKind.entries else listOf(null)
			for (k in kinds) {
				if (k != null) { setField(s, "inventoryKind", k); setField(s, "cachedItems", null) }
				Screen::class.java.getDeclaredMethod("rebuildWidgets").also { it.isAccessible = true }.invoke(s)
				out += "$page ${k ?: ""}: " + frame(s)
				for ((mx, my) in listOf(165 to 70, 185 to 70, 165 to 95, 240 to 120, 300 to 200, 100 to 120)) out += "  hover $mx,$my: " + frame(s, mx, my)
				for (d in listOf(-1.0, -5.0, 2.0)) out += "  scroll $d: " + runCatching { s.mouseScrolled(200.0, 120.0, 0.0, d) }.getOrElse { "!" + it.javaClass.simpleName } + " " + frame(s)
			}
		}
		OursGolden.check("profileviewer-inventories", out)
	}
}
