package dev.ashtonite365.apex.features.mining

import me.shedaniel.math.Rectangle
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds
import net.minecraft.world.level.block.Blocks
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.events.ChestInventoryUpdateEvent
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.events.ScreenChangeEvent
import dev.ashtonite365.apex.events.SlotRenderEvents
import dev.ashtonite365.apex.util.ClipboardUtils
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.ShareCodes
import dev.ashtonite365.apex.util.TimeMark
import dev.ashtonite365.apex.util.accessors.castAccessor
import dev.ashtonite365.apex.util.customgui.CustomGui
import dev.ashtonite365.apex.util.customgui.customGui
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.CommonTextures
import dev.ashtonite365.apex.util.mc.SlotUtils.clickRightMouseButton
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.displayNameAccordingToNbt
import dev.ashtonite365.apex.util.render.guiSprite
import dev.ashtonite365.apex.util.bareText
import dev.ashtonite365.apex.util.useMatch

object HotmPresets {
	val SHARE_PREFIX: String = "FIRMHOTM/"

	@Serializable
	data class HotmPreset(val perks: List<PerkPreset>)

	@Serializable
	data class PerkPreset(
		val perkName: String,
	)

	var hotmCommandSent: TimeMark = TimeMark.farPast()
	val hotmInventoryName: String = "Heart of the Mountain"
	val tierRegex = "Tier (?<tier>[0-9]+)".toPattern()

	var highlightedPerks: Set<String> = setOf()

	private val unlockedPerkItems: Set<Item> by lazy { setOf(Items.DIAMOND, Items.EMERALD, Blocks.EMERALD_BLOCK.asItem()) }

	private val ALL_TIERS = 1..10
	private const val OFF_SCREEN = -10000

	@Subscribe
	fun onScreenOpen(event: ScreenChangeEvent): Unit {
		val opened = event.new
		if (opened?.title?.bareText != hotmInventoryName) return
		val container = opened as? AbstractContainerScreen<*> ?: return
		val carried = (event.old as? AbstractContainerScreen<*>)?.customGui as? HotmScrollPrompt
		when {

			carried != null -> {
				container.customGui = carried
				carried.setNewScreen(container)
			}
			hotmCommandSent.passedTime() > 5.seconds -> {}
			else -> {
				hotmCommandSent = TimeMark.farPast()
				container.customGui = HotmScrollPrompt(container)
			}
		}
	}

	class HotmScrollPrompt(var screen: AbstractContainerScreen<*>) :
		CustomGui() {
		var bounds = Rectangle()
		var hasScrolled = false
		var hasAll = false
		val coveredRows: MutableSet<Int> = mutableSetOf()
		val unlockedPerks: MutableSet<String> = mutableSetOf()
		val allRows: Set<Int> = ALL_TIERS.toSet()

		fun setNewScreen(next: AbstractContainerScreen<*>): Unit {
			screen = next
			onInit().also { hasScrolled = false }
		}

		private fun statusKey(): String = when {
			hasAll -> "apex.hotmpreset.copied"
			hasScrolled -> "apex.hotmpreset.scrolled"
			else -> "apex.hotmpreset.scrollprompt"
		}

		override fun render(
			drawContext: GuiGraphicsExtractor, delta: Float, mouseX: Int, mouseY: Int,
		): Unit {
			val area = bounds
			drawContext.guiSprite(CommonTextures.genericWidget(), area.x, area.y, area.width, area.height)
			drawContext.centeredText(Client.font, Component.translatable(statusKey()), area.centerX, area.centerY - 5, -1)
		}

		override fun mouseClick(
			click: MouseButtonEvent,
			doubled: Boolean,
		): Boolean {
			if (hasScrolled) return super.mouseClick(click, doubled)
			val menu = screen.menu
			menu.getSlot(8).clickRightMouseButton(menu)
			hasScrolled = true
			return true
		}

		override fun shouldDrawForeground(): Boolean = false

		override fun getBounds(): List<Rectangle> = listOf(bounds)

		override fun onInit(): Unit {
			val panel = Rectangle(screen.width / 2 - 150, screen.height / 2 - 100, 300, 200)
			bounds = panel
			with(screen.castAccessor()) {
				x_Apex = panel.x
				y_Apex = panel.y
				backgroundWidth_Apex = panel.width
				backgroundHeight_Apex = panel.height
			}
		}

		override fun moveSlot(slot: Slot): Unit {
			slot.x = OFF_SCREEN
		}

		fun onNewItems(event: ChestInventoryUpdateEvent): Unit {
			val chest = screen.menu as? ChestMenu ?: return
			chest.slots.asSequence().filter { it.container !is Inventory }.map { it.item }.forEach { stack ->
				val label = stack.view().displayNameAccordingToNbt.bareText
				tierRegex.useMatch(label) { coveredRows += group("tier").toInt() }
				if (stack.item in unlockedPerkItems) unlockedPerks += label
			}
			if (coveredRows != allRows) return
			val preset = HotmPreset(unlockedPerks.map(::PerkPreset))
			ClipboardUtils.setTextContent(ShareCodes.toShareCode(SHARE_PREFIX, preset))
			hasAll = true
		}
	}

	@Subscribe
	fun onSlotUpdates(event: ChestInventoryUpdateEvent): Unit {
		val prompt = (event.inventory as? AbstractContainerScreen<*>)?.customGui as? HotmScrollPrompt ?: return
		prompt.onNewItems(event)
	}

	@Subscribe
	fun resetOnScreen(event: ScreenChangeEvent): Unit {
		val title = event.new?.title?.bareText ?: return
		if (title != hotmInventoryName) highlightedPerks = emptySet()
	}

	@Subscribe
	fun onSlotRender(event: SlotRenderEvents.Before): Unit {
		if (Client.screenTitle != hotmInventoryName) return
		val perk = event.slot.item.view().displayNameAccordingToNbt.bareText
		if (perk in highlightedPerks) event.highlight(Apex.identifier("hotm_perk_preset"))
	}

	@Subscribe
	fun onCommand(event: CommandEvent.SubCommand): Unit {
		event.addSubcommand("exporthotm", ManagedConfig.Category.MINING) { onRun { source.sendFeedback(startExport()) } }
		event.addSubcommand("importhotm", ManagedConfig.Category.MINING) {
			onRun {
				val imported = importFromClipboard()
				source.sendFeedback(Component.translatable(if (imported) "apex.hotmpreset.okayimport" else "apex.hotmpreset.failedimport"))
				if (imported) openHotm()
			}
		}
	}

	private fun openHotm() = Client.runCommand("hotm")

	private fun startExport(): Component {
		hotmCommandSent = TimeMark.now()
		openHotm()
		return Component.translatable("apex.hotmpreset.openinghotm")
	}

	private fun importFromClipboard(): Boolean {
		val imported = ShareCodes.fromShareCode<HotmPreset>(SHARE_PREFIX, ClipboardUtils.getTextContents())
			?: return false
		highlightedPerks = imported.perks.mapTo(HashSet()) { it.perkName }
		return true
	}
}
