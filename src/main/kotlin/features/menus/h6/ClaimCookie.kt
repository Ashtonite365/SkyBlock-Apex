// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.h6

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.support.ApexReadWarn
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.SlotText

object ClaimCookieParser {
	val TITLE = Regex("^Claim Booster Cookie$")
	private val CLAIM = Regex("^Click to claim!?$", RegexOption.IGNORE_CASE)

	fun parse(title: String, slots: List<SlotText>): HubModel? {
		if (!TITLE.matches(MenuText.clean(title))) return null
		val claimable = slots.filter { !HubText.isEmpty(it) && HubText.clean(it.lore).any { l -> CLAIM.matches(l) } }
		val item = claimable.singleOrNull() ?: return null
		val info = HubText.infoLines(item.lore).filterNot { HubText.RARITY_FOOTER.matches(it) }

		val lines = HubText.clean(item.lore)
		val dur = lines.indexOfFirst { it.startsWith("Consume to gain", true) }.takeIf { it >= 0 }
			?.let { lines.getOrNull(it + 1)?.removeSuffix(":")?.trim() }?.takeIf { it.isNotEmpty() }
		val chips = listOfNotNull(dur?.let { "Buff" to it })
		val tile = HubTile(item.slot, item.cleanName, info.take(4), tooltip = info)
		return HubModel(MenuText.clean(title), chips, listOf(HubSection("Claim", listOf(tile))), HubText.navOf(slots))
	}
}

object ClaimCookieMenu : CustomMenu {
	override val id = "claim-booster-cookie"
	override val displayName = "Claim Booster Cookie"
	override val title = ClaimCookieParser.TITLE
	override val settings get() = BoosterCookieSettings
	override val defaultSize = 300 to 170

	@Subscribe
	fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }

	override fun create(screen: AbstractContainerScreen<*>): MenuView = ClaimCookieView(screen)
}

class ClaimCookieView(screen: AbstractContainerScreen<*>) : HubView(screen, { BoosterCookieSettings }) {
	private val warn by lazy { ApexReadWarn(ClaimCookieMenu.id) }
	override fun parse(title: String, slots: List<SlotText>) = ClaimCookieParser.parse(title, slots)
		.also { warn.parsed(it != null, slots.count { s -> s.cleanName.isNotEmpty() }, System.currentTimeMillis()) }

	override fun render(ctx: net.minecraft.client.gui.GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
		super.render(ctx, mouseX, mouseY, delta)
		warn.check(screen.menu.containerId, MenuText.clean(screen.title.string), System.currentTimeMillis())
			?.let { dev.ashtonite365.apex.Apex.logger.warn(it) }
	}
}
