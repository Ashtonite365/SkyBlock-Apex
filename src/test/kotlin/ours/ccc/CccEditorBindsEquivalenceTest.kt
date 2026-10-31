
package dev.ashtonite365.apex.test.ours.ccc

import io.github.notenoughupdates.moulconfig.xml.Bind
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.customchatcommands.CustomChatCommandsUI
import dev.ashtonite365.apex.test.ours.OursGolden

class CccEditorBindsEquivalenceTest {
	private fun surface(c: Class<*>): List<String> {
		val fields = c.declaredFields.mapNotNull { f -> f.getAnnotation(Bind::class.java)?.let { b -> "field ${b.value.ifEmpty { f.name }}: ${f.type.simpleName}" } }
		val methods = c.declaredMethods.mapNotNull { m -> m.getAnnotation(Bind::class.java)?.let { b -> "fun ${b.value.ifEmpty { m.name }}/${m.parameterCount}: ${m.returnType.simpleName}" } }
		return (fields + methods).sorted().map { "${c.simpleName} $it" }
	}

	@Test
	fun bindSurfaceIsTheSame() {
		val root = CustomChatCommandsUI::class.java
		val out = (listOf(root) + root.declaredClasses.filter { it.simpleName != "Companion" }.sortedBy { it.simpleName }).flatMap(::surface).toMutableList()
		out += "consts ${CustomChatCommandsUI.COMMAND} ${CustomChatCommandsUI.ALIAS}"
		OursGolden.check("ccc-editor-binds", out)
	}
}
