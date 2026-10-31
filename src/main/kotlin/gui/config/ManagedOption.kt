package dev.ashtonite365.apex.gui.config

import io.github.notenoughupdates.moulconfig.observer.GetSetter
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.util.Faults
import dev.ashtonite365.apex.util.data.ManagedConfig

class ManagedOption<T : Any> constructor(
	val element: ManagedConfig, val propertyName: String,
	val default: () -> T, val handler: ManagedConfig.OptionCodec<T>,
) : ReadWriteProperty<Any?, T>,
	GetSetter<T> {

	var riskLevel: RiskLevel = RiskLevel.NONE
		internal set

	val shownRisk: RiskLevel get() = FeatureRisk.shownRisk(this)

	var parentToggle: ManagedOption<Boolean>? = null
		internal set

	val parentActive: Boolean
		get() = parentToggle?.storedOrDefault() != false

	var labelOverride: Component? = null
	var descriptionOverride: Component? = null

	private val keyPrefix get() = "apex.config.${element.name}.$propertyName"
	val rawLabelText = keyPrefix
	val descriptionTranslationKey = "$keyPrefix.description"

	val labelText: Component
		get() = labelOverride ?: (OptionLabels.label(rawLabelText) ?: Component.translatable(rawLabelText)).let { plain ->
			if (shownRisk.isRisky) Component.literal("⚠ ").withStyle(ChatFormatting.RED).append(plain) else plain
		}

	val labelDescription: Component
		get() = descriptionOverride ?: composeDescription()

	private fun composeDescription(): Component {
		val plain = OptionLabels.description(rawLabelText) ?: Component.translatable(descriptionTranslationKey)
		val extras = listOfNotNull(
			RiskDisclaimer.warningText.takeIf { shownRisk.isRisky },
			FeatureRisk.descriptionSuffix(this),
		)
		if (extras.isEmpty()) return plain
		return extras.fold(Component.empty().append(plain)) { text, extra -> text.append("\n").append(extra) }
	}

	val riskTier: FeatureRisk.Tier? by lazy { FeatureRisk.tierOf(this) }
	private val permanentlyDisabled: Boolean by lazy { FeatureRisk.key(this) in FeatureRisk.FORCED_OFF }

	val forcedOff: Boolean
		get() = permanentlyDisabled || (riskTier == FeatureRisk.Tier.HARD && !DangerousSettings.allowed)

	var _actualValue: T? = null

	private var restoring = false

	private fun storedOrDefault(): T = _actualValue ?: default()

	var value: T
		get() {
			val stored = _actualValue ?: error("Lateinit variable not initialized")
			@Suppress("UNCHECKED_CAST")
			return if (stored == true && forcedOff) false as T else stored
		}
		set(next) {
			if (!restoring && !confirmChange(next)) return
			_actualValue = next
			element.onChange(this)
		}

	@Suppress("UNCHECKED_CAST")
	private fun confirmChange(next: T): Boolean {
		if (next is Boolean && next != _actualValue && DangerousSettings.isSwitch(this)) {
			if (!DangerousSettings.requestFlip(this as ManagedOption<Boolean>, next)) return false
		}
		val turningOnRisky = shownRisk.isRisky && next == true && _actualValue != true
		return !turningOnRisky || RiskDisclaimer.requestEnable(this as ManagedOption<Boolean>)
	}

	override fun get(): T = value
	override fun set(newValue: T): Unit { value = newValue }
	override fun getValue(thisRef: Any?, property: KProperty<*>): T = value
	override fun setValue(thisRef: Any?, property: KProperty<*>, value: T): Unit { this.value = value }

	fun load(root: JsonElement): Unit {
		restoring = true
		try {
			value = readFrom(root) ?: default()
		} finally {
			restoring = false
		}
	}

	private fun readFrom(root: JsonElement): T? {
		val saved = (root as? JsonObject)?.get(propertyName) ?: return null
		if (handler.isRetired(saved)) {
			dev.ashtonite365.apex.Apex.logger.info("Config ${element.name}.$propertyName: '$saved' was removed, using the default")
			return null
		}
		return try {
			handler.decode(saved)
		} catch (unreadable: Exception) {
			Faults.logFault("Exception during loading of config file ${element.name}. This will reset this config.", unreadable)
			null
		}
	}

	fun toJson(): JsonElement? = handler.encode(storedOrDefault())

	fun appendToGui(guiapp: GuiAppender): Unit = handler.appendRows(this, guiapp)
}
