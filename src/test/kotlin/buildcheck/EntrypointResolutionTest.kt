// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.buildcheck

import com.google.gson.JsonParser
import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode

class EntrypointResolutionTest {
	private val project = File(System.getProperty("apex.projectDir") ?: error("apex.projectDir not set by the build"))
	private val classDirs = (System.getProperty("apex.entrypointClassDirs") ?: error("apex.entrypointClassDirs not set by the build"))
		.split(File.pathSeparator).filter { it.isNotBlank() }.map(::File)

	private fun readClass(name: String): ClassNode? {
		val path = name.replace('.', '/') + ".class"
		val file = classDirs.map { File(it, path) }.firstOrNull { it.isFile } ?: return null
		return ClassNode().also { ClassReader(file.readBytes()).accept(it, ClassReader.SKIP_CODE) }
	}

	private fun entrypoints(): List<Pair<String, String>> {
		val root = JsonParser.parseString(File(project, "src/main/resources/fabric.mod.json").readText()).asJsonObject
		return root.getAsJsonObject("entrypoints").entrySet().flatMap { (stage, list) ->
			list.asJsonArray.map { e -> stage to (if (e.isJsonObject) e.asJsonObject["value"].asString else e.asString) }
		}
	}

	private fun problem(ref: String): String? {
		val className = ref.substringBefore("::")
		val node = readClass(className) ?: return "class $className not found"
		if (node.access and Opcodes.ACC_PUBLIC == 0) return "class $className is not public"
		if (!ref.contains("::")) {
			val ctor = node.methods.any { it.name == "<init>" && it.desc == "()V" && it.access and Opcodes.ACC_PUBLIC != 0 }
			return if (ctor) null else "$className has no public no-arg constructor"
		}
		val member = ref.substringAfter("::")
		val methods = node.methods.filter { it.name == member }
		if (methods.isEmpty()) return if (node.fields.any { it.name == member }) null else "$className has no method or field '$member'"
		if (methods.size > 1) return "$className::$member is ambiguous (${methods.size} overloads)"
		val m = methods.single()
		if (m.access and Opcodes.ACC_STATIC == 0) return "$className::$member is not static (add @JvmStatic on a Kotlin object)"
		if (m.access and Opcodes.ACC_PUBLIC == 0) return "$className::$member is not public"
		return null
	}

	@Test
	fun everyEntrypointResolves() {
		val refs = entrypoints()
		assertTrue(refs.size >= 10, "expected the full entrypoint list, got $refs")
		val problems = refs.mapNotNull { (stage, ref) -> problem(ref)?.let { "$stage -> $ref: $it" } }
		if (problems.isNotEmpty()) fail<Unit>(problems.joinToString("\n"))
	}
}
