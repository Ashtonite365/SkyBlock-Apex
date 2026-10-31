// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.ours.repo

import java.nio.file.Files
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import io.github.moulberry.repo.data.NEUIngredient
import io.github.moulberry.repo.data.NEURecipe
import io.github.moulberry.repo.data.Rarity
import me.shedaniel.math.Point
import me.shedaniel.math.Rectangle
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.PackResources
import net.minecraft.server.packs.PackType
import net.minecraft.world.entity.LivingEntity
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.repo.ExpLadders
import dev.ashtonite365.apex.repo.ExpensiveItemCacheApi
import dev.ashtonite365.apex.repo.HypixelStaticData
import dev.ashtonite365.apex.repo.ItemCache
import dev.ashtonite365.apex.repo.ItemCache.asItemStack
import dev.ashtonite365.apex.repo.ItemNameLookup
import dev.ashtonite365.apex.repo.PetData
import dev.ashtonite365.apex.repo.Reforge
import dev.ashtonite365.apex.repo.ReforgeStore
import dev.ashtonite365.apex.repo.RepoItemTypeCache
import dev.ashtonite365.apex.repo.RepoManager
import dev.ashtonite365.apex.repo.RepoModResourcePack
import dev.ashtonite365.apex.repo.SBItemStack
import dev.ashtonite365.apex.repo.SafeRepoConstants
import dev.ashtonite365.apex.repo.itemsSafe
import dev.ashtonite365.apex.repo.recipes.GenericRecipeRenderer
import dev.ashtonite365.apex.repo.recipes.RecipeLayouter
import dev.ashtonite365.apex.repo.recipes.SBCraftingRecipeRenderer
import dev.ashtonite365.apex.repo.recipes.SBEssenceUpgradeRecipeRenderer
import dev.ashtonite365.apex.repo.recipes.SBForgeRecipeRenderer
import dev.ashtonite365.apex.repo.recipes.SBReforgeRecipeRenderer
import dev.ashtonite365.apex.test.ours.OursGolden
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.mc.KeptStack
import dev.ashtonite365.apex.util.skyblock.ItemType

@OptIn(ExpensiveItemCacheApi::class)
class RepoEquivalenceTest {
	init {
		dev.ashtonite365.apex.test.ApexTestBootstrap.bootstrapMinecraft()
		RepoManager.initialize()
	}

	private fun dropItemCache() {
		for (f in ItemCache::class.java.declaredFields) {
			if (!Map::class.java.isAssignableFrom(f.type) || "KeptStack" !in f.genericType.typeName) continue
			f.isAccessible = true
			(f.get(ItemCache) as? MutableMap<*, *>)?.clear()
		}
	}

	@org.junit.jupiter.api.AfterEach
	fun afterEach() = dropItemCache()

	private val items get() = RepoManager.coreRepo.items.itemsSafe.values.sortedBy { it.skyblockItemId }

	private fun <T> attempt(block: () -> T): String = try {
		block().toString()
	} catch (e: Throwable) {
		"ERR:" + e.javaClass.simpleName
	}

	private fun withFixedSkinTimestamp(json: String): String = Regex("\"value\":\"([A-Za-z0-9+/=]+)\"").replace(json) { m ->
		val decoded = runCatching { String(java.util.Base64.getDecoder().decode(m.groupValues[1])) }.getOrNull()
		if (decoded == null || "\"timestamp\":" !in decoded) m.value
		else "\"value\":\"" + java.util.Base64.getEncoder().encodeToString(decoded.replace(Regex("\"timestamp\":\\d+"), "\"timestamp\":0").toByteArray()) + "\""
	}

	private fun stackJson(s: KeptStack): String = attempt {
		OursGolden.json(
			KeptStack.CODEC.encodeStart(
				Client.registries.createSerializationContext(JsonOps.INSTANCE), s
			).getOrThrow()
		)
	}

	private fun sb(s: SBItemStack?): String =
		if (s == null) "-" else "${s.catalogItemId.skyblockId}x${s.getStackSize()}*${s.stars}r${s.reforge?.id}p${s.getPetData()}l${s.extraLore.size}"

	@Test
	fun everyRepoItemConvertsTheSame() {
		val list = items
		assertTrue(list.size > 1000)
		val stacks = OursGolden.Digests("repo-itemcache-items", 97)
		for ((n, it) in list.withIndex()) {
			stacks += it.skyblockItemId to stackJson(it.asItemStack())
			if (n % 400 == 399) dropItemCache()
		}
		stacks.check()
		val misc = mutableListOf<String>()
		misc += "broken-null\t" + attempt { stackJson(ItemCache.brokenItemStack(null)) }
		misc += "broken-hint\t" + attempt { stackJson(ItemCache.brokenItemStack(null, ItemId("NOT_A_REAL_THING"))) }
		misc += "broken-item\t" + attempt { stackJson(ItemCache.brokenItemStack(list[3])) }
		for (n in listOf(0, 1, 2, 64, 1000, 150000, 5_000_000, 20_000_000, 2_000_000_000))
			misc += "coin-$n\t" + attempt { withFixedSkinTimestamp(stackJson(ItemCache.coinItem(n))) }
		misc += "missing\t" + attempt { stackJson((null as io.github.moulberry.repo.data.NEUItem?).asItemStack(ItemId("NOPE"))) }
		misc += "repl\t" + attempt { stackJson(list.first { it.lore.any { l -> "{" in l } }.asItemStack(loreReplacements = mapOf("LVL" to "42", "COUNT" to "7"))) }
		for (it in list.take(40)) misc += "glint-${it.skyblockItemId}\t" + attempt {
			ItemCache.legacyWantsGlint(net.minecraft.nbt.TagParser.parseCompoundFully(it.nbttag))
		}
		misc += "hasCache\t" + ItemCache.hasCacheFor(ItemId(list[5].skyblockItemId))
		misc += "headNoTex\t" + ItemCache.legacyHeadWithoutTexture("minecraft:skull", "{SkullOwner:{Id:\"x\"}}") +
			ItemCache.legacyHeadWithoutTexture("minecraft:skull", null) + ItemCache.legacyHeadWithoutTexture("minecraft:stone", "{}") +
			ItemCache.legacyHeadWithoutTexture(null, "{SkullOwner:{Properties:{textures:[{Value:\"abc\"}]}}}")
		misc += "version\t" + ItemCache.currentSaveVersion
		OursGolden.check("repo-itemcache-misc", misc)
	}

	@Test
	fun legacyLoreParsesTheSame() {
		val lines = items.flatMap { it.lore + it.displayName }.distinct().sorted()
		OursGolden.checkDigests("repo-un189lore", lines.map { it to OursGolden.text(ItemCache.un189Lore(it)) })
		val odd = listOf("", "§", "§z", "a§", "§l§6x§r§ky", "§§a", "plain", "§aA§bB§lC§oD§nE§mF§kG§rH", "§AUPPER§Lbold")
		OursGolden.check("repo-un189lore-odd", odd.map { it + "\t" + OursGolden.text(ItemCache.un189Lore(it)) })
	}

	@Test
	fun statLinesParseTheSame() {
		val out = mutableListOf<String>()
		for ((n, item) in items.withIndex()) {
			if (n % 400 == 399) dropItemCache()
			val stack = item.asItemStack()
			val stats = attempt {
				SBItemStack.parseStatBlock(stack).joinToString(";") {
					"${it.statName}|${OursGolden.text(it.value)}|${it.valueNum}|${it.rest.size}|${OursGolden.text(it.rebuildComponent())}|${OursGolden.text(it.rebuildComponent(7))}|${it.statFormatting}"
				}
			}
			if (stats.isNotEmpty()) out += item.skyblockItemId + "\t" + stats
		}
		OursGolden.checkDigests("repo-statblocks", out.map { it.substringBefore('\t') to it })
		val ids = ReforgeStore.allReforges.flatMap { it.statUniverse }.toSortedSet() +
			listOf("walk_speed", "a", "", "true_defense", "x_y_z_")
		OursGolden.check("repo-statnames", ids.map { "$it\t${SBItemStack.statIdToName(it)}" })
		val bonus = listOf(
			SBItemStack.Companion.StatLine("Strength", null).addStat(12.5, SBItemStack.Companion.BuffKind.REFORGE),
			SBItemStack.Companion.StatLine("Crit Damage", Component.literal("+5%")).addStat(3.0, SBItemStack.Companion.BuffKind.STAR_BUFF),
			SBItemStack.Companion.StatLine("Mining Speed", Component.literal("+100")).addStat(-1.0, SBItemStack.Companion.BuffKind.CATA_STAR_BUFF),
			SBItemStack.Companion.StatLine("Unknown Stat", Component.literal("7")),
		)
		OursGolden.check("repo-statline-buffs", bonus.map {
			"${it.statName}|${it.valueNum}|${OursGolden.text(it.value)}|${it.rest.map(OursGolden::text)}|${OursGolden.text(it.rebuildComponent())}|${OursGolden.text(it.rebuildComponent(4))}"
		} + SBItemStack.Companion.BuffKind.entries.map { "${it.name}|${it.color}|${it.prefix}|${it.postFix}|${it.isHidden}" })
	}

	@Test
	fun modifiedStacksBuildTheSame() {
		val list = items
		val out = OursGolden.Digests("repo-sbitemstack", 53)
		for ((i, item) in list.withIndex()) {
			if (i % 11 != 0) continue
			if (i % 440 == 0) dropItemCache()
			val id = ItemId(item.skyblockItemId)
			for (stars in listOf(0, 1, 5, 7, 10, 15, 16)) {
				val s = SBItemStack(id).copy(stars = stars)
				out += "${item.skyblockItemId}*$stars" to stackJson(s.asImmutableItemStack())
			}
			for (n in listOf(0, 2, 64, 200)) out += "${item.skyblockItemId}x$n" to stackJson(SBItemStack(id, n).asImmutableItemStack())
			val lore = SBItemStack(id).copy(extraLore = listOf(Component.literal("extra"), Component.literal("lines")))
			out += "${item.skyblockItemId}+lore" to stackJson(lore.asImmutableItemStack())
			out += "${item.skyblockItemId}:est" to lore.estimateLore().joinToString("\n") { OursGolden.text(it) }
			out += "${item.skyblockItemId}:bp" to "${lore.breakingPower}|${attempt { lore.rarity }}|${attempt { lore.itemType }}|${lore.hasModifiers}"
		}

		for (r in ReforgeStore.allReforges.sortedBy { it.prefixName }) {
			val targets = eligible(r).take(3)
			for (t in targets) out += "${t.catalogItemId.skyblockId}@${r.reforgeId.id}" to
				attempt { stackJson(t.copy(reforge = r.reforgeId).asImmutableItemStack()) }
		}
		out += "coins" to attempt { withFixedSkinTimestamp(stackJson(SBItemStack(ItemId.COINS, 12345).asImmutableItemStack())) }
		out += "empty" to attempt { stackJson(SBItemStack.EMPTY.asImmutableItemStack()) }
		out.check()
	}

	@Test
	fun petsBuildTheSame() {
		val out = OursGolden.Digests("repo-pets", 41)
		val pets = SafeRepoConstants.petNumbers().toSortedMap()
		for ((pet, byRarity) in pets) {
			for (rarity in Rarity.entries) {
				if (rarity !in byRarity.keys) continue
				val ladder = ExpLadders.getExpLadder(pet, rarity)
				out += "$pet;$rarity:ladder" to ladder.individualLevelCost.toString() + ladder.cumulativeLevelCost
				for (exp in listOf(0.0, 1.0, 1000.0, 123456.0, 2.5e7, 1e9)) out += "$pet;$rarity:exp$exp" to ladder.getPetLevel(exp).let {
					"$it|${it.levelText}|${it.percentageToNextLevel}|${it.percentageToMaxLevel}"
				}
				for (level in listOf(0, 1, 2, 50, 99, 100, 101, 200)) out += "$pet;$rarity:lvl$level" to ladder.getPetExpForLevel(level).toString()
				for (level in listOf(1, 50, 100)) {
					val data = PetData.forLevel(pet, rarity, level)
					val s = SBItemStack(ItemId("$pet;${rarity.ordinal}"), data)
					out += "$pet;$rarity@$level" to "$data|${data.levelData}|" + stackJson(s.asImmutableItemStack())
				}
			}
			out += "$pet:max" to ExpLadders.maxLevelOverride(pet).toString()
		}
		for (id in listOf("BEE;3", "BEE;9", "BEE;x", "BEE", "NOT_A_PET;2", "ENDER_DRAGON;4", "a;b;c"))
			out += "stub:$id" to RepoManager.getPotentialStubPetData(ItemId(id)).toString()
		out.check()
	}

	private fun eligible(r: Reforge): List<SBItemStack> = r.eligibleItems.flatMap(::stacksFor)

	private fun stacksFor(rule: Reforge.Eligibility): List<SBItemStack> = when (rule) {
		is Reforge.Eligibility.OfVanillaItem -> emptyList()
		is Reforge.Eligibility.OfSkyblockId -> listOf(SBItemStack(rule.id))
		is Reforge.Eligibility.OfKind -> {
			val kinds = ReforgeStore.resolveItemType(rule.kind).flatMapTo(LinkedHashSet()) { listOf(it, it.dungeonVariant) }
			val items = kinds.flatMapTo(LinkedHashSet()) { RepoItemTypeCache.byItemType[it].orEmpty() }
			items.map { SBItemStack(ItemId(it.skyblockItemId)) }
		}
	}

	@Test
	fun reforgesIndexTheSame() {
		val out = mutableListOf<String>()
		for (r in ReforgeStore.allReforges) {
			out += "reforge\t" + attempt { Apex.json.encodeToString(Reforge.serializer(), r) } +
				"\t${r.reforgeId.id}\t${r.statUniverse}\t${r.eligibleItems}\t${dev.ashtonite365.apex.util.skyblock.Rarity.entries.map { x -> r.costByTier?.get(x) }}" +
				"\t${dev.ashtonite365.apex.util.skyblock.Rarity.entries.map { x -> r.statsByTier?.get(x) }}\t${dev.ashtonite365.apex.util.skyblock.Rarity.entries.map { x -> r.abilityByTier?.get(x) }}"
		}
		fun names(l: List<Reforge>?) = l?.map { it.prefixName }.toString()
		ReforgeStore.byType.entries.sortedBy { it.key.name }.forEach { out += "type\t${it.key}\t${names(it.value)}" }
		ReforgeStore.byVanilla.entries.sortedBy { it.key.toString() }.forEach { out += "vanilla\t${it.key}\t${names(it.value)}" }
		ReforgeStore.byInternalName.entries.sortedBy { it.key.skyblockId }.forEach { out += "id\t${it.key.skyblockId}\t${names(it.value)}" }
		ReforgeStore.byReforgeStone.entries.sortedBy { it.key.skyblockId }.forEach { out += "stone\t${it.key.skyblockId}\t${it.value.prefixName}" }
		ReforgeStore.modifierLut.entries.sortedBy { it.key.id }.forEach { out += "lut\t${it.key.id}\t${it.value.prefixName}" }
		val types = (RepoItemTypeCache.byItemType.keys.filterNotNull() + ItemType.PET + ItemType.ofName("NOT REAL")).distinct().sortedBy { it.name }
		for (t in types) {
			out += "resolve\t$t\t${ReforgeStore.resolveItemType(t)}"
			out += "eligible\t$t\t${names(ReforgeStore.findEligibleForItem(t))}"
		}
		for (i in items.filterIndexed { i, _ -> i % 13 == 0 })
			out += "eligibleId\t${i.skyblockItemId}\t${names(ReforgeStore.findEligibleForInternalName(ItemId(i.skyblockItemId)))}"
		out += "extra\t" + ReforgeStore.provideExtraRecipes().count()
		OursGolden.check("repo-reforges", out)
	}

	@Test
	fun repoIndexesMatch() {
		val out = mutableListOf<String>()
		RepoItemTypeCache.byItemType.entries.sortedBy { it.key?.name ?: "" }
			.forEach { out += "type\t${it.key}\t${it.value.map { i -> i.skyblockItemId }}" }
		val rc = RepoManager.recipeCache
		fun desc(r: NEURecipe) = r.javaClass.simpleName + r.allInputs.map(NEUIngredient::toString).sorted() + "->" + r.allOutputs.map(NEUIngredient::toString).sorted()
		rc.recipes.entries.sortedBy { it.key.skyblockId }.forEach { out += "recipes\t${it.key.skyblockId}\t${it.value.map(::desc).sorted()}" }
		rc.usages.entries.sortedBy { it.key.skyblockId }.forEach { out += "usages\t${it.key.skyblockId}\t${it.value.map(::desc).sorted()}" }
		for (r in RepoManager.essenceRecipeProvider.recipes) out += "essence\t$r\t${r.essenceIngredient}\t${r.allInputs}\t${r.allOutputs}"
		val mining = RepoManager.miningData
		mining.customMiningAreas.entries.sortedBy { it.key.toString() }.forEach { out += "area\t${it.key}\t${it.value}" }
		for (b in mining.customMiningBlocks) out += "block\t$b\t${b.dropItem?.let(::sb)}\t${b.blocks189.map { "${it.block}|${it.isActiveIn(dev.ashtonite365.apex.util.SkyBlockIsland.ofMode("mining_3"))}" }}"
		mining.toolsByBreakingPower.forEach { (k, v) -> out += "tool\t$k\t${sb(v)}" }
		for (bp in listOf(0, 1, 4, 5, 7, 9, 10, 20)) out += "canBreak\t$bp\t${mining.getToolsThatCanBreak(bp).map(::sb)}"
		RepoManager.overlayData.overlays.entries.sortedBy { it.key.skyblockId }.forEach { (k, v) ->
			out += "overlay\t${k.skyblockId}\t${v.sortedBy { it.version }.map { "${it.version}:${it.path.fileName}" }}"
			for (ver in listOf(0, 3000, 4000, 4500, 9999)) out += "overlayFor\t${k.skyblockId}\t$ver\t${RepoManager.overlayData.getMostModernReadableOverlay(k, ver)?.let { "${it.version}:${it.path.fileName}" }}"
		}
		RepoManager.enchantedBookCache.byName.entries.sortedBy { it.key }.forEach { out += "book\t${it.key}\t${it.value.skyblockId}" }
		RepoManager.enchantData.maxLevels.entries.sortedBy { it.key }.forEach { out += "maxlvl\t${it.key}\t${it.value}" }
		out += "enchants\t${RepoManager.enchantData.allEnchants?.availableEnchants?.toSortedMap()}"
		ItemNameLookup.nameMap.entries.forEach { out += "chunk\t${it.key}\t${it.value.map { i -> i.skyblockId }.sorted()}" }
		for (i in items.filterIndexed { i, _ -> i % 5 == 0 }) {
			out += "names\t${i.skyblockItemId}\t${ItemNameLookup.getAllNamesForItem(i).sorted()}"
			for (n in ItemNameLookup.getAllNamesForItem(i)) out += "guess\t$n\t${ItemNameLookup.guessItemByName(n, false)}\t${ItemNameLookup.guessItemByName(n + "s", true)}"
		}
		out += "recipesFor\t" + RepoManager.getRecipesFor(ItemId("ENCHANTED_DIAMOND")).size + "\t" + RepoManager.getUsagesFor(ItemId("DIAMOND")).size
		out += "allIds\t" + RepoManager.allItemIds().size + "\t" + RepoManager.getAllRecipes().count()
		OursGolden.checkDigests("repo-indexes", out.mapIndexed { i, s -> "$i:${s.substringBefore('\t')}" to s })
		OursGolden.check("repo-indexes-sample", out.filterIndexed { i, _ -> i % 61 == 0 })
	}

	@Test
	fun bazaarAndPackDataMatch() {
		val out = mutableListOf<String>()
		val json = """{"success":true,"lastUpdated":1,"products":{"ENCHANTED_DIAMOND":{"product_id":"ENCHANTED_DIAMOND","quick_status":{"productId":"ENCHANTED_DIAMOND","sellPrice":1.5,"sellVolume":10,"sellMovingWeek":20,"sellOrders":3,"buyPrice":2.5,"buyVolume":11,"buyMovingWeek":21,"buyOrders":4},"sell_summary":[{"amount":5,"pricePerUnit":1.5,"orders":2}],"buy_summary":[]}}}"""
		out += attempt {
			val clazz = Class.forName("dev.ashtonite365.apex.repo.HypixelStaticData\$BazaarResponse")
			val ser = kotlinx.serialization.serializer(clazz)
			val v = Apex.json.decodeFromString(ser, json)
			v.toString() + "\n" + Apex.json.encodeToString(ser, v)
		}
		out += "price\t" + HypixelStaticData.getPriceOfItem(ItemId("NOTHING"))
		val dir = Files.createTempDirectory("ours-core-pack")
		try {
			Files.createDirectories(dir.resolve("textures/item"))
			Files.writeString(dir.resolve("textures/item/a.png"), "a")
			Files.writeString(dir.resolve("textures/item/b.json"), "{}")
			Files.writeString(dir.resolve("pack.png"), "p")
			val pack = RepoModResourcePack(dir)
			out += "ns\t${pack.getNamespaces(PackType.CLIENT_RESOURCES)}\t${pack.getNamespaces(PackType.SERVER_DATA)}"
			out += "res\t" + listOf(
				Identifier.fromNamespaceAndPath("neurepo", "textures/item/a.png"),
				Identifier.fromNamespaceAndPath("neurepo", "textures/item/zz.png"),
				Identifier.fromNamespaceAndPath("minecraft", "textures/item/a.png"),
			).map { id -> pack.getResource(PackType.CLIENT_RESOURCES, id)?.get()?.use { s -> String(s.readAllBytes()) } } +
				"\t" + (pack.getResource(PackType.SERVER_DATA, Identifier.fromNamespaceAndPath("neurepo", "textures/item/a.png")) == null)
			out += "root\t" + pack.getRootResource("pack.png")?.get()?.use { s -> String(s.readAllBytes()) } + "\t" + pack.getRootResource("nope")
			for (ns in listOf("neurepo", "minecraft")) for (prefix in listOf("textures", "missing")) {
				val got = mutableListOf<String>()
				pack.listResources(PackType.CLIENT_RESOURCES, ns, prefix, PackResources.ResourceOutput { id, _ -> got += id.toString() })
				out += "list\t$ns\t$prefix\t${got.sorted()}"
			}
			out += "meta\t" + attempt { pack.getMetadataSection(net.minecraft.server.packs.metadata.pack.PackMetadataSection.CLIENT_TYPE) }
			out += "loc\t" + pack.location().id() + "\t" + pack.location().title().string + "\t" + pack.location().source()
			out += "overlay\t" + (pack.createOverlay("sub") as RepoModResourcePack).basePath.fileName
		} finally {
			dir.toFile().deleteRecursively()
		}
		OursGolden.check("repo-misc", out)
	}

	private class Recorder : RecipeLayouter {
		val log = mutableListOf<String>()
		override fun cyclingSlotAt(x: Int, y: Int, content: List<SBItemStack>, slotRole: RecipeLayouter.SlotRole): RecipeLayouter.RotatingCell {
			val n = log.size
			log += "slot $x,$y $slotRole ${content.map { describe(it) }}"
			return object : RecipeLayouter.RotatingCell {
				var cur = content.firstOrNull() ?: SBItemStack.EMPTY
				val updates = mutableListOf<() -> Unit>()
				override fun onRotate(action: () -> Unit) { updates += action; log += "onUpdate#$n" }
				override fun showing(): SBItemStack = cur
				override fun setTo(newValue: SBItemStack) { cur = newValue; log += "update#$n ${describe(newValue)}" }
			}
		}

		override fun hoverArea(rectangle: Rectangle, label: List<Component>) {
			log += "tooltip $rectangle ${label.map(OursGolden::text)}"
		}

		override fun placeLabel(x: Int, y: Int, text: Component): RecipeLayouter.Settable<Component> {
			val n = log.size
			log += "label $x,$y ${OursGolden.text(text)}"
			return object : RecipeLayouter.Settable<Component> {
				override fun setTo(newValue: Component) { log += "relabel#$n ${OursGolden.text(newValue)}" }
			}
		}

		override fun arrowAt(x: Int, y: Int): Rectangle {
			log += "arrow $x,$y"
			return Rectangle(x, y, 24, 17)
		}

		override fun embedAt(box: Rectangle, component: io.github.notenoughupdates.moulconfig.gui.GuiComponent) {
			log += "moulconfig ${box.x},${box.y},${box.width},${box.height} ${component.javaClass.simpleName}"
		}

		override fun flameAt(point: Point, animationTicks: Int) {
			log += "fire $point $animationTicks"
		}

		override fun entityIn(rectangle: Rectangle, entity: LivingEntity) {
			log += "entity $rectangle ${entity.type}"
		}

		fun describe(s: SBItemStack) = "${s.catalogItemId.skyblockId}x${s.getStackSize()}*${s.stars}r${s.reforge?.id}p${s.getPetData()}"
	}

	private fun <T : Any> renderAll(name: String, renderer: GenericRecipeRenderer<T>, filter: (T) -> Boolean = { true }) {
		val out = mutableListOf<Pair<String, String>>()
		out += "meta" to "${renderer.identifier}|${OursGolden.text(renderer.title)}|${renderer.displayWidth}x${renderer.displayHeight}|${renderer.typ.simpleName}|" +
			attempt { stackJson(KeptStack.ofStack(renderer.icon)) }
		for ((i, recipe) in renderer.findAllRecipes(RepoManager.coreRepo).filter(filter).withIndex()) {
			val rec = Recorder()
			val res = attempt { renderer.render(recipe, Rectangle(3, 5, renderer.displayWidth, renderer.displayHeight), rec, null) }
			out += "$i" to rec.log.joinToString("\n") + "\n" + res +
				"\nin=" + attempt { renderer.getInputs(recipe).map(rec::describe) } + "\nout=" + attempt { renderer.getOutputs(recipe).map(rec::describe) }
		}
		OursGolden.checkDigests("repo-render-$name", out)
		OursGolden.check("repo-render-$name-sample", out.filterIndexed { i, _ -> i % 29 == 0 }.map { "${it.first}\t${it.second}" })
	}

	@Test
	fun craftingLayoutsMatch() = renderAll("crafting", SBCraftingRecipeRenderer)

	@Test
	fun forgeLayoutsMatch() = renderAll("forge", SBForgeRecipeRenderer)

	@Test
	fun essenceLayoutsMatch() = renderAll("essence", SBEssenceUpgradeRecipeRenderer)

	@Test
	fun reforgeLayoutsMatch() {
		renderAll("reforge", SBReforgeRecipeRenderer)

		val out = mutableListOf<String>()
		for (r in ReforgeStore.allReforges.sortedBy { it.prefixName }.take(30)) {
			val main = eligible(r).firstOrNull() ?: continue
			val rec = Recorder()
			out += r.prefixName + "\t" + attempt { SBReforgeRecipeRenderer.render(r, Rectangle(0, 0, 150, 66), rec, main) } + "\t" + rec.log.joinToString(" | ")
		}
		OursGolden.check("repo-render-reforge-main", out)
	}
}
