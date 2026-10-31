package dev.ashtonite365.apex.features.profileviewer

import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.repo.ExpLadders
import dev.ashtonite365.apex.repo.ExpensiveItemCacheApi
import dev.ashtonite365.apex.repo.HypixelStaticData
import dev.ashtonite365.apex.repo.ReforgeStore.kJson
import dev.ashtonite365.apex.repo.RepoManager
import dev.ashtonite365.apex.repo.SBItemStack
import dev.ashtonite365.apex.repo.SafeRepoConstants
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.MinecraftDispatcher
import dev.ashtonite365.apex.util.mc.NeedsComponents
import dev.ashtonite365.apex.util.tr
import io.github.moulberry.repo.data.Rarity
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.PlayerSkinWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

class ProfileViewerScreen(val requestedName: String) : Screen(Component.literal("Profile Viewer")) {
	companion object {
		const val PANEL_WIDTH = 360
		const val PANEL_HEIGHT = 220
		const val SLOT = 18

		const val BACKPACK_SLOTS = 54

		const val COLUMN_GAP = 6

		fun twoColumnBlockRows(kind: InventoryKind): Int? = when (kind) {
			InventoryKind.ENDER_CHEST, InventoryKind.ACCESSORIES -> 5
			InventoryKind.BACKPACKS -> 6
			else -> null
		}

		fun gridCell(index: Int, blockRows: Int?): Pair<Int, Int> {
			if (blockRows == null) return index % 9 to index / 9
			val perBlock = blockRows * 9
			val block = index / perBlock
			val inBlock = index % perBlock
			return inBlock % 9 + 9 * (block % 2) to inBlock / 9 + blockRows * (block / 2)
		}

		fun gridRows(size: Int, blockRows: Int?): Int {
			if (size <= 0) return 0
			if (blockRows == null) return (size + 8) / 9
			val perPair = blockRows * 18
			val pairs = (size + perPair - 1) / perPair
			val rest = size - (pairs - 1) * perPair
			return (pairs - 1) * blockRows + minOf(blockRows, (rest + 8) / 9)
		}
	}

	enum class Page(val label: Component) {
		OVERVIEW(tr("apex.pv.page.overview", "Info")),
		INVENTORIES(tr("apex.pv.page.inventories", "Items")),
		PETS(tr("apex.pv.page.pets", "Pets")),
		COLLECTIONS(tr("apex.pv.page.collections", "Coll.")),
		DUNGEONS(tr("apex.pv.page.dungeons", "Dungeon")),
		MINING(tr("apex.pv.page.mining", "HOTM")),
		MUSEUM(tr("apex.pv.page.museum", "Museum")),
		RIFT(tr("apex.pv.page.rift", "Rift")),
		CRIMSON(tr("apex.pv.page.crimson", "Crimson")),
		BESTIARY(tr("apex.pv.page.bestiary", "Bestiary")),
		GARDEN(tr("apex.pv.page.garden", "Garden")),
		CHOCOLATE(tr("apex.pv.page.chocolate", "Choco")),
		GLACITE(tr("apex.pv.page.glacite", "Glacite")),
		FORAGING(tr("apex.pv.page.foraging", "Galatea")),
		HUNTING(tr("apex.pv.page.hunting", "Hunting")),
		SACKS(tr("apex.pv.page.sacks", "Sacks")),
	}

	enum class InventoryKind(val label: String) {
		INVENTORY("Inventory"),
		ARMOR("Armor"),
		ENDER_CHEST("Ender Chest"),
		BACKPACKS("Backpacks"),
		WARDROBE("Wardrobe"),
		ACCESSORIES("Accessories"),
		VAULT("Vault"),
		POTION_BAG("Potions"),
		FISHING_BAG("Fishing"),
		QUIVER("Quiver"),
		CANDY_BAG("Candy"),
		SACK_BAG("Sack Bag"),
	}

	data class DisplayItem(val stack: ItemStack, val tooltip: List<Component>, val highlight: Boolean = false)

	private var result: ProfileViewer.LoadResult? = null
	private var profileIndex = 0
	private var page = Page.OVERVIEW
	private var inventoryKind = InventoryKind.INVENTORY
	private var scroll = 0
	private var cachedItems: List<DisplayItem?>? = null

	private val skills = listOf(
		"Farming" to 0xFFE3C000, "Mining" to 0xFFAAAAAA, "Combat" to 0xFFFF5555, "Foraging" to 0xFF8B5A2B,
		"Fishing" to 0xFF5555FF, "Enchanting" to 0xFFFF55FF, "Alchemy" to 0xFFAA00AA, "Taming" to 0xFF55FF55,
		"Carpentry" to 0xFFFFAA00, "Runecrafting" to 0xFFFF77FF, "Social" to 0xFFFFFFFF,
	)
	private val networthCache = mutableMapOf<String, Networth.Breakdown>()
	private val hotmLayout: JsonObject? by lazy {
		try {
			SafeRepoConstants.file(RepoManager.coreRepo, "constants/hotmlayout.json", "profile viewer HOTM tree")
				?.kJson(JsonObject.serializer())
		} catch (e: Exception) {
			Apex.logger.warn("Could not load hotmlayout.json", e)
			null
		}
	}
	private val gardenData = mutableMapOf<String, JsonObject>()
	private val gardenLoading = mutableSetOf<String>()
	private val museumData = mutableMapOf<String, JsonObject>()
	private val museumLoading = mutableSetOf<String>()

	private val profiles get() = (result as? ProfileViewer.LoadResult.Success)?.data
	private val profile get() = profiles?.profiles?.getOrNull(profileIndex)
	private val left get() = (width - PANEL_WIDTH) / 2
	private val top get() = 35

	fun setResult(result: ProfileViewer.LoadResult) {
		this.result = result
		profileIndex = (result as? ProfileViewer.LoadResult.Success)?.data?.profiles
			?.indexOfFirst { it.selected }?.coerceAtLeast(0) ?: 0
		invalidate()

		if (minecraft != null) rebuildWidgets()
	}

	private fun invalidate() {
		cachedItems = null
		scroll = 0
	}

	override fun init() {
		if (result == null) return
		for ((i, p) in Page.entries.withIndex()) {
			addRenderableWidget(Button.builder(p.label) { page = p; invalidate(); rebuildWidgets() }
				.bounds(left + 45 * (i % 8), 3 + 16 * (i / 8), 43, 15).build()).active = p != page
		}
		val all = profiles?.profiles ?: listOf()
		if (all.size > 1) {
			fun cycle(step: Int) {
				profileIndex = (profileIndex + step).mod(all.size)
				invalidate(); rebuildWidgets()
			}
			addRenderableWidget(Button.builder(Component.literal("◀")) { cycle(-1) }.bounds(left + 230, top + 6, 16, 14).build())
			addRenderableWidget(Button.builder(Component.literal("▶")) { cycle(1) }.bounds(left + 338, top + 6, 16, 14).build())
		}
		if (page == Page.INVENTORIES) for ((i, k) in InventoryKind.entries.withIndex()) {
			addRenderableWidget(Button.builder(Component.literal(k.label)) { inventoryKind = k; invalidate(); rebuildWidgets() }
				.bounds(left + 6 + 58 * (i % 6), top + 22 + 14 * (i / 6), 56, 13).build()).active = k != inventoryKind
		}
		val game = minecraft
		val gameProfile = profiles?.gameProfile
		if (page == Page.OVERVIEW && gameProfile != null && game != null)
			addRenderableWidget(PlayerSkinWidget(56, 100, game.entityModels, game.skinManager.createLookup(gameProfile, false)))
				.setPosition(left - 58, top + 40)
	}

	override fun isPauseScreen(): Boolean = false

	override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
		val gridTop = gridTop() ?: return true
		val rows = gridRows(items().size, if (page == Page.INVENTORIES) twoColumnBlockRows(inventoryKind) else null)
		val maxScroll = (rows * SLOT - (gridBottom() - gridTop)).coerceAtLeast(0)
		scroll = (scroll - (verticalAmount * SLOT).toInt()).coerceIn(0, maxScroll)
		return true
	}

	private fun gridBottom() = top + PANEL_HEIGHT - 8

	private fun gridTop(): Int? = when (page) {
		Page.INVENTORIES -> top + 52
		Page.PETS, Page.COLLECTIONS -> top + 26
		Page.MUSEUM -> top + 48
		else -> null
	}

	private fun lit(s: String): Component = Component.literal(s)
	private fun GuiGraphicsExtractor.line(s: String, x: Int, y: Int) = text(font, lit(s), x, y, -1, true)

	override fun extractBackground(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
		if (!dev.ashtonite365.apex.features.menus.style.MenuStyle.clearOutsideNow()) super.extractBackground(context, mouseX, mouseY, delta)
	}

	override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
		super.extractRenderState(context, mouseX, mouseY, delta)

		ProfileViewerStyle.panel(context, font, left, top, PANEL_WIDTH, PANEL_HEIGHT)
		try { drawPage(context, mouseX, mouseY) } finally { ProfileViewerStyle.buttons(context, font, children(), mouseX, mouseY) }
	}

	private fun drawPage(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
		val centreX = left + PANEL_WIDTH / 2
		val centreY = top + PANEL_HEIGHT / 2
		when (val r = result) {
			null -> { context.centeredText(font, tr("apex.pv.loading", "Loading $requestedName..."), centreX, centreY, -1); return }
			is ProfileViewer.LoadResult.Error -> { context.centeredText(font, r.message, centreX, centreY, 0xFFFF5555.toInt()); return }
			is ProfileViewer.LoadResult.Success -> context.line(r.data.displayName, left + 8, top + 8)
		}
		val p = profile
		if (p == null) {
			context.centeredText(font, tr("apex.pv.no-profiles", "This player has no SkyBlock profiles."), centreX, centreY, 0xFFFF5555.toInt())
			return
		}
		val modeMark = when (p.gameMode) {
			"ironman" -> " §7♲"
			"island" -> " §b☀"
			"bingo" -> " §eⒷ"
			else -> ""
		}
		context.centeredText(font, lit("§e${p.cuteName}$modeMark"), left + 292, top + 9, -1)
		val member = p.member
		when (page) {
			Page.OVERVIEW -> drawOverview(context, p, mouseX, mouseY)
			Page.INVENTORIES, Page.PETS -> drawItemGrid(context, mouseX, mouseY, gridTop()!!)
			Page.COLLECTIONS -> when (HypixelStaticData.collectionDataState) {
				HypixelStaticData.CollectionDataState.LOADED -> drawItemGrid(context, mouseX, mouseY, gridTop()!!)
				HypixelStaticData.CollectionDataState.FAILED -> {
					HypixelStaticData.requestCollectionData()
					context.centeredText(font, tr("apex.pv.collections-failed", "Could not download collection data, retrying..."), centreX, (gridTop()!! + gridBottom()) / 2, -1)
				}
				else -> {
					HypixelStaticData.requestCollectionData()
					context.centeredText(font, tr("apex.pv.collections-loading", "Loading collection data..."), centreX, (gridTop()!! + gridBottom()) / 2, -1)
				}
			}
			Page.DUNGEONS -> drawDungeons(context, p)
			Page.MINING -> drawHotm(context, member, mouseX, mouseY)
			Page.MUSEUM -> {
				for ((i, s) in museumLines(p).withIndex()) context.line(s, left + 10, top + 26 + 10 * i)
				drawItemGrid(context, mouseX, mouseY, gridTop()!!)
			}
			Page.RIFT -> drawLines(context, riftLines(member))
			Page.CRIMSON -> drawLines(context, crimsonLines(member))
			Page.BESTIARY -> drawLines(context, ProfileViewerPages.bestiary(member))
			Page.GARDEN -> drawLines(context, gardenLines(p))
			Page.CHOCOLATE -> drawLines(context, ProfileViewerPages.chocolate(member))
			Page.GLACITE -> drawLines(context, ProfileViewerPages.glacite(member))
			Page.FORAGING -> drawLines(context, ProfileViewerPages.foraging(member))
			Page.HUNTING -> drawLines(context, ProfileViewerPages.attributes(member))
			Page.SACKS -> drawLines(context, ProfileViewerPages.sacks(member))
		}
	}

	private fun levelFromLadder(xp: Double, ladder: List<Int>, cap: Int): Pair<Int, Double> {
		var rest = xp
		var level = 0
		while (level < cap && level < ladder.size && rest >= ladder[level]) {
			rest -= ladder[level]
			level++
		}
		val progress = if (level < cap && level < ladder.size && ladder[level] > 0) rest / ladder[level] else 0.0
		return level to progress
	}

	private fun drawBar(context: GuiGraphicsExtractor, x: Int, y: Int, w: Int, progress: Double, colour: Int) {
		context.fill(x, y, x + w, y + 3, 0xFF202020.toInt())
		context.fill(x, y, x + (w * progress.coerceIn(0.0, 1.0)).toInt(), y + 3, colour)
	}

	private fun skillLevel(member: JsonObject, skill: String): Pair<Int, Double>? {
		val xp = member.path("player_data", "experience", "SKILL_$skill").num() ?: return null
		val leveling = SafeRepoConstants.leveling() ?: return null
		val ladder = when (skill) {
			"RUNECRAFTING" -> leveling.runecraftingExperienceRequiredPerLevel
			"SOCIAL" -> leveling.socialExperienceRequiredPerLevel
			else -> leveling.skillExperienceRequiredPerLevel
		}
		val cap = leveling.maximumLevels[skill.lowercase()] ?: ladder.size
		return levelFromLadder(xp, ladder, cap)
	}

	private fun drawOverview(context: GuiGraphicsExtractor, profile: ProfileViewer.SkyblockProfile, mouseX: Int, mouseY: Int) {
		val member = profile.member
		val x = left + 10
		var y = top + 28
		fun line(text: String) {
			context.line(text, x, y)
			y += 11
		}
		val sbXp = member.path("leveling", "experience").long() ?: 0
		line("§bSkyBlock Level: §f${sbXp / 100} §7(${sbXp % 100}/100)")
		line("§6Purse: §f${fmt(member.path("currencies", "coin_purse").num())}")
		profile.bank?.let { line("§6Bank: §f${fmt(it)}") }
		member.path("fairy_soul", "total_collected").long()?.let { line("§dFairy Souls: §f$it") }
		if (profile.memberCount > 1) line("§7Co-op members: §f${profile.memberCount}")
		val nw = networth(profile)
		val nwText = "§6Networth: §f${ApexFormatters.compactNumber(nw.total)} §8(hover)"
		if (mouseX >= x && mouseX < x + font.width(lit(nwText)) && mouseY >= y && mouseY < y + 10)
			context.setComponentTooltipForNextFrame(font, networthTooltip(nw), mouseX, mouseY)
		line(nwText)
		line("§5Magical Power: §f${ProfileViewerPages.magicalPower(member) ?: "§cno accessory bag data"}")
		line("§aPet score: §f${ProfileViewerPages.petScore(member)}")
		val levels = skills.map { (name, _) -> skillLevel(member, name.uppercase()) }
		val averaged = levels.take(8).filterNotNull()
		if (averaged.isEmpty()) line("§cSkills API disabled")
		else line("§aSkill Average: §f${"%.2f".format(averaged.sumOf { it.first + it.second } / averaged.size)}")
		y += 6
		line("§cSlayers:")
		val slayerLadders = SafeRepoConstants.leveling()?.slayerExperienceRequiredPerLevel ?: mapOf()
		for ((key, name) in listOf("zombie" to "Revenant", "spider" to "Tarantula", "wolf" to "Sven",
			"enderman" to "Voidgloom", "blaze" to "Inferno", "vampire" to "Riftstalker")) {
			val xp = member.path("slayer", "slayer_bosses", key, "xp").long() ?: 0
			val level = slayerLadders[key]?.count { xp >= it } ?: 0
			line(" §7$name: §f$level §8(${fmt(xp)} xp)")
		}
		for ((i, skill) in skills.withIndex()) {
			val (lvl, progress) = levels[i] ?: continue
			val bx = left + 170 + 90 * (i % 2)
			val by = top + 30 + 26 * (i / 2)
			context.line("§7${skill.first} §f$lvl", bx, by)
			drawBar(context, bx, by + 11, 80, progress, skill.second.toInt())
		}
	}

	private fun networth(profile: ProfileViewer.SkyblockProfile): Networth.Breakdown =
		networthCache.getOrPut(profile.profileId) { Networth.calculate(profile) }

	private fun networthTooltip(nw: Networth.Breakdown): List<Component> {
		fun c(n: Double) = ApexFormatters.compactNumber(n)
		return listOf(
			"§6Networth: §f${fmt(nw.total)}",
			"§7Purse: §f${c(nw.purse)}",
			"§7Bank: §f${c(nw.bank)}",
			"§7Items: §f${c(nw.items)}",
			"§7Enchantments: §f${c(nw.enchantments)}",
			"§7Reforges: §f${c(nw.reforges)}",
			"§7Recombobulators: §f${c(nw.recombobulators)}",
			"§7Gemstones: §f${c(nw.gemstones)}",
			"§7Potato books / Art of War: §f${c(nw.books)}",
			"§7Pets: §f${c(nw.pets)}",
			"§7Sacks: §f${c(nw.sacks)}",
			"§7Essence: §f${c(nw.essence)}",
			"§8Estimate from lowest BIN / bazaar prices.",
		).map(::lit)
	}

	private fun drawLines(context: GuiGraphicsExtractor, lines: List<String>) {
		for ((i, s) in lines.withIndex()) context.line(s, left + 10, top + 28 + 11 * i)
	}

	private fun fmt(n: Number?) = ApexFormatters.withThousands((n ?: 0).toDouble(), 1)

	private fun miningLines(member: JsonObject): List<String> {
		val core = member.obj("mining_core") ?: return listOf("§cNo Heart of the Mountain data.")
		val ladder = SafeRepoConstants.leveling()?.hotmExperienceRequiredPerLevel ?: listOf()
		val (level, progress) = levelFromLadder(core["experience"].num() ?: 0.0, ladder, ladder.size)
		return buildList {
			add("§5Heart of the Mountain §f$level §7(${(progress * 100).toInt()}%)")
			add("§7Tokens: §f${fmt(core["tokens"].num())} §7(spent §f${fmt(core["tokens_spent"].num())}§7)")
			for ((key, name) in listOf("mithril" to "§2Mithril", "gemstone" to "§dGemstone", "glacite" to "§bGlacite")) {
				val current = core["powder_$key"].num() ?: 0.0
				val spent = core["powder_spent_$key"].num() ?: 0.0
				add("$name Powder: §f${fmt(current)} §7(total §f${fmt(current + spent)}§7)")
			}
			add("§7Pickaxe ability: §f${core["selected_pickaxe_ability"].str()?.let(::prettify) ?: "None"}")
			val crystals = core.obj("crystals")?.values?.count { ((it as? JsonObject)?.get("total_found").long() ?: 0) > 0 } ?: 0
			add("§7Crystals found: §f$crystals/9")
		}
	}

	private fun drawHotm(context: GuiGraphicsExtractor, member: JsonObject, mouseX: Int, mouseY: Int) {
		val gx = left + 10
		val gy = top + 28
		val textX = left + 150
		val nodes = member.path("mining_core", "nodes") as? JsonObject
		val perks = hotmLayout?.path("hotm", "perks") as? JsonObject
		val lines = mutableListOf<String>()
		if (perks == null) lines += "§cHOTM layout missing from repo."
		else {
			ProfileViewerStyle.grid(context, font, gx, gy, 7, 10, SLOT)
			var tooltip: List<Component>? = null
			for ((key, value) in perks) {
				val perk = value as? JsonObject ?: continue
				val px = perk["x"].long()?.toInt() ?: continue
				val py = perk["y"].long()?.toInt() ?: continue
				val max = perk["maxLevel"].long()?.toInt() ?: 1
				val level = nodes?.get(key).long()?.toInt() ?: 0
				val icon = when {
					level <= 0 -> Items.COAL
					level >= max -> Items.EMERALD_BLOCK
					else -> Items.EMERALD
				}
				val stack = ItemStack(icon, level.coerceIn(1, 99))
				val ix = gx + 1 + SLOT * px
				val iy = gy + 1 + SLOT * py
				context.item(stack, ix, iy)
				if (level > 0) context.itemDecorations(font, stack, ix, iy)
				if (mouseX >= ix && mouseX < ix + 16 && mouseY >= iy && mouseY < iy + 16) tooltip = buildList {
					add(lit("§e${perk["name"].str() ?: prettify(key)} §7(${prettify(perk["powder"].str() ?: "")})"))
					add(lit("§7Level §f$level§7/§f$max"))
					if (nodes?.get("toggle_$key").bool() == false) add(lit("§cDisabled"))
				}
			}
			tooltip?.let { context.setComponentTooltipForNextFrame(font, it, mouseX, mouseY) }
		}
		lines += miningLines(member)
		lines += ""
		lines += ProfileViewerPages.heartOfTheForest(member).ifEmpty { listOf("§8No Heart of the Forest data.") }
		for ((i, s) in lines.withIndex()) context.line(s, textX, top + 28 + 11 * i)
	}

	private fun riftLines(member: JsonObject): List<String> {
		val rift = member.obj("rift") ?: return listOf("§cNo Rift data.")
		val stats = member.path("player_stats", "rift") as? JsonObject
		fun size(vararg path: String) = (rift.path(*path) as? JsonArray)?.size ?: 0
		return listOf(
			"§dMotes: §f${fmt(member.path("currencies", "motes_purse").num())}",
			"§dLifetime motes: §f${fmt(stats?.get("lifetime_motes_earned").num())}",
			"§7Visits: §f${fmt(stats?.get("visits").num())}",
			"§5Enigma souls: §f${size("enigma", "found_souls")}",
			"§7Montezuma cat pieces: §f${size("dead_cats", "found_cats")}",
			"§7Grubber stacks: §f${fmt(rift.path("castle", "grubber_stacks").num())}",
			"§7Burger stacks: §f${fmt(rift.path("castle", "burger_stacks").num())}",
			"§7Timecharms: §f${size("gallery", "secured_trophies")}/8",
		)
	}

	private fun crimsonLines(member: JsonObject): List<String> {
		val isle = member.obj("nether_island_crimson_isle") ?: return listOf("§cNo Crimson Isle data.")
		return buildList {
			add("§7Faction: §f${isle["selected_faction"].str()?.let(::prettify) ?: "None"}")
			add("§5Mage reputation: §f${fmt(isle["mages_reputation"].num())}")
			add("§cBarbarian reputation: §f${fmt(isle["barbarians_reputation"].num())}")
			add("")
			add("§6Kuudra completions:")
			val tiers = isle.obj("kuudra_completed_tiers")
			for ((key, name) in listOf("none" to "Basic", "hot" to "Hot", "burning" to "Burning", "fiery" to "Fiery", "infernal" to "Infernal"))
				add(" §7$name: §f${fmt(tiers?.get(key).num())}")
			add("")
			add("§eDojo:")
			isle.obj("dojo")?.entries?.filter { it.key.startsWith("dojo_points_") }?.forEach { (k, v) ->
				add(" §7${prettify(k.removePrefix("dojo_points_"))}: §f${fmt(v.num())}")
			}
		}
	}

	private fun gardenLines(profile: ProfileViewer.SkyblockProfile): List<String> {
		val id = profile.profileId
		gardenData[id]?.let { return ProfileViewerPages.garden(it, false) }
		if (gardenLoading.add(id)) Apex.coroutineScope.launch {
			val data = try {
				ProfileViewer.ursaJson("v1/hypixel/v2/garden/$id")
			} catch (e: Exception) {
				Apex.logger.warn("Could not load garden", e)
				JsonObject(mapOf())
			}
			withContext(MinecraftDispatcher) {
				gardenData[id] = data
				gardenLoading.remove(id)
			}
		}
		return ProfileViewerPages.garden(null, true)
	}

	private fun museumMember(profile: ProfileViewer.SkyblockProfile): JsonObject? {
		val uuid = profiles?.uuid?.toString()?.replace("-", "") ?: return null
		return museumData[profile.profileId]?.obj("members")?.obj(uuid)
	}

	private fun museumLines(profile: ProfileViewer.SkyblockProfile): List<String> {
		val id = profile.profileId
		if (id !in museumData) {
			if (museumLoading.add(id)) Apex.coroutineScope.launch {
				val data = try {
					ProfileViewer.ursaJson("v1/hypixel/v2/museum/$id")
				} catch (e: Exception) {
					Apex.logger.warn("Could not load museum", e)
					JsonObject(mapOf())
				}
				withContext(MinecraftDispatcher) {
					museumData[id] = data
					museumLoading.remove(id)
					cachedItems = null
				}
			}
			return listOf("§7Loading museum...")
		}
		val m = museumMember(profile) ?: return listOf("§cNo museum data (museum API disabled or not unlocked).")
		val items = m.obj("items") ?: JsonObject(mapOf())
		val borrowed = items.values.count { (it as? JsonObject)?.get("borrowing").bool() == true }
		val appraised = if (m["appraisal"].bool() == true) "Yes" else "No"
		return listOf(
			"§6Value: §f${fmt(m["value"].num())} §7| Appraised: §f$appraised",
			"§7Donations: §f${items.size} §7| Special: §f${m.arr("special").size} §7| Borrowed: §f$borrowed",
		)
	}

	private fun museumItems(profile: ProfileViewer.SkyblockProfile): List<DisplayItem?> {
		val m = museumMember(profile) ?: return listOf()
		val out = mutableListOf<DisplayItem?>()
		for ((_, entry) in (m.obj("items") ?: JsonObject(mapOf())).entries.sortedBy { it.key }) {
			val e = entry as? JsonObject ?: continue
			val borrowing = e["borrowing"].bool() == true
			ProfileViewer.decodeInventory(e["items"]).forEach { out += toDisplay(it)?.copy(highlight = borrowing) }
		}
		val specials = m.arr("special").flatMap { ProfileViewer.decodeInventory((it as? JsonObject)?.get("items")) }
		if (specials.isNotEmpty()) {
			while (out.size % 9 != 0) out += null
			repeat(9) { out += null }
			specials.forEach { out += toDisplay(it) }
		}
		return out
	}

	private fun prettify(key: String) = ProfileViewerPages.prettify(key)

	private fun collectionItems(member: JsonObject): List<DisplayItem?> {
		val collected = member.obj("collection") ?: return listOf()
		return HypixelStaticData.collectionData.entries.sortedBy { it.key }.flatMap { (skill, data) ->
			data.items.map { (type, info) ->
				val amount = collected[type.string].long() ?: 0
				val tier = info.tiers.filter { amount >= it.amountRequired }.maxOfOrNull { it.tier } ?: 0
				val next = info.tiers.firstOrNull { amount < it.amountRequired }
				val stack = itemFor(type.catalogItemId)
				DisplayItem(stack, buildList {
					add(lit("§e${info.name} §7(${prettify(skill)})"))
					add(lit("§7Collected: §f${fmt(amount)}"))
					add(lit("§7Tier: §f$tier/${info.maxTiers}"))
					if (next != null) add(lit("§7Next tier at: §f${fmt(next.amountRequired)}")) else add(lit("§6Maxed!"))
				}, highlight = next == null && amount > 0)
			}
		}
	}

	@OptIn(ExpensiveItemCacheApi::class, NeedsComponents::class)
	private fun itemFor(id: ItemId): ItemStack =
		if (RepoManager.getCoreItem(id) != null) SBItemStack(id).asImmutableItemStack().current().copy()
		else ItemStack(Items.PAPER)

	private fun drawDungeons(context: GuiGraphicsExtractor, profile: ProfileViewer.SkyblockProfile) {
		val d = profile.member.obj("dungeons")
		if (d == null) {
			context.centeredText(font, tr("apex.pv.no-dungeons", "No dungeon data."), left + PANEL_WIDTH / 2, top + PANEL_HEIGHT / 2, -1)
			return
		}
		val ladder = SafeRepoConstants.leveling()?.catacombsExperienceRequiredPerLevel ?: listOf()
		val x = left + 10
		var y = top + 28
		val (cata, cataProgress) = levelFromLadder(d.path("dungeon_types", "catacombs", "experience").num() ?: 0.0, ladder, 50)
		context.line("§cCatacombs §f$cata", x, y)
		drawBar(context, x, y + 11, 140, cataProgress, 0xFFFF5555.toInt())
		y += 22
		val selected = d["selected_dungeon_class"].str()
		for (cls in listOf("healer", "mage", "berserk", "archer", "tank")) {
			val (lvl, progress) = levelFromLadder(d.path("player_classes", cls, "experience").num() ?: 0.0, ladder, 50)
			context.line("${if (cls == selected) "§a" else "§7"}${prettify(cls)} §f$lvl", x, y)
			drawBar(context, x, y + 10, 140, progress, 0xFF55FF55.toInt())
			y += 18
		}
		context.line("§bSecrets: §f${fmt(d["secrets"].num())}", x, y + 4)

		val showPbs = ProfileViewer.TConfig.showDungeonPbs
		val tx = left + 180
		context.line("§7Floor  §fNormal  §cMaster${if (showPbs) "  §ePB S+" else ""}", tx, top + 28)
		for (floor in 0..7) {
			val ry = top + 40 + 11 * floor
			fun count(type: String) = fmt(d.path("dungeon_types", type, "tier_completions", "$floor").num())
			fun pb(type: String) = d.path("dungeon_types", type, "fastest_time_s_plus", "$floor").num()?.let(::formatRunTime)
			context.line(if (floor == 0) "§7Entr." else "§7F$floor", tx, ry)
			context.line("§f${count("catacombs")}", tx + 36, ry)
			if (floor > 0) context.line("§c${count("master_catacombs")}", tx + 80, ry)
			if (showPbs) {
				val normal = pb("catacombs")?.let { "§e$it" } ?: "§8-"
				context.line(if (floor == 0) normal else "$normal §c${pb("master_catacombs") ?: "-"}", tx + 112, ry)
			}
		}
	}

	private fun formatRunTime(ms: Double): String {
		val seconds = (ms / 1000).toInt()
		return "%d:%02d".format(seconds / 60, seconds % 60)
	}

	private fun toDisplay(item: ProfileViewer.ProfileItem?): DisplayItem? {
		item ?: return null
		val stack = item.toItemStack()
		val name = item.displayName?.let(::lit) ?: stack.hoverName
		return DisplayItem(stack, listOf(name) + item.lore.map(::lit))
	}

	private fun inventoryItems(member: JsonObject): List<ProfileViewer.ProfileItem?> {
		val inv = member.obj("inventory") ?: return listOf()
		fun d(vararg path: String) = ProfileViewer.decodeInventory(inv.path(*path))
		return when (inventoryKind) {
			InventoryKind.INVENTORY -> d("inv_contents").let { it.drop(9) + it.take(9) }
			InventoryKind.ARMOR -> {
				val armor = d("inv_armor").reversed()
				armor + List(if (armor.isEmpty()) 9 else (9 - armor.size % 9) % 9) { null } + d("equipment_contents").reversed()
			}
			InventoryKind.ENDER_CHEST -> d("ender_chest_contents")
			InventoryKind.BACKPACKS -> (inv.obj("backpack_contents") ?: JsonObject(mapOf())).entries
				.sortedBy { it.key.toIntOrNull() ?: Int.MAX_VALUE }
				.flatMap { (_, blob) ->
					val pack = ProfileViewer.decodeInventory(blob)
					pack + List((BACKPACK_SLOTS - pack.size % BACKPACK_SLOTS) % BACKPACK_SLOTS) { null }
				}
			InventoryKind.WARDROBE -> d("wardrobe_contents")
			InventoryKind.ACCESSORIES -> d("bag_contents", "talisman_bag")
			InventoryKind.VAULT -> d("personal_vault_contents")
			InventoryKind.POTION_BAG -> d("bag_contents", "potion_bag")
			InventoryKind.FISHING_BAG -> d("bag_contents", "fishing_bag")
			InventoryKind.QUIVER -> d("bag_contents", "quiver")
			InventoryKind.CANDY_BAG -> d("candy_inventory_contents")
			InventoryKind.SACK_BAG -> d("bag_contents", "sacks_bag")
		}
	}

	private fun petItems(member: JsonObject): List<DisplayItem?> {
		val pets = member.path("pets_data", "pets") as? JsonArray ?: return listOf()
		fun rarityOf(p: JsonObject) = runCatching { Rarity.valueOf(p["tier"].str() ?: "UNKNOWN") }.getOrDefault(Rarity.UNKNOWN)
		return pets.mapNotNull { it as? JsonObject }
			.sortedWith(compareByDescending<JsonObject> { it["active"].bool() == true }
				.thenByDescending { rarityOf(it).ordinal }
				.thenByDescending { it["exp"].num() ?: 0.0 })
			.map { pet ->
				val type = pet["type"].str() ?: "UNKNOWN"
				val rarity = rarityOf(pet)
				val exp = pet["exp"].num() ?: 0.0
				val level = ExpLadders.getExpLadder(type, rarity).getPetLevel(exp)
				val held = pet["heldItem"].str()
				DisplayItem(itemFor(ItemId("$type;${rarity.ordinal}")), buildList {
					add("§7[Lvl ${level.currentLevel}] ${rarityColour(rarity)}${prettify(type)}")
					add("§7Exp: §f${fmt(exp)}")
					if (level.known) {
						if (level.currentLevel < level.maxLevel) add("§7Next level: §e${"%.1f".format(level.percentageToNextLevel * 100)}%")
						else add("§6Maxed!")
					}
					if (held != null) {
						add("§7Held item: §f$held")
						HypixelStaticData.lowestBin[ItemId(held)]?.let { add("§7Held item value: §6${ApexFormatters.compactNumber(it)}") }
					}
					pet["skin"].str()?.let { add("§7Skin: §f$it") }
					add("§7Candy used: §f${pet["candyUsed"].long() ?: 0}")
					add(if (level.known) "§7Level: §f${level.currentLevel}/${level.maxLevel}" else "§7Level: §f?")
					HypixelStaticData.lowestBin[ItemId("$type;${rarity.ordinal}")]?.let { add("§7Lowest BIN (base): §6${ApexFormatters.compactNumber(it)}") }
					if (pet["active"].bool() == true) add("§aActive pet")
				}.map(::lit), highlight = pet["active"].bool() == true)
			}
	}

	private fun rarityColour(rarity: Rarity) = when (rarity) {
		Rarity.COMMON -> "§f"
		Rarity.UNCOMMON -> "§a"
		Rarity.RARE -> "§9"
		Rarity.EPIC -> "§5"
		Rarity.LEGENDARY -> "§6"
		Rarity.MYTHIC -> "§d"
		else -> "§c"
	}

	private fun items(): List<DisplayItem?> {
		cachedItems?.let { return it }
		val p = profile ?: return listOf()
		val list = when (page) {
			Page.INVENTORIES -> inventoryItems(p.member).map(::toDisplay)
			Page.PETS -> petItems(p.member)
			Page.MUSEUM -> museumItems(p)
			Page.COLLECTIONS -> collectionItems(p.member)
			else -> listOf()
		}
		cachedItems = list
		return list
	}

	private fun drawItemGrid(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, gridTop: Int) {
		val list = items()
		val bottom = gridBottom()
		if (list.isEmpty()) {
			val key = if (page == Page.MUSEUM && profile?.profileId in museumLoading) tr("apex.pv.museum-loading", "Loading museum...")
			else tr("apex.pv.nothing", "Nothing here (or the inventory API is disabled).")
			context.centeredText(font, key, left + PANEL_WIDTH / 2, (gridTop + bottom) / 2, -1)
			return
		}
		val blockRows = if (page == Page.INVENTORIES) twoColumnBlockRows(inventoryKind) else null
		val columnWidth = 9 * SLOT
		val gridWidth = if (blockRows == null) columnWidth else 2 * columnWidth + COLUMN_GAP
		val gx = left + (PANEL_WIDTH - gridWidth) / 2
		val rows = gridRows(list.size, blockRows)
		context.enableScissor(gx - 1, gridTop, gx + gridWidth + 1, bottom)
		ProfileViewerStyle.grid(context, font, gx, gridTop - scroll, 9, rows, SLOT)
		if (blockRows != null && list.size > blockRows * 9)
			ProfileViewerStyle.grid(context, font, gx + columnWidth + COLUMN_GAP, gridTop - scroll, 9, rows, SLOT)
		var hovered: DisplayItem? = null
		for ((i, item) in list.withIndex()) {
			item ?: continue
			val (col, r) = gridCell(i, blockRows)
			val x = gx + 1 + SLOT * col + if (col >= 9) COLUMN_GAP else 0
			val y = gridTop + 1 + SLOT * r - scroll
			if (y + 16 < gridTop || y > bottom) continue
			if (item.highlight) context.fill(x, y, x + 16, y + 16, 0x8055FF55.toInt())
			if (!item.stack.isEmpty) {
				context.item(item.stack, x, y)
				context.itemDecorations(font, item.stack, x, y)
			}
			if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16 && mouseY in gridTop until bottom) hovered = item
		}
		context.disableScissor()
		hovered?.let { context.setComponentTooltipForNextFrame(font, it.tooltip, mouseX, mouseY) }
	}
}
