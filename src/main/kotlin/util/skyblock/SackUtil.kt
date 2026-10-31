// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.util.skyblock

import java.util.regex.Pattern
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ChestInventoryUpdateEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.gui.config.storage.ConfigFixEvent
import dev.ashtonite365.apex.gui.config.storage.ConfigStorageClass
import dev.ashtonite365.apex.repo.ItemNameLookup
import dev.ashtonite365.apex.util.SHORT_NUMBER_FORMAT
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ProfileSpecificDataHolder
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.displayNameAccordingToNbt
import dev.ashtonite365.apex.util.mc.iterableView
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.parseShortNumber
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.bareText

object SackUtil {

	@Serializable
	data class SackContents(
		@SerialName("contents") val amounts: MutableMap<ItemId, Long> = LinkedHashMap(),
		@SerialName("capacity") val limits: MutableMap<ItemId, Long> = LinkedHashMap(),
	)

	@Config
	object SackStore : ProfileSpecificDataHolder<SackContents>(serializer(), PROFILE_KEY, ::SackContents)

	private const val PROFILE_KEY = "sacks"

	@Subscribe
	fun onConfigFix(event: ConfigFixEvent) =
		event.on(toVersion = 996, storageClass = ConfigStorageClass.PROFILE) { move("Sacks", PROFILE_KEY) }

	val items: MutableMap<ItemId, Long> get() = SackStore.data?.amounts ?: mutableMapOf()
	val capacity: MutableMap<ItemId, Long> get() = SackStore.data?.limits ?: mutableMapOf()

	val storedRegex: Pattern = Pattern.compile("^Stored: (?<stored>$SHORT_NUMBER_FORMAT)/(?<max>$SHORT_NUMBER_FORMAT)$")

	val changePattern: Pattern = Pattern.compile("  (?<amount>[+\\-]$SHORT_NUMBER_FORMAT) (?<itemName>[^(]+) \\(.*\\)")

	private fun isSackMenu(screen: ContainerScreen): Boolean {
		if (!screen.title.bareText.endsWith(" Sack")) return false
		val chest = screen.menu?.container ?: return false
		if (chest.containerSize < 18) return false
		val back = chest.getItem(chest.containerSize - 5).view()
		return back.displayNameAccordingToNbt.bareText == "Go Back" &&
			back.loreAccordingToNbt.map { line -> line.bareText } == listOf("To Sack of Sacks")
	}

	private fun readStored(stack: ItemStack): Long? {
		val view = stack.view()
		for (line in view.loreAccordingToNbt) {
			val match = storedRegex.matcher(line.bareText)
			if (!match.matches()) continue
			view.repoItemId?.let { id -> capacity[id] = parseShortNumber(match.group("max")).toLong() }
			return parseShortNumber(match.group("stored")).toLong()
		}
		return null
	}

	@Subscribe
	fun storeDataFromInventory(event: ChestInventoryUpdateEvent): Unit {
		val screen = (event.inventory as? ContainerScreen)?.takeIf(::isSackMenu) ?: return
		for (stack in screen.menu.container.iterableView) {
			val amount = readStored(stack) ?: continue
			items[stack.view().repoItemId ?: continue] = amount
		}
		SackStore.markDirty()
	}

	@Subscribe
	fun updateFromChat(event: ProcessChatEvent): Unit {
		if (event.bareText.startsWith("[Sacks]")) getUpdatesFromMessage(event.text)
	}

	data class SackUpdate(val itemId: ItemId?, val itemName: String, val changeAmount: Long)

	fun getUpdatesFromMessage(text: Component): List<SackUpdate> = HoverCollector().apply { text.siblings.forEach(::visit) }.found

	private class HoverCollector {
		val found = ArrayList<SackUpdate>()
		private val sectionsDone = HashSet<String>()

		fun visit(part: Component) {
			part.siblings.forEach(::visit)
			val hover = (part.style.hoverEvent as? HoverEvent.ShowText)?.value?.bareText ?: return
			for (section in SECTIONS) {
				if (hover.startsWith(section) && sectionsDone.add(section)) readLines(hover)
			}
		}

		private fun readLines(hover: String) {
			for (line in hover.split('\n')) {
				val match = changePattern.matcher(line)
				if (!match.matches()) continue
				val name = match.group("itemName")
				found += SackUpdate(ItemNameLookup.guessItemByName(name, false), name, parseShortNumber(match.group("amount")).toLong())
			}
		}

		companion object Sections {
			val SECTIONS = listOf("Added items:\n", "Removed items:\n")
		}
	}
}
