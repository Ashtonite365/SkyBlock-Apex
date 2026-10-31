// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.ours.init

import java.io.File
import java.net.URI
import java.nio.file.Files
import me.shedaniel.mm.api.ClassTinkerers
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.FrameNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.LineNumberNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode
import dev.ashtonite365.apex.init.AutoDiscoveryPlugin
import dev.ashtonite365.apex.init.EarlyRiser
import dev.ashtonite365.apex.init.Intermediary
import dev.ashtonite365.apex.init.MixinPlugin
import dev.ashtonite365.apex.test.ours.OursGolden

class InitEquivalenceTest {
	private val opcodeNames: Map<Int, String> = Opcodes::class.java.fields
		.filter { it.type == Int::class.javaPrimitiveType && Regex("^[A-Z][A-Z0-9_]*$").matches(it.name) }
		.filterNot { Regex("^(ACC_|T_|H_|F_|V\\d|V_|ASM|SOURCE_)").containsMatchIn(it.name) }
		.map { it.getInt(null) to it.name }
		.filter { it.first in 0..201 }
		.toMap()

	private fun dumpMethod(m: MethodNode): List<String> {
		val labels = HashMap<LabelNode, Int>()
		fun l(n: LabelNode) = "L" + labels.getOrPut(n) { labels.size }
		val out = mutableListOf("method ${m.access} ${m.name}${m.desc} sig=${m.signature} ex=${m.exceptions}")
		for (insn: AbstractInsnNode in m.instructions) {
			val op = opcodeNames[insn.opcode] ?: "op${insn.opcode}"
			out += "  " + when (insn) {
				is LabelNode -> l(insn) + ":"
				is LineNumberNode -> "LINE ${insn.line} ${l(insn.start)}"
				is FrameNode -> "FRAME ${insn.type}"
				is VarInsnNode -> "$op ${insn.`var`}"
				is IntInsnNode -> "$op ${insn.operand}"
				is MethodInsnNode -> "$op ${insn.owner}.${insn.name}${insn.desc} itf=${insn.itf}"
				is FieldInsnNode -> "$op ${insn.owner}.${insn.name} ${insn.desc}"
				is JumpInsnNode -> "$op ${l(insn.label)}"
				is LdcInsnNode -> "$op ${insn.cst.javaClass.simpleName}:${insn.cst}"
				is TypeInsnNode -> "$op ${insn.desc}"
				else -> "$op ${insn.javaClass.simpleName}"
			}
		}
		return out
	}

	private fun readClass(internalName: String): ClassNode {
		val bytes = javaClass.classLoader.getResourceAsStream("$internalName.class")!!.use { it.readBytes() }
		return ClassNode().also { ClassReader(bytes).accept(it, 0) }
	}

	@Suppress("UNCHECKED_CAST")
	@Test
	fun earlyRiserPatchesTheSameBytecode() {
		val out = mutableListOf<String>()
		val field = ClassTinkerers::class.java.getDeclaredField("tinkerers").apply { isAccessible = true }
		val map = field.get(ClassTinkerers.INSTANCE) as MutableMap<String, MutableSet<Any>>
		val before = map.mapValues { (_, v) -> v.toList() }
		EarlyRiser().run()
		val added = map.mapValues { (k, v) -> v.filter { c -> before[k].orEmpty().none { it === c } } }.filterValues { it.isNotEmpty() }
		try {
			out += "targets ${added.keys.sorted()}"
			for (target in added.keys.sorted()) {
				val consumers = added.getValue(target)
				val untouched = readClass(target)
				val node = readClass(target)
				for (c in consumers) {
					c.javaClass.getMethod("accept", ClassNode::class.java).apply { isAccessible = true }.invoke(c, node)
					c.javaClass.getMethod("acceptPost", ClassNode::class.java).apply { isAccessible = true }.invoke(c, node)
				}
				val oldMethods = untouched.methods.associate { (it.name + it.desc) to dumpMethod(it) }
				out += "class $target fields=${node.fields.map { "${it.access} ${it.name} ${it.desc}" }.sorted()} methods=${node.methods.size - untouched.methods.size}"
				for (m in node.methods.sortedBy { it.name + it.desc }) {
					val now = dumpMethod(m)
					if (oldMethods[m.name + m.desc] != now) out += now
				}
				out += "removed ${(oldMethods.keys - node.methods.map { it.name + it.desc }.toSet()).sorted()}"
			}
		} finally {
			for ((k, v) in added) map[k]?.removeAll { c -> v.any { it === c } }
		}
		OursGolden.check("init-risers", out)
	}

	@Test
	fun intermediaryHelpersResolveTheSame() {
		val out = mutableListOf<String>()
		val cls = Intermediary.ofIntermediaryClass("net.minecraft.client.gui.screens.Screen")
		val prim = Intermediary.ofClass(Double::class.javaPrimitiveType!!)
		val arr = Intermediary.ofClass(IntArray::class.java)
		val own = Intermediary.ofClass(MixinPlugin::class.java)
		for ((n, c) in listOf("cls" to cls, "prim" to prim, "arr" to arr, "own" to own))
			out += "$n ${c.unmapped()} ${c.mapped()} ${c.mapped().sort}"
		val m = Intermediary.ofMethod("mouseScrolled", "net.minecraft.client.gui.components.events.GuiEventListener",
			Intermediary.ofClass(Boolean::class.javaPrimitiveType!!), prim, prim, cls)
		out += "method ${m.name()} ${m.owner().unmapped()} ${m.intermediaryDesc()} ${m.mappedDesc()} ${m.mapped()} args=${m.parameters().size}"
		val v = Intermediary.ofMethod("internalCheckForMutation", own.unmapped().className, Intermediary.ofClass(Void.TYPE), cls)
		out += "void ${v.intermediaryDesc()} ${v.mapped()}"
		out += "eq ${cls == Intermediary.ofIntermediaryClass("net/minecraft/client/gui/screens/Screen")} ${m == Intermediary.ofMethod("mouseScrolled", "net.minecraft.client.gui.components.events.GuiEventListener", Intermediary.ofClass(Boolean::class.javaPrimitiveType!!), prim, prim, cls)}"
		out += "thrown ${runCatching { Intermediary::class.java.getDeclaredMethod("intermediaryClass").apply { isAccessible = true }.invoke(null) }.exceptionOrNull()?.cause}"
		OursGolden.check("init-intermediary", out)
	}

	@Test
	fun mixinDiscoveryIsTheSame() {
		val out = mutableListOf<String>()
		val plugin = AutoDiscoveryPlugin()
		for (u in listOf("jar:file:/a/b.jar!/dev/x/Y.class", "file:/r/dev/ashtonite365/apex/init/AutoDiscoveryPlugin.class",
			"file:/r/", "file:/r/other/Thing.class", "jar:file:/a/c.jar!/"))
			out += "base $u -> ${plugin.getBaseUrlForClassUrl(URI(u).toURL())}"
		plugin.setMixinPackage("dev.ashtonite365.apex.mixins")
		out += "pkg ${plugin.mixinPackage} ${plugin.mixinBaseDir}"
		val found = plugin.mixins

		val roots = (listOf(File(AutoDiscoveryPlugin::class.java.protectionDomain.codeSource.location.toURI())) +
			System.getProperty("apex.classroots").orEmpty().split(File.pathSeparator).filter { it.isNotBlank() }.map(::File))
		val expected = roots.filter { it.isDirectory }.flatMap { root ->
			val base = File(root, "dev/ashtonite365/apex/mixins")
			if (!base.isDirectory) emptyList() else Files.walk(base.toPath()).use { s ->
				s.map { root.toPath().relativize(it).toString() }.filter { it.endsWith(".class") }.toList()
			}.map { it.removeSuffix(".class").replace('/', '.').removePrefix("dev.ashtonite365.apex.mixins.") }
		}
		val clean = expected.filter { it.isNotEmpty() && !it.endsWith(".") }
		out += "scan missing=${(clean - found.toSet()).sorted()} extra=${(found - clean.toSet()).sorted()} dupes=${found.size - found.toSet().size}"
		out += "sameInstance ${plugin.mixins === found}"
		val n = found.size
		for (c in listOf("dev/ashtonite365/apex/mixins/Foo.class", "dev\\ashtonite365\\apex\\mixins\\Bar.class", "dev/ashtonite365/apex/mixins/Foo.txt",
			"dev/ashtonite365/apex/mixinsX/Baz.class", "dev/ashtonite365/apex/mixins/.class", "dev/ashtonite365/apex/mixins/sub/Q.class",
			"dev.ashtonite365.apex.mixins.Dotted.class", "other/Foo.class"))
			out += "try $c " + (runCatching { plugin.tryAddMixinClass(c) }.exceptionOrNull()?.javaClass?.simpleName ?: "ok")
		out += "added ${found.drop(n)}"
		out += "registered ${AutoDiscoveryPlugin.getMixinPlugins().contains(plugin)}"
		val defaults = AutoDiscoveryPlugin.getDefaultAllMixinClassesFQNs()
		out += "defaults ${defaults.size == n} ${defaults.all { it.startsWith("dev.ashtonite365.apex.mixins.") }}"

		val mp = MixinPlugin()
		mp.onLoad("dev.ashtonite365.apex.mixins")
		out += "mp pkg=${mp.mixinPackage} refmap=${mp.refMapperConfig} inInstances=${MixinPlugin.instances.contains(mp)}"
		out += "mp mixins=${mp.mixins == found.take(n).filter { mp.shouldApplyMixin(null, it) }}"
		out += "mp expected=${mp.expectedFullPathMixins.size} ${mp.expectedFullPathMixins.all { it.startsWith("dev.ashtonite365.apex.mixins.") }}"
		for (name in listOf("dev.ashtonite365.apex.mixins.devenv.X", "devenv.X", "dev.ashtonite365.apex.mixins.sodium.Y", "sodium.Y",
			"dev.ashtonite365.apex.mixins.Normal", "Normal", "xsodium.Z"))
			out += "apply $name ${mp.shouldApplyMixin("t", name)} req(none)=${MixinPlugin.requiredModPresent(name) { false }} req(all)=${MixinPlugin.requiredModPresent(name) { true }}"
		out += "req(null) ${MixinPlugin.requiredModPresent(null) { false }}"
		mp.postApply("t", null, "a.B", null)
		mp.postApply("t", null, "a.B", null)
		out += "applied ${mp.appliedFullPathMixins} list=${mp.appliedMixins}"
		mp.acceptTargets(mutableSetOf(), mutableSetOf())
		mp.preApply("t", null, "a.B", null)
		MixinPlugin.instances.remove(mp)
		OursGolden.check("init-mixins", out)
	}
}
