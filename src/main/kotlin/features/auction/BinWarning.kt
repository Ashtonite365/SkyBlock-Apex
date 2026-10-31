// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.auction

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HandledScreenClickEvent
import dev.ashtonite365.apex.events.HandledScreenKeyPressedEvent
import dev.ashtonite365.apex.events.IsSlotProtectedEvent
import dev.ashtonite365.apex.events.ScreenChangeEvent
import dev.ashtonite365.apex.events.ScreenRenderPostEvent
import dev.ashtonite365.apex.keybindings.GenericInputAction
import dev.ashtonite365.apex.repo.HypixelStaticData
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.tr
import dev.ashtonite365.apex.util.bareText

object BinWarning {
	const val CONFIRM_SLOT = 29
	const val PRICE_SLOT = 31
	const val ITEM_SLOT = 13

	data class Warning(
		val screen: AbstractContainerScreen<*>,
		val itemName: String,
		val sellingPrice: Long,
		val lowestBinTotal: Double,
		val buying: Boolean = false,
		val slotIndex: Int = CONFIRM_SLOT,
	)

	private fun coinsAfter(text: String, label: String, anywhere: Boolean): Long? {
		var from = 0
		while (true) {
			val at = text.indexOf(label, from)
			if (at < 0 || (!anywhere && at > 0)) return null
			val start = at + label.length
			var end = start
			while (end < text.length && (text[end] in '0'..'9' || text[end] == ',')) end++
			if (end > start && text.startsWith(" coins", end)) return text.substring(start, end).replace(",", "").toLongOrNull()
			from = at + 1
		}
	}

	fun sellPrice(buttonName: String): Long? = coinsAfter(buttonName, "Item price: ", anywhere = true)

	fun buyPrice(loreLine: String): Long? = coinsAfter(loreLine.trim(), "Price: ", anywhere = false)

	fun shouldWarnBuy(price: Long, lowestBinTotal: Double, pct: Int): Boolean =
		price > 0 && lowestBinTotal > 0 && price > lowestBinTotal * (1 + pct / 100.0)

	fun shouldWarn(sellingPrice: Long, lowestBinTotal: Double, undercutPct: Int, overcutPct: Int, overcut: Boolean): Boolean {
		if (sellingPrice <= 0 || lowestBinTotal <= 0) return false
		val floor = lowestBinTotal * (1 - undercutPct / 100.0).coerceIn(0.0, 1.0)
		return sellingPrice < floor || (overcut && sellingPrice > lowestBinTotal * (1 + overcutPct / 100.0))
	}

	class Hold<W : Any> {
		var held: W? = null
			private set
		private var releasing = false

		fun judge(check: () -> W?): Boolean {
			if (releasing) { releasing = false; return false }
			if (held != null) return true
			held = check() ?: return false
			return true
		}

		fun release(stillOpen: (W) -> Boolean, send: (W) -> Unit) {
			val w = held ?: return
			held = null
			if (!stillOpen(w)) return
			releasing = true
			send(w)
			releasing = false
		}

		fun drop() { held = null }
	}

	private val hold = Hold<Warning>()
	val pending: Warning? get() = hold.held

	enum class Button { YES, NO }

	fun button(width: Int, height: Int, mx: Double, my: Double): Button? {
		val top = height / 2 + 23
		if (my < top || my >= top + 16) return null
		val yesLeft = width / 2 - 43
		val noLeft = width / 2 + 3
		return when {
			mx >= yesLeft && mx < yesLeft + 40 -> Button.YES
			mx >= noLeft && mx < noLeft + 40 -> Button.NO
			else -> null
		}
	}

	fun dialogText(itemName: String, price: Long, lowest: Double, buying: Boolean): List<Triple<Any, Int, Int>> {
		val change = (price / lowest - 1) * 100
		val changeText = (if (change >= 0) "§a+" else "§c") + "%.0f%%".format(change)
		val grey = 0xFFA0A0A0.toInt()
		return listOf(
			Triple(tr("apex.auction.binwarning.title", "Are you SURE?"), -35, 0xFFFF4040.toInt()),
			Triple("[ $itemName ]", -20, 0xFFFFFFFF.toInt()),
			Triple(tr("apex.auction.binwarning.lowest", "has a lowest BIN of ${ApexFormatters.compactNumber(lowest)} coins"), -11, grey),
			Triple(if (buying) tr("apex.auction.binwarning.continue-buy", "Continue buying it for")
			else tr("apex.auction.binwarning.continue", "Continue selling it for"), 5, grey),
			Triple("§6${ApexFormatters.compactNumber(price.toDouble())}§r coins? ($changeText§r)", 14, grey),
		)
	}

	fun isBinViewScreen(screen: AbstractContainerScreen<*>?) =
		screen != null && screen.title.bareText.trim().startsWith("BIN Auction View")

	fun isCreateBinScreen(screen: AbstractContainerScreen<*>?) =
		screen != null && screen.title.bareText.trim().startsWith("Create BIN Auction")

	private fun stackLowest(screen: AbstractContainerScreen<*>): Pair<ItemStack, Double>? {
		val stack = screen.menu.slots.getOrNull(ITEM_SLOT)?.item ?: return null
		val id = stack.view().repoItemId ?: return null
		val each = HypixelStaticData.lowestBin[id] ?: HypixelStaticData.avg3dlowestBin[id] ?: return null
		return stack to each * stack.count
	}

	private fun guardBuy(slot: Slot, screen: AbstractContainerScreen<*>): Boolean {
		if (!AuctionTools.TConfig.buyGuard || slot.container is Inventory) return false
		if ("Buy Item Right Now" !in slot.item.hoverName.bareText) return false
		return hold.judge {
			val price = slot.item.view().loreAccordingToNbt.firstNotNullOfOrNull { buyPrice(it.bareText) } ?: return@judge null
			val (stack, total) = stackLowest(screen) ?: return@judge null
			if (!shouldWarnBuy(price, total, AuctionTools.TConfig.buyGuardThreshold)) null
			else Warning(screen, stack.hoverName.bareText, price, total, buying = true, slotIndex = slot.index)
		}
	}

	private fun guardSell(slot: Slot, screen: AbstractContainerScreen<*>): Boolean {
		if (slot.container is Inventory || slot.containerSlot != CONFIRM_SLOT) return false
		return hold.judge {
			val label = screen.menu.slots.getOrNull(PRICE_SLOT)?.item?.hoverName?.bareText ?: return@judge null
			val price = sellPrice(label) ?: return@judge null
			val (stack, total) = stackLowest(screen) ?: return@judge null
			val c = AuctionTools.TConfig
			if (!shouldWarn(price, total, c.undercutThreshold, c.overcutThreshold, c.overcutWarning)) null
			else Warning(screen, stack.hoverName.bareText, price, total)
		}
	}

	@Subscribe
	fun onSlotProtect(event: IsSlotProtectedEvent) {
		if (event.origin != IsSlotProtectedEvent.MoveOrigin.INVENTORY_MOVE) return
		if (!AuctionTools.TConfig.binWarning || !SkyBlockSession.inSkyBlock) return
		val screen = Client.containerScreen ?: return
		val slot = event.slot ?: return
		val swallow = when {
			isBinViewScreen(screen) -> guardBuy(slot, screen)
			isCreateBinScreen(screen) -> guardSell(slot, screen)
			else -> false
		}
		if (swallow) event.protectSilent()
	}

	private fun confirm() {
		val player = Client.player

		hold.release({ player != null && player.containerMenu === it.screen.menu }) {
			Client.gameMode?.handleContainerInput(it.screen.menu.containerId, it.slotIndex, 0, ContainerInput.PICKUP, player!!)
		}
	}

	@Subscribe
	fun onScreenChange(event: ScreenChangeEvent) {
		val w = hold.held ?: return
		if (event.new !== w.screen) hold.drop()
	}

	@Subscribe
	fun onClick(event: HandledScreenClickEvent) {
		val w = hold.held ?: return
		if (w.screen !== event.screen) return
		event.cancel()
		when (button(event.screen.width, event.screen.height, event.mouseX, event.mouseY)) {
			Button.YES -> { dev.ashtonite365.apex.features.menus.MenuPress.acted(); confirm() }
			Button.NO -> { dev.ashtonite365.apex.features.menus.MenuPress.acted(); hold.drop() }
			null -> {}
		}
	}

	@Subscribe
	fun onKey(event: HandledScreenKeyPressedEvent) {
		val w = hold.held ?: return
		if (w.screen !== event.screen) return
		when ((event.input as? GenericInputAction.KeyboardInput)?.keyCode ?: return) {
			InputConstants.KEY_Y, InputConstants.KEY_RETURN -> confirm()
			InputConstants.KEY_N, InputConstants.KEY_ESCAPE -> hold.drop()
			else -> return
		}
		event.cancel()
	}

	@Subscribe
	fun onRender(event: ScreenRenderPostEvent) {
		val w = hold.held ?: return
		val screen = event.screen as? AbstractContainerScreen<*> ?: return
		if (w.screen !== screen) return
		val ctx: GuiGraphicsExtractor = event.drawContext
		ctx.nextStratum()
		val cx = screen.width / 2
		val cy = screen.height / 2
		ctx.fill(0, 0, screen.width, screen.height, 0x80000000.toInt())
		ctx.fill(cx - 90, cy - 45, cx + 90, cy + 45, 0xF0202020.toInt())
		for ((text, dy, colour) in dialogText(w.itemName, w.sellingPrice, w.lowestBinTotal, w.buying)) when (text) {
			is Component -> ctx.centeredText(Client.font, text, cx, cy + dy, colour)
			else -> ctx.centeredText(Client.font, text.toString(), cx, cy + dy, colour)
		}
		val top = cy + 23
		for ((left, label) in listOf(cx - 43 to "§a[Y]es", cx + 3 to "§c[N]o")) {
			ctx.fill(left, top, left + 40, top + 16, 0xFF404040.toInt())
			ctx.centeredText(Client.font, label, left + 20, top + 4, 0xFFFFFFFF.toInt())
		}
	}
}
