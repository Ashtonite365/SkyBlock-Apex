// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.ours.gui

import java.lang.reflect.Proxy
import java.util.function.BiFunction
import java.util.function.Consumer
import java.util.function.Supplier
import kotlin.time.Duration
import io.github.notenoughupdates.moulconfig.common.MyResourceLocation
import io.github.notenoughupdates.moulconfig.common.RenderContext
import io.github.notenoughupdates.moulconfig.common.text.StructuredText
import io.github.notenoughupdates.moulconfig.gui.GuiComponent
import io.github.notenoughupdates.moulconfig.gui.GuiContext
import io.github.notenoughupdates.moulconfig.gui.GuiImmediateContext
import io.github.notenoughupdates.moulconfig.gui.KeyboardEvent
import io.github.notenoughupdates.moulconfig.gui.MouseEvent
import io.github.notenoughupdates.moulconfig.observer.GetSetter
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.gui.BarComponent
import dev.ashtonite365.apex.gui.CheckboxComponent
import dev.ashtonite365.apex.gui.EmptyComponent
import dev.ashtonite365.apex.gui.ApexButton
import dev.ashtonite365.apex.gui.ApexHoverComponent
import dev.ashtonite365.apex.gui.FixedComponent
import dev.ashtonite365.apex.gui.ImageComponent
import dev.ashtonite365.apex.gui.TickComponent
import dev.ashtonite365.apex.gui.toMoulConfig
import dev.ashtonite365.apex.test.ours.OursGolden

@Tag("moulplatform")
class GuiComponentsEquivalenceTest {
	init {
		dev.ashtonite365.apex.test.ApexTestBootstrap.bootstrapMinecraft()
		platformReady
	}

	companion object {

		val platformReady: Boolean by lazy {
			val thread = Thread.currentThread()
			val previous = thread.contextClassLoader
			thread.contextClassLoader = GuiComponent::class.java.classLoader
			try {
				Class.forName("io.github.notenoughupdates.moulconfig.common.IMinecraft", true, GuiComponent::class.java.classLoader)
				true
			} finally {
				thread.contextClassLoader = previous
			}
		}
	}

	private val log = mutableListOf<String>()
	private val names = mutableMapOf<Any, String>()

	private val unsafe: sun.misc.Unsafe by lazy {
		sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").also { it.isAccessible = true }.get(null) as sun.misc.Unsafe
	}

	private fun show(a: Any?): String = when (a) {
		null -> "null"
		in names -> names.getValue(a)
		is List<*> -> a.joinToString(",", "[", "]") { show(it) }
		is StructuredText -> "'" + a.text + "'"
		is MyResourceLocation -> "${a.root}:${a.path}"
		else -> a.toString()
	}

	private val render: RenderContext = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(RenderContext::class.java)) { _, m, args ->
		val a = args ?: emptyArray()
		if (m.name == "drawComplexTexture") {
			val b = unsafe.allocateInstance(RenderContext.DrawTextureBuilder::class.java) as RenderContext.DrawTextureBuilder
			@Suppress("UNCHECKED_CAST")
			(a[5] as Consumer<RenderContext.DrawTextureBuilder>).accept(b)
			val uv = listOf("u1", "v1", "u2", "v2").map { f -> RenderContext.DrawTextureBuilder::class.java.getDeclaredField(f).also { it.isAccessible = true }.get(b) }
			log += "draw ${m.name}(${a.take(5).joinToString(",") { show(it) }}) uv=$uv"
		} else log += "draw ${m.name}(${a.joinToString(",") { show(it) }})"
		when (m.returnType) {
			java.lang.Boolean.TYPE -> false
			Integer.TYPE -> 0
			java.lang.Float.TYPE -> 0F
			java.lang.Double.TYPE -> 0.0
			else -> null
		}
	} as RenderContext

	private fun ctx(w: Int, h: Int, mx: Int, my: Int) = GuiImmediateContext(render, 0, 0, w, h, mx, my, mx, my, mx.toFloat(), my.toFloat())

	private inner class Probe(val label: String, val w: Int = 10, val h: Int = 6) : GuiComponent() {
		override fun getWidth() = w
		override fun getHeight() = h
		override fun render(context: GuiImmediateContext) {
			log += "$label.render ${context.width}x${context.height} at ${context.mouseX},${context.mouseY} hovered=${context.isHovered}"
		}
		override fun mouseEvent(mouseEvent: MouseEvent, context: GuiImmediateContext): Boolean {
			log += "$label.mouse $mouseEvent ${context.width}x${context.height} hovered=${context.isHovered}"
			return true
		}
		override fun keyboardEvent(event: KeyboardEvent, context: GuiImmediateContext): Boolean {
			log += "$label.key $event ${context.width}x${context.height}"
			return false
		}
	}

	private fun attempt(block: () -> Any?): String = try {
		show(block())
	} catch (e: Throwable) {
		"ERR:" + e.javaClass.simpleName
	}

	private fun flush(out: MutableList<String>) {
		out += log.map { "  $it" }
		log.clear()
	}

	private val hover = ctx(40, 20, 5, 5)
	private val away = ctx(40, 20, 100, 100)
	private val down = MouseEvent.Click(0, true)
	private val up = MouseEvent.Click(0, false)
	private val rightDown = MouseEvent.Click(1, true)
	private val rightUp = MouseEvent.Click(1, false)

	@Test
	fun simpleComponentsAreTheSame() {
		val out = mutableListOf<String>()
		val bar = BarComponent(GetSetter.constant(3.0), GetSetter.constant(10.0), me.shedaniel.math.Color.ofOpaque(0xff0000), me.shedaniel.math.Color.ofOpaque(0x00ff00))
		out += "bar ${bar.width}x${bar.height} res=${BarComponent.resource} render=${attempt { bar.render(hover) }}"
		out += "bar textures ${BarComponent.left} ${BarComponent.middle} ${BarComponent.right} ${BarComponent.segmentOverlay}"
		out += "bar tex ${BarComponent.Texture(Apex.identifier("x"), 1F, 2F, 3F, 4F).let { listOf(it, it.copy(u1 = 9F), it == it.copy(), it.hashCode() == it.copy().hashCode()) }}"
		out += "moul ${show(Apex.identifier("a/b.png").toMoulConfig())}"
		val empty = EmptyComponent()
		out += "empty ${empty.width}x${empty.height} ${attempt { empty.render(hover) }}"
		var ticks = 0
		val tick = TickComponent { ticks++ }
		tick.render(hover); tick.render(away)
		out += "tick ${tick.width}x${tick.height} ticks=$ticks"
		val img = ImageComponent(12, 7, { MyResourceLocation("apex", "img.png") }, 0.1F, 0.2F, 0.3F, 0.4F)
		out += "image ${img.width}x${img.height} ${img.u1} ${img.u2} ${img.v1} ${img.v2} ${show(img.Identifier.get())}"
		img.render(ctx(30, 15, 0, 0)); flush(out)
		OursGolden.check("gui-simple", out)
	}

	@Test
	fun checkboxClicksAreTheSame() {
		val out = mutableListOf<String>()
		var state = "a"
		val box = CheckboxComponent(object : GetSetter<String> {
			override fun get() = state
			override fun set(newValue: String) {
				state = newValue
			}
		}, "b")
		GuiContext(box)
		out += "box ${box.width}x${box.height} enabled=${box.isEnabled()} render=${attempt { box.render(hover) }}"
		val steps = listOf(
			"down-away" to (down to away), "up-away" to (up to away), "down" to (down to hover), "right-up" to (rightUp to hover),
			"up-away" to (up to away), "down" to (down to hover), "up" to (up to hover), "up-again" to (up to hover),
			"right-down" to (rightDown to hover), "move" to (MouseEvent.Move(1F, 1F) to hover), "right-up" to (rightUp to hover),
			"reset" to (MouseEvent.Move(0F, 0F) to away), "right-down-away" to (rightDown to away), "right-down" to (rightDown to hover),
			"left-up" to (up to hover), "right-up-away" to (rightUp to away), "right-up-again" to (rightUp to hover),
		)
		for ((label, step) in steps) {
			if (label == "reset") state = "a"
			val r = box.mouseEvent(step.first, step.second)
			out += "$label -> $r state=$state clicking=${box.isClicking} enabled=${box.isEnabled()} focused=${box.isFocused}"
		}
		OursGolden.check("gui-checkbox", out)
	}

	@Test
	fun buttonsAreTheSame() {
		val out = mutableListOf<String>()
		for (noBg in listOf(false, true)) for (enabled in listOf(true, false)) {
			val clicks = mutableListOf<Int>()
			val probe = Probe("child")
			val button = ApexButton(probe, GetSetter.constant(enabled), noBg) { b: Int -> clicks += b }
			names.clear()
			names[button.skinHover] = "hoveredBg"; names[button.skinIdle] = "unhoveredBg"
			names[button.skinDisabled] = "disabledBg"; names[button.skinActive] = "activeBg"
			GuiContext(button)
			out += "button noBg=$noBg enabled=$enabled ${button.width}x${button.height} insets=${button.insets} bg=${show(button.skinFor(away))}/${show(button.skinFor(hover))}"
			for ((label, step) in listOf(
				"down-away" to (down to away), "down" to (down to hover), "move" to (MouseEvent.Move(2F, 0F) to hover),
				"up-away" to (up to away), "right-down" to (rightDown to hover), "right-up" to (rightUp to hover), "up" to (up to hover),
			)) {
				val r = button.mouseEvent(step.first, step.second)
				out += "  $label -> $r clicks=$clicks clicking=${button.pressHeld} focused=${button.isFocused} bg=${show(button.skinFor(away))}"
			}
			button.render(hover); flush(out)
			button.render(away); flush(out)
		}
		var ran = 0
		val viaRunnable = ApexButton(Probe("r"), action = Runnable { ran++ })
		GuiContext(viaRunnable)
		viaRunnable.mouseEvent(down, hover); viaRunnable.mouseEvent(up, hover)
		out += "runnable ran=$ran noBg=${viaRunnable.skinless} enabled=${viaRunnable.enabledWhen.get()}"
		flush(out)
		names.clear()
		OursGolden.check("gui-button", out)
	}

	@Test
	fun wrappersAreTheSame() {
		val out = mutableListOf<String>()
		val probe = Probe("inner", 14, 9)
		val fixedBoth = FixedComponent(GetSetter.constant(30), GetSetter.constant(12), probe)
		val fixedNone = FixedComponent(null, null, probe)
		for ((label, f) in listOf("both" to fixedBoth, "none" to fixedNone)) {
			out += "fixed $label ${f.width}x${f.height} fold=${f.foldChildren(listOf<String>(), BiFunction { c, acc -> acc + c.javaClass.simpleName })}"
			out += "  ctx ${f.fixContext(hover).let { "${it.width}x${it.height} ${it.mouseX},${it.mouseY}" }}"
			f.render(hover); out += "  mouse ${f.mouseEvent(down, away)} key ${f.keyboardEvent(KeyboardEvent.CharTyped('x'), hover)}"; flush(out)
		}
		val lines = mutableListOf("one", "two")
		for (delay in listOf(Duration.ZERO, Duration.INFINITE)) {
			val h = ApexHoverComponent(Probe("hovered", 20, 8), Supplier { lines.toList() }, delay)
			out += "hover delay=$delay ${h.width}x${h.height} fold=${h.foldChildren(0, BiFunction { _, n -> n + 1 })} perma=${h.permaHover}"
			h.render(hover); out += "  after hover render perma=${h.permaHover}"; flush(out)
			h.render(hover); out += "  again perma=${h.permaHover}"; flush(out)
			out += "  move -> ${h.mouseEvent(MouseEvent.Move(1F, 1F), hover)}"; flush(out)
			h.render(hover); out += "  after move perma=${h.permaHover}"; flush(out)
			h.render(away); out += "  away perma=${h.permaHover}"; flush(out)
			out += "  key -> ${h.keyboardEvent(KeyboardEvent.CharTyped('q'), hover)}"; flush(out)
		}
		OursGolden.check("gui-wrappers", out)
	}
}
