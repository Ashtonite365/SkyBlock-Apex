package dev.ashtonite365.apex.features.events.anniversity

import java.util.Optional
import kotlin.jvm.optionals.getOrNull
import net.minecraft.world.entity.player.Player
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.TextColor
import net.minecraft.util.ARGB
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.EntityRenderTintEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.render.TintOverlay
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.skyblock.SkyBlockItems

object CenturyRaffleFeatures {
	@Config
	object TConfig :
		ManagedConfig("centuryraffle", Category.EVENTS) {
		val highlightPlayersForSlice: Boolean by toggle("highlight-cake-players") { true }
	}

	val cakeIcon: String = "⛃"

	data class CakeTeam(val id: ItemId, val formatting: ChatFormatting) {
		val searchedTextRgb: Int = requireNotNull(TextColor.fromLegacyFormat(formatting)).value
		val brightenedRgb: Int = ARGB.opaque(searchedTextRgb)
		val tintOverlay: TintOverlay by lazy { TintOverlay().tinted(brightenedRgb) }
	}

	val cakeColors: List<CakeTeam> = listOf(
		SkyBlockItems.SLICE_OF_BLUEBERRY_CAKE to ChatFormatting.BLUE,
		SkyBlockItems.SLICE_OF_CHEESECAKE to ChatFormatting.YELLOW,
		SkyBlockItems.SLICE_OF_GREEN_VELVET_CAKE to ChatFormatting.GREEN,
		SkyBlockItems.SLICE_OF_RED_VELVET_CAKE to ChatFormatting.RED,
		SkyBlockItems.SLICE_OF_STRAWBERRY_SHORTCAKE to ChatFormatting.LIGHT_PURPLE,
	).map { (slice, colour) -> CakeTeam(slice, colour) }

	val sliceToColor: Map<ItemId, CakeTeam> = cakeColors.associateBy(CakeTeam::id)

	private fun iconStyle(name: Component): Style? =
		name.visit({ style, segment -> if (segment == cakeIcon) Optional.of(style) else Optional.empty() }, Style.EMPTY)
			.getOrNull()

	@Subscribe
	fun onEntityRender(event: EntityRenderTintEvent): Unit {
		if (TConfig.highlightPlayersForSlice.not()) return
		val team = sliceToColor[Client.mainHandStack.view().repoItemId] ?: return
		val player = event.entity as? Player ?: return
		val style = iconStyle(player.feedbackDisplayName) ?: return
		if (style.color?.value == team.searchedTextRgb) event.renderState.overlayTexture_apex = team.tintOverlay
	}
}
