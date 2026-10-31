package dev.ashtonite365.apex.repo.mobs

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.moulberry.repo.IReloadable
import io.github.moulberry.repo.NEURepository
import io.github.moulberry.repo.data.NEUMobDropRecipe
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import dev.ashtonite365.apex.repo.itemsSafe

data class MobDrop(val itemId: String, val count: Int = 1, val chance: String? = null, val extra: List<String> = listOf())

data class MobEntry(
	val id: String,
	val name: String,
	val level: Int = 0,
	val coins: Int = 0,
	val xp: Map<String, Int> = mapOf(),
	val panorama: String? = null,
	val extra: List<String> = listOf(),
	val drops: List<MobDrop> = listOf(),

	val render: String? = null,

	val renderSpec: JsonObject? = null,
	val fallbackEntity: String? = null,

	val hostItem: String? = null,

	val hostItems: List<String> = listOfNotNull(hostItem),

	val type: String? = null,

	val location: String? = null,

	val stats: List<Pair<String, String>> = listOf(),
) {
	val plainName: String get() = name.replace(dev.ashtonite365.apex.util.SharedRegex.SECTION, "")
}

data class MobRenderPlan(val entity: String, val modifiers: List<JsonObject>, val fromHint: Boolean, val unknown: Boolean = false)

object MobRenderPlanner {

	val NAME_HINTS: List<Pair<String, String>> = listOf(
		"scatha" to "Silverfish", "worm" to "Silverfish", "silverfish" to "Silverfish", "endermite" to "Endermite",
		"spider" to "Spider", "tarantula" to "Spider", "broodmother" to "Spider", "zombie" to "Zombie",
		"ghoul" to "Zombie", "skeleton" to "Skeleton", "wolf" to "Wolf", "hound" to "Wolf", "pack" to "Wolf",
		"enderman" to "Enderman", "zealot" to "Enderman", "voidgloom" to "Enderman", "blaze" to "Blaze",
		"golem" to "Eisengolem", "slime" to "Slime", "magma" to "MagmaCube", "ghast" to "Ghast", "witch" to "Witch",
		"creeper" to "Creeper", "pig" to "Pig", "cow" to "Cow", "sheep" to "Sheep", "chicken" to "Chicken",
		"rabbit" to "Rabbit", "bat" to "Bat", "squid" to "Squid", "guardian" to "Guardian", "dragon" to "Dragon",
		"wither" to "Wither", "frog" to "Frog", "goblin" to "Zombie",
	)

	fun hintFor(name: String): String? {
		val n = name.replace(dev.ashtonite365.apex.util.SharedRegex.SECTION, "").lowercase()
		return NAME_HINTS.firstOrNull { n.contains(it.first) }?.second
	}

	fun isHeadOnly(spec: JsonObject): Boolean {
		val entity = spec.get("entity")?.takeIf { it.isJsonPrimitive }?.asString ?: return false
		if (entity != "ArmorStand") return false
		val mods = (spec.get("modifiers") as? JsonArray)?.mapNotNull { it as? JsonObject } ?: return false
		return mods.any { it.get("type")?.asString == "invisible" }
	}

	const val UNKNOWN_SKIN = "apex:textures/entity/unknown_mob.png"

	fun unknown(mob: MobEntry): MobRenderPlan = MobRenderPlan(
		"Player",
		listOf(
			JsonObject().apply { addProperty("type", "playerdata"); addProperty("skin", UNKNOWN_SKIN); addProperty("parts", false) },
			JsonObject().apply { addProperty("type", "name"); addProperty("name", mob.name) },
		),
		fromHint = false, unknown = true,
	)

	fun plan(mob: MobEntry, isKnownEntity: (String) -> Boolean = { true }, resolveRef: (String) -> JsonObject?): MobRenderPlan {
		val spec: JsonObject? = mob.renderSpec ?: mob.render?.trim()?.takeIf { it.isNotEmpty() }?.let { r ->
			if (r.startsWith("@")) resolveRef(r.substring(1))
			else JsonObject().apply { addProperty("entity", r) }
		}
		val entity = spec?.get("entity")?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotEmpty() }
		val hint = (mob.fallbackEntity ?: hintFor(mob.name) ?: hintFor(mob.id))?.takeIf(isKnownEntity)
		if (spec != null && entity != null && isHeadOnly(spec) && hint != null) return MobRenderPlan(hint, listOf(), true)
		if (spec != null && entity != null && isKnownEntity(entity)) {
			val mods = (spec.get("modifiers") as? JsonArray)?.mapNotNull { it as? JsonObject } ?: listOf()
			return MobRenderPlan(entity, mods, false)
		}
		return unknown(mob)
	}
}

object MobIndex : IReloadable {
	@Volatile
	var mobs: Map<String, MobEntry> = mapOf()
		private set
	@Volatile
	var droppedBy: Map<String, List<String>> = mapOf()
		private set

	fun get(id: String): MobEntry? = lookup(id, mobs)

	val ID_SUFFIXES = listOf("_monster", "_miniboss", "_boss", "_sc", "_npc")

	fun lookup(key: String, mobs: Map<String, MobEntry>): MobEntry? {
		val k = key.trim().lowercase()
		mobs[k]?.let { return it }
		val slug = k.replace(Regex("[^a-z0-9]+"), "_").trim('_')
		mobs[slug]?.let { return it }
		ID_SUFFIXES.firstNotNullOfOrNull { mobs[slug + it] }?.let { return it }
		ID_SUFFIXES.firstOrNull { slug.endsWith(it) }?.let { s -> mobs[slug.removeSuffix(s)]?.let { return it } }
		mobs.values.firstOrNull { m -> m.hostItems.any { it.equals(key.trim(), true) } }?.let { return it }
		val plain = key.replace(dev.ashtonite365.apex.util.SharedRegex.SECTION, "").trim().lowercase()

		return mobs.values.filter { it.plainName.trim().lowercase() == plain }
			.maxByOrNull { if (it.renderSpec != null || !it.render.isNullOrBlank()) 1 else 0 }
	}
	fun all(): List<MobEntry> = mobs.values.sortedBy { it.plainName.lowercase() }
	fun mobsDropping(itemId: String): List<MobEntry> = droppedBy[itemId].orEmpty().mapNotNull { mobs[it] }

	data class DropRecipeInput(val hostItem: String, val name: String?, val level: Int, val coins: Int,
	                           val xp: Map<String, Int>, val panorama: String?, val extra: List<String>,
	                           val render: String?, val drops: List<MobDrop>, val hostLore: List<String> = listOf())

	private val TYPE_SUFFIXES = listOf("_SC" to "Sea Creature", "_MINIBOSS" to "Miniboss", "_BOSS" to "Boss",
	                                   "_MONSTER" to "Monster", "_NPC" to "NPC")

	fun typeFor(hostItem: String?): String? =
		hostItem?.uppercase()?.let { h -> TYPE_SUFFIXES.firstOrNull { h.endsWith(it.first) }?.second }

	fun loreFacts(lore: List<String>): List<Pair<String, String>> = lore.mapNotNull { raw ->
		val l = raw.replace(dev.ashtonite365.apex.util.SharedRegex.SECTION, "").trim()
		val i = l.indexOf(':').takeIf { it > 0 } ?: return@mapNotNull null
		val k = l.substring(0, i).trim(); val v = l.substring(i + 1).trim()
		if (k.isEmpty() || v.isEmpty() || v.equals("unknown", true) || k.equals("Level", true)) null else k to v
	}

	fun nameKey(name: String?): String = name.orEmpty().replace(dev.ashtonite365.apex.util.SharedRegex.SECTION, "").trim().lowercase()

	fun idFor(render: String?, hostItem: String): String {
		val ref = render?.takeIf { it.startsWith("@") }?.substringAfterLast('/')?.removeSuffix(".json")
		return (ref ?: hostItem).lowercase()
	}

	fun prettify(id: String) = id.split('_', ' ').filter { it.isNotEmpty() }
		.joinToString(" ") { it.lowercase().replaceFirstChar(Char::uppercase) }

	fun parseDrop(o: JsonObject): MobDrop? {
		val raw = o.get("id")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
		if (raw.isEmpty()) return null
		val id = raw.substringBefore(':')
		val count = raw.substringAfter(':', "1").toDoubleOrNull()?.toInt() ?: 1
		val chance = o.get("chance")?.takeIf { it.isJsonPrimitive }?.asString
		val extra = (o.get("extra") as? JsonArray)?.mapNotNull { it.takeIf { it.isJsonPrimitive }?.asString } ?: listOf()
		return MobDrop(id, count, chance, extra)
	}

	private fun JsonObject.str(k: String) = get(k)?.takeIf { it.isJsonPrimitive }?.asString
	private fun JsonObject.int(k: String) = get(k)?.takeIf { it.isJsonPrimitive }?.asString?.toDoubleOrNull()?.toInt()

	fun build(recipes: List<DropRecipeInput>, mobFiles: Map<String, JsonObject>): Map<String, MobEntry> {
		val out = LinkedHashMap<String, MobEntry>()
		val byName = HashMap<String, String>()
		fun entryFor(r: DropRecipeInput, id: String): MobEntry {
			val facts = loreFacts(r.hostLore)
			return MobEntry(id, r.name?.takeIf { it.isNotBlank() } ?: prettify(id), r.level, r.coins, r.xp,
			                r.panorama, r.extra, r.drops, r.render, null, null, r.hostItem,
			                type = typeFor(r.hostItem),
			                location = facts.firstOrNull { it.first.equals("Location", true) }?.second,
			                stats = facts.filterNot { it.first.equals("Location", true) })
		}
		fun mergeInto(prev: MobEntry, add: MobEntry) = prev.copy(
			drops = (prev.drops + add.drops).distinct().distinctBy { Triple(it.itemId, it.count, it.chance) },
			hostItems = (prev.hostItems + add.hostItems).distinct(),
			level = prev.level.takeIf { it > 0 } ?: add.level,
			coins = prev.coins.takeIf { it > 0 } ?: add.coins,
			xp = add.xp + prev.xp,
			type = prev.type ?: add.type,
			location = prev.location ?: add.location,
			stats = (prev.stats + add.stats).distinctBy { it.first.lowercase() },
		)

		val (rendered, renderless) = recipes.partition { !it.render.isNullOrBlank() }
		for (r in rendered) {
			val id = idFor(r.render, r.hostItem)
			val entry = entryFor(r, id)

			out[id] = out[id]?.let { mergeInto(it, entry) } ?: entry
			byName.putIfAbsent(nameKey(out[id]!!.name), id)
		}
		for (r in renderless) {
			val key = nameKey(r.name)
			val target = byName[key]?.takeIf { key.isNotEmpty() } ?: idFor(r.render, r.hostItem)
			val entry = entryFor(r, target)
			out[target] = out[target]?.let { mergeInto(it, entry) } ?: entry
			if (key.isNotEmpty()) byName.putIfAbsent(key, target)
		}
		for ((rawId, json) in mobFiles) {
			val id = rawId.lowercase()
			val prev = out[id] ?: MobEntry(id, prettify(id))
			val xp = prev.xp.toMutableMap()
			json.entrySet().filter { it.key.endsWith("_xp") || it.key == "xp" }.forEach { (k, v) ->
				v.takeIf { it.isJsonPrimitive }?.asString?.toDoubleOrNull()?.let { xp[k.removeSuffix("_xp")] = it.toInt() }
			}
			val drops = (json.get("drops") as? JsonArray)?.mapNotNull { (it as? JsonObject)?.let(::parseDrop) }.orEmpty()
			out[id] = prev.copy(
				name = json.str("name") ?: prev.name,
				level = json.int("level") ?: prev.level,
				coins = json.int("coins") ?: prev.coins,
				xp = xp,
				panorama = json.str("panorama") ?: prev.panorama,
				extra = (json.get("extra") as? JsonArray)?.mapNotNull { it.takeIf { it.isJsonPrimitive }?.asString } ?: prev.extra,
				drops = drops.ifEmpty { prev.drops },
				render = json.str("render") ?: prev.render,
				renderSpec = if (json.get("entity")?.isJsonPrimitive == true) json else prev.renderSpec,
				fallbackEntity = json.str("fallback_entity") ?: prev.fallbackEntity,
				type = json.str("type") ?: prev.type,
				location = (json.str("location") ?: json.str("island"))?.takeUnless { it.equals("unknown", true) } ?: prev.location,
				stats = (listOf("health", "damage", "defense").mapNotNull { k ->
					json.str(k)?.let { k.replaceFirstChar(Char::uppercase) to it }
				} + prev.stats).distinctBy { it.first.lowercase() },
			)
		}
		return out
	}

	fun indexDrops(mobs: Map<String, MobEntry>): Map<String, List<String>> {
		val m = LinkedHashMap<String, MutableList<String>>()
		mobs.values.forEach { mob -> mob.drops.forEach { d -> m.getOrPut(d.itemId) { mutableListOf() }.let { if (mob.id !in it) it.add(mob.id) } } }
		return m
	}

	fun install(built: Map<String, MobEntry>) {
		mobs = built
		droppedBy = indexDrops(built)
	}

	fun readMobFiles(base: Path?): Map<String, JsonObject> {
		val dir = base?.resolve("mobs") ?: return mapOf()
		if (!dir.isDirectory()) return mapOf()
		return Files.list(dir).use { s ->
			s.filter { it.extension == "json" }.toList().mapNotNull { p ->
				runCatching { p.nameWithoutExtension to JsonParser.parseString(p.readText()).asJsonObject }.getOrNull()
			}.toMap()
		}
	}

	fun fromRecipe(host: String, r: NEUMobDropRecipe, hostLore: List<String> = listOf()): DropRecipeInput {
		val xp = mapOf(
			"combat" to r.combatExperience, "taming" to r.tamingExperience, "mining" to r.miningExperience,
			"foraging" to r.foragingExperience, "enchanting" to r.enchantingExperience, "carpentry" to r.carpentryExperience,
			"farming" to r.farmingExperience, "fishing" to r.fishingExperience, "alchemy" to r.alchemyExperience,
			"runecrafting" to r.runecraftingExperience, "exp" to r.experienceOrbs,
		).filterValues { it > 0 }
		val drops = r.drops.orEmpty().mapNotNull { d ->
			val ing = d.dropItem ?: return@mapNotNull null
			MobDrop(ing.itemId, ing.amount.toInt().coerceAtLeast(1), d.chance, d.extra.orEmpty())
		}
		return DropRecipeInput(host, r.name, r.level, r.coins, xp, r.panorama, r.extra.orEmpty(), r.render, drops, hostLore)
	}

	fun forHostItem(itemId: String, mobs: Map<String, MobEntry> = this.mobs): MobEntry? =
		mobs.values.firstOrNull { m -> m.hostItems.any { it.equals(itemId, true) } }

	override fun reload(repository: NEURepository) {
		val recipes = repository.items.itemsSafe.values.flatMap { item ->
			item.recipes.filterIsInstance<NEUMobDropRecipe>().map { fromRecipe(item.skyblockItemId, it, item.lore.orEmpty()) }
		}
		install(build(recipes, readMobFiles(runCatching { repository.baseFolder }.getOrNull())))
	}

	private val gson = Gson()
}

object KnownEntityNames {
	val names: Set<String> = setOf("Allay","Camel","Cat","Hoglin","Nautilus","Parched","Parrot","SkeletonHorse","ZombieHorse","ZombieNautilus","Armadillo","ArmorStand","Axolotl","Bat","Bee","Blaze","Bogged","Breeze","CaveSpider","Chicken","Cod","Cow","Creaking","Creeper","Dolphin","Donkey","Dragon","Drowned","Eisengolem","Enderman","Endermite","Evoker","Fox","Frog","Ghast","Giant","GlowSquid","Goat","Guardian","Horse","Husk","Illusioner","LLama","MagmaCube","Mooshroom","Mule","Ocelot","Panda","Phantom","Pig","Piglin","PiglinBrute","Pigman","Pillager","Player","PolarBear","Pufferfish","Rabbit","Salmom","Salmon","Sheep","Shulker","Silverfish","SiNelverfish","Skeleton","Slime","Sniffer","Snowman","Spider","Squid","Stray","Strider","Tadpole","TropicalFish","Turtle","Vex","Villager","Vindicator","Warden","Witch","Wither","WitherSkeleton","Wolf","Zoglin","Zombie","ZombieVillager")
}

object MobText {
	fun header(mob: MobEntry): String = if (mob.level > 0) "[Lv${mob.level}] ${mob.name}" else mob.name

	fun hover(mob: MobEntry): List<String> = listOf(header(mob))

	fun cardLines(mob: MobEntry): List<String> = buildList {
		listOfNotNull(mob.type, mob.level.takeIf { it > 0 }?.let { "Lv$it" }).takeIf { it.isNotEmpty() }
			?.let { add("§7" + it.joinToString(" · ")) }
		mob.location?.let { add("§7Location: §a$it") }
		mob.stats.forEach { (k, v) -> add("§7$k: §f$v") }
		if (mob.coins > 0) add("§6${mob.coins} coins")
		mob.xp.forEach { (k, v) -> add("§3+$v ${k.replaceFirstChar(Char::uppercase)} XP") }
		addAll(mob.extra)
	}
}
