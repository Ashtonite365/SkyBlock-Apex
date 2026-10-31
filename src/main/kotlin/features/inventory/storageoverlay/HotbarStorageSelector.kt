package dev.ashtonite365.apex.features.inventory.storageoverlay

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.DyeColor
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.util.StringRepresentable
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.WorldKeyboardEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.render.guiSprite
import dev.ashtonite365.apex.util.mc.NeedsComponents
import dev.ashtonite365.apex.util.tr

@OptIn(NeedsComponents::class)
object HotbarStorageSelector {
	val identifier: String get() = "hotbar-storage-selector"

	@Config
	object TConfig : ManagedConfig(identifier, category = Category.INVENTORY) {
		val enabled by toggle("enabled") { false }
		val scrollMode by choice("scroll-mode") { ScrollMode.WITH_KEY }
		val scrollKey by keyBinding("scroll-key") { InputConstants.KEY_LSHIFT }
		val hotkey by keyBinding("hotkey") { InputConstants.KEY_GRAVE }
		val side by choice("side") { Side.LEFT }
		val preview by toggle("preview") { true }
		val previewOpacity by integer("preview-opacity", 0, 100) { 50 }
		val arrowKeys by toggle("arrow-keys") { false }
		val arrowLeft by keyBinding("arrow-left") { InputConstants.KEY_LEFT }
		val arrowRight by keyBinding("arrow-right") { InputConstants.KEY_RIGHT }
		val arrowOpen by keyBinding("arrow-open") { InputConstants.KEY_DOWN }
	}

	enum class ScrollMode : StringRepresentable {
		WITH_KEY, ALWAYS, NEVER;

		override fun getSerializedName(): String = name
	}

	enum class Side : StringRepresentable {
		LEFT, RIGHT;

		override fun getSerializedName(): String = name
	}

	var isOverridingSlot = false
		private set

	var selectedIndex: Int
		get() {
			val pages = pages()
			val selected = StorageOverlay.Data.data.hotbarPage
			return pages.indexOf(selected).coerceAtLeast(0)
		}
		private set(value) {
			val pages = pages()
			StorageOverlay.Data.data.hotbarPage = pages.getOrNull(value.coerceIn(0, (pages.size - 1).coerceAtLeast(0)))
			StorageOverlay.Data.markDirty()
		}

	private val hotbarSlotSprite = Identifier.withDefaultNamespace("hud/hotbar_offhand_left")
	private val hotbarSelectionSprite = Identifier.withDefaultNamespace("hud/hotbar_selection")

	private fun isActive() = TConfig.enabled && SkyBlockSession.inSkyBlock && Client.player != null

	private fun pages(): List<StoragePageSlot> =
		StorageOverlay.Data.data?.knownPages?.keys?.toList() ?: listOf()

	private fun clampSelection() {

		if (StorageOverlay.Data.data.hotbarPage !in pages() && pages().isNotEmpty())
			selectedIndex = 0
	}

	private fun selectedPage(): StoragePageSlot? = pages().getOrNull(selectedIndex)

	fun isSlotSelected(): Boolean {
		if (!isActive()) {
			isOverridingSlot = false
			return false
		}
		if (Client.screen != null) return false
		if (Client.player?.inventory?.selectedSlot != 0) {
			isOverridingSlot = false
			return false
		}
		return isOverridingSlot
	}

	private fun moveSelection(delta: Int) {
		selectedIndex = selectedIndex + delta
	}

	private fun openSelectedPage() {
		val page = selectedPage()
		if (page == null) Client.runCommand("storage")
		else page.navigateTo()
	}

	private fun isScrollAllowed() = when (TConfig.scrollMode) {
		ScrollMode.WITH_KEY -> TConfig.scrollKey.isPressed()
		ScrollMode.ALWAYS -> true
		ScrollMode.NEVER -> false
	}

	@JvmStatic
	fun onHotbarScroll(wheel: Int, current: Int, result: Int): Int {
		if (!isActive() || Client.screen != null || wheel == 0) return result

		val direction = if (wheel > 0) 1 else -1
		if (isOverridingSlot && current == 0 && TConfig.scrollKey.isPressed()) {
			moveSelection(-direction)
			return 0
		}
		val allowScroll = isScrollAllowed()
		if (allowScroll && result == 0 && direction == -1 && !isOverridingSlot) {
			isOverridingSlot = true
			return 0
		}
		if (current == 0 && result == 1 && direction == -1 && isOverridingSlot) {
			isOverridingSlot = false
			return 0
		}
		if (allowScroll && current == 0 && result == 8 && direction == 1 && !isOverridingSlot) {
			isOverridingSlot = true
			return 0
		}
		if (isOverridingSlot && result != 0) isOverridingSlot = false
		return result
	}

	@JvmStatic
	fun beforeHandleKeybinds() {
		val options = Client.options
		if (options.keyHotbarSlots[0].isDown) isOverridingSlot = false
		if (!isSlotSelected()) return
		var open = false
		while (options.keyAttack.consumeClick()) open = true
		while (options.keyUse.consumeClick()) open = true
		if (options.keyAttack.isDown || options.keyUse.isDown) {
			options.keyAttack.setDown(false)
			options.keyUse.setDown(false)
		}
		if (open && !TConfig.scrollKey.isPressed()) openSelectedPage()
	}

	@Subscribe
	fun onKey(event: WorldKeyboardEvent) {
		if (!isActive() || Client.screen != null) return
		if (event.matches(TConfig.hotkey)) {
			Client.player?.inventory?.selectedSlot = 0
			isOverridingSlot = true
		}
		if (TConfig.arrowKeys) {
			when {
				event.matches(TConfig.arrowLeft) -> moveSelection(-1)
				event.matches(TConfig.arrowRight) -> moveSelection(1)
				event.matches(TConfig.arrowOpen) -> openSelectedPage()
			}
		}
	}

	private fun displayStack(page: StoragePageSlot?): ItemStack {
		page ?: return ItemStack(Items.CHEST)
		val icon = StorageOverlay.Data.data.knownPages[page]?.iconStack?.current()
		if (icon != null && !icon.isEmpty && !isGlassPane(icon)) return icon
		return if (page.isEnderChest) ItemStack(Items.ENDER_CHEST) else ItemStack(Items.CHEST)
	}

	@JvmStatic
	fun heldItemOverride(original: ItemStack): ItemStack {
		if (!isSlotSelected()) return original
		return displayStack(selectedPage())
	}

	@JvmStatic
	fun shouldDrawVanillaSelection(): Boolean = !isSlotSelected()

	private fun isGlassPane(stack: ItemStack) =
		StoragePaneCtm.isPane(stack)

	@Subscribe
	fun onRenderHud(event: HudRenderEvent) {
		if (!isActive() || Client.screen != null) return
		clampSelection()
		val context = event.context
		val width = context.guiWidth()
		val height = context.guiHeight()
		val centerX = width / 2
		var offset = 91 + 10 + 12
		if (TConfig.side == Side.RIGHT) offset = -offset
		val left = centerX - offset - 12
		val top = height - 22
		val page = selectedPage()
		val held = displayStack(page)

		if (TConfig.preview && isSlotSelected()) {
			drawPreview(event, page, centerX, height, held)
		}

		context.guiSprite(hotbarSlotSprite, left + 1, top - 1, 29, 24)
		if (isSlotSelected()) {
			context.guiSprite(hotbarSelectionSprite, left, top - 1, 24, 23)
		}
		val label = page?.let { if (it.isEnderChest) it.index + 1 else it.index - 9 + 1 } ?: 1
		context.item(held, left + 4, top + 3)
		context.itemDecorations(Client.font, held, left + 4, top + 3, label.toString())
	}

	private fun drawPreview(
		event: HudRenderEvent,
		page: StoragePageSlot?,
		centerX: Int,
		height: Int,
		held: ItemStack
	) {
		val context = event.context
		val inventory = page?.let { StorageOverlay.Data.data?.knownPages?.get(it)?.inventory }
		if (page == null) {
			context.centeredText(Client.font, tr("apex.hotbar-storage.run-storage", "Run /storage to enable this feature!"), centerX, height - 80, 0xFFFF0000.toInt())
			return
		}
		if (inventory == null) {
			context.centeredText(Client.font, tr("apex.hotbar-storage.load", "Right-click to load items"), centerX, height - 80, 0xFFFF0000.toInt())
			return
		}
		val rows = inventory.rows
		val startX = centerX - 176 / 2
		val startY = height - 80 - (10 + 18 * rows)
		val alpha = TConfig.previewOpacity / 100F
		val style = StorageOverlay.TConfig.textureStyle
		context.blitSprite(RenderPipelines.GUI_TEXTURED, style.sprite("upper_background"), startX, startY, 176, 14 + 18 * rows, alpha)
		context.blitSprite(RenderPipelines.GUI_TEXTURED, style.sprite("storage_row"), startX + 7, startY + 7, 162, 18 * rows, alpha)
		val stacks = inventory.stacks.map { it.current() }

		StoragePaneCtm.drawPanes(
			context, stacks, alpha.coerceAtLeast(0.6F),
			{ startX + 8 + 18 * (it % 9) }, { startY + 8 + 18 * (it / 9) })
		stacks.forEachIndexed { index, stack ->
			if (stack.isEmpty || isGlassPane(stack)) return@forEachIndexed
			val itemX = startX + 8 + 18 * (index % 9)
			val itemY = startY + 8 + 18 * (index / 9)
			context.item(stack, itemX, itemY)
			context.itemDecorations(Client.font, stack, itemX, itemY)
		}
		context.item(held, centerX - 8, startY - 8)
		context.centeredText(Client.font, Component.literal(StorageOverlay.Data.data.labelFor(page)), centerX, height - 76, 0xFFFF0000.toInt())
		if (TConfig.scrollKey.isBound && !TConfig.scrollKey.isPressed()) {
			context.centeredText(
				Client.font,
				Component.literal("[${TConfig.scrollKey}] ").append(tr("apex.hotbar-storage.scroll-hint", "Scroll Backpacks")),
				centerX, startY - 10, 0xFF32CD32.toInt()
			)
		}
	}
}
