
package dev.ashtonite365.apex.test.ours.guixml

import io.github.notenoughupdates.moulconfig.gui.GuiComponent
import io.github.notenoughupdates.moulconfig.observer.GetSetter
import io.github.notenoughupdates.moulconfig.xml.Bind
import io.github.notenoughupdates.moulconfig.xml.XMLContext
import io.github.notenoughupdates.moulconfig.xml.XMLGuiLoader
import io.github.notenoughupdates.moulconfig.xml.XMLUniverse
import javax.xml.namespace.QName
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.gui.ApexButton
import dev.ashtonite365.apex.gui.ApexHoverComponent
import dev.ashtonite365.apex.gui.BarComponent
import dev.ashtonite365.apex.gui.FixedComponent
import dev.ashtonite365.apex.gui.ImageComponent
import dev.ashtonite365.apex.gui.TickComponent
import dev.ashtonite365.apex.test.ours.OursGolden
import dev.ashtonite365.apex.util.GuiXml
import dev.ashtonite365.apex.util.GuiXml.xmap

@Tag("moulplatform")
class GuiXmlEquivalenceTest {
	init {
		dev.ashtonite365.apex.test.ApexTestBootstrap.bootstrapMinecraft()
		dev.ashtonite365.apex.test.ours.gui.GuiComponentsEquivalenceTest.platformReady
	}

	class Bound {
		val log = mutableListOf<String>()
		@field:Bind var progress: Double = 3.0
		@field:Bind var total: Double = 8.0
		@field:Bind var hoverText: String = "one;two"
		@field:Bind var on: Boolean = false
		@field:Bind var w: Int = 33
		@Bind fun click() { log += "click" }
		@Bind fun tick() { log += "tick" }
	}

	private fun <T> attempt(f: () -> T): String = try { f().toString() } catch (e: Throwable) { "ERR:" + e.javaClass.simpleName + ":" + e.message?.lineSequence()?.firstOrNull() }

	private fun loaders(): Map<QName, XMLGuiLoader<*>> {
		val f = XMLUniverse::class.java.getDeclaredField("guiElements").also { it.isAccessible = true }
		@Suppress("UNCHECKED_CAST") return f.get(GuiXml.universe) as Map<QName, XMLGuiLoader<*>>
	}

	@Test
	fun elements() {
		val out = mutableListOf("apexUrl ${GuiXml.apexUrl}")
		for ((name, loader) in loaders().entries.filter { it.key.namespaceURI == GuiXml.apexUrl }.sortedBy { it.key.localPart }) {
			val basic = loader as? XMLGuiLoader.Basic<*>
			out += "${name.localPart} ${loader.name} children=${basic?.childCount} attrs=${basic?.attributeNames?.toSortedMap()}"
		}
		out += "moulconfig loaders ${loaders().keys.count { it.namespaceURI != GuiXml.apexUrl }}"
		OursGolden.check("guixml-elements", out)
	}

	private fun build(xml: String, bound: Bound): GuiComponent {
		val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
			.parse(("<apex:X xmlns=\"http://notenoughupdates.org/moulconfig\" xmlns:apex=\"${GuiXml.apexUrl}\">$xml</apex:X>").byteInputStream())
		val el = doc.documentElement.firstChild as org.w3c.dom.Element
		return GuiXml.universe.load(XMLContext(GuiXml.universe, bound), el)
	}

	private fun describe(c: GuiComponent, b: Bound): String = when (c) {
		is BarComponent -> "Bar p=${c.progress.get()} t=${c.total.get()} fill=${c.fillColor.color} empty=${c.emptyColor.color} w=${c.width} h=${c.height}"
		is ApexHoverComponent -> "Hover lines=${c.hoverLines.get()} delay=${c.hoverDelay} child=${describe(c.child, b)}"
		is ApexButton -> { c.action(0); c.action(1); "Button skinless=${c.skinless} enabled=${attempt { c.enabledWhen.get() }} log=${b.log}" }
		is ImageComponent -> "Image ${c.Identifier.get().let { it.root + ":" + it.path }} ${c.width}x${c.height} u=${c.u1}..${c.u2} v=${c.v1}..${c.v2}"
		is TickComponent -> { c.onTick.run(); "Tick log=${b.log}" }
		is FixedComponent -> "Fixed w=${c.fixedWidth?.get()} h=${c.fixedHeight?.get()} size=${attempt { c.width }}x${attempt { c.height }} child=${describe(c.component, b)}"
		else -> c.javaClass.simpleName
	}

	@Test
	fun building() {
		val tick = "<apex:Tick tick=\"@tick\"/>"
		val cases = listOf(
			"<apex:Bar progress=\"@progress\" total=\"@total\" fillColor=\"#FF8000\" emptyColor=\"#80112233\"/>",
			"<apex:Bar progress=\"@progress\" total=\"@total\" fillColor=\"-16711936\" emptyColor=\"#000000\"/>",
			"<apex:Bar progress=\"@progress\" total=\"@total\" fillColor=\"#12345\" emptyColor=\"#000000\"/>",
			"<apex:Hover lines=\"a;b;c\">$tick</apex:Hover>",
			"<apex:Hover lines=\"@hoverText\">$tick</apex:Hover>",
			"<apex:Hover lines=\"single\" delay=\"2\">$tick</apex:Hover>",
			"<apex:Button onClick=\"@click\">$tick</apex:Button>",
			"<apex:Button onClick=\"@click\" noBackground=\"true\">$tick</apex:Button>",
			"<apex:Button onClick=\"@click\" noBackground=\"false\" enabled=\"@on\">$tick</apex:Button>",
			"<apex:Image resource=\"apex:textures/gui/x.png\" width=\"16\" height=\"12\"/>",
			"<apex:Image resource=\"apex:textures/gui/x.png\" width=\"16\" height=\"12\" u1=\"0.25\" u2=\"0.5\" v1=\"0.125\" v2=\"0.75\"/>",
			tick,
			"<apex:Fixed width=\"300\">$tick</apex:Fixed>",
			"<apex:Fixed width=\"380\" height=\"30\">$tick</apex:Fixed>",
			"<apex:Fixed height=\"@w\">$tick</apex:Fixed>",
			"<apex:Fixed>$tick</apex:Fixed>",
		)
		val out = cases.map { xml -> val b = Bound(); xml + " -> " + attempt { describe(build(xml, b), b) } }
		OursGolden.check("guixml-building", out)
	}

	@Test
	fun mappers() {
		val out = listOf("#FF0000", "#7F00FF00", "#FF0000FF", "123456", "-1", "#GG0000", "#123").flatMap { s ->
			listOf(
				"$s awt=" + attempt { GuiXml.universe.mapXMLObject(s, java.awt.Color::class.java).rgb },
				"$s shedaniel=" + attempt { GuiXml.universe.mapXMLObject(s, me.shedaniel.math.Color::class.java).color },
			)
		}
		OursGolden.check("guixml-mappers", out)
	}

	@Test
	fun xmapProperty() {
		var backing = 10
		val gs = object : GetSetter<Int> { override fun get() = backing; override fun set(newValue: Int) { backing = newValue } }
		val mapped = gs.xmap({ it * 2.5 }, { (it / 2.5).toInt() })
		val out = mutableListOf("read ${mapped.get()}")
		mapped.set(50.0); out += "after set 50.0 backing=$backing read=${mapped.get()}"
		backing = 3; out += "backing 3 read=${mapped.get()}"
		out += "class ${mapped.javaClass.simpleName}"
		OursGolden.check("guixml-xmap", out)
	}
}
