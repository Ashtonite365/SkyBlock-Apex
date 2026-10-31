package dev.ashtonite365.apex.util

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.hud.HudStyle

object Alerts {
	@Config
	object TConfig : ManagedConfig("alerts", Category.GENERAL) {
		val titles by toggle("titles") { true }
		val sounds by toggle("sounds") { true }
		val titleScale by integer("title-scale", 100, 400) { 200 }
	}

	private var current: Component? = null
	private var shownAt = TimeMark.farPast()
	private var duration: Duration = 2.seconds

	fun show(title: Component, duration: Duration = 2.seconds, sound: Boolean = true) {
		if (TConfig.titles) {
			current = title
			shownAt = TimeMark.now()
			this.duration = duration
		}
		if (sound && TConfig.sounds) CommonSoundEffects.playDing()
	}

	fun sound() {
		if (TConfig.sounds) CommonSoundEffects.playDing()
	}

	fun clear() {
		current = null
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		val title = current ?: return
		if (shownAt.passedTime() > duration) {
			current = null
			return
		}
		val ctx = event.context
		val scale = TConfig.titleScale / 100F
		ctx.pose().pushMatrix()
		ctx.pose().translate(Client.window.guiScaledWidth / 2F, Client.window.guiScaledHeight / 3F)
		ctx.pose().scale(scale, scale)
		val width = Client.font.width(title)
		ctx.text(Client.font, title, -width / 2, -Client.font.lineHeight / 2, HudStyle.textColour(), HudStyle.TConfig.textShadow)
		ctx.pose().popMatrix()
	}
}
