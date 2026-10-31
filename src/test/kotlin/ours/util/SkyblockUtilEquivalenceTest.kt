// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.ours.util

import kotlinx.serialization.json.Json
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.test.ours.OursGolden
import dev.ashtonite365.apex.test.testutil.ItemResources
import dev.ashtonite365.apex.util.skyblock.AbilityUtils
import dev.ashtonite365.apex.util.skyblock.ItemType
import dev.ashtonite365.apex.util.skyblock.PartyUtil
import dev.ashtonite365.apex.util.skyblock.Rarity
import dev.ashtonite365.apex.util.skyblock.SBItemUtil.getSearchName
import dev.ashtonite365.apex.util.skyblock.SackUtil
import dev.ashtonite365.apex.util.skyblock.SkyBlockItems
import dev.ashtonite365.apex.util.skyblock.TabListAPI

@OptIn(dev.ashtonite365.apex.util.mc.NeedsComponents::class)
class SkyblockUtilEquivalenceTest {
	init {
		dev.ashtonite365.apex.test.ApexTestBootstrap.bootstrapMinecraft()
		dev.ashtonite365.apex.repo.RepoManager.initialize()
	}

	private fun <T> attempt(block: () -> T): String = try {
		block().toString()
	} catch (e: Throwable) {
		"ERR:" + e.javaClass.simpleName
	}

	private val itemNames = listOf(
		"aspect-of-the-void", "backpack-in-menu", "diamond-pickaxe", "gemstone-gauntlet", "hyperion",
		"necron-boots", "rune-in-sack", "titanium-drill", "pets/lion-item", "pets/mithril-golem-not-selected", "pets/rabbit-selected",
	)

	@Test
	fun rarityAndItemTypeMatch() {
		val out = mutableListOf<String>()
		for (r in Rarity.entries) out += "rarity $r names=${r.names} repo=${r.coreRepoRarity} text=${OursGolden.text(r.text)} back=${r.coreRepoRarity?.let { Rarity.fromCoreRepo(it) }}" +
			" json=${Json.encodeToString(Rarity.serializer(), r)}"
		for (s in listOf("COMMON", "LEGENJERRY", "VERY_SPECIAL", "VERY SPECIAL", "common", "", "UNKNOWN", "SHINY"))
			out += "fromString '$s' ${Rarity.fromString(s)} json=${attempt { Json.decodeFromString(Rarity.serializer(), "\"$s\"") }}"
		for (t in -1..14) out += "tier $t ${Rarity.fromTier(t)}"
		out += "byName ${Rarity.byName.toSortedMap()} fromRepo ${Rarity.fromCoreRepo.entries.map { "${it.key}=${it.value}" }}"
		val lores = listOf(
			listOf("a", "§6§lLEGENDARY SWORD"), listOf("§d§l§ka§r §d§lMYTHIC DUNGEON HELMET §d§l§ka"), listOf("RARE", "nothing"),
			listOf("lower case epic"), listOf("x VERY SPECIAL y"), listOf("LEGENJERRY BOW", "EPIC"), listOf(),
		)
		for (l in lores) out += "fromLore $l ${Rarity.fromLore(l.map { Component.literal(it) })}"
		for (s in listOf("§6§lLEGENDARY SWORD", "§d§l§kU§r §d§lMYTHIC DUNGEON CHESTPLATE §d§l§kU", "§kabc§r COMMON", "SINGLE", "", "  RARE   LONG SWORD ", "§K§6x§a EPIC BOW"))
			out += "escape '$s' ${ItemType.fromEscapeCodeLore(s)}"
		for (t in listOf(ItemType.SWORD, ItemType.LONGSWORD, ItemType.ofName("DUNGEON HELMET"), ItemType.NIL, ItemType.PET))
			out += "type $t dung=${t.dungeonVariant} is=${t.isDungeon} non=${t.nonDungeonVariant} eq=${t == ItemType.ofName(t.name)}"
		for (n in itemNames) {
			val item = runCatching { ItemResources.loadItem(n) }.getOrNull()
			if (item == null) { out += "item $n unloadable"; continue }
			out += "item $n type=${attempt { ItemType.fromItemStack(item) }} rarity=${attempt { Rarity.fromItem(item) }} pet=${attempt { Rarity.fromPetItem(item) }}" +
				" search=${attempt { item.getSearchName() }}"
			out += "  abilities ${attempt { AbilityUtils.abilitiesIn(item).map { "${it.name}|${it.hasPowerScroll}|${it.activation.label}|${it.manaCost}|${it.cooldown}|${it.descriptionLines.map(OursGolden::text)}" } }}"
		}
		for (a in listOf(null, "", "  ", " RIGHT CLICK ", "LEFT CLICK")) out += "activation '$a' '${AbilityUtils.AbilityActivation.fromHeader(a).label}'"
		out += "consts ${listOf(AbilityUtils.AbilityActivation.RIGHT_CLICK, AbilityUtils.AbilityActivation.SNEAK_RIGHT_CLICK, AbilityUtils.AbilityActivation.SNEAK, AbilityUtils.AbilityActivation.EMPTY).map { it.label }}"
		val hand = listOf(
			Component.empty().append("Ability: Zap ").append("RIGHT CLICK"),
			Component.empty().append("Zaps things."),
			Component.empty().append("Mana Cost: ").append("1.5k"),
			Component.empty().append("Cooldown: ").append("1m 5s"),
			Component.literal(""),
			Component.empty().append("⦾ ").append("Ability: Second ").append(" SNEAK "),
			Component.empty().append("Cooldown: ").append("garbage").append("extra"),
			Component.empty().append("Ability: Broken").append("one").append("two"),
			Component.literal("not an ability"),
		)
		out += "handAbilities ${attempt { AbilityUtils.abilitiesIn(hand).map { "${it.name}|${it.hasPowerScroll}|${it.activation.label}|${it.manaCost}|${it.cooldown}|${it.descriptionLines.map(OursGolden::text)}" } }}"
		OursGolden.check("util-skyblock-items", out)
	}

	@Test
	fun sacksTabAndPartyMatch() {
		val out = mutableListOf<String>()
		for (n in listOf("sacks/gain-rotten-flesh", "sacks/gain-and-lose-regular", "all-chat"))
			out += "sack $n ${attempt { SackUtil.getUpdatesFromMessage(ItemResources.loadText(n)) }}"
		val hover = { s: String -> Component.literal("x").withStyle { it.withHoverEvent(net.minecraft.network.chat.HoverEvent.ShowText(Component.literal(s))) } }
		val msg = Component.literal("[Sacks] ").append(hover("Added items:\n  +1,234 Enchanted Diamond (Mining Sack)\n  +5 Nothing Real (x)\n  bad line"))
			.append(hover("Removed items:\n  -2k Rotten Flesh (Combat Sack)")).append(hover("Added items:\n  +9 Diamond (again)"))
		out += "sackHand ${attempt { SackUtil.getUpdatesFromMessage(msg) }}"
		for (s in listOf("  +1 Diamond (x)", "  -1.5k Enchanted Diamond (Mining Sack)", " +1 Diamond (x)", "  +1 Diamond"))
			out += "change '$s' ${SackUtil.changePattern.matcher(s).matches()}"
		for (s in listOf("Stored: 1/2", "Stored: 1.2k/60k", "Stored: x/2")) out += "stored '$s' ${SackUtil.storedRegex.matcher(s).matches()}"
		val tab = ItemResources.loadTablist("dungeon_hub")
		for (w in TabListAPI.WidgetName.entries) {
			out += "widget $w label=${w.label} regex=${w.regex} t=${TabListAPI.getWidgetLines(w, true, tab).map { it.string }} f=${TabListAPI.getWidgetLines(w, false, tab).map { it.string }}"
		}
		out += "widgetTitle ${TabListAPI.WidgetName.SKILLS.matchesTitle(Component.literal("Skills:"))} ${TabListAPI.WidgetName.SKILLS.matchesTitle(Component.literal("Skills: Mining 50"))} ${TabListAPI.WidgetName.PET.matchesTitle(Component.literal("Pet:"))} ${TabListAPI.WidgetName.PET.matchesTitle(Component.literal("Pets:"))}"

		val chat = listOf(
			"[MVP+] Alice joined the party.", "Bob joined the party.", "Party Finder > Carol joined the Dungeon group! (Combat Level 30)",
			"Bob has left the party.", "The party was transferred to Alice by [VIP] Carol", "Dave joined the party.",
			"The party was transferred to Dave because Alice left", "Kicked Carol because they were offline.",
			"Eve was removed from your party because they disconnected.", "Frank has been removed from the party.",
			"Gina joined the party.", "Dave has disbanded the party!", "Hank joined the party.", "You left the party.",
			"Ian joined the party.", "You have been kicked from the party by Ian .", "Jo joined the party.",
			"The party was disbanded because all invites expired and the party was empty.", "random chat line",
		)
		PartyUtil.party = null
		for (line in chat) {
			out += "chat '$line' -> " + attempt {
				PartyUtil.Internal.readPartyChat(ProcessChatEvent(Component.literal(line), false))
				PartyUtil.party?.members?.map { "${it.name}:${it.role}:${it.uuid}" }
			}
		}
		PartyUtil.party = PartyUtil.Roster(listOf(PartyUtil.RosterEntry("Me", net.hypixel.modapi.packet.impl.clientbound.ClientboundPartyInfoPacket.PartyRole.LEADER)))
		for (line in listOf("Zed joined the party.", "The party was transferred to Yan because Me left.", "The party was transferred to Zed by Yan."))
			out += "chat2 '$line' -> " + attempt {
				PartyUtil.Internal.readPartyChat(ProcessChatEvent(Component.literal(line), false))
				PartyUtil.party?.members?.map { "${it.name}:${it.role}" }
			}
		PartyUtil.party = null
		val r = PartyUtil.Internal.ChatLines
		out += "regex ${r.WHO} ${r.WHO_ELSE}"
		out += "items ${listOf(SkyBlockItems.COINS, SkyBlockItems.ROTTEN_FLESH, SkyBlockItems.ETHERWARP_CONDUIT, SkyBlockItems.HUNTING_TOOLKIT).map { it.skyblockId }}"
		OursGolden.check("util-skyblock-chat", out)
	}
}
