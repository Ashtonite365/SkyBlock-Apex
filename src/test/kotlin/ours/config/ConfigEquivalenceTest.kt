// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.ours.config

import java.nio.file.Files
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import com.google.gson.JsonParser
import io.github.notenoughupdates.moulconfig.gui.GuiComponent
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.gui.config.GuiAppender
import dev.ashtonite365.apex.gui.config.KeyBindingStateManager
import dev.ashtonite365.apex.gui.config.ManagedOption
import dev.ashtonite365.apex.gui.config.storage.ConfigEditor
import dev.ashtonite365.apex.gui.config.storage.ConfigLoadContext
import dev.ashtonite365.apex.gui.config.storage.ConfigStorageClass
import dev.ashtonite365.apex.gui.config.storage.FirstLevelSplitJsonFolder
import dev.ashtonite365.apex.gui.config.storage.JsonSlot
import dev.ashtonite365.apex.keybindings.GenericInputButton
import dev.ashtonite365.apex.keybindings.InputModifiers
import dev.ashtonite365.apex.keybindings.SavedKeyBinding
import dev.ashtonite365.apex.test.ours.OursGolden
import dev.ashtonite365.apex.util.data.ManagedConfig

class ConfigEquivalenceTest {
	init {
		dev.ashtonite365.apex.test.ApexTestBootstrap.bootstrapMinecraft()
	}

	private fun <T> attempt(block: () -> T): String = try {
		block().toString()
	} catch (e: Throwable) {
		"ERR:" + e.javaClass.simpleName
	}

	private val configs get() = dev.ashtonite365.apex.test.gui.config.storage.MainConfigRegistry.registered.filterIsInstance<ManagedConfig>().sortedBy { it.name }

	@Test
	fun everyOptionKeepsItsKeyAndFormat() {
		val out = mutableListOf<String>()
		for (c in configs) {
			out += "config ${c.name} ${c.category} ${c.storageClass} ${c.translationKey} ${c.labelText.string} keys=${c.keys()}"
			for (o in c.sortedOptions) {
				@Suppress("UNCHECKED_CAST") val opt = o as ManagedOption<Any>
				out += "  opt ${opt.propertyName} ${opt.handler.javaClass.simpleName} ${opt.rawLabelText} ${opt.descriptionTranslationKey}" +
					" risk=${opt.riskLevel} parent=${opt.parentToggle?.propertyName} default=${attempt { opt.handler.encode(opt.default()) }}" +
					" back=${attempt { opt.handler.encode(opt.handler.decode(opt.handler.encode(opt.default())!!)) }}"
			}
			out += "  same=${c.allOptions.keys.sorted() == c.sortedOptions.map { it.propertyName }.sorted()}"
		}
		OursGolden.check("config-options", out)
	}

	@Test
	fun savingAndLoadingRoundTrips() {
		val out = mutableListOf<String>()
		for (c in configs) {
			@Suppress("UNCHECKED_CAST")
			val options = c.sortedOptions as List<ManagedOption<Any>>
			val before = options.map { it._actualValue }
			val saved = c.saveTo(Unit)
			out += "${c.name}\tsaved=${saved.keys}\t${saved[c.name]?.let { (it as? JsonObject)?.keys }}"
			c.loadFrom(Unit, saved)
			out += "${c.name}\tagain=${c.saveTo(Unit) == saved}"
			c.loadFrom(Unit, JsonObject(mapOf(c.name to JsonObject(mapOf("__nope" to JsonPrimitive(1))))))
			out += "${c.name}\tjunk=${OursGolden.sha(c.saveTo(Unit).toString())}"
			c.loadFrom(Unit, saved)
			options.forEachIndexed { i, o -> o._actualValue = before[i] }
		}
		OursGolden.check("config-roundtrip", out)
	}

	@Test
	fun splitFolderReadsAndWritesTheSame() {
		val dir = Files.createTempDirectory("ours-core-flsjf")
		val out = mutableListOf<String>()
		try {
			val folder = dir.resolve("cfg")
			val ctx = ConfigLoadContext("golden")
			val f = FirstLevelSplitJsonFolder(ctx, folder)
			out += "missing ${f.load()}"
			f.save(buildJsonObject { put("one", buildJsonObject { put("k", "v") }); put("two", JsonArray(listOf(JsonPrimitive(1)))); put("three", 3) })
			out += "files ${folder.listDirectoryEntries().map { it.name + "=" + it.readText() }.sorted()}"
			out += "load ${f.load()}"
			Files.writeString(folder.resolve("bad.json"), "{nope")
			Files.writeString(folder.resolve("other.txt"), "x")
			out += "bad ${f.load()} backup=${f.hasCreatedBackup}"
			f.save(buildJsonObject { put("one", 1) })
			out += "after ${folder.listDirectoryEntries().map { it.name }.sorted()}"

			out += "loglevels " + ctx.logBuffer.toString().lines()
				.filter { Regex("^\\[\\w+\\]").containsMatchIn(it) && "backups" !in it }
				.joinToString("|") { it.substringBefore("]") + "]" }
			out += "ctx ${ctx.loadId} ${ctx.shouldSaveLogBuffer} ${ctx.backupPath.fileName} ${ctx.logFile.fileName}"
			ctx.shouldSaveLogBuffer = false
			ctx.close()
		} finally {
			dir.toFile().deleteRecursively()
		}
		OursGolden.check("config-flsjf", out)
	}

	private fun gson(s: String) = JsonParser.parseString(s).asJsonObject

	@Test
	fun editorAndFixersBehaveTheSame() {
		val out = mutableListOf<String>()
		fun run(label: String, json: String, edit: ConfigEditor.() -> Unit) {
			val holder = arrayOf(gson(json))
			val root = object : JsonSlot() {
				override fun read() = holder[0]
				override fun write(value: com.google.gson.JsonElement) { holder[0] = value.asJsonObject }
			}
			out += "$label " + attempt { ConfigEditor(listOf(root)).edit(); holder[0].toString() }
		}
		run("move", """{"a":{"b":1,"c":2}}""") { move("a.b", "x.y.z") }
		run("moveSame", """{"a":1}""") { move("a", "a") }
		run("moveMissing", """{"a":1}""") { move("q.r", "x") }
		run("moveClash", """{"a":1,"b":2}""") { move("a", "b") }
		run("moveRoot", """{"a":{"k":1}}""") { move("a", "") }
		run("moveFromRoot", """{"a":1}""") { move("", "z") }
		run("star", """{"p":{"u1":{"old":1},"u2":{"old":2}}}""") { at("p.*") { move("old", "new") } }
		run("arr", """{"l":[{"v":1},{"v":2}]}""") { at("l.*.v") { transform { JsonPrimitive(9) } } }
		run("idx", """{"l":[{"v":1},{"v":2}]}""") { at("l.1") { move("v", "w") } }
		run("prim", """{"l":3}""") { at("l.*") {} }
		run("transform", """{"a":{"b":[1,2]}}""") { at("a") { transform { buildJsonObject { put("swapped", it) } } } }
		OursGolden.check("config-editor", out)
	}

	@Test
	fun keyBindingEditorBehavesTheSame() {
		val out = mutableListOf<String>()
		var value = SavedKeyBinding.unbound()
		val m = KeyBindingStateManager({ value }, { value = it; out += "set ${it}" }, { out += "blur" }, { out += "focus" })
		val none = InputModifiers(0)
		val shift = GenericInputButton.ofKeyCode(340)
		val a = GenericInputButton.ofKeyCode(65)
		out += "idle ${m.keyDown(a, none)} ${m.capturing}"
		out += "click " + attempt { m.onClick(1) } + " ${m.capturing}"
		out += "click0 " + attempt { m.onClick(0) } + " ${m.capturing}"
		out += "shift ${attempt { m.keyDown(shift, none) }} ${m.pendingModifier} ${m.capturing}"
		out += "releaseShift ${attempt { m.keyUp(shift, none) }} ${m.capturing} $value"
		m.capturing = true
		out += "a ${attempt { m.keyDown(a, none) }} ${m.capturing} $value"
		m.capturing = true
		out += "esc ${attempt { m.keyDown(GenericInputButton.escape(), none) }} ${m.capturing} $value"
		m.capturing = true
		m.pendingModifier = a
		m.focusLost()
		out += "lost ${m.capturing} ${m.pendingModifier} ${m.label.string}"
		out += "releaseIdle ${m.keyUp(a, none)}"
		OursGolden.check("config-keybinding", out)
	}

	private fun dump(c: GuiComponent, depth: Int, out: MutableList<String>) {
		out += "  ".repeat(depth) + c.javaClass.name.substringAfterLast('.') + " w=" + attempt { c.width } + " h=" + attempt { c.height }
		val kids = attempt {
			val list = mutableListOf<GuiComponent>()
			c.foldChildren(Unit) { child, _ -> list += child }
			list
		}
		@Suppress("UNCHECKED_CAST")
		val l = runCatching {
			val list = mutableListOf<GuiComponent>()
			c.foldChildren(Unit) { child, _ -> list += child }
			list
		}.getOrNull()
		if (l == null) out += "  ".repeat(depth + 1) + kids
		else for (k in l) dump(k, depth + 1, out)
	}

	@Test
	fun settingsRowsAreBuiltTheSame() {
		val out = mutableListOf<String>()
		for (c in configs) for (o in c.sortedOptions) {
			val app = GuiAppender(400) { error("no screen in tests") }

			val outcome = attempt { o.appendToGui(app) }.replace("ERR:IllegalStateException", "ERR:moulconfig-platform-unavailable")
			out += "${c.name}.${o.propertyName} " + outcome + " rows=${app.panel.size}"
			for (row in app.panel) dump(row, 1, out)
		}
		OursGolden.checkDigests("config-rows", out.mapIndexed { i, s -> "$i" to s })
		OursGolden.check("config-rows-sample", out.filterIndexed { i, _ -> i % 23 == 0 })
	}
}
