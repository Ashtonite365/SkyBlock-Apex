// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.misc

import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.Items
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.IsSlotProtectedEvent
import dev.ashtonite365.apex.events.OutgoingPacketEvent
import dev.ashtonite365.apex.events.ScreenRenderPostEvent
import dev.ashtonite365.apex.mixins.accessor.AccessorSignEditScreen
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.math.Calculator
import dev.ashtonite365.apex.util.tr
import dev.ashtonite365.apex.util.bareText

object CoreMiscTweaks {
	@Config
	object TConfig : ManagedConfig("core-misc", Category.MISC) {
		val signCalculator by toggle("sign-calculator") { true }
		val antiCoopAdd by toggle("anti-coop-add") { true }
	}

	private const val SEARCH_SIGN_MARK = "Enter query"
	private const val COOP_ADD_COMMAND = "hypixelcommand:coopadd"

	fun calculate(line: String): String? =
		if (line.isBlank() || Calculator.isPlainNumber(line)) null else Calculator.evaluateOrNull(line)?.let(Calculator::format)

	private fun signsOn() = TConfig.signCalculator && SkyBlockSession.inSkyBlock

	private fun editedSum(screen: AbstractSignEditScreen): String? {
		val access = screen as AccessorSignEditScreen
		val lines = access.sign_Apex?.getText(access.slot_Apex)?.getMessages(false) ?: return null
		if (lines.getOrNull(3)?.bareText?.trim() == SEARCH_SIGN_MARK) return null
		return lines.firstOrNull()?.bareText?.trim()
	}

	@Subscribe
	fun onRenderSign(event: ScreenRenderPostEvent) {
		if (!signsOn()) return
		val screen = event.screen as? AbstractSignEditScreen ?: return
		val sum = editedSum(screen)?.takeUnless { it.isBlank() || Calculator.isPlainNumber(it) } ?: return
		val preview = Calculator.evaluateOrNull(sum)?.let { "§a= ${Calculator.format(it)}" } ?: "§c= ?"
		event.drawContext.nextStratum()
		event.drawContext.centeredText(Client.font, preview, screen.width / 2, 30, 0xFFFFFFFF.toInt())
	}

	private fun rewriteSign(event: OutgoingPacketEvent, packet: ServerboundSignUpdatePacket) {
		if (!signsOn()) return
		val lines = packet.lines()
		if (lines.getOrNull(3)?.trim() == SEARCH_SIGN_MARK) return
		val first = lines.firstOrNull()?.trim() ?: return
		val value = calculate(first) ?: return
		event.cancel()
		Client.connection?.send(ServerboundSignUpdatePacket(packet.pos(), listOf(value) + lines.drop(1), packet.slot()))
	}

	private fun guardCommand(event: OutgoingPacketEvent, packet: ServerboundChatCommandPacket) {
		if (!TConfig.antiCoopAdd || !packet.command().lowercase().startsWith(COOP_ADD_COMMAND)) return
		event.cancel()
		Client.showChat(tr("apex.anticoopadd.command",
			"§eApex blocked a suspicious co-op add command. If you really want to add someone, type /coopadd <name>."))
	}

	@Subscribe
	fun onPacket(event: OutgoingPacketEvent) {
		when (val packet = event.packet) {
			is ServerboundSignUpdatePacket -> rewriteSign(event, packet)
			is ServerboundChatCommandPacket -> guardCommand(event, packet)
		}
	}

	@Subscribe
	fun onSlotClick(event: IsSlotProtectedEvent) {
		if (!TConfig.antiCoopAdd || event.origin != IsSlotProtectedEvent.MoveOrigin.INVENTORY_MOVE) return
		val slot = event.slot?.takeUnless { it.container is Inventory } ?: return
		val title = Client.screenTitle?.takeIf { "Profile" in it } ?: return
		val item = slot.item
		if (item.item != Items.DIAMOND || "Co-op Request" !in item.hoverName.bareText) return
		val player = title.split("'s Profile")[0]
		event.protectSilent()
		val offer = tr("apex.anticoopadd.click",
			"§eYou just clicked the Co-op add button. If you want to co-op add $player, click this message.").copy()
		Client.showChat(offer.withStyle {
			it.withClickEvent(ClickEvent.RunCommand("/coopadd $player"))
				.withHoverEvent(HoverEvent.ShowText(Component.literal("§eClick to add $player to your co-op")))
		})
	}
}
