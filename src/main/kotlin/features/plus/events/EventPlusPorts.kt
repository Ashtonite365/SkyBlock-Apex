// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.events

import kotlin.time.Duration.Companion.seconds
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.sounds.SoundEvents
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.SoundReceiveEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.features.diana.DianaWaypoints
import dev.ashtonite365.apex.features.inventory.PetFeatures
import dev.ashtonite365.apex.util.Alerts
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.TimeMark
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.skyblock.SkyBlockItems
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import net.minecraft.util.StringRepresentable

object EventPlusPorts {
	@Config
	object TConfig : ManagedConfig("plus-events", Category.EVENTS) {
		val eggSoundMode by choice("hoppity-egg-sound") { EggSoundMode.NO_MODIFICATION }
		val hoppityCompactChat by toggle("hoppity-compact-chat") { false }
		val hoppityCallWarning by toggle("hoppity-call-warning") { false }
		val mythicRabbitWarning by toggle("mythic-rabbit-warning") { false }
	}

	enum class EggSoundMode : StringRepresentable {
		NO_MODIFICATION, MUTE;

		override fun getSerializedName() = name.lowercase()
	}

	private val spadeIds = setOf(SkyBlockItems.ANCESTRAL_SPADE, SkyBlockItems.ARCHAIC_SPADE, SkyBlockItems.DEIFIC_SPADE)

	private fun holdingSpade(): Boolean = Client.player?.mainHandItem?.view()?.repoItemId in spadeIds

	private fun onRitual(): Boolean {
		if (SkyBlockSession.skyblockIsland != SkyBlockIsland.HUB) return false
		val inv = Client.player?.inventory ?: return false
		return inv.hasAnyMatching { it.view().repoItemId in spadeIds }
	}

	fun isBuggedSpadeSound(soundId: String, pitch: Float, volume: Float, x: Double, y: Double, z: Double): Boolean {
		if (!soundId.startsWith("music")) return false
		val atOrigin = x == 0.0 && y == 0.0 && z == 0.0
		val plain = pitch == 1f && volume == 1f
		return !(plain && atOrigin)
	}

	private val eggMenuTitle = Regex("^Chocolate (Breakfast|Lunch|Dinner|Brunch|Déjeuner|Supper) Egg.*")
	private fun eggMenuOpen(): Boolean {
		val title = Client.screenTitle ?: return false
		return eggMenuTitle.matches(title)
	}

	private var eggMenuSeen = TimeMark.farPast()

	private fun isEggMenuBit(event: SoundReceiveEvent): Boolean {
		if (event.sound.value() != SoundEvents.NOTE_BLOCK_BIT.value() || event.volume != 1f) return false
		val menuRecent = eggMenuOpen() || eggMenuSeen.passedTime() < 2.seconds
		if (!menuRecent) return false
		val away = Client.player?.position()?.distanceTo(event.position) ?: 99.0
		return away < 2.0
	}

	@Subscribe
	fun onSound(event: SoundReceiveEvent) {
		val path = event.sound.unwrapKey().orElse(null)?.identifier()?.path ?: return
		val pos = event.position
		val spadeNoise = DianaWaypoints.TConfig.muteBuggedSpade && onRitual() &&
			isBuggedSpadeSound(path, event.pitch, event.volume, pos.x, pos.y, pos.z)
		if (spadeNoise) {
			event.cancel()
			return
		}
		if (TConfig.eggSoundMode == EggSoundMode.MUTE && isEggMenuBit(event)) event.cancel()
	}

	private val callStart = Regex("^✆ Hoppity ✆.*")
	private val eggFound = Regex("^HOPPITY'S HUNT You found an? .*Egg.*!$")
	private var rabbitNagAt = TimeMark.farPast()

	fun needsMythicRabbit(petName: String?, rarity: String?): Boolean {
		val isRabbit = petName?.contains("Rabbit") == true
		return !isRabbit || rarity != "MYTHIC"
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!SkyBlockSession.inSkyBlock) return
		val line = event.bareText.trim()
		if (TConfig.hoppityCallWarning && callStart.matches(line)) Alerts.show(Component.literal("§aHoppity Calling!"))
		if (!TConfig.mythicRabbitWarning || !eggFound.matches(line)) return
		if (rabbitNagAt.passedTime() <= 30.seconds) return
		val pet = PetFeatures.currentPet() ?: return
		if (!needsMythicRabbit(pet.name, pet.rarity.name)) return
		rabbitNagAt = TimeMark.now()
		Alerts.show(Component.literal("§cNo Rabbit Pet!"))
		Client.showChat(Component.literal("§e[SkyBlock Apex] Use a §dMythic Rabbit Pet §efor more chocolate!"))
	}

	private object Griffin {
		var seenThisIsland = false
		var warnedAt = TimeMark.farPast()
		var island: SkyBlockIsland? = null
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		if (eggMenuOpen()) eggMenuSeen = TimeMark.now()
		if (event.tickCount % 10 != 0) return
		val here = SkyBlockSession.skyblockIsland
		if (here != Griffin.island) {
			Griffin.island = here
			Griffin.seenThisIsland = false
		}
		if (!DianaWaypoints.TConfig.griffinPetWarning) return
		if (!onRitual() || !holdingSpade()) return
		val pet = PetFeatures.currentPet() ?: return
		if ("Griffin" in pet.name) {
			Griffin.seenThisIsland = true
			return
		}
		if (Griffin.seenThisIsland || Griffin.warnedAt.passedTime() < 30.seconds) return
		Griffin.warnedAt = TimeMark.now()
		Alerts.show(Component.literal("§cGriffin Pet!"))
		val hint = Component.literal("§e[SkyBlock Apex] Reminder to use a Griffin pet for Mythological Ritual! §7(click to open pets)")
			.withStyle { it.withClickEvent(ClickEvent.RunCommand("/pets")).withHoverEvent(HoverEvent.ShowText(Component.literal("/pets"))) }
		Client.showChat(hint)
	}
}
