package dev.ashtonite365.apex.util

import io.github.notenoughupdates.moulconfig.common.IMinecraft
import io.github.notenoughupdates.moulconfig.common.MyResourceLocation
import io.github.notenoughupdates.moulconfig.gui.CloseEventListener
import io.github.notenoughupdates.moulconfig.gui.GuiComponent
import io.github.notenoughupdates.moulconfig.gui.GuiContext
import io.github.notenoughupdates.moulconfig.gui.GuiImmediateContext
import io.github.notenoughupdates.moulconfig.gui.KeyboardEvent
import io.github.notenoughupdates.moulconfig.gui.MouseEvent
import io.github.notenoughupdates.moulconfig.observer.GetSetter
import io.github.notenoughupdates.moulconfig.platform.MoulConfigRenderContext
import io.github.notenoughupdates.moulconfig.platform.MoulConfigScreenComponent
import io.github.notenoughupdates.moulconfig.xml.ChildCount
import io.github.notenoughupdates.moulconfig.xml.XMLContext
import io.github.notenoughupdates.moulconfig.xml.XMLGuiLoader
import io.github.notenoughupdates.moulconfig.xml.XMLUniverse
import io.github.notenoughupdates.moulconfig.xml.XSDGenerator
import java.io.File
import java.util.function.Supplier
import javax.xml.namespace.QName
import me.shedaniel.math.Color
import org.w3c.dom.Element
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import com.mojang.blaze3d.platform.InputConstants
import me.shedaniel.math.Rectangle
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.gui.BarComponent
import dev.ashtonite365.apex.gui.ApexButton
import dev.ashtonite365.apex.gui.ApexHoverComponent
import dev.ashtonite365.apex.gui.FixedComponent
import dev.ashtonite365.apex.gui.ImageComponent
import dev.ashtonite365.apex.gui.TickComponent

object GuiXml {

	const val apexUrl = "http://apex.ashtonite365.dev/moulconfig"

	private val schemaFiles = listOf(
		XMLUniverse.MOULCONFIG_XML_NS to "MoulConfig.xsd",
		apexUrl to "Apex.xsd",
	)

	@JvmStatic
	fun main(args: Array<out String>) {
		val dir = File(args.firstOrNull() ?: "build/gui-schemas").apply { mkdirs() }
		for ((namespace, fileName) in schemaFiles) writeSchema(File(dir, fileName), namespace)
	}

	private fun writeSchema(target: File, namespace: String) {
		val generator = XSDGenerator(universe, namespace)
		generator.writeAll()
		generator.dumpToFile(target)
	}

	private class ApexElement<T : GuiComponent>(
		private val localName: String,
		private val children: ChildCount,
		required: List<String>,
		optional: List<String> = emptyList(),
		private val build: (XMLContext<*>, Element) -> T,
	) : XMLGuiLoader.Basic<T> {
		private val attributes: Map<String, Boolean> = required.associateWith { true } + optional.associateWith { false }

		override fun getName(): QName = QName(apexUrl, localName)
		override fun getChildCount(): ChildCount = children
		override fun getAttributeNames(): Map<String, Boolean> = attributes
		override fun createInstance(context: XMLContext<*>, element: Element): T = build(context, element)
	}

	private fun <V> attr(ctx: XMLContext<*>, element: Element, name: String, type: Class<V>): GetSetter<V> =
		ctx.getPropertyFromAttribute(element, QName(name), type) ?: error("apex element needs the attribute '$name'")

	private fun <V> attr(ctx: XMLContext<*>, element: Element, name: String, type: Class<V>, fallback: V): V =
		ctx.getPropertyFromAttribute(element, QName(name), type, fallback)

	private fun optionalInt(ctx: XMLContext<*>, element: Element, name: String): GetSetter<Int>? =
		if (element.hasAttribute(name)) attr(ctx, element, name, Int::class.javaObjectType) else null

	private fun apexElements(): List<ApexElement<*>> = listOf(
		ApexElement("Bar", ChildCount.NONE, listOf("progress", "total", "fillColor", "emptyColor")) { ctx, el ->
			BarComponent(
				attr(ctx, el, "progress", Double::class.javaObjectType), attr(ctx, el, "total", Double::class.javaObjectType),
				attr(ctx, el, "fillColor", Color::class.java).get(), attr(ctx, el, "emptyColor", Color::class.java).get(),
			)
		},
		ApexElement("Hover", ChildCount.ONE, listOf("lines"), listOf("delay")) { ctx, el ->
			val text = attr(ctx, el, "lines", String::class.java)
			val delay = attr(ctx, el, "delay", Duration::class.java, 600.milliseconds)
			ApexHoverComponent(ctx.getChildFragment(el), Supplier { text.get().split(";") }, delay)
		},
		ApexElement("Button", ChildCount.ONE, listOf("onClick"), listOf("enabled", "noBackground")) { ctx, el ->
			val click = ctx.getMethodFromAttribute(el, QName("onClick"))
			val skinless = attr(ctx, el, "noBackground", Boolean::class.javaObjectType, false)
			val child = ctx.getChildFragment(el)
			if (el.hasAttribute("enabled"))
				ApexButton(child, attr(ctx, el, "enabled", Boolean::class.javaObjectType), skinless) { click.run() }
			else ApexButton(child, skinless = skinless) { click.run() }
		},
		ApexElement("Image", ChildCount.NONE, listOf("resource", "width", "height"), listOf("u1", "u2", "v1", "v2")) { ctx, el ->
			val f = Float::class.javaObjectType
			ImageComponent(
				attr(ctx, el, "width", Int::class.javaObjectType).get(), attr(ctx, el, "height", Int::class.javaObjectType).get(),
				attr(ctx, el, "resource", MyResourceLocation::class.java),
				attr(ctx, el, "u1", f, 0f), attr(ctx, el, "u2", f, 1f), attr(ctx, el, "v1", f, 0f), attr(ctx, el, "v2", f, 1f),
			)
		},
		ApexElement("Tick", ChildCount.NONE, listOf("tick")) { ctx, el -> TickComponent(ctx.getMethodFromAttribute(el, QName("tick"))) },
		ApexElement("Fixed", ChildCount.ONE, emptyList(), listOf("width", "height")) { ctx, el ->
			FixedComponent(optionalInt(ctx, el, "width"), optionalInt(ctx, el, "height"), ctx.getChildFragment(el))
		},
	)

	val universe: XMLUniverse = XMLUniverse.getDefaultUniverse().also { u ->
		u.registerMapper(java.awt.Color::class.java) { XmlColours.awt(it) }
		u.registerMapper(Color::class.java) { XmlColours.shedaniel(it) }
		apexElements().forEach(u::registerLoader)
	}

	private class ClosableGuiScreen(
		private val gui: GuiContext,
		private val parent: Screen?,
		private val afterClose: () -> Unit,
	) : MoulConfigScreenComponent(Component.empty(), gui, parent) {
		init {
			gui.closeRequestHandler = Runnable { closeUnlessObjected() }
		}

		override fun onClose() = closeUnlessObjected()

		private fun closeUnlessObjected() {
			if (gui.onBeforeClose() != CloseEventListener.CloseAction.NO_OBJECTIONS_TO_CLOSE) return
			Client.screen = parent
			afterClose()
		}
	}

	fun wrapScreen(gui: GuiContext, parent: Screen?, afterClose: () -> Unit = {}): Screen =
		ClosableGuiScreen(gui, parent, afterClose)

	fun loadScreen(name: String, bindTo: Any, parent: Screen?): Screen = wrapScreen(loadGui(name, bindTo), parent)

	fun GuiContext.adopt(element: GuiComponent) =
		element.foldRecursive(Unit) { component, _ -> component.context = this }

	private class MappedProperty<T, R>(
		private val backing: GetSetter<T>,
		private val read: (T) -> R,
		private val write: (R) -> T,
	) : GetSetter<R> {
		override fun get(): R = read(backing.get())
		override fun set(newValue: R) = backing.set(write(newValue))
	}

	fun <T, R> GetSetter<T>.xmap(read: (T) -> R, write: (R) -> T): GetSetter<R> = MappedProperty(this, read, write)

	private fun contextIn(area: Rectangle, drawContext: GuiGraphicsExtractor?, mouseX: Number, mouseY: Number) =
		originContext(drawContext, mouseX, mouseY).translated(area.x, area.y, area.width, area.height)

	fun keyIntoComponent(component: GuiComponent, area: Rectangle, event: KeyboardEvent): Boolean {
		val (mouseX, mouseY) = IMinecraft.getInstance().mousePosition
		val used = component.keyboardEvent(event, contextIn(area, null, mouseX, mouseY))
		val focusOwner = component.context ?: return used
		if (focusOwner.focusedElement == null) return used
		val escape = event is KeyboardEvent.KeyPressed && event.pressed && event.keycode == InputConstants.KEY_ESCAPE
		if (escape) focusOwner.setFocusedElement(null)
		return true
	}

	fun mouseIntoComponent(component: GuiComponent, area: Rectangle, mouseX: Int, mouseY: Int, event: MouseEvent): Boolean =
		component.mouseEvent(event, contextIn(area, null, mouseX, mouseY))

	fun originContext(drawContext: GuiGraphicsExtractor?, mouseX: Number, mouseY: Number): GuiImmediateContext {
		val target = if (drawContext == null) IMinecraft.getInstance().provideTopLevelRenderContext()
		else MoulConfigRenderContext(drawContext)
		val x = mouseX.toInt()
		val y = mouseY.toInt()
		return GuiImmediateContext(target, 0, 0, 0, 0, x, y, x, y, mouseX.toFloat(), mouseY.toFloat())
	}

	fun GuiGraphicsExtractor.drawComponentIn(component: GuiComponent, area: Rectangle, mouseX: Int, mouseY: Int) {
		val immediate = contextIn(area, this, mouseX, mouseY)
		val pose = pose()
		pose.pushMatrix()
		pose.translate(area.x.toFloat(), area.y.toFloat())
		try {
			component.render(immediate)
		} finally {
			pose.popMatrix()
		}
	}

	fun loadGui(name: String, bindTo: Any): GuiContext =
		GuiContext(universe.load(bindTo, MyResourceLocation("apex", "gui/$name.xml")))
}
