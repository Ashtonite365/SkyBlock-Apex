// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.util.ours

import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.invariantSeparatorsPathString
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import org.w3c.dom.Node
import dev.ashtonite365.apex.test.util.ours.Golden.Companion.golden

class GuiLayoutTreeGoldenTest {
	private val guiRoot: Path = Path.of(System.getProperty("apex.projectDir") ?: ".")
		.resolve("src/main/resources/assets/apex/gui")

	@Test
	fun layoutTreesMatchGolden() {
		val files = Files.walk(guiRoot).use { s ->
			s.filter { it.toString().endsWith(".xml") }.map { guiRoot.relativize(it).invariantSeparatorsPathString }.toList()
		}.sorted()
		assertTrue(files.size >= 10, "expected the GUI layouts (config/main + config/category archived in menus-1031) under $guiRoot, found $files")
		golden("gui-layout-trees") {
			for (rel in files) case(rel) { canonFile(guiRoot.resolve(rel)) }
		}
	}

	private fun canonFile(p: Path): String {
		val f = DocumentBuilderFactory.newInstance()
		f.isNamespaceAware = true
		f.isIgnoringComments = true
		return Files.newInputStream(p).use { canon(f.newDocumentBuilder().parse(it).documentElement) }
	}

	private fun name(uri: String?, local: String): String = when (uri) {
		null, "" -> local
		MOUL -> local
		APEX -> "apex:$local"
		else -> "{$uri}$local"
	}

	private fun quote(v: String) = "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

	private fun canon(e: Element): String = buildString {
		append(name(e.namespaceURI, e.localName))
		val attrs = (0 until e.attributes.length).map { e.attributes.item(it) }
			.filter { it.namespaceURI != XMLNS }
			.map { name(it.namespaceURI, it.localName) to it.nodeValue }
			.sortedBy { it.first }
		if (attrs.isNotEmpty()) append(attrs.joinToString(",", "(", ")") { it.first + "=" + quote(it.second) })
		val kids = ArrayList<String>()
		var n = e.firstChild
		while (n != null) {
			when (n.nodeType) {
				Node.ELEMENT_NODE -> kids += canon(n as Element)
				Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> if (n.nodeValue.isNotBlank()) kids += "!text"
			}
			n = n.nextSibling
		}
		if (kids.isNotEmpty()) append(kids.joinToString(",", "[", "]"))
	}

	private companion object {
		const val MOUL = "http://notenoughupdates.org/moulconfig"
		const val APEX = "http://apex.ashtonite365.dev/moulconfig"
		const val XMLNS = "http://www.w3.org/2000/xmlns/"
	}
}
