package dev.ashtonite365.apex.features.inventory

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HandledScreenKeyPressedEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.SlotUtils.clickLeftMouseButton

object WardrobeKeybinds {
	@Config
	object TConfig : ManagedConfig("wardrobe-keybinds", Category.INVENTORY) {
		val slotKeysEnabled by toggle("wardrobe-keybinds") { false }

		val changePageKeybind by keyBindingWithDefaultUnbound("change-page")
		val nextPage by keyBindingWithDefaultUnbound("next-page")
		val previousPage by keyBindingWithDefaultUnbound("previous-page")
		val slotKeybinds = (1..9).map { keyBindingWithDefaultUnbound("slot-$it") }
		val unequipOnRepress by toggle("allow-unequipping") { true }

		override fun migrate(saved: kotlinx.serialization.json.JsonObject) = UnbindWardrobeKeysMigration.migrate(name, saved)
		override fun extraSave() = UnbindWardrobeKeysMigration.flag()
	}

	val pageTitle = Regex("""\(\d+/\d+\) (?:Armor|Equipment) Sets""")
	const val FIRST_SET_BUTTON = 36
	const val BACK_ARROW = 45
	const val FORWARD_ARROW = 53

	fun setButtonSlot(keyIndex: Int) = FIRST_SET_BUTTON + keyIndex

	fun arrowToClick(goesBack: Boolean, goesForward: Boolean, backShown: Boolean, forwardShown: Boolean): Int? = when {
		goesBack && backShown -> BACK_ARROW
		goesForward && forwardShown -> FORWARD_ARROW
		else -> null
	}

	fun maySelect(equipped: Boolean, selectable: Boolean, unequipOnRepress: Boolean) =
		if (equipped) unequipOnRepress else selectable

	private fun isArrow(item: Item) = item == Items.ARROW

	@Subscribe
	fun onWardrobeKey(event: HandledScreenKeyPressedEvent) {
		if (Client.player == null || Client.level == null || Client.gameMode == null) return
		val screen = event.screen as? AbstractContainerScreen<*> ?: return
		if (event.isRepeat) return
		if (!pageTitle.matches(screen.title.string) || !TConfig.slotKeysEnabled) return
		val menu = screen.menu

		val both = event.matches(TConfig.changePageKeybind)
		val back = both || event.matches(TConfig.previousPage)
		val forward = both || event.matches(TConfig.nextPage)
		if (back || forward) {
			event.cancel()
			arrowToClick(back, forward, isArrow(menu.getSlot(BACK_ARROW).item.item), isArrow(menu.getSlot(FORWARD_ARROW).item.item))
				?.let { menu.getSlot(it).clickLeftMouseButton(menu) }
			return
		}

		val keyIndex = TConfig.slotKeybinds.indexOfFirst { event.matches(it.get()) }
		if (keyIndex < 0) return
		event.cancel()
		val button = menu.getSlot(setButtonSlot(keyIndex))
		val item = button.item.item
		if (maySelect(item == Items.DYE.lime, item == Items.DYE.gray, TConfig.unequipOnRepress))
			button.clickLeftMouseButton(menu)
	}
}
