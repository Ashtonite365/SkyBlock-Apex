// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.ours.mixins

import java.io.File
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import dev.ashtonite365.apex.test.ours.OursGolden
import dev.ashtonite365.apex.test.testutil.RepoFile

class MixinWiringEquivalenceTest {
	private val roots = listOf("src/main/java", "src/compat/jade/java", "src/compat/jei/java", "src/compat/sodium/java")

	private fun value(v: Any?): String = when (v) {
		null -> "null"
		is AnnotationNode -> annotation(v)
		is List<*> -> if (v.size == 2 && v[0] is String && (v[0] as String).startsWith("L") && v[1] is String && v.size == 2 && (v[0] as String).endsWith(";") && !(v[1] as String).contains('/'))
			"${v[0]}.${v[1]}" else v.joinToString(",", "[", "]") { value(it) }
		is Type -> v.descriptor
		is Array<*> -> v.joinToString(".")
		else -> v.toString()
	}

	private fun annotation(a: AnnotationNode): String {
		val vals = a.values.orEmpty().chunked(2).joinToString(",") { (k, v) -> "$k=${value(v)}" }
		return "@${a.desc}($vals)"
	}

	private fun annotations(vararg lists: List<AnnotationNode>?) = lists.flatMap { it.orEmpty() }.map(::annotation).sorted()

	private fun isWiring(a: AnnotationNode) = (a.desc.startsWith("Lorg/spongepowered/") || a.desc.startsWith("Lcom/llamalad7/")) && !a.desc.endsWith("/Unique;")

	private fun paramAnnotations(m: MethodNode): String =
		listOfNotNull(m.visibleParameterAnnotations, m.invisibleParameterAnnotations).let { both ->
			(0 until Type.getArgumentTypes(m.desc).size).joinToString(";") { i ->
				both.flatMap { arr -> arr.getOrNull(i).orEmpty() }.map(::annotation).sorted().joinToString("")
			}
		}

	private fun describe(cls: String, bytes: ByteArray?): List<String> {
		if (bytes == null) return listOf("$cls MISSING")
		val node = ClassNode().also { ClassReader(bytes).accept(it, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG) }
		val out = mutableListOf<String>()
		val kind = if (node.access and Opcodes.ACC_INTERFACE != 0) "interface" else "class"
		out += "$cls $kind super=${node.superName} ifaces=${node.interfaces.sorted()} ${annotations(node.visibleAnnotations, node.invisibleAnnotations)}"
		for (f in node.fields.sortedBy { it.name + it.desc }) {
			val ann = annotations(f.visibleAnnotations, f.invisibleAnnotations)
			val shadowed = ann.any { "Shadow" in it || "Accessor" in it }
			if (shadowed) out += "  field ${f.name} ${f.desc} static=${f.access and Opcodes.ACC_STATIC != 0} $ann"
		}
		val methods = mutableListOf<String>()
		for (m in node.methods) {
			val ann = (m.visibleAnnotations.orEmpty() + m.invisibleAnnotations.orEmpty()).filter(::isWiring)
			if (ann.isEmpty()) continue
			val named = ann.any { a -> listOf("Shadow", "Accessor", "Invoker", "Overwrite").any { "/$it;" in a.desc } }
			val static = m.access and Opcodes.ACC_STATIC != 0
			methods += "  method ${if (named) m.name else "<handler>"} ${m.desc} static=$static ${ann.map(::annotation).sorted()} params=[${paramAnnotations(m)}]"
		}
		out += methods.sorted()
		return out
	}

	@Test
	fun mixinWiringIsTheSame() {
		val classes = roots.flatMap { root ->
			val base = RepoFile(root)
			File(base, "dev/ashtonite365/apex/mixins").walkTopDown().filter { it.isFile && it.extension == "java" }
				.map { it.relativeTo(base).path.removeSuffix(".java") }.toList()
		}.sorted()
		val out = mutableListOf<String>()
		for (c in classes) {
			val bytes = javaClass.classLoader.getResourceAsStream("$c.class")?.use { it.readBytes() }
			out += describe(c.removePrefix("dev/ashtonite365/apex/mixins/"), bytes)
		}
		out += "classes ${classes.size}"
		OursGolden.check("mixins-wiring", out)
	}
}
