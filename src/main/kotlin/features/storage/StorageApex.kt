package dev.ashtonite365.apex.features.storage

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.util.StringRepresentable
import net.minecraft.world.entity.player.Inventory
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ScreenRenderPostEvent
import dev.ashtonite365.apex.events.WorldKeyboardEvent
import dev.ashtonite365.apex.features.inventory.storageoverlay.StorageMenuHandle
import dev.ashtonite365.apex.features.inventory.storageoverlay.StorageData
import dev.ashtonite365.apex.features.inventory.storageoverlay.StorageOverlay
import dev.ashtonite365.apex.features.inventory.storageoverlay.StorageOverlayScreen
import dev.ashtonite365.apex.features.inventory.storageoverlay.StorageOverviewScreen
import dev.ashtonite365.apex.features.inventory.storageoverlay.StoragePageSlot
import dev.ashtonite365.apex.mixins.accessor.AccessorHandledScreen
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.ScreenUtil
import dev.ashtonite365.apex.util.mc.NeedsComponents
import dev.ashtonite365.apex.util.tr

object StorageApex {
	enum class OpenTarget : StringRepresentable {
		OVERLAY, OVERVIEW;

		override fun getSerializedName(): String = name
	}

	val config get() = StorageOverlay.TConfig

	fun open(target: OpenTarget = config.openKeyTarget) {
		val player = Client.player ?: return
		ScreenUtil.setScreenLater(if (target == OpenTarget.OVERVIEW) StorageOverviewScreen() else StorageOverlayScreen())
		player.connection.sendCommand("storage")
	}

	@Subscribe
	fun onWorldKey(event: WorldKeyboardEvent) {
		if (Client.screen != null) return
		if (event.matches(config.openKey)) open()
	}

	fun hoveredPage(screen: AbstractContainerScreen<*>): StoragePageSlot? {
		if (StorageMenuHandle.fromScreen(screen) !is StorageMenuHandle.Overview) return null
		val slot = (screen as AccessorHandledScreen).focusedSlot_Apex ?: return null
		if (slot.container is Inventory) return null
		if (slot.item.isEmpty || slot.item.item in StorageOverviewScreen.placeholderPanes) return null
		return StoragePageSlot.fromOverviewSlotIndex(slot.containerSlot)
	}

	@OptIn(NeedsComponents::class)
	@Subscribe
	fun onScreenRender(event: ScreenRenderPostEvent) {
		if (!config.hoverPreview) return
		val screen = event.screen as? AbstractContainerScreen<*> ?: return
		val page = hoveredPage(screen) ?: return
		val inventory = StorageOverlay.Data.data.knownPages[page]
		drawPagePreview(event.drawContext, event.screen.width, event.screen.height, event.mouseX, event.mouseY, page, inventory)
	}

	@OptIn(NeedsComponents::class)
	fun drawPagePreview(context: net.minecraft.client.gui.GuiGraphicsExtractor, screenWidth: Int, screenHeight: Int, mouseX: Int, mouseY: Int, page: StoragePageSlot, inventory: StorageData.RememberedPage?) {
		val font = Client.font
		val name = StorageOverlay.Data.data.labelFor(page)
		val inv = inventory?.inventory
		val rows = inv?.rows ?: 0
		val w = 176
		val h = if (inv == null) 30 else 18 + 18 * rows
		val scale = config.hoverPreviewScale / 100F
		val sw = (w * scale).toInt()
		val sh = (h * scale).toInt()

		var x = mouseX - 12 - sw
		if (x < 2) x = (mouseX + 12).coerceAtMost((screenWidth - sw - 2).coerceAtLeast(2))
		val y = (mouseY - sh / 2).coerceIn(2, (screenHeight - sh - 2).coerceAtLeast(2))
		context.nextStratum()
		context.pose().pushMatrix()
		context.pose().translate(x.toFloat(), y.toFloat())
		context.pose().scale(scale, scale)
		val style = StorageOverlay.TConfig.textureStyle
		context.blitSprite(RenderPipelines.GUI_TEXTURED, style.sprite("upper_background"), 0, 0, w, h)
		context.text(font, Component.literal(name), 7, 5, -1, true)
		if (inv == null) {
			context.text(font, tr("apex.storage-apex.preview.not-loaded", "Not cached yet: open this page once"), 7, 17, 0xFFFF5555.toInt(), true)
		} else {
			context.blitSprite(RenderPipelines.GUI_TEXTURED, style.sprite("storage_row"), 7, 15, 162, 18 * rows)
			val search = StorageOverlayScreen.searchText.get().takeIf { it.isNotBlank() && StorageOverlay.TConfig.highlightSearchResults }
			inv.stacks.forEachIndexed { index, lazy ->
				val stack = lazy.current()
				if (stack.isEmpty) return@forEachIndexed
				val ix = 8 + 18 * (index % 9)
				val iy = 16 + 18 * (index / 9)
				if (search != null && StorageOverlayScreen.matchesSearch(stack, search))
					context.fill(ix, iy, ix + 16, iy + 16, StorageOverlay.TConfig.highlightSearchResultsColour.getEffectiveColourRGB())
				context.item(stack, ix, iy)
				context.itemDecorations(font, stack, ix, iy)
			}
		}
		context.pose().popMatrix()
	}

}
