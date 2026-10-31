// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.combat

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HotbarItemRenderEvent
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ParticleSpawnEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.UseItemEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.features.combat.AbilityCooldowns
import dev.ashtonite365.apex.features.misc.GapFeatures2
import dev.ashtonite365.apex.util.Alerts
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.TimeMark
import dev.ashtonite365.apex.util.mc.TabListReader
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.skyblock.AbilityUtils
import dev.ashtonite365.apex.util.plus.drawPlusLines
import dev.ashtonite365.apex.util.bareText

object CombatPlusMerges {
	private val ability get() = AbilityCooldowns.TConfig
	private val extras get() = GapFeatures2.TConfig
	private val atSpidersDen get() = SkyBlockSession.skyblockIsland == SkyBlockIsland.SPIDER

	private val crownLore = Regex("^Collected Coins: ([\\d,.]+[kMB]?)")

	private var lastEgg: TimeMark? = null
	private var freezeCast: TimeMark? = null
	private var veilCast = TimeMark.farPast()
	private var crownText: String? = null
	private var arachneSince: TimeMark? = null
	private var broodStage: String? = null

	private fun Player.helmetId(): String? = getItemBySlot(EquipmentSlot.HEAD).view().repoItemId?.skyblockId

	@Subscribe
	fun onHotbar(event: HotbarItemRenderEvent) {
		if (event.item.isEmpty || !ability.enabled || !ability.hotbarOverlay) return
		val abilityNames = AbilityUtils.abilitiesIn(event.item.view()).map { it.name }
		if (abilityNames.isEmpty()) return
		val seconds = AbilityCooldowns.secondsLeft(abilityNames) ?: return
		val label = seconds.toString()
		event.context.fill(event.x, event.y, event.x + 16, event.y + 16, 0x80FF0000.toInt())
		event.context.text(Client.font, label, event.x + 17 - Client.font.width(label), event.y + 9, -1, true)
	}

	@Subscribe
	fun onUse(event: UseItemEvent) {
		if (!SkyBlockSession.inSkyBlock) return
		val id = event.item.view().repoItemId?.skyblockId
		if (id == "FIRE_VEIL_WAND") veilCast = TimeMark.now()
		else if (id == "FIRE_FREEZE_STAFF" && ability.fireFreeze) freezeCast = TimeMark.now()
	}

	@Subscribe
	fun onParticle(event: ParticleSpawnEvent) {
		if (!ability.hideFireVeilParticles) return
		if (veilCast.passedTime() <= 5.seconds && event.particleEffect.type == ParticleTypes.FLAME) event.cancel()
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		if (event.tickCount % 20 != 0) return
		val me = Client.player ?: return
		crownText = if (ability.crownOfAvarice) readCrown(me) else null
		if (extras.broodmotherAlert && atSpidersDen) checkBroodmother()
	}

	private fun readCrown(me: Player): String? {
		val helmet = me.getItemBySlot(EquipmentSlot.HEAD)
		if (helmet.view().repoItemId?.skyblockId != "CROWN_OF_AVARICE") return null
		return helmet.view().loreAccordingToNbt.asSequence()
			.map { it.bareText.trim() }
			.mapNotNull { crownLore.find(it)?.groupValues?.get(1) }
			.firstOrNull()
	}

	private fun checkBroodmother() {
		val seen = PlusCombatLogic.broodmotherStage(TabListReader.currentSnapshot.body.map { it.bareText })
		val before = broodStage
		if (seen != null && before != null && seen != before && (seen == "Imminent" || seen == "Alive!"))
			Alerts.show(Component.literal("§4Broodmother: $seen"))
		if (seen != null) broodStage = seen
	}

	private fun countdown(from: TimeMark, length: Duration): Duration = length - from.passedTime()

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		val rows = ArrayList<String>()
		if (ability.chickenHead && Client.player?.helmetId() == "CHICKEN_HEAD") {
			val left = lastEgg?.let { countdown(it, 20.seconds) }
			rows += if (left != null && left.isPositive()) "§eChicken Head Timer: §b${ApexFormatters.timespanText(left)}"
			else "§eChicken Head Timer: §aNow"
		}
		freezeCast?.let { cast ->
			val left = countdown(cast, 5.seconds)
			if (left.isPositive()) rows += "§b❄ Fire Freeze in ${ApexFormatters.timespanText(left)}" else freezeCast = null
		}
		crownText?.let { rows += "§6Crown of Avarice: $it" }
		arachneSince?.let { rows += "§cArachne: §e${ApexFormatters.timespanText(it.passedTime())}" }
		if (rows.isEmpty()) return
		event.drawPlusLines(ability.extrasHud) { rows.forEach { add(it) } }
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		val text = event.bareText.trim()
		if (text == "You laid an egg!") lastEgg = TimeMark.now()
		if (extras.arachneKillTimer && atSpidersDen) arachneChat(text)
	}

	private fun arachneChat(text: String) {
		if (text == PlusCombatLogic.ARACHNE_CALLING || text == PlusCombatLogic.ARACHNE_CRYSTAL) arachneSince = TimeMark.now()
		if (!PlusCombatLogic.isArachneDown(text)) return
		val since = arachneSince ?: return
		Client.showChat(Component.literal("§eArachne took §b${ApexFormatters.timespanText(since.passedTime())}§e to kill."))
		arachneSince = null
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		if (!extras.arachneHighlight || !atSpidersDen) return
		val me = Client.player ?: return
		val world = Client.level ?: return
		WorldDraw.drawInWorld(event) {
			val labels = world.getEntitiesOfClass(ArmorStand::class.java, me.boundingBox.inflate(40.0)) { stand ->
				val n = stand.customName?.bareText
				n != null && ("Arachne" in n || "Broodmother" in n)
			}
			for (label in labels) {
				val body = world.getEntitiesOfClass(LivingEntity::class.java, label.boundingBox.inflate(1.5, 3.0, 1.5)) {
					it !== me && it !is ArmorStand
				}.minByOrNull { it.distanceToSqr(label) } ?: continue
				box(body.boundingBox, 0x50FF3030)
			}
		}
	}

	fun shouldHide(entity: Entity): Boolean {
		if (!extras.arachneHideMinisNametags || !atSpidersDen) return false
		val label = (entity as? ArmorStand)?.customName?.bareText ?: return false
		return listOf("Arachne's Brood", "Arachne's Keeper").any { it in label }
	}

	@Subscribe
	fun onWorld(event: WorldReadyEvent) {
		arachneSince = null
		freezeCast = null
		broodStage = null
	}
}
