// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.misc

import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.IsSlotProtectedEvent
import dev.ashtonite365.apex.events.ItemTooltipEvent
import dev.ashtonite365.apex.events.ReloadRegistrationEvent
import dev.ashtonite365.apex.events.SlotRenderEvents
import dev.ashtonite365.apex.features.world.NavigableWaypoint
import dev.ashtonite365.apex.features.world.NavigationHelper
import dev.ashtonite365.apex.repo.ReforgeStore.kJson
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.data.ProfileSpecificDataHolder
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.tr
import dev.ashtonite365.apex.util.bareText

object AbiphoneHelper {
	@Config
	object TConfig : ManagedConfig("abiphone", Category.MISC) {
		val favourites by toggle("favourites") { true }
		val onlyFavourites by toggle("only-favourites") { false }
		val requirements by toggle("requirements") { true }
		val markers by toggle("markers") { true }
	}

	@Serializable
	data class Data(val favourites: MutableSet<String> = mutableSetOf())

	@Config
	object DConfig : ProfileSpecificDataHolder<Data>(serializer(), "abiphone-favourites", ::Data)

	@Serializable
	data class Contact(
		val requirement: List<String> = listOf(),
		val island: String? = null,
		val x: Int? = null,
		val y: Int? = null,
		val z: Int? = null,
	)

	var contacts: Map<String, Contact> = mapOf()
		private set

	@Subscribe
	fun onRepo(event: ReloadRegistrationEvent) {
		event.repo.registerReloadListener { repo ->
			contacts = runCatching { repo.file("constants/abiphone.json")?.kJson(serializer<Map<String, Contact>>()) }.getOrNull().orEmpty()
		}
	}

	data class ContactWaypoint(override val name: String, override val position: BlockPos, override val island: SkyBlockIsland) :
		NavigableWaypoint

	enum class Where { PHONE, DIRECTORY, ELSEWHERE }

	fun where(title: String, onSkyBlock: Boolean): Where = when {
		!onSkyBlock -> Where.ELSEWHERE
		title == "Contacts Directory" -> Where.DIRECTORY
		title == "Abiphone Shop" -> Where.ELSEWHERE
		title == "AⒷiphone" || title.startsWith("Abiphone ") -> Where.PHONE
		else -> Where.ELSEWHERE
	}

	private val callPrompts = setOf("Left-click to call!", "Click to call!")

	fun isContactLore(lore: List<String>) = lore.any { it in callPrompts }

	fun isContact(stack: ItemStack): Boolean = isContactLore(stack.view().loreAccordingToNbt.map { it.bareText })

	data class Switches(val favourites: Boolean, val onlyFavourites: Boolean, val requirements: Boolean, val markers: Boolean)

	data class Addition(val index: Int?, val line: Component)

	fun tooltipLines(where: Where, sw: Switches, name: String, contact: Boolean, stars: Set<String>?, entry: Contact?, markedName: String?): List<Addition> {
		val add = mutableListOf<Addition>()
		if (where == Where.PHONE && sw.favourites && contact) {
			if (stars == null) return add
			val starred = name in stars
			if (starred) add += Addition(1, tr("apex.abiphone.favourite", "★ Favourite").copy().withStyle { it.withColor(0xFFAA00) })
			add += Addition(null, if (starred) tr("apex.abiphone.unfavourite.hint", "§eShift-click to remove from the favourites!")
			else tr("apex.abiphone.favourite.hint", "§eShift-click to add to the favourites!"))
		}
		if (where != Where.DIRECTORY || entry == null) return add
		if (sw.requirements && entry.requirement.isNotEmpty())
			add += (listOf(" ", "§e§lRequirements:") + entry.requirement).map { Addition(null, Component.literal(it)) }
		if (sw.markers && entry.x != null) {
			add += Addition(null, Component.literal(" "))
			add += Addition(null, if (markedName == name) tr("apex.abiphone.marker.remove", "§eClick to remove the marker!")
			else tr("apex.abiphone.marker.set", "§eClick to set a marker!"))
			add += Addition(null, tr("apex.abiphone.marker.warp", "§eShift-Click to warp near and set a marker!"))
		}
		return add
	}

	sealed interface Click {
		data object Pass : Click

		data object Swallow : Click

		data class Star(val name: String, val starred: Boolean) : Click
		data object Unmark : Click
		data class Mark(val name: String, val at: BlockPos, val island: String, val warp: Boolean) : Click
	}

	fun decideClick(where: Where, sw: Switches, name: String, contact: Boolean, shift: Boolean, stars: Set<String>?, entry: Contact?, markedName: String?): Click {
		if (where == Where.PHONE && sw.favourites && contact) {
			val s = stars ?: return Click.Pass
			return when {
				shift -> Click.Star(name, name !in s)
				sw.onlyFavourites && name !in s -> Click.Swallow
				else -> Click.Pass
			}
		}
		if (where != Where.DIRECTORY || !sw.markers || entry == null) return Click.Pass
		val island = entry.island
		if (entry.x == null || entry.y == null || entry.z == null || island == null) return Click.Pass
		if (!shift && markedName == name) return Click.Unmark
		return Click.Mark(name, BlockPos(entry.x, entry.y, entry.z), island, shift)
	}

	private const val STAR_TINT = 0x80FFAA00.toInt()
	private const val DIM_TINT = 0xC0202020.toInt()

	fun tint(where: Where, sw: Switches, name: String, contact: Boolean, stars: Set<String>?): Pair<Int?, Int?> {
		if (where != Where.PHONE || !sw.favourites || !contact || stars == null) return null to null
		val starred = name in stars
		return (if (starred) STAR_TINT else null) to (if (sw.onlyFavourites && !starred) DIM_TINT else null)
	}

	private fun here() = where(Client.screenTitle ?: "", SkyBlockSession.inSkyBlock)
	private fun switches() = Switches(TConfig.favourites, TConfig.onlyFavourites, TConfig.requirements, TConfig.markers)
	private fun nameOf(stack: ItemStack) = stack.hoverName.bareText.trim()
	private val stars get() = DConfig.data?.favourites

	@Subscribe
	fun onTooltip(event: ItemTooltipEvent) {
		val where = here()
		if (where == Where.ELSEWHERE) return
		val name = nameOf(event.stack)
		val adds = tooltipLines(where, switches(), name, where == Where.PHONE && isContact(event.stack), stars, contacts[name],
			NavigationHelper.targetWaypoint?.name)
		for (a in adds) if (a.index != null) event.lines.add(a.index, a.line) else event.lines.add(a.line)
	}

	@Subscribe
	fun onClick(event: IsSlotProtectedEvent) {
		if (event.origin != IsSlotProtectedEvent.MoveOrigin.INVENTORY_MOVE) return
		val slot = event.slot ?: return
		if (slot.container is Inventory) return
		val where = here()
		if (where == Where.ELSEWHERE) return
		val stack = slot.item
		val name = nameOf(stack)
		val decision = decideClick(where, switches(), name, where == Where.PHONE && isContact(stack),
			event.actionType == ContainerInput.QUICK_MOVE, stars, contacts[name], NavigationHelper.targetWaypoint?.name)
		when (decision) {
			Click.Pass -> {}
			Click.Swallow -> event.protectSilent()
			is Click.Star -> {
				val s = stars ?: return
				if (decision.starred) s.add(decision.name) else s.remove(decision.name)
				DConfig.markDirty()
				Client.showChat(if (decision.starred) tr("apex.abiphone.added", "Added ${decision.name} to your favourite contacts.")
				else tr("apex.abiphone.removed", "Removed ${decision.name} from your favourite contacts."))
				event.protectSilent()
			}
			Click.Unmark -> NavigationHelper.targetWaypoint = null
			is Click.Mark -> {
				NavigationHelper.targetWaypoint = ContactWaypoint(decision.name, decision.at, SkyBlockIsland.ofMode(decision.island))
				if (decision.warp) NavigationHelper.tryWarpNear()
			}
		}
	}

	private fun slotTint(slot: net.minecraft.world.inventory.Slot): Pair<Int?, Int?> {
		if (slot.container is Inventory) return null to null
		val where = here()
		if (where != Where.PHONE) return null to null
		return tint(where, switches(), nameOf(slot.item), isContact(slot.item), stars)
	}

	@Subscribe
	fun onSlotRender(event: SlotRenderEvents.Before) {
		val colour = slotTint(event.slot).first ?: return
		event.context.fill(event.slot.x, event.slot.y, event.slot.x + 16, event.slot.y + 16, colour)
	}

	@Subscribe
	fun onSlotRenderAfter(event: SlotRenderEvents.After) {
		val colour = slotTint(event.slot).second ?: return
		event.context.fill(event.slot.x, event.slot.y, event.slot.x + 16, event.slot.y + 16, colour)
	}
}
