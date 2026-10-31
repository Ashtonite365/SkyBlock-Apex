package dev.ashtonite365.apex.features.inventory

import com.mojang.blaze3d.platform.InputConstants
import kotlin.jvm.optionals.getOrNull
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ItemTooltipEvent
import dev.ashtonite365.apex.features.menus.enchant.EnchantConflicts
import dev.ashtonite365.apex.repo.RepoManager
import dev.ashtonite365.apex.util.IntUtil.toRomanNumeral
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.darkGrey
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.customDataTag
import dev.ashtonite365.apex.util.grey
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.stripSectionCodes
import dev.ashtonite365.apex.util.skyblock.ItemType
import dev.ashtonite365.apex.util.bareText

object MissingEnchantments {
	@Config
	object TConfig : ManagedConfig("missing-enchantments", Category.INVENTORY) {
		val active by toggle("enabled") { true }
		val enableKeybinding by keyBinding("show-missing-enchantments") { InputConstants.KEY_LSHIFT }
		val markUpgrades by toggle("show-upgradable-enchantments") { false }
		val listConflicts by toggle("show-conflicting-enchantments") { false }
	}

	private const val WRAP_WIDTH = 200
	private val rarityLine = Regex("^(COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE|SPECIAL|VERY SPECIAL).*")

	fun poolFor(itemType: ItemType): String {
		val pools = RepoManager.enchantData.allEnchants?.availableEnchants.orEmpty()
		return EnchantPoolLogic.poolKey(itemType.toString(), pools.keys)
			?: itemType.nonDungeonVariant.toString().replace(" ", "")
	}

	fun displayName(enchId: String, level: Int? = null): String? {
		val lore = RepoManager.getCoreItem(ItemId("${enchId.uppercase()};${level ?: 1}"))?.lore
		return EnchantPoolLogic.enchantName(enchId, level, lore)
	}

	fun withUpgrades(line: Component, upgrades: Map<String, Int>): Component {
		if (upgrades.isEmpty()) return line
		val style = line.style
		val rebuilt = Component.empty()
		val parts = line.bareText.split(", ")
		for ((n, part) in parts.withIndex()) {
			rebuilt.append(Component.literal(part).withStyle(style))
			val max = upgrades.entries.firstOrNull { it.key in part }?.value
			if (max != null) rebuilt.append(Component.literal(" → ${max.toRomanNumeral()}").darkGrey())
			if (n == parts.lastIndex) continue
			val separator: MutableComponent = Component.literal(", ")
			rebuilt.append(if (max != null) separator.darkGrey() else separator.withStyle(style))
		}
		return rebuilt
	}

	fun insertAt(lineTexts: List<String>): Int =
		lineTexts.indexOfLast { rarityLine.matches(it) }.takeIf { it >= 0 } ?: lineTexts.size

	private fun block(title: String, entries: List<String>): List<Component> {
		if (entries.isEmpty()) return emptyList()
		val width: (String) -> Int = { Client.font.width(Component.literal(it)) }
		return listOf(Component.literal(title).grey()) +
			EnchantPoolLogic.wrapJoined(entries, WRAP_WIDTH, width).map { Component.literal(it.stripSectionCodes()).darkGrey() }
	}

	@Subscribe
	fun addEnchantHelp(event: ItemTooltipEvent) {
		if (!TConfig.active) return
		val gate = TConfig.enableKeybinding
		if (gate.isBound && !gate.isPressed()) return
		val item = event.stack.view()
		val type = ItemType.fromItemStack(item)
		val onItem = item.customDataTag.getCompound("enchantments").getOrNull()
		val levels = onItem?.keySet()?.associateWith { onItem.getInt(it) }.orEmpty()
		val pool = RepoManager.enchantData.allEnchants?.availableEnchants[type?.let(::poolFor) ?: return] ?: return

		if (TConfig.markUpgrades) {
			val maxLevels = RepoManager.enchantData.maxLevels
			for (i in event.lines.indices) {
				val text = event.lines[i].bareText
				val upgrades = LinkedHashMap<String, Int>()
				for ((id, level) in levels) {
					val name = displayName(id, level.getOrNull())?.stripSectionCodes() ?: continue
					val max = maxLevels[id] ?: continue
					if (name in text && level.isPresent && level.get() < max) upgrades[name] = max
				}
				if (upgrades.isNotEmpty()) event.lines[i] = withUpgrades(event.lines[i], upgrades)
			}
		}

		val split = EnchantConflicts.splitMissing(pool, onItem?.keySet().orEmpty(), EnchantConflicts.livePools())
		val conflictTexts = if (TConfig.listConflicts) split.conflicts.mapNotNull { (id, with) ->
			displayName(id)?.let { EnchantPoolLogic.conflictText(it, displayName(with) ?: EnchantPoolLogic.titleCase(with)) }
		} else emptyList()
		if (split.missing.isEmpty() && conflictTexts.isEmpty()) return

		val body = block("Missing Enchantments:", split.missing.mapNotNull { displayName(it) }) +
			block("Conflicting Enchantments (not missing):", conflictTexts)
		if (body.isEmpty()) return
		val spacer = { Component.literal("") }
		event.lines.addAll(insertAt(event.lines.map { it.bareText }), listOf(spacer()) + body + spacer())
	}
}
