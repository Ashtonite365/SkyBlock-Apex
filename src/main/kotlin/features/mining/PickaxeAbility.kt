package dev.ashtonite365.apex.features.mining

import io.github.notenoughupdates.moulconfig.ChromaColour
import java.util.regex.Pattern
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.util.StringRepresentable
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.ItemStack
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.ProfileSwitchEvent
import dev.ashtonite365.apex.events.SlotClickEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.features.timers.Timers
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.DurabilityBarEvent
import dev.ashtonite365.apex.util.SHORT_NUMBER_FORMAT
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.TIME_PATTERN
import dev.ashtonite365.apex.util.TimeMark
import dev.ashtonite365.apex.util.bareText
import dev.ashtonite365.apex.util.customDataTag
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.displayNameAccordingToNbt
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.parseShortNumber
import dev.ashtonite365.apex.util.parseTimePattern
import dev.ashtonite365.apex.util.render.gui.pip.RenderCircleProgressRenderer
import dev.ashtonite365.apex.util.render.interpolate
import dev.ashtonite365.apex.util.skyblock.AbilityUtils
import dev.ashtonite365.apex.util.skyblock.DungeonUtil
import dev.ashtonite365.apex.util.skyblock.ItemType
import dev.ashtonite365.apex.util.toShedaniel
import dev.ashtonite365.apex.util.tr
import dev.ashtonite365.apex.util.useMatch

object PickaxeAbility {

	const val identifier = "pickaxe-info"

	enum class ShowOnTools(val wireName: String) : StringRepresentable {
		ALL("all"),
		PICKAXES_AND_DRILLS("pick-and-drill"),
		DRILLS("drills"),
		;

		override fun getSerializedName() = wireName

		fun matches(type: ItemType): Boolean = when (type) {
			ItemType.DRILL -> true
			ItemType.PICKAXE -> this != DRILLS
			ItemType.AXE, ItemType.SHOVEL -> this == ALL
			else -> false
		}
	}

	@Config
	object TConfig : ManagedConfig(identifier, Category.MINING) {
		val cooldownEnabled: Boolean by toggle("ability-cooldown") { true }
		val disableInDungeons: Boolean by toggle("disable-in-dungeons") { true }
		val showOnTools: ShowOnTools by choice("show-on-tools") { ShowOnTools.PICKAXES_AND_DRILLS }
		val cooldownScale: Int by integer("ability-scale", 16, 64) { 16 }
		val cooldownColour: ChromaColour by colour("ability-colour") { ChromaColour.fromStaticRGB(187, 54, 44, 128) }
		val cooldownReadyToast: Boolean by toggle("ability-cooldown-toast") { false }
		val drillFuelBar: Boolean by toggle("fuel-bar") { true }
	}

	var lobbyJoinTime: TimeMark = TimeMark.farPast()

	var lastUsage: MutableMap<String, TimeMark> = mutableMapOf()

	var abilityOverride: String? = null

	var defaultAbilityDurations: MutableMap<String, Duration> = listOf(
		"Mining Speed Boost" to 120, "Pickobulus" to 110, "Gemstone Infusion" to 140,
		"Hazardous Miner" to 140, "Maniac Miner" to 59, "Vein Seeker" to 60,
	).associateTo(LinkedHashMap()) { (ability, secs) -> ability to secs.seconds }

	val pickaxeTypes: Set<ItemType> = setOf(ItemType.PICKAXE, ItemType.DRILL, ItemType.GAUNTLET)

	val abilityUsePattern: Pattern = Pattern.compile("You used your (?<name>.*) Pickaxe Ability!")
	val fuelPattern: Pattern = Pattern.compile("Fuel: .*/(?<maxFuel>$SHORT_NUMBER_FORMAT)")
	val pickaxeAbilityCooldownPattern: Pattern =
		Pattern.compile("Your pickaxe ability is on cooldown for (?<remainingCooldown>$TIME_PATTERN)\\.")
	val nowAvailable: Pattern = Pattern.compile("(?<name>[a-zA-Z0-9 ]+) is now available!")
	val cooldownPattern: Pattern = Pattern.compile("Cooldown: (?<cooldown>$TIME_PATTERN)")
	val abilitySwitchPattern: Pattern =
		Pattern.compile("You selected (?<ability>.*) as your Pickaxe Ability\\. " + "This ability will apply to all of your pickaxes!")

	private val circleTexture = Identifier.fromNamespaceAndPath("apex", "textures/gui/circle.png")

	fun getCooldownPercentage(name: String, cooldown: Duration): Double {
		val usedAt = lastUsage[name] ?: return 1.0
		val freshMineshaft = SkyBlockSession.skyblockIsland == SkyBlockIsland.MINESHAFT && usedAt < lobbyJoinTime
		if (freshMineshaft || !cooldown.isPositive()) return 1.0
		val sinceUse = usedAt.passedTime()
		return (sinceUse / cooldown).coerceIn(0.0, 1.0)
	}

	private fun heldAbilityName(): String? =
		Client.player?.mainHandItem?.let { getCooldownFromLore(it)?.name }

	@Subscribe
	fun onSlotClick(event: SlotClickEvent) {
		if (Client.screenTitle?.contains("Heart of the Mountain") != true) return
		val item = event.stack.view()
		val cooldown = item.loreAccordingToNbt.firstNotNullOfOrNull { line ->
			cooldownPattern.useMatch(line.bareText) { parseTimePattern(group("cooldown")) }
		} ?: return
		defaultAbilityDurations[item.displayNameAccordingToNbt.bareText.trim()] = cooldown
	}

	@Subscribe
	fun onDurabilityBar(event: DurabilityBarEvent) {
		if (!TConfig.drillFuelBar) return
		val item = event.item.view()
		if (ItemType.fromItemStack(item) != ItemType.DRILL) return
		val lore = item.loreAccordingToNbt
		val capacity = lore.firstNotNullOfOrNull { line ->
			fuelPattern.useMatch(line.bareText) { parseShortNumber(group("maxFuel")) }
		} ?: return
		if (capacity <= 0.0) return
		val fuel = item.customDataTag.getInt("drill_fuel").getOrNull() ?: return
		val fill = (fuel / capacity).toFloat().coerceIn(0F, 1F)
		val empty = DyeColor.RED.toShedaniel()
		val full = DyeColor.LIME.toShedaniel()
		event.barOverride = DurabilityBarEvent.DurabilityBar(interpolate(empty, full, fill), fill)
	}

	@Subscribe
	fun onChatMessage(event: ProcessChatEvent) {
		val line = event.bareText
		abilityUsePattern.useMatch(line) {
			val used = group("name")
			lastUsage[used] = TimeMark.now()
			abilityOverride = used
		}
		pickaxeAbilityCooldownPattern.useMatch(line) {
			val remaining = parseTimePattern(group("remainingCooldown"))
			val current = abilityOverride ?: heldAbilityName() ?: return@useMatch
			val full = defaultAbilityDurations[current] ?: return@useMatch
			lastUsage[current] = TimeMark.ago(full - remaining)
		}
		nowAvailable.useMatch(line) {
			val ready = group("name")
			lastUsage[ready] = TimeMark.farPast()
			if (TConfig.cooldownReadyToast) {
				SystemToast.add(
					Client.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
					tr("apex.pickaxe.ability-ready", "Pickaxe ability is ready!"),
					tr("apex.pickaxe.ability-ready.desc", "Your pickaxe ability can be used again.").append(Component.literal(" ($ready)")),
				)
			}
		}
		abilitySwitchPattern.useMatch(line) {
			abilityOverride = group("ability")
		}
	}

	@Subscribe
	fun newLobby(event: WorldReadyEvent) {
		abilityOverride = null
		lobbyJoinTime = TimeMark.now()
	}

	@Subscribe
	fun profileChanged(event: ProfileSwitchEvent) {
		val joined = lobbyJoinTime
		lastUsage.entries.removeIf { it.value < joined }
	}

	data class PickaxeAbilityData(val name: String, val cooldown: Duration)

	fun getCooldownFromLore(itemStack: ItemStack): PickaxeAbilityData? {
		val item = itemStack.view()
		if (item.loreAccordingToNbt.none { "Breaking Power" in it.bareText }) return null
		val first = AbilityUtils.abilitiesIn(item).firstOrNull { it.cooldown != null } ?: return null
		val cooldown = first.cooldown ?: return null
		defaultAbilityDurations[first.name] = cooldown
		return PickaxeAbilityData(first.name, cooldown)
	}

	@Subscribe
	fun onCollectTimers(event: Timers.TimerCollectEvent) {
		val fromLore = Client.player?.mainHandItem?.let { getCooldownFromLore(it) }
		val ability = abilityOverride ?: fromLore?.name ?: return
		val cooldown = defaultAbilityDurations[ability] ?: fromLore?.cooldown ?: return
		val left = cooldown * (1.0 - getCooldownPercentage(ability, cooldown))
		event.cooldown("pickaxe-ability", "Pickaxe Cooldown ($ability)", left)
	}

	@Subscribe
	fun renderHud(event: HudRenderEvent) {
		val hiddenHere = TConfig.disableInDungeons && DungeonUtil.isInDungeonIsland
		if (hiddenHere || !TConfig.cooldownEnabled) return
		val stack = Client.player?.mainHandItem ?: return
		if (stack.isEmpty) return
		val heldKind = ItemType.fromItemStack(stack.view()) ?: return
		if (!TConfig.showOnTools.matches(heldKind)) return
		val ability = abilityOverride ?: getCooldownFromLore(stack)?.name ?: return
		val cooldown = defaultAbilityDurations[ability] ?: return
		val progress = getCooldownPercentage(ability, cooldown).toFloat()
		val gui = event.context
		val pose = gui.pose()
		val scale = TConfig.cooldownScale / 2F
		pose.pushMatrix()
		pose.translate(gui.guiWidth() / 2F, gui.guiHeight() / 2F)
		pose.scale(scale, scale)
		RenderCircleProgressRenderer.extractCircle(gui, circleTexture, progress, 0F, 1F, 0F, 1F,
			TConfig.cooldownColour.getEffectiveColourRGB())
		pose.popMatrix()
	}
}
