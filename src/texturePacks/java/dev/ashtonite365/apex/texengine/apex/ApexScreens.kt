/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex texture engine: screen layouts (SPEC §2.8) and container title colours (SPEC §2.9).
 */
package dev.ashtonite365.apex.texengine.apex

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import dev.ashtonite365.apex.texengine.ActiveLayout
import dev.ashtonite365.apex.texengine.LayoutBackground
import dev.ashtonite365.apex.texengine.LayoutMover
import dev.ashtonite365.apex.texengine.ScreenLayouts
import dev.ashtonite365.apex.texengine.TitleMover
import dev.ashtonite365.apex.util.accessors.castAccessor
import dev.ashtonite365.apex.util.trail.Crumb
import dev.ashtonite365.apex.util.trail.Trail
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.HangingSignEditScreen
import net.minecraft.client.gui.screens.inventory.SignEditScreen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimplePreparableReloadListener
import net.minecraft.util.profiling.ProfilerFiller

object ApexScreens : ScreenLayouts {
	val recognisedNamespaces = listOf("firmskyblock")
	private const val FOLDER = "overrides/screen_layout"

	val SIGN: Identifier = Identifier.fromNamespaceAndPath("firmskyblock", "sign")
	val HANGING_SIGN: Identifier = Identifier.fromNamespaceAndPath("firmskyblock", "hanging_sign")

	enum class Align { LEFT, CENTER, RIGHT }

	class Title(val x: Int?, val y: Int?, val align: Align, val replacement: Component?) : TitleMover {
		override fun text(text: Component): Component = replacement ?: text
		override fun x(font: Font, text: Component, x: Int): Int {
			val start = this.x ?: x
			return when (align) {
				Align.LEFT -> start
				Align.CENTER -> start - font.width(text) / 2
				Align.RIGHT -> start - font.width(text)
			}
		}
		override fun y(y: Int): Int = this.y ?: y
		override fun color(text: Component, color: Int): Int = ApexTextColors.colorFor(text, color)
	}

	class Background(val texture: Identifier, override val x: Int, override val y: Int,
					 override val width: Int, override val height: Int) : LayoutBackground {
		override fun draw(graphics: GuiGraphicsExtractor, screen: AbstractContainerScreen<*>) {
			val acc = screen.castAccessor()

			blitAt(graphics, (screen.width - acc.backgroundWidth_Apex) / 2 + x, (screen.height - acc.backgroundHeight_Apex) / 2 + y)
		}
		override fun drawDirect(graphics: GuiGraphicsExtractor) = blitAt(graphics, x, y)
		private fun blitAt(graphics: GuiGraphicsExtractor, left: Int, top: Int) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, texture, left, top, 0f, 0f, width, height, width, height)
		}
	}

	class SlotMove(val index: Int, val x: Int, val y: Int)

	class Layout(
		val label: TextMatch,
		val type: Identifier?,
		val slotMoves: List<SlotMove>,
		override val background: Background?,
		override val nameField: LayoutMover?,
		val signLines: List<LayoutMover>?,
		override val containerTitle: TitleMover,
		override val playerTitle: TitleMover,
		override val repairCostTitle: TitleMover,
	) : ActiveLayout {
		override fun signLine(index: Int): LayoutMover? = signLines?.getOrNull(index)
	}

	override val noLayoutTitle: TitleMover = Title(null, null, Align.LEFT, null)

	@Volatile
	private var layouts: List<Layout> = listOf()

	@Volatile
	var current: Layout? = null
		private set

	override val active: ActiveLayout? get() = current

	fun screenChanged(screen: Screen?) {
		if (screen == null || !settings.screenLayouts) {
			current = null
			return
		}
		val chosen = select(screen.title, typeOf(screen))
		current = chosen
		if (chosen != null && screen is AbstractContainerScreen<*>) {
			val slots = screen.menu.slots
			for (move in chosen.slotMoves) {
				val slot = slots.getOrNull(move.index) ?: continue
				slot.x = move.x
				slot.y = move.y
			}
		}
	}

	fun select(title: Component, type: Identifier?): Layout? {
		val all = layouts
		if (all.isEmpty()) return null
		val flat = title.string
		for (layout in all) {
			if (layout.type != null && layout.type != type) continue
			if (layout.label.test(flat)) return layout
		}
		return null
	}

	fun typeOf(screen: Screen): Identifier? = when (screen) {
		is HangingSignEditScreen -> HANGING_SIGN
		is SignEditScreen -> SIGN
		is AbstractContainerScreen<*> -> try {
			BuiltInRegistries.MENU.getKey(screen.menu.type)
		} catch (_: UnsupportedOperationException) {
			null
		}
		else -> null
	}

	fun use(loaded: List<Layout>) {
		layouts = loaded
	}

	fun load(manager: ResourceManager): List<Layout> {
		val out = ArrayList<Layout>()
		for ((fileId, resource) in PackFiles.list(manager, FOLDER, recognisedNamespaces)) {
			try {
				out += layoutFrom(PackFiles.read(resource).asJsonObject)
			} catch (e: Exception) {
				PackFiles.log.warn("Skipping screen layout {}: {}", fileId, e.message)
				Trail.crumb(Crumb.TEXTURE_FILE_SKIPPED, fileId)
			}
		}
		return out
	}

	private fun layoutFrom(o: JsonObject): Layout {
		val predicates = o.getAsJsonObject("predicates") ?: badPack("missing predicates")
		val label = TextMatch.read(predicates.get("label") ?: badPack("missing predicates.label"))
		val type = predicates.get("screenType")?.let { Identifier.parse(it.asString) }
		val slots = o.getAsJsonArray("slots")?.map {
			val s = it.asJsonObject
			SlotMove(s.int("index"), s.int("x"), s.int("y"))
		} ?: listOf()
		val background = o.getAsJsonObject("background")?.let {
			Background(Identifier.parse(it.str("texture")), it.int("x"), it.int("y"), it.int("width"), it.int("height"))
		}
		val signLines = o.getAsJsonArray("signLines")?.map { mover(it) }
		if (signLines != null && signLines.size != 4) badPack("signLines needs exactly 4 entries, found ${signLines.size}")
		return Layout(
			label, type, slots, background,
			o.get("nameField")?.let(::mover),
			signLines,
			title(o.get("containerTitle")),
			title(o.get("playerTitle")),
			title(o.get("repairCostTitle")),
		)
	}

	private fun mover(json: JsonElement): LayoutMover {
		val m = json.asJsonObject
		return LayoutMover(m.int("x"), m.int("y"), m.intOrNull("width"), m.intOrNull("height"))
	}

	private fun title(json: JsonElement?): TitleMover {
		if (json == null) return noLayoutTitle
		val t = json.asJsonObject
		val align = when (val a = t.get("align")?.asString?.lowercase() ?: "left") {
			"left" -> Align.LEFT
			"center" -> Align.CENTER
			"right" -> Align.RIGHT
			else -> badPack("unknown align '$a'")
		}
		return Title(t.intOrNull("x"), t.intOrNull("y"), align, t.get("replace")?.asString?.let(Component::literal))
	}

	private fun JsonObject.int(key: String): Int = get(key)?.asInt ?: badPack("missing '$key'")
	private fun JsonObject.intOrNull(key: String): Int? = get(key)?.takeUnless { it.isJsonNull }?.asInt
	private fun JsonObject.str(key: String): String = get(key)?.asString ?: badPack("missing '$key'")

	object Listener : SimplePreparableReloadListener<List<Layout>>() {
		override fun prepare(manager: ResourceManager, profiler: ProfilerFiller) = load(manager)
		override fun apply(data: List<Layout>, manager: ResourceManager, profiler: ProfilerFiller) = use(data)
	}
}

object ApexTextColors {
	private val FILE: Identifier = Identifier.fromNamespaceAndPath("firmskyblock", "overrides/text_colors.json")

	class Table(val defaultColor: Int, val overrides: List<Pair<TextMatch, Int>>) {
		fun colorFor(text: Component): Int {
			if (overrides.isEmpty()) return defaultColor
			val flat = text.string
			for ((match, color) in overrides) if (match.test(flat)) return color
			return defaultColor
		}
	}

	private class Memo(val table: Table, val text: Component, val color: Int)

	@Volatile
	private var table: Table? = null
	private var memoA: Memo? = null
	private var memoB: Memo? = null

	fun colorFor(text: Component, original: Int): Int {
		val t = table ?: return original
		val a = memoA
		if (a != null && a.text === text && a.table === t) return a.color
		val b = memoB
		if (b != null && b.text === text && b.table === t) return b.color
		val color = t.colorFor(text)
		memoB = a
		memoA = Memo(t, text, color)
		return color
	}

	fun use(loaded: Table?) {
		table = loaded
		memoA = null
		memoB = null
	}

	fun load(manager: ResourceManager): Table? {
		val resource = manager.getResource(FILE).orElse(null) ?: return null
		return try {
			val o = PackFiles.read(resource).asJsonObject
			val default = o.get("defaultColor")?.asInt ?: badPack("missing defaultColor")
			val overrides = o.getAsJsonArray("overrides")?.map {
				val e = it.asJsonObject
				TextMatch.read(e.get("predicate") ?: badPack("missing predicate")) to (e.get("override")?.asInt ?: badPack("missing override"))
			} ?: listOf()
			Table(default, overrides)
		} catch (e: Exception) {
			PackFiles.log.warn("Skipping text colour table {}: {}", FILE, e.message)
			Trail.crumb(Crumb.TEXTURE_FILE_SKIPPED, FILE)
			null
		}
	}

	object Listener : SimplePreparableReloadListener<java.util.Optional<Table>>() {
		override fun prepare(manager: ResourceManager, profiler: ProfilerFiller): java.util.Optional<Table> =
			java.util.Optional.ofNullable(load(manager))
		override fun apply(data: java.util.Optional<Table>, manager: ResourceManager, profiler: ProfilerFiller) = use(data.orElse(null))
	}
}
