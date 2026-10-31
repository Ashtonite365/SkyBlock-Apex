// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import net.minecraft.core.Holder
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import dev.ashtonite365.apex.events.SoundReceiveEvent
import dev.ashtonite365.apex.features.menus.MenuChain
import dev.ashtonite365.apex.features.menus.MenuChainRunner
import dev.ashtonite365.apex.features.menus.MenuClicks
import dev.ashtonite365.apex.features.menus.MenuPress
import dev.ashtonite365.apex.util.ApexClickDelay

@ExtendWith(AutoBootstrapExtension::class)
class ClickSoundOnceTest {
	private class FakeMenu(id: Int) : AbstractContainerMenu(null, id) {
		val box = SimpleContainer(54)
		init { for (i in 0 until 54) addSlot(Slot(box, i, 0, 0)) }
		override fun quickMoveStack(player: Player, i: Int) = ItemStack.EMPTY
		override fun stillValid(player: Player) = true
	}

	private var now = 10_000L

	private var localSounds = 0

	private var serverSounds = 0

	private var otherSounds = 0
	data class Sent(val containerId: Int, val slot: Int, val button: Int)
	private val packets = mutableListOf<Sent>()

	private val queue = mutableListOf<Pair<Long, () -> Unit>>()
	private val oldSound = MenuPress.sound; private val oldClock = MenuPress.clock

	init {
		MenuPress.resetForTests()
		ApexClickDelay.testSink = { c, _, sl, b, _ -> packets += Sent(c, sl, b) }
		MenuPress.sound = { localSounds++; Assertions.assertFalse(MenuPress.onLocalSound(SoundEvents.UI_BUTTON_CLICK.value().location, now), "Apex's own sound is never dropped") }
		MenuPress.clock = { now }
		ApexClickDelay.random = { 150 }
		ApexClickDelay.scheduler = { ms, a -> queue += (now + ms) to a }
	}
	@AfterEach fun reset() {
		runCatching { MenuChainRunner.cancel("test end") }
		MenuPress.sound = oldSound; MenuPress.clock = oldClock
		MenuPress.resetForTests(); ApexClickDelay.resetForTests()
	}

	private fun advance(ms: Long) {
		val end = now + ms
		while (true) {
			val next = queue.filter { it.first <= end }.minByOrNull { it.first } ?: break
			queue.remove(next); now = maxOf(now, next.first); next.second()
		}
		now = end
	}

	private fun server(sound: Holder<SoundEvent> = SoundEvents.UI_BUTTON_CLICK) {
		val e = SoundReceiveEvent(sound, SoundSource.MASTER, Vec3.ZERO, 1f, 1f, 0L)
		MenuPress.onServerSound(e)
		if (!e.cancelled) { if (sound === SoundEvents.UI_BUTTON_CLICK) serverSounds++ else otherSounds++ }
	}
	private val heard get() = localSounds + serverSounds

	private val owner = object : MenuChainRunner.Owner { override val id = "click-sound-test" }
	private fun chain() = MenuChain(listOf(MenuChain.Step(Regex("x"), MenuChain.Target("a"))))

	private fun bazaarPress(steps: Int, hypixelPerClick: Int) {
		val m = FakeMenu(30)
		MenuPress.press {
			MenuPress.acted()
			Assertions.assertTrue(MenuChainRunner.start(chain(), owner))
		}
		Assertions.assertTrue(MenuChainRunner.pressDriven)

		fun step(left: Int) {
			MenuPress.carry(MenuChainRunner.pressDriven) {
				ApexClickDelay.laterMs(150) {
					MenuClicks.autoClick(m, m.slots[10 + left], 0)
					queue += (now + 250) to { repeat(hypixelPerClick) { server() } }
					if (left > 1) queue += (now + 300) to { step(left - 1) }
				}
			}
		}
		step(steps)
		advance(10_000)
		Assertions.assertEquals(steps, packets.size)
	}

	@Test
	fun bazaarChainPressIsOneSoundWhateverHypixelSends() {
		for (n in listOf(0, 1, 3)) {
			localSounds = 0; serverSounds = 0; packets.clear()
			bazaarPress(steps = 3, hypixelPerClick = n)
			Assertions.assertEquals(1, localSounds, "Hypixel sent $n per click")
			Assertions.assertEquals(0, serverSounds, "Hypixel sent $n per click")
			MenuChainRunner.cancel("next")
			Assertions.assertEquals(1, heard)
			now += MenuPress.WINDOW_MS + 1
		}
	}

	@Test
	fun bazaarConfirmAndCancelPressesClickOnce() {

		Assertions.assertTrue(MenuChainRunner.start(chain(), owner))
		Assertions.assertFalse(MenuChainRunner.pressDriven); Assertions.assertEquals(0, heard)
		MenuPress.press { MenuPress.acted(); MenuChainRunner.approveHold() }
		Assertions.assertTrue(MenuChainRunner.pressDriven)
		now += 300; server()
		Assertions.assertEquals(1, heard)
		MenuChainRunner.cancel("reset")
		now += MenuPress.WINDOW_MS + 1
		Assertions.assertTrue(MenuChainRunner.start(chain(), owner))
		Assertions.assertEquals(1, heard)
		MenuPress.press { MenuChainRunner.cancel("cancelled") }
		Assertions.assertEquals(2, heard)
	}

	@Test
	fun followUpChainOfAPressStaysMuted() {

		var refreshDriven: Boolean? = null
		val o = object : MenuChainRunner.Owner {
			override val id = "click-sound-test"
			override fun refresh(result: MenuChain.Action) {
				MenuChainRunner.start(chain(), object : MenuChainRunner.Owner { override val id = "click-sound-test-refresh" })
				refreshDriven = MenuChainRunner.pressDriven
			}
		}
		MenuPress.press { MenuPress.acted(); MenuChainRunner.start(chain(), o) }
		MenuChainRunner.cancel("done")
		Assertions.assertEquals(true, refreshDriven)
		Assertions.assertEquals(1, heard)
		MenuChainRunner.cancel("end")

		now += MenuPress.WINDOW_MS + 1
		Assertions.assertTrue(MenuChainRunner.start(chain(), owner)); Assertions.assertFalse(MenuChainRunner.pressDriven)
		server(); Assertions.assertEquals(2, heard)
	}

	@Test
	fun ahClickWithHypixelEchoIsHeardOnce() {
		val m = FakeMenu(19)
		MenuPress.press { MenuPress.acted(); MenuClicks.click(m, m.slots[15], 0) }
		now += 250; server()
		Assertions.assertEquals(1, localSounds); Assertions.assertEquals(0, serverSounds)
	}

	@Test
	fun apexOnlyPressStillClicksOnceAndEmptyPressIsSilent() {
		MenuPress.press { MenuPress.acted() }
		Assertions.assertEquals(1, heard)
		MenuPress.press { }
		Assertions.assertEquals(1, heard)
	}

	@Test
	fun vanillaWidgetSoundCountsAsTheOneSound() {
		val click = SoundEvents.UI_BUTTON_CLICK.value().location

		MenuPress.press {
			Assertions.assertFalse(MenuPress.onLocalSound(click, now))
			MenuPress.acted()
			Assertions.assertTrue(MenuPress.onLocalSound(click, now), "second local click sound in one press is dropped")
			Assertions.assertFalse(MenuPress.onLocalSound(SoundEvents.NOTE_BLOCK_PLING.value().location, now))
		}
		Assertions.assertEquals(0, localSounds)

		Assertions.assertFalse(MenuPress.onLocalSound(click, now)); Assertions.assertFalse(MenuPress.onLocalSound(click, now))
	}

	@Test
	fun fastMultiPressCountsEachPressOnce() {
		val m = FakeMenu(7)
		repeat(5) { i -> MenuPress.press { MenuPress.acted(); MenuClicks.click(m, m.slots[10 + i], 0) }; now += 40 }
		repeat(5) { now += 30; server() }
		Assertions.assertEquals(5, localSounds)
		Assertions.assertEquals(0, serverSounds)
		Assertions.assertEquals(5, heard)

		MenuPress.press { MenuPress.press { MenuPress.acted() }; MenuPress.acted() }
		Assertions.assertEquals(6, heard)
	}

	@Test
	fun nonClickServerSoundsAndBackgroundClicksAreUntouched() {
		val m = FakeMenu(2)
		MenuPress.press { MenuPress.acted(); MenuClicks.click(m, m.slots[13], 0) }
		server(SoundEvents.NOTE_BLOCK_PLING)
		server(SoundEvents.NOTE_BLOCK_BELL)
		Assertions.assertEquals(2, otherSounds)
		now += MenuPress.WINDOW_MS + 1

		MenuClicks.autoClick(m, m.slots[20], 0); now += 200; server()
		ApexClickDelay.later { MenuClicks.autoClick(m, m.slots[21], 0) }; advance(500); server()
		Assertions.assertEquals(1, localSounds); Assertions.assertEquals(2, serverSounds)

		now += MenuPress.WINDOW_MS + 1; server()
		Assertions.assertEquals(3, serverSounds)
	}

	@Test
	fun delayedClickOfAPressKeepsItsAttribution() {
		val m = FakeMenu(4)

		MenuPress.press { MenuPress.acted(); ApexClickDelay.later { MenuClicks.autoClick(m, m.slots[11], 0) } }
		Assertions.assertTrue(packets.isEmpty())
		advance(1_400)
		Assertions.assertEquals(1, packets.size)
		advance(200); server()
		Assertions.assertEquals(1, heard)
	}

	@Test
	fun pureRules() {
		Assertions.assertTrue(MenuPress.isClickSound(SoundEvents.UI_BUTTON_CLICK.value().location))
		Assertions.assertFalse(MenuPress.isClickSound(SoundEvents.NOTE_BLOCK_PLING.value().location))
		Assertions.assertFalse(MenuPress.swallowServerSound(SoundEvents.UI_BUTTON_CLICK.value().location, now))
		Assertions.assertTrue(MenuPress.isApexScreen("dev.ashtonite365.apex.features.menus.MenuBrowseScreen"))
		Assertions.assertFalse(MenuPress.isApexScreen("net.minecraft.client.gui.screens.inventory.ContainerScreen"))
		Assertions.assertFalse(MenuPress.inPress); Assertions.assertFalse(MenuPress.attributed)
		MenuPress.carry(true) { MenuPress.acted() }
		Assertions.assertEquals(0, localSounds)
		Assertions.assertTrue(MenuPress.swallowServerSound(SoundEvents.UI_BUTTON_CLICK.value().location, now + MenuPress.WINDOW_MS))
		Assertions.assertFalse(MenuPress.swallowServerSound(SoundEvents.UI_BUTTON_CLICK.value().location, now + MenuPress.WINDOW_MS + 1))
	}

	private val root = listOf("src/main", "../src/main", "../../src/main").map { java.io.File(it).absoluteFile.normalize() }
		.first { java.io.File(it, "kotlin/util/ApexClickDelay.kt").isFile }
	private fun sources() = root.walk().filter { it.isFile && (it.name.endsWith(".kt") || it.name.endsWith(".java")) }
	private fun text(rel: String) = java.io.File(root, rel).readText()

	@Test
	fun singleLocalClickSoundSource() {
		val owners = sources().filter { it.readText().contains("UI_BUTTON_CLICK") }.map { it.name }.toSet()
		Assertions.assertEquals(setOf("MenuPress.kt"), owners)
	}

	@Test
	fun everyEntryPointOpensAPress() {
		Assertions.assertTrue(Regex("""allowMouseClick\(screen\)\.register\(ScreenMouseEvents\.AllowMouseClick \{ _, click -> MenuPress\.press \{""").containsMatchIn(text("kotlin/features/menus/MenuHost.kt")))
		Assertions.assertTrue(text("kotlin/features/menus/support/ApexViewBase.kt").contains("override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean = dev.ashtonite365.apex.features.menus.MenuPress.press {"))
		val screenPatch = text("java/dev/ashtonite365/apex/mixins/MenuPressScreenPatch.java")
		Assertions.assertTrue(screenPatch.contains("Screen;mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z") && screenPatch.contains("MenuPress.INSTANCE.press("))

		Assertions.assertTrue(text("java/dev/ashtonite365/apex/mixins/customgui/PatchGenericScreen.java").contains("return CustomGuiHost.press(apexGui, button, twice);"))
		Assertions.assertTrue(text("kotlin/util/customgui/CustomGuiHost.kt").contains("gui != null && MenuPress.press { gui.mouseClick(click, doubled) }"))
		Assertions.assertTrue(text("java/dev/ashtonite365/apex/mixins/MixinHandledScreen.java").contains("if (ContainerScreenHooks.released(apex\$self(), mouse)) result.setReturnValue(true);"))
		Assertions.assertTrue(text("kotlin/hooks/ContainerScreenHooks.kt").contains("if (MenuPress.press { HandledScreenClickEvent.publish(click).cancelled }) return true"))
		Assertions.assertTrue(text("java/dev/ashtonite365/apex/mixins/MenuPressSoundPatch.java").contains("MenuPress.INSTANCE.onLocalSound("))

		val host = text("kotlin/features/menus/MenuHost.kt")
		Assertions.assertTrue(host.indexOf("MenuPress.press {") in 0 until host.indexOf("InventoryKeyClose.handleMenuClick(screen, click,"))
		Assertions.assertTrue(text("kotlin/util/InventoryKeyClose.kt").contains("if (isApexScreen(s)) dev.ashtonite365.apex.features.menus.MenuPress.press(handle) else handle()"))

		for (f in sources().filter { it.readText().contains("allowMouseClick(") })
			Assertions.assertTrue(f.readText().contains("MenuPress.press"), "${f.name}: allowMouseClick without MenuPress.press")

		for (f in sources().filter { Regex(""":\s*(Screen|PopupHostScreen)\(""").containsMatchIn(it.readText()) })
			Assertions.assertTrue(f.path.replace('\\', '/').contains("/dev/ashtonite365/apex/") || f.readText().contains("package dev.ashtonite365.apex"), f.name)
	}

	@Test
	fun noMenuClickHandlerBypassesTheRule() {
		val reviewed = setOf(
			"PopupHostScreen.kt",
			"CustomGui.kt",
			"HotmPresets.kt",
			"HubSupport.kt", "MinionMenu.kt",
			"CraftMenu.kt", "MinionCraftMenu.kt", "MenuBrowseScreen.kt",
			"AhHub.kt",
			"WorkbenchMenus.kt",
			"HudEditorScreen.kt", "OptionSearch.kt",
			"ModulesScreen.kt",
			"StyledSettingsScreen.kt",
			"BzMenu.kt",
			"VanillaStyleDropdown.kt",
		)
		val handlers = sources().filter { Regex("""fun mouseClicked\(|fun mouseClick\(|allowMouseClick\(|HandledScreenClickEvent\)""").containsMatchIn(it.readText()) }
		val bypass = handlers.filter { !it.readText().contains("MenuPress") }.map { it.name }.toSet()
		Assertions.assertEquals(reviewed, bypass)
	}

	@Test
	fun everyClickSendCountsForItsPress() {
		Assertions.assertTrue(text("java/dev/ashtonite365/apex/mixins/SlotClickEventPatch.java").contains("MenuPress.INSTANCE.clickSent("))
		Assertions.assertTrue(text("kotlin/util/ApexClickDelay.kt").contains("MenuPress.clickSent()"))

		Assertions.assertTrue(Regex("""fun laterMs\(ms: Long, action: \(\) -> Unit\) = scheduler\(clamp\(ms\), attributed\(action\)\)""").containsMatchIn(text("kotlin/util/ApexClickDelay.kt")))

		val rawSink = sources().filter { it.readText().contains("ServerboundContainerClickPacket(") || it.readText().contains("SilentClick") }.map { it.name }.toSet()
		Assertions.assertEquals(emptySet<String>(), rawSink)

		Assertions.assertTrue(sources().none { it.readText().contains("silentAutoClick") })
	}

	@Test
	fun overARealMenuOnlyHypixelsSoundsPlay() {
		MenuPress.overRealMenu = { true }
		val m = FakeMenu(9)
		MenuPress.press { MenuPress.acted(); MenuClicks.click(m, m.slots[13], 0) }
		Assertions.assertEquals(1, packets.size)
		Assertions.assertEquals(0, localSounds)
		server()
		Assertions.assertEquals(1, serverSounds)
		Assertions.assertFalse(MenuPress.onLocalSound(SoundEvents.UI_BUTTON_CLICK.value().location, now))
	}
}
