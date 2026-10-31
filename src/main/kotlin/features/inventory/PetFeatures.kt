package dev.ashtonite365.apex.features.inventory

import org.joml.Vector2i
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.util.StringRepresentable
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.ProfileSwitchEvent
import dev.ashtonite365.apex.events.SlotClickEvent
import dev.ashtonite365.apex.events.SlotRenderEvents
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.formattedString
import dev.ashtonite365.apex.util.hud.HudStyle
import dev.ashtonite365.apex.util.mc.NeedsComponents
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.keepable
import dev.ashtonite365.apex.util.petBlob
import dev.ashtonite365.apex.util.render.guiSprite
import dev.ashtonite365.apex.util.skyblock.TabListAPI
import dev.ashtonite365.apex.util.itemUuid
import dev.ashtonite365.apex.util.bareText
import dev.ashtonite365.apex.util.withColor

object PetFeatures {
	@Config
	object TConfig : ManagedConfig("pets", Category.INVENTORY) {
		val markActivePet by toggle("highlight-pet") { true }
		val candyUsed by toggle("candy-used") { false }
		val nametagHideLevel by toggle("nametag-hide-level") { false }
		val nametagHideMaxLevel by toggle("nametag-hide-max-level") { false }
		val hudOn by toggle("pet-overlay") { false }
		val petOverlayHud by position("pet-overlay-hud", 80, 10) { Vector2i() }
		val hudLook by choice("pet-overlay-hud-style") { PetOverlayHudStyles.PLAIN_NO_BACKGROUND }
	}

	enum class PetOverlayHudStyles(val coloured: Boolean, val boxed: Boolean, val text: Boolean = true) : StringRepresentable {
		PLAIN_NO_BACKGROUND(false, false),
		COLOUR_NO_BACKGROUND(true, false),
		PLAIN_BACKGROUND(false, true),
		COLOUR_BACKGROUND(true, true),
		ICON_ONLY(false, false, text = false);

		override fun getSerializedName(): String = name
	}

	val tracker = PetTracker()

	fun currentPet(): PetRecord? = tracker.current()

	private val PETS_MENU = Regex("""(?:\(\d+/\d+\)\s)?Pets""")

	private fun inPetsMenu() = Client.screenTitle?.let(PETS_MENU::matches) == true

	private fun uuidOf(stack: ItemStack) = stack.view().itemUuid?.toString()

	@Subscribe
	fun onProfileSwitch(event: ProfileSwitchEvent) = tracker.forget()

	@Subscribe
	fun scanPetsMenuSlot(event: SlotRenderEvents.Before) {
		if (!inPetsMenu()) return
		val shown = event.slot.item
		val active = shown.view().petBlob?.equipped == true
		if (!shown.isEmpty) tracker.menuItemShown(uuidOf(shown), active) { PetReadings.fromMenuItem(shown.keepable()) }
		if (active && TConfig.markActivePet)
			event.context.guiSprite(Apex.identifier("selected_pet_background"), event.slot.x, event.slot.y, 16, 16)
	}

	@Subscribe
	fun noteSummonOrDespawn(event: SlotClickEvent) {
		if (!inPetsMenu() || event.slot.container is Inventory) return
		val info = event.stack.view().petBlob ?: return

		tracker.menuItemClicked(uuidOf(event.stack), info.equipped == true, event.button) { PetReadings.fromMenuItem(event.stack.keepable()) }
	}

	@Subscribe
	fun onChatEvent(event: ProcessChatEvent) = tracker.chatLine(event.text.formattedString())

	@OptIn(NeedsComponents::class)
	@Subscribe
	fun onRenderHud(event: HudRenderEvent) {

		if (!TConfig.hudOn || !SkyBlockSession.inSkyBlock || SkyBlockSession.skyblockIsland == SkyBlockIsland.RIFT) return
		val style = TConfig.hudLook
		val pick = tracker.pickForHud(PetReadings.fromTab(TabListAPI.getWidgetLines(TabListAPI.WidgetName.PET)))
		val pose = event.context.pose()
		when (pick) {
			PetTracker.HudPick.Hidden -> return
			PetTracker.HudPick.NothingKnown -> {
				pose.pushMatrix()
				TConfig.petOverlayHud.applyTextTransformations(pose)
				drawText(event, PetHudText.nothingKnown, style)
				pose.popMatrix()
			}
			is PetTracker.HudPick.Show -> {
				pose.pushMatrix()
				TConfig.petOverlayHud.applyTextTransformations(pose)
				drawText(event, if (style.text) PetHudText.lines(pick.pet, style.coloured) else emptyList(), style)
				pose.pushMatrix()
				pose.translate(-0.5F, -0.5F)
				pose.scale(2f, 2f)
				event.context.item(pick.pet.icon.value.current(), 0, 0)
				pose.popMatrix()
				pose.popMatrix()
			}
		}
	}

	private fun drawText(event: HudRenderEvent, lines: List<Component>, style: PetOverlayHudStyles) {
		val font = Client.font
		if (style.boxed) {

			var boxText = 0
			for (line in lines) if (font.width(line) > boxText) boxText = font.width(line.bareText)
			val textHeight = (font.lineHeight * lines.size).coerceAtLeast(32)
			HudStyle.drawBackground(event.context, 0, -3, 40 + boxText, textHeight + 2, HudStyle.Override(backgroundOpacity = 50))
		}
		lines.forEachIndexed { row, line ->
			event.context.text(font, line.copy().withColor(ChatFormatting.GRAY), 36, font.lineHeight * row,
				HudStyle.textColour(), HudStyle.TConfig.textShadow)
		}
	}
}
