// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.fishing

import kotlin.time.Duration.Companion.seconds
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.decoration.ArmorStand
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.features.fishing.FishingHelper
import dev.ashtonite365.apex.features.fishing.SeaCreatureBook
import dev.ashtonite365.apex.util.Alerts
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.bareText

object PlusSeaCreatureSettings {
	@Config
	object TConfig : ManagedConfig("sea-creature-settings", Category.FISHING) {
		val titleNames by string("title-names") { "" }
		val soundNames by string("sound-names") { "" }

		val highlightNames by string("highlight-names") { "" }
	}

	private const val HIGHLIGHT_RANGE = 30.0
	private const val HIGHLIGHT_COLOUR = 0x5500AAFF

	fun names(list: String): Set<String> {
		val out = LinkedHashSet<String>()
		for (part in list.split(',')) part.trim().lowercase().takeIf { it.isNotEmpty() }?.let(out::add)
		return out
	}

	fun creatureOf(msg: String): String? = SeaCreatureBook.caughtBy(msg)?.name

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!SkyBlockSession.inSkyBlock) return
		val titleFor = names(TConfig.titleNames)
		val soundFor = names(TConfig.soundNames)
		if (titleFor.isEmpty() && soundFor.isEmpty()) return
		val creature = creatureOf(event.bareText.trim()) ?: return
		val id = creature.lowercase()
		if (id in titleFor) Alerts.show(Component.literal("§b§l$creature"), 2.seconds, sound = false)
		if (id in soundFor) Client.player?.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, 1.5f)
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		if (!FishingHelper.TConfig.highlightRareCreatures || !SkyBlockSession.inSkyBlock) return
		val listed = names(TConfig.highlightNames)
		if (listed.isEmpty()) return
		val player = Client.player ?: return
		val level = Client.level ?: return
		val matches = level.getEntitiesOfClass(ArmorStand::class.java, player.boundingBox.inflate(HIGHLIGHT_RANGE)) { stand ->
			val label = stand.customName?.bareText?.lowercase()
			label != null && listed.any { label.contains(it) }
		}
		if (matches.isEmpty()) return
		WorldDraw.drawInWorld(event) {
			for (stand in matches) box(stand.boundingBox.move(0.0, -2.0, 0.0).inflate(0.5, 1.0, 0.5), HIGHLIGHT_COLOUR)
		}
	}
}
