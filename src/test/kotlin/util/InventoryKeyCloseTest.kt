// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.util

import com.mojang.blaze3d.platform.InputConstants
import io.github.notenoughupdates.moulconfig.gui.GuiComponent
import io.github.notenoughupdates.moulconfig.gui.GuiContext
import io.github.notenoughupdates.moulconfig.gui.component.TextFieldComponent
import io.github.notenoughupdates.moulconfig.platform.MoulConfigScreenComponent
import net.minecraft.client.KeyMapping
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.MultiLineEditBox
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.auction.SignSearchScreen
import dev.ashtonite365.apex.features.inventory.InventorySearch
import dev.ashtonite365.apex.features.inventory.storageoverlay.StorageOverlayScreen
import dev.ashtonite365.apex.features.menus.MenuBrowseScreen
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.support.MenuSearch
import dev.ashtonite365.apex.gui.EmptyComponent
import dev.ashtonite365.apex.util.PopupHostScreen
import dev.ashtonite365.apex.util.InventoryKeyClose
import dev.ashtonite365.apex.util.PopupFragment
import dev.ashtonite365.apex.util.TextInputFocus
import dev.ashtonite365.apex.util.UserCloseHook

open class FakeApexScreen : Screen(Component.empty()) {
	var closes = 0
	override fun onClose() { closes++ }
}

class FakeHookScreen : FakeApexScreen(), UserCloseHook {
	var intercept = false
	var before = 0
	var closesAtBefore = -1
	override fun interceptUserClose() = intercept
	override fun beforeUserClose() { before++; closesAtBefore = closes }
}

class FakeOwnInputScreen : FakeApexScreen(), TextInputFocus {
	var typing = false
	override val textInputFocused get() = typing
}

class FakeApexTextField : GuiEventListener, TextInputFocus {
	var f = false
	override val textInputFocused get() = f
	override fun setFocused(focused: Boolean) { f = focused }
	override fun isFocused() = f
}

class FakePanel : AbstractContainerEventHandler() {
	override fun children(): List<GuiEventListener> = emptyList()
}

class FakeMoulScreen : MoulConfigScreenComponent(Component.empty(), InventoryKeyCloseTest.alloc(GuiContext::class.java), null) {
	var closes = 0
	override fun onClose() { closes++ }
}

class FakeFragmentScreen : PopupHostScreen() {
	var closes = 0
	override fun onClose() { closes++ }
}

class FakeSearchView : MenuView {
	val search = MenuSearch()
	override val x = 0; override val y = 0; override val w = 10; override val h = 10
	override fun layout(x: Int, y: Int, w: Int, h: Int) {}
	override fun render(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {}
	override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean) = false
	override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double) = false
	override fun keyPressed(input: KeyEvent) = search.keyPressed(input)
	override fun charTyped(input: CharacterEvent) = search.charTyped(input)
	override val textFocused get() = search.focused
	var claim = false
	override fun interceptClose() = claim
}

class InventoryKeyCloseTest {
	companion object {
		private val unsafe = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null) as sun.misc.Unsafe
		@Suppress("UNCHECKED_CAST")
		fun <T> alloc(c: Class<T>): T = unsafe.allocateInstance(c) as T
		fun set(o: Any, owner: Class<*>, name: String, v: Any?) =
			owner.getDeclaredField(name).apply { isAccessible = true }.set(o, v)

		fun focusedEditBox(focused: Boolean = true): EditBox = alloc(EditBox::class.java).also {
			set(it, AbstractWidget::class.java, "visible", true)
			set(it, AbstractWidget::class.java, "active", true)
			set(it, AbstractWidget::class.java, "focused", focused)
			set(it, EditBox::class.java, "isEditable", true)
		}
		fun setScreenFocus(s: Any, w: GuiEventListener?) = set(s, AbstractContainerEventHandler::class.java, "focused", w)
		fun key(code: Int) = KeyEvent(code, 0, 0)
		fun click(button: Int) = MouseButtonEvent(1.0, 1.0, MouseButtonInfo(button, 0))
	}

	private val binding = KeyMapping("key.apex.test.inventory_close", InputConstants.KEY_E, KeyMapping.Category.INVENTORY)
	private lateinit var saved: () -> KeyMapping?
	private lateinit var savedItemList: () -> Boolean
	private var itemListTyping = false

	@BeforeEach fun seam() {
		saved = InventoryKeyClose.inventoryKey; InventoryKeyClose.inventoryKey = { binding }
		savedItemList = InventoryKeyClose.itemListFocus; InventoryKeyClose.itemListFocus = { itemListTyping }
	}
	@AfterEach fun restore() { InventoryKeyClose.inventoryKey = saved; InventoryKeyClose.itemListFocus = savedItemList }

	private fun rebind(code: Int) = binding.setKey(InputConstants.Type.KEYBOARD.getOrCreate(code))
	private fun screen() = alloc(FakeApexScreen::class.java)
	private fun assertTyping(s: Screen, closes: () -> Int, what: String) {
		assertTrue(InventoryKeyClose.textInputFocused(s), "$what: reported as focused")
		assertFalse(InventoryKeyClose.handleScreenKey(s, key(InputConstants.KEY_E)), "$what: key goes to the text box")
		assertEquals(0, closes(), "$what: not closed")
	}

	@Test fun inventoryKeyCloses() {
		val s = screen()
		assertTrue(InventoryKeyClose.handleScreenKey(s, key(InputConstants.KEY_E)))
		assertEquals(1, s.closes)
	}

	@Test fun reboundKeyClosesAndEThenDoesNot() {
		val s = screen()
		rebind(InputConstants.KEY_I)
		assertFalse(InventoryKeyClose.handleScreenKey(s, key(InputConstants.KEY_E)), "E no longer the inventory key")
		assertEquals(0, s.closes)
		assertTrue(InventoryKeyClose.handleScreenKey(s, key(InputConstants.KEY_I)))
		assertEquals(1, s.closes)

		rebind(InputConstants.KEY_E)
		assertFalse(InventoryKeyClose.handleScreenKey(s, key(InputConstants.KEY_I)))
		assertTrue(InventoryKeyClose.handleScreenKey(s, key(InputConstants.KEY_E)))
		assertEquals(2, s.closes)
	}

	@Test fun readsTheGameOptionsBindingByDefault() {

		InventoryKeyClose.inventoryKey = saved
		assertFalse(InventoryKeyClose.isInventoryKey(key(InputConstants.KEY_E)))
	}

	@Test fun mouseBindingClosesButLeftAndRightClickNever() {
		val s = screen()
		binding.setKey(InputConstants.Type.MOUSE.getOrCreate(InputConstants.MOUSE_BUTTON_MIDDLE))
		assertFalse(InventoryKeyClose.handleScreenKey(s, key(InputConstants.KEY_E)), "keyboard E not bound")
		assertTrue(InventoryKeyClose.handleScreenClick(s, click(InputConstants.MOUSE_BUTTON_MIDDLE)))
		assertEquals(1, s.closes)
		assertFalse(InventoryKeyClose.handleScreenClick(s, click(4)))
		binding.setKey(InputConstants.Type.MOUSE.getOrCreate(InputConstants.MOUSE_BUTTON_LEFT))
		assertFalse(InventoryKeyClose.handleScreenClick(s, click(InputConstants.MOUSE_BUTTON_LEFT)))
		binding.setKey(InputConstants.Type.MOUSE.getOrCreate(InputConstants.MOUSE_BUTTON_RIGHT))
		assertFalse(InventoryKeyClose.handleScreenClick(s, click(InputConstants.MOUSE_BUTTON_RIGHT)))
		assertEquals(1, s.closes)
	}

	@Test fun unboundNeverCloses() {
		val s = screen()
		binding.setKey(InputConstants.UNKNOWN)
		for (k in listOf(InputConstants.KEY_E, InputConstants.KEY_I, InputConstants.KEY_ESCAPE)) assertFalse(InventoryKeyClose.handleScreenKey(s, key(k)))
		assertEquals(0, s.closes)
	}

	@Test fun escapeIsUnchanged() {

		val s = screen()
		assertFalse(InventoryKeyClose.isInventoryKey(key(InputConstants.KEY_ESCAPE)))
		assertFalse(InventoryKeyClose.handleScreenKey(s, key(InputConstants.KEY_ESCAPE)))
		var before = 0
		assertFalse(InventoryKeyClose.handleMenuKey(s, key(InputConstants.KEY_ESCAPE), false, { false }, { before++ }))
		assertEquals(0, s.closes); assertEquals(0, before)
	}

	@Test fun nonApexScreensAreLeftToVanilla() {
		val vanilla = alloc(TitleScreen::class.java)
		assertFalse(InventoryKeyClose.isApexScreen(vanilla))
		assertFalse(InventoryKeyClose.handleScreenKey(vanilla, key(InputConstants.KEY_E)))
	}

	@Test fun menuPathRunsTheEscapeStepThenTheNormalClose() {
		val s = screen()
		val order = mutableListOf<String>()
		assertTrue(InventoryKeyClose.handleMenuKey(s, key(InputConstants.KEY_E), false, { false }, { order += "escape-step(closes=${s.closes})" }))
		assertEquals(listOf("escape-step(closes=0)"), order); assertEquals(1, s.closes)

		assertTrue(InventoryKeyClose.handleMenuKey(s, key(InputConstants.KEY_E), false, { true }, { order += "x" }))
		assertEquals(1, s.closes); assertEquals(1, order.size)

		binding.setKey(InputConstants.Type.MOUSE.getOrCreate(InputConstants.MOUSE_BUTTON_MIDDLE))
		assertTrue(InventoryKeyClose.handleMenuClick(s, click(InputConstants.MOUSE_BUTTON_MIDDLE), false, { false }, {}))
		assertFalse(InventoryKeyClose.handleMenuClick(s, click(InputConstants.MOUSE_BUTTON_LEFT), false, { false }, {}))
		assertEquals(2, s.closes)
	}

	@Test fun userCloseHookRunsLikeEscape() {
		val s = alloc(FakeHookScreen::class.java)
		assertTrue(InventoryKeyClose.handleScreenKey(s, key(InputConstants.KEY_E)))
		assertEquals(1, s.before); assertEquals(0, s.closesAtBefore); assertEquals(1, s.closes)
		s.intercept = true
		assertTrue(InventoryKeyClose.handleScreenKey(s, key(InputConstants.KEY_E)))
		assertEquals(1, s.closes)
	}

	@Test fun vanillaEditBoxFocused() {
		val s = screen()
		setScreenFocus(s, focusedEditBox())
		assertTyping(s, { s.closes }, "EditBox")
		setScreenFocus(s, focusedEditBox(focused = false))
		assertTrue(InventoryKeyClose.handleScreenKey(s, key(InputConstants.KEY_E)), "unfocused EditBox: closes")
		assertEquals(1, s.closes)
		val readOnly = focusedEditBox().also { set(it, EditBox::class.java, "isEditable", false) }
		setScreenFocus(s, readOnly)
		assertFalse(InventoryKeyClose.textInputFocused(s), "a read-only box takes no typing")
	}

	@Test fun editBoxNestedInAPanelFocused() {
		val s = screen()
		val panel = alloc(FakePanel::class.java)
		setScreenFocus(panel, focusedEditBox())
		setScreenFocus(s, panel)
		assertTyping(s, { s.closes }, "EditBox inside a panel")
	}

	@Test fun multiLineEditBoxFocused() {
		val s = screen()
		val box = alloc(MultiLineEditBox::class.java)
		set(box, AbstractWidget::class.java, "visible", true)
		set(box, AbstractWidget::class.java, "focused", true)
		setScreenFocus(s, box)
		assertTyping(s, { s.closes }, "MultiLineEditBox")
	}

	@Test fun customApexTextFieldWidgetFocused() {
		val s = screen()
		val field = FakeApexTextField().also { it.setFocused(true) }
		setScreenFocus(s, field)
		assertTyping(s, { s.closes }, "custom Apex text field")
		field.setFocused(false)
		assertTrue(InventoryKeyClose.handleScreenKey(s, key(InputConstants.KEY_E)))
	}

	@Test fun screenOwnTextInputFocused() {
		val s = alloc(FakeOwnInputScreen::class.java).also { it.typing = true }
		assertTyping(s, { s.closes }, "screen-owned text input")
	}

	@Test fun menuSearchFocusedInAMenu() {

		val s = screen()
		val v = FakeSearchView()
		v.search.focused = true
		assertTrue(v.textFocused)
		assertFalse(InventoryKeyClose.handleMenuKey(s, key(InputConstants.KEY_E), v.textFocused, { v.interceptClose() }, {}))
		assertEquals(0, s.closes)
		v.search.focused = false
		assertTrue(InventoryKeyClose.handleMenuKey(s, key(InputConstants.KEY_E), v.textFocused, { v.interceptClose() }, {}))
		assertEquals(1, s.closes)
	}

	@Test fun menuBrowseScreenWithFocusedSearch() {
		val s = alloc(MenuBrowseScreen::class.java)
		val v = FakeSearchView().also { it.search.focused = true }
		set(s, MenuBrowseScreen::class.java, "view", v)
		assertTrue(InventoryKeyClose.textInputFocused(s))
		v.search.focused = false; v.claim = true
		assertFalse(InventoryKeyClose.textInputFocused(s))
		assertTrue((s as UserCloseHook).interceptUserClose(), "the view's own close claim applies to the inventory key too")
	}

	@Test fun signAuctionSearchAndNumberInputFocused() {

		val s = alloc(SignSearchScreen::class.java)
		setScreenFocus(s, focusedEditBox())
		assertTrue(InventoryKeyClose.isApexScreen(s))
		assertTrue(InventoryKeyClose.textInputFocused(s))
		assertFalse(InventoryKeyClose.handleScreenKey(s, key(InputConstants.KEY_E)))
	}

	@Test fun settingsSearchAndKeybindCaptureFocused() {

		val s = alloc(FakeMoulScreen::class.java)
		val ctx = alloc(GuiContext::class.java)
		set(s, MoulConfigScreenComponent::class.java, "guiContext", ctx)
		assertFalse(InventoryKeyClose.textInputFocused(s))
		set(ctx, GuiContext::class.java, "focusedElement", alloc(TextFieldComponent::class.java))
		assertTyping(s, { s.closes }, "settings search")
		set(ctx, GuiContext::class.java, "focusedElement", alloc(EmptyComponent::class.java) as GuiComponent)
		assertTyping(s, { s.closes }, "keybind capture")
		set(ctx, GuiContext::class.java, "focusedElement", null)
		assertTrue(InventoryKeyClose.handleScreenKey(s, key(InputConstants.KEY_E)))
		assertEquals(1, s.closes)
	}

	@Test fun storageOverlaySearchFocused() {
		val s = alloc(StorageOverlayScreen::class.java)
		val ctx = alloc(GuiContext::class.java)
		val knob = alloc(EmptyComponent::class.java)
		set(s, StorageOverlayScreen::class.java, "guiContext", ctx)
		set(s, StorageOverlayScreen::class.java, "knobHolder", knob)
		set(ctx, GuiContext::class.java, "focusedElement", alloc(TextFieldComponent::class.java))
		assertTrue(InventoryKeyClose.textInputFocused(s), "storage search")
		set(ctx, GuiContext::class.java, "focusedElement", knob)
		assertFalse(InventoryKeyClose.textInputFocused(s), "dragging the scroll bar is not typing")
	}

	@Test fun fragmentPopupFocused() {
		val s = alloc(FakeFragmentScreen::class.java)
		assertFalse(InventoryKeyClose.textInputFocused(s))
		set(s, PopupHostScreen::class.java, "popup", alloc(PopupFragment::class.java))
		assertTyping(s, { s.closes }, "popup")
	}

	@Test fun itemListSearchFocused() {
		val s = screen()
		assertFalse(InventoryKeyClose.textInputFocused(s))
		itemListTyping = true
		assertTyping(s, { s.closes }, "item list search")
	}

	@Test fun typedInventorySearchOpen() {
		val s = screen()
		InventorySearch.onInit(ClientInitEvent())
		val q = InventorySearch::class.java.getDeclaredField("query").apply { isAccessible = true }
		try {
			q.set(InventorySearch, "")
			assertTyping(s, { s.closes }, "typed inventory search")
		} finally { q.set(InventorySearch, null) }
		assertFalse(InventoryKeyClose.textInputFocused(s))
	}

	@Test fun throwingProbeCountsAsTyping() {
		var boom = false
		InventoryKeyClose.registerFocusProbe { if (boom) error("probe failed") else false }
		val s = screen()
		assertFalse(InventoryKeyClose.textInputFocused(s))
		boom = true
		try { assertTrue(InventoryKeyClose.textInputFocused(s), "unsure = do not close") } finally { boom = false }
	}
}
