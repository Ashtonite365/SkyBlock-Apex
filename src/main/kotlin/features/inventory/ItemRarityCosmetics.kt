package dev.ashtonite365.apex.features.inventory

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.TextColor
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HotbarItemRenderEvent
import dev.ashtonite365.apex.events.SlotRenderEvents
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.skyblock.Rarity

object ItemRarityCosmetics {
	@Config
	object TConfig : ManagedConfig("item-rarity-cosmetics", Category.INVENTORY) {
		val inInventories by toggle("background") { false }
		val inHotbar by toggle("background-hotbar") { false }
	}

	private val glow = Identifier.parse("apex:item_rarity_background")
	private const val OPAQUE = 0xFF000000.toInt()
	const val UNKNOWN_TINT = 0xFF00FF80.toInt()

	fun tintFor(rarity: Rarity): Int = Rarity.colourMap[rarity]?.let { TextColor.fromLegacyFormat(it)?.value }?.let { it or OPAQUE } ?: UNKNOWN_TINT

	private fun drawGlow(context: GuiGraphicsExtractor, x: Int, y: Int, stack: ItemStack) {
		val rarity = Rarity.fromItem(stack.view()) ?: return
		context.blitSprite(RenderPipelines.GUI_TEXTURED, glow, x, y, 16, 16, tintFor(rarity))
	}

	@Subscribe
	fun behindSlot(event: SlotRenderEvents.Before) {
		if (TConfig.inInventories) drawGlow(event.context, event.slot.x, event.slot.y, event.slot.item)
	}

	@Subscribe
	fun behindHotbarItem(event: HotbarItemRenderEvent) {
		if (TConfig.inHotbar) drawGlow(event.context, event.x, event.y, event.item)
	}
}
