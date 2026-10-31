// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.items.wiki

data class ItemRef(

	val id: String?,

	val displayName: String? = null,

	val bookEnchants: List<String> = emptyList(),

	val isPet: Boolean = false,

	val isBook: Boolean = false,
)

interface WikiRepoView {

	fun ids(): Collection<String>

	fun wikiUrl(id: String): String?
	fun displayName(id: String): String?

	object Empty : WikiRepoView {
		override fun ids(): Collection<String> = emptyList()
		override fun wikiUrl(id: String): String? = null
		override fun displayName(id: String): String? = null
	}
}

data class WikiTarget(
	val title: String,
	val anchor: String? = null,
	val alternates: List<String> = emptyList(),
	val search: String? = null,
	val rule: String,
) {

	val cacheKey get() = WikiTitles.normalise(title)
}

object WikiTitles {

	fun normalise(title: String): String {
		val t = title.replace('_', ' ').replace(dev.ashtonite365.apex.util.SharedRegex.WHITESPACE, " ").trim()
		return if (t.isEmpty()) t else t.substring(0, 1).uppercase() + t.substring(1)
	}

	fun percentDecode(s: String): String {
		if ('%' !in s) return s
		val out = java.io.ByteArrayOutputStream()
		var i = 0
		while (i < s.length) {
			val c = s[i]
			if (c == '%' && i + 2 < s.length && s.substring(i + 1, i + 3).toIntOrNull(16) != null) {
				out.write(s.substring(i + 1, i + 3).toInt(16)); i += 3
			} else {
				out.write(c.toString().toByteArray(Charsets.UTF_8)); i++
			}
		}
		return out.toString(Charsets.UTF_8)
	}

	fun ofUrl(url: String): Pair<String, String?>? {
		val u = url.trim()
		val m = Regex("^https?://([^/]+)/w/([^?#]+)(?:\\?[^#]*)?(?:#(.*))?$").matchEntire(u) ?: return null
		if (m.groupValues[1].lowercase() !in WikiHosts.HOSTS) return null
		val title = normalise(percentDecode(m.groupValues[2]))
		if (title.isEmpty()) return null
		val anchor = m.groupValues[3].takeIf { it.isNotEmpty() }?.let { percentDecode(it).replace('_', ' ') }
		return title to anchor
	}

	private val roman = Regex("^[IVXLCDM]+$", RegexOption.IGNORE_CASE)
	fun isRoman(s: String) = s.isNotEmpty() && roman.matches(s)

	fun titleCase(id: String): String = id.lowercase().split('_').filter { it.isNotEmpty() }.mapIndexed { i, w ->
		if (i > 0 && w in setOf("of", "the", "and")) w else w.replaceFirstChar { it.uppercase() }
	}.joinToString(" ")
}

object ItemWikiEnchants {

	val aliases = mapOf(
		"AIMING" to "Dragon Tracer", "ARCANE" to "Woodsplitter", "DRAGON_HUNTER" to "Gravity",
		"FEROCIOUS_MANA" to "Vivacious Vitality", "HARDENED_MANA" to "Hardened Vitality", "MAGMARIZER" to "Pyroclasm",
		"MANA_VAMPIRE" to "Vampiric Vitality", "PRISTINE" to "Prismatic", "STRONG_MANA" to "Strong Vitality",
		"SYPHON" to "Drain", "TRIPLE_STRIKE" to "Triple-Strike",
		"TURBO_CACTUS" to "Turbo-Cacti", "TURBO_CANE" to "Turbo-Cane", "TURBO_CARROT" to "Turbo-Carrot",
		"TURBO_COCO" to "Turbo-Cocoa", "TURBO_MELON" to "Turbo-Melon", "TURBO_MOONFLOWER" to "Turbo-Moonflower",
		"TURBO_MUSHROOMS" to "Turbo-Mushrooms", "TURBO_POTATO" to "Turbo-Potato", "TURBO_PUMPKIN" to "Turbo-Pumpkin",
		"TURBO_ROSE" to "Turbo-Rose", "TURBO_SUNFLOWER" to "Turbo-Sunflower", "TURBO_WARTS" to "Turbo-Warts",
		"TURBO_WHEAT" to "Turbo-Wheat", "ULTIMATE_REITERATE" to "Duplex", "ULTIMATE_WISDOM" to "Wisdom (Enchantment)",
	)

	private val keepUltimate = setOf("ULTIMATE_WISE", "ULTIMATE_JERRY")

	fun pageOf(key: String): String {
		val k = key.trim().uppercase().replace(' ', '_').removePrefix("ENCHANTMENT_")
		aliases[k]?.let { return it }
		val base = if (k.startsWith("ULTIMATE_") && k !in keepUltimate) k.removePrefix("ULTIMATE_") else k
		return WikiTitles.titleCase(base)
	}

	private val repoBook = Regex("^([A-Z0-9_]+);(\\d+)$")
	private val prefixed = Regex("^ENCHANTMENT_([A-Z0-9_]+?)_(\\d+)$")

	fun keyOfId(id: String): String? =
		prefixed.matchEntire(id)?.groupValues?.get(1) ?: repoBook.matchEntire(id)?.groupValues?.get(1)
}

class ItemWikiResolver(private val repo: WikiRepoView = WikiRepoView.Empty, private val reforges: Collection<String> = emptyList()) {
	private val gems = setOf("RUBY", "AMBER", "SAPPHIRE", "JADE", "AMETHYST", "TOPAZ", "JASPER", "OPAL", "ONYX", "AQUAMARINE", "CITRINE", "PERIDOT")
	private val rarities = setOf("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL", "VERY_SPECIAL")
	private val pieces = setOf("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS")
	private val trophyFish = setOf(
		"BLOBFISH", "FLYFISH", "GOLDEN_FISH", "GUSHER", "KARATE_FISH", "LAVA_HORSE", "MANA_RAY", "MOLDFIN",
		"OBFUSCATED_FISH_1", "OBFUSCATED_FISH_2", "OBFUSCATED_FISH_3", "SKELETON_FISH", "SLUGFISH", "SOUL_FISH",
		"STEAMING_HOT_FLOUNDER", "SULPHUR_SKITTER", "VANILLE", "VOLCANIC_STONEFISH",
	)

	private val trophyNoAnchor = setOf("STEAMING_HOT_FLOUNDER")

	private val urlTitle: Map<String, Pair<String, String?>> by lazy {
		buildMap { for (id in repo.ids()) repo.wikiUrl(id)?.let(WikiTitles::ofUrl)?.let { put(id, it) } }
	}

	private val hubSize: Map<String, Int> by lazy { urlTitle.values.groupingBy { it.first }.eachCount() }

	private val petSpecies: Map<String, String> by lazy {
		buildMap {
			for (id in repo.ids()) {
				val m = petId.matchEntire(id) ?: continue
				val name = repo.displayName(id)?.let(::strip) ?: continue
				if (name.startsWith("[Lvl")) putIfAbsent(m.groupValues[1], name.replace(Regex("^\\[Lvl[^]]*]\\s*"), "").trim())
			}
		}
	}

	private val petId = Regex("^([A-Z0-9_]+);([0-5])$")
	private fun strip(s: String) = s.replace(dev.ashtonite365.apex.util.SharedRegex.SECTION, "").trim()

	fun resolve(ref: ItemRef): WikiTarget? {
		val id = ref.id?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
		val name = ref.displayName?.let(::strip)?.takeIf { it.isNotEmpty() }
		if (id != null) {
			ItemWikiOverrides.byId[id]?.let { return target(it, "override") }
			if (id in ItemWikiOverrides.furniture) return WikiTarget("Furniture", rule = "furniture")
			if (id.startsWith("ANNI_PODIUM_")) return WikiTarget("Furniture", "10th Anniversary Podium", rule = "furniture")
			val t = petOrBook(id, ref) ?: family(id, name) ?: repoLink(id, name)
			if (t != null) return withFallbacks(id, name, t)
		} else if (ref.isBook || ref.bookEnchants.isNotEmpty()) {
			book(ref)?.let { return it }
		}
		val base = name?.let { WikiResolveNames.baseName(it, reforges) }.orEmpty()
		if (base.isBlank()) return null
		val t = WikiTarget(base, search = base, rule = "name")
		return if (id != null) withFallbacks(id, name, t) else t
	}

	private val oldSuffix = Regex("\\s+\\((Old|Legacy|Admin|Item)\\)$")
	private val camelJoin = Regex("(?<=[a-z])(?=[A-Z])")

	private fun withFallbacks(id: String, name: String?, t: WikiTarget): WikiTarget {
		val extra = buildList {
			if (name != null) oldSuffix.find(name)?.let { add(name.removeRange(it.range).trim()) }
			if (id.startsWith("PET_SKIN_") && name != null) {
				val split = camelJoin.replace(name, " ").replace(dev.ashtonite365.apex.util.SharedRegex.WHITESPACE, " ").trim()
				add(if (split.endsWith(" Skin")) split else "$split Skin")
				add("Pet Skins")
			}
			if (id.endsWith("_MONSTER") && name != null) {
				val mob = name.substringBefore(" (").trim()
				if (mob.isNotEmpty()) add("$mob (Private Island)")
				add("Mob")
			}
			if (id in ItemWikiOverrides.adminOnly) add("Admin-only")
		}.filter { e -> e.isNotBlank() && WikiTitles.normalise(e) != t.cacheKey && e !in t.alternates }
		return if (extra.isEmpty()) t else t.copy(alternates = t.alternates + extra)
	}

	private fun target(spec: String, rule: String, alternates: List<String> = emptyList()): WikiTarget {
		val t = spec.substringBefore('#')
		val a = spec.substringAfter('#', "").takeIf { it.isNotEmpty() }
		return WikiTarget(WikiTitles.normalise(t), a, alternates, null, rule)
	}

	private fun book(ref: ItemRef): WikiTarget? {
		val key = ref.bookEnchants.firstOrNull() ?: ref.id?.let(ItemWikiEnchants::keyOfId) ?: return WikiTarget("Enchanted Book", rule = "book")
		return WikiTarget(ItemWikiEnchants.pageOf(key), rule = "enchant")
	}

	private fun petOrBook(id: String, ref: ItemRef): WikiTarget? {
		val pm = petId.matchEntire(id)
		val species = pm?.groupValues?.get(1)

		if (species != null && (ref.isPet || species in petSpecies)) {
			val repoPage = urlTitle[id]?.first?.takeIf { it.endsWith(" Pet") }
				?: urlTitle["$species;0"]?.first?.takeIf { it.endsWith(" Pet") }
			val petName = petSpecies[species] ?: ref.displayName?.let(::strip)?.replace(Regex("^\\[Lvl[^]]*]\\s*"), "")?.trim()?.takeIf { it.isNotEmpty() } ?: WikiTitles.titleCase(species)
			val page = repoPage ?: "$petName Pet"
			return WikiTarget(page, rule = "pet")
		}
		if (id.startsWith("ENCHANTMENT_") && ItemWikiEnchants.keyOfId(id) != null) return book(ref.copy(id = id))
		if (ref.bookEnchants.isNotEmpty()) return book(ref)
		if (id == "ENCHANTED_BOOK") return book(ref)

		if (ref.isBook && ItemWikiEnchants.keyOfId(id) != null) return book(ref)
		return null
	}

	private val starred = Regex("^STARRED_(.+)$")
	private val heated = Regex("^(HOT|BURNING|FIERY|INFERNAL)_(CRIMSON|AURORA|TERROR|FERVOR|HOLLOW)_(HELMET|CHESTPLATE|LEGGINGS|BOOTS)$")
	private val kuudraSet = Regex("^(CRIMSON|AURORA|TERROR|FERVOR|HOLLOW)_(HELMET|CHESTPLATE|LEGGINGS|BOOTS)$")
	private val perfect = Regex("^PERFECT_(HELMET|CHESTPLATE|LEGGINGS|BOOTS)_(\\d+)$")
	private val minion = Regex("^([A-Z0-9_]+)_GENERATOR_(\\d+)$")
	private val gem = Regex("^(ROUGH|FLAWED|FINE|FLAWLESS|PERFECT)_([A-Z]+)_GEM$")
	private val gemCrystal = Regex("^([A-Z]+)_CRYSTAL$")
	private val rarity = Regex("^(.+)_(COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE|SPECIAL)$")
	private val trophy = Regex("^([A-Z0-9_]+)_(BRONZE|SILVER|GOLD|DIAMOND)$")
	private val campfire = Regex("^(SOUL_)?CAMPFIRE_TALISMAN_\\d+$")
	private val masterStar = Regex("^(FIRST|SECOND|THIRD|FOURTH|FIFTH)_MASTER_STAR$")
	private val rune = Regex("^([A-Z0-9_]+)_RUNE;(\\d+)$")

	private fun family(id: String, name: String?): WikiTarget? {
		starred.matchEntire(id)?.let { m ->
			val inner = m.groupValues[1]
			return resolve(ItemRef(inner, name))?.copy(rule = "starred")
		}
		heated.matchEntire(id)?.let { return WikiTarget("${WikiTitles.titleCase(it.groupValues[2])} Armor", rule = "kuudra-armour") }
		kuudraSet.matchEntire(id)?.let { return WikiTarget("${WikiTitles.titleCase(it.groupValues[1])} Armor", rule = "kuudra-armour") }
		perfect.matchEntire(id)?.let { return WikiTarget("Perfect Armor", if (it.groupValues[2] == "1") "Tier I" else null, rule = "perfect-armour") }
		minion.matchEntire(id)?.let { m ->
			val sib = (1..12).firstNotNullOfOrNull { urlTitle["${m.groupValues[1]}_GENERATOR_$it"]?.first }
			val fromName = name?.let { n -> n.split(' ').let { w -> if (w.size > 1 && WikiTitles.isRoman(w.last())) w.dropLast(1).joinToString(" ") else n } }
			val page = sib ?: fromName?.takeIf { it.endsWith(" Minion") } ?: "${WikiTitles.titleCase(m.groupValues[1])} Minion"
			return WikiTarget(page, rule = "minion")
		}
		gem.matchEntire(id)?.let { if (it.groupValues[2] in gems) return WikiTarget("Gemstone", rule = "gemstone") }
		gemCrystal.matchEntire(id)?.let { if (it.groupValues[1] in gems) return WikiTarget("Gemstone Crystals", WikiTitles.titleCase(it.groupValues[1]), rule = "gemstone-crystal") }
		trophy.matchEntire(id)?.let { m ->
			val fish = m.groupValues[1]
			if (fish in trophyFish) {
				val anchor = if (fish in trophyNoAnchor) null else trophyAnchor(fish)
				return WikiTarget("Trophy Fish", anchor, rule = "trophy-fish")
			}
		}
		campfire.matchEntire(id)?.let { return WikiTarget(if (it.groupValues[1].isNotEmpty()) "Soul Campfire Badge" else "Campfire Badge", rule = "campfire") }
		masterStar.matchEntire(id)?.let { return WikiTarget("Master Star", rule = "master-star") }
		if ("KUUDRA" in id && id.endsWith("_KEY") || id.startsWith("KUUDRA_") && "_KEY" in id) return WikiTarget("Kuudra Keys", rule = "kuudra-key")
		rune.matchEntire(id)?.let { m ->
			val runeName = name?.replace("◆", "")?.trim()?.split(' ')?.let { w -> if (w.size > 1 && WikiTitles.isRoman(w.last())) w.dropLast(1).joinToString(" ") else w.joinToString(" ") }
				?.takeIf { it.endsWith(" Rune") } ?: "${WikiTitles.titleCase(m.groupValues[1])} Rune"
			return WikiTarget(runeName, alternates = listOf("Runes"), rule = "rune")
		}
		if (id == "POTION" && name != null) {
			var n = name.removePrefix("Splash ").trim()
			n.split(' ').let { w -> if (w.size > 1 && WikiTitles.isRoman(w.last())) n = w.dropLast(1).joinToString(" ") }
			return WikiTarget(n, alternates = listOf("Potions"), rule = "potion")
		}
		rarity.matchEntire(id)?.let { m ->
			val base = m.groupValues[1]
			if (base.startsWith("PET_ITEM_") || base == "BEASTMASTER_CREST" || base == "GRIFFIN_UPGRADE_STONE") {
				val sib = (rarities.map { "${base}_$it" } + base + "BASE_$base").firstNotNullOfOrNull { urlTitle[it]?.first }
				val page = sib ?: name?.let { n -> rarities.fold(n) { acc, r -> acc.removePrefix(WikiTitles.titleCase(r) + " ") } } ?: WikiTitles.titleCase(base.removePrefix("PET_ITEM_"))
				return WikiTarget(page, rule = "rarity-family")
			}
		}
		hub(id, name)?.let { return it }
		return null
	}

	private fun trophyAnchor(fish: String): String = when (fish) {
		"OBFUSCATED_FISH_1" -> "Obfuscated 1"
		"OBFUSCATED_FISH_2" -> "Obfuscated 2"
		"OBFUSCATED_FISH_3" -> "Obfuscated 3"
		else -> WikiTitles.titleCase(fish)
	}

	private val bannerId = Regex("^BANNER-\\d+$")
	private val powderTier = Regex("^(MITHRIL|GEMSTONE|GLACITE)_POWDER_TIER_\\d+$")
	private val classAbility = Regex("^(ARCHER|BERSERK|HEALER|MAGE|TANK)_(DUNGEON|ULTIMATE)_ABILITY(_\\d+)?$")

	private fun hub(id: String, name: String?): WikiTarget? {
		if (id.startsWith("ABIPHONE_")) return WikiTarget("Abiphones", rule = "hub")
		if (id.endsWith("_BACKPACK")) return WikiTarget("Backpack", rule = "hub")
		if (id.endsWith("_PERSONALITY")) {
			val skin = name?.removeSuffix(" Minion Skin")?.trim()?.takeIf { it.isNotEmpty() && it != name }
			return WikiTarget("Minion Skins", skin, rule = "hub")
		}
		if (id.startsWith("LAMP_")) return WikiTarget("Lamps", rule = "hub")
		if (id == "BANNER" || bannerId.matches(id)) return WikiTarget("Banner", rule = "hub")
		powderTier.matchEntire(id)?.let { return WikiTarget("${WikiTitles.titleCase(it.groupValues[1])} Powder", rule = "hub") }
		if (id.endsWith("_TALISMAN_BAG")) return WikiTarget("Accessory Bag", rule = "hub")
		if (id.endsWith("_RUNES_SACK")) return WikiTarget("Rune Sack", rule = "hub")
		if (id.endsWith("_POWER_SCROLL")) return WikiTarget("Power Scrolls", rule = "hub")
		classAbility.matchEntire(id)?.let { return WikiTarget(WikiTitles.titleCase(it.groupValues[1]), rule = "class-ability") }
		if (id.endsWith("_BARN_SKIN") && urlTitle[id] == null) return WikiTarget("Barn Skins", rule = "hub")
		if (id.startsWith("TALISMAN_ENRICHMENT_")) return WikiTarget("Enrichments", rule = "hub")
		if (id.endsWith("_GARDEN_CHIP")) return WikiTarget("Garden Chips", rule = "hub")
		if (id.endsWith("_TRAVEL_SCROLL")) {
			val own = urlTitle[id]?.first
			val area = name?.substringAfter("Travel Scroll to ", "")?.trim()?.takeIf { it.isNotEmpty() }
			return WikiTarget("Travel Scrolls", area, alternates = listOfNotNull(own), rule = "hub")
		}
		return null
	}

	private fun repoLink(id: String, name: String?): WikiTarget? {
		val own = urlTitle[id] ?: siblingLink(id) ?: return null
		val (title, anchor) = own

		val base = name?.let { WikiResolveNames.baseName(it, reforges) }
		if ((hubSize[title] ?: 0) > 1 && base != null && WikiTitles.normalise(base) != title && base.isNotBlank())
			return WikiTarget(WikiTitles.normalise(base), alternates = listOf(title), rule = "repo-hub-own")
		return WikiTarget(title, anchor, rule = "repo")
	}

	private val tierSuffix = Regex("^(.+?)_(\\d+)$")

	private fun siblingLink(id: String): Pair<String, String?>? {
		tierSuffix.matchEntire(id)?.let { m ->
			val b = m.groupValues[1]
			(listOf(b) + (0..12).map { "${b}_$it" }).firstNotNullOfOrNull { urlTitle[it] }?.let { return it }
		}
		rarity.matchEntire(id)?.let { m -> urlTitle[m.groupValues[1]]?.let { return it } }
		return null
	}
}

object WikiResolveNames {
	private val colour = dev.ashtonite365.apex.util.SharedRegex.SECTION
	private val starChars = Regex("[✪➊➋➌➍➎⚚✦✿❁⚔◆]")
	private val petLevel = Regex("^\\[Lvl [^]]*]\\s*")
	private val countSuffix = Regex("\\s+x[\\d,]+$")
	private val countPrefix = Regex("^[\\d,]+x\\s+")

	fun baseName(display: String, reforges: Collection<String>): String {
		var s = colour.replace(display, "").replace(starChars, "").replace(Regex("^[✔✖]\\s*"), "").trim()
		s = petLevel.replace(s, "").let { countSuffix.replace(it, "") }.let { countPrefix.replace(it, "") }.trim()
		val r = reforges.filter { it.isNotBlank() && s.startsWith("$it ", ignoreCase = true) }.maxByOrNull { it.length }
		if (r != null && s.length > r.length + 1) s = s.substring(r.length + 1)
		return s.replace(dev.ashtonite365.apex.util.SharedRegex.WHITESPACE, " ").trim()
	}
}
