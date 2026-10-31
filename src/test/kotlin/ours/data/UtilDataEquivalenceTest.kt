// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.ours.data

import java.util.UUID
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.test.gui.config.storage.MainConfigRegistry
import dev.ashtonite365.apex.test.ours.OursGolden
import dev.ashtonite365.apex.util.data.DataHolder
import dev.ashtonite365.apex.util.data.GenericConfig
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.data.ProfileKeyedConfig
import dev.ashtonite365.apex.util.data.ProfileSpecificDataHolder

class UtilDataEquivalenceTest {
	init {
		dev.ashtonite365.apex.test.ApexTestBootstrap.bootstrapMinecraft()
	}

	private fun attempt(block: () -> Any?): String = try {
		block().toString()
	} catch (e: Throwable) {
		"ERR:" + e.javaClass.simpleName
	}

	private fun digestTwice(first: () -> JsonObject, second: () -> JsonObject): String {
		val a = first().toString()
		val b = second().toString()
		return if (a == b) "keys=" + first().keys + " sha=" + OursGolden.sha(a) else "unstable"
	}

	@Test
	fun genericHoldersBehaveTheSame() {
		val out = mutableListOf<String>()
		val holders = MainConfigRegistry.registered.filterIsInstance<GenericConfig<*>>().sortedBy { it.javaClass.name }
		for (g in holders) {
			val before = if (g.isLoaded) g.saveTo(Unit) else null
			out += "generic ${g.javaClass.name} prefix=${g.prefix} storage=${g.storageClass} dataHolder=${g is DataHolder<*>} keys=${g.keys()}"
			out += "  default " + attempt { digestTwice({ g.explicitDefaultLoad(); g.saveTo(Unit) }, { g.explicitDefaultLoad(); g.saveTo(Unit) }) }
			out += "  empty " + attempt { digestTwice({ g.loadFrom(Unit, JsonObject(emptyMap())); g.saveTo(Unit) }, { g.loadFrom(Unit, JsonObject(emptyMap())); g.saveTo(Unit) }) }
			out += "  other-key " + attempt { g.loadFrom(Unit, JsonObject(mapOf(g.prefix + "-x" to JsonPrimitive(1)))); g.isLoaded }
			out += "  junk " + attempt { g.loadFrom(Unit, JsonObject(mapOf(g.prefix to JsonPrimitive("\u0000junk")))) }
			g.clear()
			out += "  cleared loaded=${g.isLoaded} data=${attempt { g.data }} save=${attempt { g.saveTo(Unit) }}"
			if (before != null) g.loadFrom(Unit, before) else g.clear()
		}
		OursGolden.check("data-generic", out)
	}

	@Test
	fun profileHoldersBehaveTheSame() {
		val out = mutableListOf<String>()
		val holders = MainConfigRegistry.registered.filterIsInstance<ProfileKeyedConfig<*>>().sortedBy { it.javaClass.name }
		val other = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef")
		for (p in holders) {
			val before = if (p.isLoaded) p.keys().associateWith { p.saveTo(it) } else null
			out += "profile ${p.javaClass.name} prefix=${p.prefix} storage=${p.storageClass} specific=${p is ProfileSpecificDataHolder<*>}"
			p.explicitDefaultLoad()
			out += "  fresh loaded=${p.isLoaded} keys=${p.keys().size}"
			out += "  data " + attempt { p.data; p.keys().size }
			out += "  save-current " + attempt { digestTwice({ p.saveTo(p.keys().first()) }, { p.saveTo(p.keys().first()) }) }
			out += "  save-missing " + attempt { p.saveTo(other) }
			out += "  load-empty " + attempt { p.loadFrom(other, JsonObject(emptyMap())); p.keys().size }
			out += "  load-junk " + attempt { p.loadFrom(other, JsonObject(mapOf(p.prefix to JsonPrimitive("\u0000junk")))) }
			p.clear()
			out += "  cleared loaded=${p.isLoaded} keys=${attempt { p.keys() }} data=${attempt { p.data }} save=${attempt { p.saveTo(other) }}"
			out += "  load-into-cleared " + attempt { p.loadFrom(other, JsonObject(emptyMap())); p.keys().size }
			p.clear()
			before?.forEach { (k, v) -> p.loadFrom(k, v) }
		}
		OursGolden.check("data-profile", out)
	}

	class NoAnnotation : ManagedConfig("ours-data-no-annotation", Category.CHAT)

	@Test
	fun managedConfigBasicsAreTheSame() {
		val out = mutableListOf<String>()

		val shipped = MainConfigRegistry.registered.filterIsInstance<ManagedConfig>().toSet()
		for (c in ManagedConfig.Category.entries) {

			val mine = c.configs.filter { it in shipped }
			out += "category ${c.name} ${c.ordinal} ${c.translationKey} ${c.labelText.string} ${c.description.string} configs=${mine.size} first=${mine.map { it.name }.minOrNull()}"
		}
		@Suppress("DEPRECATION")
		out += "aliases ${ManagedConfig.Category.META} ${ManagedConfig.Category.INTEGRATIONS} ${ManagedConfig.Category.DEV} ${ManagedConfig.Category.ITEMS} ${ManagedConfig.Category.CHAT.keybindGroupId}"
		out += "noann " + try { NoAnnotation(); "made" } catch (e: Exception) { e.javaClass.simpleName + " " + e.message }
		out += "registered ${shipped.count { it in ManagedConfig.everyHolder.getAll() }} of ${shipped.size}"
		val configs = MainConfigRegistry.registered.filterIsInstance<ManagedConfig>().sortedBy { it.name }
		for (c in configs) {
			@Suppress("UNCHECKED_CAST")
			val options = c.sortedOptions as List<dev.ashtonite365.apex.gui.config.ManagedOption<Any>>
			val previous = options.map { it._actualValue }
			val saved = c.saveTo(Unit)
			c.clear()
			val cleared = attempt { OursGolden.sha(c.saveTo(Unit).toString()) }
			c.explicitDefaultLoad()
			val defaults = attempt { OursGolden.sha(c.saveTo(Unit).toString()) }
			c.loadFrom(Unit, saved)
			options.forEachIndexed { i, o -> o._actualValue = previous[i] }
			out += "config ${c.name} keys=${c.keys()} storage=${c.storageClass} cleared=$cleared defaults=$defaults section=${attempt { c.mainSection }}" +
				" path=${attempt { c.sortedOptions.firstOrNull()?.let { c.settingPath(it.propertyName) } }} nope=${attempt { c.settingPath("__nope") }}"
		}

		val editor = attempt { configs.first().getConfigEditor(null).javaClass.superclass.simpleName }
		out += "editor " + editor.replace("ERR:ServiceConfigurationError", "ERR:no-platform").replace("ERR:NoClassDefFoundError", "ERR:no-platform")
		OursGolden.check("data-managed", out)
	}
}
