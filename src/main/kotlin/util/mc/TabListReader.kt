package dev.ashtonite365.apex.util.mc

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import java.util.Optional
import org.jetbrains.annotations.TestOnly
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Hud
import net.minecraft.client.gui.components.PlayerTabOverlay
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.world.scores.PlayerTeam
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.mixins.accessor.AccessorPlayerListHud
import dev.ashtonite365.apex.util.intoOptional

object TabListReader {

	data class TabSnapshot(val header: Optional<Component>, val footer: Optional<Component>, val body: List<Component>) {
		companion object {
			private val text: Codec<Component> = ComponentSerialization.CODEC

			val CODEC: Codec<TabSnapshot> = RecordCodecBuilder.create { built ->
				built.group(
					text.optionalFieldOf("header").forGetter(TabSnapshot::header),
					text.optionalFieldOf("footer").forGetter(TabSnapshot::footer),
					text.listOf().fieldOf("body").forGetter(TabSnapshot::body),
				).apply(built, ::TabSnapshot)
			}

			val EMPTY = TabSnapshot(Optional.empty(), Optional.empty(), listOf())
		}
	}

	@set:TestOnly
	var cachedSnapshot: TabSnapshot? = null

	val currentSnapshot: TabSnapshot
		get() = cachedSnapshot ?: takeSnapshot().also { cachedSnapshot = it }

	@Subscribe
	fun dropSnapshot(event: TickEvent) {
		cachedSnapshot = null
	}

	fun PlayerTabOverlay.asAccessor(): AccessorPlayerListHud = this as AccessorPlayerListHud

	private fun lineFor(info: PlayerInfo): Component =
		info.tabListDisplayName ?: PlayerTeam.formatNameForTeam(info.team, Component.literal(info.profile.name()))

	private fun takeSnapshot(): TabSnapshot {
		val game: Minecraft = Minecraft.getInstance() ?: return TabSnapshot.EMPTY
		val hud: Hud = game.gui?.hud ?: return TabSnapshot.EMPTY

		if (game.player?.connection == null) return TabSnapshot.EMPTY
		val overlay = hud.tabList.asAccessor()
		return TabSnapshot(
			overlay.header_apex.intoOptional(),
			overlay.footer_apex.intoOptional(),
			overlay.collectPlayerEntries_apex().map(::lineFor),
		)
	}
}
