package dev.ashtonite365.apex.util.data

import io.github.notenoughupdates.moulconfig.ChromaColour
import io.github.notenoughupdates.moulconfig.gui.CloseEventListener
import io.github.notenoughupdates.moulconfig.gui.GuiContext
import io.github.notenoughupdates.moulconfig.gui.component.CenterComponent
import io.github.notenoughupdates.moulconfig.gui.component.ColumnComponent
import io.github.notenoughupdates.moulconfig.gui.component.PanelComponent
import io.github.notenoughupdates.moulconfig.gui.component.RowComponent
import io.github.notenoughupdates.moulconfig.gui.component.TextComponent
import io.github.notenoughupdates.moulconfig.platform.MoulConfigPlatform
import io.github.notenoughupdates.moulconfig.platform.MoulConfigScreenComponent
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.gui.ApexButton
import dev.ashtonite365.apex.gui.config.AllConfigsGui
import dev.ashtonite365.apex.gui.config.BooleanHandler
import dev.ashtonite365.apex.gui.config.ChoiceHandler
import dev.ashtonite365.apex.gui.config.ClickHandler
import dev.ashtonite365.apex.gui.config.ColourHandler
import dev.ashtonite365.apex.gui.config.DurationHandler
import dev.ashtonite365.apex.gui.config.FeatureRisk
import dev.ashtonite365.apex.gui.config.GuiAppender
import dev.ashtonite365.apex.gui.config.HudAnchor
import dev.ashtonite365.apex.gui.config.HudEnabled
import dev.ashtonite365.apex.gui.config.HudMeta
import dev.ashtonite365.apex.gui.config.HudMetaHandler
import dev.ashtonite365.apex.gui.config.HudPosition
import dev.ashtonite365.apex.gui.config.IntegerHandler
import dev.ashtonite365.apex.gui.config.IntegerInputHandler
import dev.ashtonite365.apex.gui.config.KeyBindingHandler
import dev.ashtonite365.apex.gui.config.ManagedOption
import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.gui.config.SettingsTabs
import dev.ashtonite365.apex.gui.config.StringHandler
import dev.ashtonite365.apex.gui.config.storage.ConfigStorageClass
import dev.ashtonite365.apex.keybindings.SavedKeyBinding
import dev.ashtonite365.apex.settings.MainSection
import dev.ashtonite365.apex.settings.SettingPath
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.RememberedScrollPanel
import dev.ashtonite365.apex.util.ScreenUtil
import dev.ashtonite365.apex.util.collections.InstanceList
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.util.StringRepresentable
import org.joml.Vector2i
import kotlin.time.Duration

abstract class ManagedConfig(val name: String, val category: Category) : IDataHolder<Unit>() {

	enum class Category {
		GENERAL, HUD, TIMERS, RENDER, INVENTORY, AUCTION_BAZAAR, PROFILE_VIEWER, MUSEUM, FISHING, DUNGEONS, KUUDRA,
		SLAYERS, GARDEN, MINING, FORAGING, COMBAT, RIFT, CRIMSON_ISLE, EVENTS, CHAT, MISC, CUSTOM_MENUS, CREDITS;

		val translationKey: String get() = "apex.config.category." + name.lowercase()
		val labelText: Component = Component.translatable(translationKey)
		val description: Component = Component.translatable("$translationKey.description")

		val configs: List<ManagedConfig> = mutableListOf()

		@Deprecated("Keybind groups follow MainSection", ReplaceWith("mainSection.id", "dev.ashtonite365.apex.settings.MainSection.id"))
		val keybindGroupId get() = name.lowercase()

		companion object {
			@Deprecated("Merged into GENERAL", ReplaceWith("GENERAL"))
			val META get() = GENERAL

			@Deprecated("Merged into GENERAL", ReplaceWith("GENERAL"))
			val INTEGRATIONS get() = GENERAL

			@Deprecated("Merged into GENERAL", ReplaceWith("GENERAL"))
			val DEV get() = GENERAL

			@Deprecated("Split up; pick the category matching the item's use, or MISC", ReplaceWith("MISC"))
			val ITEMS get() = MISC
		}
	}

	companion object {

		val everyHolder = InstanceList<ManagedConfig>("ManagedConfig")
	}

	interface OptionCodec<T : Any> {

		fun onCreated(opt: ManagedOption<T>) {}
		fun encode(element: T): JsonElement?
		fun decode(element: JsonElement): T

		fun isRetired(element: JsonElement): Boolean = false
		fun appendRows(opt: ManagedOption<T>, guiAppender: GuiAppender)
	}

	private val byName = LinkedHashMap<String, ManagedOption<*>>()
	private val inOrder = ArrayList<ManagedOption<*>>()
	private var openEditorRows: GuiAppender? = null

	init {
		require(everyHolder.getAll().none { it.name == name }) { "Duplicate name '$name' used for config" }
		everyHolder.add(this)
		(category.configs as MutableList<ManagedConfig>).add(this)
	}

	val allOptions: Map<String, ManagedOption<*>> get() = byName

	val sortedOptions: List<ManagedOption<*>> get() = inOrder

	val mainSection: MainSection get() = SettingsTabs.mainSectionOf(this)

	fun settingPath(propertyName: String): SettingPath {
		val opt = byName[propertyName] ?: error("No option $propertyName in $name")
		return SettingsTabs.placementOf(opt).path
	}

	val translationKey get() = "apex.config.$name"
	val labelText: Component = Component.translatable(translationKey)

	override val storageClass: ConfigStorageClass get() = ConfigStorageClass.CONFIG

	override fun keys(): Collection<Unit> = listOf(Unit)

	override fun clear() {
		for (opt in inOrder) {
			@Suppress("UNCHECKED_CAST")
			(opt as ManagedOption<Any>)._actualValue = null
		}
	}

	override fun saveTo(key: Unit): JsonObject {
		val body = LinkedHashMap<String, JsonElement>()
		for (opt in inOrder) opt.toJson()?.let { body[opt.propertyName] = it }
		body.putAll(extraSave())
		return JsonObject(mapOf(name to JsonObject(body)))
	}

	override fun explicitDefaultLoad() = feed(JsonObject(emptyMap()))

	override fun loadFrom(key: Unit, jsonObject: JsonObject) {
		val mine = jsonObject[name]?.jsonObject ?: JsonObject(emptyMap())
		feed(migrateFromAll(mine, jsonObject))
	}

	private fun feed(own: JsonObject) {
		for (opt in inOrder) opt.load(own)
	}

	protected open fun migrate(saved: JsonObject): JsonObject = saved

	protected open fun migrateFromAll(saved: JsonObject, all: JsonObject): JsonObject = migrate(saved)

	protected open fun extraSave(): Map<String, JsonElement> = emptyMap()

	open fun onChange(option: ManagedOption<*>) {}

	protected fun <T : Any> option(id: String, default: () -> T, handler: OptionCodec<T>): ManagedOption<T> {
		require(id !in byName) { "Cannot register the same name twice" }
		val made = ManagedOption(this, id, default, handler)
		handler.onCreated(made)
		byName[id] = made
		inOrder += made
		return made
	}

	private fun optionKey(id: String) = "apex.config.$name.$id"

	protected fun toggle(id: String, default: () -> Boolean) = option(id, default, BooleanHandler(this))

	protected fun riskyToggle(id: String, riskLevel: RiskLevel = RiskLevel.RISKY): ManagedOption<Boolean> {
		requireRisky(riskLevel)
		val made = toggle(id) { false }
		made.riskLevel = riskLevel
		return made
	}

	protected fun riskySubToggle(id: String, riskLevel: RiskLevel, parent: ManagedOption<Boolean>, default: () -> Boolean): ManagedOption<Boolean> {
		requireRisky(riskLevel)
		val parentGuards = parent.riskLevel.isRisky && !parent.default()
		require(parentGuards || !default()) { "A default-ON ⚠ sub-option needs a ⚠ parent that defaults OFF" }
		val made = toggle(id, default)
		made.riskLevel = riskLevel
		made.parentToggle = parent
		return made
	}

	private fun requireRisky(level: RiskLevel) {
		require(level.isRisky) { "Use toggle() for options without risk" }
	}

	protected fun colour(id: String, default: () -> ChromaColour) = option(id, default, ColourHandler(this))

	protected fun <C> choice(id: String, type: Class<C>, default: () -> C): ManagedOption<C> where C : StringRepresentable, C : Enum<C> {
		val values = type.enumConstants.toList()
		return option(id, default, ChoiceHandler(type, values))
	}

	protected inline fun <reified C> choice(id: String, noinline default: () -> C) where C : StringRepresentable, C : Enum<C> =
		choice(id, C::class.java, default)

	protected fun duration(id: String, min: Duration, max: Duration, default: () -> Duration) =
		option(id, default, DurationHandler(this, min, max))

	protected fun position(id: String, width: Int, height: Int, default: () -> Vector2i): ManagedOption<HudMeta> =
		hud(id, width, height) { at -> HudPosition(at.x, at.y, 1F) }(default)

	protected fun position(id: String, width: Int, height: Int, anchor: HudAnchor, default: () -> Vector2i): ManagedOption<HudMeta> =
		hud(id, width, height) { at -> HudPosition(at.x, at.y, 1F, anchor) }(default)

	private fun hud(id: String, width: Int, height: Int, placeAt: (Vector2i) -> HudPosition): (() -> Vector2i) -> ManagedOption<HudMeta> {
		val key = optionKey(id)
		val title = Component.translatable(key)
		return { start ->
			val made = option(id, { HudMeta(placeAt(start()), Apex.identifier(id), title, width, height) },
				HudMetaHandler(this, id, title, width, height))
			HudEnabled.register(key, this)
			made
		}
	}

	protected fun keyBinding(id: String, default: () -> Int): ManagedOption<SavedKeyBinding> =
		keyBindingExact(id) { SavedKeyBinding.keyWithoutMods(default()) }

	protected fun keyBindingExact(id: String, default: () -> SavedKeyBinding) =
		option(id, default, KeyBindingHandler(optionKey(id), this))

	protected fun keyBindingWithDefaultUnbound(id: String) =
		keyBindingExact(id) { SavedKeyBinding.unbound() }

	protected fun integer(id: String, min: Int, max: Int, default: () -> Int) =
		option(id, default, IntegerHandler(this, min, max))

	protected fun integerInput(id: String, min: Int, max: Int, default: () -> Int) =
		option(id, default, IntegerInputHandler(this, min, max))

	protected fun button(id: String, runnable: () -> Unit) = option(id, { }, ClickHandler(this, runnable))

	protected fun dynamicButton(id: String, label: Component, description: Component, runnable: () -> Unit): ManagedOption<Unit> {
		val made = button(id, runnable)
		made.labelOverride = label
		made.descriptionOverride = description
		return made
	}

	protected fun string(id: String, default: () -> String) = option(id, default, StringHandler(this))

	fun reloadGui() = openEditorRows?.reloadables?.forEach { it() }

	private class EditorScreen(context: GuiContext, private val back: Screen?) :
		MoulConfigScreenComponent(Component.empty(), context, back) {
		override fun onClose() {
			val verdict = guiContext.onBeforeClose()
			if (verdict != CloseEventListener.CloseAction.NO_OBJECTIONS_TO_CLOSE) return
			Client.screen = back
		}
	}

	fun getConfigEditor(parent: Screen? = null): Screen = AllConfigsGui.makeScreenFor(this, parent)

	fun getOptionEditor(option: ManagedOption<*>, parent: Screen?): Screen {
		var built: Screen? = null
		val rows = GuiAppender(EDITOR_WIDTH, { checkNotNull(built) { "Screen Accessor called too early" } })
		option.appendToGui(rows)
		openEditorRows = rows
		val header = RowComponent(
			ApexButton(TextComponent("←"), action = { Client.screen = parent ?: AllConfigsGui.makeScreenFor(this) }),
			TextComponent(MoulConfigPlatform.wrap(labelText)),
		)
		val scroller = RememberedScrollPanel("option:$name.${option.propertyName}", EDITOR_WIDTH, OPTION_HEIGHT, ColumnComponent(rows.panel))
		val column = if (FeatureRisk.purpleBoxed(option)) ColumnComponent(header, TextComponent("⚠ " + FeatureRisk.HARD_HEADER), scroller)
		else ColumnComponent(header, scroller)
		return EditorScreen(GuiContext(CenterComponent(PanelComponent(column))), parent ?: AllConfigsGui.makeScreenFor(this)).also { built = it }
	}

	fun showConfigEditor(parent: Screen? = null) {
		ScreenUtil.setScreenLater(getConfigEditor(parent))
	}
}

private const val EDITOR_WIDTH = 400
private const val EDITOR_HEIGHT = 300
private const val OPTION_HEIGHT = 120
